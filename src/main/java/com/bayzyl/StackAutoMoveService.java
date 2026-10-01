package com.bayzyl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StackAutoMoveService {
    private final Map<UUID, Boolean> playerStates = new HashMap<>();

    public boolean isEnabled(java.util.UUID playerId) {
        return playerStates.getOrDefault(playerId, true);
    }

    public void setEnabled(java.util.UUID playerId, boolean enabled) {
        playerStates.put(playerId, enabled);
    }
}
