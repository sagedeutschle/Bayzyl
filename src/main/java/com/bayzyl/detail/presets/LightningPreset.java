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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class LightningPreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.intRange("length", 14, 2, 96, "Total length of the main bolt in blocks"),
            DetailBrushParameterSpec.floatRange("jaggedness", 0.55, 0.0, 1.0, "Sideways jitter. 0 straight, 1 chaotic"),
            DetailBrushParameterSpec.intRange("branches", 2, 0, 8, "Number of forked branches off the main bolt"),
            DetailBrushParameterSpec.floatRange("branch_length", 0.45, 0.0, 1.0, "Branch length as fraction of main bolt"),
            DetailBrushParameterSpec.floatRange("glow", 0.6, 0.0, 1.0, "Glowstone weight. Higher = more luminous core"),
            DetailBrushParameterSpec.string("direction", "down", "Travel direction: down, up, north, south, east, west"),
            DetailBrushParameterSpec.string("color", "blue", "Bolt color: blue, white, purple, yellow, red")
    );

    @Override
    public String id() {
        return "lightning";
    }

    @Override
    public int stampCooldownTicks() {
        return 6;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int length = Math.max(2, parameters.getInt("length", 14));
        int branches = Math.max(0, parameters.getInt("branches", 2));
        double branchLen = Math.max(0.0, Math.min(1.0, parameters.getFloat("branch_length", 0.45)));
        return length + (int) Math.round(branches * length * branchLen);
    }

    @Override
    public String displayName() {
        return "Lightning";
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
                Material.GLOWSTONE,
                Material.SEA_LANTERN,
                Material.SHROOMLIGHT,
                Material.CRYING_OBSIDIAN,
                Material.WHITE_STAINED_GLASS,
                Material.LIGHT_BLUE_STAINED_GLASS,
                Material.BLUE_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS,
                Material.RED_STAINED_GLASS
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) {
            return List.of();
        }

        int length = Math.max(2, params.getInt("length", 14));
        double jaggedness = clamp(params.getFloat("jaggedness", 0.55), 0.0, 1.0);
        int branches = Math.max(0, params.getInt("branches", 2));
        double branchLengthFactor = clamp(params.getFloat("branch_length", 0.45), 0.0, 1.0);
        double glow = clamp(params.getFloat("glow", 0.6), 0.0, 1.0);
        int[] direction = parseDirection(params.get("direction", "down"));
        BoltPalette palette = paletteFor(params.get("color", "blue"));

        Random random = new Random(seed);
        List<BlockChange> changes = new ArrayList<>();
        Set<Long> placed = new HashSet<>();

        traceBolt(world, target.getBlockX(), target.getBlockY(), target.getBlockZ(),
                direction, length, jaggedness, glow, palette, random, changes, placed);

        for (int b = 0; b < branches; b++) {
            int branchLen = Math.max(2, (int) Math.round(length * branchLengthFactor));
            int startStep = 1 + random.nextInt(Math.max(1, length - 1));
            int[] cursor = walkPosition(target.getBlockX(), target.getBlockY(), target.getBlockZ(), direction, startStep);
            int[] branchDir = perturbDirection(direction, random);
            traceBolt(world, cursor[0], cursor[1], cursor[2], branchDir, branchLen,
                    Math.min(1.0, jaggedness + 0.15), glow * 0.8, palette, random, changes, placed);
        }

        return changes;
    }

    private void traceBolt(World world, int x, int y, int z, int[] dir, int length,
                           double jaggedness, double glow, BoltPalette palette, Random random,
                           List<BlockChange> changes, Set<Long> placed) {
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;
        int cx = x;
        int cy = y;
        int cz = z;
        for (int i = 0; i < length; i++) {
            if (cy < worldMin || cy > worldMax) {
                return;
            }

            placeBoltBlock(world, cx, cy, cz, glow, palette, random, changes, placed);
            if (random.nextDouble() < 0.35 + glow * 0.35) {
                int axis = random.nextBoolean() ? 0 : 2;
                int sign = random.nextBoolean() ? 1 : -1;
                placeBoltBlock(world, cx + (axis == 0 ? sign : 0), cy, cz + (axis == 2 ? sign : 0),
                        glow * 0.35, palette.withAura(), random, changes, placed);
            }

            cx += dir[0];
            cy += dir[1];
            cz += dir[2];

            if (random.nextDouble() < jaggedness) {
                int axis = random.nextInt(3);
                int sign = random.nextBoolean() ? 1 : -1;
                if (axis == 0) cx += sign;
                else if (axis == 1) cy += sign;
                else cz += sign;
            }

        }
    }

    private void placeBoltBlock(World world, int x, int y, int z, double glow, BoltPalette palette, Random random,
                                List<BlockChange> changes, Set<Long> placed) {
        long key = packKey(x, y, z);
        if (!placed.add(key)) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) {
            return;
        }
        Material material = random.nextDouble() < glow ? palette.core : palette.glass;
        BlockData before = block.getBlockData().clone();
        block.setType(material, false);
        BlockData after = block.getBlockData().clone();
        if (!before.matches(after)) {
            changes.add(new BlockChange(block.getLocation(), before, after));
        }
    }

    private int[] perturbDirection(int[] base, Random random) {
        int[] result = new int[]{base[0], base[1], base[2]};
        int axis = random.nextInt(3);
        int sign = random.nextBoolean() ? 1 : -1;
        if (axis == 0) result[0] += sign;
        else if (axis == 1) result[1] += sign;
        else result[2] += sign;
        // Normalize back to unit-ish step
        if (result[0] != 0) result[0] = Integer.signum(result[0]);
        if (result[1] != 0) result[1] = Integer.signum(result[1]);
        if (result[2] != 0) result[2] = Integer.signum(result[2]);
        if (result[0] == 0 && result[1] == 0 && result[2] == 0) {
            return base;
        }
        return result;
    }

    private int[] walkPosition(int x, int y, int z, int[] dir, int steps) {
        return new int[]{x + dir[0] * steps, y + dir[1] * steps, z + dir[2] * steps};
    }

    private int[] parseDirection(String raw) {
        return switch (raw == null ? "down" : raw.trim().toLowerCase(Locale.ROOT)) {
            case "up" -> new int[]{0, 1, 0};
            case "north" -> new int[]{0, 0, -1};
            case "south" -> new int[]{0, 0, 1};
            case "east" -> new int[]{1, 0, 0};
            case "west" -> new int[]{-1, 0, 0};
            default -> new int[]{0, -1, 0};
        };
    }

    private long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private BoltPalette paletteFor(String raw) {
        return switch (raw == null ? "blue" : raw.trim().toLowerCase(Locale.ROOT)) {
            case "white" -> new BoltPalette(Material.SEA_LANTERN, Material.WHITE_STAINED_GLASS);
            case "purple" -> new BoltPalette(Material.CRYING_OBSIDIAN, Material.PURPLE_STAINED_GLASS);
            case "yellow" -> new BoltPalette(Material.GLOWSTONE, Material.YELLOW_STAINED_GLASS);
            case "red" -> new BoltPalette(Material.SHROOMLIGHT, Material.RED_STAINED_GLASS);
            default -> new BoltPalette(Material.SEA_LANTERN, Material.LIGHT_BLUE_STAINED_GLASS);
        };
    }

    private record BoltPalette(Material core, Material glass) {
        BoltPalette withAura() {
            return new BoltPalette(glass, glass);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
