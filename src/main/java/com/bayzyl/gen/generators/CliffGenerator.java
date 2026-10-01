package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.FbmNoise;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * A tall directional cliff face. Picks a normal direction (from
 * facing or {@code direction} arg) and raises terrain on one side of
 * the half-plane while keeping the other side at the original level.
 */
public final class CliffGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "cliff";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int height = ctx.params.getInt("height", Math.max(8, radius));
        double steepness = Math.max(0.1, Math.min(1.0, ctx.params.getFloat("steepness", 0.85)));
        double frequency = ctx.params.getFloat("frequency", 0.07);
        double warpStrength = ctx.params.getFloat("warp", 1.4);
        double blend = Math.max(0.0, Math.min(0.6, ctx.params.getFloat("blend", 0.15)));
        String dirParam = ctx.params.get("direction", null);

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0xCCDDL, warpStrength, frequency * 0.4);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);

        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();
        int anchorY = ctx.env.probeSurfaceY();

        double yawRad;
        if (dirParam != null) {
            yawRad = switch (dirParam.toLowerCase(java.util.Locale.ROOT)) {
                case "north" -> Math.toRadians(-90);
                case "south" -> Math.toRadians(90);
                case "east" -> Math.toRadians(0);
                case "west" -> Math.toRadians(180);
                default -> Math.toRadians(ctx.player.getLocation().getYaw() - 90);
            };
        } else {
            yawRad = Math.toRadians(ctx.player.getLocation().getYaw() - 90);
        }
        // Normal points "outward" from the cliff face — the side we raise.
        double normalX = Math.cos(yawRad);
        double normalZ = Math.sin(yawRad);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                if (dist > 1.0) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                // Signed distance from the cliff line. positive = high side, negative = low.
                double signed = dx * normalX + dz * normalZ;
                double[] warped = warp.warp2(x * frequency, z * frequency);
                double wiggle = noise.fbm2D(warped[0], warped[1], 3) * radius * 0.15;
                double signedWobble = signed + wiggle;
                // Skip far away points on either side, but always update points near the line.
                double radial = 1.0 - dist;
                if (signedWobble < -radius * 0.1) {
                    continue;
                }
                double t = Math.max(0.0, Math.min(1.0, signedWobble / (radius * 0.6)));
                // Sharp shoulder: t < steepness => full height; beyond => fall off.
                double riseT = t < steepness
                        ? 1.0
                        : Math.max(0.0, 1.0 - (t - steepness) / Math.max(0.05, 1.0 - steepness));
                int rise = (int) Math.round(height * riseT * radial);
                if (rise < 1) {
                    continue;
                }
                int columnBase = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                if (columnBase <= world.getMinHeight()) {
                    columnBase = anchorY;
                }
                int newTop = columnBase + rise;
                if (blend > 0 && dist > 1.0 - blend) {
                    double seamT = (dist - (1.0 - blend)) / blend;
                    newTop = GenHelpers.blendTopWithExisting(world, x, z, newTop, seamT);
                }
                if (!ctx.maskAllows(world.getBlockAt(x, columnBase, z).getType())) {
                    continue;
                }
                GenHelpers.paintColumn(ctx.changes, x, z, newTop, columnBase + 1, palette, ctx.env, random);
            }
        }
    }
}
