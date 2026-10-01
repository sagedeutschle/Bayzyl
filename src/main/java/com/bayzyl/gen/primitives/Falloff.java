package com.bayzyl.gen.primitives;

/**
 * Falloff curves used to blend gen-brush effects into surrounding
 * terrain. Picking the right falloff is the difference between a brush
 * that looks pasted on and one that reads as natural.
 */
public final class Falloff {
    private Falloff() {
    }

    /** Linear from 1 at t=0 to 0 at t=1. Sharp, mechanical. */
    public static double linear(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        return 1.0 - t;
    }

    /** Smoothstep. Soft shoulder at center, soft heel at edge. Default for ridges/plateaus. */
    public static double smooth(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        return 1.0 - (t * t * (3.0 - 2.0 * t));
    }

    /** Smootherstep. Even softer than smooth. Use when you want minimal seam. */
    public static double smoother(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        return 1.0 - (t * t * t * (t * (t * 6.0 - 15.0) + 10.0));
    }

    /** Quadratic ease-out. Sharp center, soft edge. */
    public static double sharp(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        double f = 1.0 - t;
        return f * f;
    }

    /** Plateau curve: stays high near center, then falls quickly. Used by /brush gen plateau. */
    public static double plateau(double t, double flatness) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        double clampedFlat = Math.max(0.0, Math.min(0.9, flatness));
        if (t < clampedFlat) {
            return 1.0;
        }
        double remap = (t - clampedFlat) / (1.0 - clampedFlat);
        return 1.0 - (remap * remap * (3.0 - 2.0 * remap));
    }

    /** Cliff curve: stays high until the edge, then drops fast. Used by /brush gen cliff. */
    public static double cliffEdge(double t, double steepness) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        double s = Math.max(0.0, Math.min(0.95, steepness));
        if (t < s) {
            return 1.0;
        }
        double remap = (t - s) / (1.0 - s);
        return Math.max(0.0, 1.0 - Math.pow(remap, 0.35));
    }
}
