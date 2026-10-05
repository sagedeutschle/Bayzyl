package com.bayzyl;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Material.isBlock() needs a running server (registries), so block-name lookups that would reach it
 * are stubbed; everything asserted here is the mask/distribution logic around them.
 */
final class EditMaskAndDistributionTest {
    private static MockedStatic<EditUtil> stubbedBlockNames() {
        MockedStatic<EditUtil> util = mockStatic(EditUtil.class);
        util.when(() -> EditUtil.parseBlock("stone")).thenReturn(Material.STONE);
        util.when(() -> EditUtil.parseBlock("dirt")).thenReturn(Material.DIRT);
        return util;
    }

    @Test
    void maskWhoseTokensAllFailToResolveMatchesNothingInsteadOfEverything() {
        try (MockedStatic<EditUtil> ignored = stubbedBlockNames()) {
            BlockMask typo = BlockMask.parse("stoen");

            assertFalse(typo.matches(Material.STONE));
            assertFalse(typo.matches(Material.DIAMOND_BLOCK));
            assertFalse(typo.isAny());
        }
    }

    @Test
    void maskKeepsResolvedTokensWhenOthersAreTypos() {
        try (MockedStatic<EditUtil> ignored = stubbedBlockNames()) {
            BlockMask mask = BlockMask.parse("stone,stoen");

            assertTrue(mask.matches(Material.STONE));
            assertFalse(mask.matches(Material.DIRT));
        }
    }

    @Test
    void blankMaskStillMeansAnyBlock() {
        assertTrue(BlockMask.parse("").matches(Material.DIRT));
        assertTrue(BlockMask.parse(null).isAny());
    }

    @Test
    void unresolvedMaskStaysEmptyWhenCombinedAndWhenRestoredLazily() {
        try (MockedStatic<EditUtil> ignored = stubbedBlockNames()) {
            BlockMask combined = BlockMask.and(BlockMask.parse("stoen"), BlockMask.parse("stone"));
            assertFalse(combined.matches(Material.STONE));

            assertFalse(BlockMask.deferred("stoen").matches(Material.STONE));
        }
    }

    @Test
    void blockNamesRejectCharactersThatMatchMaterialWouldSilentlyDrop() {
        assertNull(EditUtil.parseBlock("!air"));
        assertNull(EditUtil.parseBlock("sto.ne"));
        assertNull(EditUtil.parseBlock("stone%"));
        assertNull(EditUtil.parseBlock("stone[facing=east]"));
        assertEquals(Material.AIR, EditUtil.parseBlock("0"));
    }

    @Test
    void nonFiniteWeightsAreNotWeights() {
        try (MockedStatic<EditUtil> ignored = stubbedBlockNames()) {
            assertThrows(IllegalArgumentException.class, () -> BlockDistribution.parse("NaN%stone,dirt"));
            assertThrows(IllegalArgumentException.class, () -> BlockDistribution.parse("Infinity%stone,50%dirt"));
            assertEquals(2, BlockDistribution.parse("50%stone,50%dirt").weights().size());
        }
    }
}
