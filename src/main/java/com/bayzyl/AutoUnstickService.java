package com.bayzyl;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoUnstickService {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public void setEnabled(Player player, boolean value) {
        if (value) {
            enabled.add(player.getUniqueId());
        } else {
            enabled.remove(player.getUniqueId());
        }
    }
}
