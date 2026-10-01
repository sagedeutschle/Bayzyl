package com.bayzyl.redstone;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Drops a player's audit results and markers when they leave. */
public final class RedstoneAuditListener implements Listener {
    private final RedstoneAuditCommand command;

    public RedstoneAuditListener(RedstoneAuditCommand command) {
        this.command = command;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        command.forget(event.getPlayer().getUniqueId());
    }
}
