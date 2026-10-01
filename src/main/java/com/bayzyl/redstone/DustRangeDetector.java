package com.bayzyl.redstone;

import com.bayzyl.redstone.Topology.Feed;
import com.bayzyl.redstone.Topology.Network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dust loses one strength per block. Measured from the nearest feed (not by line length): the 16th dust from a
 * full-strength source is where the signal dies (HIGH); with only analog feeds the strength is unknown (MEDIUM).
 */
public final class DustRangeDetector implements RedstoneAuditDetector {
    public static final String ID = "dust-range";
    private static final int MAX_DISTANCE = 15;

    @Override
    public List<AuditFinding> detect(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        for (Network network : Topology.networks(snapshot)) {
            if (network.unknown()) {
                continue;
            }
            List<AuditPosition> full = new ArrayList<>();
            List<AuditPosition> analog = new ArrayList<>();
            boolean unknown = false;
            for (AuditPosition dust : network.dust()) {
                Feed feed = Topology.dustFeed(snapshot, dust);
                if (feed == null) {
                    unknown = true;
                    break;
                }
                if (feed == Feed.FULL) {
                    full.add(dust);
                } else if (feed == Feed.ANALOG) {
                    analog.add(dust);
                }
            }
            if (unknown || (full.isEmpty() && analog.isEmpty())) {
                continue;
            }
            Map<AuditPosition, Integer> fromFull = distances(network, full);
            Map<AuditPosition, Integer> fromAnalog = distances(network, analog);
            for (AuditPosition dust : network.dust()) {
                Integer fullDistance = fromFull.get(dust);
                Integer analogDistance = fromAnalog.get(dust);
                if (!full.isEmpty()) {
                    if (fullDistance != null && fullDistance == MAX_DISTANCE
                            && (analogDistance == null || analogDistance >= MAX_DISTANCE)) {
                        findings.add(new AuditFinding(dust, AuditConfidence.HIGH, AuditSeverity.BREAKS, ID,
                                "Signal runs out after 15 dust",
                                "This is the 16th dust from the nearest powered point of the line; the signal is 0 here.",
                                "Place a repeater within 15 dust of the source to boost the signal back to full strength."));
                    }
                } else if (analogDistance != null && analogDistance == MAX_DISTANCE) {
                    findings.add(new AuditFinding(dust, AuditConfidence.MEDIUM, AuditSeverity.DEGRADES, ID,
                            "Analog signal cannot reach here",
                            "This dust is 16 blocks from the comparator or analog source feeding the line; even a "
                                    + "full-strength reading never arrives here.",
                            "Add a repeater (it outputs full strength) or shorten the line."));
                }
            }
        }
        return findings;
    }

    /** Breadth-first steps along the dust from the given starting pieces. */
    private static Map<AuditPosition, Integer> distances(Network network, List<AuditPosition> starts) {
        Map<AuditPosition, Integer> distance = new HashMap<>();
        ArrayDeque<AuditPosition> queue = new ArrayDeque<>();
        for (AuditPosition start : starts) {
            distance.put(start, 0);
            queue.add(start);
        }
        Set<AuditPosition> members = network.dust();
        while (!queue.isEmpty()) {
            AuditPosition current = queue.removeFirst();
            int next = distance.get(current) + 1;
            for (Topology.Link link : network.links().getOrDefault(current, List.of())) {
                if (link.kind() == Topology.LinkKind.CONTINUES && members.contains(link.next())
                        && !distance.containsKey(link.next())) {
                    distance.put(link.next(), next);
                    queue.addLast(link.next());
                }
            }
        }
        return distance;
    }
}
