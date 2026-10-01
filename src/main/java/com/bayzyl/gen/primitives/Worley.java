package com.bayzyl.gen.primitives;

/**
 * Worley (cellular) noise. Returns the distance to the nearest feature
 * point in a hashed lattice — handy for boulders, cracked terrain, and
 * cave room placement. F1 is the nearest distance; F2 is the second
 * nearest; F2 - F1 gives a crack pattern.
 */
public final class Worley {
    private final long seed;

    public Worley(long seed) {
        this.seed = seed;
    }

    public double f1(double x, double y, double z) {
        return distances(x, y, z)[0];
    }

    public double f1_2D(double x, double z) {
        return distances(x, 0.0, z)[0];
    }

    public double crack(double x, double y, double z) {
        double[] d = distances(x, y, z);
        return d[1] - d[0];
    }

    public double crack2D(double x, double z) {
        return crack(x, 0.0, z);
    }

    private double[] distances(double x, double y, double z) {
        int cx = floor(x);
        int cy = floor(y);
        int cz = floor(z);
        double best = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int ix = cx + dx;
                    int iy = cy + dy;
                    int iz = cz + dz;
                    double[] p = featurePoint(ix, iy, iz);
                    double ex = (ix + p[0]) - x;
                    double ey = (iy + p[1]) - y;
                    double ez = (iz + p[2]) - z;
                    double d = ex * ex + ey * ey + ez * ez;
                    if (d < best) {
                        second = best;
                        best = d;
                    } else if (d < second) {
                        second = d;
                    }
                }
            }
        }
        return new double[]{Math.sqrt(best), Math.sqrt(second)};
    }

    private double[] featurePoint(int x, int y, int z) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L + x;
        h = h * 0xBF58476D1CE4E5B9L + y;
        h = h * 0x94D049BB133111EBL + z;
        h ^= (h >>> 33);
        double px = ((h >>> 11) & ((1L << 24) - 1)) / (double) (1L << 24);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        double py = ((h >>> 11) & ((1L << 24) - 1)) / (double) (1L << 24);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        double pz = ((h >>> 11) & ((1L << 24) - 1)) / (double) (1L << 24);
        return new double[]{px, py, pz};
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
