package com.bayzyl;

import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

public final class BiomeCommandUtil {
    private static final Backend BUKKIT_BACKEND = new Backend() {
        private CommandSender console;

        @Override
        public boolean isChunkLoaded(World world, int chunkX, int chunkZ) {
            return world.isChunkLoaded(chunkX, chunkZ);
        }

        @Override
        public void loadChunk(World world, int chunkX, int chunkZ) {
            world.loadChunk(chunkX, chunkZ, true);
        }

        @Override
        public boolean dispatch(String command) {
            if (console == null) {
                console = Bukkit.getConsoleSender();
            }
            return Bukkit.dispatchCommand(console, command);
        }
    };

    private BiomeCommandUtil() {
    }

    public static int applySelection(World world, Set<Long> included, boolean hollow, Biome biome) {
        return applySelection(world, included, hollow, biome, BUKKIT_BACKEND);
    }

    static int applySelection(World world, Set<Long> included, boolean hollow, Biome biome, Backend backend) {
        if (!preflightPositions(included)) {
            return 0;
        }
        return applyRanges(world, buildRanges(world, included, hollow, biome), backend);
    }

    public static int applyHistory(List<BiomeChange> changes, boolean undo) {
        return applyHistory(changes, undo, BUKKIT_BACKEND);
    }

    static int applyHistory(List<BiomeChange> changes, boolean undo, Backend backend) {
        if (assessHistory(changes, List.of()).hardRejected()) {
            return 0;
        }
        return applyRangesByWorld(buildRanges(changes, undo), backend);
    }

    private static int applyRangesByWorld(Map<World, List<BiomeRange>> byWorld, Backend backend) {
        int applied = 0;
        for (Map.Entry<World, List<BiomeRange>> entry : byWorld.entrySet()) {
            applied += applyRanges(entry.getKey(), entry.getValue(), backend);
        }
        return applied;
    }

    private static int applyRanges(World world, List<BiomeRange> ranges, Backend backend) {
        if (!preflightRanges(ranges)) {
            return 0;
        }
        ensureChunksLoaded(world, ranges, backend);
        int applied = 0;
        java.util.logging.Logger paperLogger = java.util.logging.Logger.getLogger("Paper");
        java.util.logging.Logger mcLogger = java.util.logging.Logger.getLogger("Minecraft");
        Level paperLevel = paperLogger.getLevel();
        Level mcLevel = mcLogger.getLevel();
        paperLogger.setLevel(Level.WARNING);
        mcLogger.setLevel(Level.WARNING);
        try {
            for (BiomeRange range : ranges) {
                String command = "execute in " + world.getKey() + " run fillbiome "
                        + range.x() + " " + range.minY() + " " + range.z() + " "
                        + range.x() + " " + range.maxY() + " " + range.z() + " "
                        + range.biome().getKey();
                if (backend.dispatch(command)) {
                    applied++;
                }
            }
        } finally {
            paperLogger.setLevel(paperLevel);
            mcLogger.setLevel(mcLevel);
        }
        return applied;
    }

    private static void ensureChunksLoaded(World world, List<BiomeRange> ranges, Backend backend) {
        Set<Long> chunks = new java.util.LinkedHashSet<>();
        for (BiomeRange range : ranges) {
            int chunkX = range.x() >> 4;
            int chunkZ = range.z() >> 4;
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFF_FFFFL);
            chunks.add(key);
        }
        for (long chunkKey : chunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            if (!backend.isChunkLoaded(world, chunkX, chunkZ)) {
                backend.loadChunk(world, chunkX, chunkZ);
            }
        }
    }

    private static boolean preflightPositions(Set<Long> included) {
        if (included == null || OperationLimits.checkMaterialized(included.size()).hardRejected()) {
            return false;
        }
        Set<Long> chunks = new java.util.LinkedHashSet<>();
        for (long encoded : included) {
            WorldPosCodec.Position position = WorldPosCodec.unpack(encoded);
            chunks.add(encodeColumn(position.x() >> 4, position.z() >> 4));
            if (chunks.size() > OperationLimits.BIOME_CHUNK_HARD_MAX) {
                return false;
            }
        }
        return true;
    }

    private static boolean preflightRanges(List<BiomeRange> ranges) {
        if (ranges == null || OperationLimits.checkMaterialized(ranges.size()).hardRejected()) {
            return false;
        }
        Set<Long> chunks = new java.util.LinkedHashSet<>();
        for (BiomeRange range : ranges) {
            chunks.add(encodeColumn(range.x() >> 4, range.z() >> 4));
            if (chunks.size() > OperationLimits.BIOME_CHUNK_HARD_MAX) {
                return false;
            }
        }
        return true;
    }

    static WorkEstimate assessHistory(
            List<BiomeChange> blockChanges,
            List<BiomeColumnChange> columnChanges
    ) {
        if (blockChanges == null || columnChanges == null) {
            return rejectedHistory("Biome history lists cannot be null.");
        }
        WorkEstimate total = OperationLimits.checkedAdd(blockChanges.size(), columnChanges.size());
        if (total.hardRejected()) {
            return total;
        }

        Set<BiomeChunkKey> chunks = new java.util.LinkedHashSet<>();
        for (BiomeChange change : blockChanges) {
            if (change == null) {
                return rejectedHistory("Biome history contains a null block entry.");
            }
            Location location = change.getLocation();
            if (location == null) {
                return rejectedHistory("Biome history contains a malformed block entry.");
            }
            World world = location.getWorld();
            if (world == null || change.getBefore() == null || change.getAfter() == null) {
                return rejectedHistory("Biome history contains a malformed block entry.");
            }
            chunks.add(new BiomeChunkKey(world, location.getBlockX() >> 4, location.getBlockZ() >> 4));
            if (chunks.size() > OperationLimits.BIOME_CHUNK_HARD_MAX) {
                return OperationLimits.checkBiomeChunks(chunks.size());
            }
        }
        for (BiomeColumnChange change : columnChanges) {
            if (change == null) {
                return rejectedHistory("Biome history contains a null column entry.");
            }
            World world = change.getWorld();
            if (world == null || change.getBefore() == null || change.getAfter() == null) {
                return rejectedHistory("Biome history contains a malformed column entry.");
            }
            chunks.add(new BiomeChunkKey(world, change.getX() >> 4, change.getZ() >> 4));
            if (chunks.size() > OperationLimits.BIOME_CHUNK_HARD_MAX) {
                return OperationLimits.checkBiomeChunks(chunks.size());
            }
        }
        return total;
    }

    private static WorkEstimate rejectedHistory(String reason) {
        return new WorkEstimate(0L, false, true, reason);
    }

    private static List<BiomeRange> buildRanges(World world, Set<Long> included, boolean hollow, Biome biome) {
        Map<Long, TreeSet<Integer>> columns = new LinkedHashMap<>();
        for (long encoded : included) {
            if (hollow && !isBoundary(encoded, included)) {
                continue;
            }
            int[] pos = decodePos(encoded);
            if (pos[1] < world.getMinHeight() || pos[1] > world.getMaxHeight() - 1) {
                continue;
            }
            long columnKey = encodeColumn(pos[0], pos[2]);
            columns.computeIfAbsent(columnKey, ignored -> new TreeSet<>()).add(pos[1]);
        }

        List<BiomeRange> ranges = new ArrayList<>();
        for (Map.Entry<Long, TreeSet<Integer>> entry : columns.entrySet()) {
            int[] column = decodeColumn(entry.getKey());
            appendRanges(ranges, column[0], column[1], biome, entry.getValue());
        }
        return ranges;
    }

    private static Map<World, List<BiomeRange>> buildRanges(List<BiomeChange> changes, boolean undo) {
        Map<World, Map<BiomeColumnBiomeKey, TreeSet<Integer>>> columns = new LinkedHashMap<>();
        for (BiomeChange change : changes) {
            Location location = change.getLocation();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            Biome biome = undo ? change.getBefore() : change.getAfter();
            if (biome == null) {
                continue;
            }
            BiomeColumnBiomeKey key = new BiomeColumnBiomeKey(location.getBlockX(), location.getBlockZ(), biome);
            columns.computeIfAbsent(world, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(key, ignored -> new TreeSet<>())
                    .add(location.getBlockY());
        }

        Map<World, List<BiomeRange>> byWorld = new LinkedHashMap<>();
        for (Map.Entry<World, Map<BiomeColumnBiomeKey, TreeSet<Integer>>> worldEntry : columns.entrySet()) {
            List<BiomeRange> ranges = new ArrayList<>();
            for (Map.Entry<BiomeColumnBiomeKey, TreeSet<Integer>> entry : worldEntry.getValue().entrySet()) {
                appendRanges(ranges, entry.getKey().x(), entry.getKey().z(), entry.getKey().biome(), entry.getValue());
            }
            ranges.sort(Comparator.comparingInt(BiomeRange::x)
                    .thenComparingInt(BiomeRange::z)
                    .thenComparingInt(BiomeRange::minY));
            byWorld.put(worldEntry.getKey(), ranges);
        }
        return byWorld;
    }

    private static void appendRanges(List<BiomeRange> ranges, int x, int z, Biome biome, TreeSet<Integer> ys) {
        Integer start = null;
        Integer previous = null;
        for (int y : ys) {
            if (start == null) {
                start = y;
                previous = y;
                continue;
            }
            if (y == previous + 1) {
                previous = y;
                continue;
            }
            ranges.add(new BiomeRange(x, z, start, previous, biome));
            start = y;
            previous = y;
        }
        if (start != null && previous != null) {
            ranges.add(new BiomeRange(x, z, start, previous, biome));
        }
    }

    private static boolean isBoundary(long encoded, Set<Long> included) {
        WorldPosCodec.Position pos = WorldPosCodec.unpack(encoded);
        return !WorldPosCodec.contains(included, pos.x() + 1, pos.y(), pos.z())
                || !WorldPosCodec.contains(included, pos.x() - 1, pos.y(), pos.z())
                || !WorldPosCodec.contains(included, pos.x(), pos.y() + 1, pos.z())
                || !WorldPosCodec.contains(included, pos.x(), pos.y() - 1, pos.z())
                || !WorldPosCodec.contains(included, pos.x(), pos.y(), pos.z() + 1)
                || !WorldPosCodec.contains(included, pos.x(), pos.y(), pos.z() - 1);
    }

    private static long encodePos(int x, int y, int z) {
        return WorldPosCodec.pack(x, y, z);
    }

    private static int[] decodePos(long value) {
        WorldPosCodec.Position position = WorldPosCodec.unpack(value);
        return new int[]{position.x(), position.y(), position.z()};
    }

    private static long encodeColumn(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private static int[] decodeColumn(long value) {
        return new int[]{(int) (value >> 32), (int) value};
    }

    private record BiomeRange(int x, int z, int minY, int maxY, Biome biome) {
    }

    private record BiomeColumnBiomeKey(int x, int z, Biome biome) {
    }

    private record BiomeChunkKey(World world, int x, int z) {
    }

    interface Backend {
        boolean isChunkLoaded(World world, int chunkX, int chunkZ);

        void loadChunk(World world, int chunkX, int chunkZ);

        boolean dispatch(String command);
    }
}
