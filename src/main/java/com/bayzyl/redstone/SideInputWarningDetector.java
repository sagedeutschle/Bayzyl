package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.AuditKind;
import com.bayzyl.redstone.AuditCell.WireLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A dust line that runs past a comparator connects into its side. That side signal makes a compare-mode
 * comparator switch off, or weakens a subtract-mode one. A line that ends at the side on purpose is left alone.
 */
public final class SideInputWarningDetector implements RedstoneAuditDetector {
    public static final String ID = "comparator-side-input";

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            AuditPosition comparator = entry.getKey();
            AuditCell cell = entry.getValue();
            if (cell.kind() != AuditKind.COMPARATOR || !snapshot.inSelection(comparator)) {
                continue;
            }
            for (Side side : Side.HORIZONTAL) {
                if (!side.perpendicularTo(cell.facing())) {
                    continue;
                }
                AuditPosition beside = comparator.relative(side);
                AuditCell dust = snapshot.at(beside);
                if (dust == null || !dust.isDust() || dust.wire(side.opposite()) == WireLink.NONE) {
                    continue;
                }
                boolean runsPast = dust.wire(cell.facing()) != WireLink.NONE
                        || dust.wire(cell.facing().opposite()) != WireLink.NONE;
                if (runsPast) {
                    findings.add(new AuditFinding(comparator, AuditConfidence.MEDIUM, AuditSeverity.DEGRADES, ID,
                            "Dust line feeds a comparator's side",
                            "The line passing at " + beside + " connects into this comparator's side. In compare "
                                    + "mode it turns off whenever that line is stronger; in subtract mode it weakens.",
                            "Route the passing line one block away, or block it with a non-dust block."));
                    break;
                }
            }
        }
        return findings;
    }
}
