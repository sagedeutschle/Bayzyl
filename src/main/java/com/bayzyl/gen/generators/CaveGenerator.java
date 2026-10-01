package com.bayzyl.gen.generators;

import com.bayzyl.gen.CaveSubtype;
import com.bayzyl.gen.palette.CavePalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.FbmNoise;
import com.bayzyl.gen.primitives.Worley;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Vanilla-style 3D cave carving. The subtype determines which silhouette
 * we draw (noodle, cheese, spaghetti, etc.) and which palette decorates
 * the resulting walls.
 *
 * Each subtype is a small specialization on top of the same core loop:
 *   1. Pick a region to carve relative to the brush target.
 *   2. Sample a density field for each block (1 = solid, 0 = air).
 *   3. Anything below the threshold becomes air or, for subtypes with
 *      accents, a sparse decorative material.
 */
public final class CaveGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "cave";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        CaveSubtype subtype = ctx.settings.caveSubtype();
        if (subtype == CaveSubtype.AUTO) {
            subtype = CavePalette.pickAutoSubtype(ctx.env);
        }
        switch (subtype) {
            case NOODLE -> carveNoodle(ctx, subtype);
            case CHEESE -> carveCheese(ctx, subtype);
            case SPAGHETTI -> carveSpaghetti(ctx, subtype);
            case DRIPSTONE -> carveCheese(ctx, subtype);
            case LUSH -> carveCheese(ctx, subtype);
            case DEEP_DARK -> carveCheese(ctx, subtype);
            case AQUIFER -> carveSpaghetti(ctx, subtype);
            case REGULAR -> carveSpaghetti(ctx, subtype);
            default -> carveSpaghetti(ctx, CaveSubtype.SPAGHETTI);
        }
    }

    private void carveNoodle(GenBrushContext ctx, CaveSubtype subtype) {
        int radius = ctx.settings.radius();
        int verticalRadius = ctx.params.getInt("vertical", radius / 2);
        double density = clamp01(ctx.params.getFloat("density", 0.55));
        double frequency = ctx.params.getFloat("frequency", 0.16);
        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0xD0D0L, 1.8, frequency * 0.5);
        CavePalette palette = CavePalette.forSubtype(subtype, ctx.env);
        Random random = new Random(ctx.seed);
        carveLoop(ctx, radius, verticalRadius, palette, random,
                (x, y, z) -> {
                    double[] w = warp.warp3(x * frequency, y * frequency * 1.3, z * frequency);
                    double n = noise.fbm(w[0], w[1], w[2], 3);
                    // Noodles are thin, so only carve when noise is right at zero.
                    return Math.abs(n) < density * 0.18;
                });
    }

    private void carveCheese(GenBrushContext ctx, CaveSubtype subtype) {
        int radius = ctx.settings.radius();
        int verticalRadius = ctx.params.getInt("vertical", Math.max(4, radius * 2 / 3));
        double density = clamp01(ctx.params.getFloat("density", 0.6));
        double frequency = ctx.params.getFloat("frequency", 0.1);
        FbmNoise noise = new FbmNoise(ctx.seed);
        Worley worley = new Worley(ctx.seed ^ 0xCEE5L);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0x1234L, 1.2, frequency * 0.4);
        CavePalette palette = CavePalette.forSubtype(subtype, ctx.env);
        Random random = new Random(ctx.seed);
        carveLoop(ctx, radius, verticalRadius, palette, random,
                (x, y, z) -> {
                    double[] w = warp.warp3(x * frequency, y * frequency, z * frequency);
                    double fbm = noise.fbm(w[0], w[1], w[2], 3);
                    double cellular = worley.f1(x * frequency * 0.4,
                            y * frequency * 0.5, z * frequency * 0.4);
                    // Cheese: large round chambers => carve where cellular distance is small.
                    return (cellular < 0.55) || (Math.abs(fbm) < density * 0.25);
                });
    }

    private void carveSpaghetti(GenBrushContext ctx, CaveSubtype subtype) {
        int radius = ctx.settings.radius();
        int verticalRadius = ctx.params.getInt("vertical", Math.max(4, radius / 2));
        double density = clamp01(ctx.params.getFloat("density", 0.55));
        double frequency = ctx.params.getFloat("frequency", 0.12);
        double verticality = clamp01(ctx.params.getFloat("verticality", 0.35));
        FbmNoise noise = new FbmNoise(ctx.seed);
        FbmNoise noiseB = new FbmNoise(ctx.seed ^ 0xBEEFL);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0xABCDL, 2.0, frequency * 0.5);
        CavePalette palette = CavePalette.forSubtype(subtype, ctx.env);
        Random random = new Random(ctx.seed);
        carveLoop(ctx, radius, verticalRadius, palette, random,
                (x, y, z) -> {
                    double[] w = warp.warp3(x * frequency, y * frequency * (0.5 + verticality),
                            z * frequency);
                    // Two perpendicular fields near zero => a tunnel where they cross.
                    double a = noise.fbm(w[0], w[1], w[2], 3);
                    double b = noiseB.fbm(w[0] + 13.0, w[1] + 7.0, w[2] - 5.0, 3);
                    return Math.abs(a) < density * 0.2 && Math.abs(b) < density * 0.2;
                });
    }

    /**
     * Shared carve loop. Iterates a 3D region centered on the target and
     * carves any solid block whose density function says "yes". Caps the
     * resulting air pocket with palette-appropriate accent blocks.
     */
    private void carveLoop(GenBrushContext ctx, int horizontalRadius, int verticalRadius,
                           CavePalette palette, Random random, DensityFn density) {
        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cy = ctx.target.getBlockY();
        int cz = ctx.target.getBlockZ();
        int minY = Math.max(world.getMinHeight() + 1, cy - verticalRadius);
        int maxY = Math.min(world.getMaxHeight() - 2, cy + verticalRadius);
        boolean wantsLiquid = palette.liquidBlock() != null && palette.liquidChance() > 0;

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                double horizDist = Math.sqrt(dx * dx + dz * dz) / horizontalRadius;
                if (horizDist > 1.0) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    double dy = (y - cy) / (double) Math.max(1, verticalRadius);
                    // Ellipsoid falloff inside which we ALLOW carving.
                    if (horizDist * horizDist + dy * dy > 1.0) {
                        continue;
                    }
                    if (!density.solidOpen(x, y, z)) {
                        continue;
                    }
                    Material existing = ctx.changes.peek(x, y, z);
                    if (existing.isAir()) {
                        continue;
                    }
                    if (existing == Material.WATER && palette.liquidBlock() != Material.WATER) {
                        continue;
                    }
                    if (!ctx.maskAllows(existing)) {
                        continue;
                    }
                    // Choose what to leave behind.
                    Material replacement = pickReplacement(palette, random);
                    if (replacement == Material.AIR) {
                        ctx.changes.clearAir(x, y, z);
                    } else {
                        ctx.changes.setMaterial(x, y, z, replacement);
                    }
                    if (wantsLiquid && random.nextDouble() < palette.liquidChance()) {
                        Material below = ctx.changes.peek(x, y - 1, z);
                        if (below.isSolid()) {
                            ctx.changes.setMaterial(x, y, z, palette.liquidBlock());
                        }
                    }
                    // Dripstone subtype gets pointed dripstone stalactites under solid blocks.
                    if (palette.ceilingAccent() == Material.POINTED_DRIPSTONE
                            && random.nextDouble() < palette.accentChance()) {
                        int above = y + 1;
                        if (above <= maxY && ctx.changes.peek(x, above, z).isSolid()) {
                            ctx.changes.setMaterial(x, y, z, Material.POINTED_DRIPSTONE);
                        }
                    }
                }
            }
        }
    }

    private Material pickReplacement(CavePalette palette, Random random) {
        if (palette.accentChance() <= 0) {
            return Material.AIR;
        }
        double roll = random.nextDouble();
        if (roll < palette.accentChance() * 0.5 && palette.floorAccent() != null) {
            return palette.floorAccent();
        }
        if (roll < palette.accentChance() && palette.veinBlock() != null) {
            return palette.veinBlock();
        }
        return Material.AIR;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    @FunctionalInterface
    private interface DensityFn {
        boolean solidOpen(int x, int y, int z);
    }
}
