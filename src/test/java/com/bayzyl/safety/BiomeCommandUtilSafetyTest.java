package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class BiomeCommandUtilSafetyTest {
    @Test
    void selectionAcross4097ChunksRefusesBeforeBackendAccess() {
        Set<Long> included = packedChunkColumns(4_097);

        int applied = BiomeCommandUtil.applySelection(
                mock(World.class), included, false, Biome.PLAINS, poisonBackend());

        assertEquals(0, applied);
    }

    @Test
    void oversizedHistoryRefusesBeforeReadingChangesOrBackendAccess() {
        List<BiomeChange> poisonChanges = new AbstractList<>() {
            @Override
            public BiomeChange get(int index) {
                throw new AssertionError("history entries must not be read");
            }

            @Override
            public int size() {
                return 1_000_001;
            }
        };

        int applied = BiomeCommandUtil.applyHistory(poisonChanges, true, poisonBackend());

        assertEquals(0, applied);
    }

    @Test
    void malformedHistoryRefusesWithoutRangeOrBackendAccess() {
        World world = mock(World.class);
        List<List<BiomeChange>> malformedHistories = List.of(
                Collections.singletonList(null),
                List.of(new BiomeChange(null, Biome.PLAINS, Biome.DESERT)),
                List.of(new BiomeChange(new Location(null, 0, 64, 0), Biome.PLAINS, Biome.DESERT)),
                List.of(new BiomeChange(new Location(world, 0, 64, 0), null, Biome.DESERT)),
                List.of(new BiomeChange(new Location(world, 0, 64, 0), Biome.PLAINS, null))
        );

        for (List<BiomeChange> changes : malformedHistories) {
            assertEquals(0, BiomeCommandUtil.applyHistory(changes, true, poisonBackend()));
            assertEquals(0, BiomeCommandUtil.applyHistory(changes, false, poisonBackend()));
        }
    }

    @Test
    void exact4096ChunkBoundaryReachesBackend() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        CountingBackend backend = new CountingBackend();

        int applied = BiomeCommandUtil.applySelection(
                world, packedChunkColumns(4_096), false, Biome.PLAINS, backend);

        assertEquals(4_096, applied);
        assertEquals(4_096, backend.chunkChecks);
        assertEquals(4_096, backend.dispatches);
    }

    private static Set<Long> packedChunkColumns(int count) {
        Set<Long> positions = new LinkedHashSet<>();
        for (int chunkX = 0; chunkX < count; chunkX++) {
            positions.add(WorldPosCodec.pack(chunkX * 16, 64, 0));
        }
        return positions;
    }

    private static BiomeCommandUtil.Backend poisonBackend() {
        return new BiomeCommandUtil.Backend() {
            @Override
            public boolean isChunkLoaded(World world, int chunkX, int chunkZ) {
                throw new AssertionError("chunk state must not be read");
            }

            @Override
            public void loadChunk(World world, int chunkX, int chunkZ) {
                throw new AssertionError("chunk must not load");
            }

            @Override
            public boolean dispatch(String command) {
                throw new AssertionError("command must not dispatch");
            }
        };
    }

    private static final class CountingBackend implements BiomeCommandUtil.Backend {
        private int chunkChecks;
        private int dispatches;

        @Override
        public boolean isChunkLoaded(World world, int chunkX, int chunkZ) {
            chunkChecks++;
            return true;
        }

        @Override
        public void loadChunk(World world, int chunkX, int chunkZ) {
            throw new AssertionError("loaded chunk should not be reloaded");
        }

        @Override
        public boolean dispatch(String command) {
            dispatches++;
            return true;
        }
    }
}
