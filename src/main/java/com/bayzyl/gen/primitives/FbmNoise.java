package com.bayzyl.gen.primitives;

/**
 * Multi-octave fractional Brownian motion over value noise.
 *
 * Decoupled from {@code com.bayzyl.detail.primitives.NoiseField} so the
 * gen brushes can evolve their math independently from the detail brush
 * presets — they need access to the raw octave loop and to ridged
 * variants that the detail framework does not.
 */
public final class FbmNoise {
    private final long seed;

    public FbmNoise(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

    public double sample(double x, double y, double z) {
        int x0 = floor(x);
        int y0 = floor(y);
        int z0 = floor(z);
        double tx = smoothstep(x - x0);
        double ty = smoothstep(y - y0);
        double tz = smoothstep(z - z0);

        double c000 = hashed(x0, y0, z0);
        double c100 = hashed(x0 + 1, y0, z0);
        double c010 = hashed(x0, y0 + 1, z0);
        double c110 = hashed(x0 + 1, y0 + 1, z0);
        double c001 = hashed(x0, y0, z0 + 1);
        double c101 = hashed(x0 + 1, y0, z0 + 1);
        double c011 = hashed(x0, y0 + 1, z0 + 1);
        double c111 = hashed(x0 + 1, y0 + 1, z0 + 1);

        double xy0 = lerp(lerp(c000, c100, tx), lerp(c010, c110, tx), ty);
        double xy1 = lerp(lerp(c001, c101, tx), lerp(c011, c111, tx), ty);
        return lerp(xy0, xy1, tz);
    }

    public double sample2D(double x, double z) {
        return sample(x, 0.0, z);
    }

    public double fbm(double x, double y, double z, int octaves, double lacunarity, double gain) {
        double total = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double max = 0.0;
        for (int i = 0; i < octaves; i++) {
            total += sample(x * frequency, y * frequency, z * frequency) * amplitude;
            max += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return max == 0 ? 0 : total / max;
    }

    public double fbm(double x, double y, double z, int octaves) {
        return fbm(x, y, z, octaves, 2.0, 0.5);
    }

    public double fbm2D(double x, double z, int octaves) {
        return fbm(x, 0.0, z, octaves);
    }

    /** Ridged noise: {@code 1 - 2*|fbm|}. Produces sharp crests near zero crossings. */
    public double ridged(double x, double y, double z, int octaves) {
        double total = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double max = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - 2.0 * Math.abs(sample(x * frequency, y * frequency, z * frequency));
            total += n * amplitude;
            max += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return max == 0 ? 0 : total / max;
    }

    public double ridged2D(double x, double z, int octaves) {
        return ridged(x, 0.0, z, octaves);
    }

    /** Billowy noise: {@code 2*|fbm| - 1}. Rolling rounded humps — used by dunes/boulders. */
    public double billow(double x, double y, double z, int octaves) {
        double total = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double max = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = 2.0 * Math.abs(sample(x * frequency, y * frequency, z * frequency)) - 1.0;
            total += n * amplitude;
            max += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return max == 0 ? 0 : total / max;
    }

    public double billow2D(double x, double z, int octaves) {
        return billow(x, 0.0, z, octaves);
    }

    private double hashed(int x, int y, int z) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L + x;
        h = h * 0xBF58476D1CE4E5B9L + y;
        h = h * 0x94D049BB133111EBL + z;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        double normalized = ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
        return normalized * 2.0 - 1.0;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
