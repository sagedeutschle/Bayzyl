package com.bayzyl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class EditHistory {
    private static final int DEFAULT_MAX_HISTORY = 50;

    private final ConcurrentMap<UUID, Deque<EditAction>> undo = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Deque<EditAction>> redo = new ConcurrentHashMap<>();
    private volatile int maxHistory = DEFAULT_MAX_HISTORY;

    public void push(UUID playerId, EditAction action) {
        if (action == null) {
            return;
        }
        if (action.getChanges().isEmpty()
                && action.getEntityChanges().isEmpty()
                && action.getBiomeChanges().isEmpty()
                && action.getBiomeColumnChanges().isEmpty()
                && action.getBeforeSelection() == null
                && action.getAfterSelection() == null) {
            return;
        }
        Deque<EditAction> stack = undo.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        stack.push(action);
        trim(stack);
        Deque<EditAction> redoStack = redo.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        redoStack.clear();
    }

    public EditAction popUndo(UUID playerId) {
        Deque<EditAction> stack = undo.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        EditAction action = stack.pop();
        redo.computeIfAbsent(playerId, id -> new ArrayDeque<>()).push(action);
        return action;
    }

    public EditAction peekUndo(UUID playerId) {
        Deque<EditAction> stack = undo.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    public EditAction popRedo(UUID playerId) {
        Deque<EditAction> stack = redo.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        EditAction action = stack.pop();
        undo.computeIfAbsent(playerId, id -> new ArrayDeque<>()).push(action);
        return action;
    }

    public EditAction peekRedo(UUID playerId) {
        Deque<EditAction> stack = redo.get(playerId);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    public boolean hasUndo(UUID playerId) {
        Deque<EditAction> stack = undo.get(playerId);
        return stack != null && !stack.isEmpty();
    }

    public boolean hasRedo(UUID playerId) {
        Deque<EditAction> stack = redo.get(playerId);
        return stack != null && !stack.isEmpty();
    }

    public void clear(UUID playerId) {
        Deque<EditAction> u = undo.get(playerId);
        if (u != null) {
            u.clear();
        }
        Deque<EditAction> r = redo.get(playerId);
        if (r != null) {
            r.clear();
        }
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
        undo.values().forEach(this::trim);
        redo.values().forEach(this::trim);
    }

    public List<EditAction> snapshotUndo(UUID playerId, int limit) {
        return snapshot(undo.get(playerId), limit);
    }

    public List<EditAction> snapshotRedo(UUID playerId, int limit) {
        return snapshot(redo.get(playerId), limit);
    }

    public void restore(UUID playerId, List<EditAction> undoActions, List<EditAction> redoActions) {
        Deque<EditAction> undoStack = new ArrayDeque<>();
        if (undoActions != null) {
            for (EditAction action : undoActions) {
                if (action != null) {
                    undoStack.addLast(action);
                }
            }
        }
        Deque<EditAction> redoStack = new ArrayDeque<>();
        if (redoActions != null) {
            for (EditAction action : redoActions) {
                if (action != null) {
                    redoStack.addLast(action);
                }
            }
        }
        undo.put(playerId, undoStack);
        redo.put(playerId, redoStack);
        trim(undoStack);
        trim(redoStack);
    }

    private List<EditAction> snapshot(Deque<EditAction> stack, int limit) {
        if (stack == null || stack.isEmpty() || limit <= 0) {
            return List.of();
        }
        return stack.stream()
                .limit(limit)
                .toList();
    }

    private void trim(Deque<EditAction> stack) {
        while (stack.size() > maxHistory) {
            stack.removeLast();
        }
    }

    /**
     * Compact every uncompacted action across all players' undo and redo stacks
     * using a single shared {@link BlockDataInterner}. Returns a 3-element array:
     * [actionsCompacted, blocksRewritten, distinctStatesPooled].
     */
    public long[] compactAll() {
        BlockDataInterner interner = new BlockDataInterner();
        long actionsCompacted = 0;
        long blocksRewritten = 0;
        for (Deque<EditAction> stack : undo.values()) {
            for (EditAction action : stack) {
                if (action.isCompacted()) continue;
                blocksRewritten += action.compact(interner);
                actionsCompacted++;
            }
        }
        for (Deque<EditAction> stack : redo.values()) {
            for (EditAction action : stack) {
                if (action.isCompacted()) continue;
                blocksRewritten += action.compact(interner);
                actionsCompacted++;
            }
        }
        return new long[]{actionsCompacted, blocksRewritten, interner.distinctStates()};
    }

    /**
     * Drop every action whose creation timestamp is older than {@code now - ttlMs}
     * from both undo and redo stacks. Returns the number of actions dropped.
     */
    public int evictOlderThan(long ttlMs) {
        long cutoff = System.currentTimeMillis() - ttlMs;
        int dropped = 0;
        dropped += evictStack(undo, cutoff);
        dropped += evictStack(redo, cutoff);
        return dropped;
    }

    private int evictStack(ConcurrentMap<UUID, Deque<EditAction>> stacks, long cutoff) {
        int dropped = 0;
        for (Deque<EditAction> stack : stacks.values()) {
            java.util.Iterator<EditAction> it = stack.iterator();
            while (it.hasNext()) {
                EditAction action = it.next();
                if (action.getCreatedAtMs() < cutoff) {
                    it.remove();
                    dropped++;
                }
            }
        }
        return dropped;
    }
}
