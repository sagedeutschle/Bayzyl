package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.FbmNoise;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Loose rock debris draped down a slope. Walks the surface and drops a
 * thin scatter of cobble/gravel-style blocks, biased toward steeper
 * slopes so the result looks like real talus.
 */
public final class ScreeGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "scree";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        double density = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("density", 0.65)));
        String blockOverride = ctx.params.get("block", null);

        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Material primary = blockOverride != null
                ? parseMaterial(blockOverride, palette.accent())
                : palette.accent();
        Material secondary = palette.fill() == Material.NETHERRACK ? Material.SOUL_SAND : Material.GRAVEL;

        Random random = new Random(ctx.seed);
        FbmNoise noise = new FbmNoise(ctx.seed);
        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                if (dist > 1.0) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int surfaceY = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                if (surfaceY <= world.getMinHeight()) {
                    continue;
                }
                double slope = estimateSlope(world, x, z, surfaceY);
                double roll = random.nextDouble();
                double placementChance = density * (0.35 + slope * 0.65) * (1.0 - dist);
                if (roll > placementChance) {
                    continue;
                }
                if (!ctx.maskAllows(world.getBlockAt(x, surfaceY, z).getType())) {
                    continue;
                }
                Material block = random.nextDouble() < 0.3 ? secondary : primary;
                double n = noise.fbm2D(x * 0.3, z * 0.3, 2);
                int aboveY = surfaceY + 1;
                if (aboveY < world.getMaxHeight() - 1
                        && ctx.changes.peek(x, aboveY, z).isAir()) {
                    ctx.changes.setMaterial(x, aboveY, z, block);
                }
                // Occasionally bury a second tile a block deep so it doesn't look painted on.
                if (n > 0.2) {
                    ctx.changes.setMaterial(x, surfaceY, z, block);
                }
            }
        }
    }

    /**
     * Rough proxy for slope: how much the surface Y differs from the
     * four orthogonal neighbors. Returns 0..1.
     */
    private double estimateSlope(World world, int x, int z, int centerY) {
        int total = 0;
        int samples = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            int sy = GenHelpers.findSurfaceY(world, x + off[0], z + off[1], world.getMaxHeight() - 1);
            if (sy <= world.getMinHeight()) {
                continue;
            }
            total += Math.abs(sy - centerY);
            samples++;
        }
        if (samples == 0) {
            return 0.0;
        }
        double avgDelta = total / (double) samples;
        return Math.min(1.0, avgDelta / 4.0);
    }

    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace("MINECRAFT:", ""));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
