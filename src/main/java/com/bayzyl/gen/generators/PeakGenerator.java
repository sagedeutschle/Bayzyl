package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.Falloff;
import com.bayzyl.gen.primitives.FbmNoise;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Sharp single-summit mountain. The top tapers to a near-point so it
 * reads as a peak even at small radii. Snow caps the summit
 * automatically when the env is cold enough.
 */
public final class PeakGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "peak";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int height = ctx.params.getInt("height", Math.max(8, radius * 2));
        double sharpness = Math.max(0.1, Math.min(1.0, ctx.params.getFloat("sharpness", 0.7)));
        double frequency = ctx.params.getFloat("frequency", 0.09);
        double warpStrength = ctx.params.getFloat("warp", 1.0);
        double snowLine = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("snowline", 0.7)));
        double blendEdge = Math.max(0.0, Math.min(0.6, ctx.params.getFloat("blend", 0.25)));

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0x88CCL, warpStrength, frequency * 0.4);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);

        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();
        int anchorY = ctx.env.probeSurfaceY();
        boolean snowyEnv = ctx.env.isSnowy() || ctx.env.shape() == com.bayzyl.gen.env.TerrainShape.MOUNTAIN
                || ctx.env.shape() == com.bayzyl.gen.env.TerrainShape.PEAK;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                if (dist > 1.0) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                double[] warped = warp.warp2(x * frequency, z * frequency);
                double rough = noise.fbm2D(warped[0], warped[1], 3) * 0.15;
                double curve = Math.pow(Falloff.smooth(dist), 1.0 + sharpness * 2.5);
                double heightT = Math.max(0.0, curve + rough);
                int rawTop = anchorY + (int) Math.round(height * heightT);
                int newTop = rawTop;
                if (blendEdge > 0 && dist > 1.0 - blendEdge) {
                    double seamT = (dist - (1.0 - blendEdge)) / blendEdge;
                    newTop = GenHelpers.blendTopWithExisting(world, x, z, rawTop, seamT);
                }
                if (newTop <= anchorY) {
                    continue;
                }
                int columnBase = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                if (columnBase <= world.getMinHeight()) {
                    columnBase = anchorY;
                }
                if (!ctx.maskAllows(world.getBlockAt(x, columnBase, z).getType())) {
                    continue;
                }
                GenHelpers.paintColumn(ctx.changes, x, z, newTop, columnBase + 1, palette, ctx.env, random);
                // Snow cap on the upper portion.
                if (snowyEnv) {
                    int snowStart = anchorY + (int) Math.round(height * snowLine);
                    for (int y = Math.max(snowStart, columnBase + 1); y <= newTop; y++) {
                        if (y == newTop) {
                            ctx.changes.setMaterial(x, y, z, Material.SNOW_BLOCK);
                            int aboveY = y + 1;
                            if (aboveY < world.getMaxHeight() - 1 && random.nextDouble() < 0.35) {
                                ctx.changes.setMaterial(x, aboveY, z, Material.SNOW);
                            }
                        } else if (y > snowStart + 2) {
                            ctx.changes.setMaterial(x, y, z, Material.SNOW_BLOCK);
                        }
                    }
                }
            }
        }
    }
}
