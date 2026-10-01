package com.bayzyl.detail;

import com.bayzyl.BlockChange;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

public interface DetailBrushPreset {
    String id();

    String displayName();

    DetailBrushFamily family();

    List<DetailBrushParameterSpec> parameterSpecs();

    List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed);

    default Set<Material> transparentTargetMaterials() {
        return Set.of();
    }

    default DetailBrushParameters defaults() {
        return DetailBrushParameters.empty().mergeDefaults(parameterSpecs());
    }

    /**
     * Minimum ticks between successive held-use stamps for this preset.
     * Heavier presets (linear bolts, surface coverage) override this upward.
     * NOTE: defaults are first-pass; revisit during alpha 0.1.1 playtest.
     */
    default int stampCooldownTicks() {
        return 2;
    }

    /**
     * Rough block-count estimate for one stamp at the given parameters.
     * Used by /detailbrush info and the bind action-bar so builders can size
     * a brush before the first click. Heuristic, not exact.
     */
    default int estimatedStampBlockCount(DetailBrushParameters parameters) {
        return -1;
    }
}
