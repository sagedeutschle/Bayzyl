package com.bayzyl.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeneratorCommandParserSafetyTest {
    @Test
    void forestParserRejectsRadiusAbove32() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GeneratorCommandParser.parseForest(new String[]{"33"}));

        assertTrue(error.getMessage().contains("hard maximum of 32"), error.getMessage());
    }

    @Test
    void pumpkinParserRejectsRadiusAbove64() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GeneratorCommandParser.parsePumpkins(new String[]{"65"}));

        assertTrue(error.getMessage().contains("hard maximum of 64"), error.getMessage());
    }

    @Test
    void forestParserPreservesZeroDensity() {
        ForestGenRequest request = GeneratorCommandParser.parseForest(new String[]{"5", "tree", "0"});

        assertEquals(0.0, request.density());
    }

    @Test
    void forestParserAcceptsExactRadius32() {
        ForestGenRequest request = GeneratorCommandParser.parseForest(new String[]{"32"});

        assertEquals(32, request.size());
    }

    @Test
    void pumpkinParserAcceptsExactRadius64() {
        PumpkinPatchRequest request = GeneratorCommandParser.parsePumpkins(new String[]{"64"});

        assertEquals(64, request.size());
    }
}
