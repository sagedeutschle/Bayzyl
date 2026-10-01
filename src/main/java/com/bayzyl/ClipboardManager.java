package com.bayzyl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClipboardManager {
    private final Map<UUID, Clipboard> clipboards = new ConcurrentHashMap<>();
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();
    private CrashRecoveryService crashRecoveryService;

    public void set(UUID playerId, Clipboard clipboard) {
        if (clipboard == null) {
            clipboards.remove(playerId);
            if (crashRecoveryService != null) {
                crashRecoveryService.saveClipboard(playerId, null);
            }
            bumpRevision(playerId);
            return;
        }
        clipboards.put(playerId, clipboard);
        if (crashRecoveryService != null) {
            crashRecoveryService.saveClipboard(playerId, clipboard);
        }
        bumpRevision(playerId);
    }

    public Clipboard get(UUID playerId) {
        Clipboard clipboard = clipboards.get(playerId);
        // Try to load from crash recovery if not in memory
        if (clipboard == null && crashRecoveryService != null) {
            clipboard = crashRecoveryService.loadClipboard(playerId);
            if (clipboard != null) {
                clipboards.put(playerId, clipboard);
            }
        }
        return clipboard;
    }

    public long revision(UUID playerId) {
        return revisions.getOrDefault(playerId, 0L);
    }

    private void bumpRevision(UUID playerId) {
        if (playerId == null) {
            return;
        }
        revisions.merge(playerId, 1L, Long::sum);
    }

    /**
     * Connect to crash recovery service for persistence across restarts.
     * Should be called during plugin initialization.
     */
    public void setCrashRecoveryService(CrashRecoveryService service) {
        this.crashRecoveryService = service;
    }
}
