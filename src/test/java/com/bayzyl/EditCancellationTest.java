package com.bayzyl;

import com.bayzyl.edit.EditAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EditCancellationTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final UUID id = UUID.randomUUID();
    private final HistoryService history = mock(HistoryService.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final BukkitTask scheduled = mock(BukkitTask.class);
    private final EditAdapter adapter = mock(EditAdapter.class);
    private final EditService service = new EditService(plugin, adapter,
            mock(ClipboardManager.class), history, mock(SelectionManager.class));
    private final Clipboard clipboard;

    EditCancellationTest() {
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        BlockData stone = mock(BlockData.class);
        when(stone.clone()).thenReturn(stone);
        when(stone.getMaterial()).thenReturn(Material.STONE);
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(stone);
        Material solid = mock(Material.class);
        when(solid.isAir()).thenReturn(false);
        when(block.getType()).thenReturn(solid);
        when(block.getLocation()).thenReturn(new Location(world, 100, 64, 100));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(scheduled);
        clipboard = new Clipboard(1, 1, 1, new BlockData[]{stone}, new org.bukkit.block.BlockState[1],
                List.of(), new Location(world, 0, 0, 0), 1, 0, 0);
    }

    @Test
    void cancelPasteRecordsAlreadyPlacedBlocksExactlyOnce() throws Exception {
        withPartialPaste(0, () -> {
            service.cancelPasteTask(id);
            service.cancelPasteTask(id);
            verifyRecordedOnce();
            verify(scheduled).cancel();
            assertFalse(service.hasPasteTask(id));
        });
    }

    @Test
    void disableRecordsAlreadyPlacedBlocksExactlyOnce() throws Exception {
        withPartialPaste(0, () -> {
            service.cancelAllAsyncTasks();
            service.cancelAllAsyncTasks();
            verifyRecordedOnce();
            verify(scheduled).cancel();
            assertFalse(service.hasPasteTask(id));
        });
    }

    @Test
    void negativeQuarterTurnRotatesTheBlockPosition() throws Exception {
        withPartialPaste(-90, () -> {
            verify(world).getBlockAt(100, 64, 99);
            service.cancelPasteTask(id);
        });
    }

    @Test
    void cutRefusesToRemoveAnythingWhenItsCopyWasRefused() {
        Selection selection = new Selection(new Location(world, 0, 64, 0),
                new Location(world, 0, 64, 0), SelectionType.CUBOID);
        assertEquals(EditService.REFUSED, service.cutSelection(player, selection, BlockMask.parse(null), true));
        verify(adapter, never()).cutSelection(any(), any(), any());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void cancelledChunkedCutRecordsExactlyTheAppliedBlocks() {
        Selection selection = new Selection(new Location(world, 0, 64, 0),
                new Location(world, 599, 64, 599), SelectionType.CUBOID);
        when(adapter.copySelection(any(), any(), any())).thenReturn(clipboard);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            assertEquals(EditService.DEFERRED, service.cutSelection(player, selection, BlockMask.parse(null), true));
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(1L), eq(1L));
            task.getValue().run();
            assertTrue(service.hasPasteTask(id));
            service.cancelPasteTask(id);
            service.cancelAllAsyncTasks();
            ArgumentCaptor<List<BlockChange>> changes = ArgumentCaptor.forClass(List.class);
            verify(history).record(eq(id), changes.capture(), eq(List.of()), isNull(), isNull());
            assertFalse(changes.getValue().isEmpty());
            Block block = world.getBlockAt(0, 64, 0);
            verify(block, times(changes.getValue().size())).setType(Material.AIR, false);
        }
    }

    @Test
    void historyFailureStopsTheTaskAndIsReportedToShutdown() throws Exception {
        withPartialPaste(0, () -> {
            doThrow(new IllegalStateException("history unavailable")).when(history)
                    .record(eq(id), anyList(), anyList(), isNull(), isNull());
            assertThrows(IllegalStateException.class, service::cancelAllAsyncTasks);
            verify(scheduled).cancel();
            assertFalse(service.hasPasteTask(id));
        });
    }

    @Test
    void cleanPasteCompletionRecordsOnlyOnce() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            assertTrue(service.pasteClipboardAsync(player, clipboard, new Location(world, 100, 64, 100), 0, false, false));
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(1L), eq(1L));
            task.getValue().run();
            service.cancelAllAsyncTasks();
            verifyRecordedOnce();
            assertFalse(service.hasPasteTask(id));
        }
    }

    private void withPartialPaste(int rotation, Runnable assertions) throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertTrue(service.pasteClipboardAsync(player, clipboard, new Location(world, 100, 64, 100),
                    rotation, false, false));
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(1L), eq(1L));
            // Pause at a deterministic point between a block write and task completion.
            Method process = task.getValue().getClass().getDeclaredMethod("processBlock", int.class, int.class, int.class);
            process.setAccessible(true);
            process.invoke(task.getValue(), 0, 0, 0);
            assertions.run();
        }
    }

    private void verifyRecordedOnce() {
        ArgumentCaptor<List<BlockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(history).record(eq(id), changes.capture(), eq(List.of()), isNull(), isNull());
        assertEquals(1, changes.getValue().size());
    }
}
