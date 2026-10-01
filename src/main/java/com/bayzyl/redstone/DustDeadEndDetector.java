package com.bayzyl.redstone;

import com.bayzyl.redstone.Topology.Link;
import com.bayzyl.redstone.Topology.LinkKind;
import com.bayzyl.redstone.Topology.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A dust line that ends pointing at nothing. HIGH when the whole line powers nothing at all (the signal is lost);
 * LOW when the line works elsewhere and this is only an unused stub.
 */
public final class DustDeadEndDetector implements RedstoneAuditDetector {
    public static final String ID = "dust-dead-end";

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Network network : Topology.networks(snapshot)) {
            if (network.unknown()) {
                continue;
            }
            boolean fed = false;
            boolean delivers = false;
            boolean unknown = false;
            for (AuditPosition dust : network.dust()) {
                AuditCell cell = snapshot.at(dust);
                Topology.Feed feed = Topology.dustFeed(snapshot, dust);
                Boolean delivered = Topology.dustDelivers(snapshot, dust, cell);
                if (feed == null || delivered == null) {
                    unknown = true;
                    break;
                }
                fed |= feed != Topology.Feed.NONE
                        || network.links().get(dust).stream().anyMatch(link -> link.kind() == LinkKind.FEEDS);
                delivers |= delivered;
            }
            if (unknown || !fed) {
                continue;
            }
            for (Map.Entry<AuditPosition, List<Link>> entry : network.links().entrySet()) {
                AuditPosition dust = entry.getKey();
                AuditCell cell = snapshot.at(dust);
                if (Boolean.TRUE.equals(Topology.dustDelivers(snapshot, dust, cell))) {
                    continue;
                }
                for (Link link : entry.getValue()) {
                    if (link.kind() == LinkKind.LOOSE) {
                        findings.add(delivers ? stub(dust, link) : deadEnd(snapshot, dust, link));
                        break;
                    }
                }
            }
        }
        return findings;
    }

    private static AuditFinding deadEnd(RedstoneAuditSnapshot snapshot, AuditPosition dust, Link link) {
        AuditPosition pointed = dust.relative(link.side());
        StringBuilder pattern = new StringBuilder("The line ends here pointing ")
                .append(Topology.direction(link.side())).append(" into ")
                .append(Topology.describe(snapshot.at(pointed)))
                .append(", and nothing along the line receives the signal.");
        for (Side side : Side.HORIZONTAL) {
            AuditPosition beside = dust.relative(side);
            AuditCell cell = snapshot.at(beside);
            if (side != link.side() && cell != null && cell.receiver()) {
                pattern.append(" The component at ").append(beside)
                        .append(" sits beside the line, but the line does not point into it.");
            }
        }
        return new AuditFinding(dust, AuditConfidence.HIGH, AuditSeverity.BREAKS, ID,
                "Dust line ends before a receiver", pattern.toString(),
                "Continue the line toward the component it should power, or turn its last piece to face it.");
    }

    private static AuditFinding stub(AuditPosition dust, Link link) {
        return new AuditFinding(dust, AuditConfidence.LOW, AuditSeverity.ADVISORY, ID,
                "Unused dust branch",
                "This branch ends pointing " + Topology.direction(link.side())
                        + " without reaching a receiver; the rest of the line does power something.",
                "Remove the branch, or extend it to the component it was meant to reach.");
    }
}
