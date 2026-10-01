package com.bayzyl.redstone;

/** One of the six block faces, in audit coordinates (x east, y up, z south). */
public enum Side {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    public static final Side[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};

    private final int dx;
    private final int dy;
    private final int dz;

    Side(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public boolean horizontal() {
        return dy == 0;
    }

    public Side opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    /** True for the two horizontal sides perpendicular to this horizontal side. */
    public boolean perpendicularTo(Side other) {
        return horizontal() && other.horizontal() && this != other && this != other.opposite();
    }
}
