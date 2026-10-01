package com.bayzyl;

import java.util.Locale;

public enum TerrainBrushType {
    SMOOTH("smooth", "Smooth", "Iterations", BrushFamily.TERRAIN),
    RAISE("raise", "Raise", "Strength", BrushFamily.TERRAIN),
    LOWER("lower", "Lower", "Strength", BrushFamily.TERRAIN),
    FLATTEN("flatten", "Flatten", "Strength", BrushFamily.TERRAIN),
    NATURALIZE("naturalize", "Naturalize", "Depth", BrushFamily.TERRAIN),
    CLEANUP_FLOATING("floatingcleanup", "Floating Cleanup Eraser", "Reach", BrushFamily.CLEANUP),
    CLEANUP_FOLIAGE("foliagecleanup", "Foliage Cleanup Eraser", "Aggression", BrushFamily.CLEANUP),
    CLEANUP_LIQUIDS("liquidcleanup", "Liquid Cleanup Eraser", "Depth", BrushFamily.CLEANUP),
    CLEANUP_SNOW("snowcleanup", "Snow Cleanup Eraser", "Aggression", BrushFamily.CLEANUP),
    CLEANUP_LIGHTSPAM("lightcleanup", "Light Cleanup Eraser", "Reach", BrushFamily.CLEANUP);

    private final String commandName;
    private final String displayName;
    private final String powerLabel;
    private final BrushFamily family;

    TerrainBrushType(String commandName, String displayName, String powerLabel, BrushFamily family) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.powerLabel = powerLabel;
        this.family = family;
    }

    public String displayName() {
        return displayName;
    }

    public String powerLabel() {
        return powerLabel;
    }

    public String commandName() {
        return commandName;
    }

    public boolean isTerrainMode() {
        return family == BrushFamily.TERRAIN;
    }

    public boolean isCleanupMode() {
        return family == BrushFamily.CLEANUP;
    }

    public static TerrainBrushType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (TerrainBrushType value : values()) {
            if (value.commandName.equals(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return switch (normalized) {
            case "floating" -> CLEANUP_FLOATING;
            case "foliage" -> CLEANUP_FOLIAGE;
            case "liquids", "liquid" -> CLEANUP_LIQUIDS;
            case "snow" -> CLEANUP_SNOW;
            case "lightspam", "lights" -> CLEANUP_LIGHTSPAM;
            case "naturalise" -> NATURALIZE;
            default -> parseEnum(raw);
        };
    }

    private static TerrainBrushType parseEnum(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private enum BrushFamily {
        TERRAIN,
        CLEANUP
    }
}
