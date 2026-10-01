package com.bayzyl;

import com.bayzyl.persistence.AtomicYamlStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CrashRecoveryStateTest {
    private static final Executor DIRECT = Runnable::run;

    @TempDir
    Path temporaryDirectory;

    @Test
    void lifecycleTruthTableIsCachedBeforeStartupPersistsRunning() throws Exception {
        Path clean = recoveryFile("clean");
        write(clean, "meta:\n  lifecycleState: CLEAN\n");
        CrashRecoveryService cleanService = service(clean);
        assertFalse(cleanService.wasCrashDetected());
        assertTrue(Files.readString(clean).contains("lifecycleState: RUNNING"));

        Path running = recoveryFile("running");
        write(running, "meta:\n  lifecycleState: RUNNING\n");
        assertTrue(service(running).wasCrashDetected());

        Path unknown = recoveryFile("unknown");
        write(unknown, "meta:\n  lifecycleState: SOMETHING_ELSE\n");
        assertTrue(service(unknown).wasCrashDetected());

        Path legacy = recoveryFile("legacy");
        write(legacy, "sessions: {}\nmeta:\n  lastCleanShutdown: 123\n");
        assertTrue(service(legacy).wasCrashDetected());

        Path brandNew = recoveryFile("brand-new");
        CrashRecoveryService brandNewService = service(brandNew);
        assertFalse(brandNewService.wasCrashDetected());
        assertTrue(Files.readString(brandNew).contains("lifecycleState: RUNNING"));
    }

    @Test
    void backupOnlyIsAbnormalEvenWhenBackupWasCleanAndBothCorruptFailClosed() throws Exception {
        Path backupOnly = recoveryFile("backup-only");
        write(backupOnly.resolveSibling(backupOnly.getFileName() + ".bak"),
                "meta:\n  lifecycleState: CLEAN\n");
        assertTrue(service(backupOnly).wasCrashDetected());

        Path corrupt = recoveryFile("corrupt");
        write(corrupt, "meta: [unterminated\n");
        write(corrupt.resolveSibling(corrupt.getFileName() + ".bak"), "also: [broken\n");
        assertThrows(IllegalStateException.class, () -> service(corrupt));
    }

    @Test
    void startupRunningPersistenceFailureAbortsConstruction() {
        Path target = recoveryFile("startup-failure");
        CrashRecoveryService.RecoveryStoreFactory failingFactory =
                (path, codec, executor, success, failure) -> new AtomicYamlStore<>(path,
                        new AtomicYamlStore.SnapshotCodec<>() {
                            @Override
                            public byte[] encode(Map<String, Object> snapshot) throws IOException {
                                throw new IOException("forced startup failure");
                            }

                            @Override
                            public Map<String, Object> decodeStrict(byte[] encoded) throws IOException {
                                return codec.decodeStrict(encoded);
                            }
                        }, executor, success, failure);

        assertThrows(IllegalStateException.class, () -> new CrashRecoveryService(
                plugin(), target, DIRECT, false, System::currentTimeMillis, failingFactory));
    }

    @Test
    void copySessionSchemaRoundTripsSelectionMaskAndActualPlayerUuid() throws Exception {
        Path target = recoveryFile("session-round-trip");
        UUID playerId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Selection selection = new Selection(
                new Location(world, 1.25, 2.5, 3.75),
                new Location(world, 8.0, 9.0, 10.0),
                SelectionType.CUBOID);
        BlockMask mask = new BlockMask("stone", List.of(), List.of(Material.STONE));

        CrashRecoveryService writer = service(target);
        writer.startSession(playerId, "copy", "starting",
                Map.of("selection", selection, "mask", mask), ignored -> { });
        assertTrue(writer.flushRecovery());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            CrashRecoveryService reader = service(target);
            CrashRecoveryService.ActiveCommandSession restored = reader.getSession(playerId);
            assertNotNull(restored);
            assertEquals(playerId, restored.playerId());
            assertEquals("copy", restored.command());
            Selection restoredSelection = assertInstanceOf(Selection.class, restored.data().get("selection"));
            assertEquals(1.25, restoredSelection.getPos1().getX());
            assertEquals(SelectionType.CUBOID, restoredSelection.getType());
            assertEquals("stone", assertInstanceOf(BlockMask.class, restored.data().get("mask")).getRaw());
        }
    }

    @Test
    void sessionReplacementIsTransactionalAndSmallerDataRemovesStaleKeys() throws Exception {
        Path target = recoveryFile("session-transaction");
        UUID playerId = UUID.randomUUID();
        CrashRecoveryService service = service(target);
        service.startSession(playerId, "copy", "starting", Map.of("first", "kept", "stale", 7), null);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateSession(playerId, "bad", Map.of("unsupported", new Object())));
        assertEquals("starting", service.getSession(playerId).stage());
        assertEquals("kept", service.getSession(playerId).data().get("first"));

        service.updateSession(playerId, "smaller", Map.of("first", "new"));
        assertTrue(service.flushRecovery());
        String persisted = Files.readString(target);
        assertTrue(persisted.contains("first: new"));
        assertFalse(persisted.contains("stale:"));
        assertFalse(persisted.contains("unsupported:"));
    }

    @Test
    void mutationDuringQueuedWriteKeepsNewerDirtyRevisionUntilPersisted() {
        Path target = recoveryFile("dirty-revision");
        QueuedExecutor executor = new QueuedExecutor();
        UUID playerId = UUID.randomUUID();
        CrashRecoveryService service = new CrashRecoveryService(
                plugin(), target, executor, false, System::currentTimeMillis,
                CrashRecoveryService.systemStoreFactory());
        executor.runAll(); // stale startup reservation is harmless after constructor flush steals it

        service.startSession(playerId, "copy", "one", Map.of("value", 1), null);
        long firstDirty = service.dirtyRevision(playerId);
        service.updateSession(playerId, "two", Map.of("value", 2));
        long secondDirty = service.dirtyRevision(playerId);
        assertTrue(secondDirty > firstDirty);

        executor.runNext();
        assertEquals(secondDirty, service.dirtyRevision(playerId));
        executor.runAll();
        assertEquals(-1L, service.dirtyRevision(playerId));
    }

    @Test
    void cleanupRemovalGetsARevisionAndPersists() throws Exception {
        Path target = recoveryFile("cleanup");
        AtomicLong clock = new AtomicLong(1_000L);
        UUID playerId = UUID.randomUUID();
        CrashRecoveryService service = new CrashRecoveryService(
                plugin(), target, DIRECT, false, clock::get, CrashRecoveryService.systemStoreFactory());
        service.startSession(playerId, "copy", "old", Map.of(), null);
        clock.addAndGet(24L * 60L * 60L * 1_000L + 1L);

        service.cleanupOldSessions();
        assertNull(service.getSession(playerId));
        assertTrue(service.flushRecovery());
        assertFalse(Files.readString(target).contains(playerId.toString()));
    }

    @Test
    void disableIsIdempotentAndCannotUpgradeFailClosedFirstCall() throws Exception {
        Path falseTarget = recoveryFile("disable-false");
        CrashRecoveryService falseService = service(falseTarget);
        assertFalse(falseService.disable(false));
        assertFalse(falseService.disable(true));
        assertTrue(Files.readString(falseTarget).contains("lifecycleState: RUNNING"));

        Path cleanTarget = recoveryFile("disable-clean");
        CrashRecoveryService cleanService = service(cleanTarget);
        assertTrue(cleanService.disable(true));
        assertTrue(cleanService.disable(true));
        assertTrue(Files.readString(cleanTarget).contains("lifecycleState: CLEAN"));

        Path adapterTarget = recoveryFile("disable-adapter");
        CrashRecoveryService adapter = service(adapterTarget);
        adapter.disable();
        assertTrue(Files.readString(adapterTarget).contains("lifecycleState: RUNNING"));
    }

    private CrashRecoveryService service(Path target) {
        return new CrashRecoveryService(plugin(), target, DIRECT, false,
                System::currentTimeMillis, CrashRecoveryService.systemStoreFactory());
    }

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CrashRecoveryStateTest"));
        return plugin;
    }

    private Path recoveryFile(String name) {
        return temporaryDirectory.resolve(name).resolve("crash-recovery.yml");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static final class QueuedExecutor implements Executor {
        private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runNext() {
            tasks.removeFirst().run();
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }
    }
}
