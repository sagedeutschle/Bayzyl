package com.bayzyl;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhostHandService {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return enabled.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (enabled.contains(player.getUniqueId())) {
            enabled.remove(player.getUniqueId());
            return false;
        }
        enabled.add(player.getUniqueId());
        return true;
    }

    public void setEnabled(Player player, boolean value) {
        if (value) {
            enabled.add(player.getUniqueId());
        } else {
            enabled.remove(player.getUniqueId());
        }
    }
}
