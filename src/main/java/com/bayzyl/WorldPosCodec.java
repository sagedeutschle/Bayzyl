package com.bayzyl;

import java.util.Set;

/** Shared 26/12/26 world-position codec used by materialized block maps. */
public final class WorldPosCodec {
    public static final int MIN_X = -30_000_000;
    public static final int MAX_X = 37_108_863;
    public static final int MIN_Y = -2_048;
    public static final int MAX_Y = 2_047;
    public static final int MIN_Z = -30_000_000;
    public static final int MAX_Z = 37_108_863;

    private static final long X_OFFSET = 30_000_000L;
    private static final long Y_OFFSET = 2_048L;
    private static final long Z_OFFSET = 30_000_000L;
    private static final long X_MASK = 0x3FF_FFFFL;
    private static final long Y_MASK = 0xFFFL;
    private static final long Z_MASK = 0x3FF_FFFFL;

    private WorldPosCodec() {
    }

    public static long pack(int x, int y, int z) {
        if (!isRepresentable(x, y, z)) {
            throw new IllegalArgumentException("World position is outside the 26/12/26 packed range: "
                    + x + "," + y + "," + z + ".");
        }
        long ox = x + X_OFFSET;
        long oy = y + Y_OFFSET;
        long oz = z + Z_OFFSET;
        return ((ox & X_MASK) << 38) | ((oy & Y_MASK) << 26) | (oz & Z_MASK);
    }

    public static Position unpack(long packed) {
        int x = (int) ((packed >>> 38) - X_OFFSET);
        int y = (int) (((packed >>> 26) & Y_MASK) - Y_OFFSET);
        int z = (int) ((packed & Z_MASK) - Z_OFFSET);
        return new Position(x, y, z);
    }

    public static boolean isRepresentable(int x, int y, int z) {
        return x >= MIN_X && x <= MAX_X
                && y >= MIN_Y && y <= MAX_Y
                && z >= MIN_Z && z <= MAX_Z;
    }

    /** Returns false for an unrepresentable probe without attempting to pack it. */
    public static boolean contains(Set<Long> packedPositions, int x, int y, int z) {
        return isRepresentable(x, y, z) && packedPositions.contains(pack(x, y, z));
    }

    public record Position(int x, int y, int z) {
    }
}
