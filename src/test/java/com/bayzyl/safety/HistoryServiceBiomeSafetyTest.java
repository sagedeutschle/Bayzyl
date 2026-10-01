package com.bayzyl;

import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
final class HistoryServiceBiomeSafetyTest {
    @Test
    void combinedHistoryChecksTotalBeforeReadingEntries() {
        List<BiomeChange> blockBiomes = poisonList(600_000);
        List<BiomeColumnChange> columnBiomes = poisonList(400_001);

        WorkEstimate estimate = BiomeCommandUtil.assessHistory(blockBiomes, columnBiomes);

        assertTrue(estimate.hardRejected());
        assertTrue(estimate.reason().contains("hard maximum"), estimate.reason());
    }

    @Test
    void combinedHistorySharesOne4096ChunkBudgetAcrossBlockAndColumnChanges() {
        World world = mock(World.class);
        List<BiomeChange> blockBiomes = blockBiomes(world, 0, 2_048);

        WorkEstimate exact = BiomeCommandUtil.assessHistory(
                blockBiomes, columnBiomes(world, 2_048, 2_048));
        WorkEstimate over = BiomeCommandUtil.assessHistory(
                blockBiomes, columnBiomes(world, 2_048, 2_049));

        assertFalse(exact.hardRejected(), exact.reason());
        assertEquals(4_096L, exact.workUnits());
        assertTrue(over.hardRejected());
        assertTrue(over.reason().contains("Biome chunk footprint"), over.reason());
    }

    @Test
    void combinedHistoryRejectsMalformedListsAndColumnEntries() {
        World world = mock(World.class);
        List<List<BiomeColumnChange>> malformedColumns = List.of(
                Collections.singletonList(null),
                List.of(new BiomeColumnChange(null, 0, 0, Biome.PLAINS, Biome.DESERT)),
                List.of(new BiomeColumnChange(world, 0, 0, null, Biome.DESERT)),
                List.of(new BiomeColumnChange(world, 0, 0, Biome.PLAINS, null))
        );

        assertTrue(BiomeCommandUtil.assessHistory(null, List.of()).hardRejected());
        assertTrue(BiomeCommandUtil.assessHistory(List.of(), null).hardRejected());
        for (List<BiomeColumnChange> columns : malformedColumns) {
            assertTrue(BiomeCommandUtil.assessHistory(List.of(), columns).hardRejected());
        }
    }

    @Test
    void undoRejects4097ChunkActionBeforePopOrAnyMutation() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        World world = mock(World.class);
        BlockChange block = mock(BlockChange.class);
        EntityChange entity = mock(EntityChange.class);
        EditAction action = actionWithPoisonMutations(block, entity, columnBiomes(world, 0, 4_097));
        EditHistory history = new EditHistory();
        history.push(playerId, action);

        int undone = service(history).undo(player, 1);

        assertEquals(0, undone);
        assertSame(action, history.peekUndo(playerId));
        assertNull(history.peekRedo(playerId));
        verify(entity, never()).undo();
        verify(block, never()).getLocation();
        verify(world, never()).setBiome(anyInt(), anyInt(), eq(Biome.PLAINS));
    }

    @Test
    void undoAllowsExact4096ChunkActionAndMovesItToRedo() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        World world = mock(World.class);
        EditAction action = action(columnBiomes(world, 0, 4_096));
        EditHistory history = new EditHistory();
        history.push(playerId, action);

        int undone = service(history).undo(player, 1);

        assertEquals(1, undone);
        assertNull(history.peekUndo(playerId));
        assertSame(action, history.peekRedo(playerId));
        verify(world, times(4_096)).setBiome(anyInt(), anyInt(), eq(Biome.PLAINS));
    }

    @Test
    void redoRejects4097ChunkActionBeforePopOrAnyMutation() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        World world = mock(World.class);
        BlockChange block = mock(BlockChange.class);
        EntityChange entity = mock(EntityChange.class);
        EditAction action = actionWithPoisonMutations(block, entity, columnBiomes(world, 0, 4_097));
        EditHistory history = new EditHistory();
        history.restore(playerId, List.of(), List.of(action));

        int redone = service(history).redo(player, 1);

        assertEquals(0, redone);
        assertSame(action, history.peekRedo(playerId));
        assertNull(history.peekUndo(playerId));
        verify(entity, never()).redo();
        verify(block, never()).getLocation();
        verify(world, never()).setBiome(anyInt(), anyInt(), eq(Biome.DESERT));
    }

    @Test
    void redoAllowsExact4096ChunkActionAndMovesItToUndo() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        World world = mock(World.class);
        EditAction action = action(columnBiomes(world, 0, 4_096));
        EditHistory history = new EditHistory();
        history.restore(playerId, List.of(), List.of(action));

        int redone = service(history).redo(player, 1);

        assertEquals(1, redone);
        assertNull(history.peekRedo(playerId));
        assertSame(action, history.peekUndo(playerId));
        verify(world, times(4_096)).setBiome(anyInt(), anyInt(), eq(Biome.DESERT));
    }

    @Test
    void acceptedAsyncUndoRestoresBothBiomeFormsOnceAfterFinalBlockSlice() {
        AsyncUndoFixture fixture = asyncUndoFixture();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString())).thenReturn(true);

            assertEquals(1, fixture.service().undo(fixture.player(), 1));
            assertSame(fixture.action(), fixture.history().peekRedo(fixture.playerId()));
            assertTrue(fixture.service().hasAsyncHistoryTask(fixture.playerId()));

            fixture.scheduler().runSlice();

            verify(fixture.secondBlock()).setBlockData(fixture.before(), false);
            verify(fixture.firstBlock(), never()).setBlockData(fixture.before(), false);
            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString()), never());
            verify(fixture.world(), never()).setBiome(16, 0, Biome.PLAINS);
            verify(fixture.persistence(), never()).save(eq(fixture.playerId()), anyList(), anyList());

            fixture.scheduler().runSlice();

            verify(fixture.firstBlock()).setBlockData(fixture.before(), false);
            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), contains("minecraft:plains")), times(1));
            verify(fixture.world(), times(1)).setBiome(16, 0, Biome.PLAINS);
            verify(fixture.persistence(), times(1)).save(eq(fixture.playerId()), anyList(), anyList());
            assertFalse(fixture.service().hasAsyncHistoryTask(fixture.playerId()));
            assertTrue(fixture.scheduler().cancelled());

            fixture.scheduler().runSlice();
            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString()), times(1));
            verify(fixture.world(), times(1)).setBiome(16, 0, Biome.PLAINS);
        }
    }

    @Test
    void cancellingAsyncUndoBeforeFinalBlockLeavesBiomesUntouched() {
        AsyncUndoFixture fixture = asyncUndoFixture();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString())).thenReturn(true);

            assertEquals(1, fixture.service().undo(fixture.player(), 1));
            fixture.scheduler().runSlice();
            fixture.service().cancelAllAsyncTasks();
            fixture.scheduler().runSlice();

            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString()), never());
            verify(fixture.world(), never()).setBiome(16, 0, Biome.PLAINS);
            assertFalse(fixture.service().hasAsyncHistoryTask(fixture.playerId()));
            assertTrue(fixture.scheduler().cancelled());
        }
    }

    @Test
    void acceptedAsyncRedoAppliesBothBiomeFormsOnceAfterFinalBlockSlice() {
        AsyncUndoFixture fixture = asyncUndoFixture();
        assertSame(fixture.action(), fixture.history().popUndo(fixture.playerId()));
        assertNull(fixture.history().peekUndo(fixture.playerId()));
        assertSame(fixture.action(), fixture.history().peekRedo(fixture.playerId()));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString())).thenReturn(true);

            assertEquals(1, fixture.service().redo(fixture.player(), 1));
            assertSame(fixture.action(), fixture.history().peekUndo(fixture.playerId()));
            assertNull(fixture.history().peekRedo(fixture.playerId()));
            assertTrue(fixture.service().hasAsyncHistoryTask(fixture.playerId()));

            fixture.scheduler().runSlice();

            verify(fixture.firstBlock()).setBlockData(fixture.after(), false);
            verify(fixture.secondBlock(), never()).setBlockData(fixture.after(), false);
            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString()), never());
            verify(fixture.world(), never()).setBiome(16, 0, Biome.DESERT);
            verify(fixture.persistence(), never()).save(eq(fixture.playerId()), anyList(), anyList());

            fixture.scheduler().runSlice();

            verify(fixture.secondBlock()).setBlockData(fixture.after(), false);
            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), contains("minecraft:desert")), times(1));
            verify(fixture.world(), times(1)).setBiome(16, 0, Biome.DESERT);
            verify(fixture.persistence(), times(1)).save(eq(fixture.playerId()), anyList(), anyList());
            assertFalse(fixture.service().hasAsyncHistoryTask(fixture.playerId()));
            assertTrue(fixture.scheduler().cancelled());

            fixture.scheduler().runSlice();
            bukkit.verify(() -> Bukkit.dispatchCommand(
                    nullable(CommandSender.class), anyString()), times(1));
            verify(fixture.world(), times(1)).setBiome(16, 0, Biome.DESERT);
        }
    }

    private static HistoryService service(EditHistory history) {
        return new HistoryService(history, new SelectionManager(),
                mock(PersistentEditHistoryService.class), mock(JavaPlugin.class));
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }

    private static AsyncUndoFixture asyncUndoFixture() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        when(player.isOnline()).thenReturn(true);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        BlockData before = mock(BlockData.class);
        BlockData after = mock(BlockData.class);
        Block firstBlock = mock(Block.class);
        Block secondBlock = mock(Block.class);
        Location firstLocation = mock(Location.class);
        Location secondLocation = mock(Location.class);
        when(firstLocation.getBlock()).thenReturn(firstBlock);
        when(secondLocation.getBlock()).thenReturn(secondBlock);

        List<BlockChange> blocks = List.of(
                new BlockChange(firstLocation, before, after),
                new BlockChange(secondLocation, before, after));
        List<BiomeChange> blockBiomes = List.of(
                new BiomeChange(new Location(world, 0, 64, 0), Biome.PLAINS, Biome.DESERT));
        List<BiomeColumnChange> columnBiomes = List.of(
                new BiomeColumnChange(world, 16, 0, Biome.PLAINS, Biome.DESERT));
        EditAction action = new EditAction(
                blocks, List.of(), blockBiomes, columnBiomes, null, null);
        EditHistory history = new EditHistory();
        history.push(playerId, action);

        PersistentEditHistoryService persistence = mock(PersistentEditHistoryService.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        CapturingScheduler scheduler = new CapturingScheduler();
        HistoryService service = new HistoryService(
                history,
                new SelectionManager(),
                persistence,
                plugin,
                scheduler,
                ignored -> player,
                new HistoryService.TaskPolicy(1L, 1, Long.MAX_VALUE));
        return new AsyncUndoFixture(
                playerId, player, world, before, after, firstBlock, secondBlock,
                action, history, persistence, scheduler, service);
    }

    private static EditAction action(List<BiomeColumnChange> columns) {
        return new EditAction(List.of(), List.of(), List.of(), columns, null, null);
    }

    private static EditAction actionWithPoisonMutations(
            BlockChange block, EntityChange entity, List<BiomeColumnChange> columns
    ) {
        return new EditAction(List.of(block), List.of(entity), List.of(), columns, null, null);
    }

    private static List<BiomeChange> blockBiomes(World world, int startChunk, int count) {
        List<BiomeChange> changes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int x = (startChunk + index) * 16;
            changes.add(new BiomeChange(new Location(world, x, 64, 0), Biome.PLAINS, Biome.DESERT));
        }
        return changes;
    }

    private static List<BiomeColumnChange> columnBiomes(World world, int startChunk, int count) {
        List<BiomeColumnChange> changes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int x = (startChunk + index) * 16;
            changes.add(new BiomeColumnChange(world, x, 0, Biome.PLAINS, Biome.DESERT));
        }
        return changes;
    }

    private static <T> List<T> poisonList(int size) {
        return new AbstractList<>() {
            @Override
            public T get(int index) {
                throw new AssertionError("entries must not be read before combined count rejection");
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    private record AsyncUndoFixture(
            UUID playerId,
            Player player,
            World world,
            BlockData before,
            BlockData after,
            Block firstBlock,
            Block secondBlock,
            EditAction action,
            EditHistory history,
            PersistentEditHistoryService persistence,
            CapturingScheduler scheduler,
            HistoryService service
    ) {
    }

    private static final class CapturingScheduler implements HistoryService.TickScheduler {
        private Runnable runnable;
        private boolean cancelled;

        @Override
        public HistoryService.TaskHandle scheduleRepeating(Runnable task, long delayTicks, long periodTicks) {
            runnable = task;
            return () -> cancelled = true;
        }

        void runSlice() {
            if (!cancelled) {
                runnable.run();
            }
        }

        boolean cancelled() {
            return cancelled;
        }
    }
}
