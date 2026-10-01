package com.bayzyl;

import com.bayzyl.persistence.AtomicYamlStore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrashRecoveryTransactionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @TempDir
    Path temporaryDirectory;

    @Test
    void failedWriteKeepsNewestDirtyRevisionUntilARetrySucceeds() throws Exception {
        Path target = temporaryDirectory.resolve("retry").resolve("crash-recovery.yml");
        QueuedExecutor executor = new QueuedExecutor();
        AtomicInteger failuresRemaining = new AtomicInteger();
        CrashRecoveryService service = new CrashRecoveryService(plugin(), target, executor, false,
                System::currentTimeMillis, flakyFactory(failuresRemaining));
        executor.runAll();

        failuresRemaining.set(1);
        service.startSession(PLAYER, "copy", "starting", Map.of("note", "kept"), null);
        long dirty = service.dirtyRevision(PLAYER);
        executor.runAll();
        assertEquals(dirty, service.dirtyRevision(PLAYER), "a failed write must not clear the dirty revision");
        assertFalse(Files.readString(target).contains("note: kept"));

        service.retryFailedWrites();
        executor.runAll();
        assertEquals(-1L, service.dirtyRevision(PLAYER));
        assertTrue(Files.readString(target).contains("note: kept"));
    }

    @Test
    void clearingAClipboardRemovesItsPayloadFromTheFile() throws Exception {
        Path target = temporaryDirectory.resolve("clipboard").resolve("crash-recovery.yml");
        CrashRecoveryService service = direct(target);
        service.saveClipboard(PLAYER, clipboard(blockData("minecraft:stone"), null));
        assertTrue(service.flushRecovery());
        String saved = Files.readString(target);
        assertTrue(saved.contains("minecraft:stone"), saved);
        assertTrue(saved.contains(PLAYER.toString()), saved);

        service.saveClipboard(PLAYER, null);
        assertTrue(service.flushRecovery());
        assertEquals(-1L, service.dirtyRevision(PLAYER));
        String cleared = Files.readString(target);
        assertFalse(cleared.contains("minecraft:stone"), cleared);
        assertFalse(cleared.contains(PLAYER.toString()), cleared);
    }

    @Test
    void clipboardAboveTheRecoveryCapIsNotPersistedAndDoesNotLeaveAStaleOne() throws Exception {
        Path target = temporaryDirectory.resolve("cap").resolve("crash-recovery.yml");
        CrashRecoveryService service = direct(target);
        service.saveClipboard(PLAYER, clipboard(blockData("minecraft:stone"), null));
        assertTrue(service.flushRecovery());
        assertTrue(Files.readString(target).contains("minecraft:stone"));

        int volume = 250_001;
        BlockData dirt = blockData("minecraft:dirt");
        BlockData[] data = new BlockData[volume];
        java.util.Arrays.fill(data, dirt);
        Clipboard huge = new Clipboard(volume, 1, 1, data, new BlockState[volume], List.of(), location(0, 0, 0), 0, 0, 0);
        service.saveClipboard(PLAYER, huge);
        assertTrue(service.flushRecovery());
        String saved = Files.readString(target);
        assertFalse(saved.contains("minecraft:dirt"), "an over-cap clipboard must not be persisted");
        assertFalse(saved.contains("minecraft:stone"), "the superseded clipboard must not survive either");
    }

    @Test
    void maskedCellsArePersistedAsSkipAndNeverAsAir() throws Exception {
        Path target = temporaryDirectory.resolve("skip").resolve("crash-recovery.yml");
        CrashRecoveryService service = direct(target);
        service.saveClipboard(PLAYER, clipboard(blockData("minecraft:stone"), null));
        assertTrue(service.flushRecovery());
        String saved = Files.readString(target);
        assertTrue(saved.contains("bayzyl:skip"), saved);
        assertFalse(saved.contains("minecraft:air"), saved);
    }

    @Test
    void nudgeStateIsRevisionedAndRemovable() throws Exception {
        Path target = temporaryDirectory.resolve("nudge").resolve("crash-recovery.yml");
        CrashRecoveryService service = direct(target);
        Clipboard clipboard = clipboard(blockData("minecraft:oak_planks"), blockData("minecraft:oak_planks"));
        Selection selection = new Selection(location(0, 0, 0), location(1, 0, 0), SelectionType.CUBOID);
        service.saveNudgeSession(PLAYER, new EditService.NudgeSession(clipboard, selection, new ConcurrentHashMap<>()));
        assertTrue(service.flushRecovery());
        assertTrue(Files.readString(target).contains("minecraft:oak_planks"));

        service.saveNudgeSession(PLAYER, null);
        assertTrue(service.flushRecovery());
        assertFalse(Files.readString(target).contains("minecraft:oak_planks"));
    }

    private CrashRecoveryService direct(Path target) {
        return new CrashRecoveryService(plugin(), target, Runnable::run, false, System::currentTimeMillis,
                CrashRecoveryService.systemStoreFactory());
    }

    private static CrashRecoveryService.RecoveryStoreFactory flakyFactory(AtomicInteger failuresRemaining) {
        return (path, codec, executor, success, failure) -> new AtomicYamlStore<>(path,
                new AtomicYamlStore.SnapshotCodec<>() {
                    @Override
                    public byte[] encode(Map<String, Object> snapshot) throws IOException {
                        if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            throw new IOException("disk full");
                        }
                        return codec.encode(snapshot);
                    }

                    @Override
                    public Map<String, Object> decodeStrict(byte[] encoded) throws IOException {
                        return codec.decodeStrict(encoded);
                    }
                }, executor, success, failure);
    }

    static Clipboard clipboard(BlockData first, BlockData second) {
        return new Clipboard(2, 1, 1, new BlockData[]{first, second}, new BlockState[2], List.of(),
                location(0, 64, 0), 0, 0, 0);
    }

    static BlockData blockData(String asString) {
        BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn(asString);
        when(data.clone()).thenReturn(data);
        return data;
    }

    static World world() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        return world;
    }

    private static final World WORLD = world();

    static Location location(double x, double y, double z) {
        return new Location(WORLD, x, y, z);
    }

    private JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CrashRecoveryTransactionTest"));
        return plugin;
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
    }
}
