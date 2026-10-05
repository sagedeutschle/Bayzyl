package com.bayzyl.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Regression: an unrecognised mode: value (e.g. a typo like mode:rwa) silently fell back to normalized, so the
// formula evaluated in a different coordinate space than the player asked for and no error was shown.
final class CoordinateModeParseTest {
    @Test
    void unknownCoordinateModeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CoordinateMode.parse("rwa"));
        assertThrows(IllegalArgumentException.class, () -> CoordinateMode.parse(""));
    }

    @Test
    void knownCoordinateModesStillParse() {
        assertEquals(CoordinateMode.RAW, CoordinateMode.parse("RAW"));
        assertEquals(CoordinateMode.CENTER, CoordinateMode.parse("centre"));
        assertEquals(CoordinateMode.ORIGIN, CoordinateMode.parse("placement"));
        assertEquals(CoordinateMode.NORMALIZED, CoordinateMode.parse("normalized"));
        assertEquals(CoordinateMode.NORMALIZED, CoordinateMode.parse(null));
    }
}
