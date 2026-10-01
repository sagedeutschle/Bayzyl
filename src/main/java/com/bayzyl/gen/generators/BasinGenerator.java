package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.Falloff;
import com.bayzyl.gen.primitives.FbmNoise;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Circular bowl depression. Default behavior auto-fills with water when
 * the floor drops below sea level (or with lava in the Nether).
 */
public final class BasinGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "basin";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int depth = ctx.params.getInt("depth", autoDepth(ctx.env, radius));
        double flatness = Math.max(0.0, Math.min(0.85, ctx.params.getFloat("flatness", 0.5)));
        String waterMode = ctx.params.get("water", "auto");
        double frequency = ctx.params.getFloat("frequency", 0.08);
        double warpStrength = ctx.params.getFloat("warp", 1.2);
        double noiseAmp = ctx.params.getFloat("roughness", 0.15);

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0x7A21L, warpStrength, frequency * 0.4);
        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();

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
                double rough = noise.fbm2D(warped[0], warped[1], 3) * noiseAmp;
                double curve = Falloff.plateau(dist, flatness);
                double carveT = Math.max(0.0, curve + rough * 0.4);
                int carveDepth = (int) Math.round(depth * carveT);
                if (carveDepth < 1) {
                    continue;
                }
                int surfaceY = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                if (surfaceY <= world.getMinHeight()) {
                    surfaceY = anchorY;
                }
                int newFloor = Math.max(world.getMinHeight() + 1, surfaceY - carveDepth);
                for (int y = surfaceY; y > newFloor; y--) {
                    Material existing = ctx.changes.peek(x, y, z);
                    if (!ctx.maskAllows(existing)) {
                        continue;
                    }
                    ctx.changes.clearAir(x, y, z);
                }
                ctx.changes.setMaterial(x, newFloor, z,
                        palette.wet() == Material.LAVA ? Material.NETHERRACK : palette.fill());
                if (newFloor - 1 >= world.getMinHeight()) {
                    ctx.changes.setMaterial(x, newFloor - 1, z, palette.sub());
                }
                boolean fillLiquid = switch (waterMode.toLowerCase(java.util.Locale.ROOT)) {
                    case "on", "true", "yes" -> true;
                    case "off", "false", "no" -> false;
                    default -> newFloor < ctx.env.waterLevel();
                };
                if (fillLiquid) {
                    int liquidTop = Math.min(surfaceY - 1, ctx.env.waterLevel());
                    for (int y = newFloor + 1; y <= liquidTop; y++) {
                        ctx.changes.setMaterial(x, y, z, palette.wet());
                    }
                }
            }
        }
    }

    private int autoDepth(EnvironmentSample env, int radius) {
        return switch (env.shape()) {
            case SUPERFLAT, FLAT -> Math.max(3, radius / 2);
            case ROLLING -> Math.max(4, radius);
            case HILLY, MOUNTAIN, PEAK -> Math.max(6, radius + radius / 2);
            default -> Math.max(4, radius / 2);
        };
    }
}
