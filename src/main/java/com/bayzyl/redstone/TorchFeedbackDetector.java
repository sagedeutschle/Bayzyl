package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.WireLink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A redstone torch whose own output reaches the block it is attached to through dust alone: it switches itself
 * off, back on, and burns out. A repeater or comparator in the loop makes a deliberate clock and is left alone.
 */
public final class TorchFeedbackDetector implements RedstoneAuditDetector {
    public static final String ID = "torch-feedback";
    private static final int MAX_STEPS = 15;

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            AuditPosition torch = entry.getKey();
            AuditCell cell = entry.getValue();
            if (!cell.isTorch() || !snapshot.inSelection(torch)) {
                continue;
            }
            AuditPosition attached = torch.relative(cell.attachedSide());
            AuditCell block = snapshot.at(attached);
            if (block == null || !block.conductor()) {
                continue;
            }
            AuditPosition loop = loopBack(snapshot, torch, cell.attachedSide(), attached);
            if (loop != null) {
                findings.add(new AuditFinding(torch, AuditConfidence.MEDIUM, AuditSeverity.BREAKS, ID,
                        "Torch powers its own block",
                        "The dust at " + loop + " carries this torch's output back into the block it hangs on ("
                                + attached + "), so it will flicker and burn out.",
                        "Break the loop, or put a repeater in it if you meant to build a clock."));
            }
        }
        return findings;
    }

    private static AuditPosition loopBack(RedstoneAuditSnapshot snapshot, AuditPosition torch, Side attachedSide,
                                          AuditPosition attached) {
        ArrayDeque<AuditPosition> queue = new ArrayDeque<>();
        Map<AuditPosition, Integer> steps = new java.util.HashMap<>();
        for (Side side : Side.values()) {
            if (side == attachedSide || side == Side.UP) {
                continue;
            }
            AuditPosition next = torch.relative(side);
            AuditCell cell = snapshot.at(next);
            if (cell != null && cell.isDust()) {
                steps.put(next, 0);
                queue.add(next);
            }
        }
        Set<AuditPosition> seen = new HashSet<>(steps.keySet());
        while (!queue.isEmpty()) {
            AuditPosition dust = queue.removeFirst();
            AuditCell cell = snapshot.at(dust);
            if (dust.relative(Side.DOWN).equals(attached)) {
                return dust;
            }
            for (Side side : Side.HORIZONTAL) {
                if (cell.wire(side) == WireLink.NONE) {
                    continue;
                }
                if (cell.wire(side) == WireLink.SIDE && dust.relative(side).equals(attached)) {
                    return dust;
                }
            }
            if (steps.get(dust) >= MAX_STEPS) {
                continue;
            }
            for (Topology.Link link : Topology.links(snapshot, dust, cell)) {
                if (link.kind() == Topology.LinkKind.CONTINUES && seen.add(link.next())) {
                    steps.put(link.next(), steps.get(dust) + 1);
                    queue.addLast(link.next());
                }
            }
        }
        return null;
    }
}
