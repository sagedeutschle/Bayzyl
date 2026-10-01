package com.bayzyl.gen;

import com.bayzyl.safety.WorkEstimate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenBrushSafetyTest {
    @Test
    void baseRadiusBoundariesUseTheSharedExactLimits() {
        WorkEstimate radius22 = GenBrushSafety.assess(settings(22));
        WorkEstimate radius23 = GenBrushSafety.assess(settings(23));
        WorkEstimate radius35 = GenBrushSafety.assess(settings(35));
        WorkEstimate radius36 = GenBrushSafety.assess(settings(36));

        assertFalse(radius22.confirmationRequired());
        assertTrue(radius22.permits(false));

        assertTrue(radius23.confirmationRequired());
        assertFalse(radius23.permits(false));
        assertTrue(radius23.permits(true));

        assertTrue(radius35.confirmationRequired());
        assertFalse(radius35.hardRejected());
        assertTrue(radius35.permits(true));

        assertTrue(radius36.hardRejected());
        assertFalse(radius36.permits(true));
    }

    @Test
    void settingsRejectNonPositiveRadiusInsteadOfSilentlyClampingIt() {
        assertThrows(IllegalArgumentException.class, () -> settings(0));
        assertThrows(IllegalArgumentException.class, () -> settings(-1));
    }

    @Test
    void consumedNumericParametersMustBeFiniteAndInsideTheirDocumentedRanges() {
        assertRejected(settings(GenBrushType.RIDGE, Map.of("height", "0")));
        assertRejected(settings(GenBrushType.RIDGE, Map.of("intensity", "1.01")));
        assertRejected(settings(GenBrushType.RIDGE, Map.of("frequency", "NaN")));
        assertRejected(settings(GenBrushType.RIDGE, Map.of("warp", "Infinity")));
        assertRejected(settings(GenBrushType.DUNES, Map.of("wavelength", "1.99")));
        assertRejected(settings(GenBrushType.ERODE, Map.of("passes", "1.5")));
        assertRejected(settings(GenBrushType.MESA, Map.of("terraces", "65")));
    }

    @Test
    void consumedFlagsEnumsDirectionsAndBlockOverridesAreStrict() {
        assertRejected(settings(GenBrushType.PLATEAU, Map.of("edges", "rounded")));
        assertRejected(settings(GenBrushType.VALLEY, Map.of("water", "sometimes")));
        assertRejected(settings(GenBrushType.VALLEY, Map.of("river", "maybe")));
        assertRejected(settings(GenBrushType.ERODE, Map.of("debris", "sometimes")));
        assertRejected(settings(GenBrushType.RAVINE, Map.of("direction", "sideways")));
        assertRejected(settings(GenBrushType.BOULDER, Map.of("block", "definitely_not_a_block")));
        assertRejected(settings(GenBrushType.RIDGE, Map.of("confirm", "eventually")));

        assertAllowed(settings(GenBrushType.PLATEAU, Map.of("edges", "cliff")));
        assertAllowed(settings(GenBrushType.VALLEY, Map.of("water", "auto", "river", "true")));
        assertAllowed(settings(GenBrushType.RAVINE, Map.of("direction", "ne")));
        assertAllowed(settings(GenBrushType.BOULDER, Map.of("block", "minecraft:stone")));
    }

    @Test
    void unknownExtensionParametersAreInertOnlyWithinStrictStorageBounds() {
        assertAllowed(settings(GenBrushType.RIDGE,
                Map.of("x".repeat(GenBrushSafety.MAX_PARAMETER_KEY_LENGTH),
                        "v".repeat(GenBrushSafety.MAX_PARAMETER_VALUE_LENGTH))));

        Map<String, String> maximumCount = new LinkedHashMap<>();
        for (int i = 0; i < GenBrushSafety.MAX_PARAMETER_COUNT; i++) {
            maximumCount.put("extension" + i, "ok");
        }
        assertAllowed(settings(GenBrushType.RIDGE, maximumCount));

        Map<String, String> tooMany = new LinkedHashMap<>(maximumCount);
        tooMany.put("one_too_many", "no");
        assertRejected(settings(GenBrushType.RIDGE, tooMany));
        assertRejected(settings(GenBrushType.RIDGE,
                Map.of("x".repeat(GenBrushSafety.MAX_PARAMETER_KEY_LENGTH + 1), "value")));
        assertRejected(settings(GenBrushType.RIDGE,
                Map.of("extension", "v".repeat(GenBrushSafety.MAX_PARAMETER_VALUE_LENGTH + 1))));

        Map<String, String> separatorOverflow = new LinkedHashMap<>();
        for (int i = 0; i < GenBrushSafety.MAX_PARAMETER_COUNT; i++) {
            separatorOverflow.put(String.format("key%029d", i), "v".repeat(95));
        }
        assertEquals(GenBrushSafety.MAX_SERIALIZED_PARAMETER_LENGTH + 31,
                new GenBrushParameters(separatorOverflow).serialize().length());
        assertRejected(settings(GenBrushType.RIDGE, separatorOverflow));
    }

    @Test
    void nonCaveBrushCannotCarryAnActiveCaveSubtype() {
        GenBrushSettings settings = new GenBrushSettings(
                GenBrushType.RIDGE,
                CaveSubtype.NOODLE,
                10,
                GenBrushParameters.empty(),
                null,
                true,
                1L);

        assertRejected(settings);
    }

    @Test
    void loopDrivingParametersIncreaseTheCheckedWorkEstimate() {
        assertEquals(500L, GenBrushSafety.assess(
                settings(GenBrushType.RIDGE, 2, Map.of("height", "20"))).workUnits());
        assertEquals(500L, GenBrushSafety.assess(
                settings(GenBrushType.VALLEY, 2, Map.of("depth", "20"))).workUnits());
        assertEquals(525L, GenBrushSafety.assess(
                settings(GenBrushType.CAVE, 2, Map.of("vertical", "10"))).workUnits());
        assertEquals(750L, GenBrushSafety.assess(
                settings(GenBrushType.ERODE, 2, Map.of("passes", "3"))).workUnits());
        assertEquals(266L, GenBrushSafety.assess(
                settings(GenBrushType.BOULDER, 2, Map.of("count", "2", "radius", "2"))).workUnits());
    }

    @Test
    void everyLoopDrivingOverflowOrOversizedProductHardRejects() {
        assertRejected(settings(GenBrushType.RIDGE, 1, Map.of("height", Integer.toString(Integer.MAX_VALUE))));
        assertRejected(settings(GenBrushType.BASIN, 1, Map.of("depth", Integer.toString(Integer.MAX_VALUE))));
        assertRejected(settings(GenBrushType.CAVE, 1, Map.of("vertical", Integer.toString(Integer.MAX_VALUE))));
        assertRejected(settings(GenBrushType.ERODE, 10, Map.of("passes", "41")));
        assertRejected(settings(GenBrushType.RAVINE, 1,
                Map.of("length", "10000", "depth", "100", "width", "2")));
        assertRejected(settings(GenBrushType.BOULDER, 1,
                Map.of("count", "100", "radius", "10")));
    }

    @Test
    void zeroTerracesRemainAValidBoundedNoStepMode() {
        assertAllowed(settings(GenBrushType.MESA, Map.of("terraces", "0")));
    }

    private static GenBrushSettings settings(int radius) {
        return settings(GenBrushType.RIDGE, radius, Map.of());
    }

    private static GenBrushSettings settings(GenBrushType type, Map<String, String> parameters) {
        return settings(type, 10, parameters);
    }

    private static GenBrushSettings settings(GenBrushType type, int radius, Map<String, String> parameters) {
        return new GenBrushSettings(
                type,
                CaveSubtype.AUTO,
                radius,
                new GenBrushParameters(parameters),
                null,
                true,
                1L);
    }

    private static void assertRejected(GenBrushSettings settings) {
        WorkEstimate estimate = GenBrushSafety.assess(settings);
        assertTrue(estimate.hardRejected(), estimate.reason());
        assertFalse(estimate.permits(true));
    }

    private static void assertAllowed(GenBrushSettings settings) {
        WorkEstimate estimate = GenBrushSafety.assess(settings);
        assertFalse(estimate.hardRejected(), estimate.reason());
    }
}
