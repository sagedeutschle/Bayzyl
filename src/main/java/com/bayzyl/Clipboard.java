package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.util.List;

public final class Clipboard {
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockData[] data;
    private final BlockState[] states;
    private final List<ClipboardEntity> entities;
    private final Location origin;
    private final int minOffsetX;
    private final int minOffsetY;
    private final int minOffsetZ;

    public Clipboard(int sizeX, int sizeY, int sizeZ, BlockData[] data, BlockState[] states, List<ClipboardEntity> entities, Location origin,
                     int minOffsetX, int minOffsetY, int minOffsetZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.data = data;
        this.states = states;
        this.entities = entities;
        this.origin = origin;
        this.minOffsetX = minOffsetX;
        this.minOffsetY = minOffsetY;
        this.minOffsetZ = minOffsetZ;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public BlockData get(int x, int y, int z) {
        int idx = (y * sizeZ + z) * sizeX + x;
        return data[idx];
    }

    public BlockState getState(int x, int y, int z) {
        int idx = (y * sizeZ + z) * sizeX + x;
        return states[idx];
    }

    public List<ClipboardEntity> getEntities() {
        return entities;
    }

    public Location getOrigin() {
        return origin;
    }

    public int getMinOffsetX() {
        return minOffsetX;
    }

    public int getMinOffsetY() {
        return minOffsetY;
    }

    public int getMinOffsetZ() {
        return minOffsetZ;
    }
}
