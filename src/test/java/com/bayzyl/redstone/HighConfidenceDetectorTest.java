package com.bayzyl.redstone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bayzyl.redstone.Layout.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures sit on a floor at y=0 with components at y=1, z=1. Every rule has a broken layout and the intentional
 * layouts builders actually use, which must stay silent.
 */
class HighConfidenceDetectorTest {
    private static final int Y = 1;
    private static final int Z = 1;

    // ---- dust dead end ---------------------------------------------------------------------------------------

    @Test
    void dustLineEndingInAirIsAHighConfidenceBreak() {
        RedstoneAuditSnapshot snapshot = new Layout(20, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .build();

        AuditFinding finding = single(new DustDeadEndDetector(), snapshot);
        assertEquals(at(3, Y, Z), finding.position());
        assertEquals(AuditConfidence.HIGH, finding.confidence());
        assertEquals(AuditSeverity.BREAKS, finding.severity());
    }

    @Test
    void dustLineEndingBesideAPistonItDoesNotPointIntoIsAHighConfidenceBreak() {
        RedstoneAuditSnapshot snapshot = new Layout(20, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .put(3, Y, Z - 1, AuditCell.pistonBlock())
                .put(4, Y, Z, AuditCell.receiverBlock(true))
                .put(4, Y, Z, AuditCell.EMPTY)
                .build();

        AuditFinding finding = single(new DustDeadEndDetector(), snapshot);
        assertEquals(at(3, Y, Z), finding.position());
        assertEquals(AuditConfidence.HIGH, finding.confidence());
        assertTrue(finding.pattern().contains("x:3 y:1 z:0"), finding.pattern());
    }

    @Test
    void dustEndingInAReceiverIsFine() {
        assertSilent(new DustDeadEndDetector(), new Layout(20, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .put(4, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void dustEndingInABlockThatPowersAPistonIsFine() {
        assertSilent(new DustDeadEndDetector(), new Layout(20, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .put(4, Y, Z, AuditCell.conductorBlock())
                .put(5, Y, Z, AuditCell.pistonBlock())
                .build());
    }

    @Test
    void dustOnTopOfABlockThatPowersALampIsFine() {
        assertSilent(new DustDeadEndDetector(), new Layout(20, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .put(3, 0, Z + 1, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void dustSteppingDownIsAContinuationNotAnEnd() {
        assertSilent(new DustDeadEndDetector(), new Layout(20, 4, 3).floor(1, Z, 0, 3)
                .put(0, 2, Z, AuditCell.sourceBlock())
                .dustLine(2, Z, 1, 3)
                .floor(0, Z, 4, 6)
                .put(4, 1, Z, AuditCell.dust(java.util.Map.of(Side.WEST, AuditCell.WireLink.UP, Side.EAST, AuditCell.WireLink.SIDE)))
                .put(5, 1, Z, Layout.eastWestDust())
                .put(6, 1, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void dustFeedingARepeaterInputIsFine() {
        assertSilent(new DustDeadEndDetector(), new Layout(20, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .put(4, Y, Z, AuditCell.repeater(Side.WEST))
                .put(5, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void receiverJustOutsideTheSelectionEdgeIsSeenThroughTheHalo() {
        assertSilent(new DustDeadEndDetector(), new Layout(3, 3, 3).floor(0, Z, 0, 3)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 3)
                .put(4, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void anUnusedStubOnALineThatWorksIsOnlyLowConfidence() {
        RedstoneAuditSnapshot snapshot = new Layout(20, 3, 4).floor(0, Z, 0, 6).floor(0, Z + 1, 2, 2)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .put(1, Y, Z, Layout.eastWestDust())
                .put(2, Y, Z, Layout.dust(Side.EAST, Side.WEST, Side.SOUTH))
                .put(3, Y, Z, Layout.eastWestDust())
                .put(4, Y, Z, AuditCell.receiverBlock(true))
                .put(2, Y, Z + 1, Layout.dust(Side.NORTH, Side.SOUTH))
                .build();

        AuditFinding finding = single(new DustDeadEndDetector(), snapshot);
        assertEquals(at(2, Y, Z + 1), finding.position());
        assertEquals(AuditConfidence.LOW, finding.confidence());
    }

    // ---- dust range ------------------------------------------------------------------------------------------

    @Test
    void sixteenthDustFromTheSourceIsWhereTheSignalDies() {
        RedstoneAuditSnapshot snapshot = new Layout(30, 3, 3).floor(0, Z, 0, 18)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 17)
                .put(18, Y, Z, AuditCell.receiverBlock(true))
                .build();

        AuditFinding finding = single(new DustRangeDetector(), snapshot);
        assertEquals(at(16, Y, Z), finding.position());
        assertEquals(AuditConfidence.HIGH, finding.confidence());
        assertEquals(AuditSeverity.BREAKS, finding.severity());
    }

    @Test
    void fifteenDustReachTheirReceiver() {
        assertSilent(new DustRangeDetector(), new Layout(30, 3, 3).floor(0, Z, 0, 16)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 15)
                .put(16, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void longLineFedFromTheMiddleIsFine() {
        assertSilent(new DustRangeDetector(), new Layout(31, 3, 3).floor(0, Z, 0, 30)
                .dustLine(Y, Z, 1, 29)
                .put(15, Y, Z + 1, AuditCell.sourceBlock())
                .build());
    }

    @Test
    void repeaterResetsTheSignal() {
        assertSilent(new DustRangeDetector(), new Layout(31, 3, 3).floor(0, Z, 0, 24)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 10)
                .put(11, Y, Z, AuditCell.repeater(Side.WEST))
                .dustLine(Y, Z, 12, 23)
                .put(24, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void lineFedOnlyByAComparatorIsMediumConfidence() {
        RedstoneAuditSnapshot snapshot = new Layout(30, 3, 3).floor(0, Z, 0, 18)
                .put(0, Y, Z, AuditCell.readableBlock(false))
                .put(1, Y, Z, AuditCell.comparator(Side.WEST))
                .dustLine(Y, Z, 2, 18)
                .build();

        AuditFinding finding = single(new DustRangeDetector(), snapshot);
        assertEquals(at(17, Y, Z), finding.position());
        assertEquals(AuditConfidence.MEDIUM, finding.confidence());
    }

    @Test
    void unfedDustIsNotARangeProblem() {
        assertSilent(new DustRangeDetector(), new Layout(30, 3, 3).floor(0, Z, 0, 20).dustLine(Y, Z, 1, 20).build());
    }

    // ---- repeaters, comparators, observers ---------------------------------------------------------------------

    @Test
    void repeaterWithNothingBehindItHasNoInput() {
        RedstoneAuditSnapshot snapshot = new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(3, Y, Z, AuditCell.receiverBlock(true))
                .build();

        AuditFinding finding = single(new DirectionalComponentDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertEquals(AuditConfidence.HIGH, finding.confidence());
    }

    @Test
    void repeaterPlacedBackwardsIsReportedAtTheRepeater() {
        RedstoneAuditSnapshot snapshot = new Layout(10, 3, 3).floor(0, Z, 0, 6)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .dustLine(Y, Z, 1, 2)
                .put(3, Y, Z, AuditCell.repeater(Side.EAST))
                .put(4, Y, Z, AuditCell.EMPTY)
                .build();

        List<AuditFinding> findings = new DirectionalComponentDetector().detect(snapshot);
        assertTrue(findings.stream().allMatch(finding -> finding.position().equals(at(3, Y, Z))), findings.toString());
        assertTrue(findings.stream().anyMatch(finding -> finding.pattern().contains("backwards")), findings.toString());
    }

    @Test
    void repeaterOutputIntoAirDrivesNothing() {
        RedstoneAuditSnapshot snapshot = new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(0, Y, Z, AuditCell.sourceBlock())
                .put(1, Y, Z, Layout.eastWestDust())
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .build();

        AuditFinding finding = single(new DirectionalComponentDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertTrue(finding.title().toLowerCase().contains("output"), finding.title());
    }

    @Test
    void repeaterFedThroughAPoweredBlockIntoATorchInverterIsFine() {
        assertSilent(new DirectionalComponentDetector(), new Layout(10, 3, 3).floor(0, Z, 0, 6)
                .put(1, Y, Z, AuditCell.conductorBlock())
                .put(1, Y + 1, Z, AuditCell.sourceBlock())
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(3, Y, Z, AuditCell.conductorBlock())
                .put(4, Y, Z, AuditCell.wallTorch(Side.EAST))
                .build());
    }

    @Test
    void comparatorReadingAChestDirectlyIsFine() {
        assertSilent(new DirectionalComponentDetector(), new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(1, Y, Z, AuditCell.readableBlock(false))
                .put(2, Y, Z, AuditCell.comparator(Side.WEST))
                .dustLine(Y, Z, 3, 4)
                .build());
    }

    @Test
    void comparatorReadingAChestThroughABlockIsFine() {
        assertSilent(new DirectionalComponentDetector(), new Layout(10, 3, 3).floor(0, Z, 0, 6)
                .put(1, Y, Z, AuditCell.readableBlock(false))
                .put(2, Y, Z, AuditCell.conductorBlock())
                .put(3, Y, Z, AuditCell.comparator(Side.WEST))
                .put(4, Y, Z, AuditCell.pistonBlock())
                .build());
    }

    @Test
    void comparatorReadingAnItemFrameIsFine() {
        assertSilent(new DirectionalComponentDetector(), new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(1, Y, Z, AuditCell.readableBlock(false))
                .put(2, Y, Z, AuditCell.comparator(Side.WEST))
                .put(3, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    @Test
    void comparatorWithNothingBehindItReadsNothing() {
        RedstoneAuditSnapshot snapshot = new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(2, Y, Z, AuditCell.comparator(Side.WEST))
                .dustLine(Y, Z, 3, 4)
                .build();

        AuditFinding finding = single(new DirectionalComponentDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertEquals(AuditConfidence.HIGH, finding.confidence());
    }

    @Test
    void observerOutputIntoAirDrivesNothing() {
        RedstoneAuditSnapshot snapshot = new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(2, Y, Z, AuditCell.observer(Side.WEST))
                .build();

        AuditFinding finding = single(new DirectionalComponentDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertEquals(AuditConfidence.HIGH, finding.confidence());
    }

    @Test
    void observerIntoAPistonIsFine() {
        assertSilent(new DirectionalComponentDetector(), new Layout(10, 3, 3).floor(0, Z, 0, 5)
                .put(2, Y, Z, AuditCell.observer(Side.WEST))
                .put(3, Y, Z, AuditCell.pistonBlock())
                .build());
    }

    @Test
    void edgeComponentFedFromTheHaloIsFine() {
        assertSilent(new DirectionalComponentDetector(), new Layout(4, 3, 3)
                .put(-2, Y, Z, AuditCell.sourceBlock())
                .put(-1, Y, Z, AuditCell.conductorBlock())
                .put(0, Y, Z, AuditCell.repeater(Side.WEST))
                .put(1, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    private static AuditFinding single(RedstoneAuditDetector detector, RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new RedstoneAuditService(List.of(detector)).audit(snapshot);
        assertEquals(1, findings.size(), findings.toString());
        return findings.get(0);
    }

    private static void assertSilent(RedstoneAuditDetector detector, RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new RedstoneAuditService(List.of(detector)).audit(snapshot);
        assertEquals(List.of(), findings);
    }
}
