package com.bayzyl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GlobalMaskService {
    private final Map<UUID, BlockMask> masks = new HashMap<>();
    private final Set<UUID> disabled = new HashSet<>();

    public void set(UUID playerId, BlockMask mask) {
        if (mask == null || mask.isAny()) {
            masks.remove(playerId);
            disabled.remove(playerId);
            return;
        }
        masks.put(playerId, mask);
        disabled.remove(playerId);
    }

    public BlockMask get(UUID playerId) {
        if (disabled.contains(playerId)) {
            return null;
        }
        return masks.get(playerId);
    }

    public BlockMask getConfigured(UUID playerId) {
        return masks.get(playerId);
    }

    public boolean isEnabled(UUID playerId) {
        BlockMask mask = masks.get(playerId);
        return mask != null && !mask.isAny() && !disabled.contains(playerId);
    }

    public Boolean toggle(UUID playerId) {
        BlockMask mask = masks.get(playerId);
        if (mask == null || mask.isAny()) {
            return null;
        }
        if (disabled.remove(playerId)) {
            return Boolean.TRUE;
        }
        disabled.add(playerId);
        return Boolean.FALSE;
    }

    public void clear(UUID playerId) {
        masks.remove(playerId);
        disabled.remove(playerId);
    }

    public boolean has(UUID playerId) {
        return isEnabled(playerId);
    }
}
