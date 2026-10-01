package com.bayzyl;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TabMenuSettingsService {
    private final Map<UUID, EnumMap<TabMenuModule, Boolean>> states = new ConcurrentHashMap<>();

    public boolean isEnabled(UUID playerId, TabMenuModule module) {
        return states.getOrDefault(playerId, new EnumMap<>(TabMenuModule.class))
                .getOrDefault(module, module.defaultEnabled());
    }

    public void setEnabled(UUID playerId, TabMenuModule module, boolean enabled) {
        states.computeIfAbsent(playerId, id -> new EnumMap<>(TabMenuModule.class))
                .put(module, enabled);
    }

    public void setAllEnabled(UUID playerId, boolean enabled) {
        EnumMap<TabMenuModule, Boolean> values = states.computeIfAbsent(playerId, id -> new EnumMap<>(TabMenuModule.class));
        for (TabMenuModule module : TabMenuModule.values()) {
            values.put(module, enabled);
        }
    }

    public boolean hasAnyEnabled(UUID playerId) {
        for (TabMenuModule module : TabMenuModule.values()) {
            if (isEnabled(playerId, module)) {
                return true;
            }
        }
        return false;
    }

    public void clear(UUID playerId) {
        states.remove(playerId);
    }
}
