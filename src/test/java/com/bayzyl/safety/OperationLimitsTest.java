package com.bayzyl.safety;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OperationLimitsTest {
    @Test
    void softConfirmationBoundaryIsExact() {
        WorkEstimate allowed = OperationLimits.checkMaterialized(200_000L);
        WorkEstimate confirmation = OperationLimits.checkMaterialized(200_001L);

        assertTrue(allowed.permits(false));
        assertFalse(allowed.confirmationRequired());
        assertTrue(confirmation.confirmationRequired());
        assertFalse(confirmation.permits(false));
        assertTrue(confirmation.permits(true));
    }

    @Test
    void materializedHardMaximumCannotBeBypassed() {
        assertTrue(OperationLimits.checkMaterialized(1_000_000L).permits(true));
        WorkEstimate rejected = OperationLimits.checkMaterialized(1_000_001L);
        assertTrue(rejected.hardRejected());
        assertFalse(rejected.permits(true));
    }

    @Test
    void specializedCapsAreExact() {
        assertFalse(OperationLimits.checkBiomeChunks(4_096L).hardRejected());
        assertTrue(OperationLimits.checkBiomeChunks(4_097L).hardRejected());
        assertFalse(OperationLimits.checkRecoveryClipboard(250_000L).hardRejected());
        assertTrue(OperationLimits.checkRecoveryClipboard(250_001L).hardRejected());
        assertFalse(OperationLimits.checkForestRadius(32).hardRejected());
        assertTrue(OperationLimits.checkForestRadius(33).hardRejected());
        assertFalse(OperationLimits.checkPumpkinRadius(64).hardRejected());
        assertTrue(OperationLimits.checkPumpkinRadius(65).hardRejected());
        assertFalse(OperationLimits.checkGenBrush(750_000L).hardRejected());
        assertTrue(OperationLimits.checkGenBrush(750_001L).hardRejected());
    }

    @Test
    void inclusiveFullIntegerSpanIsRepresentedExactly() {
        assertEquals(4_294_967_296L, OperationLimits.inclusiveSpan(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void exposedCheckedArithmeticRejectsOverflow() {
        assertOverflowRejection(OperationLimits.checkedAdd(Long.MAX_VALUE, 1L));
        assertOverflowRejection(OperationLimits.checkedMultiply(Long.MAX_VALUE, 2L));
        assertEquals(42L, OperationLimits.checkedAdd(40L, 2L).workUnits());
        assertEquals(42L, OperationLimits.checkedMultiply(6L, 7L).workUnits());
    }

    @Test
    void cubingFullIntegerSpanRejectsWithoutWrapping() {
        WorkEstimate estimate = OperationLimits.estimateSelection(
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE);

        assertOverflowRejection(estimate);
    }

    @Test
    void conservativeShapeFormulasUseCheckedBoundingDimensions() {
        assertEquals(125L, OperationLimits.estimateSphere(2, 2, 2).workUnits());
        assertEquals(75L, OperationLimits.estimateCylinder(2, 1, 5).workUnits());
        assertEquals(75L, OperationLimits.estimatePyramid(2).workUnits());
        assertTrue(OperationLimits.estimateSphere(Integer.MAX_VALUE, 1, 1).hardRejected());
        assertTrue(OperationLimits.estimateCylinder(Integer.MAX_VALUE, 1, Integer.MAX_VALUE).hardRejected());
        assertTrue(OperationLimits.estimatePyramid(Integer.MAX_VALUE).hardRejected());
    }

    @Test
    void structureUsesActualSnapshotFootprintAndSeparateChunks() {
        assertEquals(1_225L, OperationLimits.estimateStructure(3, 23).workUnits());
        assertEquals(9L, OperationLimits.estimateStructureChunks(3).workUnits());
        assertTrue(OperationLimits.estimateStructure(Integer.MAX_VALUE, Integer.MAX_VALUE).hardRejected());
        assertTrue(OperationLimits.estimateStructureChunks(Integer.MAX_VALUE).hardRejected());
    }

    @Test
    void biomeChunksUseCheckedInclusiveChunkSpans() {
        assertEquals(6L, OperationLimits.estimateBiomeChunks(-16, 15, -1, 16).workUnits());
        assertTrue(OperationLimits.estimateBiomeChunks(
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE).hardRejected());
        assertTrue(OperationLimits.estimateBiomeChunks(1, 0, 0, 1).hardRejected());
    }

    @Test
    void forestAndPumpkinRejectRadiusBeforeFurtherArithmetic() {
        assertEquals(4_225L, OperationLimits.estimateForest(32, 0.0).workUnits());
        assertEquals(422_500L, OperationLimits.estimateForest(32, 100.0).workUnits());
        assertTrue(OperationLimits.estimateForest(33, 100.0).hardRejected());
        assertTrue(OperationLimits.estimatePumpkins(65).hardRejected());
        assertEquals(OperationLimits.checkForestRadius(33).reason(), OperationLimits.estimateForest(33, 100.0).reason());
        assertEquals(OperationLimits.checkPumpkinRadius(65).reason(), OperationLimits.estimatePumpkins(65).reason());
    }

    @Test
    void genBrushCombinesCheckedRadiusVolumeAndMultiplier() {
        assertEquals(250L, OperationLimits.estimateGenBrush(2, 2L).workUnits());
        assertTrue(OperationLimits.estimateGenBrush(Integer.MAX_VALUE, Long.MAX_VALUE).hardRejected());
    }

    @Test
    void invalidDimensionsAndDensitiesReject() {
        assertTrue(OperationLimits.estimateSphere(0, 1, 1).hardRejected());
        assertTrue(OperationLimits.estimateCylinder(1, 1, 0).hardRejected());
        assertTrue(OperationLimits.estimatePyramid(-1).hardRejected());
        assertTrue(OperationLimits.estimateStructure(0, 1).hardRejected());
        assertFalse(OperationLimits.estimateForest(1, 0.0).hardRejected());
        assertTrue(OperationLimits.estimateForest(1, -0.01).hardRejected());
        assertTrue(OperationLimits.estimateForest(1, Double.NaN).hardRejected());
        assertTrue(OperationLimits.estimateForest(1, Double.POSITIVE_INFINITY).hardRejected());
        assertTrue(OperationLimits.estimateGenBrush(1, 0L).hardRejected());
    }

    @Test
    void everyRejectionIsNonNegativeAndHasStableReason() {
        WorkEstimate[] rejected = {
                OperationLimits.checkMaterialized(-1L),
                OperationLimits.checkBiomeChunks(4_097L),
                OperationLimits.checkRecoveryClipboard(250_001L),
                OperationLimits.estimateSphere(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                OperationLimits.estimateForest(1, Double.NaN)
        };
        for (WorkEstimate estimate : rejected) {
            assertTrue(estimate.hardRejected());
            assertTrue(estimate.workUnits() >= 0L);
            assertFalse(estimate.reason().isBlank());
        }
        assertEquals(OperationLimits.checkBiomeChunks(4_097L).reason(), OperationLimits.checkBiomeChunks(4_097L).reason());
    }

    private static void assertOverflowRejection(WorkEstimate estimate) {
        assertEquals(Long.MAX_VALUE, estimate.workUnits());
        assertTrue(estimate.hardRejected());
        assertFalse(estimate.permits(true));
        assertFalse(estimate.reason().isBlank());
    }
}
