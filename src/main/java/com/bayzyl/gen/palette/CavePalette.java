package com.bayzyl.gen.palette;

import com.bayzyl.gen.CaveSubtype;
import com.bayzyl.gen.env.EnvironmentSample;
import org.bukkit.Material;

/**
 * Material choices for the various cave subtypes. Generators consult
 * this when deciding what to leave behind when a cave block is "carved":
 * a regular cave is just air, a lush cave drapes moss, a deep dark cave
 * stipples sculk, etc.
 */
public record CavePalette(
        Material lining,
        Material floorAccent,
        Material ceilingAccent,
        Material veinBlock,
        Material liquidBlock,
        double accentChance,
        double liquidChance
) {
    public static CavePalette forSubtype(CaveSubtype subtype, EnvironmentSample env) {
        return switch (subtype) {
            case AUTO -> auto(env);
            case NOODLE -> new CavePalette(Material.STONE, Material.STONE, Material.STONE,
                    null, null, 0.0, 0.0);
            case CHEESE -> new CavePalette(env.isNetherLike() ? Material.NETHERRACK : Material.STONE,
                    env.isNetherLike() ? Material.SOUL_SAND : Material.GRAVEL,
                    env.isNetherLike() ? Material.NETHERRACK : Material.STONE,
                    null, null, 0.08, 0.0);
            case SPAGHETTI -> new CavePalette(Material.STONE, Material.STONE, Material.STONE,
                    null, null, 0.0, 0.0);
            case DRIPSTONE -> new CavePalette(Material.DRIPSTONE_BLOCK, Material.POINTED_DRIPSTONE,
                    Material.POINTED_DRIPSTONE, Material.DRIPSTONE_BLOCK,
                    Material.WATER, 0.18, 0.05);
            case LUSH -> new CavePalette(Material.MOSS_BLOCK, Material.MOSS_CARPET,
                    Material.HANGING_ROOTS, Material.CLAY, Material.WATER, 0.32, 0.04);
            case DEEP_DARK -> new CavePalette(Material.DEEPSLATE, Material.SCULK,
                    Material.SCULK_VEIN, Material.SCULK, null, 0.22, 0.0);
            case AQUIFER -> new CavePalette(Material.STONE, Material.GRAVEL,
                    Material.STONE, null, Material.WATER, 0.0, 0.85);
            case REGULAR -> new CavePalette(Material.STONE, Material.STONE, Material.STONE,
                    null, null, 0.0, 0.0);
        };
    }

    public static CavePalette auto(EnvironmentSample env) {
        if (env.isNetherLike()) {
            return new CavePalette(Material.NETHERRACK, Material.SOUL_SAND, Material.NETHERRACK,
                    null, Material.LAVA, 0.04, 0.02);
        }
        if (env.isEndLike()) {
            return new CavePalette(Material.END_STONE, Material.END_STONE, Material.END_STONE,
                    null, null, 0.0, 0.0);
        }
        if (env.probeSurfaceY() < 0) {
            return forSubtype(CaveSubtype.DEEP_DARK, env);
        }
        return switch (env.family()) {
            case LUSH, JUNGLE, SWAMP -> forSubtype(CaveSubtype.LUSH, env);
            case DRIPSTONE -> forSubtype(CaveSubtype.DRIPSTONE, env);
            case DEEP_DARK -> forSubtype(CaveSubtype.DEEP_DARK, env);
            case OCEAN, RIVER, BEACH -> forSubtype(CaveSubtype.AQUIFER, env);
            default -> forSubtype(CaveSubtype.SPAGHETTI, env);
        };
    }

    public static CaveSubtype pickAutoSubtype(EnvironmentSample env) {
        if (env.probeSurfaceY() < 0) {
            return CaveSubtype.DEEP_DARK;
        }
        return switch (env.family()) {
            case LUSH, JUNGLE, SWAMP -> CaveSubtype.LUSH;
            case DRIPSTONE -> CaveSubtype.DRIPSTONE;
            case DEEP_DARK -> CaveSubtype.DEEP_DARK;
            case OCEAN, RIVER, BEACH -> CaveSubtype.AQUIFER;
            default -> CaveSubtype.SPAGHETTI;
        };
    }
}
