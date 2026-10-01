package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.FbmNoise;
import com.bayzyl.gen.primitives.Worley;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Scatters round natural-looking boulders on the surface. Uses Worley
 * feature points as boulder centers so they don't overlap evenly.
 */
public final class BoulderGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "boulder";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int count = ctx.params.getInt("count", Math.max(2, radius / 2));
        int boulderRadiusMax = ctx.params.getInt("radius", Math.max(2, radius / 3));
        double clustering = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("cluster", 0.4)));
        String blockOverride = ctx.params.get("block", null);

        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Material boulderBlock = blockOverride != null
                ? parseMaterial(blockOverride, palette.fill())
                : palette.fill();
        Random random = new Random(ctx.seed);
        FbmNoise shape = new FbmNoise(ctx.seed ^ 0xB011L);

        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();

        int placed = 0;
        int attempts = 0;
        int maxAttempts = count * 8;
        while (placed < count && attempts < maxAttempts) {
            attempts++;
            double angle = random.nextDouble() * Math.PI * 2.0;
            // Clustering pulls centers toward the brush center.
            double r = Math.pow(random.nextDouble(), 1.0 + clustering * 2.5) * radius;
            int bx = cx + (int) Math.round(Math.cos(angle) * r);
            int bz = cz + (int) Math.round(Math.sin(angle) * r);
            int by = GenHelpers.findSurfaceY(world, bx, bz, world.getMaxHeight() - 1);
            if (by <= world.getMinHeight()) {
                continue;
            }
            if (!ctx.maskAllows(world.getBlockAt(bx, by, bz).getType())) {
                continue;
            }
            int br = 1 + random.nextInt(Math.max(1, boulderRadiusMax));
            paintBoulder(ctx, shape, random, bx, by, bz, br, boulderBlock, palette);
            placed++;
        }
    }

    private void paintBoulder(GenBrushContext ctx, FbmNoise shape, Random random,
                              int bx, int by, int bz, int br, Material block, TerrainPalette palette) {
        double squashY = 0.7 + random.nextDouble() * 0.5;
        for (int dx = -br; dx <= br; dx++) {
            for (int dy = -br; dy <= br + 1; dy++) {
                for (int dz = -br; dz <= br; dz++) {
                    double dyAdj = dy / squashY;
                    double r2 = dx * dx + dyAdj * dyAdj + dz * dz;
                    if (r2 > br * br) {
                        continue;
                    }
                    double n = shape.fbm((bx + dx) * 0.4, (by + dy) * 0.4, (bz + dz) * 0.4, 2) * 0.5;
                    if (r2 > (br - n) * (br - n)) {
                        continue;
                    }
                    int x = bx + dx;
                    int y = by + dy;
                    int z = bz + dz;
                    Material existing = ctx.changes.peek(x, y, z);
                    if (!existing.isAir() && existing != Material.WATER && y > by) {
                        continue;
                    }
                    ctx.changes.setMaterial(x, y, z, block);
                    // Mossy decoration in lush biomes.
                    if (dy == br && random.nextDouble() < 0.2
                            && (ctx.env.family() == com.bayzyl.gen.env.BiomeFamily.LUSH
                            || ctx.env.family() == com.bayzyl.gen.env.BiomeFamily.JUNGLE
                            || ctx.env.family() == com.bayzyl.gen.env.BiomeFamily.SWAMP)) {
                        ctx.changes.setMaterial(x, y + 1, z, Material.MOSS_CARPET);
                    }
                }
            }
        }
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
