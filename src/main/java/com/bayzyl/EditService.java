package com.bayzyl;

import com.bayzyl.edit.EditAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class EditService {
    private static final long RESPONSIVE_COPY_THRESHOLD = 200_000L;
    private static final long MAX_RESPONSIVE_COPY_VOLUME = 20_000_000L;
    private static final int COPY_BLOCKS_PER_TICK = 20_000;
    private static final long COPY_TIME_BUDGET_NANOS = 6_000_000L;
    
    // Async paste constants
    private static final long MAX_ASYNC_PASTE_VOLUME = 10_000_000L; // 10 million blocks max
    private static final int PASTE_BLOCKS_PER_TICK = 10_000; // Conservative for paste
    private static final long PASTE_TIME_BUDGET_NANOS = 4_000_000L; // 4ms max per tick
    private static final int MAX_ENTITIES_PER_PASTE = 1000; // Prevent entity spam
    private static final long ASYNC_PASTE_THRESHOLD = 300_000L; // Use async for pastes >300K blocks (chunking threshold)

    // Safety tiers for paste/cut. Each tier is friendly but firm.
    //   < SOFT_CONFIRM_VOLUME: no prompt
    //   SOFT_CONFIRM_VOLUME .. DANGER_CONFIRM_VOLUME: existing requiresConfirm gate (200k)
    //   DANGER_CONFIRM_VOLUME .. HARD_REFUSE_VOLUME: stronger warning, still overrideable with confirm:true
    //   >= HARD_REFUSE_VOLUME: hard refuse, never run
    private static final long DANGER_CONFIRM_VOLUME = 2_000_000L; // strong "are you sure" tier
    private static final long HARD_REFUSE_VOLUME = MAX_ASYNC_PASTE_VOLUME; // hard floor
    // Chunk-span (horizontal) safety for paste areas. A chunk is 16x16 blocks.
    private static final int PASTE_WARN_CHUNK_SPAN = 1_024;   // ~256x256 blocks: warn (overrideable)
    private static final int PASTE_HARD_CHUNK_SPAN = 16_384;  // ~2048x2048 blocks: refuse outright

    // Memory + reliability guardrails for chunked paste/cut. These are belt-and-braces:
    // even when every other gate has passed, we still refuse to start (or abort mid-run)
    // if running would risk OOM or main-thread stalls. The container should never crash
    // because of a paste — at worst, the paste itself is refused or aborted.
    private static final long PASTE_BYTES_PER_CHANGE_ESTIMATE = 200L; // conservative per-BlockChange heap cost
    private static final double PASTE_MAX_HEAP_FRACTION = 0.40D; // refuse if expected change list > 40% of free heap
    /** Beyond this many in-memory recorded changes, the paste switches to "non-undoable" mode and stops appending. */
    // The in-memory undo cap was removed in favor of the EditHistory TTL eviction
    // (HistoryService.startTtlEvictionTask). Full undo records are now retained for
    // any paste/cut size; stale entries age out after the configured TTL.
    /** If max-heap usage crosses this fraction during a paste, abort the task (defensive). */
    private static final double HEAP_ABORT_FRACTION = 0.92D;

    private final JavaPlugin plugin;
    private final EditAdapter adapter;
    private final ClipboardManager clipboardManager;
    private final HistoryService historyService;
    private final SelectionManager selectionManager;
    private AdminModeService adminModeService;
    private final Map<UUID, NudgeSession> nudgeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> copyTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> asyncPasteTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pasteProgress = new ConcurrentHashMap<>();
    /** Records whatever a running paste/cut already changed, so a cancelled task still leaves an undoable edit. */
    private final Map<UUID, Runnable> partialHistoryFlushers = new ConcurrentHashMap<>();
    private CrashRecoveryService crashRecoveryService;

    public EditService(JavaPlugin plugin, EditAdapter adapter, ClipboardManager clipboardManager, HistoryService historyService, SelectionManager selectionManager) {
        this.plugin = plugin;
        this.adapter = adapter;
        this.clipboardManager = clipboardManager;
        this.historyService = historyService;
        this.selectionManager = selectionManager;
    }

    public void setAdminModeService(AdminModeService adminModeService) {
        this.adminModeService = adminModeService;
    }

    public String getBackendName() {
        return adapter.getName();
    }

    public boolean requiresConfirm(Selection selection, boolean confirm) {
        return adapter.requiresConfirm(selection, confirm);
    }

    public int setBlocks(Player player, Selection selection, BlockDistribution distribution, BlockMask mask, String ifMode) {
        List<BlockChange> changes = adapter.setBlocks(player, selection, distribution, mask, ifMode);
        pushHistory(player.getUniqueId(), changes);
        return changes.size();
    }

    public int replaceBlocks(Player player, Selection selection, BlockMask from, BlockDistribution toDistribution, BlockMask mask) {
        List<BlockChange> changes = adapter.replaceBlocks(player, selection, from, toDistribution, mask);
        pushHistory(player.getUniqueId(), changes);
        return changes.size();
    }

    public Clipboard copySelection(Player player, Selection selection, BlockMask mask) {
        Clipboard clipboard = adapter.copySelection(player, selection, mask);
        clipboardManager.set(player.getUniqueId(), clipboard);
        return clipboard;
    }

    public boolean shouldCopyResponsively(Selection selection) {
        return selection != null && selection.isComplete() && selection.getVolume() > RESPONSIVE_COPY_THRESHOLD;
    }

    public boolean hasCopyTask(UUID playerId) {
        BukkitTask task = copyTasks.get(playerId);
        return task != null && !task.isCancelled();
    }

    public boolean startResponsiveCopy(Player player, Selection selection, BlockMask mask, Consumer<Clipboard> onComplete) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (hasCopyTask(playerId)) {
            ChatOutput.send(player, ChatColor.RED + "A copy is already running. Wait for it to finish before starting another.");
            return false;
        }

        World world = selection.getPos1().getWorld();
        if (world == null) {
            return false;
        }

        long volume = selection.getVolume();
        if (volume > MAX_RESPONSIVE_COPY_VOLUME || volume > Integer.MAX_VALUE) {
            ChatOutput.send(player, ChatColor.RED + "Selection is too large to copy safely right now (" + volume + " blocks, cap "
                    + MAX_RESPONSIVE_COPY_VOLUME + "). Use a smaller selection or schematic workflow.");
            return false;
        }

        int sizeX = selection.getMaxX() - selection.getMinX() + 1;
        int sizeY = selection.getMaxY() - selection.getMinY() + 1;
        int sizeZ = selection.getMaxZ() - selection.getMinZ() + 1;
        Location origin = player.getLocation().getBlock().getLocation();
        ResponsiveCopyTask copyTask = new ResponsiveCopyTask(
                playerId,
                player.getName(),
                world,
                selection.getMinX(),
                selection.getMinY(),
                selection.getMinZ(),
                sizeX,
                sizeY,
                sizeZ,
                (int) volume,
                mask,
                origin,
                onComplete
        );
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, copyTask, 1L, 1L);
        copyTask.setScheduledTask(scheduled);
        copyTasks.put(playerId, scheduled);
        ChatOutput.send(player, ChatColor.WHITE + "Copy queued: " + sizeX + "x" + sizeY + "x" + sizeZ + " (" + volume + " blocks).");
        return true;
    }

    public int cutSelection(Player player, Selection selection, BlockMask mask) {
        return cutSelection(player, selection, mask, false);
    }

    public int cutSelection(Player player, Selection selection, BlockMask mask, boolean confirm) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return 0;
        }
        SafetyVerdict verdict = evaluateCutSafety(selection, confirm);
        if (verdict.refuse()) {
            ChatOutput.send(player, ChatColor.RED + verdict.message());
            return REFUSED;
        }
        long volume = selection.getVolume();
        if (volume >= ASYNC_PASTE_THRESHOLD) {
            // Use async cut to avoid blocking server
            if (hasPasteTask(player.getUniqueId())) {
                ChatOutput.send(player, ChatColor.RED + "Already performing an edit. Wait for it to finish.");
                return 0;
            }
            Clipboard clipboard = adapter.copySelection(player, selection, mask);
            if (clipboard == null) return REFUSED;
            clipboardManager.set(player.getUniqueId(), clipboard);
            // Cut entities synchronously first
            List<EntityChange> entityChanges = EditUtil.cutEntities(player, selection);
            AsyncCutTask task = new AsyncCutTask(player, selection, mask, clipboard, entityChanges);
            BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 1L);
            task.setTask(scheduled);
            asyncPasteTasks.put(player.getUniqueId(), scheduled);
            partialHistoryFlushers.put(player.getUniqueId(), task::flushHistory);
            pasteProgress.put(player.getUniqueId(), 0);
            ChatOutput.send(player, ChatColor.GREEN + "Starting chunked cut of " + volume + " blocks. Progress will be shown every 25%.");
            return DEFERRED;
        }

        Clipboard clipboard = adapter.copySelection(player, selection, mask);
        if (clipboard == null) return REFUSED;
        clipboardManager.set(player.getUniqueId(), clipboard);
        List<EntityChange> entityChanges = EditUtil.cutEntities(player, selection);
        List<BlockChange> changes = adapter.cutSelection(player, selection, mask);
        pushHistory(player.getUniqueId(), changes, entityChanges);
        return changes.size();
    }

    private final class AsyncCutTask implements Runnable {
        private final UUID playerId;
        private final Selection selection;
        private final BlockMask mask;
        private final Clipboard clipboard;
        private final List<EntityChange> entityChanges;
        private BukkitTask task;
        private final int sizeX, sizeY, sizeZ;
        private final int minX, minY, minZ;
        private final long totalBlocks;
        private int currentIndex = 0;
        private int nextProgressReport = 25;
        private final List<BlockChange> changes = Collections.synchronizedList(new ArrayList<>());
        private boolean historyPushed = false;
        private final HistoryService.RecordAttempt historyAttempt = new HistoryService.RecordAttempt();

        AsyncCutTask(Player player, Selection selection, BlockMask mask, Clipboard clipboard, List<EntityChange> entityChanges) {
            this.playerId = player.getUniqueId();
            this.selection = cloneSelection(selection);
            this.mask = mask;
            this.clipboard = clipboard;
            this.entityChanges = entityChanges == null ? List.of() : entityChanges;
            this.minX = selection.getMinX();
            this.minY = selection.getMinY();
            this.minZ = selection.getMinZ();
            this.sizeX = selection.getMaxX() - selection.getMinX() + 1;
            this.sizeY = selection.getMaxY() - selection.getMinY() + 1;
            this.sizeZ = selection.getMaxZ() - selection.getMinZ() + 1;
            this.totalBlocks = (long) sizeX * sizeY * sizeZ;
        }

        void setTask(BukkitTask task) { this.task = task; }

        @Override
        public void run() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancel("Player went offline");
                return;
            }
            long start = System.nanoTime();
            int processed = 0;
            while (currentIndex < totalBlocks && processed < PASTE_BLOCKS_PER_TICK && System.nanoTime() - start < PASTE_TIME_BUDGET_NANOS) {
                int index = currentIndex++;
                int x = index % sizeX;
                int yz = index / sizeX;
                int z = yz % sizeZ;
                int y = yz / sizeZ;
                int worldX = minX + x;
                int worldY = minY + y;
                int worldZ = minZ + z;
                World world = selection.getPos1().getWorld();
                if (world == null) continue;
                // Chunk loaded check
                if (!world.isChunkLoaded(worldX >> 4, worldZ >> 4)) continue;
                try {
                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    if (block == null) continue;
                    if (!mask.matches(block.getType())) continue;
                    BlockData before = block.getBlockData().clone();
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                } catch (Exception ex) {
                    // continue
                }
                processed++;
            }
            if (currentIndex >= totalBlocks) {
                try {
                    flushHistory();
                    ChatOutput.send(player, ChatColor.GREEN + "Cut completed: " + changes.size() + " blocks removed.");
                } finally {
                    cleanup();
                }
            } else if (currentIndex >= totalBlocks * nextProgressReport / 100) {
                int percent = (int) (currentIndex * 100 / totalBlocks);
                ChatOutput.send(player, ChatColor.GRAY + "Cut progress: " + ChatColor.WHITE + percent + "%");
                nextProgressReport += 25;
            }
        }

        private void cancel(String reason) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) ChatOutput.send(player, ChatColor.RED + "Cut cancelled: " + reason);
            try {
                flushPartialHistory(playerId);
            } finally {
                cleanup();
            }
        }

        /** Records the blocks and entities removed so far (once), so a cancelled cut can still be undone. */
        void flushHistory() {
            if (historyPushed) {
                return;
            }
            if (!changes.isEmpty() || !entityChanges.isEmpty()) {
                historyService.recordOnce(playerId, changes, entityChanges, historyAttempt);
            }
            historyPushed = true;
        }

        private void cleanup() {
            asyncPasteTasks.remove(playerId);
            if (historyPushed) partialHistoryFlushers.remove(playerId);
            pasteProgress.remove(playerId);
            if (task != null) task.cancel();
        }
    }

    /**
     * Pastes a clipboard. For large clipboards, the work is chunked across server ticks
     * via {@link #pasteClipboardAsync}; this method returns {@link #DEFERRED} immediately
     * and the player is messaged on completion by the async task itself.
     *
     * @return number of blocks placed for synchronous (small) pastes,
     *         {@link #DEFERRED} if the paste was queued as a chunked async task,
     *         {@link #REFUSED} if a safety check rejected it.
     */
    public int pasteClipboard(Player player, Clipboard clipboard, Location target, int rotation, boolean ignoreAir) {
        return pasteClipboard(player, clipboard, target, rotation, ignoreAir, false);
    }

    public int pasteClipboard(Player player, Clipboard clipboard, Location target,
                              int rotation, boolean ignoreAir, boolean confirm) {
        if (rotation % 90 != 0) {
            ChatOutput.send(player, ChatColor.RED + "Only 90-degree rotations are supported.");
            return REFUSED;
        }
        rotation = normalizeRotation(rotation);
        try {
            long volume = (long) clipboard.getSizeX() * (long) clipboard.getSizeY() * (long) clipboard.getSizeZ();

            boolean isAdmin = adminModeService != null && adminModeService.isActive(player);
            SafetyVerdict verdict = evaluatePasteSafety(clipboard, target, rotation, volume, confirm, isAdmin);
            if (verdict.refuse()) {
                ChatOutput.send(player, ChatColor.RED + verdict.message());
                return REFUSED;
            }
            if (verdict.skipUndo()) {
                ChatOutput.send(player, ChatColor.YELLOW + verdict.message());
            } else if (!verdict.message().isEmpty()) {
                // Informational note (e.g. "12,345 blocks fall above world cap and will be skipped").
                ChatOutput.send(player, ChatColor.YELLOW + verdict.message());
            }

            if (volume >= ASYNC_PASTE_THRESHOLD) {
                // Fire-and-forget chunked paste. Never block the server thread on a latch —
                // the async task itself runs on the main thread (time-sliced), so awaiting
                // it here would self-deadlock and freeze the server. The AsyncPasteTask
                // sends its own "Starting async paste …" + progress + completion messages.
                boolean started = pasteClipboardAsync(player, clipboard, target, rotation, ignoreAir, verdict.skipUndo());
                return started ? DEFERRED : 0;
            }

            // For small pastes, use synchronous version. Small pastes don't trip the
            // memory pre-flight, so skipUndo can't be set on this path.
            return pasteClipboardSafe(player, clipboard, target, rotation, ignoreAir);
        } catch (Throwable t) {
            plugin.getLogger().severe("CRITICAL: Paste crashed: " + t.getMessage());
            t.printStackTrace();

            if (player != null) {
                player.sendMessage(ChatColor.RED + "Paste failed due to server error. "
                                  + "Try using /bayzyl paste async for large pastes.");
            }
            return 0;
        }
    }

    /** Sentinel returned by paste/cut when the operation was queued as a chunked async task. */
    public static final int DEFERRED = -1;
    /** Sentinel returned by paste/cut when a safety check rejected the operation. */
    public static final int REFUSED = -2;

    /**
     * Pre-flight safety verdict for large paste/cut operations.
     * - {@code refuse=true}: hard rejection, do not run.
     * - {@code refuse=false} with a non-empty {@code message}: informational note (e.g. Y-shave).
     * - {@code refuse=false} with empty {@code message}: green light.
     */
    private record SafetyVerdict(boolean refuse, boolean skipUndo, String message) {
        static SafetyVerdict ok() { return new SafetyVerdict(false, false, ""); }
        static SafetyVerdict note(String msg) { return new SafetyVerdict(false, false, msg); }
        static SafetyVerdict proceedNoUndo(String msg) { return new SafetyVerdict(false, true, msg); }
        static SafetyVerdict refuse(String msg) { return new SafetyVerdict(true, false, msg); }
    }

    private static int normalizeRotation(int rotation) {
        return ((rotation % 360) + 360) % 360;
    }

    private SafetyVerdict evaluatePasteSafety(Clipboard clipboard, Location target, int rotation,
                                              long volume, boolean confirm, boolean isAdmin) {
        // Tier 1: hard refuse on absurd volumes — no override.
        if (volume >= HARD_REFUSE_VOLUME) {
            return SafetyVerdict.refuse("That paste is " + volume + " blocks (cap is " + HARD_REFUSE_VOLUME
                    + "). Even chunked, this would risk thrashing the server. Break it into smaller pastes.");
        }

        // Tier 2: danger volume — overrideable with confirm:true.
        if (volume >= DANGER_CONFIRM_VOLUME && !confirm) {
            return SafetyVerdict.refuse("Heads up: that's a very large paste (" + volume + " blocks). "
                    + "Re-run with confirm:true if you really want to do this — it'll run chunked but may "
                    + "still slow the server while it's running.");
        }

        // Memory pre-flight: refuse if recording every change to in-memory undo history
        // would consume more than PASTE_MAX_HEAP_FRACTION of currently free heap.
        long expectedBytes = volume * PASTE_BYTES_PER_CHANGE_ESTIMATE;
        Runtime rt = Runtime.getRuntime();
        long freeNow = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
        if (expectedBytes > freeNow * PASTE_MAX_HEAP_FRACTION) {
            long needMb = expectedBytes / 1_000_000L;
            long freeMb = freeNow / 1_000_000L;
            if (!isAdmin) {
                return SafetyVerdict.refuse("Server memory headroom is too low to safely record this paste's "
                        + "undo data (need ~" + needMb + "MB, have ~" + freeMb + "MB free). Try again after "
                        + "fewer pastes in this session, or break this into smaller chunks.");
            }
            if (!confirm) {
                return SafetyVerdict.refuse("Server memory headroom is too low to record undo for this paste "
                        + "(need ~" + needMb + "MB, have ~" + freeMb + "MB free). "
                        + "Admin override available: re-run with confirm:true to paste anyway. "
                        + "WARNING: undo will not be recorded — server safety is prioritized over preserving paste undo.");
            }
            return SafetyVerdict.proceedNoUndo("Admin override: pasting without undo. The world will be modified, "
                    + "but you cannot /undo this paste.");
        }

        // Entity safety — hard refuse if the clipboard has too many.
        int entityCount = clipboard.getEntities() == null ? 0 : clipboard.getEntities().size();
        if (entityCount > MAX_ENTITIES_PER_PASTE) {
            return SafetyVerdict.refuse("That clipboard has " + entityCount + " entities (cap is "
                    + MAX_ENTITIES_PER_PASTE + "). Strip some entities and try again.");
        }

        World world = target.getWorld();
        if (world == null) {
            return SafetyVerdict.refuse("Target world is missing.");
        }

        // Horizontal chunk-span safety. After rotation, X and Z extents may swap; account for that.
        int sizeX = clipboard.getSizeX();
        int sizeZ = clipboard.getSizeZ();
        int extentX = (rotation % 180 == 0) ? sizeX : sizeZ;
        int extentZ = (rotation % 180 == 0) ? sizeZ : sizeX;
        int chunkSpan = ((extentX + 15) >> 4) * ((extentZ + 15) >> 4);
        if (chunkSpan >= PASTE_HARD_CHUNK_SPAN) {
            return SafetyVerdict.refuse("That paste covers " + chunkSpan
                    + " chunks horizontally (cap is " + PASTE_HARD_CHUNK_SPAN
                    + "). That'd force-load too much of the world at once.");
        }
        if (chunkSpan >= PASTE_WARN_CHUNK_SPAN && !confirm) {
            return SafetyVerdict.refuse("That paste covers " + chunkSpan
                    + " chunks horizontally. Re-run with confirm:true if you want to load that much terrain.");
        }

        // Y-bounds shave: how many blocks of the clipboard fall outside the world's Y range?
        // We can't undo the bounds, but we can warn the user up front; AsyncPasteTask
        // already skips per-block out-of-bounds writes (no error, just a no-op).
        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight() - 1; // getMaxHeight is exclusive
        int pasteMinY = target.getBlockY() + clipboard.getMinOffsetY();
        int pasteMaxY = pasteMinY + clipboard.getSizeY() - 1;
        if (pasteMaxY < worldMinY || pasteMinY > worldMaxY) {
            return SafetyVerdict.refuse("Nothing to paste: the entire clipboard would land outside the world's "
                    + "Y range (" + worldMinY + ".." + worldMaxY + "). Move closer to a valid Y and retry.");
        }
        long shavedLayers = 0;
        if (pasteMinY < worldMinY) shavedLayers += (worldMinY - pasteMinY);
        if (pasteMaxY > worldMaxY) shavedLayers += (pasteMaxY - worldMaxY);
        if (shavedLayers > 0) {
            long shavedBlocks = shavedLayers * (long) sizeX * (long) sizeZ;
            return SafetyVerdict.note("Note: " + shavedBlocks + " blocks of this paste are outside the world's "
                    + "Y range (" + worldMinY + ".." + worldMaxY + ") and will be skipped automatically.");
        }

        return SafetyVerdict.ok();
    }

    private SafetyVerdict evaluateCutSafety(Selection selection, boolean confirm) {
        if (selection == null || !selection.isComplete()) return SafetyVerdict.ok();
        long volume = selection.getVolume();
        if (volume >= HARD_REFUSE_VOLUME) {
            return SafetyVerdict.refuse("That cut is " + volume + " blocks (cap is " + HARD_REFUSE_VOLUME
                    + "). Even chunked, this would risk thrashing the server. Cut a smaller selection.");
        }
        if (volume >= DANGER_CONFIRM_VOLUME && !confirm) {
            return SafetyVerdict.refuse("Heads up: that's a very large cut (" + volume + " blocks). "
                    + "Re-run with confirm:true if you really want to do this.");
        }
        return SafetyVerdict.ok();
    }
    
    private int pasteClipboardSafe(Player player, Clipboard clipboard, Location target,
                                  int rotation, boolean ignoreAir) {
        List<BlockChange> changes = adapter.pasteClipboard(player, clipboard, target, rotation, ignoreAir);
        List<EntityChange> entityChanges = EditUtil.pasteEntities(player, clipboard, target, rotation);
        pushHistory(player.getUniqueId(), changes, entityChanges);
        return changes.size();
    }
    
    public boolean pasteClipboardAsync(Player player, Clipboard clipboard, Location target,
                                     int rotation, boolean ignoreAir, boolean skipUndo) {
        return pasteClipboardAsync(player, clipboard, target, rotation, ignoreAir, skipUndo, count -> {
            plugin.getLogger().info("Async paste completed: " + count + " blocks for " + player.getName()
                    + (skipUndo ? " (no undo recorded)" : ""));
        });
    }

    public boolean pasteClipboardAsync(Player player, Clipboard clipboard, Location target,
                                     int rotation, boolean ignoreAir, boolean skipUndo, Consumer<Integer> onComplete) {
        if (clipboard == null) {
            ChatOutput.send(player, ChatColor.RED + "Clipboard is empty.");
            return false;
        }
        if (rotation % 90 != 0) {
            ChatOutput.send(player, ChatColor.RED + "Only 90-degree rotations are supported.");
            return false;
        }
        // The task rotates block positions with the raw angle but block states with a normalized one,
        // so -90 or 450 would turn the states and leave the layout unrotated.
        rotation = normalizeRotation(rotation);

        UUID playerId = player.getUniqueId();
        if (hasPasteTask(playerId)) {
            ChatOutput.send(player, ChatColor.RED + "Already pasting. Wait for current paste to finish.");
            return false;
        }

        long totalBlocks = (long) clipboard.getSizeX() * (long) clipboard.getSizeY() * (long) clipboard.getSizeZ();

        // Safety checks
        if (totalBlocks > MAX_ASYNC_PASTE_VOLUME) {
            ChatOutput.send(player, ChatColor.RED + "Clipboard too large (" + totalBlocks +
                           " blocks). Maximum for async paste is " + MAX_ASYNC_PASTE_VOLUME + " blocks.");
            return false;
        }

        if (clipboard.getEntities().size() > MAX_ENTITIES_PER_PASTE) {
            ChatOutput.send(player, ChatColor.RED + "Too many entities (" +
                           clipboard.getEntities().size() + "). Maximum is " + MAX_ENTITIES_PER_PASTE + ".");
            return false;
        }

        // Pre-validate target area chunks
        World world = target.getWorld();
        if (!validatePasteArea(world, clipboard, target, rotation)) {
            ChatOutput.send(player, ChatColor.RED + "Cannot paste in this area. Some chunks are invalid or unloaded.");
            return false;
        }

        // Start async paste task
        AsyncPasteTask task = new AsyncPasteTask(player, clipboard, target, rotation, ignoreAir, skipUndo, onComplete);
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 1L);
        task.setTask(scheduled);
        asyncPasteTasks.put(playerId, scheduled);
        partialHistoryFlushers.put(playerId, task::flushHistory);
        pasteProgress.put(playerId, 0);

        ChatOutput.send(player, ChatColor.GREEN + "Starting async paste of " + totalBlocks +
                       " blocks. Progress will be shown every 25%."
                       + (skipUndo ? " (no undo will be recorded)" : ""));

        return true;
    }

    public boolean pasteClipboardAsync(Player player, Clipboard clipboard, Location target,
                                     int rotation, boolean ignoreAir, Consumer<Integer> onComplete) {
        return pasteClipboardAsync(player, clipboard, target, rotation, ignoreAir, false, onComplete);
    }

    public boolean pasteClipboardAsync(Player player, Clipboard clipboard, Location target,
                                     int rotation, boolean ignoreAir) {
        return pasteClipboardAsync(player, clipboard, target, rotation, ignoreAir, false, count -> {
            plugin.getLogger().info("Async paste completed: " + count + " blocks for " + player.getName());
        });
    }
    
    public boolean hasPasteTask(UUID playerId) {
        return asyncPasteTasks.containsKey(playerId) || partialHistoryFlushers.containsKey(playerId);
    }
    
    public void cancelPasteTask(UUID playerId) {
        BukkitTask task = asyncPasteTasks.remove(playerId);
        try {
            if (task != null) task.cancel();
        } finally {
            pasteProgress.remove(playerId);
            flushPartialHistory(playerId);
        }
    }

    private void flushPartialHistory(UUID playerId) {
        Runnable flusher = partialHistoryFlushers.get(playerId);
        if (flusher != null) {
            // Keep the flusher available if recording fails, and propagate the failure to shutdown.
            flusher.run();
            partialHistoryFlushers.remove(playerId, flusher);
        }
    }

    /** Stops every task and records partial edits before shutdown persists player history. */
    public void cancelAllAsyncTasks() {
        RuntimeException failure = null;
        java.util.Set<UUID> players = new java.util.HashSet<>(asyncPasteTasks.keySet());
        players.addAll(partialHistoryFlushers.keySet());
        for (UUID playerId : players) {
            try {
                cancelPasteTask(playerId);
            } catch (RuntimeException ex) {
                if (failure == null) failure = new IllegalStateException("Could not finish cancelled edit history", ex);
                else failure.addSuppressed(ex);
            }
        }
        for (BukkitTask task : copyTasks.values()) {
            try {
                task.cancel();
            } catch (RuntimeException ex) {
                if (failure == null) failure = new IllegalStateException("Could not stop copy task", ex);
                else failure.addSuppressed(ex);
            }
        }
        copyTasks.clear();
        if (failure != null) throw failure;
    }

    private boolean validatePasteArea(World world, Clipboard clipboard, Location target, int rotation) {
        // Per-block chunk loading is now handled lazily on the main thread inside
        // AsyncPasteTask.processBlock. The previous pre-validation here ignored
        // clipboard.getMinOffsetX/Z and rotation, so it was checking the wrong region
        // (and silently rejecting valid pastes whose actual chunks were unloaded).
        return world != null;
    }

    public int stackSelection(Player player, Selection selection, int count, int[] direction, boolean ignoreAir) {
        return stackSelection(player, selection, count, direction, ignoreAir, null, null);
    }

    public int stackSelection(Player player, Selection selection, int count, int[] direction, boolean ignoreAir,
                              SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        if (selection == null || !selection.isComplete() || count <= 0) {
            return 0;
        }

        Clipboard clipboard = adapter.copySelection(player, selection, BlockMask.parse(null));
        if (clipboard == null) {
            return 0;
        }

        int stepX = direction[0] * clipboard.getSizeX();
        int stepY = direction[1] * clipboard.getSizeY();
        int stepZ = direction[2] * clipboard.getSizeZ();

        List<BlockChange> allChanges = new ArrayList<>();
        List<EntityChange> entityChanges = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Location origin = clipboard.getOrigin().clone().add(stepX * i, stepY * i, stepZ * i);
            allChanges.addAll(adapter.pasteClipboard(player, clipboard, origin, 0, ignoreAir));
            entityChanges.addAll(EditUtil.pasteEntities(player, clipboard, origin, 0));
        }
        pushHistory(player.getUniqueId(), allChanges, entityChanges, beforeSelection, afterSelection);
        return allChanges.size();
    }

    public int stackSelectionRandom(Player player, Selection selection, int count, int spreadX, int spreadY, int spreadZ, boolean ignoreAir) {
        return stackSelectionRandom(player, selection, count, spreadX, spreadY, spreadZ, ignoreAir, null, null);
    }

    public int stackSelectionRandom(Player player, Selection selection, int count, int spreadX, int spreadY, int spreadZ, boolean ignoreAir,
                                    SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        if (selection == null || !selection.isComplete() || count <= 0) {
            return 0;
        }

        Clipboard clipboard = adapter.copySelection(player, selection, BlockMask.parse(null));
        if (clipboard == null) {
            return 0;
        }

        int baseX = Math.max(1, clipboard.getSizeX());
        int baseY = Math.max(1, clipboard.getSizeY());
        int baseZ = Math.max(1, clipboard.getSizeZ());

        List<BlockChange> allChanges = new ArrayList<>();
        List<EntityChange> entityChanges = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            int offsetX = randomOffset(random, spreadX) * baseX;
            int offsetY = randomOffset(random, spreadY) * baseY;
            int offsetZ = randomOffset(random, spreadZ) * baseZ;
            if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
                offsetX = baseX;
            }
            Location origin = clipboard.getOrigin().clone().add(offsetX, offsetY, offsetZ);
            allChanges.addAll(adapter.pasteClipboard(player, clipboard, origin, 0, ignoreAir));
            entityChanges.addAll(EditUtil.pasteEntities(player, clipboard, origin, 0));
        }
        pushHistory(player.getUniqueId(), allChanges, entityChanges, beforeSelection, afterSelection);
        return allChanges.size();
    }

    public int moveSelection(Player player, Selection selection, int distance, int[] direction, boolean ignoreAir) {
        if (selection == null || !selection.isComplete() || distance <= 0) {
            return 0;
        }
        SelectionSnapshot beforeSelection = SelectionSnapshot.from(selection);

        Clipboard clipboard = adapter.copySelection(player, selection, BlockMask.parse(null));
        if (clipboard == null || clipboard.getOrigin() == null) {
            return 0;
        }
        clipboardManager.set(player.getUniqueId(), clipboard);

        List<EntityChange> cutEntityChanges = EditUtil.cutEntities(player, selection);
        List<BlockChange> cutChanges = adapter.cutSelection(player, selection, BlockMask.parse(null));

        Location targetOrigin = clipboard.getOrigin().clone().add(
                direction[0] * distance,
                direction[1] * distance,
                direction[2] * distance
        );
        List<BlockChange> pasteChanges = adapter.pasteClipboard(player, clipboard, targetOrigin, 0, ignoreAir);
        List<EntityChange> pasteEntityChanges = EditUtil.pasteEntities(player, clipboard, targetOrigin, 0);

        List<BlockChange> allChanges = new ArrayList<>(cutChanges.size() + pasteChanges.size());
        allChanges.addAll(cutChanges);
        allChanges.addAll(pasteChanges);

        List<EntityChange> allEntityChanges = new ArrayList<>(cutEntityChanges.size() + pasteEntityChanges.size());
        allEntityChanges.addAll(cutEntityChanges);
        allEntityChanges.addAll(pasteEntityChanges);

        Selection movedSelection = shiftSelection(selection, direction[0] * distance, direction[1] * distance, direction[2] * distance);
        selectionManager.setCuboid(player.getUniqueId(), movedSelection.getPos1(), movedSelection.getPos2());
        SelectionSnapshot afterSelection = SelectionSnapshot.from(movedSelection);

        pushHistory(player.getUniqueId(), allChanges, allEntityChanges, beforeSelection, afterSelection);
        return allChanges.size();
    }

    public int nudgeSelection(Player player, Selection selection, int distance, int[] direction) {
        if (selection == null || !selection.isComplete() || distance <= 0) {
            return 0;
        }
        UUID playerId = player.getUniqueId();
        NudgeSession session = nudgeSessions.get(playerId);
        if (session == null || !sameSelection(session.selection(), selection)) {
            Clipboard copied = adapter.copySelection(player, selection, BlockMask.parse(null));
            if (copied == null) {
                return 0;
            }
            Clipboard anchored = EditUtil.reanchorClipboard(copied, getSelectionMinLocation(selection));
            session = new NudgeSession(anchored, cloneSelection(selection), new ConcurrentHashMap<>());
            nudgeSessions.put(playerId, session);
            if (crashRecoveryService != null) {
                if (crashRecoveryService != null) {
                crashRecoveryService.saveNudgeSession(playerId, session);
            }
            }
        }

        Selection targetSelection = shiftSelection(selection, direction[0] * distance, direction[1] * distance, direction[2] * distance);
        Map<BlockPos, org.bukkit.block.data.BlockData> affectedBefore = new LinkedHashMap<>();
        Map<BlockPos, org.bukkit.Location> affectedLocations = new LinkedHashMap<>();
        List<EntityChange> entityChanges = new ArrayList<>();

        WorldContext world = new WorldContext(selection.getPos1().getWorld());
        if (!world.valid()) {
            return 0;
        }

        Map<BlockPos, Boolean> oldPositions = buildPositionMap(selection);
        Map<BlockPos, Boolean> newPositions = buildPositionMap(targetSelection);
        Location targetEntityOrigin = getSelectionMinLocation(targetSelection);
        entityChanges.addAll(EditUtil.cutEntities(player, selection));

        for (BlockPos position : oldPositions.keySet()) {
            if (newPositions.containsKey(position)) {
                continue;
            }
            org.bukkit.block.Block block = world.blockAt(position);
            rememberBefore(block, position, affectedBefore, affectedLocations);
            org.bukkit.block.data.BlockData restore = session.background().remove(position);
            block.setBlockData((restore == null ? Material.AIR.createBlockData() : restore.clone()), false);
        }

        Clipboard clipboard = session.clipboard();
        org.bukkit.Location targetOrigin = getSelectionMinLocation(targetSelection);
        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = 0; z < clipboard.getSizeZ(); z++) {
                for (int x = 0; x < clipboard.getSizeX(); x++) {
                    org.bukkit.block.data.BlockData originalData = clipboard.get(x, y, z);
                    if (originalData == null) {
                        continue;
                    }
                    int worldX = targetOrigin.getBlockX() + clipboard.getMinOffsetX() + x;
                    int worldY = targetOrigin.getBlockY() + clipboard.getMinOffsetY() + y;
                    int worldZ = targetOrigin.getBlockZ() + clipboard.getMinOffsetZ() + z;
                    BlockPos position = new BlockPos(worldX, worldY, worldZ);
                    org.bukkit.block.Block block = world.world().getBlockAt(worldX, worldY, worldZ);
                    if (!oldPositions.containsKey(position)) {
                        session.background().putIfAbsent(position, block.getBlockData().clone());
                    }
                    rememberBefore(block, position, affectedBefore, affectedLocations);
                    org.bukkit.block.data.BlockData placedData = originalData.clone();
                    block.setBlockData(placedData, false);
                    org.bukkit.block.BlockState state = clipboard.getState(x, y, z);
                    if (state != null) {
                        org.bukkit.block.BlockState placed = state.copy(block.getLocation());
                        placed.setBlockData(placedData.clone());
                        placed.update(true, false);
                    }
                }
            }
        }

        List<BlockChange> changes = new ArrayList<>();
        for (Map.Entry<BlockPos, org.bukkit.block.data.BlockData> entry : affectedBefore.entrySet()) {
            org.bukkit.Location location = affectedLocations.get(entry.getKey());
            org.bukkit.block.data.BlockData before = entry.getValue();
            org.bukkit.block.data.BlockData after = location.getBlock().getBlockData().clone();
            if (!before.matches(after)) {
                changes.add(new BlockChange(location, before, after));
            }
        }

        entityChanges.addAll(EditUtil.pasteEntities(player, session.clipboard(), targetEntityOrigin, 0));

        SelectionSnapshot beforeSelection = SelectionSnapshot.from(selection);
        selectionManager.setCuboid(playerId, targetSelection.getPos1(), targetSelection.getPos2());
        SelectionSnapshot afterSelection = SelectionSnapshot.from(targetSelection);
        nudgeSessions.put(playerId, new NudgeSession(clipboard, cloneSelection(targetSelection), session.background()));
        pushHistory(playerId, changes, entityChanges, beforeSelection, afterSelection);
        return changes.size();
    }

    public int rotateSelectionLive(Player player, Selection selection, int rotation) {
        if (selection == null || !selection.isComplete()) {
            return 0;
        }
        SelectionSnapshot beforeSelection = SelectionSnapshot.from(selection);
        Clipboard clipboard = adapter.copySelection(player, selection, BlockMask.parse(null));
        if (clipboard == null) {
            return 0;
        }

        Location centerOrigin = getSelectionCenterBlock(selection);
        Clipboard centered = EditUtil.reanchorClipboard(clipboard, centerOrigin);
        Clipboard rotated = ClipboardTransforms.rotateY(centered, rotation);
        clipboardManager.set(player.getUniqueId(), rotated);

        List<EntityChange> cutEntityChanges = EditUtil.cutEntities(player, selection);
        List<BlockChange> cutChanges = adapter.cutSelection(player, selection, BlockMask.parse(null));
        List<BlockChange> pasteChanges = adapter.pasteClipboard(player, rotated, centerOrigin, 0, false);
        List<EntityChange> pasteEntityChanges = EditUtil.pasteEntities(player, rotated, centerOrigin, 0);

        List<BlockChange> allChanges = new ArrayList<>(cutChanges.size() + pasteChanges.size());
        allChanges.addAll(cutChanges);
        allChanges.addAll(pasteChanges);

        List<EntityChange> allEntityChanges = new ArrayList<>(cutEntityChanges.size() + pasteEntityChanges.size());
        allEntityChanges.addAll(cutEntityChanges);
        allEntityChanges.addAll(pasteEntityChanges);

        Selection rotatedSelection = EditUtil.getPasteSelection(rotated, centerOrigin, 0);
        if (rotatedSelection != null) {
            selectionManager.setCuboid(player.getUniqueId(), rotatedSelection.getPos1(), rotatedSelection.getPos2());
        }
        SelectionSnapshot afterSelection = SelectionSnapshot.from(selectionManager.get(player.getUniqueId()));
        pushHistory(player.getUniqueId(), allChanges, allEntityChanges, beforeSelection, afterSelection);
        return allChanges.size();
    }

    public int makeWalls(Player player, Selection selection, BlockDistribution distribution, BlockMask mask) {
        List<BlockChange> changes = adapter.makeWalls(player, selection, distribution, mask);
        pushHistory(player.getUniqueId(), changes);
        return changes.size();
    }

    public int overlaySelection(Player player, Selection selection, Material material, BlockMask mask) {
        List<BlockChange> changes = adapter.overlaySelection(player, selection, material, mask);
        pushHistory(player.getUniqueId(), changes);
        return changes.size();
    }

    public int smoothSelection(Player player, Selection selection, int iterations) {
        List<BlockChange> changes = adapter.smoothSelection(player, selection, iterations);
        pushHistory(player.getUniqueId(), changes);
        return changes.size();
    }

    private void pushHistory(UUID playerId, List<BlockChange> changes) {
        pushHistory(playerId, changes, List.of());
    }

    private void pushHistory(UUID playerId, List<BlockChange> changes, List<EntityChange> entityChanges) {
        pushHistory(playerId, changes, entityChanges, null, null);
    }

    private void pushHistory(UUID playerId, List<BlockChange> changes, List<EntityChange> entityChanges,
                             SelectionSnapshot beforeSelection, SelectionSnapshot afterSelection) {
        historyService.record(playerId, changes, entityChanges, beforeSelection, afterSelection);
    }

    public void clearNudgeSession(UUID playerId) {
        nudgeSessions.remove(playerId);
        if (crashRecoveryService != null) {
            if (crashRecoveryService != null) {
            crashRecoveryService.saveNudgeSession(playerId, null);
        }
        }
    }
    
    /**
     * Connect to crash recovery service for persistence across restarts.
     * Should be called during plugin initialization.
     */
    public void setCrashRecoveryService(CrashRecoveryService service) {
        this.crashRecoveryService = service;
        // Load any persisted nudge sessions
        if (service != null) {
            for (UUID playerId : nudgeSessions.keySet()) {
                NudgeSession persisted = service.loadNudgeSession(playerId);
                if (persisted != null) {
                    nudgeSessions.put(playerId, persisted);
                }
            }
        }
    }

    private Selection shiftSelection(Selection selection, int dx, int dy, int dz) {
        return new Selection(
                selection.getPos1().clone().add(dx, dy, dz),
                selection.getPos2().clone().add(dx, dy, dz),
                selection.getType()
        );
    }

    private int randomOffset(ThreadLocalRandom random, int spread) {
        if (spread <= 0) {
            return 0;
        }
        return random.nextInt(-spread, spread + 1);
    }

    private Location getSelectionCenterBlock(Selection selection) {
        int centerX = (selection.getMinX() + selection.getMaxX()) / 2;
        int centerY = (selection.getMinY() + selection.getMaxY()) / 2;
        int centerZ = (selection.getMinZ() + selection.getMaxZ()) / 2;
        return new Location(selection.getPos1().getWorld(), centerX, centerY, centerZ);
    }

    private Location getSelectionMinLocation(Selection selection) {
        return new Location(selection.getPos1().getWorld(), selection.getMinX(), selection.getMinY(), selection.getMinZ());
    }

    private boolean sameSelection(Selection a, Selection b) {
        if (a == null || b == null || !a.isComplete() || !b.isComplete()) {
            return false;
        }
        return a.getPos1().getWorld().equals(b.getPos1().getWorld())
                && a.getMinX() == b.getMinX()
                && a.getMinY() == b.getMinY()
                && a.getMinZ() == b.getMinZ()
                && a.getMaxX() == b.getMaxX()
                && a.getMaxY() == b.getMaxY()
                && a.getMaxZ() == b.getMaxZ();
    }

    private Selection cloneSelection(Selection selection) {
        return new Selection(selection.getPos1().clone(), selection.getPos2().clone(), selection.getType());
    }

    private Map<BlockPos, Boolean> buildPositionMap(Selection selection) {
        Map<BlockPos, Boolean> positions = new LinkedHashMap<>();
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    positions.put(new BlockPos(x, y, z), Boolean.TRUE);
                }
            }
        }
        return positions;
    }

    private void rememberBefore(org.bukkit.block.Block block,
                                BlockPos position,
                                Map<BlockPos, org.bukkit.block.data.BlockData> affectedBefore,
                                Map<BlockPos, org.bukkit.Location> affectedLocations) {
        affectedBefore.putIfAbsent(position, block.getBlockData().clone());
        affectedLocations.putIfAbsent(position, block.getLocation());
    }

    private final class ResponsiveCopyTask implements Runnable {
        private final UUID playerId;
        private final String playerName;
        private final World world;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int volume;
        private final BlockMask mask;
        private final Location origin;
        private final Selection selectionSnapshot;
        private final Consumer<Clipboard> onComplete;
        private final BlockData airData = Material.AIR.createBlockData();
        private final BlockData[] data;
        private final BlockState[] states;
        private BukkitTask scheduledTask;
        private int index;
        private int nextProgressPercent = 25;

        private ResponsiveCopyTask(UUID playerId,
                                   String playerName,
                                   World world,
                                   int minX,
                                   int minY,
                                   int minZ,
                                   int sizeX,
                                   int sizeY,
                                   int sizeZ,
                                   int volume,
                                   BlockMask mask,
                                   Location origin,
                                   Consumer<Clipboard> onComplete) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.world = world;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.volume = volume;
            this.mask = mask;
            this.origin = origin.clone();
            this.selectionSnapshot = new Selection(
                    new Location(world, minX, minY, minZ),
                    new Location(world, minX + sizeX - 1, minY + sizeY - 1, minZ + sizeZ - 1),
                    SelectionType.CUBOID
            );
            this.onComplete = onComplete;
            this.data = new BlockData[volume];
            this.states = new BlockState[volume];
        }

        private void setScheduledTask(BukkitTask scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public void run() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancel();
                return;
            }

            long started = System.nanoTime();
            int processedThisTick = 0;
            while (index < volume
                    && processedThisTick < COPY_BLOCKS_PER_TICK
                    && System.nanoTime() - started < COPY_TIME_BUDGET_NANOS) {
                copyIndex(index);
                index++;
                processedThisTick++;
            }

            sendProgress(player);
            if (index >= volume) {
                finish(player);
            }
        }

        private void copyIndex(int currentIndex) {
            int x = currentIndex % sizeX;
            int yz = currentIndex / sizeX;
            int z = yz % sizeZ;
            int y = yz / sizeZ;
            Block block = world.getBlockAt(minX + x, minY + y, minZ + z);
            if (!mask.matches(block.getType())) {
                data[currentIndex] = airData;
                states[currentIndex] = null;
                return;
            }
            data[currentIndex] = block.getBlockData().clone();
            BlockState state = block.getState();
            states[currentIndex] = state instanceof TileState ? state.copy() : null;
        }

        private void sendProgress(Player player) {
            int percent = (int) Math.floor(index * 100.0D / Math.max(1, volume));
            if (percent < nextProgressPercent || percent >= 100) {
                return;
            }
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Copy progress: " + ChatColor.WHITE + percent + "%");
            nextProgressPercent += 25;
        }

        private void finish(Player player) {
            List<ClipboardEntity> entities = new ArrayList<>();
            for (var entity : EditUtil.collectSelectionEntities(selectionSnapshot)) {
                entities.add(ClipboardEntity.from(entity, origin));
            }
            int minOffsetX = minX - origin.getBlockX();
            int minOffsetY = minY - origin.getBlockY();
            int minOffsetZ = minZ - origin.getBlockZ();
            Clipboard clipboard = new Clipboard(sizeX, sizeY, sizeZ, data, states, entities, origin, minOffsetX, minOffsetY, minOffsetZ);
            clipboardManager.set(playerId, clipboard);
            copyTasks.remove(playerId);
            cancelTaskOnly();
            onComplete.accept(clipboard);
        }

        private void cancel() {
            copyTasks.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                ChatOutput.send(player, ChatColor.RED + "Copy cancelled: " + playerName + " is no longer online.");
            }
            cancelTaskOnly();
        }

        private void cancelTaskOnly() {
            if (scheduledTask != null) {
                scheduledTask.cancel();
            }
        }
    }

    private final class AsyncPasteTask implements Runnable {
        private final UUID playerId;
        private final Clipboard clipboard;
        private final Location target;
        private final int rotation;
        private final boolean ignoreAir;
        private final boolean recordHistory;
        private final World world;
        private final int sizeX, sizeY, sizeZ;
        private final long totalBlocks;
        
        private BukkitTask task;
        private int currentIndex = 0;
        private int nextProgressReport = 25;
        private final List<BlockChange> changes = Collections.synchronizedList(new ArrayList<>());
        private boolean entitiesPasted = false;
        private List<EntityChange> pastedEntityChanges = List.of();
        private boolean historyPushed = false;
        private final HistoryService.RecordAttempt historyAttempt = new HistoryService.RecordAttempt();
        private int skippedOutOfBounds = 0;
        private int skippedChunkLoad = 0;
        private int placedCount = 0; // actual block.setBlockData() successes; distinct from changes.size()
        private boolean aborted = false;
        // Retained for binary stability; no longer set under the cap-removed history model.
        private boolean changeRecordingExceeded = false;
        // Cache the last chunk we touched so we only call ensureChunkLoaded on chunk transitions.
        private int lastChunkX = Integer.MIN_VALUE;
        private int lastChunkZ = Integer.MIN_VALUE;

        private final Consumer<Integer> onComplete;
        
        AsyncPasteTask(Player player, Clipboard clipboard, Location target,
                       int rotation, boolean ignoreAir, Consumer<Integer> onComplete) {
            this(player, clipboard, target, rotation, ignoreAir, false, onComplete);
        }

        AsyncPasteTask(Player player, Clipboard clipboard, Location target,
                       int rotation, boolean ignoreAir, boolean skipUndo, Consumer<Integer> onComplete) {
            this.playerId = player.getUniqueId();
            this.clipboard = clipboard;
            this.target = target.clone();
            this.rotation = rotation;
            this.ignoreAir = ignoreAir;
            this.recordHistory = !skipUndo;
            this.world = target.getWorld();
            this.sizeX = clipboard.getSizeX();
            this.sizeY = clipboard.getSizeY();
            this.sizeZ = clipboard.getSizeZ();
            this.totalBlocks = (long) sizeX * sizeY * sizeZ;
            this.onComplete = onComplete;
        }
        
        void setTask(BukkitTask task) {
            this.task = task;
        }
        
        @Override
        public void run() {
            // Top-level guard: any uncaught exception inside a single tick of this task
            // must not propagate up to the Bukkit scheduler — that's what kills the
            // server. Catch everything, log, cancel cleanly, never re-throw.
            try {
                tick();
            } catch (Throwable t) {
                plugin.getLogger().severe("AsyncPasteTask crashed mid-tick at index "
                        + currentIndex + "/" + totalBlocks + ": " + t.getClass().getSimpleName()
                        + ": " + t.getMessage());
                t.printStackTrace();
                Player offender = Bukkit.getPlayer(playerId);
                if (offender != null && offender.isOnline()) {
                    ChatOutput.send(offender, ChatColor.RED + "Paste aborted due to an internal error. "
                            + "Server is fine; this paste was stopped to protect it.");
                }
                aborted = true;
                try {
                    flushPartialHistory(playerId);
                } catch (RuntimeException failure) {
                    plugin.getLogger().severe("Undo recording is still pending for a stopped paste: " + failure.getClass().getSimpleName());
                } finally {
                    cleanup();
                }
            }
        }

        private void tick() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cancel("Player went offline");
                return;
            }

            // Heap pressure abort: if the JVM is sitting above HEAP_ABORT_FRACTION of
            // its max, a long paste is more likely to OOM than to finish. Bail out
            // before that happens. The visible blocks placed so far stay; the rest
            // of the paste is skipped and the player is told why.
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            if (used > rt.maxMemory() * HEAP_ABORT_FRACTION) {
                ChatOutput.send(player, ChatColor.RED + "Paste aborted: server memory pressure too high "
                        + "(" + (used * 100L / rt.maxMemory()) + "% of max heap used). "
                        + "Placed " + placedCount + " blocks before stopping; the rest was skipped.");
                aborted = true;
                pasteEntities(player); // try to record what we placed so /undo works for it
                entitiesPasted = true;
                finish(player);
                return;
            }

            // Process blocks for this tick
            long startTime = System.nanoTime();
            int processedThisTick = 0;

            while (currentIndex < totalBlocks &&
                   processedThisTick < PASTE_BLOCKS_PER_TICK &&
                   System.nanoTime() - startTime < PASTE_TIME_BUDGET_NANOS) {

                // Convert linear index to 3D coordinates
                int index = currentIndex++;
                int x = index % sizeX;
                int yz = index / sizeX;
                int z = yz % sizeZ;
                int y = yz / sizeZ;

                processBlock(x, y, z);
                processedThisTick++;
            }

            // Report progress
            if (currentIndex >= totalBlocks * nextProgressReport / 100) {
                int percent = (int) (currentIndex * 100 / totalBlocks);
                ChatOutput.send(player, ChatColor.GRAY + "Paste progress: " +
                               ChatColor.WHITE + percent + "%");
                nextProgressReport += 25;
            }

            // Check if done with blocks
            if (currentIndex >= totalBlocks && !entitiesPasted) {
                pasteEntities(player);
                entitiesPasted = true;
                finish(player);
            }
        }
        
        private void processBlock(int x, int y, int z) {
            try {
                BlockData originalData = clipboard.get(x, y, z);
                if (originalData == null) return;
                if (ignoreAir && originalData.getMaterial().isAir()) return;

                int vectorX = clipboard.getMinOffsetX() + x;
                int vectorZ = clipboard.getMinOffsetZ() + z;
                int[] rotated = EditUtil.rotateY(vectorX, vectorZ, rotation);

                int worldX = target.getBlockX() + rotated[0];
                int worldY = target.getBlockY() + clipboard.getMinOffsetY() + y;
                int worldZ = target.getBlockZ() + rotated[1];

                // Skip if outside world bounds (already surfaced as a Y-shave note up front).
                if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) {
                    skippedOutOfBounds++;
                    return;
                }

                // We're on the main thread (runTaskTimer), so loading a chunk synchronously is
                // safe. Only force-load on chunk transitions to avoid hammering the lookup.
                int cx = worldX >> 4;
                int cz = worldZ >> 4;
                if (cx != lastChunkX || cz != lastChunkZ) {
                    if (!world.isChunkLoaded(cx, cz)) {
                        try {
                            world.getChunkAt(cx, cz); // synchronous load on main thread
                        } catch (Exception e) {
                            plugin.getLogger().warning("Could not load chunk " + cx + "," + cz
                                    + " during paste: " + e.getClass().getSimpleName());
                            skippedChunkLoad++;
                            return;
                        }
                    }
                    lastChunkX = cx;
                    lastChunkZ = cz;
                }

                Block block = world.getBlockAt(worldX, worldY, worldZ);
                if (block == null) return;

                BlockData before = block.getBlockData().clone();
                BlockData placedData = ClipboardTransforms.applyRotation(originalData.clone(), rotation);

                if (placedData == null) return;

                block.setBlockData(placedData, false);
                placedCount++;

                // Handle tile entities with isolated error handling and user notice
                BlockState state = clipboard.getState(x, y, z);
                if (state != null) {
                    try {
                        BlockState placed = state.copy(block.getLocation());
                        placed.setBlockData(placedData.clone());
                        placed.update(true, false);
                    } catch (Exception e) {
                        Player p = Bukkit.getPlayer(playerId);
                        if (p != null) {
                            ChatOutput.send(p, ChatColor.YELLOW + "Note: Failed to restore tile entity at " +
                                            worldX + "," + worldY + "," + worldZ);
                        }
                        plugin.getLogger().warning("Tile entity restore failed at " + worldX + "," + worldY + "," + worldZ + ": " + e.getClass().getSimpleName());
                    }
                }

                if (recordHistory) {
                    BlockData after = block.getBlockData().clone();
                    changes.add(new BlockChange(block.getLocation(), before, after));
                }

            } catch (Exception e) {
                // Skip this block but continue paste
            }
        }

        private void pasteEntities(Player player) {
            try {
                pastedEntityChanges = EditUtil.pasteEntities(player, clipboard, target, rotation);
                ChatOutput.send(player, ChatColor.GRAY + "Pasted " + pastedEntityChanges.size() + " entities.");
            } catch (Exception e) {
                ChatOutput.send(player, ChatColor.RED + "Failed to paste entities: " + e.getMessage());
            }
            flushHistory();
        }

        private void finish(Player player) {
            try {
                flushHistory();
                
                StringBuilder skipsTail = new StringBuilder();
                if (skippedOutOfBounds > 0) {
                    skipsTail.append(", ").append(skippedOutOfBounds).append(" skipped (outside world Y range)");
                }
                if (skippedChunkLoad > 0) {
                    skipsTail.append(", ").append(skippedChunkLoad).append(" skipped (chunk load failed)");
                }
                ChatColor color = placedCount == 0 ? ChatColor.YELLOW : ChatColor.GREEN;
                String headline;
                if (aborted) {
                    headline = "Paste stopped early: " + placedCount + " blocks placed before abort.";
                    color = ChatColor.YELLOW;
                } else if (placedCount == 0) {
                    headline = "Paste completed but placed 0 blocks. "
                            + "Check that the clipboard isn't all-air with ignoreAir, "
                            + "or that the destination Y range overlaps the world.";
                } else {
                    headline = "Paste completed: " + placedCount + " blocks placed" + skipsTail + ".";
                }
                ChatOutput.send(player, color + headline);

                // After a large paste, schedule a delayed compaction sweep so
                // the freshly-recorded undo BlockData entries get interned to
                // shared instances. Delayed by 30s to let GC settle the warm
                // allocations from the paste itself.
                if (recordHistory && placedCount > 200_000) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            historyService.compactStaleActions();
                        } catch (Throwable ignored) {}
                    }, 20L * 30L);
                }

                // Call completion callback with the actual placed count (not the
                // capped recording count, which would mislead callers/log readers).
                if (onComplete != null) {
                    onComplete.accept(placedCount);
                }

            } catch (Exception e) {
                ChatOutput.send(player, ChatColor.RED + "Error saving paste history: " + 
                               e.getMessage());
                // Still call callback with 0 count on error
                if (onComplete != null) {
                    onComplete.accept(0);
                }
            } finally {
                cleanup();
            }
        }
        
        private void cancel(String reason) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                ChatOutput.send(player, ChatColor.RED + "Paste cancelled: " + reason);
            }
            try {
                flushPartialHistory(playerId);
            } finally {
                cleanup();
            }
        }

        /** Records the blocks placed so far (once), so a cancelled or crashed paste can still be undone. */
        void flushHistory() {
            if (historyPushed) return;
            if (recordHistory && (!changes.isEmpty() || !pastedEntityChanges.isEmpty())) {
                historyService.recordOnce(playerId, changes, pastedEntityChanges, historyAttempt);
            }
            historyPushed = true;
        }
        
        private void cleanup() {
            asyncPasteTasks.remove(playerId);
            if (historyPushed) partialHistoryFlushers.remove(playerId);
            pasteProgress.remove(playerId);
            if (task != null) {
                task.cancel();
            }
        }
    }

    record NudgeSession(Clipboard clipboard, Selection selection,
                                Map<BlockPos, org.bukkit.block.data.BlockData> background) {
    }

    private record BlockPos(int x, int y, int z) {
    }

    private record WorldContext(org.bukkit.World world) {
        boolean valid() {
            return world != null;
        }

        org.bukkit.block.Block blockAt(BlockPos position) {
            return world.getBlockAt(position.x(), position.y(), position.z());
        }
    }
}
