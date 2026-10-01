package com.bayzyl.detail;

public record DetailBrushParameterSpec(
        String name,
        DetailBrushParameterType type,
        String defaultValue,
        double min,
        double max,
        String description
) {
    public static DetailBrushParameterSpec floatRange(String name, double defaultValue, double min, double max, String description) {
        return new DetailBrushParameterSpec(name, DetailBrushParameterType.FLOAT, Double.toString(defaultValue), min, max, description);
    }

    public static DetailBrushParameterSpec intRange(String name, int defaultValue, int min, int max, String description) {
        return new DetailBrushParameterSpec(name, DetailBrushParameterType.INT, Integer.toString(defaultValue), min, max, description);
    }

    public static DetailBrushParameterSpec string(String name, String defaultValue, String description) {
        return new DetailBrushParameterSpec(name, DetailBrushParameterType.STRING, defaultValue, 0, 0, description);
    }
}
