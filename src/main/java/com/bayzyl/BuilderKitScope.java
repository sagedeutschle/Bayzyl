package com.bayzyl;

import java.util.Locale;

public enum BuilderKitScope {
    HOTBAR("hotbar", 0, 9),
    INVENTORY("inventory", 0, 36);

    private final String key;
    private final int startSlot;
    private final int endSlotExclusive;

    BuilderKitScope(String key, int startSlot, int endSlotExclusive) {
        this.key = key;
        this.startSlot = startSlot;
        this.endSlotExclusive = endSlotExclusive;
    }

    public String key() {
        return key;
    }

    public int startSlot() {
        return startSlot;
    }

    public int endSlotExclusive() {
        return endSlotExclusive;
    }

    public int slotCount() {
        return endSlotExclusive - startSlot;
    }

    public static BuilderKitScope fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BuilderKitScope scope : values()) {
            if (scope.key.equals(normalized)) {
                return scope;
            }
        }
        return null;
    }
}
