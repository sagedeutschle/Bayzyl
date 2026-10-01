package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.AuditKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A repeater or comparator pointing into a repeater's side locks it: while that side is powered the repeater holds
 * its current state. Latches do this on purpose, so it is a warning, not a fault.
 */
public final class RepeaterLockDetector implements RedstoneAuditDetector {
    public static final String ID = "repeater-lock";

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            AuditPosition repeater = entry.getKey();
            AuditCell cell = entry.getValue();
            if (cell.kind() != AuditKind.REPEATER || !snapshot.inSelection(repeater)) {
                continue;
            }
            for (Side side : Side.HORIZONTAL) {
                if (!side.perpendicularTo(cell.facing())) {
                    continue;
                }
                AuditPosition beside = repeater.relative(side);
                AuditCell locker = snapshot.at(beside);
                if (locker != null && locker.isDiode() && beside.relative(locker.outputSide()).equals(repeater)) {
                    findings.add(new AuditFinding(repeater, AuditConfidence.MEDIUM, AuditSeverity.DEGRADES, ID,
                            "Repeater can be locked",
                            "The " + (locker.kind() == AuditKind.COMPARATOR ? "comparator" : "repeater") + " at "
                                    + beside + " points into this repeater's side; while it is on, this repeater "
                                    + "freezes in its current state.",
                            "Move the side component if the lock is accidental; keep it if you are building a latch."));
                    break;
                }
            }
        }
        return findings;
    }
}
