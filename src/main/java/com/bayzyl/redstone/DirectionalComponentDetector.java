package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.AuditKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repeaters, comparators, and observers that are structurally disconnected: nothing behind a repeater or
 * comparator can power (or, for a comparator, be measured by) it, or nothing in front of any of the three uses its
 * output. Comparators reading containers, directly or through one block, are inputs, as are item frames.
 */
public final class DirectionalComponentDetector implements RedstoneAuditDetector {
    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        Context context = new Context(snapshot);
        List<AuditFinding> findings = new ArrayList<>();
        for (Map.Entry<AuditPosition, AuditCell> entry : snapshot.cells().entrySet()) {
            AuditPosition position = entry.getKey();
            AuditCell cell = entry.getValue();
            if (!snapshot.inSelection(position)) {
                continue;
            }
            if (cell.isDiode()) {
                Boolean input = inputOk(snapshot, position, cell);
                if (Boolean.FALSE.equals(input)) {
                    findings.add(missingInput(context, position, cell));
                }
            }
            if (cell.isDiode() || cell.kind() == AuditKind.OBSERVER) {
                Boolean output = outputOk(snapshot, position, cell);
                if (Boolean.FALSE.equals(output)) {
                    findings.add(deadOutput(snapshot, position, cell));
                }
            }
        }
        return findings;
    }

    private static Boolean inputOk(RedstoneAuditSnapshot snapshot, AuditPosition position, AuditCell diode) {
        AuditPosition input = position.relative(diode.inputSide());
        AuditCell behind = snapshot.at(input);
        if (behind == null) {
            return null;
        }
        boolean comparator = diode.kind() == AuditKind.COMPARATOR;
        if (behind.isDust() || behind.source() || behind.analogSource() || behind.isTorch()) {
            return true;
        }
        if (behind.isDiode() || behind.kind() == AuditKind.OBSERVER) {
            return input.relative(behind.outputSide()).equals(position);
        }
        if (comparator && behind.readable()) {
            return true;
        }
        if (behind.conductor()) {
            if (comparator) {
                AuditCell beyond = snapshot.at(input.relative(diode.inputSide()));
                if (beyond == null) {
                    return null;
                }
                if (beyond.readable()) {
                    return true;
                }
            }
            return Topology.conductorPowerable(snapshot, input, position);
        }
        return false;
    }

    private static Boolean outputOk(RedstoneAuditSnapshot snapshot, AuditPosition position, AuditCell component) {
        AuditPosition output = position.relative(component.outputSide());
        AuditCell front = snapshot.at(output);
        if (front == null) {
            return null;
        }
        if (front.isDust() || front.receiver()) {
            return true;
        }
        if (front.isDiode()) {
            return !output.relative(front.outputSide()).equals(position);
        }
        if (front.conductor()) {
            return Topology.conductorHasConsumers(snapshot, output, true, position);
        }
        return false;
    }

    private static AuditFinding missingInput(Context context, AuditPosition position, AuditCell diode) {
        RedstoneAuditSnapshot snapshot = context.snapshot;
        boolean comparator = diode.kind() == AuditKind.COMPARATOR;
        AuditPosition input = position.relative(diode.inputSide());
        StringBuilder pattern = new StringBuilder("Nothing behind it at ").append(input).append(" (")
                .append(Topology.describe(snapshot.at(input))).append(") can ")
                .append(comparator ? "power it or be measured by it." : "power it.");
        AuditPosition output = position.relative(diode.outputSide());
        AuditCell front = snapshot.at(output);
        if (front != null && arrivesFromFront(context, output, front, position)) {
            pattern.append(" The signal seems to arrive from its output side, so it may be placed backwards.");
        }
        String name = comparator ? "Comparator" : "Repeater";
        return new AuditFinding(position, AuditConfidence.HIGH, AuditSeverity.BREAKS,
                name.toLowerCase(java.util.Locale.ROOT) + "-input", name + " has no input", pattern.toString(),
                "Feed it from behind, or rotate it so its arrow points away from the incoming signal.");
    }

    /** Is a signal arriving at this component's output side from something other than the component itself? */
    private static boolean arrivesFromFront(Context context, AuditPosition output, AuditCell front,
                                            AuditPosition component) {
        if (front.isDust()) {
            return context.fedOtherThan(output, component);
        }
        if (front.source() || front.analogSource() || front.isTorch()) {
            return true;
        }
        return (front.isDiode() || front.kind() == AuditKind.OBSERVER)
                && output.relative(front.outputSide()).equals(component);
    }

    /** Per-audit memo: dust networks are built once, not once per finding. */
    private static final class Context {
        private final RedstoneAuditSnapshot snapshot;
        private Map<AuditPosition, Topology.Network> networkOf;

        Context(RedstoneAuditSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        boolean fedOtherThan(AuditPosition dust, AuditPosition component) {
            if (networkOf == null) {
                networkOf = new java.util.HashMap<>();
                for (Topology.Network network : Topology.networks(snapshot)) {
                    for (AuditPosition piece : network.dust()) {
                        networkOf.put(piece, network);
                    }
                }
            }
            Topology.Network network = networkOf.get(dust);
            if (network == null) {
                return false;
            }
            for (AuditPosition piece : network.dust()) {
                Topology.Feed feed = Topology.dustFeed(snapshot, piece, component);
                if (feed != null && feed != Topology.Feed.NONE) {
                    return true;
                }
            }
            return false;
        }
    }

    private static AuditFinding deadOutput(RedstoneAuditSnapshot snapshot, AuditPosition position, AuditCell component) {
        String name = switch (component.kind()) {
            case REPEATER -> "Repeater";
            case COMPARATOR -> "Comparator";
            default -> "Observer";
        };
        AuditPosition output = position.relative(component.outputSide());
        return new AuditFinding(position, AuditConfidence.HIGH, AuditSeverity.BREAKS,
                name.toLowerCase(java.util.Locale.ROOT) + "-output", name + " output drives nothing",
                "Its output at " + output + " is " + Topology.describe(snapshot.at(output)) + " with nothing to power.",
                "Point it at the component it should power, or carry the signal on with dust or a solid block.");
    }
}
