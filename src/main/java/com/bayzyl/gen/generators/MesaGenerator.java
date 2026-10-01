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
 * Banded plateau in the badlands style. Layers alternate through a
 * palette of stained terracotta, with optional terraces shaving the
 * silhouette into stepped tiers.
 */
public final class MesaGenerator implements GenBrushGenerator {
    private static final Material[] BADLANDS_BANDS = new Material[]{
            Material.TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
            Material.BROWN_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA
    };

    @Override
    public String id() {
        return "mesa";
    }

    @Override
    public void generate(GenBrushContext ctx) {
        int radius = ctx.settings.radius();
        int height = ctx.params.getInt("height", Math.max(8, radius));
        double banding = Math.max(0.0, Math.min(1.0, ctx.params.getFloat("banding", 0.85)));
        int terraces = Math.max(0, ctx.params.getInt("terraces", 3));
        double frequency = ctx.params.getFloat("frequency", 0.05);
        double warpStrength = ctx.params.getFloat("warp", 1.6);
        double blendEdge = Math.max(0.0, Math.min(0.6, ctx.params.getFloat("blend", 0.18)));

        FbmNoise noise = new FbmNoise(ctx.seed);
        DomainWarp warp = new DomainWarp(ctx.seed ^ 0xFADEL, warpStrength, frequency * 0.5);
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
                double curve = Falloff.plateau(dist, 0.45);
                double rough = noise.fbm2D(warped[0], warped[1], 3) * 0.18;
                double heightT = Math.max(0.0, curve + rough);
                if (terraces > 0) {
                    heightT = Math.floor(heightT * terraces) / terraces;
                }
                int rawTop = anchorY + (int) Math.round(height * heightT);
                int newTop = rawTop;
                if (blendEdge > 0 && dist > 1.0 - blendEdge) {
                    double seamT = (dist - (1.0 - blendEdge)) / blendEdge;
                    newTop = GenHelpers.blendTopWithExisting(world, x, z, rawTop, seamT);
                }
                if (newTop <= anchorY) {
                    continue;
                }
                int columnBase = anchorY;
                if (!ctx.maskAllows(world.getBlockAt(x, columnBase, z).getType())) {
                    continue;
                }
                for (int y = columnBase + 1; y <= newTop; y++) {
                    double layerT = (y - columnBase) / (double) (newTop - columnBase + 1);
                    Material band = pickBand(palette, layerT, banding, random);
                    ctx.changes.setMaterial(x, y, z, band);
                }
            }
        }
    }

    private Material pickBand(TerrainPalette palette, double layerT, double banding, Random random) {
        // banding=1 → strict ordered bands; banding=0 → plain fill material.
        if (random.nextDouble() > banding) {
            return palette.fill();
        }
        int idx = (int) Math.floor((1.0 - layerT) * BADLANDS_BANDS.length);
        if (idx < 0) idx = 0;
        if (idx >= BADLANDS_BANDS.length) idx = BADLANDS_BANDS.length - 1;
        // Snap top layer to the env cap to blend with the grass/sand around it.
        if (layerT > 0.95) {
            return palette.cap();
        }
        return BADLANDS_BANDS[idx];
    }
}
