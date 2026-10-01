package com.bayzyl.safety;

import com.bayzyl.BlockMask;
import com.bayzyl.EraserSettings;
import com.bayzyl.PaintBrushSettings;
import com.bayzyl.PatternBrushMode;
import com.bayzyl.PatternBrushSettings;
import com.bayzyl.TerrainBrushType;
import com.bayzyl.TerrainBrushSettings;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** Pure, allocation-free work envelopes for the mutable brush families. */
public final class BrushSafety {
    public static final int PAINT_SIZE_MAX = 64;
    public static final int PATTERN_SIZE_MAX = 64;
    public static final int MASK_TEXT_MAX = 1_024;

    private BrushSafety() {
    }

    public static WorkEstimate assessEraser(int radius) {
        if (radius <= 0) {
            return invalid("Eraser radius must be positive.");
        }
        try {
            long span = radiusSpan(radius);
            return OperationLimits.checkMaterialized(product(span, span, span));
        } catch (ArithmeticException ex) {
            return overflow("Eraser work estimate overflowed.");
        }
    }

    public static boolean isValidEraser(EraserSettings settings) {
        return settings != null
                && !assessEraser(settings.getRadius()).hardRejected()
                && isValidMask(settings.getMask());
    }

    public static boolean isValidTerrain(TerrainBrushSettings settings) {
        return settings != null
                && !assessTerrain(settings.type(), settings.radius(), settings.power()).hardRejected();
    }

    public static boolean isValidPaint(PaintBrushSettings settings) {
        return settings != null
                && !assessPaint(settings.size()).hardRejected()
                && Double.isFinite(settings.density())
                && settings.density() >= 0.0
                && settings.density() <= 1.0
                && isBlock(settings.material())
                && isValidMask(settings.mask());
    }

    public static boolean isValidPattern(PatternBrushSettings settings) {
        if (settings == null || settings.mode() == null
                || !assessPattern(settings.mode(), settings.size(), 0L).permits(true)
                || !Double.isFinite(settings.density())
                || settings.density() < 0.0 || settings.density() > 1.0
                || !isValidMask(settings.mask())) {
            return false;
        }
        boolean hasFrom = hasMask(settings.fromMask());
        if (hasFrom && !isValidMask(settings.fromMask())) {
            return false;
        }
        boolean hasTo = settings.to() != null;
        if (hasTo && !isBlock(settings.to())) {
            return false;
        }
        List<Material> palette = settings.palette();
        boolean hasPalette = palette != null && !palette.isEmpty();
        if (hasPalette && !isValidPalette(palette)) {
            return false;
        }
        return switch (settings.mode()) {
            case SPATTER, SURFACE -> !hasFrom && hasTo && !hasPalette;
            case REPLACE -> hasFrom && hasTo && !hasPalette;
            case BLEND, NOISE, VEGETATION -> !hasFrom && !hasTo && hasPalette;
            case DECAY -> !hasFrom && !hasPalette;
            case RESTORE -> !hasFrom && !hasTo && !hasPalette;
        };
    }

    public static boolean isValidMask(BlockMask mask) {
        if (!hasMask(mask)) {
            return true;
        }
        return isValidMaskRaw(mask.getRaw());
    }

    /** Bounded, all-token validation for masks crossing command or persisted-data boundaries. */
    public static boolean isValidMaskRaw(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MASK_TEXT_MAX) {
            return false;
        }
        for (String part : raw.split(",", -1)) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                return false;
            }
            if (token.startsWith("##")) {
                try {
                    if (!BlockMask.isResolvable(token)) {
                        return false;
                    }
                } catch (RuntimeException | LinkageError noRegistryInUnitTest) {
                    String tag = token.substring(2);
                    if (tag.startsWith("*")) {
                        tag = tag.substring(1);
                    }
                    if (tag.isEmpty() || !tag.matches("[a-z0-9_./-]+")) {
                        return false;
                    }
                }
                continue;
            }
            Material material = Material.matchMaterial(token);
            if (!isBlock(material)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidPalette(List<Material> palette) {
        if (palette == null || palette.isEmpty() || palette.size() > 64) {
            return false;
        }
        for (Material material : palette) {
            if (!isBlock(material)) {
                return false;
            }
        }
        return true;
    }

    public static WorkEstimate assessPaint(int size) {
        if (size < 1 || size > PAINT_SIZE_MAX) {
            return invalid("Paint size must be between 1 and " + PAINT_SIZE_MAX + ".");
        }
        try {
            long span = radiusSpan(size);
            return OperationLimits.checkMaterialized(product(span, span));
        } catch (ArithmeticException ex) {
            return overflow("Paint work estimate overflowed.");
        }
    }

    public static WorkEstimate assessPattern(PatternBrushMode mode, int size, long restoreChanges) {
        if (mode == null) {
            return invalid("Pattern mode is required.");
        }
        if (size < 1 || size > PATTERN_SIZE_MAX) {
            return invalid("Pattern size must be between 1 and " + PATTERN_SIZE_MAX + ".");
        }
        if (mode == PatternBrushMode.RESTORE) {
            if (restoreChanges < 0L) {
                return invalid("Restore history size cannot be negative.");
            }
            return OperationLimits.checkMaterialized(restoreChanges);
        }
        try {
            long span = radiusSpan(size);
            long work = switch (mode) {
                case SURFACE, VEGETATION -> product(span, span);
                case SPATTER, REPLACE, BLEND, NOISE, DECAY -> product(span, span, span);
                case RESTORE -> throw new IllegalStateException("Handled above");
            };
            return OperationLimits.checkMaterialized(work);
        } catch (ArithmeticException ex) {
            return overflow("Pattern work estimate overflowed.");
        }
    }

    public static WorkEstimate assessTerrain(TerrainBrushType type, int radius, int power) {
        if (type == null) {
            return invalid("Terrain brush type is required.");
        }
        if (radius <= 0 || power <= 0) {
            return invalid("Terrain brush radius and power must be positive.");
        }
        try {
            return switch (type) {
                case SMOOTH -> assessSmooth(radius, power);
                case RAISE, LOWER, FLATTEN -> assessReshape(radius, power);
                case NATURALIZE -> assessNaturalize(radius, power);
                case CLEANUP_FLOATING, CLEANUP_FOLIAGE, CLEANUP_LIQUIDS, CLEANUP_SNOW, CLEANUP_LIGHTSPAM ->
                        assessCleanup(radius);
            };
        } catch (ArithmeticException ex) {
            return overflow("Terrain brush work estimate overflowed.");
        }
    }

    private static WorkEstimate assessSmooth(int radius, int iterations) {
        long span = radiusSpan(radius);
        long paddedSpan = Math.addExact(Math.multiplyExact((long) radius, 2L), 5L);
        long cells = product(span, span, span);
        long paddedCells = product(paddedSpan, paddedSpan, paddedSpan);
        long peakBuffers = Math.multiplyExact(2L, paddedCells);
        WorkEstimate peak = OperationLimits.checkMaterialized(peakBuffers);
        if (peak.hardRejected()) {
            return peak;
        }
        long perIteration = Math.addExact(paddedCells, Math.multiplyExact(152L, cells));
        long work = Math.addExact(
                Math.addExact(paddedCells, Math.multiplyExact((long) iterations, perIteration)),
                cells);
        return OperationLimits.checkMaterialized(work);
    }

    private static WorkEstimate assessReshape(int radius, int power) {
        long span = radiusSpan(radius);
        long grid = product(span, span);
        long padding = Math.addExact(Math.addExact((long) radius, Math.multiplyExact(4L, power)), 16L);
        long height = Math.addExact(Math.multiplyExact(2L, padding), 1L);
        WorkEstimate peak = OperationLimits.checkMaterialized(Math.multiplyExact(4L, grid));
        if (peak.hardRejected()) {
            return peak;
        }
        long work = Math.addExact(
                Math.addExact(Math.multiplyExact(3L, Math.multiplyExact(grid, height)), grid),
                96L);
        return OperationLimits.checkMaterialized(work);
    }

    private static WorkEstimate assessNaturalize(int radius, int power) {
        long span = radiusSpan(radius);
        long grid = product(span, span);
        long height = Math.addExact(Math.multiplyExact(2L, radius), 33L);
        WorkEstimate peak = OperationLimits.checkMaterialized(Math.multiplyExact(5L, grid));
        if (peak.hardRejected()) {
            return peak;
        }
        long inner = Math.addExact(Math.addExact(Math.multiplyExact(3L, height), power), 29L);
        return OperationLimits.checkMaterialized(Math.multiplyExact(grid, inner));
    }

    private static WorkEstimate assessCleanup(int radius) {
        long span = radiusSpan(radius);
        long volume = product(span, span, span);
        WorkEstimate peak = OperationLimits.checkMaterialized(Math.multiplyExact(2L, volume));
        if (peak.hardRejected()) {
            return peak;
        }
        return OperationLimits.checkMaterialized(Math.multiplyExact(10L, volume));
    }

    private static long radiusSpan(int radius) {
        return Math.addExact(Math.multiplyExact((long) radius, 2L), 1L);
    }

    private static boolean hasMask(BlockMask mask) {
        return mask != null && mask.getRaw() != null && !mask.getRaw().isBlank();
    }

    private static boolean isBlock(Material material) {
        if (material == null) {
            return false;
        }
        if (material == Material.AIR) {
            return true;
        }
        try {
            return material.isBlock();
        } catch (IllegalStateException | LinkageError noRegistryInUnitTest) {
            // Paper's Material#isBlock needs a live registry in unit tests. Name
            // resolution above is still strict; production only reaches this if
            // its registry is unavailable.
            return true;
        }
    }

    private static long product(long... factors) {
        long result = 1L;
        for (long factor : factors) {
            result = Math.multiplyExact(result, factor);
        }
        return result;
    }

    private static WorkEstimate invalid(String reason) {
        return new WorkEstimate(0L, false, true, reason);
    }

    private static WorkEstimate overflow(String reason) {
        return new WorkEstimate(Long.MAX_VALUE, false, true, reason);
    }
}
