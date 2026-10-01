package com.bayzyl.detail.primitives;

public final class FalloffCurve {
    private FalloffCurve() {
    }

    public static double linear(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        return 1.0 - t;
    }

    public static double smooth(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        return 1.0 - smoothstep(t);
    }

    public static double sharp(double t) {
        if (t <= 0) return 1.0;
        if (t >= 1) return 0.0;
        double f = 1.0 - t;
        return f * f;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
