package com.bayzyl;

import org.bukkit.Material;

public record PaintBrushSettings(
        Material material,
        int size,
        double density,
        BlockMask mask
) {
}
