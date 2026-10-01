package com.bayzyl;

import java.util.List;

public final class EditAction {
    private final List<BlockChange> changes;
    private final List<EntityChange> entityChanges;
    private final List<BiomeChange> biomeChanges;
    private final List<BiomeColumnChange> biomeColumnChanges;
    private final SelectionSnapshot beforeSelection;
    private final SelectionSnapshot afterSelection;
    private final long createdAtMs = System.currentTimeMillis();
    private boolean compacted = false;

    public EditAction(List<BlockChange> changes) {
        this(changes, List.of(), List.of(), List.of(), null, null);
    }

    public EditAction(List<BlockChange> changes, List<EntityChange> entityChanges) {
        this(changes, entityChanges, List.of(), List.of(), null, null);
    }

    public EditAction(List<BlockChange> changes, List<EntityChange> entityChanges,
                      SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        this(changes, entityChanges, List.of(), List.of(), beforeSelection, afterSelection);
    }

    public EditAction(List<BlockChange> changes, List<EntityChange> entityChanges, List<BiomeChange> biomeChanges, List<BiomeColumnChange> biomeColumnChanges,
                      SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        this.changes = changes;
        this.entityChanges = entityChanges;
        this.biomeChanges = biomeChanges;
        this.biomeColumnChanges = biomeColumnChanges;
        this.beforeSelection = beforeSelection;
        this.afterSelection = afterSelection;
    }

    public List<BlockChange> getChanges() {
        return changes;
    }

    public List<EntityChange> getEntityChanges() {
        return entityChanges;
    }

    public List<BiomeChange> getBiomeChanges() {
        return biomeChanges;
    }

    public List<BiomeColumnChange> getBiomeColumnChanges() {
        return biomeColumnChanges;
    }

    public SelectionSnapshot getBeforeSelection() {
        return beforeSelection;
    }

    public SelectionSnapshot getAfterSelection() {
        return afterSelection;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean isCompacted() {
        return compacted;
    }

    /**
     * Compact this action's BlockChange list by interning duplicate BlockData
     * states through the supplied pool. Returns the number of shell BlockChange
     * entries that were rewritten with deduped state.
     */
    public int compact(BlockDataInterner interner) {
        if (compacted || changes.isEmpty()) {
            compacted = true;
            return 0;
        }
        int rewritten = 0;
        for (int i = 0; i < changes.size(); i++) {
            BlockChange original = changes.get(i);
            if (original == null) continue;
            org.bukkit.block.data.BlockData beforeI = interner.intern(original.getBefore());
            org.bukkit.block.data.BlockData afterI = interner.intern(original.getAfter());
            if (beforeI != original.getBefore() || afterI != original.getAfter()) {
                changes.set(i, new BlockChange(original.getLocation(), beforeI, afterI));
                rewritten++;
            }
        }
        compacted = true;
        return rewritten;
    }
}
