package com.bayzyl.gen;

import java.util.Locale;

/**
 * Vanilla-leaning cave families. AUTO picks a subtype based on the
 * environment probe (deepslate level → deep dark, lush biome → lush, etc.).
 */
public enum CaveSubtype {
    AUTO("auto", "Auto", "Pick a subtype from environment context."),
    NOODLE("noodle", "Noodle", "Thin meandering tunnels (vanilla noodle caves)."),
    CHEESE("cheese", "Cheese", "Large round chambers (vanilla cheese caves)."),
    SPAGHETTI("spaghetti", "Spaghetti", "Long winding worm-like tunnels."),
    DRIPSTONE("dripstone", "Dripstone", "Cheese chamber with dripstone accents."),
    LUSH("lush", "Lush", "Lush-cave palette with moss, azalea, clay."),
    DEEP_DARK("deep_dark", "Deep Dark", "Deepslate chamber with sculk patches."),
    AQUIFER("aquifer", "Aquifer", "Water-filled tunnels with stone-lined walls."),
    REGULAR("regular", "Regular", "Generic perlin worm cave.");

    private final String commandName;
    private final String displayName;
    private final String description;

    CaveSubtype(String commandName, String displayName, String description) {
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

    public static CaveSubtype parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (CaveSubtype subtype : values()) {
            if (subtype.commandName.equals(normalized) || subtype.name().equalsIgnoreCase(normalized)) {
                return subtype;
            }
        }
        return switch (normalized) {
            case "deepdark", "deep-dark", "ancient", "sculk" -> DEEP_DARK;
            case "drip", "stalactite" -> DRIPSTONE;
            case "moss", "azalea" -> LUSH;
            case "water", "flooded" -> AQUIFER;
            case "worm", "perlin" -> SPAGHETTI;
            case "round", "chamber" -> CHEESE;
            case "thin", "vein" -> NOODLE;
            default -> null;
        };
    }
}
