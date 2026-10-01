package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.block.Biome;

public final class BiomeChange {
    private final Location location;
    private final Biome before;
    private final Biome after;

    public BiomeChange(Location location, Biome before, Biome after) {
        this.location = location;
        this.before = before;
        this.after = after;
    }

    public Location getLocation() {
        return location;
    }

    public Biome getBefore() {
        return before;
    }

    public Biome getAfter() {
        return after;
    }
}
