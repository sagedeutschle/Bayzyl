package com.bayzyl;

import java.util.Locale;

public enum BuilderProfileType {
    CONFIG("config"),
    TOOLBAR("toolbar"),
    COMBINED("combined");

    private final String key;

    BuilderProfileType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean hasConfig() {
        return this == CONFIG || this == COMBINED;
    }

    public boolean hasToolbar() {
        return this == TOOLBAR || this == COMBINED;
    }

    public static BuilderProfileType fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BuilderProfileType value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
