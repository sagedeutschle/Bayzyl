package com.bayzyl;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for crash recovery system.
 * Handles restoring player sessions and clipboards after server restart.
 */
public final class CrashRecoveryListener implements Listener {
    private final CrashRecoveryService crashRecoveryService;
    
    public CrashRecoveryListener(CrashRecoveryService crashRecoveryService) {
        this.crashRecoveryService = crashRecoveryService;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if player has interrupted sessions from crash
        if (crashRecoveryService.hasInterruptedSession(event.getPlayer().getUniqueId())) {
            // Offer to resume the session
            event.getPlayer().sendMessage("§6[Bayzyl] §7You have an interrupted command from server restart.");
            event.getPlayer().sendMessage("§7Type §f/bayzyl resume §7to resume where you left off.");
        }
        
        // Restore clipboard automatically (in memory)
        // The ClipboardManager will load it on demand via get()
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up completed sessions when player leaves
        crashRecoveryService.completeSession(event.getPlayer().getUniqueId());
    }
}