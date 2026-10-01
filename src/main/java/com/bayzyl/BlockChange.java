package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

public final class BlockChange {
    private final Location location;
    private final BlockData before;
    private final BlockData after;

    public BlockChange(Location location, BlockData before, BlockData after) {
        this.location = location;
        this.before = before;
        this.after = after;
    }

    public Location getLocation() {
        return location;
    }

    public BlockData getBefore() {
        return before;
    }

    public BlockData getAfter() {
        return after;
    }
}
