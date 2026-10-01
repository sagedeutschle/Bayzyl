package com.bayzyl.generation;

import org.bukkit.Material;

public record GenerateShapeRequest(
        Material material,
        String expression,
        CoordinateMode coordinateMode,
        boolean hollow,
        boolean confirm
) {
}
