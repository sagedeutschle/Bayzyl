package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrashRecoveryIntegrationTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoredClipboardKeepsSkippedCellsAndIsSafeToPaste() {
        Path target = file("paste");
        service(target).saveClipboard(PLAYER, CrashRecoveryTransactionTest.clipboard(
                CrashRecoveryTransactionTest.blockData("minecraft:stone"), null));

        try (MockedStatic<Bukkit> bukkit = server()) {
            ClipboardManager manager = new ClipboardManager();
            manager.setCrashRecoveryService(service(target));
            Clipboard restored = manager.get(PLAYER);
            assertNotNull(restored);
            assertNotNull(restored.get(0, 0, 0));
            assertNull(restored.get(1, 0, 0), "a skipped cell must stay skipped, not become air");
            assertNull(restored.getState(0, 0, 0));
            assertNull(restored.getState(1, 0, 0));
            assertSame(restored, manager.get(PLAYER), "restored clipboard is cached after the first load");
        }
    }

    @Test
    void clipboardInAnUnloadedWorldIsKeptForLater() throws Exception {
        Path target = file("unloaded");
        service(target).saveClipboard(PLAYER, CrashRecoveryTransactionTest.clipboard(
                CrashRecoveryTransactionTest.blockData("minecraft:stone"), null));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            CrashRecoveryService reader = service(target);
            assertNull(reader.loadClipboard(PLAYER));
            assertTrue(reader.flushRecovery());
            assertTrue(Files.readString(target).contains("minecraft:stone"));

            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(CrashRecoveryTransactionTest.location(0, 0, 0).getWorld());
            BlockData stone = CrashRecoveryTransactionTest.blockData("minecraft:stone");
            bukkit.when(() -> Bukkit.createBlockData("minecraft:stone")).thenReturn(stone);
            assertNotNull(reader.loadClipboard(PLAYER));
        }
    }

    @Test
    void clipboardWithInvalidBlockDataIsRejectedAndRemoved() throws Exception {
        Path target = file("invalid");
        service(target).saveClipboard(PLAYER, CrashRecoveryTransactionTest.clipboard(
                CrashRecoveryTransactionTest.blockData("minecraft:not_a_block"), null));

        try (MockedStatic<Bukkit> bukkit = server()) {
            CrashRecoveryService reader = service(target);
            assertNull(reader.loadClipboard(PLAYER));
            assertTrue(reader.flushRecovery());
            assertFalse(Files.readString(target).contains("minecraft:not_a_block"));
        }
    }

    @Test
    void onlySessionsRestoredFromDiskAreOfferedForResume() {
        Path target = file("offer");
        CrashRecoveryService writer = service(target);
        writer.startSession(PLAYER, "copy", "starting", Map.of("note", "x"), ignored -> { });
        assertFalse(writer.hasInterruptedSession(PLAYER), "a live session in this run is not interrupted");

        CrashRecoveryService restarted = service(target);
        assertTrue(restarted.hasInterruptedSession(PLAYER));
    }

    @Test
    void quittingDoesNotEraseAnInterruptedSessionAndJoinPointsAtTheRealCommand() {
        Path target = file("quit");
        service(target).startSession(PLAYER, "copy", "starting", Map.of("note", "x"), null);
        CrashRecoveryService restarted = service(target);
        CrashRecoveryListener listener = new CrashRecoveryListener(restarted);
        Player player = player();

        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onPlayerQuit(quit);
        assertTrue(restarted.hasInterruptedSession(PLAYER));

        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        listener.onPlayerJoin(join);
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().stream().anyMatch(message -> message.contains("/resume ")),
                messages.getAllValues().toString());
        assertTrue(CommandRegistry.getTopLevel().stream().anyMatch(spec -> spec.name().equals("resume")));
    }

    @Test
    void resumeAfterRestartRunsTheRegisteredHandlerWithRestoredDataThenClears() {
        Path target = file("resume");
        CrashRecoveryService writer = service(target);
        Selection selection = new Selection(CrashRecoveryTransactionTest.location(1, 2, 3),
                CrashRecoveryTransactionTest.location(4, 5, 6), SelectionType.CUBOID);
        writer.startSession(PLAYER, "copy", "starting", Map.of("selection", selection), null);

        try (MockedStatic<Bukkit> bukkit = server()) {
            CrashRecoveryService restarted = service(target);
            List<CrashRecoveryService.ActiveCommandSession> resumed = new ArrayList<>();
            restarted.registerResumeHandler("copy", (player, session) -> resumed.add(session));
            restarted.resumeSession(player());

            assertEquals(1, resumed.size());
            Selection restored = assertInstanceOf(Selection.class, resumed.get(0).data().get("selection"));
            assertEquals(4.0, restored.getPos2().getX());
            assertNull(restarted.getSession(PLAYER));
            assertFalse(restarted.hasInterruptedSession(PLAYER));
        }
    }

    @Test
    void resumeKeepsTheSessionWhileItsWorldIsNotLoadedThenRunsAndClearsOnceItIs() throws Exception {
        Path target = file("world-later");
        Selection selection = new Selection(CrashRecoveryTransactionTest.location(1, 2, 3),
                CrashRecoveryTransactionTest.location(4, 5, 6), SelectionType.CUBOID);
        service(target).startSession(PLAYER, "copy", "starting", Map.of("selection", selection), null);

        CrashRecoveryService restarted;
        List<CrashRecoveryService.ActiveCommandSession> resumed = new ArrayList<>();
        Player player = player();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            restarted = service(target);
            restarted.registerResumeHandler("copy", (ignored, session) -> resumed.add(session));
            restarted.resumeSession(player);

            assertTrue(resumed.isEmpty(), "the handler must not run against a world that is not loaded");
            assertNotNull(restarted.getSession(PLAYER));
            assertTrue(restarted.hasInterruptedSession(PLAYER), "still offered for /resume");
            assertTrue(restarted.flushRecovery());
            assertTrue(Files.readString(target).contains("command: copy"), "still on disk");
            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(player, atLeastOnce()).sendMessage(messages.capture());
            assertTrue(messages.getValue().contains("not loaded yet"), messages.getValue());
        }

        try (MockedStatic<Bukkit> bukkit = server()) {
            restarted.resumeSession(player);
            assertEquals(1, resumed.size());
            assertNull(restarted.getSession(PLAYER));
            assertFalse(restarted.hasInterruptedSession(PLAYER));
        }
    }

    @Test
    void resumeWithoutAHandlerTellsThePlayerAndClears() {
        Path target = file("no-handler");
        service(target).startSession(PLAYER, "stack", "starting", Map.of(), null);
        CrashRecoveryService restarted = service(target);
        Player player = player();
        restarted.resumeSession(player);
        verify(player).sendMessage(anyString());
        assertNull(restarted.getSession(PLAYER));
    }

    private MockedStatic<Bukkit> server() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        World world = CrashRecoveryTransactionTest.location(0, 0, 0).getWorld();
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        BlockData stone = CrashRecoveryTransactionTest.blockData("minecraft:stone");
        bukkit.when(() -> Bukkit.createBlockData("minecraft:stone")).thenReturn(stone);
        bukkit.when(() -> Bukkit.createBlockData("minecraft:not_a_block"))
                .thenThrow(new IllegalArgumentException("Could not parse data"));
        return bukkit;
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER);
        when(player.getName()).thenReturn("sage");
        return player;
    }

    private Path file(String name) {
        return temporaryDirectory.resolve(name).resolve("crash-recovery.yml");
    }

    private CrashRecoveryService service(Path target) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CrashRecoveryIntegrationTest"));
        return new CrashRecoveryService(plugin, target, Runnable::run, false, System::currentTimeMillis,
                CrashRecoveryService.systemStoreFactory());
    }
}
