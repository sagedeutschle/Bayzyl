package com.bayzyl.redstone;

import java.util.Comparator;

/** A block coordinate. Ordered by x, then y, then z, so listings are stable. */
public record AuditPosition(int x, int y, int z) implements Comparable<AuditPosition> {
    private static final Comparator<AuditPosition> ORDER = Comparator.comparingInt(AuditPosition::x)
            .thenComparingInt(AuditPosition::y)
            .thenComparingInt(AuditPosition::z);

    public AuditPosition relative(Side side) {
        return new AuditPosition(x + side.dx(), y + side.dy(), z + side.dz());
    }

    @Override
    public int compareTo(AuditPosition other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return "x:" + x + " y:" + y + " z:" + z;
    }
}
