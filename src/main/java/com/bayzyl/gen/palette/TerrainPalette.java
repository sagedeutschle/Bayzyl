package com.bayzyl.gen.palette;

import com.bayzyl.gen.env.BiomeFamily;
import com.bayzyl.gen.env.EnvironmentSample;
import org.bukkit.Material;

import java.util.Random;

/**
 * Maps an {@link EnvironmentSample} to the four material slots every gen
 * brush works with. Generators stay agnostic of biome data and just ask
 * the palette "what goes here" by zone (cap / sub / fill / core).
 *
 * Layering convention (top-down for raised features, reverse for carved):
 *   - cap:  the top 1 layer of the feature (grass / sand / snow / nylium)
 *   - sub:  next 2-3 layers (dirt / sandstone / packed_ice / soul soil)
 *   - fill: the bulk middle (stone / sandstone / netherrack / end stone)
 *   - core: the deepest layer (deepslate / blackstone / packed ice)
 */
public record TerrainPalette(Material cap, Material sub, Material fill, Material core,
                             Material accent, Material wet) {

    public static TerrainPalette from(EnvironmentSample env) {
        Material cap = env.cap();
        Material sub = env.sub();
        Material fill;
        Material core;
        Material accent = pickAccent(env);
        Material wet = pickWet(env);
        if (env.isNetherLike()) {
            fill = Material.NETHERRACK;
            core = Material.BLACKSTONE;
        } else if (env.isEndLike()) {
            fill = Material.END_STONE;
            core = Material.END_STONE;
        } else if (env.isDesertLike()) {
            fill = env.surface() == Material.RED_SAND ? Material.RED_SANDSTONE : Material.SANDSTONE;
            core = Material.STONE;
        } else if (env.isSnowy()) {
            fill = Material.STONE;
            core = Material.DEEPSLATE;
            // Snowy biomes keep grass under the snow cap; let the cap be SNOW_BLOCK.
            if (cap == Material.GRASS_BLOCK && env.family() == BiomeFamily.SNOWY) {
                cap = Material.SNOW_BLOCK;
            }
        } else {
            fill = Material.STONE;
            core = env.probeSurfaceY() <= 8 ? Material.DEEPSLATE : Material.STONE;
        }
        return new TerrainPalette(cap, sub, fill, core, accent, wet);
    }

    /** A pure-stone palette for use when the brush is told to ignore the environment. */
    public static TerrainPalette generic() {
        return new TerrainPalette(Material.GRASS_BLOCK, Material.DIRT, Material.STONE,
                Material.DEEPSLATE, Material.COBBLESTONE, Material.WATER);
    }

    /** Pick a layer material based on a normalized 0..1 depth (0 = top, 1 = deep core). */
    public Material pickLayer(double depthT, Random random) {
        if (depthT < 0.05) {
            return cap;
        }
        if (depthT < 0.18) {
            return sub;
        }
        if (depthT < 0.55) {
            return fill;
        }
        if (depthT < 0.85) {
            // Blend between fill and core to avoid a hard transition stripe.
            return random.nextDouble() < depthT ? core : fill;
        }
        return core;
    }

    private static Material pickAccent(EnvironmentSample env) {
        return switch (env.family()) {
            case BADLANDS, MESA -> Material.RED_SAND;
            case DESERT -> Material.SANDSTONE;
            case JUNGLE -> Material.MOSS_BLOCK;
            case TAIGA, SNOWY -> Material.PODZOL;
            case CHERRY -> Material.PINK_PETALS;
            case LUSH -> Material.MOSS_BLOCK;
            case DRIPSTONE -> Material.DRIPSTONE_BLOCK;
            case DEEP_DARK -> Material.SCULK;
            case MUSHROOM -> Material.MYCELIUM;
            case NETHER -> Material.SOUL_SOIL;
            case END -> Material.END_STONE;
            default -> {
                if (env.surface() == Material.SAND) yield Material.SANDSTONE;
                if (env.surface() == Material.RED_SAND) yield Material.RED_SANDSTONE;
                yield Material.COBBLESTONE;
            }
        };
    }

    private static Material pickWet(EnvironmentSample env) {
        if (env.isNetherLike()) {
            return Material.LAVA;
        }
        return Material.WATER;
    }
}
