package com.bayzyl.gen;

import com.bayzyl.BlockMask;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the loose tail of a {@code /brush gen <type>} or {@code /brushgen <type>}
 * invocation into a {@link GenBrushSettings} record.
 *
 * Syntax (after the {@code <type>} token):
 *
 *   {@code [subtype] [size:<n>] [intensity:<0..1>] [height:<n>] [...]
 *          [mask:<blocks>] [seed:<n>] [adapt:on|off]}
 *
 * Any unknown {@code key:value} pair is stored verbatim in
 * {@link GenBrushParameters} so per-generator params don't require
 * parser changes when added.
 */
public final class GenBrushCommandParser {
    private GenBrushCommandParser() {
    }

    public static GenBrushSettings parse(GenBrushType type, String[] args) {
        return parse(type, args, 0);
    }

    /**
     * Parse starting from {@code startIndex}. The arg at {@code startIndex - 1}
     * is assumed to be the type token already consumed by the caller.
     */
    public static GenBrushSettings parse(GenBrushType type, String[] args, int startIndex) {
        if (type == null) {
            throw new IllegalArgumentException("Unknown gen brush type.");
        }
        CaveSubtype caveSubtype = CaveSubtype.AUTO;
        BlockMask mask = null;
        boolean adapt = true;
        long seed = System.currentTimeMillis();
        int radius = defaultRadius(type);
        Map<String, String> rawParams = new LinkedHashMap<>();

        int i = startIndex;
        // First positional token after the type may be a cave subtype OR a numeric radius
        // OR a key:value option. We don't consume it unless it looks like a subtype.
        if (type == GenBrushType.CAVE && i < args.length && !args[i].contains(":")) {
            CaveSubtype maybe = CaveSubtype.parse(args[i]);
            if (maybe != null) {
                caveSubtype = maybe;
                i++;
            }
        }
        // Optional bare integer second token: interpreted as radius.
        if (i < args.length && !args[i].contains(":") && isInteger(args[i])) {
            try {
                radius = parsePositiveRadius(args[i]);
                i++;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad radius: " + args[i], ex);
            }
        }
        while (i < args.length) {
            String token = args[i++];
            if (token == null || token.isEmpty()) {
                continue;
            }
            int colonIdx = token.indexOf(':');
            if (colonIdx <= 0) {
                throw new IllegalArgumentException("Unexpected option '" + token + "' (expected key:value).");
            }
            String key = token.substring(0, colonIdx).toLowerCase(Locale.ROOT);
            String value = token.substring(colonIdx + 1);
            switch (key) {
                case "size", "r" -> {
                    try {
                        radius = parsePositiveRadius(value);
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Bad number for " + key + ": " + value);
                    }
                }
                case "radius" -> {
                    if (type == GenBrushType.BOULDER) {
                        putParameter(rawParams, key, value);
                    } else {
                        try {
                            radius = parsePositiveRadius(value);
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("Bad number for " + key + ": " + value);
                        }
                    }
                }
                case "mask" -> {
                    if ("none".equalsIgnoreCase(value) || "any".equalsIgnoreCase(value)) {
                        mask = null;
                    } else {
                        if (!GenBrushSafety.isResolvableMask(value)) {
                            throw new IllegalArgumentException("Mask does not resolve to blocks: " + value);
                        }
                        mask = BlockMask.parse(value);
                    }
                }
                case "adapt", "env", "environment" -> adapt = parseBool(value);
                case "seed" -> {
                    try {
                        seed = Long.parseLong(value);
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Bad number for seed: " + value, ex);
                    }
                }
                case "subtype", "cave" -> {
                    CaveSubtype maybe = CaveSubtype.parse(value);
                    if (maybe == null) {
                        throw new IllegalArgumentException("Unknown cave subtype: " + value);
                    }
                    caveSubtype = maybe;
                }
                case "confirm" -> putParameter(rawParams, "confirm", value);
                default -> putParameter(rawParams, key, value);
            }
        }
        GenBrushParameters params = new GenBrushParameters(rawParams);
        GenBrushSettings settings = new GenBrushSettings(
                type, caveSubtype, radius, params, mask, adapt, seed);
        com.bayzyl.safety.WorkEstimate estimate = GenBrushSafety.assess(settings);
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
        return settings;
    }

    public static int defaultRadius(GenBrushType type) {
        return switch (type) {
            case BOULDER, SCREE -> 6;
            case CAVE -> 8;
            case RAVINE -> 5;
            case RIDGE, PLATEAU, VALLEY, BASIN, ERODE, DUNES,
                 MESA, PEAK, CLIFF -> 10;
        };
    }

    private static int parsePositiveRadius(String raw) {
        int parsed = Integer.parseInt(raw);
        if (parsed < 1) {
            throw new NumberFormatException("Radius must be positive.");
        }
        return parsed;
    }

    private static void putParameter(Map<String, String> parameters, String key, String value) {
        if (parameters.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Duplicate gen brush parameter: " + key);
        }
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0 && c == '-') continue;
            if (!Character.isDigit(c)) return false;
        }
        return s.length() > 0 && (s.length() > 1 || Character.isDigit(s.charAt(0)));
    }

    private static boolean parseBool(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Expected on/off, got: " + value);
        };
    }
}
