package com.bayzyl;

import org.bukkit.Location;

public final class Selection {
    private Location pos1;
    private Location pos2;
    private SelectionType type;

    public Selection(Location pos1, Location pos2, SelectionType type) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.type = type;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public SelectionType getType() {
        return type;
    }

    public void setType(SelectionType type) {
        this.type = type;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos2.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld());
    }

    public int getMinX() {
        return Math.min(pos1.getBlockX(), pos2.getBlockX());
    }

    public int getMinY() {
        return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    public int getMinZ() {
        return Math.min(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public int getMaxX() {
        return Math.max(pos1.getBlockX(), pos2.getBlockX());
    }

    public int getMaxY() {
        return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    public int getMaxZ() {
        return Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public long getVolume() {
        if (!isComplete()) {
            return 0;
        }
        long dx = (long) getMaxX() - getMinX() + 1;
        long dy = (long) getMaxY() - getMinY() + 1;
        long dz = (long) getMaxZ() - getMinZ() + 1;
        return dx * dy * dz;
    }

    public int getSizeX() {
        return getMaxX() - getMinX() + 1;
    }

    public int getSizeY() {
        return getMaxY() - getMinY() + 1;
    }

    public int getSizeZ() {
        return getMaxZ() - getMinZ() + 1;
    }

    public boolean contains(Location location) {
        if (!isComplete() || location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().equals(pos1.getWorld())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= getMinX() && x <= getMaxX()
                && y >= getMinY() && y <= getMaxY()
                && z >= getMinZ() && z <= getMaxZ();
    }
}
