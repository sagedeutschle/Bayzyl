package com.bayzyl.detail;

import com.bayzyl.safety.WorkEstimate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DetailBrushSafetyTest {
    private final DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
    private final DetailBrushSafety safety = new DetailBrushSafety(registry);

    @Test
    void everyRegisteredPresetDefaultHasAValidCheckedEnvelope() {
        for (DetailBrushPreset preset : registry.all()) {
            DetailBrushSettings settings = new DetailBrushSettings(
                    preset.id(), preset.defaults(), DetailBrushMode.STAMP);
            WorkEstimate estimate = safety.assess(settings);
            assertFalse(estimate.hardRejected(), preset.id() + ": " + estimate.reason());
            assertTrue(estimate.workUnits() > 0L, preset.id());
        }
    }

    @Test
    void cloudAndFlameMaxBoundingBoxesMatchTheSpecifiedEnvelopes() {
        DetailBrushSettings cloud = settings("cloud", Map.of(
                "volume", "32", "puffiness", "1", "density", "1",
                "flatness", "0", "opacity", "1", "tint", "storm"));
        DetailBrushSettings flame = settings("flame", Map.of(
                "heat", "1", "height", "64", "width", "24", "flicker", "1",
                "lean_x", "1", "lean_z", "-1", "density", "1"));

        assertEquals(300_763L, safety.assess(cloud).workUnits());
        assertEquals(637_065L, safety.assess(flame).workUnits());
        assertFalse(safety.assess(cloud).hardRejected());
        assertFalse(safety.assess(flame).hardRejected());
    }

    @Test
    void everyRemainingPresetMaximumMatchesItsCheckedLoopEnvelope() {
        assertEquals(70_785L, safety.assess(settings("bark", Map.of(
                "radius", "16", "height", "32", "grain", "1", "knots", "1",
                "coverage", "1", "species", "warped"))).workUnits());
        assertEquals(1_728L, safety.assess(settings("lightning", Map.of(
                "length", "96", "jaggedness", "1", "branches", "8", "branch_length", "1",
                "glow", "1", "direction", "west", "color", "red"))).workUnits());
        assertEquals(684L, safety.assess(settings("vine", Map.of(
                "length", "64", "droop", "1", "leaf_density", "1", "gap", "0.6",
                "species", "mangrove_root"))).workUnits());
        // This envelope intentionally charges the five center-list insertions as work.
        assertEquals(15_670L, safety.assess(settings("hearts", Map.of(
                "size", "12", "count", "5", "spread", "8", "density", "1",
                "sparkle_density", "1", "tone", "mixed", "rotation", "west"))).workUnits());
        assertEquals(9_908L, safety.assess(settings("rainbow", Map.of(
                "length", "96", "arc_height", "32", "bands", "7", "density", "1",
                "sparkle", "1", "cloud_size", "8", "palette", "sunset", "heading", "west"))).workUnits());
    }

    @Test
    void schemaRejectsUnknownNonfiniteFractionalIntegerRangeAndStringValues() {
        assertTrue(safety.assess(settings("flame", Map.of("heat", "NaN"))).hardRejected());
        assertTrue(safety.assess(settings("flame", Map.of("heat", "Infinity"))).hardRejected());
        assertTrue(safety.assess(settings("flame", Map.of("height", "1.0"))).hardRejected());
        assertTrue(safety.assess(settings("flame", Map.of("height", "65"))).hardRejected());
        assertTrue(safety.assess(settings("cloud", Map.of("tint", "invisible"))).hardRejected());
        assertTrue(safety.assess(settings("cloud", Map.of("unknown", "1"))).hardRejected());
        assertTrue(safety.assess(new DetailBrushSettings(
                "missing", DetailBrushParameters.empty(), DetailBrushMode.STAMP)).hardRejected());
        assertTrue(safety.assess(new DetailBrushSettings(
                "flame", DetailBrushParameters.empty(), null)).hardRejected());
    }

    @Test
    void establishedStringAliasesRemainValidButOtherFallbackTyposReject() {
        for (String species : new String[]{"darkoak", "flowering", "mangrove_roots", "root", "roots"}) {
            String preset = species.equals("darkoak") ? "bark" : "vine";
            assertFalse(safety.assess(settings(preset, Map.of("species", species))).hardRejected(), species);
        }
        assertTrue(safety.assess(settings("vine", Map.of("species", "mangrov"))).hardRejected());
        assertTrue(safety.assess(settings("hearts", Map.of("rotation", "sideways"))).hardRejected());
        assertTrue(safety.assess(settings("rainbow", Map.of("heading", "up"))).hardRejected());
    }

    @Test
    void canonicalStringValuesDriveTheSameVineEnvelopeAsRuntime() {
        DetailBrushSettings spacedRoot = settings("vine", Map.of(
                "length", "64", "species", "  Mangrove_Root  "));

        assertEquals(684L, safety.assess(spacedRoot).workUnits());
        DetailBrushSettings canonical = safety.requireValid(spacedRoot);
        assertEquals("mangrove_root", canonical.parameters().get("species", ""));
    }

    @Test
    void storedParameterParserBoundsBeforeSplitAndRejectsMalformedOrDuplicateEntries() {
        DetailBrushParameters parsed = safety.parseStoredParameters("height=8;width=3;heat=0.7");
        assertEquals("8", parsed.get("height", ""));

        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("broken"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("height="));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("height=8;HEIGHT=9"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters(";height=8"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("x".repeat(65) + "=1"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("x=" + "1".repeat(257)));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("a=1;".repeat(32) + "b=2"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("x".repeat(4_097)));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("height=8\n9"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("height=\t8"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters("\theight=8"));
        assertThrows(IllegalArgumentException.class, () -> safety.parseStoredParameters(" \t "));
    }

    @Test
    void centralParameterUpdateNormalizesOnlyValidValues() {
        DetailBrushSettings flame = settings("flame", Map.of());
        DetailBrushSettings updated = safety.withParameter(flame, "height", "12");
        assertEquals("12", updated.parameters().get("height", ""));
        assertThrows(IllegalArgumentException.class,
                () -> safety.withParameter(flame, "heat", "NaN"));
        assertThrows(IllegalArgumentException.class,
                () -> safety.withParameter(flame, "missing", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> safety.withParameter(flame, "heat", " ".repeat(257)));
        assertThrows(IllegalArgumentException.class,
                () -> safety.withParameter(flame, "heat", "\t0.5"));
        assertTrue(safety.assess(settings("flame", Map.of("heat", "0.5\n"))).hardRejected());

        Map<String, String> oversized = new LinkedHashMap<>();
        for (int i = 0; i < 17; i++) {
            oversized.put("unknown" + i, "x".repeat(240));
        }
        WorkEstimate oversizedEstimate = safety.assess(settings("flame", oversized));
        assertTrue(oversizedEstimate.hardRejected());
        assertTrue(oversizedEstimate.reason().contains("too long"));
        assertTrue(safety.assess(settings("flame", Map.of("heat", "0.5;evil=1"))).hardRejected());
    }

    private static DetailBrushSettings settings(String preset, Map<String, String> overrides) {
        return new DetailBrushSettings(preset,
                new DetailBrushParameters(new LinkedHashMap<>(overrides)), DetailBrushMode.STAMP);
    }
}
