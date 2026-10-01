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
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class CloudPreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.intRange("volume", 6, 1, 32, "Overall radius of the cloud mass"),
            DetailBrushParameterSpec.floatRange("puffiness", 0.6, 0.0, 1.0, "Lobe break-up. 0 sphere, 1 broken puffs"),
            DetailBrushParameterSpec.floatRange("density", 0.7, 0.0, 1.0, "Fill chance multiplier"),
            DetailBrushParameterSpec.floatRange("flatness", 0.35, 0.0, 1.0, "Vertical squash. 0 round, 1 flat pancake"),
            DetailBrushParameterSpec.floatRange("opacity", 0.72, 0.0, 1.0, "Solid block weight. 0 glassy, 1 mostly solid cloud"),
            DetailBrushParameterSpec.string("tint", "white", "Palette: white, gray, sunset, storm")
    );

    @Override
    public String id() {
        return "cloud";
    }

    @Override
    public int stampCooldownTicks() {
        return 3;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int volume = Math.max(1, parameters.getInt("volume", 6));
        double density = Math.max(0.0, Math.min(1.0, parameters.getFloat("density", 0.7)));
        double flatness = Math.max(0.0, Math.min(1.0, parameters.getFloat("flatness", 0.35)));
        double squash = 1.0 - flatness * 0.5;
        return (int) Math.round((4.0 / 3.0) * Math.PI * volume * volume * volume * density * squash);
    }

    @Override
    public String displayName() {
        return "Cloud";
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
                Material.LIGHT_GRAY_STAINED_GLASS,
                Material.GRAY_STAINED_GLASS,
                Material.LIGHT_BLUE_STAINED_GLASS,
                Material.PINK_STAINED_GLASS,
                Material.ORANGE_STAINED_GLASS,
                Material.WHITE_CONCRETE,
                Material.LIGHT_GRAY_CONCRETE,
                Material.GRAY_CONCRETE,
                Material.PINK_CONCRETE,
                Material.ORANGE_CONCRETE,
                Material.SNOW_BLOCK
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) {
            return List.of();
        }

        int volume = Math.max(1, params.getInt("volume", 6));
        double puffiness = clamp(params.getFloat("puffiness", 0.6), 0.0, 1.0);
        double density = clamp(params.getFloat("density", 0.7), 0.0, 1.0);
        double flatness = clamp(params.getFloat("flatness", 0.35), 0.0, 1.0);
        double opacity = clamp(params.getFloat("opacity", 0.72), 0.0, 1.0);
        String tint = params.get("tint", "white").toLowerCase(Locale.ROOT);

        NoiseField noise = new NoiseField(seed);
        Random random = new Random(seed ^ 0xBF58476D1CE4E5B9L);

        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();

        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;

        double yScale = 1.0 + flatness * 1.5; // squash vertical
        int verticalReach = Math.max(1, (int) Math.ceil(volume / yScale));

        int xLow = cx - volume - 1;
        int xHigh = cx + volume + 1;
        int yLow = Math.max(worldMin, cy - verticalReach - 1);
        int yHigh = Math.min(worldMax, cy + verticalReach + 1);
        int zLow = cz - volume - 1;
        int zHigh = cz + volume + 1;

        List<BlockChange> changes = new ArrayList<>();

        for (int x = xLow; x <= xHigh; x++) {
            double dx = x - cx;
            for (int y = yLow; y <= yHigh; y++) {
                double dy = (y - cy) * yScale;
                for (int z = zLow; z <= zHigh; z++) {
                    double dz = z - cz;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double nd = dist / volume;
                    if (nd > 1.5) continue;

                    double n = noise.fbm(x * 0.18, y * 0.22, z * 0.18, 3);
                    double shifted = nd + puffiness * 0.85 * n;
                    if (shifted < 0) shifted = 0;
                    if (shifted > 1.0) continue;

                    double placement = FalloffCurve.smooth(shifted) * density;
                    if (placement <= 0) continue;
                    if (random.nextDouble() > placement) continue;

                    Material material = cloudMaterial(tint, shifted, opacity, random);
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

    private Material cloudMaterial(String tint, double zoneT, double opacity, Random random) {
        boolean solid = random.nextDouble() < opacity;
        return switch (tint) {
            case "gray" -> grayCloud(zoneT, solid, random);
            case "storm" -> stormCloud(zoneT, solid, random);
            case "sunset" -> sunsetCloud(zoneT, solid, random);
            default -> whiteCloud(zoneT, solid, random);
        };
    }

    private Material whiteCloud(double zoneT, boolean solid, Random random) {
        if (!solid) {
            return zoneT < 0.7 ? Material.WHITE_STAINED_GLASS : Material.LIGHT_GRAY_STAINED_GLASS;
        }
        if (zoneT < 0.35) return weighted(random, Material.WHITE_CONCRETE, 4, Material.SNOW_BLOCK, 2, Material.WHITE_STAINED_GLASS, 1);
        if (zoneT < 0.75) return weighted(random, Material.WHITE_CONCRETE, 3, Material.LIGHT_GRAY_CONCRETE, 1, Material.SNOW_BLOCK, 1);
        return weighted(random, Material.LIGHT_GRAY_CONCRETE, 3, Material.WHITE_CONCRETE, 1, Material.GRAY_CONCRETE, 1);
    }

    private Material grayCloud(double zoneT, boolean solid, Random random) {
        if (!solid) {
            return zoneT < 0.6 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.GRAY_STAINED_GLASS;
        }
        if (zoneT < 0.45) return weighted(random, Material.WHITE_CONCRETE, 1, Material.LIGHT_GRAY_CONCRETE, 4, Material.SNOW_BLOCK, 1);
        return weighted(random, Material.LIGHT_GRAY_CONCRETE, 3, Material.GRAY_CONCRETE, 2, Material.GRAY_STAINED_GLASS, 1);
    }

    private Material stormCloud(double zoneT, boolean solid, Random random) {
        if (!solid) {
            return zoneT < 0.55 ? Material.GRAY_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS;
        }
        if (zoneT < 0.35) return weighted(random, Material.LIGHT_GRAY_CONCRETE, 3, Material.GRAY_CONCRETE, 2, Material.LIGHT_GRAY_STAINED_GLASS, 1);
        return weighted(random, Material.GRAY_CONCRETE, 4, Material.LIGHT_GRAY_CONCRETE, 1, Material.GRAY_STAINED_GLASS, 1);
    }

    private Material sunsetCloud(double zoneT, boolean solid, Random random) {
        if (!solid) {
            return zoneT < 0.45 ? Material.PINK_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
        }
        if (zoneT < 0.35) return weighted(random, Material.WHITE_CONCRETE, 2, Material.PINK_CONCRETE, 1, Material.PINK_STAINED_GLASS, 1);
        if (zoneT < 0.75) return weighted(random, Material.PINK_CONCRETE, 2, Material.ORANGE_CONCRETE, 1, Material.WHITE_CONCRETE, 1);
        return weighted(random, Material.ORANGE_CONCRETE, 2, Material.PINK_CONCRETE, 1, Material.LIGHT_GRAY_CONCRETE, 1);
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
}
