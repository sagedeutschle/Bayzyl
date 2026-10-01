package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.bayzyl.safety.BrushSafety;

public final class NaturalizeService {
    private static final int DEFAULT_DEPTH = 3;
    private static final int BRUSH_PADDING = 16;
    private static final Set<Material> NATURAL_SURFACES = EnumSet.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.MOSS_BLOCK,
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL,
            Material.SNOW_BLOCK,
            Material.SNOW,
            Material.MUD,
            Material.STONE,
            Material.DEEPSLATE,
            Material.TUFF,
            Material.CALCITE,
            Material.DRIPSTONE_BLOCK,
            Material.CLAY
    );

    public List<BlockChange> naturalizeSelection(Player player, Selection selection) {
        return naturalizeSelection(player, selection, DEFAULT_DEPTH, false);
    }

    public List<BlockChange> naturalizeSelection(Player player, Selection selection, int depth, boolean editBedrock) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return List.of();
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return List.of();
        }
        return naturalizeArea(
                world,
                selection.getMinX(),
                selection.getMaxX(),
                selection.getMinY(),
                selection.getMaxY(),
                selection.getMinZ(),
                selection.getMaxZ(),
                null,
                null,
                Math.max(1, depth),
                editBedrock
        );
    }

    public List<BlockChange> naturalizeBrush(Player player, Location center, int radius, int depth, boolean editBedrock) {
        TerrainBrushSettings settings = new TerrainBrushSettings(
                TerrainBrushType.NATURALIZE, radius, depth, editBedrock);
        if (!BrushSafety.isValidTerrain(settings)) {
            return List.of();
        }
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        World world = center.getWorld();
        int range = radius + BRUSH_PADDING;
        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - range);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + range);
        return naturalizeArea(world, minX, maxX, minY, maxY, minZ, maxZ, center, radius, Math.max(1, depth), editBedrock);
    }

    private List<BlockChange> naturalizeArea(World world,
                                             int minX,
                                             int maxX,
                                             int minY,
                                             int maxY,
                                             int minZ,
                                             int maxZ,
                                             Location sphereCenter,
                                             Integer sphereRadius,
                                             int depth,
                                             boolean editBedrock) {
        int width = maxX - minX + 1;
        int depthZ = maxZ - minZ + 1;
        int[][] heights = new int[width][depthZ];
        Material[][] topMaterials = new Material[width][depthZ];
        boolean[][] foundSurface = new boolean[width][depthZ];
        boolean[][] protectedColumn = detectProtectedColumns(world, minX, maxX, minY, maxY, minZ, maxZ);

        captureHeightMap(world, minX, maxX, minY, maxY, minZ, maxZ, heights, topMaterials, foundSurface, protectedColumn);
        heights = smoothHeights(heights, foundSurface);
        int[][] slope = computeSlope(heights, foundSurface);

        List<BlockChange> changes = new ArrayList<>();
        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depthZ; localZ++) {
                if (!foundSurface[localX][localZ]) {
                    continue;
                }
                int x = minX + localX;
                int z = minZ + localZ;
                if (sphereCenter != null && sphereRadius != null) {
                    int dx = x - sphereCenter.getBlockX();
                    int dz = z - sphereCenter.getBlockZ();
                    if ((dx * dx) + (dz * dz) > (sphereRadius * sphereRadius)) {
                        continue;
                    }
                }

                int targetY = clamp(heights[localX][localZ], minY, maxY);
                Material currentTop = topMaterials[localX][localZ];
                int localSlope = slope[localX][localZ];
                Material surfaceMaterial = chooseSurfaceMaterial(world, x, targetY, z, currentTop, localSlope);
                Material subsurfaceMaterial = chooseSubsurfaceMaterial(surfaceMaterial, targetY, currentTop, x, z, world, localSlope);

                for (int y = targetY + 1; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BEDROCK && !editBedrock) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }

                for (int y = targetY; y >= minY; y--) {
                    if (targetY - y > Math.max(1, depth)) {
                        break;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BEDROCK && !editBedrock) {
                        continue;
                    }
                    Material replacement = y == targetY ? surfaceMaterial : chooseSubsurfaceLayer(surfaceMaterial, subsurfaceMaterial, targetY - y);
                    BlockData before = block.getBlockData().clone();
                    if (block.getType() != replacement) {
                        block.setType(replacement, false);
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

    private void captureHeightMap(World world,
                                  int minX,
                                  int maxX,
                                  int minY,
                                  int maxY,
                                  int minZ,
                                  int maxZ,
                                  int[][] heights,
                                  Material[][] topMaterials,
                                  boolean[][] foundSurface,
                                  boolean[][] protectedColumn) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                if (protectedColumn != null && protectedColumn[localX][localZ]) {
                    continue;
                }
                for (int y = maxY; y >= minY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    if (isTreeBlock(type)) {
                        continue;
                    }
                    heights[localX][localZ] = y;
                    topMaterials[localX][localZ] = type;
                    foundSurface[localX][localZ] = true;
                    break;
                }
            }
        }
    }

    private boolean[][] detectProtectedColumns(World world,
                                               int minX,
                                               int maxX,
                                               int minY,
                                               int maxY,
                                               int minZ,
                                               int maxZ) {
        int width = maxX - minX + 1;
        int depthZ = maxZ - minZ + 1;
        boolean[][] hasTree = new boolean[width][depthZ];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int localX = x - minX;
                int localZ = z - minZ;
                for (int y = maxY; y >= minY; y--) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (isTreeBlock(type)) {
                        hasTree[localX][localZ] = true;
                        break;
                    }
                }
            }
        }
        boolean[][] protectedColumn = new boolean[width][depthZ];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depthZ; z++) {
                if (!hasTree[x][z]) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= width || nz >= depthZ) {
                            continue;
                        }
                        protectedColumn[nx][nz] = true;
                    }
                }
            }
        }
        return protectedColumn;
    }

    private boolean isTreeBlock(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.LOGS.isTagged(material)) {
            return true;
        }
        if (Tag.LEAVES.isTagged(material)) {
            return true;
        }
        if (Tag.SAPLINGS.isTagged(material)) {
            return true;
        }
        return switch (material) {
            case MANGROVE_ROOTS, MUDDY_MANGROVE_ROOTS,
                 MUSHROOM_STEM, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK,
                 BAMBOO, BAMBOO_SAPLING -> true;
            default -> false;
        };
    }

    private int[][] smoothHeights(int[][] heights, boolean[][] foundSurface) {
        int width = heights.length;
        int depth = heights[0].length;
        int[][] out = new int[width][depth];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int total = 0;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= width || nz >= depth) {
                            continue;
                        }
                        if (!foundSurface[nx][nz]) {
                            continue;
                        }
                        total += heights[nx][nz];
                        count++;
                    }
                }
                out[x][z] = count == 0 ? heights[x][z] : Math.round((float) total / count);
            }
        }
        return out;
    }

    private Material chooseSurfaceMaterial(World world, int x, int y, int z, Material currentTop, int slope) {
        Biome biome = world.getBiome(x, y, z);
        String biomeName = biome == null ? "" : biome.name().toUpperCase(Locale.ROOT);
        if (biomeName.contains("BADLAND")) {
            return Material.RED_SAND;
        }
        if (biomeName.contains("DESERT") || biomeName.contains("BEACH") || biomeName.contains("SAVANNA")) {
            return Material.SAND;
        }
        if (biomeName.contains("SNOW") || biomeName.contains("FROZEN") || biomeName.contains("ICY")) {
            return Material.SNOW_BLOCK;
        }
        if (biomeName.contains("MUSHROOM")) {
            return Material.MYCELIUM;
        }
        if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            return Material.MUD;
        }
        if (currentTop != null && isNaturalSurface(currentTop) && !isStoneLike(currentTop)) {
            return Material.GRASS_BLOCK;
        }
        if (currentTop != null && isStoneLike(currentTop)) {
            return Material.GRASS_BLOCK;
        }
        if (slope >= 3 || biomeName.contains("WINDSWEPT") || biomeName.contains("PEAK") || biomeName.contains("JAGGED") || biomeName.contains("STONY") || biomeName.contains("MOUNTAIN")) {
            return Material.GRASS_BLOCK;
        }
        return Material.GRASS_BLOCK;
    }

    private Material chooseSubsurfaceMaterial(Material surfaceMaterial, int y, Material currentTop, int x, int z, World world, int slope) {
        if (surfaceMaterial == Material.GRASS_BLOCK
                || surfaceMaterial == Material.PODZOL
                || surfaceMaterial == Material.MYCELIUM
                || surfaceMaterial == Material.MOSS_BLOCK
                || surfaceMaterial == Material.ROOTED_DIRT) {
            if (slope >= 2 || isMountainLike(world, x, y, z) || isStoneLike(currentTop)) {
                return Material.STONE;
            }
            return Material.DIRT;
        }
        return switch (surfaceMaterial) {
            case SAND -> Material.SANDSTONE;
            case RED_SAND -> Material.RED_SANDSTONE;
            case SNOW, SNOW_BLOCK -> Material.SNOW_BLOCK;
            case GRAVEL -> Material.STONE;
            case MUD -> Material.MUD;
            case STONE, DEEPSLATE -> Material.STONE;
            case TUFF -> Material.TUFF;
            case CALCITE -> Material.CALCITE;
            case DRIPSTONE_BLOCK -> Material.DRIPSTONE_BLOCK;
            default -> Material.DIRT;
        };
    }

    private Material chooseSubsurfaceLayer(Material surfaceMaterial, Material subsurfaceMaterial, int depthFromSurface) {
        if (surfaceMaterial == Material.GRASS_BLOCK
                || surfaceMaterial == Material.PODZOL
                || surfaceMaterial == Material.MYCELIUM
                || surfaceMaterial == Material.MOSS_BLOCK
                || surfaceMaterial == Material.ROOTED_DIRT) {
            if (depthFromSurface == 1) {
                return Material.STONE;
            }
            if (depthFromSurface == 2) {
                return subsurfaceMaterial;
            }
            return Material.DIRT;
        }
        return subsurfaceMaterial;
    }

    private int[][] computeSlope(int[][] heights, boolean[][] foundSurface) {
        int width = heights.length;
        int depth = heights[0].length;
        int[][] slope = new int[width][depth];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (!foundSurface[x][z]) {
                    continue;
                }
                int base = heights[x][z];
                int maxDelta = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= width || nz >= depth || (dx == 0 && dz == 0)) {
                            continue;
                        }
                        if (!foundSurface[nx][nz]) {
                            continue;
                        }
                        maxDelta = Math.max(maxDelta, Math.abs(base - heights[nx][nz]));
                    }
                }
                slope[x][z] = maxDelta;
            }
        }
        return slope;
    }

    private boolean isNaturalSurface(Material material) {
        return NATURAL_SURFACES.contains(material) || isStoneLike(material);
    }

    private boolean isStoneLike(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case STONE, COBBLESTONE, DEEPSLATE, COBBLED_DEEPSLATE, ANDESITE, DIORITE, GRANITE,
                 TUFF, CALCITE, DRIPSTONE_BLOCK, SMOOTH_BASALT, BASALT -> true;
            default -> false;
        };
    }

    private boolean isMountainLike(World world, int x, int y, int z) {
        Biome biome = world.getBiome(x, y, z);
        String biomeName = biome == null ? "" : biome.name().toUpperCase(Locale.ROOT);
        return biomeName.contains("WINDSWEPT")
                || biomeName.contains("PEAK")
                || biomeName.contains("JAGGED")
                || biomeName.contains("STONY")
                || biomeName.contains("MOUNTAIN");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
