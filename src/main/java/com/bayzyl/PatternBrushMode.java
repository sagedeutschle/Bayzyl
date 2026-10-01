package com.bayzyl;

import java.util.Locale;

public enum PatternBrushMode {
    SPATTER("spatter", "Spatter"),
    REPLACE("replace", "Replace"),
    BLEND("blend", "Blend"),
    SURFACE("surface", "Surface"),
    NOISE("noise", "Noise"),
    RESTORE("restore", "Restore"),
    VEGETATION("vegetation", "Vegetation"),
    DECAY("decay", "Decay");

    private final String commandName;
    private final String displayName;

    PatternBrushMode(String commandName, String displayName) {
        this.commandName = commandName;
        this.displayName = displayName;
    }

    public String commandName() {
        return commandName;
    }

    public String displayName() {
        return displayName;
    }

    public static PatternBrushMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (PatternBrushMode mode : values()) {
            if (mode.commandName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
