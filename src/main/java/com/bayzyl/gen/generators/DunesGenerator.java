package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.FbmNoise;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Wind-shaped sand waves. The wavelength runs perpendicular to the dune
 * direction so individual dunes appear as long ridges. Defaults to sand,
 * but if the environment has red sand we switch automatically.
 */
public final class DunesGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "dunes";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int height = ctx.params.getInt("height", Math.max(3, radius / 3));
        double wavelength = Math.max(2.0, ctx.params.getFloat("wavelength", Math.max(6.0, radius / 2.0)));
        String dirParam = ctx.params.get("direction", null);
        double frequency = ctx.params.getFloat("frequency", 0.06);
        boolean stickToEnv = ctx.settings.adaptToEnvironment();

        TerrainPalette palette = stickToEnv ? TerrainPalette.from(ctx.env) : TerrainPalette.generic();
        Material sandType = stickToEnv ? envSand(ctx.env, palette) : Material.SAND;
        Material baseType = sandBase(sandType);

        Random random = new Random(ctx.seed);
        FbmNoise noise = new FbmNoise(ctx.seed);
        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();

        // Direction vector: along the dune crest line. Perpendicular axis = wavelength.
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
        // Crest direction unit vector.
        double crestX = Math.cos(yawRad);
        double crestZ = Math.sin(yawRad);
        // Perpendicular axis (wave direction).
        double waveX = -crestZ;
        double waveZ = crestX;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) / radius;
                if (dist > 1.0) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                double waveCoord = dx * waveX + dz * waveZ;
                // Sine wave gives a clean wave; add a low-amp noise to break the regularity.
                double waveValue = Math.sin(waveCoord * (2.0 * Math.PI / wavelength));
                double jitter = noise.fbm2D(x * frequency, z * frequency, 3) * 0.4;
                double dunePeak = (waveValue + jitter) * 0.5 + 0.5; // 0..1
                double radial = 1.0 - dist * dist;
                int duneHeight = (int) Math.round(height * dunePeak * radial);
                if (duneHeight < 1) {
                    continue;
                }
                int surfaceY = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                if (surfaceY <= world.getMinHeight()) {
                    surfaceY = ctx.env.probeSurfaceY();
                }
                if (!ctx.maskAllows(world.getBlockAt(x, surfaceY, z).getType())) {
                    continue;
                }
                for (int dy = 1; dy <= duneHeight; dy++) {
                    int y = surfaceY + dy;
                    if (y >= world.getMaxHeight() - 1) break;
                    ctx.changes.setMaterial(x, y, z, sandType);
                }
                // Sandstone foundation under deep dunes so they don't immediately collapse.
                if (duneHeight >= 4) {
                    ctx.changes.setMaterial(x, surfaceY, z, baseType);
                }
            }
        }
    }

    private Material envSand(EnvironmentSample env, TerrainPalette palette) {
        if (env.surface() == Material.RED_SAND) {
            return Material.RED_SAND;
        }
        if (env.surface() == Material.SAND) {
            return Material.SAND;
        }
        return Material.SAND;
    }

    private Material sandBase(Material sand) {
        return sand == Material.RED_SAND ? Material.RED_SANDSTONE : Material.SANDSTONE;
    }
}
