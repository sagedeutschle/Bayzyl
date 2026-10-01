package com.bayzyl.gen.generators;

import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.palette.TerrainPalette;
import com.bayzyl.gen.primitives.DomainWarp;
import com.bayzyl.gen.primitives.FbmNoise;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * A vertical chasm — vanilla-style ravine. Long, narrow, slightly
 * jagged. Direction is the player's facing if not specified.
 */
public final class RavineGenerator implements GenBrushGenerator {
    @Override
    public String id() {
        return "ravine";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int length = ctx.params.getInt("length", Math.max(12, radius * 4));
        int depth = ctx.params.getInt("depth", Math.max(10, radius * 3));
        double jaggedness = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("jaggedness", 0.55)));
        double bridges = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("bridges", 0.25)));
        double widthScale = Math.max(0.4, Math.min(2.0, ctx.params.getFloat("width", 1.0)));
        String dirParam = ctx.params.get("direction", null);

        TerrainPalette palette = ctx.settings.adaptToEnvironment()
                ? TerrainPalette.from(ctx.env)
                : TerrainPalette.generic();
        Random random = new Random(ctx.seed);
        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0x33CCL, 1.0 + jaggedness * 2.5, 0.04);

        World world = ctx.target.getWorld();
        int cx = ctx.target.getBlockX();
        int cz = ctx.target.getBlockZ();

        // Resolve direction: explicit param, then player facing.
        double yawRad;
        if (dirParam != null) {
            yawRad = switch (dirParam.toLowerCase(java.util.Locale.ROOT)) {
                case "north" -> Math.toRadians(-90);
                case "south" -> Math.toRadians(90);
                case "east" -> Math.toRadians(0);
                case "west" -> Math.toRadians(180);
                case "ne" -> Math.toRadians(-45);
                case "nw" -> Math.toRadians(-135);
                case "se" -> Math.toRadians(45);
                case "sw" -> Math.toRadians(135);
                default -> Math.toRadians(ctx.player.getLocation().getYaw() - 90);
            };
        } else {
            yawRad = Math.toRadians(ctx.player.getLocation().getYaw() - 90);
        }
        double dirX = Math.cos(yawRad);
        double dirZ = Math.sin(yawRad);

        int surfaceAnchor = ctx.env.probeSurfaceY();

        for (int t = -length / 2; t <= length / 2; t++) {
            double centerX = cx + dirX * t;
            double centerZ = cz + dirZ * t;
            double[] warped = warp.warp2(centerX * 0.05, centerZ * 0.05);
            double sidewaysJitter = (warped[0] - centerX * 0.05) * 6.0 * jaggedness;
            // Push the center perpendicular to the ravine direction.
            double px = centerX + (-dirZ) * sidewaysJitter;
            double pz = centerZ + dirX * sidewaysJitter;

            double widthHere = (1.6 + noise.fbm2D(t * 0.12, 0.0, 2) * 0.8) * widthScale;
            int halfWidth = (int) Math.round(widthHere);
            if (halfWidth < 1) {
                halfWidth = 1;
            }
            int ix = (int) Math.round(px);
            int iz = (int) Math.round(pz);
            int top = GenHelpers.findSurfaceY(world, ix, iz, world.getMaxHeight() - 1);
            if (top <= world.getMinHeight()) {
                top = surfaceAnchor;
            }
            int floor = Math.max(world.getMinHeight() + 1, top - depth);

            // Bridges: occasionally leave a thin solid layer across the ravine.
            boolean bridge = random.nextDouble() < bridges * 0.08;
            int bridgeY = bridge ? floor + 1 + random.nextInt(Math.max(1, depth / 2)) : Integer.MIN_VALUE;

            for (int dx = -halfWidth - 1; dx <= halfWidth + 1; dx++) {
                for (int dz = -halfWidth - 1; dz <= halfWidth + 1; dz++) {
                    double radial = Math.sqrt(dx * dx + dz * dz);
                    if (radial > halfWidth + 0.5) {
                        continue;
                    }
                    int x = ix + dx;
                    int z = iz + dz;
                    int columnTop = GenHelpers.findSurfaceY(world, x, z, world.getMaxHeight() - 1);
                    if (columnTop <= world.getMinHeight()) {
                        columnTop = top;
                    }
                    int columnFloor = Math.max(world.getMinHeight() + 1, columnTop - depth);
                    for (int y = columnTop; y >= columnFloor; y--) {
                        if (y == bridgeY) {
                            continue;
                        }
                        Material existing = ctx.changes.peek(x, y, z);
                        if (existing.isAir() || existing == Material.WATER) {
                            continue;
                        }
                        if (!ctx.maskAllows(existing)) {
                            continue;
                        }
                        ctx.changes.clearAir(x, y, z);
                    }
                    // Re-cap the lip and floor with sensible materials.
                    int newLip = Math.max(world.getMinHeight() + 1, columnFloor + 1);
                    if (radial > halfWidth) {
                        ctx.changes.setMaterial(x, columnFloor, z, palette.fill());
                    }
                    // Optional bottom liquid: lava if Nether, water if env wet.
                    if (palette.wet() == Material.LAVA
                            && random.nextDouble() < 0.4) {
                        ctx.changes.setMaterial(x, columnFloor, z, Material.MAGMA_BLOCK);
                        if (columnFloor + 1 < newLip) {
                            ctx.changes.setMaterial(x, columnFloor + 1, z, Material.LAVA);
                        }
                    }
                }
            }
        }
    }
}
