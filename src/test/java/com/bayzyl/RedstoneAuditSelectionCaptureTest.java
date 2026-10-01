package com.bayzyl;

import com.bayzyl.redstone.AuditPosition;
import com.bayzyl.redstone.RedstoneAuditSnapshotFactory.Capture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedstoneAuditSelectionCaptureTest {
    private final UUID playerId = UUID.randomUUID();

    @Test
    void incompleteSelectionIsRefusedWithoutTouchingTheWorld() {
        World world = world();
        SelectionManager selections = new SelectionManager();
        selections.setPos1(playerId, new Location(world, 0, 64, 0));

        Capture capture = new RedstoneAuditSelectionCapture(selections).capture(player());

        assertNull(capture.snapshot());
        assertNotNull(capture.refusal());
        verify(world, never()).getBlockData(0, 64, 0);
    }

    @Test
    void capturesTheSelectionAndMarksItemFramesReadable() {
        World world = world();
        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getLocation()).thenReturn(new Location(world, 1.5, 64.5, 0.5));
        Entity other = mock(Entity.class);
        when(other.getLocation()).thenReturn(new Location(world, 0.5, 64.5, 0.5));
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.of(frame, other));
        SelectionManager selections = new SelectionManager();
        selections.setCuboid(playerId, new Location(world, 0, 64, 0), new Location(world, 2, 64, 0));

        Capture capture = new RedstoneAuditSelectionCapture(selections).capture(player());

        assertNotNull(capture.snapshot());
        assertTrue(capture.snapshot().at(new AuditPosition(1, 64, 0)).readable());
        assertTrue(capture.snapshot().at(new AuditPosition(0, 64, 0)).isEmpty());
        verify(world).getBlockData(0, 64, 0);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }

    private static World world() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }
}
