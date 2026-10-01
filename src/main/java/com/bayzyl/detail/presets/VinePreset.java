package com.bayzyl.detail.presets;

import com.bayzyl.BlockChange;
import com.bayzyl.detail.DetailBrushFamily;
import com.bayzyl.detail.DetailBrushParameterSpec;
import com.bayzyl.detail.DetailBrushParameters;
import com.bayzyl.detail.DetailBrushPreset;
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

public final class VinePreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.intRange("length", 6, 1, 64, "How far the vine drops in blocks"),
            DetailBrushParameterSpec.floatRange("droop", 0.4, 0.0, 1.0, "Sideways drift while falling. 0 straight, 1 wandering"),
            DetailBrushParameterSpec.floatRange("leaf_density", 0.35, 0.0, 1.0, "Chance of leaf clusters along the strand"),
            DetailBrushParameterSpec.floatRange("gap", 0.1, 0.0, 0.6, "Chance of skipping a step (sparse vines)"),
            DetailBrushParameterSpec.string("species", "oak", "Foliage species: oak, birch, spruce, jungle, dark_oak, azalea, mangrove, mangrove_root, cherry")
    );

    @Override
    public String id() {
        return "vine";
    }

    @Override
    public int stampCooldownTicks() {
        return 4;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int length = Math.max(1, parameters.getInt("length", 6));
        double leafDensity = Math.max(0.0, Math.min(1.0, parameters.getFloat("leaf_density", 0.4)));
        return (int) Math.round(length * (1.0 + leafDensity));
    }

    @Override
    public String displayName() {
        return "Vine";
    }

    @Override
    public DetailBrushFamily family() {
        return DetailBrushFamily.LINEAR;
    }

    @Override
    public List<DetailBrushParameterSpec> parameterSpecs() {
        return SPECS;
    }

    @Override
    public Set<Material> transparentTargetMaterials() {
        return Set.of(
                Material.VINE,
                Material.OAK_LEAVES, Material.BIRCH_LEAVES, Material.SPRUCE_LEAVES,
                Material.JUNGLE_LEAVES, Material.DARK_OAK_LEAVES, Material.AZALEA_LEAVES,
                Material.FLOWERING_AZALEA_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
                Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) {
            return List.of();
        }

        int length = Math.max(1, params.getInt("length", 6));
        double droop = clamp(params.getFloat("droop", 0.4), 0.0, 1.0);
        double leafDensity = clamp(params.getFloat("leaf_density", 0.35), 0.0, 1.0);
        double gap = clamp(params.getFloat("gap", 0.1), 0.0, 0.6);
        String species = params.get("species", "oak");
        boolean rootMode = isMangroveRoot(species);
        Material strand = rootMode ? Material.MANGROVE_ROOTS : Material.VINE;
        Material leaves = leavesFor(species);

        Random random = new Random(seed);
        List<BlockChange> changes = new ArrayList<>();

        int worldMin = world.getMinHeight();
        int x = target.getBlockX();
        int y = target.getBlockY();
        int z = target.getBlockZ();

        if (rootMode) {
            traceMangroveRoots(world, x, y, z, length, droop, leafDensity, gap, random, changes);
            return changes;
        }

        for (int i = 0; i < length; i++) {
            y -= 1;
            if (y < worldMin) break;

            if (random.nextDouble() < droop * 0.45) {
                int axis = random.nextBoolean() ? 0 : 2;
                int sign = random.nextBoolean() ? 1 : -1;
                if (axis == 0) x += sign;
                else z += sign;
            }

            if (random.nextDouble() < gap) {
                continue;
            }

            placeBlock(world, x, y, z, strand, changes);

            if (random.nextDouble() < leafDensity && !rootMode) {
                int dxOff = random.nextInt(3) - 1;
                int dzOff = random.nextInt(3) - 1;
                if (dxOff != 0 || dzOff != 0) {
                    placeBlock(world, x + dxOff, y, z + dzOff, leaves, changes);
                }
                if (random.nextDouble() < leafDensity * 0.6) {
                    placeBlock(world, x, y - 1, z, leaves, changes);
                }
            }
        }

        return changes;
    }

    private void traceMangroveRoots(World world, int startX, int startY, int startZ, int length,
                                    double droop, double branchChance, double gap, Random random,
                                    List<BlockChange> changes) {
        int worldMin = world.getMinHeight();
        int x = startX;
        int y = startY;
        int z = startZ;
        for (int i = 0; i < length; i++) {
            y -= 1;
            if (y < worldMin) break;

            if (i > 1 && random.nextDouble() < droop * 0.35) {
                int axis = random.nextBoolean() ? 0 : 2;
                int sign = random.nextBoolean() ? 1 : -1;
                if (axis == 0) x += sign;
                else z += sign;
            }

            if (random.nextDouble() >= gap) {
                placeBlock(world, x, y, z, rootMaterial(random), changes);
            }

            if (i > 1 && random.nextDouble() < branchChance) {
                int branches = 1 + random.nextInt(2);
                for (int b = 0; b < branches; b++) {
                    traceRootBranch(world, x, y, z, 2 + random.nextInt(4), random, changes);
                }
            }
        }
    }

    private void traceRootBranch(World world, int startX, int startY, int startZ, int length,
                                 Random random, List<BlockChange> changes) {
        int worldMin = world.getMinHeight();
        int x = startX;
        int y = startY;
        int z = startZ;
        int dx = random.nextBoolean() ? (random.nextBoolean() ? 1 : -1) : 0;
        int dz = dx == 0 ? (random.nextBoolean() ? 1 : -1) : 0;
        for (int i = 0; i < length; i++) {
            if (random.nextDouble() < 0.7) {
                x += dx;
                z += dz;
            }
            if (random.nextDouble() < 0.65) {
                y -= 1;
            }
            if (y < worldMin) {
                return;
            }
            placeBlock(world, x, y, z, rootMaterial(random), changes);
        }
    }

    private Material rootMaterial(Random random) {
        return random.nextDouble() < 0.78 ? Material.MANGROVE_ROOTS : Material.MUDDY_MANGROVE_ROOTS;
    }

    private void placeBlock(World world, int x, int y, int z, Material material, List<BlockChange> changes) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) {
            return;
        }
        BlockData before = block.getBlockData().clone();
        block.setType(material, false);
        BlockData after = block.getBlockData().clone();
        if (!before.matches(after)) {
            changes.add(new BlockChange(block.getLocation(), before, after));
        }
    }

    private Material leavesFor(String species) {
        return switch (species == null ? "oak" : species.trim().toLowerCase(Locale.ROOT)) {
            case "birch" -> Material.BIRCH_LEAVES;
            case "spruce" -> Material.SPRUCE_LEAVES;
            case "jungle" -> Material.JUNGLE_LEAVES;
            case "dark_oak", "darkoak" -> Material.DARK_OAK_LEAVES;
            case "azalea" -> Material.AZALEA_LEAVES;
            case "flowering_azalea", "flowering" -> Material.FLOWERING_AZALEA_LEAVES;
            case "mangrove" -> Material.MANGROVE_LEAVES;
            case "mangrove_root", "mangrove_roots", "root", "roots" -> Material.MANGROVE_ROOTS;
            case "cherry" -> Material.CHERRY_LEAVES;
            default -> Material.OAK_LEAVES;
        };
    }

    private boolean isMangroveRoot(String species) {
        String normalized = species == null ? "" : species.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("mangrove_root") || normalized.equals("mangrove_roots")
                || normalized.equals("root") || normalized.equals("roots");
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
