package com.bayzyl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldPosCodecTest {
    @Test
    void oldSignedExtractionFailsAtFirstNegativePackedX() {
        long packed = legacyPack(3_554_432, 0, 0);

        assertNotEquals(3_554_432, legacyUnpackX(packed));
        assertEquals(3_554_432, WorldPosCodec.unpack(packed).x());
    }

    @Test
    void roundTripsRequestedXBoundaries() {
        for (int x : new int[]{3_554_431, 3_554_432, -30_000_000, 30_000_000, 37_108_863}) {
            assertEquals(new WorldPosCodec.Position(x, 0, 0), WorldPosCodec.unpack(WorldPosCodec.pack(x, 0, 0)));
        }
    }

    @Test
    void roundTripsRequestedZBoundaries() {
        for (int z : new int[]{-30_000_000, 30_000_000, 37_108_863}) {
            assertEquals(new WorldPosCodec.Position(0, 0, z), WorldPosCodec.unpack(WorldPosCodec.pack(0, 0, z)));
        }
    }

    @Test
    void roundTripsRequestedYBoundaries() {
        for (int y : new int[]{-2_048, 2_047}) {
            assertEquals(new WorldPosCodec.Position(0, y, 0), WorldPosCodec.unpack(WorldPosCodec.pack(0, y, 0)));
        }
    }

    @Test
    void rejectsEveryAxisOutsideItsRepresentableRange() {
        assertThrows(IllegalArgumentException.class, () -> WorldPosCodec.pack(-30_000_001, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldPosCodec.pack(37_108_864, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldPosCodec.pack(0, -2_049, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldPosCodec.pack(0, 2_048, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldPosCodec.pack(0, 0, -30_000_001));
        assertThrows(IllegalArgumentException.class, () -> WorldPosCodec.pack(0, 0, 37_108_864));
    }

    @Test
    void preservesOrdinaryPackedValuesExactly() {
        assertEquals(0x7270E02001C9C380L, WorldPosCodec.pack(0, 0, 0));
        assertEquals(0x727CEE6101C8EF4FL, WorldPosCodec.pack(12_345, 64, -54_321));
        assertEquals(0x7264D1DF01CA97B1L, WorldPosCodec.pack(-12_345, -64, 54_321));
        assertEquals(0L, WorldPosCodec.pack(-30_000_000, -2_048, -30_000_000));
        assertEquals(0xE4E1C03FFF938700L, WorldPosCodec.pack(30_000_000, 2_047, 30_000_000));
    }

    @Test
    void unrepresentableBoundaryNeighborIsOutsideWithoutPacking() {
        long edge = WorldPosCodec.pack(WorldPosCodec.MAX_X, 0, 0);
        Set<Long> included = new LinkedHashSet<>();
        included.add(edge);

        assertFalse(WorldPosCodec.isRepresentable(WorldPosCodec.MAX_X + 1, 0, 0));
        assertFalse(WorldPosCodec.contains(included, WorldPosCodec.MAX_X + 1, 0, 0));
        assertTrue(WorldPosCodec.contains(included, WorldPosCodec.MAX_X, 0, 0));
    }

    private static long legacyPack(int x, int y, int z) {
        long ox = x + 30_000_000L;
        long oy = y + 2_048L;
        long oz = z + 30_000_000L;
        return (ox << 38) | (oy << 26) | oz;
    }

    private static int legacyUnpackX(long packed) {
        return (int) ((packed >> 38) - 30_000_000L);
    }
}
