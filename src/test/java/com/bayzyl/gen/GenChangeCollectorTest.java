package com.bayzyl.gen;

import com.bayzyl.BlockChange;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class GenChangeCollectorTest {
    @Test
    void writingTheOriginalBlockBackOverAStagedChangeCancelsIt() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        BlockData original = mock(BlockData.class);
        BlockData stone = mock(BlockData.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(1, 70, 2)).thenReturn(block);
        when(block.getBlockData()).thenReturn(original);
        when(original.getMaterial()).thenReturn(Material.DIRT);
        when(stone.getMaterial()).thenReturn(Material.STONE);
        when(original.clone()).thenReturn(original);
        when(stone.clone()).thenReturn(stone);
        when(original.matches(original)).thenReturn(true);
        when(stone.matches(stone)).thenReturn(true);
        when(original.matches(stone)).thenReturn(false);
        when(stone.matches(original)).thenReturn(false);

        GenChangeCollector collector = new GenChangeCollector(world, false);
        collector.set(1, 70, 2, stone);
        assertEquals(1, collector.stagedCount());
        // A later pass in the same click restores the world's original block here.
        collector.set(1, 70, 2, original);

        assertEquals(0, collector.stagedCount());
        List<BlockChange> committed = collector.commit();
        assertTrue(committed.isEmpty());
        verify(block, never()).setBlockData(stone, false);
    }
}
