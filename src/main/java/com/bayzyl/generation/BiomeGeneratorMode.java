package com.bayzyl.generation;

public enum BiomeGeneratorMode {
    FULL,
    EXPRESSION,
    SPHERE,
    CYLINDER,
    PYRAMID,
    DOME,
    BOWL;

    public static BiomeGeneratorMode parse(String value) {
        return switch (value.toLowerCase()) {
            case "full", "fill" -> FULL;
            case "expr", "expression", "formula" -> EXPRESSION;
            case "sphere", "ellipsoid" -> SPHERE;
            case "cyl", "cylinder" -> CYLINDER;
            case "pyramid" -> PYRAMID;
            case "dome" -> DOME;
            case "bowl" -> BOWL;
            default -> throw new IllegalArgumentException("Unknown biome generator mode: " + value);
        };
    }
}
