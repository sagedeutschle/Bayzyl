package com.bayzyl;

import org.bukkit.Material;

import java.util.List;

public record PatternBrushSettings(
        PatternBrushMode mode,
        BlockMask fromMask,
        Material to,
        List<Material> palette,
        int size,
        double density,
        BlockMask mask
) {
}
