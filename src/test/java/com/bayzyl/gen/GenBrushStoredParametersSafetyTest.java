package com.bayzyl.gen;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenBrushStoredParametersSafetyTest {
    @Test
    void serializedParametersRejectEveryPresentMalformedOrOversizedForm() {
        assertThrows(IllegalArgumentException.class,
                () -> GenBrushSafety.parseStoredParameters(" "));
        assertThrows(IllegalArgumentException.class,
                () -> GenBrushSafety.parseStoredParameters("broken"));
        assertThrows(IllegalArgumentException.class,
                () -> GenBrushSafety.parseStoredParameters("a=1;a=2"));
        assertThrows(IllegalArgumentException.class,
                () -> GenBrushSafety.parseStoredParameters("a=1;"));
        assertThrows(IllegalArgumentException.class,
                () -> GenBrushSafety.parseStoredParameters(
                        "a=" + "v".repeat(GenBrushSafety.MAX_PARAMETER_VALUE_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> GenBrushSafety.parseStoredParameters(
                        "x".repeat(GenBrushSafety.MAX_SERIALIZED_PARAMETER_LENGTH + 1)));
    }

    @Test
    void emptyLegacyAndBoundedExtensionStorageRemainCompatible() {
        assertTrue(GenBrushSafety.parseStoredParameters(null).raw().isEmpty());
        assertTrue(GenBrushSafety.parseStoredParameters("").raw().isEmpty());
        assertEquals("value", GenBrushSafety.parseStoredParameters(
                "extension=value").get("extension", null));
    }

    @Test
    void maximumLoopInputsStayConstantSpaceAndHardRejectUnderRepetition() {
        GenBrushSettings hugeRavine = settings(GenBrushType.RAVINE,
                Map.of("length", Integer.toString(Integer.MAX_VALUE),
                        "depth", Integer.toString(Integer.MAX_VALUE),
                        "width", "2"));
        GenBrushSettings hugeBoulders = settings(GenBrushType.BOULDER,
                Map.of("count", Integer.toString(Integer.MAX_VALUE),
                        "radius", Integer.toString(Integer.MAX_VALUE)));

        for (int i = 0; i < 10_000; i++) {
            assertTrue(GenBrushSafety.assess(hugeRavine).hardRejected());
            assertTrue(GenBrushSafety.assess(hugeBoulders).hardRejected());
        }
    }

    private static GenBrushSettings settings(GenBrushType type, Map<String, String> parameters) {
        return new GenBrushSettings(
                type, CaveSubtype.AUTO, 1,
                new GenBrushParameters(parameters), null, true, 1L);
    }
}
