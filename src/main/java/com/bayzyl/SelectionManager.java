package com.bayzyl;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SelectionManager {
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();
    private final AtomicLong globalRevision = new AtomicLong();

    public Selection getOrCreate(UUID playerId) {
        return selections.computeIfAbsent(playerId, id -> new Selection(null, null, SelectionType.CUBOID));
    }

    public Selection get(UUID playerId) {
        return selections.get(playerId);
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
        bumpRevision(playerId);
    }

    public boolean swap(UUID playerId) {
        Selection selection = selections.get(playerId);
        if (selection == null || selection.getPos1() == null || selection.getPos2() == null) {
            return false;
        }
        Location pos1 = selection.getPos1();
        selection.setPos1(selection.getPos2());
        selection.setPos2(pos1);
        bumpRevision(playerId);
        return true;
    }

    public void setPos1(UUID playerId, Location location) {
        Selection selection = getOrCreate(playerId);
        selection.setPos1(location);
        selection.setType(SelectionType.CUBOID);
        bumpRevision(playerId);
    }

    public void setPos2(UUID playerId, Location location) {
        Selection selection = getOrCreate(playerId);
        selection.setPos2(location);
        selection.setType(SelectionType.CUBOID);
        bumpRevision(playerId);
    }

    public void setCuboid(UUID playerId, Location pos1, Location pos2) {
        Selection selection = getOrCreate(playerId);
        selection.setPos1(pos1);
        selection.setPos2(pos2);
        selection.setType(SelectionType.CUBOID);
        bumpRevision(playerId);
    }

    public Map<UUID, Selection> snapshot() {
        return Map.copyOf(selections);
    }

    public long revision(UUID playerId) {
        return revisions.getOrDefault(playerId, 0L);
    }

    public long revision() {
        return globalRevision.get();
    }

    private void bumpRevision(UUID playerId) {
        if (playerId == null) {
            return;
        }
        revisions.merge(playerId, 1L, Long::sum);
        globalRevision.incrementAndGet();
    }
}
