package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.AuditKind;
import com.bayzyl.redstone.AuditCell.WireLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Power aimed into a block that cannot carry it (glass, slabs, leaves, slime, ...) while a component on the other
 * side of that block seems to expect it.
 */
public final class WeakTransmissionDetector implements RedstoneAuditDetector {
    public static final String ID = "non-conducting-block";

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            AuditPosition position = entry.getKey();
            AuditCell cell = entry.getValue();
            if (!snapshot.inSelection(position)) {
                continue;
            }
            if (cell.isDiode() || cell.kind() == AuditKind.OBSERVER) {
                check(snapshot, position, position.relative(cell.outputSide()), findings);
            } else if (cell.isDust()) {
                for (Side side : Side.HORIZONTAL) {
                    if (cell.wire(side) == WireLink.SIDE && check(snapshot, position, position.relative(side), findings)) {
                        break;
                    }
                }
            }
        }
        return findings;
    }

    private static boolean check(RedstoneAuditSnapshot snapshot, AuditPosition from, AuditPosition target,
                                 List<AuditFinding> findings) {
        AuditCell block = snapshot.at(target);
        if (block == null || block.kind() != AuditKind.BLOCK || block.conductor() || block.receiver()
                || block.source() || block.analogSource() || block.readable()) {
            return false;
        }
        for (Side side : Side.values()) {
            AuditPosition beyond = target.relative(side);
            if (beyond.equals(from)) {
                continue;
            }
            AuditCell expecting = snapshot.at(beyond);
            if (expecting == null) {
                continue;
            }
            boolean waiting = expecting.receiver()
                    || (expecting.isDiode() && beyond.relative(expecting.inputSide()).equals(target));
            if (waiting) {
                findings.add(new AuditFinding(from, AuditConfidence.LOW, AuditSeverity.ADVISORY, ID,
                        "Block does not carry power",
                        "The block at " + target + " does not conduct redstone, so the component at " + beyond
                                + " never receives this signal.",
                        "Swap it for a solid block such as stone or planks, or point directly at the component."));
                return true;
            }
        }
        return false;
    }
}
