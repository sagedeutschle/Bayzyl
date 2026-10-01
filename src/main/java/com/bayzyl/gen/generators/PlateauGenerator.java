package com.bayzyl.gen.generators;

import com.bayzyl.gen.GenChangeCollector;
import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.Falloff;
import com.bayzyl.gen.primitives.FbmNoise;

import java.util.Random;

/**
 * Raises a flat-topped table (a mesa-top shape, without the bands).
 * The flat-vs-edge ratio is configurable so this can produce both
 * "broad plateau with smooth shoulders" and "thin plateau with cliff
 * edges" without a separate generator per silhouette.
 */
public final class PlateauGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "plateau";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int targetHeight = ctx.params.getInt("height", autoHeight(ctx.env, radius));
        double flatness = clamp(ctx.params.getFloat("flatness", 0.65), 0.0, 0.9);
        boolean cliffEdges = ctx.params.get("edges", "smooth").equalsIgnoreCase("cliff");
        double noiseAmp = ctx.params.getFloat("roughness", 0.18);
        double frequency = ctx.params.getFloat("frequency", 0.07);
        double warpStrength = ctx.params.getFloat("warp", 1.4);
        double blendEdge = clamp(ctx.params.getFloat("blend", 0.25), 0.0, 0.8);

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0xAB12L, warpStrength, frequency * 0.5);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);

        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();
        int anchorY = ctx.env.probeSurfaceY();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                if (dist > 1.0) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                double[] warped = warp.warp2(x * frequency, z * frequency);
                double curve = cliffEdges
                        ? Falloff.cliffEdge(dist, 0.5 + flatness * 0.4)
                        : Falloff.plateau(dist, flatness);
                // Tiny roughness on the top so it's not a perfect disc.
                double roughness = noise.fbm2D(warped[0], warped[1], 3) * noiseAmp;
                double heightT = Math.max(0.0, curve + roughness * 0.5);
                if (heightT <= 0.02) {
                    continue;
                }
                int baseY = GenHelpers.findSurfaceY(ctx.target.getWorld(), x, z,
                        ctx.target.getWorld().getMaxHeight() - 1);
                if (baseY <= ctx.target.getWorld().getMinHeight()) {
                    baseY = anchorY;
                }
                if (ctx.settings.adaptToEnvironment() && ctx.env.isFlat()
                        && Math.abs(baseY - anchorY) > 2) {
                    baseY = anchorY;
                }
                int rawTop = baseY + (int) Math.round(targetHeight * heightT);
                int newTop = rawTop;
                if (blendEdge > 0 && dist > 1.0 - blendEdge) {
                    double seamT = (dist - (1.0 - blendEdge)) / blendEdge;
                    newTop = GenHelpers.blendTopWithExisting(ctx.target.getWorld(), x, z, rawTop, seamT);
                }
                if (newTop <= baseY) {
                    continue;
                }
                if (!ctx.maskAllows(ctx.target.getWorld().getBlockAt(x, baseY, z).getType())) {
                    continue;
                }
                GenHelpers.paintColumn(ctx.changes, x, z, newTop, baseY + 1, palette, ctx.env, random);
            }
        }
    }

    private int autoHeight(EnvironmentSample env, int radius) {
        return switch (env.shape()) {
            case SUPERFLAT, FLAT -> Math.max(3, radius / 3);
            case ROLLING, HILLY -> Math.max(5, radius / 2);
            case MOUNTAIN, PEAK -> Math.max(8, radius);
            default -> Math.max(5, radius / 2);
        };
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
