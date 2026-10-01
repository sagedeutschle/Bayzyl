package com.bayzyl.detail.presets;

import com.bayzyl.BlockChange;
import com.bayzyl.detail.DetailBrushFamily;
import com.bayzyl.detail.DetailBrushParameterSpec;
import com.bayzyl.detail.DetailBrushParameters;
import com.bayzyl.detail.DetailBrushPreset;
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

public final class BarkPreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.intRange("radius", 3, 1, 16, "Horizontal radius around the click target"),
            DetailBrushParameterSpec.intRange("height", 4, 1, 32, "Vertical reach above and below the click target"),
            DetailBrushParameterSpec.floatRange("grain", 0.5, 0.0, 1.0, "Vertical streak density. Higher = more pronounced grain"),
            DetailBrushParameterSpec.floatRange("knots", 0.18, 0.0, 1.0, "Frequency of knot/stripped patches"),
            DetailBrushParameterSpec.floatRange("coverage", 0.7, 0.0, 1.0, "Chance of overwriting a matching log block"),
            DetailBrushParameterSpec.string("species", "oak", "Wood species: oak, birch, spruce, jungle, dark_oak, acacia, mangrove, cherry, crimson, warped")
    );

    @Override
    public String id() {
        return "bark";
    }

    @Override
    public int stampCooldownTicks() {
        return 3;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int radius = Math.max(1, parameters.getInt("radius", 3));
        int height = Math.max(1, parameters.getInt("height", 4));
        double coverage = Math.max(0.0, Math.min(1.0, parameters.getFloat("coverage", 0.7)));
        return (int) Math.round(2.0 * Math.PI * radius * height * coverage);
    }

    @Override
    public String displayName() {
        return "Bark";
    }

    @Override
    public DetailBrushFamily family() {
        return DetailBrushFamily.SURFACE;
    }

    @Override
    public List<DetailBrushParameterSpec> parameterSpecs() {
        return SPECS;
    }

    @Override
    public Set<Material> transparentTargetMaterials() {
        return Set.of(
                Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD,
                Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD,
                Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD,
                Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD,
                Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD,
                Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD,
                Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD,
                Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD,
                Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE,
                Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) {
            return List.of();
        }

        int radius = Math.max(1, params.getInt("radius", 3));
        int height = Math.max(1, params.getInt("height", 4));
        double grain = clamp(params.getFloat("grain", 0.5), 0.0, 1.0);
        double knots = clamp(params.getFloat("knots", 0.18), 0.0, 1.0);
        double coverage = clamp(params.getFloat("coverage", 0.7), 0.0, 1.0);
        Species species = speciesFor(params.get("species", "oak"));

        NoiseField noise = new NoiseField(seed);
        Random random = new Random(seed ^ 0x94D049BB133111EBL);
        List<BlockChange> changes = new ArrayList<>();

        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;

        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                for (int dy = -height; dy <= height; dy++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y < worldMin || y > worldMax) continue;

                    Block block = world.getBlockAt(x, y, z);
                    if (!isBarkCandidate(block.getType())) continue;
                    if (random.nextDouble() > coverage) continue;

                    double grainNoise = noise.fbm(x * 0.5, y * 1.3, z * 0.5, 2);
                    double knotNoise = noise.sample(x * 0.9, y * 0.4, z * 0.9);

                    Material chosen;
                    if (knotNoise > 1.0 - knots) {
                        chosen = species.stripped;
                    } else if (grainNoise > 1.0 - grain * 0.6) {
                        chosen = species.wood;
                    } else if (grainNoise < -1.0 + grain * 0.5) {
                        chosen = species.strippedWood;
                    } else {
                        chosen = species.log;
                    }

                    if (block.getType() == chosen) continue;
                    BlockData before = block.getBlockData().clone();
                    block.setType(chosen, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }

        return changes;
    }

    private boolean isBarkCandidate(Material material) {
        return transparentTargetMaterials().contains(material);
    }

    private Species speciesFor(String raw) {
        return switch (raw == null ? "oak" : raw.trim().toLowerCase(Locale.ROOT)) {
            case "birch" -> new Species(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD);
            case "spruce" -> new Species(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD);
            case "jungle" -> new Species(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD);
            case "dark_oak", "darkoak" -> new Species(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD);
            case "acacia" -> new Species(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD);
            case "mangrove" -> new Species(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD);
            case "cherry" -> new Species(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);
            case "crimson" -> new Species(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE);
            case "warped" -> new Species(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE);
            default -> new Species(Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD);
        };
    }

    private record Species(Material log, Material stripped, Material wood, Material strippedWood) {
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
