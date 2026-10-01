package com.bayzyl.gen.primitives;

/**
 * Offsets a sample position by a low-frequency noise field before the
 * primary noise is evaluated. The cheap way to break perfectly circular
 * silhouettes and put kinks into ridges, valleys, and cave tunnels so
 * the output silhouettes look hand-eroded.
 */
public final class DomainWarp {
    private final FbmNoise warpX;
    private final FbmNoise warpZ;
    private final FbmNoise warpY;
    private final double strength;
    private final double frequency;

    public DomainWarp(long seed, double strength, double frequency) {
        // Three uncorrelated offsets so axes don't track each other.
        this.warpX = new FbmNoise(mix(seed, 0xA1L));
        this.warpZ = new FbmNoise(mix(seed, 0xB2L));
        this.warpY = new FbmNoise(mix(seed, 0xC3L));
        this.strength = strength;
        this.frequency = frequency;
    }

    public double[] warp3(double x, double y, double z) {
        double dx = warpX.fbm(x * frequency, y * frequency, z * frequency, 2) * strength;
        double dy = warpY.fbm(x * frequency, y * frequency, z * frequency, 2) * strength;
        double dz = warpZ.fbm(x * frequency, y * frequency, z * frequency, 2) * strength;
        return new double[]{x + dx, y + dy, z + dz};
    }

    public double[] warp2(double x, double z) {
        double dx = warpX.fbm2D(x * frequency, z * frequency, 2) * strength;
        double dz = warpZ.fbm2D(x * frequency, z * frequency, 2) * strength;
        return new double[]{x + dx, z + dz};
    }

    private static long mix(long seed, long salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}
