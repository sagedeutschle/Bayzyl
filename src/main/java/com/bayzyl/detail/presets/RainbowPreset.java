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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Linear rainbow + clouds brush. For funsies.
 *
 * Traces a parabolic arc with N nested color bands (red on top, violet on
 * bottom) and optionally drops a cumulus cloud puff at each end of the arc.
 * Direction snaps to a cardinal axis (auto = derived from player facing).
 */
public final class RainbowPreset implements DetailBrushPreset {
    private static final List<DetailBrushParameterSpec> SPECS = List.of(
            DetailBrushParameterSpec.intRange("length", 24, 8, 96, "Arc horizontal span in blocks"),
            DetailBrushParameterSpec.intRange("arc_height", 8, 4, 32, "Peak height of the arc above start"),
            DetailBrushParameterSpec.intRange("bands", 7, 3, 7, "Number of color bands"),
            DetailBrushParameterSpec.floatRange("density", 0.95, 0.0, 1.0, "Trace density along the arc"),
            DetailBrushParameterSpec.floatRange("sparkle", 0.0, 0.0, 1.0, "Sparkle density along the arc"),
            DetailBrushParameterSpec.intRange("cloud_size", 4, 0, 8, "Cloud puff radius at arc ends (0 = no clouds)"),
            DetailBrushParameterSpec.string("palette", "classic", "Palette: classic, pastel, sunset"),
            DetailBrushParameterSpec.string("heading", "auto", "Travel: auto, north, south, east, west")
    );

    private static final List<Material> CLASSIC = List.of(
            Material.RED_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.PURPLE_CONCRETE
    );

    private static final List<Material> PASTEL = List.of(
            Material.PINK_WOOL,
            Material.ORANGE_WOOL,
            Material.YELLOW_WOOL,
            Material.LIME_WOOL,
            Material.LIGHT_BLUE_WOOL,
            Material.BLUE_WOOL,
            Material.MAGENTA_WOOL
    );

    private static final List<Material> SUNSET = List.of(
            Material.RED_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.PINK_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.BLUE_CONCRETE
    );

    @Override
    public String id() {
        return "rainbow";
    }

    @Override
    public String displayName() {
        return "Rainbow & Clouds";
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
    public int stampCooldownTicks() {
        return 8;
    }

    @Override
    public int estimatedStampBlockCount(DetailBrushParameters parameters) {
        int length = Math.max(8, parameters.getInt("length", 24));
        int bands = Math.max(3, Math.min(7, parameters.getInt("bands", 7)));
        int cloudSize = Math.max(0, Math.min(8, parameters.getInt("cloud_size", 4)));
        double density = Math.max(0.0, Math.min(1.0, parameters.getFloat("density", 0.95)));
        // arc trace ~= length * 1.2 cells per band (parabola is slightly longer than chord) × density
        int arcCells = (int) Math.round(length * 1.2 * bands * density);
        int cloudCells = cloudSize > 0 ? (int) Math.round((4.0 / 3.0) * Math.PI * cloudSize * cloudSize * cloudSize * 0.6 * 2) : 0;
        return arcCells + cloudCells;
    }

    @Override
    public Set<Material> transparentTargetMaterials() {
        return Set.of(
                Material.WHITE_CONCRETE,
                Material.WHITE_WOOL,
                Material.SNOW_BLOCK,
                Material.RED_CONCRETE,
                Material.ORANGE_CONCRETE,
                Material.YELLOW_CONCRETE,
                Material.GREEN_CONCRETE,
                Material.LIGHT_BLUE_CONCRETE,
                Material.BLUE_CONCRETE,
                Material.PURPLE_CONCRETE,
                Material.PINK_CONCRETE,
                Material.MAGENTA_CONCRETE,
                Material.LIME_WOOL,
                Material.LIGHT_BLUE_WOOL,
                Material.PINK_WOOL,
                Material.MAGENTA_WOOL,
                Material.END_ROD,
                Material.GLOWSTONE
        );
    }

    @Override
    public List<BlockChange> apply(Player player, Location target, DetailBrushParameters parameters, long seed) {
        DetailBrushParameters params = parameters.mergeDefaults(SPECS);
        World world = target.getWorld();
        if (world == null) return List.of();

        int length = clampInt(params.getInt("length", 24), 8, 96);
        int arcHeight = clampInt(params.getInt("arc_height", 8), 4, 32);
        int bands = clampInt(params.getInt("bands", 7), 3, 7);
        double density = clamp(params.getFloat("density", 0.95), 0.0, 1.0);
        double sparkle = clamp(params.getFloat("sparkle", 0.0), 0.0, 1.0);
        int cloudSize = clampInt(params.getInt("cloud_size", 4), 0, 8);
        String paletteName = params.get("palette", "classic").toLowerCase(Locale.ROOT);
        String heading = params.get("heading", "auto").toLowerCase(Locale.ROOT);

        List<Material> palette = palette(paletteName);
        BlockFace travel = resolveHeading(player, heading);
        Vector forward = faceVector(travel);

        Random random = new Random(seed ^ 0xCA11AB1E_C0FFEEL);
        NoiseField noise = new NoiseField(seed);

        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;
        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();

        int sx = cx;
        int sy = cy;
        int sz = cz;
        int ex = cx + (int) Math.round(forward.getX() * length);
        int ez = cz + (int) Math.round(forward.getZ() * length);
        // Arc end y matches start y (level rainbow); peak at midpoint.

        List<BlockChange> changes = new ArrayList<>();

        // ----- Trace the arc -----
        // For each band b: outer (red, b=0) has highest peak; inner (violet, last) has lowest peak.
        // Each band gets its own parabolic arc with arc_height_band = arc_height + offset.
        int steps = Math.max(8, length * 2);
        double t = 0.0;
        double dt = 1.0 / (steps - 1);
        for (int i = 0; i < steps; i++, t = i * dt) {
            double clampedT = Math.max(0.0, Math.min(1.0, t));
            double parabola = 4.0 * clampedT * (1.0 - clampedT);
            double xPos = sx + (ex - sx) * clampedT;
            double zPos = sz + (ez - sz) * clampedT;

            for (int b = 0; b < bands; b++) {
                if (random.nextDouble() > density) continue;
                double bandHeight = arcHeight + ((bands - 1) / 2.0 - b);
                if (bandHeight < 1.0) bandHeight = 1.0;
                int y = sy + (int) Math.round(parabola * bandHeight);
                if (y < worldMin || y > worldMax) continue;
                int xi = (int) Math.round(xPos);
                int zi = (int) Math.round(zPos);
                Block block = world.getBlockAt(xi, y, zi);
                if (!isAir(block.getType())) continue;
                Material mat = palette.get(Math.min(b, palette.size() - 1));
                BlockData before = block.getBlockData().clone();
                block.setType(mat, false);
                BlockData after = block.getBlockData().clone();
                if (!before.matches(after)) {
                    changes.add(new BlockChange(block.getLocation(), before, after));
                }

                // Sparkles: small chance per arc step.
                if (sparkle > 0 && random.nextDouble() < sparkle * 0.08) {
                    int sxr = xi + randRange(random, -1, 1);
                    int szr = zi + randRange(random, -1, 1);
                    int syr = y + randRange(random, 1, 2);
                    if (syr >= worldMin && syr <= worldMax) {
                        Block sBlock = world.getBlockAt(sxr, syr, szr);
                        if (isAir(sBlock.getType())) {
                            Material sparkleMat = random.nextBoolean() ? Material.END_ROD : Material.GLOWSTONE;
                            BlockData sBefore = sBlock.getBlockData().clone();
                            sBlock.setType(sparkleMat, false);
                            BlockData sAfter = sBlock.getBlockData().clone();
                            if (!sBefore.matches(sAfter)) {
                                changes.add(new BlockChange(sBlock.getLocation(), sBefore, sAfter));
                            }
                        }
                    }
                }
            }
        }

        // ----- Cloud puffs at the ends -----
        if (cloudSize > 0) {
            // start cloud sits just below the start of the arc,
            // end cloud sits just below the end of the arc.
            int cloudYStart = sy - 1;
            int cloudYEnd = sy - 1;
            placeCloud(world, sx, cloudYStart, sz, cloudSize, noise, random, worldMin, worldMax, changes);
            placeCloud(world, ex, cloudYEnd, ez, cloudSize, noise, random, worldMin, worldMax, changes);
        }

        return changes;
    }

    private void placeCloud(World world, int cx, int cy, int cz, int radius,
                            NoiseField noise, Random random,
                            int worldMin, int worldMax, List<BlockChange> changes) {
        double r = radius;
        double rSq = r * r;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                for (int dy = -radius / 2; dy <= radius / 2 + 1; dy++) {
                    double ex = dx;
                    double ey = dy * 1.6; // squash vertically — clouds are flatter than spheres
                    double ez = dz;
                    double dSq = ex * ex + ey * ey + ez * ez;
                    if (dSq > rSq) continue;
                    double n = noise.fbm((cx + dx) * 0.4, (cy + dy) * 0.4, (cz + dz) * 0.4, 2);
                    double edge = 1.0 - (dSq / rSq); // 1 at center, 0 at edge
                    double placement = edge * 0.85 + n * 0.18;
                    if (placement <= 0.18) continue;
                    if (random.nextDouble() > placement) continue;

                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y < worldMin || y > worldMax) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (!isAir(block.getType())) continue;
                    Material mat = cloudMaterial(random);
                    BlockData before = block.getBlockData().clone();
                    block.setType(mat, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }
    }

    private Material cloudMaterial(Random random) {
        int roll = random.nextInt(100);
        if (roll < 55) return Material.WHITE_CONCRETE;
        if (roll < 85) return Material.SNOW_BLOCK;
        return Material.WHITE_WOOL;
    }

    private List<Material> palette(String name) {
        return switch (name) {
            case "pastel" -> PASTEL;
            case "sunset" -> SUNSET;
            default -> CLASSIC;
        };
    }

    private BlockFace resolveHeading(Player player, String heading) {
        return switch (heading) {
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            case "auto" -> {
                BlockFace f = player.getFacing();
                yield (f == BlockFace.UP || f == BlockFace.DOWN) ? BlockFace.SOUTH : f;
            }
            default -> player.getFacing();
        };
    }

    private static Vector faceVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            default -> new Vector(0, 0, 1);
        };
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
}
