package com.bayzyl;

import java.util.Locale;

public enum ShapeBrushType {
    SPHERE("sphere", "Sphere", false),
    HSPHERE("hsphere", "Hollow Sphere", true),
    CYL("cyl", "Cylinder", false),
    HCYL("hcyl", "Hollow Cylinder", true),
    PYRAMID("pyramid", "Pyramid", false),
    HPYRAMID("hpyramid", "Hollow Pyramid", true);

    private final String commandName;
    private final String displayName;
    private final boolean hollow;

    ShapeBrushType(String commandName, String displayName, boolean hollow) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.hollow = hollow;
    }

    public String commandName() {
        return commandName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean hollow() {
        return hollow;
    }

    public static ShapeBrushType parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String token = input.toLowerCase(Locale.ROOT).trim();
        if (token.equals("sphere")) {
            return SPHERE;
        }
        if (token.equals("hsphere")) {
            return HSPHERE;
        }
        if (token.equals("cyl")) {
            return CYL;
        }
        if (token.equals("hcyl")) {
            return HCYL;
        }
        if (token.equals("pyramid")) {
            return PYRAMID;
        }
        if (token.equals("hpyramid")) {
            return HPYRAMID;
        }
        return null;
    }
}
