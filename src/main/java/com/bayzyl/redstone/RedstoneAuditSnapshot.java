package com.bayzyl.redstone;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, Bukkit-free capture of a selection plus a {@link #HALO}-block read-only ring around it. Detectors
 * may read the halo to judge components at the selection edge, but findings are only reported inside the
 * selection. Positions beyond the halo are unknown ({@link #at} returns null), never assumed empty.
 */
public final class RedstoneAuditSnapshot {
    public static final int HALO = 2;

    private final AuditPosition min;
    private final AuditPosition max;
    private final Map<AuditPosition, AuditCell> cells;

    private RedstoneAuditSnapshot(AuditPosition min, AuditPosition max, Map<AuditPosition, AuditCell> cells) {
        this.min = min;
        this.max = max;
        this.cells = Collections.unmodifiableMap(new HashMap<>(cells));
    }

    public static Builder builder(AuditPosition min, AuditPosition max) {
        return new Builder(min, max);
    }

    public AuditPosition min() {
        return min;
    }

    public AuditPosition max() {
        return max;
    }

    /** The cell at {@code position}: {@link AuditCell#EMPTY} if captured and empty, null if not captured. */
    public AuditCell at(AuditPosition position) {
        if (!captured(position)) {
            return null;
        }
        return cells.getOrDefault(position, AuditCell.EMPTY);
    }

    public boolean captured(AuditPosition position) {
        return within(position, HALO);
    }

    public boolean inSelection(AuditPosition position) {
        return within(position, 0);
    }

    /** Every non-empty captured cell, halo included. */
    public Map<AuditPosition, AuditCell> cells() {
        return cells;
    }

    private boolean within(AuditPosition position, int margin) {
        return position.x() >= min.x() - margin && position.x() <= max.x() + margin
                && position.y() >= min.y() - margin && position.y() <= max.y() + margin
                && position.z() >= min.z() - margin && position.z() <= max.z() + margin;
    }

    public static final class Builder {
        private final AuditPosition min;
        private final AuditPosition max;
        private final Map<AuditPosition, AuditCell> cells = new HashMap<>();

        private Builder(AuditPosition min, AuditPosition max) {
            this.min = Objects.requireNonNull(min, "min");
            this.max = Objects.requireNonNull(max, "max");
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
                throw new IllegalArgumentException("min must not exceed max");
            }
        }

        public Builder put(AuditPosition position, AuditCell cell) {
            RedstoneAuditSnapshot bounds = new RedstoneAuditSnapshot(min, max, Map.of());
            if (!bounds.captured(position)) {
                throw new IllegalArgumentException(position + " is outside the captured area");
            }
            if (cell == null || cell.isEmpty()) {
                cells.remove(position);
            } else {
                cells.put(position, cell);
            }
            return this;
        }

        public RedstoneAuditSnapshot build() {
            return new RedstoneAuditSnapshot(min, max, cells);
        }
    }
}
