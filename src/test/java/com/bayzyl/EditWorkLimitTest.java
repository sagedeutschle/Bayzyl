package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class EditWorkLimitTest {
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final BlockDistribution distribution = mock(BlockDistribution.class);
    private final List<Integer> touchedY = new ArrayList<>();

    EditWorkLimitTest() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        BlockData data = mock(BlockData.class);
        when(data.clone()).thenReturn(data);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            touchedY.add(invocation.getArgument(1));
            if (touchedY.size() > 1000) throw new AssertionError("Unbounded world reads");
            if (touchedY.getLast() < -64 || touchedY.getLast() >= 320) throw new AssertionError("Read outside world height");
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(Material.STONE);
            when(block.getBlockData()).thenReturn(data);
            return block;
        });
    }

    private Selection selection(int x2, int y1, int y2, int z2) {
        return new Selection(new Location(world, 0, y1, 0), new Location(world, x2, y2, z2), SelectionType.CUBOID);
    }

    @Test
    void setBlocksNeverTouchesYOutsideTheWorld() {
        Selection selection = selection(0, -100_000, 100_000, 0);

        List<BlockChange> changes = EditUtil.setBlocks(player, selection, distribution,
                BlockMask.parse(null), "any");

        assertEquals(384, changes.size());
        assertTrue(touchedY.stream().allMatch(y -> y >= -64 && y <= 319), "touched y " + touchedY.stream().min(Integer::compare) + ".." + touchedY.stream().max(Integer::compare));
    }

    @Test
    void oversizedSelectionsAreRefusedBeforeAnyBlockIsRead() {
        Selection selection = selection(4_000, 0, 10, 4_000);

        assertTrue(EditUtil.setBlocks(player, selection, distribution, BlockMask.parse(null), "any").isEmpty());
        assertTrue(EditUtil.replaceBlocks(player, selection, BlockMask.parse(null), distribution, BlockMask.parse(null)).isEmpty());
        assertTrue(EditUtil.makeWalls(player, selection, distribution, BlockMask.parse(null)).isEmpty());
        assertTrue(EditUtil.smoothSelection(player, selection, 1).isEmpty());
        assertNull(EditUtil.copySelection(player, selection, BlockMask.parse(null)));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test
    void smoothRefusesAbsurdIterationCounts() {
        Selection selection = selection(8, 60, 70, 8);

        assertTrue(EditUtil.smoothSelection(player, selection, Integer.MAX_VALUE).isEmpty());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
}
