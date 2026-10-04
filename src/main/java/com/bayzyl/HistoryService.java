package com.bayzyl;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HistoryService {
    private static final int PERSISTED_HISTORY_LIMIT = 10;

    private final EditHistory editHistory;
    private final SelectionManager selectionManager;
    private final PersistentEditHistoryService persistentEditHistoryService;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final TickScheduler tickScheduler;
    private final PlayerLookup playerLookup;
    private final TaskPolicy taskPolicy;
    private GlobalMaskService globalMaskService;

    // Chunked undo/redo: same scheduler shape as paste/cut. Threshold is
    // intentionally below the typical single-paste record size so that any
    // single large action's undo fires the chunked path.
    private static final long CHUNKED_CHANGE_THRESHOLD = 50_000L;
    private static final int HISTORY_BLOCKS_PER_TICK = 10_000;
    private static final long HISTORY_TIME_BUDGET_NANOS = 4_000_000L;

    // Per-player active async undo/redo tasks. Used as a re-entry guard so
    // a second /undo while one is in flight is refused, mirroring the paste
    // scheduler's "Already performing an edit" message.
    private final Map<UUID, TaskHandle> asyncHistoryTasks = new ConcurrentHashMap<>();
    private BukkitTask evictionTask;
    private BukkitTask compactionTask;

    public HistoryService(EditHistory editHistory, SelectionManager selectionManager,
                          PersistentEditHistoryService persistentEditHistoryService,
                          org.bukkit.plugin.java.JavaPlugin plugin) {
        this(editHistory, selectionManager, persistentEditHistoryService, plugin,
                serverTickScheduler(plugin), org.bukkit.Bukkit::getPlayer,
                new TaskPolicy(CHUNKED_CHANGE_THRESHOLD, HISTORY_BLOCKS_PER_TICK, HISTORY_TIME_BUDGET_NANOS));
    }

    HistoryService(EditHistory editHistory, SelectionManager selectionManager,
                   PersistentEditHistoryService persistentEditHistoryService,
                   org.bukkit.plugin.java.JavaPlugin plugin,
                   TickScheduler tickScheduler, PlayerLookup playerLookup, TaskPolicy taskPolicy) {
        this.editHistory = editHistory;
        this.selectionManager = selectionManager;
        this.persistentEditHistoryService = persistentEditHistoryService;
        this.plugin = plugin;
        this.tickScheduler = tickScheduler;
        this.playerLookup = playerLookup;
        this.taskPolicy = taskPolicy;
    }

    private static TickScheduler serverTickScheduler(org.bukkit.plugin.java.JavaPlugin plugin) {
        return (task, delayTicks, periodTicks) -> {
            BukkitTask scheduled = org.bukkit.Bukkit.getScheduler()
                    .runTaskTimer(plugin, task, delayTicks, periodTicks);
            return scheduled::cancel;
        };
    }

    public boolean hasUndo(Player player) {
        return editHistory.hasUndo(player.getUniqueId());
    }

    public boolean hasRedo(Player player) {
        return editHistory.hasRedo(player.getUniqueId());
    }

    public int getMaxHistory() {
        return editHistory.getMaxHistory();
    }

    public void setMaxHistory(int maxHistory) {
        editHistory.setMaxHistory(maxHistory);
    }

    public void record(UUID playerId, List<BlockChange> changes) {
        record(playerId, changes, List.of(), List.of(), List.of(), null, null);
    }

    public void record(UUID playerId, List<BlockChange> changes, List<EntityChange> entityChanges) {
        record(playerId, changes, entityChanges, List.of(), List.of(), null, null);
    }

    public void record(UUID playerId, List<BlockChange> changes, List<EntityChange> entityChanges,
                       SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        record(playerId, changes, entityChanges, List.of(), List.of(), beforeSelection, afterSelection);
    }

    public void setGlobalMaskService(GlobalMaskService globalMaskService) {
        this.globalMaskService = globalMaskService;
    }

    public void record(UUID playerId, List<BlockChange> changes, List<EntityChange> entityChanges, List<BiomeChange> biomeChanges, List<BiomeColumnChange> biomeColumnChanges,
                       SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        List<BlockChange> filteredChanges = applyGlobalMask(playerId, changes);
        editHistory.push(playerId, new EditAction(filteredChanges, entityChanges, biomeChanges, biomeColumnChanges, beforeSelection, afterSelection));
        savePlayer(playerId);
    }

    private List<BlockChange> applyGlobalMask(UUID playerId, List<BlockChange> changes) {
        if (globalMaskService == null || changes == null || changes.isEmpty()) {
            return changes;
        }
        BlockMask gmask = globalMaskService.get(playerId);
        if (gmask == null || gmask.isAny()) {
            return changes;
        }
        List<BlockChange> kept = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            if (gmask.matches(change.getBefore().getMaterial())) {
                kept.add(change);
            } else {
                change.getLocation().getBlock().setBlockData(change.getBefore(), false);
            }
        }
        return kept;
    }

    public EditAction peekUndo(UUID playerId) {
        return editHistory.peekUndo(playerId);
    }

    public boolean clearPlayerHistory(UUID playerId) {
        boolean hadHistory = editHistory.hasUndo(playerId) || editHistory.hasRedo(playerId);
        editHistory.clear(playerId);
        savePlayer(playerId);
        return hadHistory;
    }

    public int undo(Player player, int steps) {
        if (steps <= 0) return 0;
        UUID playerId = player.getUniqueId();
        if (asyncHistoryTasks.containsKey(playerId)) {
            ChatOutput.send(player, org.bukkit.ChatColor.RED + "Already performing an undo/redo. Wait for it to finish.");
            return 0;
        }
        // Gather actions first
        List<EditAction> actions = new java.util.ArrayList<>();
        long totalBlocks = 0L;
        for (int i = 0; i < steps; i++) {
            EditAction action = editHistory.peekUndo(playerId);
            if (action == null) break;
            if (BiomeCommandUtil.assessHistory(
                    action.getBiomeChanges(), action.getBiomeColumnChanges()).hardRejected()) {
                break;
            }
            action = editHistory.popUndo(playerId);
            actions.add(action);
            totalBlocks += action.getChanges().size();
        }
        if (actions.isEmpty()) {
            return 0;
        }
        // If small, apply synchronously
        if (totalBlocks <= taskPolicy.chunkedThreshold()) {
            int undone = 0;
            for (EditAction action : actions) {
                undoEntities(action.getEntityChanges(), false);
                List<BiomeChange> biomeChanges = action.getBiomeChanges();
                if (!biomeChanges.isEmpty()) {
                    BiomeCommandUtil.applyHistory(biomeChanges, true);
                }
                List<BiomeColumnChange> biomeColumnChanges = action.getBiomeColumnChanges();
                for (int index = biomeColumnChanges.size() - 1; index >= 0; index--) {
                    BiomeColumnChange change = biomeColumnChanges.get(index);
                    change.getWorld().setBiome(change.getX(), change.getZ(), change.getBefore());
                }
                List<BlockChange> blockChanges = action.getChanges();
                for (int index = blockChanges.size() - 1; index >= 0; index--) {
                    BlockChange change = blockChanges.get(index);
                    change.getLocation().getBlock().setBlockData(change.getBefore(), false);
                }
                undoEntities(action.getEntityChanges(), true);
                refreshBiomeChunks(action);
                applySelection(playerId, action.getBeforeSelection());
                undone++;
            }
            savePlayer(playerId);
            return undone;
        }

        // Schedule async undo for large operations
        final long totalToProcess = totalBlocks;
        final TaskHandle[] holder = new TaskHandle[1];
        Runnable runnable = new Runnable() {
            final java.util.Iterator<EditAction> actionIter = actions.iterator();
            EditAction currentAction = null;
            int changeIndex = -1; // index into currentAction changes (reverse)
            long processedTotal = 0;
            int nextProgressReport = 25;

            @Override
            public void run() {
                org.bukkit.entity.Player p = playerLookup.find(playerId);
                if (p == null || !p.isOnline()) {
                    cancelSelf();
                    return;
                }
                long start = System.nanoTime();
                long processedThisTick = 0;
                try {
                    while ((currentAction != null || actionIter.hasNext())
                            && processedThisTick < taskPolicy.blocksPerTick()
                            && System.nanoTime() - start < taskPolicy.timeBudgetNanos()) {
                        if (currentAction == null) {
                            currentAction = actionIter.next();
                            undoEntities(currentAction.getEntityChanges(), false);
                            changeIndex = currentAction.getChanges().size() - 1;
                        }
                        List<BlockChange> blockChanges = currentAction.getChanges();
                        if (changeIndex >= 0) {
                            BlockChange change = blockChanges.get(changeIndex--);
                            change.getLocation().getBlock().setBlockData(change.getBefore(), false);
                            processedThisTick++;
                            processedTotal++;
                        }
                        if (changeIndex < 0) {
                            undoEntities(currentAction.getEntityChanges(), true);
                            if (!currentAction.getBiomeChanges().isEmpty()) {
                                BiomeCommandUtil.applyHistory(currentAction.getBiomeChanges(), true);
                            }
                            List<BiomeColumnChange> biomeColumnChanges = currentAction.getBiomeColumnChanges();
                            for (int index = biomeColumnChanges.size() - 1; index >= 0; index--) {
                                BiomeColumnChange change = biomeColumnChanges.get(index);
                                change.getWorld().setBiome(change.getX(), change.getZ(), change.getBefore());
                            }
                            refreshBiomeChunks(currentAction);
                            applySelection(playerId, currentAction.getBeforeSelection());
                            currentAction = null;
                        }
                    }
                    if (totalToProcess > 0) {
                        int percent = (int) ((processedTotal * 100L) / totalToProcess);
                        while (percent >= nextProgressReport && nextProgressReport <= 75) {
                            ChatOutput.send(p, org.bukkit.ChatColor.GRAY + "Undo " + nextProgressReport + "% (" + processedTotal + "/" + totalToProcess + " blocks)");
                            nextProgressReport += 25;
                        }
                    }
                    if (currentAction == null && !actionIter.hasNext()) {
                        savePlayer(playerId);
                        ChatOutput.send(p, org.bukkit.ChatColor.GREEN + "Undo completed: " + actions.size() + " action(s), " + processedTotal + " blocks.");
                        cancelSelf();
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Async undo failed: " + ex.getMessage());
                    cancelSelf();
                }
            }

            private void cancelSelf() {
                asyncHistoryTasks.remove(playerId);
                if (holder[0] != null) holder[0].cancel();
            }
        };
        holder[0] = tickScheduler.scheduleRepeating(runnable, 1L, 1L);
        asyncHistoryTasks.put(playerId, holder[0]);

        org.bukkit.entity.Player p = playerLookup.find(playerId);
        if (p != null) {
            ChatOutput.send(p, org.bukkit.ChatColor.GREEN + "Starting chunked undo of " + totalBlocks + " blocks. Progress will be shown every 25%.");
        }
        return actions.size();
    }

    public int redo(Player player, int steps) {
        if (steps <= 0) return 0;
        UUID playerId = player.getUniqueId();
        if (asyncHistoryTasks.containsKey(playerId)) {
            ChatOutput.send(player, org.bukkit.ChatColor.RED + "Already performing an undo/redo. Wait for it to finish.");
            return 0;
        }
        List<EditAction> actions = new java.util.ArrayList<>();
        long totalBlocks = 0L;
        for (int i = 0; i < steps; i++) {
            EditAction action = editHistory.peekRedo(playerId);
            if (action == null) break;
            if (BiomeCommandUtil.assessHistory(
                    action.getBiomeChanges(), action.getBiomeColumnChanges()).hardRejected()) {
                break;
            }
            action = editHistory.popRedo(playerId);
            actions.add(action);
            totalBlocks += action.getChanges().size();
        }
        if (actions.isEmpty()) return 0;
        if (totalBlocks <= taskPolicy.chunkedThreshold()) {
            int redone = 0;
            for (EditAction action : actions) {
                for (BlockChange change : action.getChanges()) {
                    change.getLocation().getBlock().setBlockData(change.getAfter(), false);
                }
                if (!action.getBiomeChanges().isEmpty()) {
                    BiomeCommandUtil.applyHistory(action.getBiomeChanges(), false);
                }
                for (BiomeColumnChange change : action.getBiomeColumnChanges()) {
                    change.getWorld().setBiome(change.getX(), change.getZ(), change.getAfter());
                }
                for (EntityChange entityChange : action.getEntityChanges()) {
                    loadEntityChunk(entityChange);
                    entityChange.redo();
                }
                refreshBiomeChunks(action);
                applySelection(playerId, action.getAfterSelection());
                redone++;
            }
            savePlayer(playerId);
            return redone;
        }

        final long totalToProcess = totalBlocks;
        final TaskHandle[] holder = new TaskHandle[1];
        Runnable runnable = new Runnable() {
            final java.util.Iterator<EditAction> actionIter = actions.iterator();
            EditAction currentAction = null;
            int changeIndex = 0;
            long processedTotal = 0;
            int nextProgressReport = 25;

            @Override
            public void run() {
                org.bukkit.entity.Player p = playerLookup.find(playerId);
                if (p == null || !p.isOnline()) { cancelSelf(); return; }
                long start = System.nanoTime();
                long processedThisTick = 0;
                try {
                    while ((currentAction != null || actionIter.hasNext())
                            && processedThisTick < taskPolicy.blocksPerTick()
                            && System.nanoTime() - start < taskPolicy.timeBudgetNanos()) {
                        if (currentAction == null) {
                            currentAction = actionIter.next();
                            changeIndex = 0;
                        }
                        List<BlockChange> blockChanges = currentAction.getChanges();
                        if (changeIndex < blockChanges.size()) {
                            BlockChange change = blockChanges.get(changeIndex++);
                            change.getLocation().getBlock().setBlockData(change.getAfter(), false);
                            processedThisTick++;
                            processedTotal++;
                        }
                        if (changeIndex >= blockChanges.size()) {
                            if (!currentAction.getBiomeChanges().isEmpty()) BiomeCommandUtil.applyHistory(currentAction.getBiomeChanges(), false);
                            for (BiomeColumnChange change : currentAction.getBiomeColumnChanges()) change.getWorld().setBiome(change.getX(), change.getZ(), change.getAfter());
                            for (EntityChange entityChange : currentAction.getEntityChanges()) { loadEntityChunk(entityChange); entityChange.redo(); }
                            refreshBiomeChunks(currentAction);
                            applySelection(playerId, currentAction.getAfterSelection());
                            currentAction = null;
                        }
                    }
                    if (totalToProcess > 0) {
                        int percent = (int) ((processedTotal * 100L) / totalToProcess);
                        while (percent >= nextProgressReport && nextProgressReport <= 75) {
                            ChatOutput.send(p, org.bukkit.ChatColor.GRAY + "Redo " + nextProgressReport + "% (" + processedTotal + "/" + totalToProcess + " blocks)");
                            nextProgressReport += 25;
                        }
                    }
                    if (currentAction == null && !actionIter.hasNext()) {
                        savePlayer(playerId);
                        ChatOutput.send(p, org.bukkit.ChatColor.GREEN + "Redo completed: " + actions.size() + " action(s), " + processedTotal + " blocks.");
                        cancelSelf();
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Async redo failed: " + ex.getMessage());
                    cancelSelf();
                }
            }
            private void cancelSelf() {
                asyncHistoryTasks.remove(playerId);
                if (holder[0] != null) holder[0].cancel();
            }
        };
        holder[0] = tickScheduler.scheduleRepeating(runnable, 1L, 1L);
        asyncHistoryTasks.put(playerId, holder[0]);

        org.bukkit.entity.Player p = playerLookup.find(playerId);
        if (p != null) ChatOutput.send(p, org.bukkit.ChatColor.GREEN + "Starting chunked redo of " + totalBlocks + " blocks. Progress will be shown every 25%.");
        return actions.size();
    }

    public boolean hasAsyncHistoryTask(UUID playerId) {
        return asyncHistoryTasks.containsKey(playerId);
    }

    public void cancelAllAsyncTasks() {
        int cancelled = 0;
        for (TaskHandle task : asyncHistoryTasks.values()) {
            try { task.cancel(); } catch (Throwable ignored) {}
            cancelled++;
        }
        asyncHistoryTasks.clear();
        if (cancelled > 0) {
            plugin.getLogger().info("[Bayzyl] Cancelled " + cancelled + " async history task(s) on disable.");
        }
        if (evictionTask != null) {
            try { evictionTask.cancel(); } catch (Throwable ignored) {}
            evictionTask = null;
        }
        if (compactionTask != null) {
            try { compactionTask.cancel(); } catch (Throwable ignored) {}
            compactionTask = null;
        }
    }

    /**
     * Run a single in-memory compaction pass across all players' undo/redo
     * stacks. Skips actions that have already been compacted. Cheap to call
     * on demand; safe to repeat. Returns the result tuple from
     * {@link EditHistory#compactAll()}: [actionsCompacted, blocksRewritten,
     * distinctStatesPooled].
     */
    public long[] compactStaleActions() {
        return editHistory.compactAll();
    }

    /**
     * Periodic background compaction that pools duplicate BlockData states
     * across uncompacted actions. Runs every {@code periodMinutes} on the main
     * thread; light enough to be non-disruptive (only walks uncompacted
     * actions). Idempotent — calling twice cancels the prior task and starts
     * fresh.
     */
    public void startCompactionTask(long periodMinutes) {
        if (compactionTask != null) {
            try { compactionTask.cancel(); } catch (Throwable ignored) {}
            compactionTask = null;
        }
        long periodTicks = 20L * 60L * Math.max(1L, periodMinutes);
        compactionTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                long[] result = editHistory.compactAll();
                if (result[0] > 0 && result[1] > 0) {
                    plugin.getLogger().fine("[Bayzyl] Compacted " + result[0] + " action(s), rewrote "
                            + result[1] + " block change(s) using " + result[2] + " distinct state(s).");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("History compaction sweep failed: " + t.getMessage());
            }
        }, periodTicks, periodTicks);
    }

    /**
     * Periodic task that drops in-memory undo/redo entries older than ttlMs.
     * Persistent history (on-disk) is unaffected. Idempotent — calling twice
     * cancels the prior task and starts fresh.
     */
    public void startTtlEvictionTask(long ttlMs) {
        if (evictionTask != null) {
            try { evictionTask.cancel(); } catch (Throwable ignored) {}
            evictionTask = null;
        }
        // Run every minute; cheap walk over the per-player deques.
        long periodTicks = 20L * 60L;
        evictionTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                int dropped = editHistory.evictOlderThan(ttlMs);
                if (dropped > 0) {
                    plugin.getLogger().fine("[Bayzyl] Evicted " + dropped + " stale history action(s) older than " + (ttlMs / 60_000L) + " min.");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("History eviction sweep failed: " + t.getMessage());
            }
        }, periodTicks, periodTicks);
    }

    public void loadPlayer(UUID playerId) {
        PersistentEditHistoryService.LoadedHistory loaded = persistentEditHistoryService.load(playerId);
        editHistory.restore(playerId, loaded.undoActions(), loaded.redoActions());
    }

    public void savePlayer(UUID playerId) {
        persistentEditHistoryService.save(
                playerId,
                editHistory.snapshotUndo(playerId, PERSISTED_HISTORY_LIMIT),
                editHistory.snapshotRedo(playerId, PERSISTED_HISTORY_LIMIT)
        );
    }

    /**
     * Reverts entity changes newest-first. Entities that must be removed (their creation is being undone) go
     * before the block restore so hanging entities do not pop off a vanishing support; entities that must be
     * respawned (their deletion is being undone) go after it so hanging entities have their support block back.
     * Each target chunk is loaded first: {@code Bukkit.getEntity} cannot see entities in unloaded chunks, so
     * a removal there would silently leave the entity behind.
     */
    private void undoEntities(List<EntityChange> entityChanges, boolean respawnPhase) {
        for (int index = entityChanges.size() - 1; index >= 0; index--) {
            EntityChange change = entityChanges.get(index);
            if ((change.getKind() == EntityChange.Kind.DELETE) == respawnPhase) {
                loadEntityChunk(change);
                change.undo();
            }
        }
    }

    private void loadEntityChunk(EntityChange change) {
        org.bukkit.Location location = change.getLocation();
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ);
        }
    }

    private void refreshBiomeChunks(EditAction action) {
        if (action.getBiomeChanges().isEmpty() && action.getBiomeColumnChanges().isEmpty()) {
            return;
        }

        Set<String> seen = new LinkedHashSet<>();
        for (BiomeChange change : action.getBiomeChanges()) {
            World world = change.getLocation().getWorld();
            if (world == null) {
                continue;
            }
            int chunkX = change.getLocation().getBlockX() >> 4;
            int chunkZ = change.getLocation().getBlockZ() >> 4;
            String key = world.getName() + ":" + chunkX + ":" + chunkZ;
            if (seen.add(key)) {
                world.refreshChunk(chunkX, chunkZ);
            }
        }
        for (BiomeColumnChange change : action.getBiomeColumnChanges()) {
            World world = change.getWorld();
            if (world == null) {
                continue;
            }
            int chunkX = change.getX() >> 4;
            int chunkZ = change.getZ() >> 4;
            String key = world.getName() + ":" + chunkX + ":" + chunkZ;
            if (seen.add(key)) {
                world.refreshChunk(chunkX, chunkZ);
            }
        }
    }

    private void applySelection(UUID playerId, SelectionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Selection selection = snapshot.toSelection();
        if (selection == null) {
            selectionManager.clear(playerId);
            return;
        }
        selectionManager.setCuboid(playerId, selection.getPos1(), selection.getPos2());
    }

    @FunctionalInterface
    interface TickScheduler {
        TaskHandle scheduleRepeating(Runnable task, long delayTicks, long periodTicks);
    }

    @FunctionalInterface
    interface TaskHandle {
        void cancel();
    }

    @FunctionalInterface
    interface PlayerLookup {
        Player find(UUID playerId);
    }

    record TaskPolicy(long chunkedThreshold, int blocksPerTick, long timeBudgetNanos) {
    }
}
