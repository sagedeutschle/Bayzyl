package com.bayzyl.safety;

import com.bayzyl.BlockMask;
import com.bayzyl.EraserSettings;
import com.bayzyl.PatternBrushMode;
import com.bayzyl.TerrainBrushType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrushSafetyTest {
    @Test
    void eraserUsesCheckedConservativeCubeBoundary() {
        WorkEstimate radius49 = BrushSafety.assessEraser(49);
        WorkEstimate radius50 = BrushSafety.assessEraser(50);

        assertEquals(970_299L, radius49.workUnits());
        assertFalse(radius49.hardRejected());
        assertEquals(1_030_301L, radius50.workUnits());
        assertTrue(radius50.hardRejected());
        assertTrue(BrushSafety.assessEraser(0).hardRejected());
        assertTrue(BrushSafety.assessEraser(Integer.MAX_VALUE).hardRejected());
    }

    @Test
    void smoothIncludesPaddedBuffersAndIterationWorkAtExactBoundary() {
        WorkEstimate radius8 = BrushSafety.assessTerrain(TerrainBrushType.SMOOTH, 8, 1);
        WorkEstimate radius9 = BrushSafety.assessTerrain(TerrainBrushType.SMOOTH, 9, 1);

        assertEquals(770_211L, radius8.workUnits());
        assertFalse(radius8.hardRejected());
        assertEquals(1_073_761L, radius9.workUnits());
        assertTrue(radius9.hardRejected());
        assertTrue(BrushSafety.assessTerrain(TerrainBrushType.SMOOTH, 1, Integer.MAX_VALUE).hardRejected());
    }

    @Test
    void terrainFamiliesUseTheirSpecifiedCheckedEnvelopes() {
        WorkEstimate reshape = BrushSafety.assessTerrain(TerrainBrushType.RAISE, 2, 3);
        long c = 5L;
        long grid = c * c;
        long height = 2L * (2L + 4L * 3L + 16L) + 1L;
        assertEquals(3L * grid * height + grid + 96L, reshape.workUnits());
        assertFalse(reshape.hardRejected());

        WorkEstimate naturalize = BrushSafety.assessTerrain(TerrainBrushType.NATURALIZE, 2, 3);
        assertEquals(grid * (3L * (2L * 2L + 33L) + 3L + 29L), naturalize.workUnits());
        assertFalse(naturalize.hardRejected());

        WorkEstimate cleanup = BrushSafety.assessTerrain(TerrainBrushType.CLEANUP_FLOATING, 2, 999);
        assertEquals(10L * c * c * c, cleanup.workUnits());
        assertFalse(cleanup.hardRejected());

        assertTrue(BrushSafety.assessTerrain(TerrainBrushType.RAISE, 1, 0).hardRejected());
        assertTrue(BrushSafety.assessTerrain(TerrainBrushType.FLATTEN, Integer.MAX_VALUE, 1).hardRejected());
        assertTrue(BrushSafety.assessTerrain(TerrainBrushType.NATURALIZE, 1, Integer.MAX_VALUE).hardRejected());
        assertTrue(BrushSafety.assessTerrain(TerrainBrushType.CLEANUP_SNOW, Integer.MAX_VALUE, 1).hardRejected());
    }

    @Test
    void paintAndPatternRespectSizeAndModeSpecificWork() {
        WorkEstimate paint64 = BrushSafety.assessPaint(64);
        assertEquals(16_641L, paint64.workUnits());
        assertFalse(paint64.hardRejected());
        assertTrue(BrushSafety.assessPaint(65).hardRejected());

        WorkEstimate surface64 = BrushSafety.assessPattern(PatternBrushMode.SURFACE, 64, 0L);
        WorkEstimate vegetation64 = BrushSafety.assessPattern(PatternBrushMode.VEGETATION, 64, 0L);
        WorkEstimate volume64 = BrushSafety.assessPattern(PatternBrushMode.SPATTER, 64, 0L);
        assertEquals(16_641L, surface64.workUnits());
        assertFalse(surface64.hardRejected());
        assertFalse(vegetation64.hardRejected());
        assertEquals(2_146_689L, volume64.workUnits());
        assertTrue(volume64.hardRejected());
    }

    @Test
    void restoreUsesCheckedUndoActionCardinalityBoundary() {
        WorkEstimate exact = BrushSafety.assessPattern(PatternBrushMode.RESTORE, 64, 1_000_000L);
        WorkEstimate over = BrushSafety.assessPattern(PatternBrushMode.RESTORE, 64, 1_000_001L);

        assertEquals(1_000_000L, exact.workUnits());
        assertFalse(exact.hardRejected());
        assertEquals(1_000_001L, over.workUnits());
        assertTrue(over.hardRejected());
        assertTrue(BrushSafety.assessPattern(PatternBrushMode.RESTORE, 64, -1L).hardRejected());
    }

    @Test
    void masksAreBoundedAndEveryTokenMustResolve() {
        assertTrue(BrushSafety.isValidMaskRaw("air"));
        assertTrue(BrushSafety.isValidMaskRaw("air,stone"));
        assertFalse(BrushSafety.isValidMaskRaw("air,not_a_block"));
        assertFalse(BrushSafety.isValidMaskRaw("air,"));
        assertFalse(BrushSafety.isValidMaskRaw("x".repeat(BrushSafety.MASK_TEXT_MAX + 1)));

        EraserSettings overCapWithOversizedMask = new EraserSettings(
                50, new BlockMask("x".repeat(100_000), List.of(), List.of()),
                false, false, false, false);
        assertFalse(BrushSafety.isValidEraser(overCapWithOversizedMask));
    }
}
