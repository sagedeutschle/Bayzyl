package com.bayzyl.detail.presets;

import com.bayzyl.BlockChange;
import com.bayzyl.detail.DetailBrushFamily;
import com.bayzyl.detail.DetailBrushParameterSpec;
import com.bayzyl.detail.DetailBrushParameters;
import com.bayzyl.detail.DetailBrushPreset;
import com.bayzyl.detail.primitives.FalloffCurve;
import com.bayzyl.detail.primitives.NoiseField;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class FlamePreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.floatRange("heat", 0.7, 0.0, 1.0, "Hot/cold balance: 0 dark embers, 1 white-hot core"),
            DetailBrushParameterSpec.intRange("height", 8, 1, 64, "Vertical extent of the flame in blocks"),
            DetailBrushParameterSpec.intRange("width", 3, 1, 24, "Base radius in blocks"),
            DetailBrushParameterSpec.floatRange("flicker", 0.4, 0.0, 1.0, "Edge perturbation. Higher = more wispy"),
            DetailBrushParameterSpec.floatRange("lean_x", 0.0, -1.0, 1.0, "Lean toward +x with t (top tilt). 1 = lean a full width over the height"),
            DetailBrushParameterSpec.floatRange("lean_z", 0.0, -1.0, 1.0, "Lean toward +z"),
            DetailBrushParameterSpec.floatRange("density", 0.85, 0.0, 1.0, "Overall placement chance multiplier")
    );

    @Override
    public String id() {
        return "flame";
    }

    @Override
    public int stampCooldownTicks() {
        return 2;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int height = Math.max(1, parameters.getInt("height", 8));
        int width = Math.max(1, parameters.getInt("width", 3));
        double density = Math.max(0.0, Math.min(1.0, parameters.getFloat("density", 0.85)));
        return (int) Math.round(width * width * height * density * 0.7);
    }

    @Override
    public String displayName() {
        return "Flame";
    }

    @Override
    public DetailBrushFamily family() {
        return DetailBrushFamily.VOLUMETRIC;
    }

    @Override
    public List<DetailBrushParameterSpec> parameterSpecs() {
        return SPECS;
    }

    @Override
    public Set<Material> transparentTargetMaterials() {
        return Set.of(
                Material.WHITE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS,
                Material.ORANGE_STAINED_GLASS,
                Material.RED_STAINED_GLASS,
                Material.GRAY_STAINED_GLASS,
                Material.BLACK_STAINED_GLASS,
                Material.GLOWSTONE,
                Material.MAGMA_BLOCK
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) {
            return List.of();
        }

        double heat = clamp(params.getFloat("heat", 0.7), 0.0, 1.0);
        int height = Math.max(1, params.getInt("height", 8));
        int width = Math.max(1, params.getInt("width", 3));
        double flicker = clamp(params.getFloat("flicker", 0.4), 0.0, 1.0);
        double leanX = clamp(params.getFloat("lean_x", 0.0), -1.0, 1.0);
        double leanZ = clamp(params.getFloat("lean_z", 0.0), -1.0, 1.0);
        double density = clamp(params.getFloat("density", 0.85), 0.0, 1.0);

        NoiseField noise = new NoiseField(seed);
        Random random = new Random(seed ^ 0x9E3779B97F4A7C15L);

        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();

        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;

        int yLow = Math.max(worldMin, cy);
        int yHigh = Math.min(worldMax, cy + height);
        int leanReachX = Math.abs((int) Math.ceil(leanX * width));
        int leanReachZ = Math.abs((int) Math.ceil(leanZ * width));
        int xLow = cx - width - leanReachX - 1;
        int xHigh = cx + width + leanReachX + 1;
        int zLow = cz - width - leanReachZ - 1;
        int zHigh = cz + width + leanReachZ + 1;

        List<BlockChange> changes = new ArrayList<>();

        for (int y = yLow; y <= yHigh; y++) {
            double t = ((double) (y - cy)) / Math.max(1, height);
            if (t < 0) continue;
            if (t > 1) continue;
            double topThin = 1.0 - smoothstep(t);
            double profile = Math.max(0.05, topThin);
            double radiusHere = Math.max(0.5, width * profile);

            double offsetX = leanX * t * width;
            double offsetZ = leanZ * t * width;

            for (int x = xLow; x <= xHigh; x++) {
                double dx = (x - cx) - offsetX;
                for (int z = zLow; z <= zHigh; z++) {
                    double dz = (z - cz) - offsetZ;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double nd = dist / radiusHere;
                    if (nd > 1.5) continue;

                    double n = noise.fbm(x * 0.42, y * 0.35, z * 0.42, 2);
                    double shifted = nd + flicker * 0.6 * n;
                    if (shifted < 0) shifted = 0;
                    if (shifted > 1.0) continue;

                    double placement = FalloffCurve.smooth(shifted) * topThin * density;
                    if (placement <= 0) continue;
                    if (random.nextDouble() > placement) continue;

                    double zoneT = clamp(shifted * (1.25 - heat * 0.45), 0.0, 1.0);
                    Material material = flameMaterial(zoneT, heat, random);
                    if (material == null) continue;

                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    block.setType(material, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }

        return changes;
    }

    private Material flameMaterial(double zoneT, double heat, Random random) {
        if (heat < 0.25) {
            if (zoneT < 0.25) {
                return weighted(random, Material.RED_STAINED_GLASS, 3, Material.ORANGE_STAINED_GLASS, 2, Material.MAGMA_BLOCK, 1);
            }
            if (zoneT < 0.65) {
                return weighted(random, Material.GRAY_STAINED_GLASS, 3, Material.RED_STAINED_GLASS, 2, Material.MAGMA_BLOCK, 1);
            }
            return weighted(random, Material.GRAY_STAINED_GLASS, 3, Material.BLACK_STAINED_GLASS, 2, Material.RED_STAINED_GLASS, 1);
        }
        if (zoneT < 0.28) {
            return heat > 0.92
                    ? weighted(random, Material.YELLOW_STAINED_GLASS, 4, Material.GLOWSTONE, 2, Material.WHITE_STAINED_GLASS, 1)
                    : weighted(random, Material.YELLOW_STAINED_GLASS, 4, Material.ORANGE_STAINED_GLASS, 3, Material.GLOWSTONE, 1);
        }
        if (zoneT < 0.68) {
            return weighted(random, Material.ORANGE_STAINED_GLASS, 5, Material.YELLOW_STAINED_GLASS, 2, Material.RED_STAINED_GLASS, 1);
        }
        return weighted(random, Material.RED_STAINED_GLASS, 3, Material.ORANGE_STAINED_GLASS, 2, Material.MAGMA_BLOCK, 1);
    }

    private Material weighted(Random random, Material a, int aw, Material b, int bw, Material c, int cw) {
        int roll = random.nextInt(Math.max(1, aw + bw + cw));
        if (roll < aw) return a;
        if (roll < aw + bw) return b;
        return c;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double smoothstep(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t * t * (3.0 - 2.0 * t);
    }
}
