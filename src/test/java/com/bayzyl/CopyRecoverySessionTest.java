package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** /copy tracks a recovery session only for a copy that actually starts, and ends it on every other path. */
class CopyRecoverySessionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @TempDir
    Path temporaryDirectory;

    private final EditService editService = mock(EditService.class);
    private final Player player = mock(Player.class);
    private CrashRecoveryService recovery;
    private Path file;

    @Test
    void invalidSelectionOrRunningCopyNeverStartsASession() throws Exception {
        BayzylCommand command = command();
        Selection incomplete = new Selection(CrashRecoveryTransactionTest.location(0, 0, 0), null, SelectionType.CUBOID);

        runCopy(command, null);
        runCopy(command, new Selection(null, null, SelectionType.CUBOID));
        runCopy(command, incomplete);
        when(editService.hasCopyTask(PLAYER)).thenReturn(true);
        runCopy(command, completeSelection());

        assertNull(recovery.getSession(PLAYER));
        assertTrue(recovery.flushRecovery());
        assertFalse(restart().hasInterruptedSession(PLAYER), "nothing ran, so nothing was interrupted");
        verify(editService, never()).copySelection(any(), any(), any());
        verify(editService, never()).startResponsiveCopy(any(), any(), any(), any());
    }

    @Test
    void refusedResponsiveCopyLeavesNoSession() throws Exception {
        BayzylCommand command = command();
        Selection selection = completeSelection();
        when(editService.shouldCopyResponsively(selection)).thenReturn(true);
        when(editService.startResponsiveCopy(any(), any(), any(), any())).thenReturn(false);

        runCopy(command, selection);

        assertNull(recovery.getSession(PLAYER));
        assertTrue(recovery.flushRecovery());
        assertFalse(restart().hasInterruptedSession(PLAYER));
    }

    @Test
    void copyThatProducesNothingOrThrowsLeavesNoSession() throws Exception {
        BayzylCommand command = command();
        Selection selection = completeSelection();
        when(editService.copySelection(any(), any(), any())).thenReturn(null);
        runCopy(command, selection);
        assertNull(recovery.getSession(PLAYER));

        when(editService.copySelection(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThrows(IllegalStateException.class, () -> runCopy(command, selection));
        assertNull(recovery.getSession(PLAYER));
        assertTrue(recovery.flushRecovery());
        assertFalse(restart().hasInterruptedSession(PLAYER));
    }

    @Test
    void startedResponsiveCopyKeepsItsSessionUntilItCompletes() throws Exception {
        BayzylCommand command = command();
        Selection selection = completeSelection();
        when(editService.shouldCopyResponsively(selection)).thenReturn(true);
        when(editService.startResponsiveCopy(any(), any(), any(), any())).thenReturn(true);

        runCopy(command, selection);

        assertNotNull(recovery.getSession(PLAYER), "a copy still running is tracked");
        assertTrue(recovery.flushRecovery());
        assertTrue(restart().hasInterruptedSession(PLAYER), "a restart mid-copy really interrupts it");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Clipboard>> onComplete = ArgumentCaptor.forClass(Consumer.class);
        verify(editService).startResponsiveCopy(any(), any(), any(), onComplete.capture());
        Clipboard copied = mock(Clipboard.class);
        onComplete.getValue().accept(copied);
        assertNull(recovery.getSession(PLAYER));
    }

    @Test
    void successfulCopyCompletesItsSession() throws Exception {
        BayzylCommand command = command();
        Selection selection = completeSelection();
        when(editService.copySelection(any(), any(), any())).thenReturn(mock(Clipboard.class));

        runCopy(command, selection);

        assertNull(recovery.getSession(PLAYER));
    }

    private static Selection completeSelection() {
        return new Selection(CrashRecoveryTransactionTest.location(0, 0, 0),
                CrashRecoveryTransactionTest.location(3, 3, 3), SelectionType.CUBOID);
    }

    private CrashRecoveryService openRecovery() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CopyRecoverySessionTest"));
        return new CrashRecoveryService(plugin, file, Runnable::run, false, System::currentTimeMillis,
                CrashRecoveryService.systemStoreFactory());
    }

    private CrashRecoveryService restart() {
        return openRecovery();
    }

    private BayzylCommand command() throws Exception {
        file = temporaryDirectory.resolve("crash-recovery.yml");
        recovery = openRecovery();
        when(player.getUniqueId()).thenReturn(PLAYER);
        Constructor<?> constructor = BayzylCommand.class.getConstructors()[0];
        Class<?>[] types = constructor.getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == EditService.class) {
                arguments[i] = editService;
            } else if (types[i] == CrashRecoveryService.class) {
                arguments[i] = recovery;
            } else if (types[i] == RecentEditTrailService.class) {
                arguments[i] = mock(RecentEditTrailService.class);
            } else if (types[i] == BayzylAccess.class) {
                arguments[i] = new BayzylAccess();
            } else if (types[i] == CommandAccessPolicy.class) {
                arguments[i] = new CommandAccessPolicy();
            }
        }
        return (BayzylCommand) constructor.newInstance(arguments);
    }

    private void runCopy(BayzylCommand command, Selection selection) throws Exception {
        Method method = BayzylCommand.class.getDeclaredMethod("runCopy", Player.class, Selection.class, BlockMask.class);
        method.setAccessible(true);
        try {
            method.invoke(command, player, selection, BlockMask.parse(null));
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
