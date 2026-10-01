package com.bayzyl;

import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

/**
 * Pool that returns canonical instances of {@link BlockData} so duplicate states
 * across many {@link BlockChange} entries collapse to a single shared reference.
 *
 * Typical builder pastes have very few unique block states relative to volume —
 * a 9 M-block schematic usually has &lt; 100 distinct states. Sharing one canonical
 * instance per state cuts the BlockData heap footprint of an undo record from
 * tens of millions of references to a handful.
 *
 * Not thread-safe. Compaction passes are expected to run on the main thread.
 */
public final class BlockDataInterner {
    private final Map<BlockData, BlockData> pool = new HashMap<>();
    private long lookups = 0;
    private long hits = 0;

    public BlockData intern(BlockData data) {
        if (data == null) {
            return null;
        }
        lookups++;
        BlockData existing = pool.get(data);
        if (existing != null) {
            hits++;
            return existing;
        }
        pool.put(data, data);
        return data;
    }

    public int distinctStates() {
        return pool.size();
    }

    public long lookups() {
        return lookups;
    }

    public long hits() {
        return hits;
    }
}
