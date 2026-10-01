package com.bayzyl;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NudgeSettingsService {
    private final Map<UUID, NudgeSettings> settings = new ConcurrentHashMap<>();

    public NudgeSettings get(Player player) {
        return settings.getOrDefault(player.getUniqueId(), NudgeSettings.defaults());
    }

    public void set(Player player, NudgeSettings nudgeSettings) {
        settings.put(player.getUniqueId(), nudgeSettings);
    }

    public void reset(Player player) {
        settings.remove(player.getUniqueId());
    }
}
