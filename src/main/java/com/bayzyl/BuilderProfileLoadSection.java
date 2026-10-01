package com.bayzyl;

import java.util.Locale;

public enum BuilderProfileLoadSection {
    ALL("all"),
    CONFIG("config"),
    TOOLBAR("toolbar");

    private final String key;

    BuilderProfileLoadSection(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static BuilderProfileLoadSection fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BuilderProfileLoadSection value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}
