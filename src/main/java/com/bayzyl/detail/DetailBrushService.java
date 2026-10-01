package com.bayzyl.detail;

import com.bayzyl.BlockChange;
import com.bayzyl.HistoryService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class DetailBrushService {
    private final DetailBrushPresetRegistry registry;
    private final DetailBrushSafety safety;
    private final HistoryService historyService;
    private final AtomicLong stampCounter;
    private final Map<String, String> placedDetailSignatures = new ConcurrentHashMap<>();

    public DetailBrushService(DetailBrushPresetRegistry registry, DetailBrushSafety safety,
                              HistoryService historyService) {
        this(registry, safety, historyService, new AtomicLong(0));
    }

    DetailBrushService(DetailBrushPresetRegistry registry, DetailBrushSafety safety,
                       HistoryService historyService, AtomicLong stampCounter) {
        if (registry == null || safety == null || registry != safety.registry()) {
            throw new IllegalArgumentException("Detail brush service requires the shared registry and safety.");
        }
        this.registry = registry;
        this.safety = safety;
        this.historyService = historyService;
        this.stampCounter = stampCounter;
    }

    public DetailBrushPresetRegistry registry() {
        return registry;
    }

    public DetailBrushSafety safety() {
        return safety;
    }

    public ApplyResult apply(Player player, Location target, DetailBrushSettings settings) {
        try {
            settings = safety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return ApplyResult.failure(ex.getMessage());
        }
        DetailBrushPreset preset = registry.get(settings.presetId());
        if (player == null || target == null || target.getWorld() == null) {
            return ApplyResult.failure("Invalid target.");
        }
        long seed = mixSeed(player.getUniqueId().getMostSignificantBits(), stampCounter.incrementAndGet());
        List<BlockChange> changes = preset.apply(player, target, settings.parameters(), seed);
        if (changes == null) {
            changes = Collections.emptyList();
        }
        if (!changes.isEmpty()) {
            historyService.record(player.getUniqueId(), changes);
            markPlacedDetails(preset, settings, changes);
        }
        return ApplyResult.success(preset, changes.size());
    }

    public boolean isMatchingPlacedDetail(Block block, DetailBrushPreset preset, DetailBrushSettings settings) {
        try {
            settings = safety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (block == null || preset == null) {
            return false;
        }
        Material type = block.getType();
        if (!preset.transparentTargetMaterials().contains(type)) {
            return false;
        }
        String signature = placedDetailSignatures.get(blockKey(block.getLocation()));
        return brushSignature(preset, settings).equals(signature);
    }

    private void markPlacedDetails(DetailBrushPreset preset, DetailBrushSettings settings, List<BlockChange> changes) {
        String signature = brushSignature(preset, settings);
        for (BlockChange change : changes) {
            if (change.getLocation() == null || change.getAfter() == null) {
                continue;
            }
            Material afterType = change.getAfter().getMaterial();
            if (preset.transparentTargetMaterials().contains(afterType)) {
                placedDetailSignatures.put(blockKey(change.getLocation()), signature);
            }
        }
    }

    private String brushSignature(DetailBrushPreset preset, DetailBrushSettings settings) {
        DetailBrushParameters params = settings.parameters().mergeDefaults(preset.parameterSpecs());
        return preset.id() + "|" + params.serialize();
    }

    private String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static long mixSeed(long a, long b) {
        long h = a ^ (b * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }

    public record ApplyResult(boolean success, String message, DetailBrushPreset preset, int changedBlocks) {
        static ApplyResult success(DetailBrushPreset preset, int changedBlocks) {
            return new ApplyResult(true, "", preset, changedBlocks);
        }

        static ApplyResult failure(String message) {
            return new ApplyResult(false, message, null, 0);
        }
    }
}
