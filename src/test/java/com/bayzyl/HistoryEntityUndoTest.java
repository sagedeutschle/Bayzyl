package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class HistoryEntityUndoTest {
    @Test
    void undoRemovesCreatedEntitiesBeforeBlocksAndRespawnsDeletedEntitiesAfterThem() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        Location where = new Location(world, 1, 64, 1);

        Block block = mock(Block.class);
        Location blockLocation = mock(Location.class);
        when(blockLocation.getBlock()).thenReturn(block);
        BlockData before = mock(BlockData.class);
        BlockData after = mock(BlockData.class);

        EntityChange created = mock(EntityChange.class);
        when(created.getKind()).thenReturn(EntityChange.Kind.CREATE);
        when(created.getLocation()).thenReturn(where);
        EntityChange deleted = mock(EntityChange.class);
        when(deleted.getKind()).thenReturn(EntityChange.Kind.DELETE);
        when(deleted.getLocation()).thenReturn(where);

        EditHistory history = new EditHistory();
        history.push(playerId, new EditAction(
                List.of(new BlockChange(blockLocation, before, after)), List.of(created, deleted)));
        HistoryService service = new HistoryService(history, new SelectionManager(),
                mock(PersistentEditHistoryService.class), mock(JavaPlugin.class));

        assertEquals(1, service.undo(player, 1));

        InOrder order = inOrder(world, created, block, deleted);
        // Entities in an unloaded chunk are invisible to Bukkit.getEntity, so the chunk must load first.
        order.verify(world).getChunkAt(0, 0);
        order.verify(created).undo();
        order.verify(block).setBlockData(before, false);
        order.verify(deleted).undo();
    }
}
