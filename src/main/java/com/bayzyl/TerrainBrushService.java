package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import com.bayzyl.safety.BrushSafety;

import java.util.ArrayList;
import java.util.List;

public final class TerrainBrushService {
    private final CleanupService cleanupService;
    private final NaturalizeService naturalizeService;

    public TerrainBrushService(CleanupService cleanupService, NaturalizeService naturalizeService) {
        this.cleanupService = cleanupService;
        this.naturalizeService = naturalizeService;
    }

    public List<BlockChange> apply(Player player, Location center, TerrainBrushSettings settings) {
        if (!BrushSafety.isValidTerrain(settings)) {
            return List.of();
        }
        return switch (settings.type()) {
            case SMOOTH -> smooth(player, center, settings.radius(), settings.power(), settings.editBedrock());
            case RAISE -> reshape(player, center, settings.radius(), settings.power(), settings.editBedrock(), ReshapeMode.RAISE);
            case LOWER -> reshape(player, center, settings.radius(), settings.power(), settings.editBedrock(), ReshapeMode.LOWER);
            case FLATTEN -> flatten(player, center, settings.radius(), settings.power(), settings.editBedrock());
            case NATURALIZE -> naturalizeService.naturalizeBrush(player, center, settings.radius(), settings.power(), settings.editBedrock());
            case CLEANUP_FLOATING, CLEANUP_FOLIAGE, CLEANUP_LIQUIDS, CLEANUP_SNOW, CLEANUP_LIGHTSPAM ->
                    cleanupService.cleanupBrush(player, center, settings);
        };
    }

    public List<BlockChange> smooth(Player player, Location center, int radius, int iterations, boolean editBedrock) {
        TerrainBrushSettings settings = new TerrainBrushSettings(
                TerrainBrushType.SMOOTH, radius, iterations, editBedrock);
        if (!BrushSafety.isValidTerrain(settings)) {
            return List.of();
        }
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight() - 1;
        int kernelRadius = 2;
        int kernelSize = (kernelRadius * 2 + 1);
        int kernelVolume = kernelSize * kernelSize * kernelSize;
        int solidThreshold = (kernelVolume + 1) / 2;
        int snapR = radius + kernelRadius;
        int snapSize = snapR * 2 + 1;

        Material[][][] bufA = new Material[snapSize][snapSize][snapSize];
        for (int dx = -snapR; dx <= snapR; dx++) {
            for (int dy = -snapR; dy <= snapR; dy++) {
                for (int dz = -snapR; dz <= snapR; dz++) {
                    int wy = cy + dy;
                    if (wy < worldMinY || wy > worldMaxY) {
                        bufA[dx + snapR][dy + snapR][dz + snapR] = Material.AIR;
                        continue;
                    }
                    bufA[dx + snapR][dy + snapR][dz + snapR] = world.getBlockAt(cx + dx, wy, cz + dz).getType();
                }
            }
        }
        Material[][][] bufB = new Material[snapSize][snapSize][snapSize];

        Material[][][] current = bufA;
        Material[][][] next = bufB;
        Material[] materialValues = Material.values();
        int[] materialCounts = new int[materialValues.length];
        int[] touchedMaterials = new int[27];
        for (int iter = 0; iter < iterations; iter++) {
            for (int ix = 0; ix < snapSize; ix++) {
                for (int iy = 0; iy < snapSize; iy++) {
                    System.arraycopy(current[ix][iy], 0, next[ix][iy], 0, snapSize);
                }
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if ((dx * dx) + (dy * dy) + (dz * dz) > radius * radius) {
                            continue;
                        }
                        int ix = dx + snapR, iy = dy + snapR, iz = dz + snapR;
                        int solidCount = 0;
                        for (int ox = -kernelRadius; ox <= kernelRadius; ox++) {
                            for (int oy = -kernelRadius; oy <= kernelRadius; oy++) {
                                for (int oz = -kernelRadius; oz <= kernelRadius; oz++) {
                                    Material m = current[ix + ox][iy + oy][iz + oz];
                                    if (m != null && !m.isAir()) {
                                        solidCount++;
                                    }
                                }
                            }
                        }
                        Material existing = current[ix][iy][iz];
                        if (solidCount >= solidThreshold) {
                            if (existing != null && !existing.isAir()) {
                                next[ix][iy][iz] = existing;
                            } else {
                                next[ix][iy][iz] = pickMajorityMaterial(
                                        current, ix, iy, iz, materialValues, materialCounts, touchedMaterials);
                            }
                        } else {
                            next[ix][iy][iz] = Material.AIR;
                        }
                    }
                }
            }
            Material[][][] swap = current;
            current = next;
            next = swap;
        }

        List<BlockChange> changes = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dy * dy) + (dz * dz) > radius * radius) {
                        continue;
                    }
                    int wy = cy + dy;
                    if (wy < worldMinY || wy > worldMaxY) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + dx, wy, cz + dz);
                    if (block.getType() == Material.BEDROCK && !editBedrock) {
                        continue;
                    }
                    Material target = current[dx + snapR][dy + snapR][dz + snapR];
                    if (target == null || block.getType() == target) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    block.setType(target, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }
        return changes;
    }

    private List<BlockChange> flatten(Player player, Location center, int radius, int power, boolean editBedrock) {
        BlockFace face = (center == null || center.getWorld() == null)
                ? BlockFace.UP
                : inferExposedFace(center.getBlock());
        if (face == BlockFace.UP) {
            return reshape(player, center, radius, power, editBedrock, ReshapeMode.FLATTEN);
        }
        return flattenOriented(player, center, radius, power, editBedrock, face);
    }

    private List<BlockChange> flattenOriented(Player player, Location center, int radius, int power,
                                              boolean editBedrock, BlockFace face) {
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        if (radius <= 0 || power <= 0) {
            ChatOutput.send(player, ChatColor.RED + "Brush radius and power must be greater than 0.");
            return List.of();
        }
        int nx = face.getModX(), ny = face.getModY(), nz = face.getModZ();
        int[] u, v;
        if (nx != 0) {
            u = new int[]{0, 1, 0};
            v = new int[]{0, 0, 1};
        } else if (nz != 0) {
            u = new int[]{1, 0, 0};
            v = new int[]{0, 1, 0};
        } else {
            u = new int[]{1, 0, 0};
            v = new int[]{0, 0, 1};
        }

        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight() - 1;
        int scanLen = radius + (power * 4) + 16;
        int size = radius * 2 + 1;

        int[][] depths = new int[size][size];
        Material[][] topMaterials = new Material[size][size];
        Material[][] fillMaterials = new Material[size][size];
        boolean[][] valid = new boolean[size][size];

        for (int iu = -radius; iu <= radius; iu++) {
            for (int iv = -radius; iv <= radius; iv++) {
                int sx = cx + iu * u[0] + iv * v[0];
                int sy = cy + iu * u[1] + iv * v[1];
                int sz = cz + iu * u[2] + iv * v[2];
                int foundT = Integer.MIN_VALUE;
                Material surfaceMat = null;
                for (int t = -scanLen; t <= scanLen; t++) {
                    int wy = sy - t * ny;
                    if (wy < worldMinY || wy > worldMaxY) {
                        continue;
                    }
                    Block block = world.getBlockAt(sx - t * nx, wy, sz - t * nz);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    foundT = t;
                    surfaceMat = block.getType();
                    break;
                }
                if (foundT == Integer.MIN_VALUE) {
                    valid[iu + radius][iv + radius] = false;
                    continue;
                }
                Material fillMat = surfaceMat;
                for (int t = foundT + 1; t <= scanLen; t++) {
                    int wy = sy - t * ny;
                    if (wy < worldMinY || wy > worldMaxY) {
                        break;
                    }
                    Material m = world.getBlockAt(sx - t * nx, wy, sz - t * nz).getType();
                    if (!m.isAir()) {
                        fillMat = m;
                        break;
                    }
                }
                depths[iu + radius][iv + radius] = foundT;
                topMaterials[iu + radius][iv + radius] = surfaceMat;
                fillMaterials[iu + radius][iv + radius] = fillMat;
                valid[iu + radius][iv + radius] = true;
            }
        }

        List<BlockChange> changes = new ArrayList<>();
        for (int iu = -radius; iu <= radius; iu++) {
            for (int iv = -radius; iv <= radius; iv++) {
                double distance = Math.sqrt((iu * iu) + (iv * iv));
                if (distance > radius) {
                    continue;
                }
                if (!valid[iu + radius][iv + radius]) {
                    continue;
                }
                double normalized = radius == 0 ? 1.0 : 1.0 - (distance / radius);
                double falloff = smoothFalloff(normalized);
                int currentDepth = depths[iu + radius][iv + radius];
                int targetDepth = flattenDepth(currentDepth, power, falloff);
                int sx = cx + iu * u[0] + iv * v[0];
                int sy = cy + iu * u[1] + iv * v[1];
                int sz = cz + iu * u[2] + iv * v[2];
                Material topMat = topMaterials[iu + radius][iv + radius];
                Material fillMat = fillMaterials[iu + radius][iv + radius];
                for (int t = -scanLen; t <= scanLen; t++) {
                    int wy = sy - t * ny;
                    if (wy < worldMinY || wy > worldMaxY) {
                        continue;
                    }
                    Block block = world.getBlockAt(sx - t * nx, wy, sz - t * nz);
                    if (block.getType() == Material.BEDROCK && !editBedrock) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    if (t < targetDepth) {
                        if (!block.getType().isAir()) {
                            block.setType(Material.AIR, false);
                        }
                    } else if (t == targetDepth) {
                        if (block.getType() != topMat) {
                            block.setType(topMat, false);
                        }
                    } else {
                        if (block.getType() != fillMat) {
                            block.setType(fillMat, false);
                        }
                    }
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }
        return changes;
    }

    private int flattenDepth(int currentDepth, int power, double falloff) {
        if (currentDepth == 0) {
            return 0;
        }
        int step = Math.max(1, (int) Math.round(power * (0.75 + (falloff * 1.25))));
        if (Math.abs(currentDepth) <= step) {
            return 0;
        }
        return currentDepth + (currentDepth > 0 ? -step : step);
    }

    private BlockFace inferExposedFace(Block target) {
        if (target == null) {
            return BlockFace.UP;
        }
        BlockFace[] candidates = {
                BlockFace.UP, BlockFace.DOWN,
                BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST
        };
        int scanRange = 16;
        int worldMinY = target.getWorld().getMinHeight();
        int worldMaxY = target.getWorld().getMaxHeight() - 1;
        BlockFace best = BlockFace.UP;
        int bestCount = -1;
        for (BlockFace face : candidates) {
            int count = 0;
            Block cursor = target;
            for (int i = 0; i < scanRange; i++) {
                cursor = cursor.getRelative(face);
                int y = cursor.getY();
                if (y < worldMinY || y > worldMaxY) {
                    break;
                }
                if (!cursor.getType().isAir()) {
                    break;
                }
                count++;
            }
            if (count > bestCount) {
                bestCount = count;
                best = face;
            }
        }
        return best;
    }

    private Material pickMajorityMaterial(Material[][][] field, int ix, int iy, int iz,
                                          Material[] materialValues, int[] counts, int[] touched) {
        int touchedCount = 0;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    Material m = field[ix + ox][iy + oy][iz + oz];
                    if (m != null && !m.isAir()) {
                        int ordinal = m.ordinal();
                        if (counts[ordinal] == 0) {
                            touched[touchedCount++] = ordinal;
                        }
                        counts[ordinal]++;
                    }
                }
            }
        }
        Material majority = Material.STONE;
        int best = 0;
        for (int i = 0; i < touchedCount; i++) {
            int ordinal = touched[i];
            if (counts[ordinal] > best) {
                best = counts[ordinal];
                majority = materialValues[ordinal];
            }
            counts[ordinal] = 0;
        }
        return majority;
    }

    private List<BlockChange> reshape(Player player, Location center, int radius, int power, boolean editBedrock, ReshapeMode mode) {
        BrushSnapshot snapshot = captureSnapshot(player, center, radius, power);
        if (snapshot == null) {
            return List.of();
        }
        int size = radius * 2 + 1;
        int[][] heights = copyHeights(snapshot.heights());
        int anchorY = center.getBlockY();
        for (int ix = 0; ix < size; ix++) {
            for (int iz = 0; iz < size; iz++) {
                int localX = ix - radius;
                int localZ = iz - radius;
                double distance = Math.sqrt((localX * localX) + (localZ * localZ));
                if (distance > radius) {
                    continue;
                }
                double normalized = radius == 0 ? 1.0 : 1.0 - (distance / radius);
                double falloff = smoothFalloff(normalized);
                int current = heights[ix][iz];
                heights[ix][iz] = switch (mode) {
                    case RAISE -> current + Math.max(1, (int) Math.round(power * falloff));
                    case LOWER -> current - Math.max(1, (int) Math.round(power * falloff));
                    case FLATTEN -> flattenHeight(current, anchorY, power, falloff);
                };
            }
        }
        return applyHeights(snapshot, heights, editBedrock);
    }

    private BrushSnapshot captureSnapshot(Player player, Location center, int radius, int power) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        if (radius <= 0 || power <= 0) {
            ChatOutput.send(player, ChatColor.RED + "Brush radius and power must be greater than 0.");
            return null;
        }

        World world = center.getWorld();
        int size = radius * 2 + 1;
        int[][] heights = new int[size][size];
        Material[][] topMaterials = new Material[size][size];
        Material[][] fillMaterials = new Material[size][size];
        int rangePadding = radius + (power * 4) + 16;
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - rangePadding);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + rangePadding);
        captureBrushHeightMap(world, center, radius, heights, topMaterials, fillMaterials, minY, maxY);
        return new BrushSnapshot(world, center, radius, minY, maxY, heights, topMaterials, fillMaterials);
    }

    private void captureBrushHeightMap(World world, Location center, int radius, int[][] heights, Material[][] topMaterials,
                                       Material[][] fillMaterials, int minY, int maxY) {
        for (int localX = -radius; localX <= radius; localX++) {
            for (int localZ = -radius; localZ <= radius; localZ++) {
                int ix = localX + radius;
                int iz = localZ + radius;
                int x = center.getBlockX() + localX;
                int z = center.getBlockZ() + localZ;
                heights[ix][iz] = center.getBlockY();
                topMaterials[ix][iz] = Material.GRASS_BLOCK;
                fillMaterials[ix][iz] = Material.DIRT;
                for (int y = maxY; y >= minY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    heights[ix][iz] = y;
                    topMaterials[ix][iz] = block.getType();
                    fillMaterials[ix][iz] = findFillMaterial(world, x, y - 1, z, minY, topMaterials[ix][iz]);
                    break;
                }
            }
        }
    }

    private Material findFillMaterial(World world, int x, int startY, int z, int minY, Material topMaterial) {
        for (int y = startY; y >= minY; y--) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (!material.isAir()) {
                return material;
            }
        }
        return switch (topMaterial) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, MOSS_BLOCK, ROOTED_DIRT -> Material.DIRT;
            case SAND -> Material.SANDSTONE;
            case RED_SAND -> Material.RED_SANDSTONE;
            default -> topMaterial;
        };
    }

    private List<BlockChange> applyHeights(BrushSnapshot snapshot, int[][] targetHeights, boolean editBedrock) {
        List<BlockChange> changes = new ArrayList<>();
        int radius = snapshot.radius();
        for (int localX = -radius; localX <= radius; localX++) {
            for (int localZ = -radius; localZ <= radius; localZ++) {
                if ((localX * localX) + (localZ * localZ) > radius * radius) {
                    continue;
                }
                int ix = localX + radius;
                int iz = localZ + radius;
                int x = snapshot.center().getBlockX() + localX;
                int z = snapshot.center().getBlockZ() + localZ;
                int targetY = targetHeights[ix][iz];
                Material topMaterial = snapshot.topMaterials()[ix][iz] == null ? Material.GRASS_BLOCK : snapshot.topMaterials()[ix][iz];
                Material fillMaterial = snapshot.fillMaterials()[ix][iz] == null ? Material.DIRT : snapshot.fillMaterials()[ix][iz];

                for (int y = snapshot.minY(); y <= snapshot.maxY(); y++) {
                    Block block = snapshot.world().getBlockAt(x, y, z);
                    if (block.getType() == Material.BEDROCK && !editBedrock) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    if (y < targetY) {
                        if (block.getType() != fillMaterial) {
                            block.setType(fillMaterial, false);
                        }
                    } else if (y == targetY) {
                        if (block.getType() != topMaterial) {
                            block.setType(topMaterial, false);
                        }
                    } else {
                        if (!block.getType().isAir()) {
                            block.setType(Material.AIR, false);
                        }
                    }
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }
        return changes;
    }

    private int[][] smoothHeights(int[][] heights, int radius) {
        int size = heights.length;
        int[][] out = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int total = 0;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                            continue;
                        }
                        int rx = nx - radius;
                        int rz = nz - radius;
                        if ((rx * rx) + (rz * rz) > radius * radius) {
                            continue;
                        }
                        total += heights[nx][nz];
                        count++;
                    }
                }
                out[x][z] = Math.round((float) total / Math.max(1, count));
            }
        }
        return out;
    }

    private int[][] copyHeights(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    private double smoothFalloff(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - (2.0 * clamped));
    }

    private int flattenHeight(int current, int anchorY, int power, double falloff) {
        int delta = anchorY - current;
        if (delta == 0) {
            return current;
        }
        int step = Math.max(1, (int) Math.round(power * (0.75 + (falloff * 1.25))));
        if (Math.abs(delta) <= step) {
            return anchorY;
        }
        return current + (delta > 0 ? step : -step);
    }

    private enum ReshapeMode {
        RAISE,
        LOWER,
        FLATTEN
    }

    private record BrushSnapshot(World world, Location center, int radius, int minY, int maxY, int[][] heights,
                                 Material[][] topMaterials, Material[][] fillMaterials) {
    }
}
