package com.bayzyl.generation;

public enum CoordinateMode {
    NORMALIZED,
    RAW,
    CENTER,
    ORIGIN;

    public static CoordinateMode parse(String value) {
        if (value == null) {
            return NORMALIZED;
        }
        return switch (value.toLowerCase()) {
            case "raw" -> RAW;
            case "center", "centre" -> CENTER;
            case "origin", "placement" -> ORIGIN;
            case "normalized", "normal", "norm" -> NORMALIZED;
            default -> throw new IllegalArgumentException(
                    "Unknown mode: " + value + ". Use normalized, raw, center, or origin.");
        };
    }
}
