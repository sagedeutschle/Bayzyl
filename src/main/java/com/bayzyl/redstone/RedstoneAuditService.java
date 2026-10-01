package com.bayzyl.redstone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs every detector over a snapshot and returns the findings inside the selection, ranked by confidence, then
 * severity, then position. Read-only: nothing here can change the world.
 */
public final class RedstoneAuditService {
    public static final int MAX_FINDINGS = 200;

    private final List<RedstoneAuditDetector> detectors;

    public RedstoneAuditService(List<RedstoneAuditDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    /** The shipped detector catalogue. */
    public static RedstoneAuditService standard() {
        return new RedstoneAuditService(List.of(
                new DustDeadEndDetector(),
                new DustRangeDetector(),
                new DirectionalComponentDetector()));
    }

    public List<AuditFinding> audit(RedstoneAuditSnapshot snapshot) {
        List<AuditFinding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RedstoneAuditDetector detector : detectors) {
            for (AuditFinding finding : detector.detect(snapshot)) {
                if (snapshot.inSelection(finding.position())
                        && seen.add(finding.detector() + "@" + finding.position())) {
                    findings.add(finding);
                }
            }
        }
        findings.sort(null);
        return findings.size() > MAX_FINDINGS ? List.copyOf(findings.subList(0, MAX_FINDINGS)) : List.copyOf(findings);
    }
}
