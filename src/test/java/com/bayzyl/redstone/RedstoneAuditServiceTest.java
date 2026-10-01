package com.bayzyl.redstone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedstoneAuditServiceTest {
    private static final AuditPosition MIN = new AuditPosition(0, 0, 0);
    private static final AuditPosition MAX = new AuditPosition(9, 3, 9);

    @Test
    void findingsSortByConfidenceThenSeverityThenCoordinates() {
        AuditFinding lowA = finding(1, 0, 0, AuditConfidence.LOW, AuditSeverity.BREAKS);
        AuditFinding highAdvisory = finding(0, 0, 0, AuditConfidence.HIGH, AuditSeverity.ADVISORY);
        AuditFinding highBreaksFar = finding(5, 1, 0, AuditConfidence.HIGH, AuditSeverity.BREAKS);
        AuditFinding highBreaksNear = finding(2, 1, 0, AuditConfidence.HIGH, AuditSeverity.BREAKS);
        AuditFinding medium = new AuditFinding(new AuditPosition(0, 0, 0), AuditConfidence.MEDIUM,
                AuditSeverity.BREAKS, "other", "title", "pattern", "fix");
        RedstoneAuditService service = new RedstoneAuditService(List.of(
                snapshot -> List.of(lowA, highAdvisory),
                snapshot -> List.of(highBreaksFar, medium, highBreaksNear)));

        List<AuditFinding> findings = service.audit(RedstoneAuditSnapshot.builder(MIN, MAX).build());

        assertEquals(List.of(highBreaksNear, highBreaksFar, highAdvisory, medium, lowA), findings);
    }

    @Test
    void findingsOutsideTheSelectionAreNeverReported() {
        AuditFinding inside = finding(9, 3, 9, AuditConfidence.HIGH, AuditSeverity.BREAKS);
        AuditFinding halo = finding(10, 3, 9, AuditConfidence.HIGH, AuditSeverity.BREAKS);
        RedstoneAuditService service = new RedstoneAuditService(List.of(snapshot -> List.of(halo, inside)));

        assertEquals(List.of(inside), service.audit(RedstoneAuditSnapshot.builder(MIN, MAX).build()));
    }

    @Test
    void sameDetectorAtTheSamePositionIsReportedOnce() {
        AuditFinding first = finding(1, 1, 1, AuditConfidence.HIGH, AuditSeverity.BREAKS);
        AuditFinding duplicate = finding(1, 1, 1, AuditConfidence.HIGH, AuditSeverity.BREAKS);
        RedstoneAuditService service = new RedstoneAuditService(List.of(snapshot -> List.of(first, duplicate)));

        assertEquals(1, service.audit(RedstoneAuditSnapshot.builder(MIN, MAX).build()).size());
    }

    @Test
    void standardServiceRunsTheShippedCatalogue() {
        RedstoneAuditSnapshot snapshot = new Layout(10, 3, 3).floor(0, 1, 0, 5)
                .put(0, 1, 1, AuditCell.sourceBlock())
                .dustLine(1, 1, 1, 3)
                .put(6, 1, 1, AuditCell.repeater(Side.WEST))
                .put(7, 1, 1, AuditCell.receiverBlock(true))
                .build();
        List<String> detectors = RedstoneAuditService.standard().audit(snapshot).stream()
                .map(AuditFinding::detector).toList();
        assertTrue(detectors.contains(DustDeadEndDetector.ID), detectors.toString());
        assertTrue(detectors.contains("repeater-input"), detectors.toString());
    }

    @Test
    void snapshotKnowsTheSelectionTheHaloAndNothingBeyond() {
        AuditPosition haloEdge = new AuditPosition(-2, 0, 0);
        AuditCell lever = AuditCell.sourceBlock();
        RedstoneAuditSnapshot snapshot = RedstoneAuditSnapshot.builder(MIN, MAX).put(haloEdge, lever).build();

        assertSame(lever, snapshot.at(haloEdge));
        assertFalse(snapshot.inSelection(haloEdge));
        assertSame(AuditCell.EMPTY, snapshot.at(new AuditPosition(5, 1, 5)));
        assertTrue(snapshot.inSelection(new AuditPosition(5, 1, 5)));
        assertNull(snapshot.at(new AuditPosition(-3, 0, 0)), "beyond the halo is unknown, not empty");
        assertThrows(IllegalArgumentException.class,
                () -> RedstoneAuditSnapshot.builder(MIN, MAX).put(new AuditPosition(12, 0, 0), lever));
    }

    private static AuditFinding finding(int x, int y, int z, AuditConfidence confidence, AuditSeverity severity) {
        return new AuditFinding(new AuditPosition(x, y, z), confidence, severity, "test", "title", "pattern", "fix");
    }
}
