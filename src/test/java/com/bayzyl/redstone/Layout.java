package com.bayzyl.redstone;

import com.bayzyl.redstone.AuditCell.WireLink;

import java.util.EnumMap;
import java.util.Map;

/** Test fixture builder: places cells in a selection that starts at 0,0,0. */
final class Layout {
    private final RedstoneAuditSnapshot.Builder builder;

    Layout(int maxX, int maxY, int maxZ) {
        this.builder = RedstoneAuditSnapshot.builder(new AuditPosition(0, 0, 0), new AuditPosition(maxX, maxY, maxZ));
    }

    Layout put(int x, int y, int z, AuditCell cell) {
        builder.put(new AuditPosition(x, y, z), cell);
        return this;
    }

    /** A row of conductors at height {@code y} from x0 to x1. */
    Layout floor(int y, int z, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            put(x, y, z, AuditCell.conductorBlock());
        }
        return this;
    }

    /** A straight east-west dust line; like the server, end pieces also point along the line. */
    Layout dustLine(int y, int z, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            put(x, y, z, eastWestDust());
        }
        return this;
    }

    RedstoneAuditSnapshot build() {
        return builder.build();
    }

    static AuditCell eastWestDust() {
        return dust(Side.EAST, Side.WEST);
    }

    static AuditCell dust(Side... sides) {
        Map<Side, WireLink> wire = new EnumMap<>(Side.class);
        for (Side side : sides) {
            wire.put(side, WireLink.SIDE);
        }
        return AuditCell.dust(wire);
    }

    static AuditPosition at(int x, int y, int z) {
        return new AuditPosition(x, y, z);
    }
}
