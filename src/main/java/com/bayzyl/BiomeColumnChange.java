package com.bayzyl;

import org.bukkit.World;
import org.bukkit.block.Biome;

public final class BiomeColumnChange {
    private final World world;
    private final int x;
    private final int z;
    private final Biome before;
    private final Biome after;

    public BiomeColumnChange(World world, int x, int z, Biome before, Biome after) {
        this.world = world;
        this.x = x;
        this.z = z;
        this.before = before;
        this.after = after;
    }

    public World getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public Biome getBefore() {
        return before;
    }

    public Biome getAfter() {
        return after;
    }
}
