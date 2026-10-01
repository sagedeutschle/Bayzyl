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
 * Carves a broad meandering depression. Optional water fill if the
 * floor lands at or below sea level, matching vanilla river behavior.
 */
public final class ValleyGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "valley";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int depth = ctx.params.getInt("depth", autoDepth(ctx.env, radius));
        double width = clamp(ctx.params.getFloat("width", 0.7), 0.2, 1.0);
        double meander = clamp(ctx.params.getFloat("meander", 0.5), 0.0, 1.0);
        String waterMode = ctx.params.get("water", "auto");
        double frequency = ctx.params.getFloat("frequency", 0.05);
        double warpStrength = ctx.params.getFloat("warp", 2.2);
        boolean lineRiver = ctx.params.getBool("river", false);

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0x4242L, warpStrength * (0.5 + meander),
                frequency * 0.5);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);

        World world = ctx.target.getWorld();
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
                double field = noise.fbm2D(warped[0], warped[1], 4);
                // Center the valley on the brush — distance contributes a U-shape.
                double valleyMask = Math.pow(Falloff.smooth(dist), 0.7);
                double channel = lineRiver
                        ? Math.max(0.0, 1.0 - Math.abs(field) * 1.6)
                        : Math.max(0.0, 0.55 - Math.abs(field));
                double carve = valleyMask * (channel * width + (1.0 - width) * valleyMask);
                if (carve <= 0.02) {
                    continue;
                }
                int surfaceY = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                if (surfaceY <= world.getMinHeight()) {
                    surfaceY = anchorY;
                }
                int carveDepth = (int) Math.round(depth * carve);
                if (carveDepth < 1) {
                    continue;
                }
                int newFloor = Math.max(world.getMinHeight() + 1, surfaceY - carveDepth);

                // Carve everything above the new floor.
                for (int y = surfaceY; y > newFloor; y--) {
                    Material existing = ctx.changes.peek(x, y, z);
                    if (!ctx.maskAllows(existing)) {
                        continue;
                    }
                    ctx.changes.clearAir(x, y, z);
                }
                // Re-cap the floor with the right surface material.
                ctx.changes.setMaterial(x, newFloor, z, palette.cap());
                if (newFloor - 1 >= world.getMinHeight()) {
                    ctx.changes.setMaterial(x, newFloor - 1, z, palette.sub());
                }
                // Water if the floor dropped below sea level or the user forced it.
                boolean fillWater = switch (waterMode.toLowerCase(java.util.Locale.ROOT)) {
                    case "on", "true", "yes" -> true;
                    case "off", "false", "no" -> false;
                    default -> newFloor <= ctx.env.waterLevel() - 1;
                };
                if (fillWater) {
                    for (int y = newFloor + 1; y <= Math.min(surfaceY, ctx.env.waterLevel()); y++) {
                        ctx.changes.set(x, y, z, palette.wet().createBlockData());
                    }
                    if (random.nextDouble() < 0.4) {
                        ctx.changes.setMaterial(x, newFloor, z,
                                palette.wet() == Material.LAVA ? Material.MAGMA_BLOCK : Material.GRAVEL);
                    }
                }
            }
        }
    }

    private int autoDepth(EnvironmentSample env, int radius) {
        return switch (env.shape()) {
            case SUPERFLAT, FLAT -> Math.max(3, radius / 3);
            case ROLLING -> Math.max(4, radius / 2);
            case HILLY -> Math.max(6, radius);
            case MOUNTAIN, PEAK -> Math.max(10, radius + radius / 2);
            default -> Math.max(5, radius / 2);
        };
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
