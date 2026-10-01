package com.bayzyl.gen.env;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Samples the terrain around a brush target so generators can mimic
 * whatever style of world they're being placed in. The expensive part —
 * tallying biome/material counts across a small box — is bounded by
 * {@code samples} so a click never costs more than a few hundred block
 * reads.
 */
public final class EnvironmentProbe {
    private static final int DEFAULT_RADIUS = 12;
    private static final int DEFAULT_STRIDE = 3;

    private EnvironmentProbe() {
    }

    public static EnvironmentSample probe(Location target) {
        return probe(target, DEFAULT_RADIUS, DEFAULT_STRIDE);
    }

    public static EnvironmentSample probe(Location target, int radius, int stride) {
        World world = target.getWorld();
        if (world == null) {
            return defaultSample(target);
        }
        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();

        int waterLevel = world.getSeaLevel();
        Map<Material, Integer> surfaceCounts = new HashMap<>();
        Map<Material, Integer> subsurfaceCounts = new HashMap<>();
        Map<Material, Integer> deepCounts = new HashMap<>();
        Map<Material, Integer> fillCounts = new HashMap<>();
        Map<Material, Integer> foliageCounts = new HashMap<>();
        Map<Biome, Integer> biomeCounts = new HashMap<>();

        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int probeSurfaceY = cy;
        int openColumns = 0;
        int totalColumns = 0;
        int columnsSampled = 0;
        int underwaterColumns = 0;

        int s = Math.max(1, stride);
        for (int dx = -radius; dx <= radius; dx += s) {
            for (int dz = -radius; dz <= radius; dz += s) {
                int x = cx + dx;
                int z = cz + dz;
                int surfaceY = findSurface(world, x, cy, z);
                totalColumns++;
                if (surfaceY <= world.getMinHeight()) {
                    continue;
                }
                columnsSampled++;
                if (surfaceY < minSurface) minSurface = surfaceY;
                if (surfaceY > maxSurface) maxSurface = surfaceY;
                if (dx == 0 && dz == 0) {
                    probeSurfaceY = surfaceY;
                }
                Block surface = world.getBlockAt(x, surfaceY, z);
                Material surfaceMat = surface.getType();
                if (surfaceMat.isAir()) {
                    continue;
                }
                surfaceCounts.merge(surfaceMat, 1, Integer::sum);
                biomeCounts.merge(world.getBiome(x, surfaceY, z), 1, Integer::sum);

                Material above = world.getBlockAt(x, surfaceY + 1, z).getType();
                if (above != Material.AIR) {
                    foliageCounts.merge(above, 1, Integer::sum);
                }
                if (above == Material.AIR && world.getBlockAt(x, surfaceY + 2, z).getType() == Material.AIR) {
                    openColumns++;
                }
                if (surfaceY < waterLevel) {
                    underwaterColumns++;
                }

                int subY = Math.max(world.getMinHeight(), surfaceY - 2);
                Material subMat = world.getBlockAt(x, subY, z).getType();
                if (subMat.isSolid()) {
                    subsurfaceCounts.merge(subMat, 1, Integer::sum);
                }
                int deepY = Math.max(world.getMinHeight(), surfaceY - 8);
                Material deepMat = world.getBlockAt(x, deepY, z).getType();
                if (deepMat.isSolid()) {
                    deepCounts.merge(deepMat, 1, Integer::sum);
                }
                int fillY = Math.max(world.getMinHeight(), surfaceY - 5);
                Material fillMat = world.getBlockAt(x, fillY, z).getType();
                if (fillMat.isSolid()) {
                    fillCounts.merge(fillMat, 1, Integer::sum);
                }
            }
        }

        TerrainShape shape;
        if (columnsSampled == 0) {
            shape = TerrainShape.VOID;
        } else if (underwaterColumns > columnsSampled * 0.85) {
            shape = TerrainShape.UNDERWATER;
        } else if (maxSurface - minSurface == 0) {
            shape = TerrainShape.SUPERFLAT;
        } else if (maxSurface - minSurface < 2) {
            shape = TerrainShape.FLAT;
        } else if (maxSurface - minSurface < 6) {
            shape = TerrainShape.ROLLING;
        } else if (maxSurface - minSurface < 12) {
            shape = TerrainShape.HILLY;
        } else if (maxSurface - minSurface < 30) {
            shape = TerrainShape.MOUNTAIN;
        } else {
            shape = TerrainShape.PEAK;
        }

        Biome topBiome = topKey(biomeCounts);
        BiomeFamily family = classify(topBiome);
        Material topSurface = topKey(surfaceCounts);
        Material topSub = topKey(subsurfaceCounts);
        Material topDeep = topKey(deepCounts);
        Material topFill = topKey(fillCounts);
        Material topFoliage = topKey(foliageCounts);
        double openness = totalColumns == 0 ? 0.0 : (double) openColumns / totalColumns;
        int slopeBlocks = (columnsSampled == 0) ? 0 : Math.max(0, maxSurface - minSurface);
        List<Material> topPalette = topN(surfaceCounts, 4);
        boolean underwater = (shape == TerrainShape.UNDERWATER)
                || (probeSurfaceY < waterLevel - 2);

        return new EnvironmentSample(
                topBiome,
                family,
                shape,
                topSurface == null ? Material.GRASS_BLOCK : topSurface,
                topSub == null ? Material.DIRT : topSub,
                topDeep == null ? Material.STONE : topDeep,
                topFill == null ? (topSub == null ? Material.DIRT : topSub) : topFill,
                topFoliage,
                underwater,
                waterLevel,
                probeSurfaceY,
                slopeBlocks,
                openness,
                topPalette
        );
    }

    private static EnvironmentSample defaultSample(Location target) {
        int y = target == null ? 64 : target.getBlockY();
        return new EnvironmentSample(
                null, BiomeFamily.UNKNOWN, TerrainShape.VOID,
                Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.DIRT, null,
                false, 63, y, 0, 0.0, List.of(Material.GRASS_BLOCK));
    }

    private static int findSurface(World world, int x, int near, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        // Drop through plants, snow layers, fences, etc. to find the first solid floor.
        while (highest > world.getMinHeight()) {
            Material mat = world.getBlockAt(x, highest, z).getType();
            if (isGroundLike(mat)) {
                return highest;
            }
            highest--;
        }
        return Math.max(world.getMinHeight(), near);
    }

    private static boolean isGroundLike(Material material) {
        if (!material.isSolid()) {
            return false;
        }
        String name = material.name();
        if (name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_SAPLING") || name.endsWith("_FENCE")) {
            return false;
        }
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, ROOTED_DIRT, MYCELIUM, MUD,
                 STONE, GRANITE, DIORITE, ANDESITE, DEEPSLATE, COBBLESTONE,
                 SAND, RED_SAND, SANDSTONE, RED_SANDSTONE,
                 SNOW_BLOCK, ICE, PACKED_ICE, BLUE_ICE,
                 GRAVEL, NETHERRACK, SOUL_SAND, SOUL_SOIL, BASALT, BLACKSTONE,
                 CRIMSON_NYLIUM, WARPED_NYLIUM, END_STONE, TERRACOTTA -> true;
            default -> name.endsWith("_TERRACOTTA") || name.endsWith("_CONCRETE_POWDER");
        };
    }

    private static <K> K topKey(Map<K, Integer> counts) {
        K bestKey = null;
        int best = -1;
        for (Map.Entry<K, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                bestKey = entry.getKey();
            }
        }
        return bestKey;
    }

    private static List<Material> topN(Map<Material, Integer> counts, int n) {
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static BiomeFamily classify(Biome biome) {
        if (biome == null) {
            return BiomeFamily.UNKNOWN;
        }
        String key = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        if (key.contains("ocean")) return BiomeFamily.OCEAN;
        if (key.contains("river")) return BiomeFamily.RIVER;
        if (key.contains("beach") || key.equals("snowy_beach")) return BiomeFamily.BEACH;
        if (key.contains("desert")) return BiomeFamily.DESERT;
        if (key.contains("badlands")) return BiomeFamily.BADLANDS;
        if (key.contains("eroded_badlands") || key.contains("wooded_badlands")) return BiomeFamily.BADLANDS;
        if (key.contains("savanna")) return BiomeFamily.SAVANNA;
        if (key.contains("jungle") || key.contains("bamboo")) return BiomeFamily.JUNGLE;
        if (key.contains("swamp") || key.contains("mangrove")) return BiomeFamily.SWAMP;
        if (key.contains("taiga")) return BiomeFamily.TAIGA;
        if (key.contains("snowy") || key.contains("frozen") || key.contains("ice")) return BiomeFamily.SNOWY;
        if (key.contains("mushroom")) return BiomeFamily.MUSHROOM;
        if (key.contains("cherry")) return BiomeFamily.CHERRY;
        if (key.contains("lush")) return BiomeFamily.LUSH;
        if (key.contains("dripstone")) return BiomeFamily.DRIPSTONE;
        if (key.contains("deep_dark")) return BiomeFamily.DEEP_DARK;
        if (key.contains("nether") || key.contains("crimson") || key.contains("warped")
                || key.contains("soul") || key.contains("basalt")) return BiomeFamily.NETHER;
        if (key.contains("end") || key.contains("void")) return BiomeFamily.END;
        if (key.contains("forest") || key.contains("birch") || key.contains("dark_forest")
                || key.contains("grove") || key.contains("flower_forest")) return BiomeFamily.FOREST;
        if (key.contains("meadow") || key.contains("plains") || key.contains("sunflower")
                || key.contains("stony_shore")) return BiomeFamily.GRASSLAND;
        if (key.contains("peak") || key.contains("mountain") || key.contains("hills")
                || key.contains("stony")) return BiomeFamily.MOUNTAIN;
        return BiomeFamily.UNKNOWN;
    }
}
