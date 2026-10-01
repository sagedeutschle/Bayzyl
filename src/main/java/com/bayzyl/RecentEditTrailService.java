package com.bayzyl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecentEditTrailService {
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final Map<UUID, Deque<String>> trails = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> limits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();

    public void record(UUID playerId, String entry) {
        if (playerId == null || entry == null || entry.isBlank()) {
            return;
        }
        Deque<String> trail = trails.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        trail.addFirst(entry);
        trim(playerId, trail);
        bumpRevision(playerId);
    }

    public List<String> recent(UUID playerId) {
        Deque<String> trail = trails.get(playerId);
        if (trail == null || trail.isEmpty()) {
            return List.of();
        }
        return List.copyOf(trail);
    }

    public int limit(UUID playerId) {
        return limits.getOrDefault(playerId, DEFAULT_LIMIT);
    }

    public void setLimit(UUID playerId, int limit) {
        if (playerId == null) {
            return;
        }
        int clamped = Math.max(1, Math.min(MAX_LIMIT, limit));
        limits.put(playerId, clamped);
        trim(playerId, trails.computeIfAbsent(playerId, id -> new ArrayDeque<>()));
        bumpRevision(playerId);
    }

    public int defaultLimit() {
        return DEFAULT_LIMIT;
    }

    public int maxLimit() {
        return MAX_LIMIT;
    }

    public void clear(UUID playerId) {
        trails.remove(playerId);
        limits.remove(playerId);
        bumpRevision(playerId);
    }

    public void clearEntries(UUID playerId) {
        trails.remove(playerId);
        bumpRevision(playerId);
    }

    public long revision(UUID playerId) {
        return revisions.getOrDefault(playerId, 0L);
    }

    private void trim(UUID playerId, Deque<String> trail) {
        int limit = limit(playerId);
        while (trail.size() > limit) {
            trail.removeLast();
        }
    }

    private void bumpRevision(UUID playerId) {
        if (playerId == null) {
            return;
        }
        revisions.merge(playerId, 1L, Long::sum);
    }
}
