package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.WireLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java Edition quasi-connectivity: a piston, dispenser, or dropper also activates when the space above it is
 * powered. A machine that only works through that is easy to break with an unrelated block update.
 */
public final class IndirectPowerDetector implements RedstoneAuditDetector {
    public static final String ID = "quasi-connectivity";

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            AuditPosition piston = entry.getKey();
            if (!entry.getValue().pistonLike() || !snapshot.inSelection(piston)) {
                continue;
            }
            Boolean direct = poweredSpace(snapshot, piston, null);
            if (direct == null || direct) {
                continue;
            }
            AuditPosition above = piston.relative(Side.UP);
            Boolean quasi = poweredSpace(snapshot, above, piston);
            if (Boolean.TRUE.equals(quasi)) {
                findings.add(new AuditFinding(piston, AuditConfidence.LOW, AuditSeverity.ADVISORY, ID,
                        "Powered only through quasi-connectivity",
                        "Nothing powers this block directly; it activates because the space above it (" + above
                                + ") is powered. It may not react until a neighbouring block updates.",
                        "Power it directly if this was not intended."));
            }
        }
        return findings;
    }

    /** Does something send power into {@code space} (ignoring {@code except})? Null when a neighbour is unknown. */
    private static Boolean poweredSpace(RedstoneAuditSnapshot snapshot, AuditPosition space, AuditPosition except) {
        boolean unknown = false;
        for (Side side : Side.values()) {
            AuditPosition neighbour = space.relative(side);
            if (neighbour.equals(except)) {
                continue;
            }
            AuditCell cell = snapshot.at(neighbour);
            if (cell == null) {
                unknown = true;
            } else if (cell.source() || cell.analogSource()) {
                return true;
            } else if ((cell.isDiode() || cell.kind() == AuditCell.AuditKind.OBSERVER)
                    && neighbour.relative(cell.outputSide()).equals(space)) {
                return true;
            } else if (cell.isTorch() && !neighbour.relative(cell.attachedSide()).equals(space)) {
                return true;
            } else if (cell.isDust() && (side == Side.UP
                    || (side.horizontal() && cell.wire(side.opposite()) == WireLink.SIDE))) {
                return true;
            } else if (cell.conductor() && except == null) {
                Boolean powered = Topology.conductorPowerable(snapshot, neighbour, space);
                if (powered == null) {
                    unknown = true;
                } else if (powered) {
                    return true;
                }
            }
        }
        return unknown ? null : false;
    }
}
