package com.bayzyl.detail;

import com.bayzyl.HistoryService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class DetailBrushExecutionSafetyTest {
    @Test
    void directServiceRejectsBeforePlayerTargetCounterPresetCallbackOrHistory() {
        DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
        DetailBrushSafety safety = new DetailBrushSafety(registry);
        HistoryService history = mock(HistoryService.class);
        AtomicLong counter = new AtomicLong();
        DetailBrushService service = new DetailBrushService(registry, safety, history, counter);
        Player player = mock(Player.class);
        Location target = mock(Location.class);
        DetailBrushSettings invalid = new DetailBrushSettings(
                "flame", new DetailBrushParameters(Map.of("heat", "NaN")), DetailBrushMode.STROKE);

        DetailBrushService.ApplyResult result = service.apply(player, target, invalid);

        assertFalse(result.success());
        assertEquals(0L, counter.get());
        verifyNoInteractions(player, target, history);
    }

    @Test
    void matchingCheckRejectsInvalidSettingsBeforeReadingBlock() {
        DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
        DetailBrushSafety safety = new DetailBrushSafety(registry);
        DetailBrushService service = new DetailBrushService(
                registry, safety, mock(HistoryService.class), new AtomicLong());
        Block block = mock(Block.class);
        DetailBrushSettings invalid = new DetailBrushSettings(
                "cloud", new DetailBrushParameters(Map.of("tint", "invisible")), DetailBrushMode.STAMP);

        assertFalse(service.isMatchingPlacedDetail(block, registry.get("cloud"), invalid));
        verifyNoInteractions(block);
    }

    @Test
    void serviceRequiresTheExactRegistryOwnedBySafety() {
        DetailBrushPresetRegistry first = new DetailBrushPresetRegistry();
        DetailBrushPresetRegistry second = new DetailBrushPresetRegistry();
        assertThrows(IllegalArgumentException.class, () -> new DetailBrushService(
                first, new DetailBrushSafety(second), mock(HistoryService.class)));
    }

    @Test
    void directServiceUsesCanonicalWhitespaceTrimmedVineSpecies() {
        DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
        DetailBrushSafety safety = new DetailBrushSafety(registry);
        HistoryService history = mock(HistoryService.class);
        AtomicLong counter = new AtomicLong();
        DetailBrushService service = new DetailBrushService(registry, safety, history, counter);
        Player player = mock(Player.class);
        Location target = mock(Location.class);
        World world = mock(World.class);
        Block block = mock(Block.class);
        BlockData data = mock(BlockData.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(target.getWorld()).thenReturn(world);
        when(target.getBlockY()).thenReturn(10);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getBlockAt(0, 9, 0)).thenReturn(block);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getBlockData()).thenReturn(data);
        when(data.clone()).thenReturn(data);
        when(data.matches(data)).thenReturn(true);
        DetailBrushSettings spacedRoot = new DetailBrushSettings(
                "vine", new DetailBrushParameters(Map.of(
                "length", "1", "droop", "0", "leaf_density", "0", "gap", "0",
                "species", "  Mangrove_Root  ")), DetailBrushMode.STROKE);

        DetailBrushService.ApplyResult result = service.apply(player, target, spacedRoot);

        assertEquals(1L, counter.get());
        assertEquals("vine", result.preset().id());
        verify(block).setType(org.mockito.ArgumentMatchers.argThat(material ->
                material == Material.MANGROVE_ROOTS || material == Material.MUDDY_MANGROVE_ROOTS),
                org.mockito.ArgumentMatchers.eq(false));
        verifyNoInteractions(history);
    }
}
