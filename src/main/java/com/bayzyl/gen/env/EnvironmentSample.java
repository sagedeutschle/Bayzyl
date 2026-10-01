package com.bayzyl.gen.env;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.List;

/**
 * Snapshot of the world around the brush target. {@link EnvironmentProbe}
 * fills one of these before any generator runs so the generator can
 * choose materials and behavior that match what's already there — the
 * "looks like it has always been there" property the user asked for.
 *
 * Generators should treat every field as advisory; a missing value
 * (null / 0 / empty) means the probe could not characterize that aspect
 * of the terrain and the generator should fall back to a sane vanilla
 * default.
 */
public record EnvironmentSample(
        Biome biome,
        BiomeFamily family,
        TerrainShape shape,
        Material surface,
        Material subsurface,
        Material deep,
        Material fill,
        Material foliage,
        boolean isUnderwater,
        int waterLevel,
        int probeSurfaceY,
        int slopeBlocks,
        double openness,
        List<Material> topPalette
) {
    public boolean isFlat() {
        return shape == TerrainShape.FLAT || shape == TerrainShape.SUPERFLAT;
    }

    public boolean isMountainous() {
        return shape == TerrainShape.MOUNTAIN || shape == TerrainShape.PEAK;
    }

    public boolean isDesertLike() {
        return family == BiomeFamily.DESERT || family == BiomeFamily.BADLANDS
                || family == BiomeFamily.MESA || surface == Material.SAND
                || surface == Material.RED_SAND;
    }

    public boolean isSnowy() {
        return family == BiomeFamily.SNOWY
                || surface == Material.SNOW_BLOCK
                || surface == Material.POWDER_SNOW;
    }

    public boolean isNetherLike() {
        return family == BiomeFamily.NETHER || surface == Material.NETHERRACK
                || surface == Material.CRIMSON_NYLIUM || surface == Material.WARPED_NYLIUM
                || surface == Material.SOUL_SAND || surface == Material.SOUL_SOIL;
    }

    public boolean isEndLike() {
        return family == BiomeFamily.END || surface == Material.END_STONE;
    }

    /** Best-effort answer to "what block does a vanilla mountain put on top here?" */
    public Material cap() {
        if (surface != null && surface != Material.AIR) {
            return surface;
        }
        if (family == BiomeFamily.SNOWY) {
            return Material.SNOW_BLOCK;
        }
        return Material.GRASS_BLOCK;
    }

    /** Best-effort answer for the layer just below the cap. */
    public Material sub() {
        if (subsurface != null && subsurface != Material.AIR) {
            return subsurface;
        }
        if (isDesertLike()) {
            return Material.SANDSTONE;
        }
        if (isNetherLike()) {
            return Material.NETHERRACK;
        }
        return Material.DIRT;
    }

    /** Best-effort answer for deep fill (stone heart of the hill). */
    public Material core() {
        if (deep != null && deep != Material.AIR) {
            return deep;
        }
        if (isNetherLike()) {
            return Material.NETHERRACK;
        }
        if (isEndLike()) {
            return Material.END_STONE;
        }
        if (probeSurfaceY < 0) {
            return Material.DEEPSLATE;
        }
        return Material.STONE;
    }
}
