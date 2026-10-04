package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class VisualizationManagerWorldTest {
    @Test
    void ownSelectionFromAnotherWorldIsNotDrawnInTheViewersWorld() {
        World selectionWorld = mock(World.class);
        World viewerWorld = mock(World.class);
        Player viewer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(id);
        when(viewer.getWorld()).thenReturn(viewerWorld);

        SelectionManager selections = new SelectionManager();
        selections.setCuboid(id, new Location(selectionWorld, 0, 64, 0), new Location(selectionWorld, 5, 69, 5));
        VisualizationManager visualization = new VisualizationManager(selections);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(viewer));
            visualization.render(viewer);
        }

        verify(viewer, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), any());
    }

    @Test
    void ownSelectionInTheViewersWorldIsStillDrawn() {
        World world = mock(World.class);
        Player viewer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(id);
        when(viewer.getWorld()).thenReturn(world);

        SelectionManager selections = new SelectionManager();
        selections.setCuboid(id, new Location(world, 0, 64, 0), new Location(world, 5, 69, 5));
        VisualizationManager visualization = new VisualizationManager(selections);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(viewer));
            visualization.render(viewer);
        }

        verify(viewer, atLeastOnce()).spawnParticle(any(Particle.class), any(Location.class), anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), any());
    }
}
