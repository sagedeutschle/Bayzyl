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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Volumetric hearts + sparkles brush. For funsies.
 *
 * Each stamp drops one or more hearts (implicit-equation 2D silhouette
 * extruded a couple blocks deep) facing a chosen direction, then scatters
 * sparkle blocks around the cluster.
 */
public final class HeartsPreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.intRange("size", 4, 2, 12, "Heart size in blocks (radius of bounding box)"),
            DetailBrushParameterSpec.intRange("count", 1, 1, 5, "Number of hearts per stamp"),
            DetailBrushParameterSpec.intRange("spread", 3, 0, 8, "Scatter radius for multi-heart clusters"),
            DetailBrushParameterSpec.floatRange("density", 0.92, 0.0, 1.0, "Heart placement chance multiplier"),
            DetailBrushParameterSpec.floatRange("sparkle_density", 0.4, 0.0, 1.0, "Sparkles around the cluster"),
            DetailBrushParameterSpec.string("tone", "mixed", "Color family: pink, red, magenta, mixed"),
            DetailBrushParameterSpec.string("rotation", "face", "Heart facing: face, up, down, north, south, east, west")
    );

    @Override
    public String id() {
        return "hearts";
    }

    @Override
    public String displayName() {
        return "Hearts & Sparkles";
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
    public int stampCooldownTicks() {
        return 4;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int size = Math.max(2, parameters.getInt("size", 4));
        int count = Math.max(1, parameters.getInt("count", 1));
        double density = Math.max(0.0, Math.min(1.0, parameters.getFloat("density", 0.92)));
        double sparkleDensity = Math.max(0.0, Math.min(1.0, parameters.getFloat("sparkle_density", 0.4)));
        // Heart silhouette area ~= 2.5 * size^2; depth = max(1, size/3); ×count.
        int heartCells = (int) Math.round(2.5 * size * size * Math.max(1, size / 3) * density);
        int sparkles = (int) Math.round(sparkleDensity * 8.0 * count);
        return heartCells * count + sparkles;
    }

    @Override
    public Set<Material> transparentTargetMaterials() {
        return Set.of(
                Material.PINK_STAINED_GLASS,
                Material.RED_STAINED_GLASS,
                Material.MAGENTA_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS,
                Material.PINK_WOOL,
                Material.RED_WOOL,
                Material.MAGENTA_WOOL,
                Material.PINK_CONCRETE,
                Material.RED_CONCRETE,
                Material.MAGENTA_CONCRETE,
                Material.END_ROD,
                Material.SEA_LANTERN,
                Material.GLOWSTONE,
                Material.SHROOMLIGHT,
                Material.AMETHYST_BLOCK
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) return List.of();

        int size = clampInt(params.getInt("size", 4), 2, 12);
        int count = clampInt(params.getInt("count", 1), 1, 5);
        int spread = clampInt(params.getInt("spread", 3), 0, 8);
        double density = clamp(params.getFloat("density", 0.92), 0.0, 1.0);
        double sparkleDensity = clamp(params.getFloat("sparkle_density", 0.4), 0.0, 1.0);
        String tone = params.get("tone", "mixed").toLowerCase(Locale.ROOT);
        String rotation = params.get("rotation", "face").toLowerCase(Locale.ROOT);

        BlockFace facing = resolveFacing(player, rotation);
        Random random = new Random(seed ^ 0xC0FFEE_DEAD_BEEFL);

        List<HeartCenter> centers = new ArrayList<>();
        if (count == 1) {
            centers.add(new HeartCenter(0, 0, 0, size));
        } else {
            for (int i = 0; i < count; i++) {
                int dx = randRange(random, -spread, spread);
                int dy = randRange(random, -spread / 2, spread);
                int dz = randRange(random, -spread, spread);
                int hSize = clampInt(size + random.nextInt(3) - 1, 2, 12);
                centers.add(new HeartCenter(dx, dy, dz, hSize));
            }
        }

        List<BlockChange> changes = new ArrayList<>();
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;

        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();

        for (HeartCenter heart : centers) {
            renderHeart(world, cx + heart.dx, cy + heart.dy, cz + heart.dz, heart.size, tone, facing,
                    density, random, worldMin, worldMax, changes);
        }

        // Sparkles scattered through a sphere around the cluster.
        int sparkleCount = (int) Math.round(sparkleDensity * 8.0 * count);
        int sparkleRadius = size + spread + 2;
        for (int i = 0; i < sparkleCount; i++) {
            int ox = randRange(random, -sparkleRadius, sparkleRadius);
            int oy = randRange(random, -sparkleRadius / 2, sparkleRadius);
            int oz = randRange(random, -sparkleRadius, sparkleRadius);
            int sx = cx + ox;
            int sy = cy + oy;
            int sz = cz + oz;
            if (sy < worldMin || sy > worldMax) continue;
            Block block = world.getBlockAt(sx, sy, sz);
            if (!isAir(block.getType())) continue;
            Material sparkle = sparkleMaterial(random);
            BlockData before = block.getBlockData().clone();
            block.setType(sparkle, false);
            BlockData after = block.getBlockData().clone();
            if (!before.matches(after)) {
                changes.add(new BlockChange(block.getLocation(), before, after));
            }
        }

        return changes;
    }

    private void renderHeart(World world, int cx, int cy, int cz, int size, String tone, BlockFace facing,
                             double density, Random random, int worldMin, int worldMax,
                             List<BlockChange> changes) {
        int half = size;
        int depth = Math.max(1, size / 3);
        // Iterate the heart's local plane (u, v) and depth (d).
        for (int u = -half; u <= half; u++) {
            for (int v = -half; v <= half; v++) {
                double nu = u / (double) Math.max(1, size);
                double nv = v / (double) Math.max(1, size);
                if (!insideHeart(nu, nv)) continue;

                // Distance-from-center used for zone palette weighting.
                double zone = Math.min(1.0, Math.sqrt(nu * nu + nv * nv));
                for (int d = -depth / 2; d <= (depth + 1) / 2; d++) {
                    if (random.nextDouble() > density) continue;
                    int[] world_xyz = projectToWorld(cx, cy, cz, u, v, d, facing);
                    int wx = world_xyz[0];
                    int wy = world_xyz[1];
                    int wz = world_xyz[2];
                    if (wy < worldMin || wy > worldMax) continue;
                    Block block = world.getBlockAt(wx, wy, wz);
                    if (!isAir(block.getType())) continue;
                    Material material = heartMaterial(zone, tone, random);
                    if (material == null) continue;
                    BlockData before = block.getBlockData().clone();
                    block.setType(material, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }
    }

    /**
     * Classic implicit heart curve: (x² + y² - 1)³ - x²·y³ ≤ 0.
     * The curve points UP (lobes at top, point at bottom) for normal y-up.
     */
    private static boolean insideHeart(double x, double y) {
        double a = (x * x + y * y - 1.0);
        double left = a * a * a;
        double right = x * x * y * y * y;
        return left - right <= 0.0;
    }

    private int[] projectToWorld(int cx, int cy, int cz, int u, int v, int d, BlockFace facing) {
        int x = cx, y = cy, z = cz;
        switch (facing) {
            case NORTH -> { x = cx + u; y = cy + v; z = cz + d; }       // facing -z, depth toward +z
            case SOUTH -> { x = cx - u; y = cy + v; z = cz - d; }       // facing +z, mirror x so heart isn't backward
            case EAST  -> { z = cz + u; y = cy + v; x = cx - d; }       // facing +x, depth toward -x (toward player)
            case WEST  -> { z = cz - u; y = cy + v; x = cx + d; }       // facing -x
            case UP    -> { x = cx + u; z = cz + v; y = cy - d; }       // lying flat, viewed from above
            case DOWN  -> { x = cx + u; z = cz - v; y = cy + d; }       // ceiling heart
            default    -> { x = cx + u; y = cy + v; z = cz + d; }
        }
        return new int[] { x, y, z };
    }

    private BlockFace resolveFacing(Player player, String rotation) {
        return switch (rotation) {
            case "up" -> BlockFace.UP;
            case "down" -> BlockFace.DOWN;
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            case "face" -> {
                BlockFace f = player.getFacing();
                yield (f == BlockFace.UP || f == BlockFace.DOWN) ? BlockFace.NORTH : f;
            }
            default -> player.getFacing();
        };
    }

    private Material heartMaterial(double zone, String tone, Random random) {
        // zone 0 = core, zone 1 = edge.
        boolean core = zone < 0.45;
        boolean mid = zone < 0.8;
        // Tone selection per heart, but allow individual block variation.
        String activeTone = tone.equals("mixed")
                ? switch (random.nextInt(3)) { case 0 -> "pink"; case 1 -> "red"; default -> "magenta"; }
                : tone;
        return switch (activeTone) {
            case "pink" -> core
                    ? weighted(random, Material.PINK_CONCRETE, 5, Material.PINK_WOOL, 2, Material.MAGENTA_CONCRETE, 1)
                    : (mid
                        ? weighted(random, Material.PINK_WOOL, 4, Material.PINK_CONCRETE, 2, Material.PINK_STAINED_GLASS, 1)
                        : weighted(random, Material.PINK_STAINED_GLASS, 4, Material.PINK_WOOL, 2, Material.WHITE_STAINED_GLASS, 1));
            case "red" -> core
                    ? weighted(random, Material.RED_CONCRETE, 5, Material.REDSTONE_BLOCK, 2, Material.RED_WOOL, 1)
                    : (mid
                        ? weighted(random, Material.RED_WOOL, 4, Material.RED_CONCRETE, 2, Material.RED_STAINED_GLASS, 1)
                        : weighted(random, Material.RED_STAINED_GLASS, 4, Material.RED_WOOL, 2, Material.PINK_STAINED_GLASS, 1));
            case "magenta" -> core
                    ? weighted(random, Material.MAGENTA_CONCRETE, 5, Material.MAGENTA_WOOL, 2, Material.PURPLE_CONCRETE, 1)
                    : (mid
                        ? weighted(random, Material.MAGENTA_WOOL, 4, Material.MAGENTA_CONCRETE, 2, Material.MAGENTA_STAINED_GLASS, 1)
                        : weighted(random, Material.MAGENTA_STAINED_GLASS, 4, Material.MAGENTA_WOOL, 2, Material.PURPLE_STAINED_GLASS, 1));
            default -> core
                    ? weighted(random, Material.PINK_CONCRETE, 3, Material.RED_CONCRETE, 3, Material.MAGENTA_CONCRETE, 2)
                    : weighted(random, Material.PINK_WOOL, 3, Material.RED_WOOL, 3, Material.MAGENTA_WOOL, 2);
        };
    }

    private Material sparkleMaterial(Random random) {
        int roll = random.nextInt(100);
        if (roll < 35) return Material.END_ROD;
        if (roll < 60) return Material.SEA_LANTERN;
        if (roll < 80) return Material.GLOWSTONE;
        if (roll < 92) return Material.SHROOMLIGHT;
        return Material.AMETHYST_BLOCK;
    }

    private Material weighted(Random random, Material a, int aw, Material b, int bw, Material c, int cw) {
        int roll = random.nextInt(Math.max(1, aw + bw + cw));
        if (roll < aw) return a;
        if (roll < aw + bw) return b;
        return c;
    }

    private static boolean isAir(Material mat) {
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int randRange(Random r, int lo, int hi) {
        if (hi < lo) { int t = lo; lo = hi; hi = t; }
        return lo + r.nextInt(hi - lo + 1);
    }

    private record HeartCenter(int dx, int dy, int dz, int size) {}
}
