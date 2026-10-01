package com.bayzyl;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StackLookDirectionService {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return player != null && enabled.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        UUID playerId = player.getUniqueId();
        if (enabled.contains(playerId)) {
            enabled.remove(playerId);
            return false;
        }
        enabled.add(playerId);
        return true;
    }

    public void setEnabled(Player player, boolean value) {
        if (player == null) {
            return;
        }
        if (value) {
            enabled.add(player.getUniqueId());
        } else {
            enabled.remove(player.getUniqueId());
        }
    }
}
