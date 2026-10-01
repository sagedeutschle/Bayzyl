package com.bayzyl.gen;

import java.util.Locale;

/**
 * Parametric natural-generation brushes. Each type adapts to the
 * environment around the brush target so the result reads as if vanilla
 * worldgen had produced it (superflat slab or random vanilla terrain).
 */
public enum GenBrushType {
    RIDGE("ridge", "Ridge", "Raise a long noisy crest along the terrain."),
    PLATEAU("plateau", "Plateau", "Raise a flat-topped table with falloff edges."),
    VALLEY("valley", "Valley", "Carve a broad meandering depression."),
    BASIN("basin", "Basin", "Carve a circular bowl that fills with water when wet."),
    ERODE("erode", "Erode", "Break clean terrain into weathered noise."),
    RAVINE("ravine", "Ravine", "Slash a vertical chasm through the terrain."),
    CAVE("cave", "Cave", "Carve a vanilla-style cave system. See subtypes."),
    DUNES("dunes", "Dunes", "Lay down wind-shaped sand waves."),
    MESA("mesa", "Mesa", "Layered banded plateau with stepped terraces."),
    PEAK("peak", "Peak", "Push a sharp pointed mountain upward."),
    CLIFF("cliff", "Cliff", "Carve a tall directional cliff face."),
    BOULDER("boulder", "Boulder", "Scatter natural-looking rock blobs on the surface."),
    SCREE("scree", "Scree", "Spread loose rock debris down a slope.");

    private final String commandName;
    private final String displayName;
    private final String description;

    GenBrushType(String commandName, String displayName, String description) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.description = description;
    }

    public String commandName() {
        return commandName;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean supportsSubtype() {
        return this == CAVE;
    }

    public static GenBrushType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (GenBrushType type : values()) {
            if (type.commandName.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return switch (normalized) {
            case "ridges", "crest" -> RIDGE;
            case "plateaus", "table", "mesa-top" -> PLATEAU;
            case "valleys", "trough", "vale" -> VALLEY;
            case "bowls", "depression", "crater" -> BASIN;
            case "erosion", "weather" -> ERODE;
            case "ravines", "chasm", "gorge" -> RAVINE;
            case "caves", "cavern", "tunnel" -> CAVE;
            case "sandbar", "wave" -> DUNES;
            case "stripes", "bands", "terrace" -> MESA;
            case "summit", "spire" -> PEAK;
            case "cliffs", "escarpment", "wall" -> CLIFF;
            case "boulders", "rock", "rocks" -> BOULDER;
            case "talus", "rubble", "debris" -> SCREE;
            default -> null;
        };
    }
}
