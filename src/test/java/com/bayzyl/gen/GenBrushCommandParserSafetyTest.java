package com.bayzyl.gen;

import com.bayzyl.safety.WorkEstimate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GenBrushCommandParserSafetyTest {
    @Test
    void exactRadiusBoundariesAreEnforcedDuringCommandParsing() {
        WorkEstimate radius22 = GenBrushSafety.assess(parse(GenBrushType.RIDGE, "size:22"));
        WorkEstimate radius23 = GenBrushSafety.assess(parse(GenBrushType.RIDGE, "size:23"));
        WorkEstimate radius35 = GenBrushSafety.assess(parse(GenBrushType.RIDGE, "size:35"));

        assertFalse(radius22.confirmationRequired());
        assertTrue(radius23.confirmationRequired());
        assertTrue(radius35.confirmationRequired());
        assertFalse(radius35.hardRejected());
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "size:36"));
    }

    @Test
    void presentInvalidRadiusAndConsumedParametersRejectInsteadOfClampingOrFallingBack() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "size:0"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "-1"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "height:0"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.VALLEY, "water:yes"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "confirm:maybe"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "mask:definitely_not_a_block"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "subtype:noodle"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "seed:not-a-long"));
    }

    @Test
    void boundedUnknownExtensionsSurviveWhileOversizedTailsReject() {
        List<String> maximum = new ArrayList<>();
        for (int i = 0; i < GenBrushSafety.MAX_PARAMETER_COUNT; i++) {
            maximum.add("extension" + i + ":ok");
        }
        GenBrushSettings settings = GenBrushCommandParser.parse(
                GenBrushType.RIDGE, maximum.toArray(String[]::new));

        assertEquals(GenBrushSafety.MAX_PARAMETER_COUNT, settings.parameters().raw().size());

        maximum.add("one_too_many:no");
        assertThrows(IllegalArgumentException.class, () -> GenBrushCommandParser.parse(
                GenBrushType.RIDGE, maximum.toArray(String[]::new)));
        assertThrows(IllegalArgumentException.class, () -> parse(
                GenBrushType.RIDGE,
                "extension:" + "v".repeat(GenBrushSafety.MAX_PARAMETER_VALUE_LENGTH + 1)));
    }

    @Test
    void boulderInnerRadiusUsesTheSameParameterKeyAsPersistedSettings() {
        GenBrushSettings settings = parse(
                GenBrushType.BOULDER, "size:8", "radius:3", "count:4");

        assertEquals(8, settings.radius());
        assertEquals("3", settings.parameters().get("radius", null));
        assertEquals("4", settings.parameters().get("count", null));
    }

    @Test
    void loopDrivingOverflowAndNonFiniteValuesRejectThroughTheParserEntryPoint() {
        String maximum = Integer.toString(Integer.MAX_VALUE);

        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "size:1", "height:" + maximum));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.VALLEY, "size:1", "depth:" + maximum));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.CAVE, "size:1", "vertical:" + maximum));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.ERODE, "size:1", "passes:" + maximum));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RAVINE, "size:1",
                        "length:" + maximum, "depth:" + maximum, "width:2"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.BOULDER, "size:1",
                        "count:" + maximum, "radius:" + maximum));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.CAVE, "frequency:NaN"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "warp:Infinity"));
    }

    @Test
    void duplicateNormalizedParametersRejectInsteadOfOverwritingInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "height:not-a-number", "height:5"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.RIDGE, "confirm:maybe", "CONFIRM:true"));
        assertThrows(IllegalArgumentException.class,
                () -> parse(GenBrushType.BOULDER, "radius:not-a-number", "RADIUS:2"));
    }

    private static GenBrushSettings parse(GenBrushType type, String... options) {
        return GenBrushCommandParser.parse(type, options);
    }
}
