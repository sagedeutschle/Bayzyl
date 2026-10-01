package com.bayzyl.gen;

import com.bayzyl.BlockChange;
import com.bayzyl.WorldPosCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot-and-apply helper. Generators stage changes through this so:
 *   1. {@code /undo} sees one consolidated edit per click.
 *   2. We can diff before/after and skip writes that would be a no-op.
 *   3. Bedrock and out-of-world writes can't sneak through.
 */
public final class GenChangeCollector {
    private final World world;
    private final boolean allowBedrock;
    private final Map<Long, BlockData> before = new LinkedHashMap<>();
    private final Map<Long, BlockData> after = new LinkedHashMap<>();

    public GenChangeCollector(World world, boolean allowBedrock) {
        this.world = world;
        this.allowBedrock = allowBedrock;
    }

    public void set(int x, int y, int z, BlockData data) {
        if (data == null) {
            return;
        }
        if (y < world.getMinHeight() || y > world.getMaxHeight() - 1) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        BlockData current = block.getBlockData();
        if (!allowBedrock && (current.getMaterial() == Material.BEDROCK
                || data.getMaterial() == Material.BEDROCK)) {
            return;
        }
        if (current.matches(data)) {
            return;
        }
        long key = encode(x, y, z);
        before.putIfAbsent(key, current.clone());
        after.put(key, data.clone());
    }

    public void setMaterial(int x, int y, int z, Material material) {
        if (material == null) {
            return;
        }
        set(x, y, z, material.createBlockData());
    }

    public void clearAir(int x, int y, int z) {
        setMaterial(x, y, z, Material.AIR);
    }

    public Material peek(int x, int y, int z) {
        if (y < world.getMinHeight() || y > world.getMaxHeight() - 1) {
            return Material.AIR;
        }
        long key = encode(x, y, z);
        BlockData staged = after.get(key);
        if (staged != null) {
            return staged.getMaterial();
        }
        return world.getBlockAt(x, y, z).getType();
    }

    public boolean isEmpty() {
        return after.isEmpty();
    }

    public int stagedCount() {
        return after.size();
    }

    public List<BlockChange> commit() {
        List<BlockChange> changes = new ArrayList<>(after.size());
        for (Map.Entry<Long, BlockData> entry : after.entrySet()) {
            int[] pos = decode(entry.getKey());
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            BlockData beforeData = before.get(entry.getKey());
            if (beforeData == null) {
                beforeData = block.getBlockData().clone();
            }
            BlockData target = entry.getValue();
            if (beforeData.matches(target)) {
                continue;
            }
            block.setBlockData(target, false);
            changes.add(new BlockChange(new Location(world, pos[0], pos[1], pos[2]),
                    beforeData.clone(), target.clone()));
        }
        return changes;
    }

    private static long encode(int x, int y, int z) {
        return WorldPosCodec.pack(x, y, z);
    }

    private static int[] decode(long value) {
        WorldPosCodec.Position position = WorldPosCodec.unpack(value);
        return new int[]{position.x(), position.y(), position.z()};
    }
}
