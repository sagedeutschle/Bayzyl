package com.bayzyl.safety;

/** Pure checked-arithmetic estimates and release caps for materialized work. */
public final class OperationLimits {
    public static final long CONFIRMATION_THRESHOLD = 200_000L;
    public static final long MATERIALIZED_HARD_MAX = 1_000_000L;
    public static final long BIOME_CHUNK_HARD_MAX = 4_096L;
    public static final long RECOVERY_CLIPBOARD_HARD_MAX = 250_000L;
    public static final int FOREST_RADIUS_HARD_MAX = 32;
    public static final int PUMPKIN_RADIUS_HARD_MAX = 64;
    public static final long GEN_BRUSH_HARD_MAX = 750_000L;

    private static final String WITHIN_LIMITS = "Operation is within the configured work limits.";
    private static final String OVERFLOW = "Operation estimate exceeds the supported numeric range.";

    private OperationLimits() {
    }

    public static long inclusiveSpan(int minimum, int maximum) {
        if (maximum < minimum) {
            throw new IllegalArgumentException("Maximum must be greater than or equal to minimum.");
        }
        return (long) maximum - (long) minimum + 1L;
    }

    public static WorkEstimate checkedAdd(long left, long right) {
        if (left < 0L || right < 0L) {
            return invalid("Checked addition operands cannot be negative.");
        }
        try {
            return checkMaterialized(Math.addExact(left, right));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    public static WorkEstimate checkedMultiply(long left, long right) {
        if (left < 0L || right < 0L) {
            return invalid("Checked multiplication operands cannot be negative.");
        }
        try {
            return checkMaterialized(Math.multiplyExact(left, right));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    public static WorkEstimate checkMaterialized(long workUnits) {
        return checkCap(workUnits, MATERIALIZED_HARD_MAX, "Materialized operation");
    }

    public static WorkEstimate checkBiomeChunks(long chunks) {
        return checkCap(chunks, BIOME_CHUNK_HARD_MAX, "Biome chunk footprint");
    }

    public static WorkEstimate checkRecoveryClipboard(long blocks) {
        return checkCap(blocks, RECOVERY_CLIPBOARD_HARD_MAX, "Recovery clipboard");
    }

    public static WorkEstimate checkForestRadius(int radius) {
        return checkRadius(radius, FOREST_RADIUS_HARD_MAX, "Forest radius");
    }

    public static WorkEstimate checkPumpkinRadius(int radius) {
        return checkRadius(radius, PUMPKIN_RADIUS_HARD_MAX, "Pumpkin radius");
    }

    public static WorkEstimate checkGenBrush(long workUnits) {
        return checkCap(workUnits, GEN_BRUSH_HARD_MAX, "Generation brush");
    }

    public static WorkEstimate estimateSelection(
            int minX, int maxX,
            int minY, int maxY,
            int minZ, int maxZ
    ) {
        try {
            return materializedProduct(
                    inclusiveSpan(minX, maxX),
                    inclusiveSpan(minY, maxY),
                    inclusiveSpan(minZ, maxZ));
        } catch (IllegalArgumentException ex) {
            return invalid("Selection spans must be positive and inclusive.");
        }
    }

    public static WorkEstimate estimateSphere(int radiusX, int radiusY, int radiusZ) {
        if (radiusX <= 0 || radiusY <= 0 || radiusZ <= 0) {
            return invalid("Sphere radii must be positive.");
        }
        return materializedProduct(radiusSpan(radiusX), radiusSpan(radiusY), radiusSpan(radiusZ));
    }

    public static WorkEstimate estimateCylinder(int radiusX, int radiusZ, int height) {
        if (radiusX <= 0 || radiusZ <= 0 || height <= 0) {
            return invalid("Cylinder radii and height must be positive.");
        }
        return materializedProduct(radiusSpan(radiusX), radiusSpan(radiusZ), height);
    }

    public static WorkEstimate estimatePyramid(int size) {
        if (size <= 0) {
            return invalid("Pyramid size must be positive.");
        }
        long span = radiusSpan(size);
        return materializedProduct(span, span, (long) size + 1L);
    }

    public static WorkEstimate estimateStructure(int radius, int height) {
        if (radius <= 0 || height <= 0) {
            return invalid("Structure radius and height must be positive.");
        }
        long span = radiusSpan(radius);
        return materializedProduct(span, span, (long) height + 2L);
    }

    public static WorkEstimate estimateStructureChunks(int radius) {
        if (radius <= 0) {
            return invalid("Structure radius must be positive.");
        }
        long chunkRadius = ((long) radius >> 4) + 1L;
        try {
            long span = Math.addExact(Math.multiplyExact(chunkRadius, 2L), 1L);
            return checkBiomeChunks(Math.multiplyExact(span, span));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    public static WorkEstimate estimateBiomeChunks(int minX, int maxX, int minZ, int maxZ) {
        if (maxX < minX || maxZ < minZ) {
            return invalid("Biome bounds must have positive inclusive spans.");
        }
        long minChunkX = minX >> 4;
        long maxChunkX = maxX >> 4;
        long minChunkZ = minZ >> 4;
        long maxChunkZ = maxZ >> 4;
        try {
            long spanX = Math.addExact(Math.subtractExact(maxChunkX, minChunkX), 1L);
            long spanZ = Math.addExact(Math.subtractExact(maxChunkZ, minChunkZ), 1L);
            return checkBiomeChunks(Math.multiplyExact(spanX, spanZ));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    public static WorkEstimate estimateForest(int radius, double density) {
        WorkEstimate radiusCheck = checkForestRadius(radius);
        if (radiusCheck.hardRejected()) {
            return radiusCheck;
        }
        if (!Double.isFinite(density) || density < 0.0 || density > 100.0) {
            return invalid("Forest density must be finite and between 0 and 100.");
        }
        long span = radiusSpan(radius);
        long densityMultiplier = Math.max(1L, Math.round(density));
        return materializedProduct(span, span, densityMultiplier);
    }

    public static WorkEstimate estimatePumpkins(int radius) {
        WorkEstimate radiusCheck = checkPumpkinRadius(radius);
        if (radiusCheck.hardRejected()) {
            return radiusCheck;
        }
        long span = radiusSpan(radius);
        return materializedProduct(span, span);
    }

    public static WorkEstimate estimateGenBrush(int radius, long multiplier) {
        if (radius <= 0 || multiplier <= 0L) {
            return invalid("Generation brush radius and multiplier must be positive.");
        }
        long span = radiusSpan(radius);
        try {
            long volume = checkedProduct(span, span, span, multiplier);
            return checkGenBrush(volume);
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    private static WorkEstimate materializedProduct(long... factors) {
        try {
            return checkMaterialized(checkedProduct(factors));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    private static long checkedProduct(long... factors) {
        long result = 1L;
        for (long factor : factors) {
            if (factor <= 0L) {
                throw new IllegalArgumentException("Estimate factors must be positive.");
            }
            result = Math.multiplyExact(result, factor);
        }
        return result;
    }

    private static long radiusSpan(int radius) {
        return (long) radius * 2L + 1L;
    }

    private static WorkEstimate checkRadius(int radius, int hardMaximum, String label) {
        if (radius <= 0) {
            return invalid(label + " must be positive.");
        }
        if (radius > hardMaximum) {
            return rejected(radius, label + " exceeds the hard maximum of " + hardMaximum + ".");
        }
        return allowed(radius);
    }

    private static WorkEstimate checkCap(long workUnits, long hardMaximum, String label) {
        if (workUnits < 0L) {
            return invalid(label + " cannot be negative.");
        }
        if (workUnits > hardMaximum) {
            return rejected(workUnits, label + " exceeds the hard maximum of " + hardMaximum + ".");
        }
        if (workUnits > CONFIRMATION_THRESHOLD) {
            return new WorkEstimate(workUnits, true, false,
                    label + " requires confirmation above " + CONFIRMATION_THRESHOLD + " work units.");
        }
        return allowed(workUnits);
    }

    private static WorkEstimate allowed(long workUnits) {
        return new WorkEstimate(workUnits, false, false, WITHIN_LIMITS);
    }

    private static WorkEstimate invalid(String reason) {
        return rejected(0L, reason);
    }

    private static WorkEstimate rejected(long workUnits, String reason) {
        return new WorkEstimate(Math.max(0L, workUnits), false, true, reason);
    }

    private static WorkEstimate overflow() {
        return rejected(Long.MAX_VALUE, OVERFLOW);
    }
}
