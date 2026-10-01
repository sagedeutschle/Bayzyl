package com.bayzyl.redstone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bayzyl.redstone.Layout.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Medium/low-confidence warnings: each has a firing layout and the intentional layout it must leave alone. */
class WarningDetectorTest {
    private static final int Y = 1;
    private static final int Z = 2;

    // ---- torch feedback ----------------------------------------------------------------------------------------

    @Test
    void torchThatPowersItsOwnBlockThroughDustBurnsOut() {
        // Wall torch on block (2,1,2) facing east at (3,1,2); dust from the torch loops north and back west onto the block.
        RedstoneAuditSnapshot snapshot = new Layout(8, 3, 5).floor(0, Z, 0, 5).floor(0, Z - 1, 0, 5)
                .put(2, Y, Z, AuditCell.conductorBlock())
                .put(3, Y, Z, AuditCell.wallTorch(Side.EAST))
                .put(4, Y, Z, Layout.dust(Side.WEST, Side.NORTH))
                .put(4, Y, Z - 1, Layout.dust(Side.SOUTH, Side.WEST))
                .put(3, Y, Z - 1, Layout.dust(Side.EAST, Side.WEST))
                .put(2, Y, Z - 1, Layout.dust(Side.EAST, Side.SOUTH))
                .build();

        AuditFinding finding = single(new TorchFeedbackDetector(), snapshot);
        assertEquals(at(3, Y, Z), finding.position());
        assertEquals(AuditConfidence.MEDIUM, finding.confidence());
        assertEquals(AuditSeverity.BREAKS, finding.severity());
    }

    @Test
    void torchClockWithARepeaterInTheLoopIsIntentional() {
        RedstoneAuditSnapshot snapshot = new Layout(8, 3, 5).floor(0, Z, 0, 5).floor(0, Z - 1, 0, 5)
                .put(2, Y, Z, AuditCell.conductorBlock())
                .put(3, Y, Z, AuditCell.wallTorch(Side.EAST))
                .put(4, Y, Z, Layout.dust(Side.WEST, Side.NORTH))
                .put(4, Y, Z - 1, Layout.dust(Side.SOUTH, Side.WEST))
                .put(3, Y, Z - 1, AuditCell.repeater(Side.EAST))
                .put(2, Y, Z - 1, Layout.dust(Side.EAST, Side.SOUTH))
                .build();

        assertSilent(new TorchFeedbackDetector(), snapshot);
    }

    @Test
    void ordinaryInverterIsFine() {
        assertSilent(new TorchFeedbackDetector(), new Layout(8, 3, 5).floor(0, Z, 0, 6)
                .put(2, Y, Z, AuditCell.conductorBlock())
                .put(3, Y, Z, AuditCell.wallTorch(Side.EAST))
                .dustLine(Y, Z, 4, 5)
                .put(6, Y, Z, AuditCell.receiverBlock(true))
                .build());
    }

    // ---- comparator side input ----------------------------------------------------------------------------------

    @Test
    void dustLineRunningPastAComparatorFeedsItsSide() {
        RedstoneAuditSnapshot snapshot = new Layout(8, 3, 5).floor(0, Z, 0, 6).floor(0, Z - 1, 0, 6)
                .put(1, Y, Z, AuditCell.readableBlock(false))
                .put(2, Y, Z, AuditCell.comparator(Side.WEST))
                .put(3, Y, Z, AuditCell.receiverBlock(true))
                .put(1, Y, Z - 1, Layout.eastWestDust())
                .put(2, Y, Z - 1, Layout.dust(Side.EAST, Side.WEST, Side.SOUTH))
                .put(3, Y, Z - 1, Layout.eastWestDust())
                .build();

        AuditFinding finding = single(new SideInputWarningDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertEquals(AuditConfidence.MEDIUM, finding.confidence());
    }

    @Test
    void dedicatedSideFeedIntoASubtractorIsIntentional() {
        assertSilent(new SideInputWarningDetector(), new Layout(8, 3, 5).floor(0, Z, 0, 6).floor(0, Z - 1, 0, 6)
                .put(1, Y, Z, AuditCell.readableBlock(false))
                .put(2, Y, Z, AuditCell.comparator(Side.WEST))
                .put(3, Y, Z, AuditCell.receiverBlock(true))
                .put(2, Y, Z - 1, Layout.dust(Side.NORTH, Side.SOUTH))
                .put(2, Y, Z - 2, Layout.dust(Side.NORTH, Side.SOUTH))
                .build());
    }

    // ---- repeater lock ------------------------------------------------------------------------------------------

    @Test
    void repeaterPointingIntoAnotherRepeatersSideCanLockIt() {
        RedstoneAuditSnapshot snapshot = new Layout(8, 3, 5).floor(0, Z, 0, 6).floor(0, Z - 1, 0, 6)
                .put(1, Y, Z, Layout.eastWestDust())
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(3, Y, Z, AuditCell.receiverBlock(true))
                .put(2, Y, Z - 1, AuditCell.repeater(Side.NORTH))
                .build();

        AuditFinding finding = single(new RepeaterLockDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertEquals(AuditConfidence.MEDIUM, finding.confidence());
        assertEquals(AuditSeverity.DEGRADES, finding.severity());
    }

    @Test
    void sideRepeaterFacingAwayDoesNotLock() {
        assertSilent(new RepeaterLockDetector(), new Layout(8, 3, 5).floor(0, Z, 0, 6).floor(0, Z - 1, 0, 6)
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(2, Y, Z - 1, AuditCell.repeater(Side.SOUTH))
                .build());
    }

    @Test
    void dustBesideARepeaterDoesNotLockIt() {
        assertSilent(new RepeaterLockDetector(), new Layout(8, 3, 5).floor(0, Z, 0, 6).floor(0, Z - 1, 0, 6)
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(2, Y, Z - 1, Layout.dust(Side.NORTH, Side.SOUTH))
                .build());
    }

    // ---- weak / non-conducting transmission ---------------------------------------------------------------------

    @Test
    void repeaterIntoGlassDoesNotReachThePistonBehindIt() {
        RedstoneAuditSnapshot snapshot = new Layout(8, 3, 5).floor(0, Z, 0, 6)
                .put(1, Y, Z, Layout.eastWestDust())
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(3, Y, Z, AuditCell.nonConductorBlock())
                .put(4, Y, Z, AuditCell.pistonBlock())
                .build();

        AuditFinding finding = single(new WeakTransmissionDetector(), snapshot);
        assertEquals(at(2, Y, Z), finding.position());
        assertEquals(AuditConfidence.LOW, finding.confidence());
        assertTrue(finding.pattern().contains("x:4 y:1 z:2"), finding.pattern());
    }

    @Test
    void repeaterIntoStoneThatPowersThePistonIsFine() {
        assertSilent(new WeakTransmissionDetector(), new Layout(8, 3, 5).floor(0, Z, 0, 6)
                .put(1, Y, Z, Layout.eastWestDust())
                .put(2, Y, Z, AuditCell.repeater(Side.WEST))
                .put(3, Y, Z, AuditCell.conductorBlock())
                .put(4, Y, Z, AuditCell.pistonBlock())
                .build());
    }

    // ---- quasi-connectivity -------------------------------------------------------------------------------------

    @Test
    void pistonPoweredOnlyThroughTheSpaceAboveItIsQuasiConnected() {
        RedstoneAuditSnapshot snapshot = new Layout(8, 4, 5).floor(0, Z, 0, 6)
                .put(3, Y, Z, AuditCell.pistonBlock())
                .put(4, Y + 1, Z, AuditCell.sourceBlock())
                .build();

        AuditFinding finding = single(new IndirectPowerDetector(), snapshot);
        assertEquals(at(3, Y, Z), finding.position());
        assertEquals(AuditConfidence.LOW, finding.confidence());
        assertEquals(AuditSeverity.ADVISORY, finding.severity());
    }

    @Test
    void directlyPoweredPistonIsNotQuasiConnected() {
        assertSilent(new IndirectPowerDetector(), new Layout(8, 4, 5).floor(0, Z, 0, 6)
                .put(3, Y, Z, AuditCell.pistonBlock())
                .put(4, Y, Z, AuditCell.sourceBlock())
                .put(4, Y + 1, Z, AuditCell.sourceBlock())
                .build());
    }

    @Test
    void lampsHaveNoQuasiConnectivity() {
        assertSilent(new IndirectPowerDetector(), new Layout(8, 4, 5).floor(0, Z, 0, 6)
                .put(3, Y, Z, AuditCell.receiverBlock(true))
                .put(4, Y + 1, Z, AuditCell.sourceBlock())
                .build());
    }

    @Test
    void standardCatalogueIncludesTheWarnings() {
        List<String> detectors = RedstoneAuditService.standard().audit(new Layout(8, 4, 5).floor(0, Z, 0, 6)
                        .put(3, Y, Z, AuditCell.pistonBlock())
                        .put(4, Y + 1, Z, AuditCell.sourceBlock())
                        .build())
                .stream().map(AuditFinding::detector).toList();
        assertTrue(detectors.contains(IndirectPowerDetector.ID), detectors.toString());
    }

    private static AuditFinding single(RedstoneAuditDetector detector, RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new RedstoneAuditService(List.of(detector)).audit(snapshot);
        assertEquals(1, findings.size(), findings.toString());
        return findings.get(0);
    }

    private static void assertSilent(RedstoneAuditDetector detector, RedstoneAuditSnapshot snapshot) {
        assertEquals(List.of(), new RedstoneAuditService(List.of(detector)).audit(snapshot));
    }
}
