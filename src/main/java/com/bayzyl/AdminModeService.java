package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandCapability;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class AdminModeService {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(Player player) {
        return player != null && isEnabled(player.getUniqueId());
    }

    public boolean isEnabled(UUID playerId) {
        return playerId != null && enabled.contains(playerId);
    }

    public boolean isActive(Player player, BayzylAccess access) {
        return player != null
                && access != null
                && isEnabled(player)
                && access.allowed(player, CommandCapability.ADMIN_MODE);
    }

    public boolean isActive(Player player) {
        return isActive(player, new BayzylAccess());
    }

    public boolean isActive(UUID playerId, Predicate<String> hasPermission) {
        return isEnabled(playerId)
                && new BayzylAccess().allowed(hasPermission, CommandCapability.ADMIN_MODE);
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

    public void disable(Player player) {
        if (player == null) {
            return;
        }
        enabled.remove(player.getUniqueId());
    }

    public void beginSession(UUID playerId) {
        if (playerId != null) {
            enabled.remove(playerId);
        }
    }

    public void setEnabled(UUID playerId, boolean value) {
        if (playerId == null) {
            return;
        }
        if (value) {
            enabled.add(playerId);
        } else {
            enabled.remove(playerId);
        }
    }

    public void setEnabled(Player player, boolean value) {
        if (player == null) {
            return;
        }
        setEnabled(player.getUniqueId(), value);
    }
}
