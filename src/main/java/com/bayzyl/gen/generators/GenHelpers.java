package com.bayzyl.gen.generators;

import com.bayzyl.gen.GenChangeCollector;
import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Small surface-finding / column-painting helpers shared by the raise
 * generators. Kept here rather than each generator class so they don't
 * drift apart.
 */
public final class GenHelpers {
    private GenHelpers() {
    }

    /** Walk down from the top of a column to find the first solid block. */
    public static int findSurfaceY(World world, int x, int z, int searchTop) {
        int top = Math.min(world.getMaxHeight() - 1, searchTop);
        int min = world.getMinHeight();
        for (int y = top; y >= min; y--) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (isOpaque(m)) {
                return y;
            }
        }
        return min;
    }

    /** Walk down from {@code startY} until we find a solid floor. */
    public static int findFloorBelow(World world, int x, int startY, int z) {
        int min = world.getMinHeight();
        for (int y = startY; y >= min; y--) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (isOpaque(m)) {
                return y;
            }
        }
        return min;
    }

    public static boolean isOpaque(Material m) {
        if (m == null) {
            return false;
        }
        if (m.isAir()) {
            return false;
        }
        return switch (m) {
            case WATER, LAVA, BUBBLE_COLUMN -> false;
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN,
                 OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, MANGROVE_LEAVES, CHERRY_LEAVES,
                 AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES,
                 OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING,
                 ACACIA_SAPLING, DARK_OAK_SAPLING, MANGROVE_PROPAGULE, CHERRY_SAPLING,
                 DEAD_BUSH, SUGAR_CANE, SEAGRASS, TALL_SEAGRASS,
                 KELP, KELP_PLANT, GLOW_LICHEN, VINE,
                 SNOW, MOSS_CARPET, PINK_PETALS -> false;
            default -> m.isSolid();
        };
    }

    /**
     * Paint a vertical column with cap / sub / fill / core layers, given
     * a top Y. Useful for plateau / ridge / mesa generators where we
     * decide a target height per column and want vanilla-style layering
     * below it.
     */
    public static void paintColumn(GenChangeCollector changes, int x, int z,
                                   int topY, int baseY,
                                   TerrainPalette palette,
                                   EnvironmentSample env,
                                   java.util.Random random) {
        if (topY <= baseY) {
            return;
        }
        int height = topY - baseY;
        for (int dy = 0; dy <= height; dy++) {
            int y = topY - dy;
            double depth = height == 0 ? 0.0 : (double) dy / height;
            Material material;
            if (dy == 0) {
                material = palette.cap();
            } else if (dy < 3) {
                material = palette.sub();
            } else {
                material = palette.pickLayer(depth, random);
            }
            changes.setMaterial(x, y, z, material);
            // Snowy biome: dust the top.
            if (dy == 0 && env.isSnowy() && material == Material.GRASS_BLOCK) {
                changes.setMaterial(x, y + 1, z, Material.SNOW);
            }
        }
    }

    /**
     * Carve a vertical column from {@code topY} down to {@code bottomY},
     * but only through materials that look like terrain (skip already-air,
     * skip player builds we can detect).
     */
    public static void carveColumn(GenChangeCollector changes, int x, int z,
                                   int topY, int bottomY) {
        if (topY < bottomY) {
            int swap = topY;
            topY = bottomY;
            bottomY = swap;
        }
        for (int y = topY; y >= bottomY; y--) {
            Material existing = changes.peek(x, y, z);
            if (existing.isAir() || existing == Material.WATER) {
                continue;
            }
            changes.clearAir(x, y, z);
        }
    }

    /** Smooth blend the top of {@code topY} into the env's existing surface so the seam reads natural. */
    public static int blendTopWithExisting(World world, int x, int z, int targetTopY, double blendT) {
        int existing = findSurfaceY(world, x, z, world.getMaxHeight() - 1);
        if (existing <= world.getMinHeight()) {
            return targetTopY;
        }
        double t = Math.max(0.0, Math.min(1.0, blendT));
        return (int) Math.round(targetTopY * (1.0 - t) + existing * t);
    }
}
