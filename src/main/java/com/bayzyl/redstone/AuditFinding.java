package com.bayzyl.redstone;

import java.util.Comparator;
import java.util.Objects;

/**
 * One likely wiring fault. {@code pattern} describes what the audit saw at {@code position}; {@code suggestion} is a
 * non-binding repair idea. Findings order by confidence, then severity, then position.
 */
public record AuditFinding(AuditPosition position, AuditConfidence confidence, AuditSeverity severity,
                           String detector, String title, String pattern, String suggestion)
        implements Comparable<AuditFinding> {
    private static final Comparator<AuditFinding> ORDER = Comparator.comparing(AuditFinding::confidence)
            .thenComparing(AuditFinding::severity)
            .thenComparing(AuditFinding::position)
            .thenComparing(AuditFinding::detector);

    public AuditFinding {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(detector, "detector");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(suggestion, "suggestion");
    }

    @Override
    public int compareTo(AuditFinding other) {
        return ORDER.compare(this, other);
    }
}
