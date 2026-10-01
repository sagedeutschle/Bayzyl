package com.bayzyl.generation;

import org.bukkit.block.Biome;

public record GenerateBiomeRequest(
        Biome biome,
        BiomeGeneratorMode mode,
        String expression,
        CoordinateMode coordinateMode,
        boolean hollow,
        boolean confirm,
        boolean preview,
        Integer radius,
        Integer radiusX,
        Integer radiusY,
        Integer radiusZ,
        Integer height,
        Integer size
) {
}
