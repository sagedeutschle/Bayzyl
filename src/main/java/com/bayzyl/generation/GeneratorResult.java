package com.bayzyl.generation;

public record GeneratorResult(
        boolean success,
        int changed,
        String message,
        boolean preview
) {
    public static GeneratorResult failed(String message) {
        return new GeneratorResult(false, 0, message, false);
    }

    public static GeneratorResult success(int changed, String message) {
        return new GeneratorResult(true, changed, message, false);
    }

    public static GeneratorResult preview(int changed, String message) {
        return new GeneratorResult(true, changed, message, true);
    }
}
