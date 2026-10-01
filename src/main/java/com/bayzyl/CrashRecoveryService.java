package com.bayzyl;

import com.bayzyl.persistence.AtomicYamlStore;
import com.bayzyl.persistence.CrashRecoverySnapshotCodec;
import com.bayzyl.persistence.RecoverySnapshot;
import com.bayzyl.persistence.RecoverySnapshot.ClipboardRecord;
import com.bayzyl.persistence.RecoverySnapshot.Lifecycle;
import com.bayzyl.persistence.RecoverySnapshot.NudgeRecord;
import com.bayzyl.persistence.RecoverySnapshot.SessionRecord;
import com.bayzyl.persistence.RecoverySnapshot.SessionValue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Crash recovery for Bayzyl.
 *
 * <p>State lives in memory as detached records. Every accepted mutation becomes a new revision of the complete
 * snapshot, which {@link AtomicYamlStore} writes atomically (with a rotating backup) off the main thread. The file
 * carries a lifecycle: {@code RUNNING} from startup, {@code CLEAN} only after a successful final flush on an
 * orderly disable. Unreadable state fails closed: the constructor throws and the files are left for inspection.
 */
public final class CrashRecoveryService {
    private static final long MAINTENANCE_INTERVAL_TICKS = 20L * 30L;
    private static final long SESSION_MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    /** Creates the store; a seam for storage-failure tests. */
    @FunctionalInterface
    public interface RecoveryStoreFactory {
        AtomicYamlStore<Map<String, Object>> create(Path target,
                                                    AtomicYamlStore.SnapshotCodec<Map<String, Object>> codec,
                                                    Executor executor,
                                                    Consumer<Long> successCallback,
                                                    Consumer<Exception> failureCallback);
    }

    /** Resumes a session restored from disk, whose original in-memory callback did not survive the restart. */
    @FunctionalInterface
    public interface ResumeHandler {
        void resume(Player player, ActiveCommandSession session);
    }

    public static RecoveryStoreFactory systemStoreFactory() {
        return AtomicYamlStore::new;
    }

    private final Logger logger;
    private final LongSupplier clock;
    private final AtomicYamlStore<Map<String, Object>> store;
    private final boolean crashDetected;
    private final boolean migratedLegacyFile;
    private final Map<UUID, Long> dirtyRevisions = new ConcurrentHashMap<>();
    private final Map<String, ResumeHandler> resumeHandlers = new ConcurrentHashMap<>();

    // Guarded by this.
    private final Map<UUID, SessionEntry> sessions = new HashMap<>();
    private final Map<UUID, ClipboardRecord> clipboards = new HashMap<>();
    private final Map<UUID, NudgeRecord> nudges = new HashMap<>();
    private final Set<UUID> restoredSessions = new HashSet<>();
    private final Set<UUID> reportedUnresolved = new HashSet<>();
    private Lifecycle lifecycle = Lifecycle.RUNNING;
    private long revision;
    private Map<String, Object> lastDocument;
    private Boolean disableResult;
    private BukkitTask maintenanceTask;

    private record SessionEntry(SessionRecord record, Map<String, Object> liveData,
                                Consumer<ActiveCommandSession> onResume) {
    }

    public CrashRecoveryService(JavaPlugin plugin) {
        this(plugin, plugin.getDataFolder().toPath().resolve("crash-recovery.yml"), bukkitExecutor(plugin), true,
                System::currentTimeMillis, systemStoreFactory());
    }

    /**
     * @param scheduleMaintenance whether to start the periodic cleanup/retry task on the Bukkit scheduler
     * @throws IllegalStateException if persisted state is unreadable (primary and backup) or RUNNING cannot be saved
     */
    public CrashRecoveryService(JavaPlugin plugin, Path file, Executor executor, boolean scheduleMaintenance,
                                LongSupplier clock, RecoveryStoreFactory storeFactory) {
        this.logger = plugin.getLogger();
        this.clock = clock;
        this.store = storeFactory.create(file, new CrashRecoverySnapshotCodec(), executor,
                this::onPersisted, this::onPersistFailure);

        AtomicYamlStore.LoadResult<Map<String, Object>> loaded = store.load();
        if (loaded.status() == AtomicYamlStore.LoadStatus.FAILED_CLOSED) {
            throw new IllegalStateException("crash-recovery state at " + file
                    + " and its backup are unreadable; both were left in place for inspection");
        }
        boolean abnormal = loaded.abnormal();
        boolean legacy = false;
        if (loaded.status() != AtomicYamlStore.LoadStatus.MISSING) {
            RecoverySnapshot.Decoded decoded = RecoverySnapshot.fromDocument(loaded.snapshot());
            RecoverySnapshot snapshot = decoded.snapshot();
            legacy = snapshot.legacy();
            abnormal |= snapshot.lifecycle() != Lifecycle.CLEAN;
            adopt(snapshot);
            if (!decoded.rejections().isEmpty()) {
                logger.warning("Crash recovery rejected " + decoded.rejections().size() + " unreadable payload(s):");
                decoded.rejections().forEach(rejection -> logger.warning("  - " + rejection));
            }
            if (loaded.status() == AtomicYamlStore.LoadStatus.BACKUP_RECOVERED) {
                logger.warning("Crash recovery loaded its backup file; the primary was missing or unreadable.");
            }
        }
        this.crashDetected = abnormal;
        this.migratedLegacyFile = legacy;

        synchronized (this) {
            lifecycle = Lifecycle.RUNNING;
            commitLocked(List.of());
        }
        if (!store.flush().success()) {
            throw new IllegalStateException("could not persist crash-recovery RUNNING state to " + file);
        }
        if (legacy) {
            logger.info("Migrated legacy crash-recovery.yml to format " + CrashRecoverySnapshotCodec.FORMAT_VERSION + ".");
        }
        if (scheduleMaintenance) {
            maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runMaintenance,
                    MAINTENANCE_INTERVAL_TICKS, MAINTENANCE_INTERVAL_TICKS);
        }
    }

    private static Executor bukkitExecutor(JavaPlugin plugin) {
        return task -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                // While disabling, the scheduler refuses new tasks; write on the calling thread instead.
                task.run();
            }
        };
    }

    private synchronized void adopt(RecoverySnapshot snapshot) {
        snapshot.sessions().forEach((id, record) -> {
            sessions.put(id, new SessionEntry(record, null, null));
            if (record.active()) {
                restoredSessions.add(id);
            }
        });
        clipboards.putAll(snapshot.clipboards());
        nudges.putAll(snapshot.nudges());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Sessions
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Start tracking an active command session.
     *
     * @throws IllegalArgumentException if {@code data} holds a value that cannot be persisted; nothing changes
     */
    public void startSession(UUID playerId, String command, String stage, Map<String, Object> data,
                             Consumer<ActiveCommandSession> onResume) {
        Map<String, SessionValue> detached = CrashRecoveryBridge.detachSessionData(data);
        synchronized (this) {
            SessionRecord record = new SessionRecord(command, stage, clock.getAsLong(), true, detached);
            sessions.put(playerId, new SessionEntry(record, liveCopy(data), onResume));
            restoredSessions.remove(playerId);
            commitLocked(List.of(playerId));
        }
    }

    /**
     * Replace an existing session's stage and data.
     *
     * @throws IllegalArgumentException if {@code data} holds a value that cannot be persisted; nothing changes
     */
    public void updateSession(UUID playerId, String stage, Map<String, Object> data) {
        Map<String, SessionValue> detached = CrashRecoveryBridge.detachSessionData(data);
        synchronized (this) {
            SessionEntry entry = sessions.get(playerId);
            if (entry == null) {
                return;
            }
            SessionRecord record = new SessionRecord(entry.record().command(), stage, clock.getAsLong(), true, detached);
            sessions.put(playerId, new SessionEntry(record, liveCopy(data), entry.onResume()));
            commitLocked(List.of(playerId));
        }
    }

    /** Complete a session (command finished successfully). */
    public void completeSession(UUID playerId) {
        synchronized (this) {
            restoredSessions.remove(playerId);
            if (sessions.remove(playerId) != null) {
                commitLocked(List.of(playerId));
            }
        }
    }

    /** Active session for the player, with persisted values restored lazily; null if none. */
    @Nullable
    public ActiveCommandSession getSession(UUID playerId) {
        SessionEntry entry;
        synchronized (this) {
            entry = sessions.get(playerId);
        }
        if (entry == null) {
            return null;
        }
        SessionRecord record = entry.record();
        Map<String, Object> data = entry.liveData() != null
                ? entry.liveData()
                : CrashRecoveryBridge.materializeSessionData(record.data(),
                        problem -> logger.warning("Crash recovery session for " + playerId + ": " + problem));
        return new ActiveCommandSession(playerId, record.command(), record.stage(), data,
                record.lastUpdateTime(), entry.onResume(), record.active());
    }

    /** True when the player has an active session that was restored from disk at startup. */
    public synchronized boolean hasInterruptedSession(UUID playerId) {
        SessionEntry entry = sessions.get(playerId);
        return entry != null && entry.record().active() && restoredSessions.contains(playerId);
    }

    /** Register how sessions of {@code command} resume after a restart. */
    public void registerResumeHandler(String command, ResumeHandler handler) {
        resumeHandlers.put(command, handler);
    }

    /** Resume an interrupted session. */
    public void resumeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ActiveCommandSession session = getSession(playerId);
        SessionEntry entry;
        synchronized (this) {
            entry = sessions.get(playerId);
            restoredSessions.remove(playerId);
        }
        if (session == null || entry == null) {
            return;
        }
        if (entry.onResume() != null) {
            try {
                entry.onResume().accept(session);
                synchronized (this) {
                    if (sessions.get(playerId) == entry) {
                        SessionRecord record = entry.record();
                        sessions.put(playerId, new SessionEntry(new SessionRecord(record.command(), "resumed",
                                clock.getAsLong(), false, record.data()), entry.liveData(), null));
                        commitLocked(List.of(playerId));
                    }
                }
                player.sendMessage("§6[Bayzyl] §7Resuming interrupted command: §f" + session.command());
            } catch (Exception exception) {
                logger.warning("Failed to resume session for " + player.getName() + ": " + exception.getMessage());
                completeSession(playerId);
            }
            return;
        }
        ResumeHandler handler = resumeHandlers.get(session.command());
        if (handler == null) {
            player.sendMessage("§6[Bayzyl] §7Bayzyl can't resume §f/" + session.command()
                    + "§7 automatically after a restart. It was cleared; run it again if you still need it.");
            completeSession(playerId);
            return;
        }
        try {
            player.sendMessage("§6[Bayzyl] §7Resuming interrupted command: §f" + session.command());
            handler.resume(player, session);
        } catch (RuntimeException exception) {
            logger.warning("Failed to resume " + session.command() + " for " + player.getName() + ": " + exception.getMessage());
            player.sendMessage("§c[Bayzyl] Could not resume /" + session.command() + ": " + exception.getMessage());
        } finally {
            synchronized (this) {
                if (sessions.get(playerId) == entry) {
                    sessions.remove(playerId);
                    commitLocked(List.of(playerId));
                }
            }
        }
    }

    /** Remove sessions untouched for 24 hours. */
    public void cleanupOldSessions() {
        synchronized (this) {
            long cutoff = clock.getAsLong() - SESSION_MAX_AGE_MS;
            List<UUID> removed = new java.util.ArrayList<>();
            Iterator<Map.Entry<UUID, SessionEntry>> iterator = sessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, SessionEntry> entry = iterator.next();
                if (entry.getValue().record().lastUpdateTime() < cutoff) {
                    iterator.remove();
                    restoredSessions.remove(entry.getKey());
                    removed.add(entry.getKey());
                }
            }
            if (!removed.isEmpty()) {
                commitLocked(removed);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Clipboards and nudge state
    // ---------------------------------------------------------------------------------------------------------

    /** Save (or with {@code null}, clear) the player's clipboard. Call on the thread that owns the clipboard. */
    public void saveClipboard(UUID playerId, Clipboard clipboard) {
        ClipboardRecord record = clipboard == null ? null : CrashRecoveryBridge.detachClipboard(clipboard);
        synchronized (this) {
            if (record == null) {
                clipboards.remove(playerId);
            } else {
                clipboards.put(playerId, record);
            }
            reportedUnresolved.remove(playerId);
            commitLocked(List.of(playerId));
        }
    }

    /** The player's persisted clipboard, or null if none (or its world is not loaded yet). */
    @Nullable
    public Clipboard loadClipboard(UUID playerId) {
        ClipboardRecord record;
        synchronized (this) {
            record = clipboards.get(playerId);
        }
        if (record == null) {
            return null;
        }
        CrashRecoveryBridge.Materialized<Clipboard> restored = CrashRecoveryBridge.materializeClipboard(record);
        if (restored.value() == null) {
            handleUnrestorable(playerId, "clipboard", restored, () -> clipboards.remove(playerId, record));
            return null;
        }
        reportClipboardLoss(playerId, record.omittedTileStates(), restored.rejectedEntities());
        return restored.value();
    }

    /** Save (or with {@code null}, clear) the player's nudge state. */
    public void saveNudgeSession(UUID playerId, EditService.NudgeSession nudgeSession) {
        NudgeRecord record = nudgeSession == null ? null : CrashRecoveryBridge.detachNudge(nudgeSession);
        synchronized (this) {
            if (record == null) {
                nudges.remove(playerId);
            } else {
                nudges.put(playerId, record);
            }
            commitLocked(List.of(playerId));
        }
    }

    /** The player's persisted nudge state, or null. Its background map is not persisted and is always empty. */
    @Nullable
    public EditService.NudgeSession loadNudgeSession(UUID playerId) {
        NudgeRecord record;
        synchronized (this) {
            record = nudges.get(playerId);
        }
        if (record == null) {
            return null;
        }
        CrashRecoveryBridge.Materialized<EditService.NudgeSession> restored = CrashRecoveryBridge.materializeNudge(record);
        if (restored.value() == null) {
            handleUnrestorable(playerId, "nudge state", restored, () -> nudges.remove(playerId, record));
            return null;
        }
        return restored.value();
    }

    private void handleUnrestorable(UUID playerId, String what, CrashRecoveryBridge.Materialized<?> restored,
                                    java.util.function.BooleanSupplier discard) {
        if (restored.permanent()) {
            synchronized (this) {
                if (discard.getAsBoolean()) {
                    commitLocked(List.of(playerId));
                }
            }
            logger.warning("Crash recovery rejected the " + what + " of " + playerId + ": " + restored.failure());
            return;
        }
        boolean firstReport;
        synchronized (this) {
            firstReport = reportedUnresolved.add(playerId);
        }
        if (firstReport) {
            logger.warning("Crash recovery kept the " + what + " of " + playerId + " for later: " + restored.failure());
        }
    }

    private void reportClipboardLoss(UUID playerId, int omittedTileStates, int rejectedEntities) {
        if (omittedTileStates == 0 && rejectedEntities == 0) {
            return;
        }
        StringBuilder message = new StringBuilder("§6[Bayzyl] §7Recovered your clipboard after a restart.");
        if (omittedTileStates > 0) {
            message.append(" §f").append(omittedTileStates)
                    .append("§7 container/sign content(s) were not saved by crash recovery.");
        }
        if (rejectedEntities > 0) {
            message.append(" §f").append(rejectedEntities).append("§7 entit(ies) could not be restored.");
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message.toString());
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lifecycle and persistence
    // ---------------------------------------------------------------------------------------------------------

    /** True when the previous run did not end with a CLEAN lifecycle (or state came from the backup file). */
    public boolean wasCrashDetected() {
        return crashDetected;
    }

    /** True when startup read the unversioned 0.1 layout and rewrote it in the current format. */
    public boolean migratedLegacyFile() {
        return migratedLegacyFile;
    }

    /** Persist the latest revision now. */
    public boolean flushRecovery() {
        return store.flush().success();
    }

    /** The newest unpersisted revision touching this player, or -1 once it is durable. */
    public long dirtyRevision(UUID playerId) {
        return dirtyRevisions.getOrDefault(playerId, -1L);
    }

    /** Equivalent to {@code disable(true)}. */
    @Deprecated
    public void markCleanShutdown() {
        disable(true);
    }

    /** Disable without knowing whether in-flight work stopped cleanly: the lifecycle stays RUNNING. */
    public void disable() {
        disable(false);
    }

    /**
     * Flush the latest state, then write CLEAN only if {@code cleanRequested} and every write succeeded. The first
     * call decides the result; later calls return it unchanged.
     */
    public boolean disable(boolean cleanRequested) {
        synchronized (this) {
            if (disableResult != null) {
                return disableResult;
            }
            if (maintenanceTask != null) {
                maintenanceTask.cancel();
                maintenanceTask = null;
            }
        }
        boolean result = false;
        if (store.flush().success() && cleanRequested) {
            synchronized (this) {
                lifecycle = Lifecycle.CLEAN;
                commitLocked(List.of());
            }
            result = store.flush().success();
            if (!result) {
                synchronized (this) {
                    lifecycle = Lifecycle.RUNNING;
                }
                logger.warning("Crash recovery could not record a clean shutdown; the next start will report a crash.");
            }
        }
        synchronized (this) {
            disableResult = result;
        }
        return result;
    }

    private void runMaintenance() {
        cleanupOldSessions();
        retryFailedWrites();
    }

    /** Re-schedule the newest retained revision after a failed write; a no-op when nothing failed. */
    public synchronized void retryFailedWrites() {
        if (store.state() == AtomicYamlStore.State.RETRYABLE && lastDocument != null) {
            store.submit(revision, lastDocument);
        }
    }

    /** Caller holds this monitor. Records a new revision of the complete snapshot and hands it to the store. */
    private void commitLocked(Collection<UUID> touchedPlayers) {
        long next = ++revision;
        Map<UUID, SessionRecord> records = new LinkedHashMap<>();
        sessions.forEach((id, entry) -> records.put(id, entry.record()));
        Map<String, Object> document = new RecoverySnapshot(lifecycle, false, records, clipboards, nudges).toDocument();
        lastDocument = document;
        for (UUID player : touchedPlayers) {
            dirtyRevisions.put(player, next);
        }
        store.submit(next, document);
    }

    private void onPersisted(long persistedRevision) {
        dirtyRevisions.entrySet().removeIf(entry -> entry.getValue() <= persistedRevision);
    }

    private void onPersistFailure(Exception exception) {
        logger.warning("Could not save crash-recovery.yml (will retry): " + exception.getMessage());
    }

    private static Map<String, Object> liveCopy(Map<String, Object> data) {
        if (data == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (value != null) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Represents an active command session that can be resumed after crash.
     */
    public static record ActiveCommandSession(
        UUID playerId,
        String command,
        String stage,
        Map<String, Object> data,
        long lastUpdateTime,
        @Nullable Consumer<ActiveCommandSession> onResume,
        boolean active
    ) {}
}
