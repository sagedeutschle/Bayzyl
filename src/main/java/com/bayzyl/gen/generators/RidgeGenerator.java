package com.bayzyl.gen.generators;

import com.bayzyl.gen.GenBrushSettings;
import com.bayzyl.gen.GenChangeCollector;
import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.Falloff;
import com.bayzyl.gen.primitives.FbmNoise;

import java.util.Random;

/**
 * Raises a noisy crest along the surface. The "ridged" noise variant
 * gives sharp peaks near zero-crossings of the underlying field, which
 * is what makes a vanilla mountain range look like a vanilla mountain
 * range rather than a smooth hump.
 */
public final class RidgeGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "ridge";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        double intensity = clamp01(ctx.params.getFloat("intensity", 0.85));
        int maxHeight = ctx.params.getInt("height", autoHeight(ctx.env, radius));
        double steepness = clamp01(ctx.params.getFloat("steepness", 0.6));
        double crest = clamp01(ctx.params.getFloat("crest", 0.75));
        double warpStrength = ctx.params.getFloat("warp", 1.2);
        double frequency = ctx.params.getFloat("frequency", 0.08);
        double blendEdge = clamp01(ctx.params.getFloat("blend", 0.35));

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0x5A17L, warpStrength, frequency * 0.6);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);

        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();
        int anchorY = resolveAnchorY(ctx);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                if (dist > 1.0) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                double[] warped = warp.warp2(x * frequency, z * frequency);
                double ridge = noise.ridged2D(warped[0], warped[1], 4);
                // Bias the field upward so we get a single dominant ridge instead of a wash of bumps.
                ridge = Math.pow(Math.max(0.0, ridge), 1.0 + crest);
                double radial = Falloff.smooth(dist);
                double blendedRadial = Math.pow(radial, 1.0 + (1.0 - steepness) * 2.0);
                double height = ridge * blendedRadial * intensity * maxHeight;
                if (height < 1.0) {
                    continue;
                }
                int baseY = surfaceAt(ctx, x, z, anchorY);
                if (ctx.settings.adaptToEnvironment() && ctx.env.isFlat() && Math.abs(baseY - anchorY) > 2) {
                    baseY = anchorY;
                }
                int newTop = baseY + (int) Math.round(height);
                if (blendEdge > 0 && dist > 1.0 - blendEdge) {
                    double seamT = (dist - (1.0 - blendEdge)) / blendEdge;
                    newTop = GenHelpers.blendTopWithExisting(ctx.target.getWorld(), x, z, newTop, seamT);
                }
                if (!ctx.maskAllows(ctx.target.getWorld().getBlockAt(x, baseY, z).getType())) {
                    continue;
                }
                GenHelpers.paintColumn(ctx.changes, x, z, newTop, baseY + 1, palette, ctx.env, random);
            }
        }
    }

    private int resolveAnchorY(GenBrushContext ctx) {
        return ctx.env.probeSurfaceY();
    }

    private int surfaceAt(GenBrushContext ctx, int x, int z, int anchorY) {
        int top = ctx.target.getWorld().getMaxHeight() - 1;
        int found = GenHelpers.findSurfaceY(ctx.target.getWorld(), x, z, top);
        if (found <= ctx.target.getWorld().getMinHeight()) {
            return anchorY;
        }
        return found;
    }

    private int autoHeight(EnvironmentSample env, int radius) {
        // Match the env: a flat plain gets a modest ridge, a peaky biome gets a tall one.
        return switch (env.shape()) {
            case SUPERFLAT, FLAT -> Math.max(4, radius / 2);
            case ROLLING -> Math.max(6, radius);
            case HILLY -> Math.max(8, radius + radius / 2);
            case MOUNTAIN, PEAK -> Math.max(12, radius * 2);
            default -> Math.max(6, radius);
        };
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
