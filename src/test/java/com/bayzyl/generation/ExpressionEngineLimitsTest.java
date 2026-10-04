package com.bayzyl.generation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Regression: /generate formulas had no nesting, length or evaluation budget. A long or deeply nested expression
// either overflowed the stack (an Error nobody catches) or multiplied its cost by up to a million blocks.
final class ExpressionEngineLimitsTest {
    private static double eval(String expression) {
        return ExpressionEngine.compile(expression).evaluate(Map.of("x", 3.0, "y", 4.0, "z", 5.0));
    }

    @Test
    void deeplyNestedParenthesesAreRejectedInsteadOfOverflowingTheStack() {
        String expression = "(".repeat(20_000) + "1" + ")".repeat(20_000);

        assertThrows(IllegalArgumentException.class, () -> ExpressionEngine.compile(expression));
    }

    @Test
    void longUnaryChainsAreRejectedInsteadOfOverflowingTheStack() {
        String expression = "-".repeat(200_000) + "1";

        assertThrows(IllegalArgumentException.class, () -> ExpressionEngine.compile(expression));
    }

    @Test
    void veryLongExpressionsAreRejectedBeforeTheyCanStallEvaluation() {
        String expression = "1" + "+1".repeat(20_000);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExpressionEngine.compile(expression));
        assertTrue(error.getMessage().contains("too long"), error.getMessage());
    }

    @Test
    void ordinaryFormulasStillCompileAndReportTheirSize() {
        ExpressionEngine.Program sphere = ExpressionEngine.compile("1 - (x^2 + y^2 + z^2)");

        assertTrue(sphere.nodeCount() >= 9 && sphere.nodeCount() < 100, "node count " + sphere.nodeCount());
        assertEquals(1.0 - (9 + 16 + 25), sphere.evaluate(Map.of("x", 3.0, "y", 4.0, "z", 5.0)), 1e-9);
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        assertEquals(-4.0, eval("-2^2"), 1e-9);
        assertEquals(0.5, eval("2^-1"), 1e-9);
        assertEquals(512.0, eval("2^3^2"), 1e-9);
        assertEquals(-9.0, eval("-x^2"), 1e-9);
        assertEquals(9.0, eval("(-x)^2"), 1e-9);
        assertEquals(6.0, eval("--6"), 1e-9);
    }

    @Test
    void malformedNumbersReportAnInvalidNumberInsteadOfAJavaMessage() {
        for (String bad : new String[]{"1.2.3", ".", "x + 1..2"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ExpressionEngine.compile(bad), bad);
            assertTrue(error.getMessage().startsWith("Invalid number"), bad + " -> " + error.getMessage());
        }
    }
}
