package com.bayzyl;

public record ShapeResult(
        boolean success,
        boolean preview,
        int changed,
        String message
) {
    public static ShapeResult created(int changed, String message) {
        return new ShapeResult(true, false, changed, message);
    }

    public static ShapeResult preview(String message) {
        return new ShapeResult(true, true, 0, message);
    }

    public static ShapeResult failed(String message) {
        return new ShapeResult(false, false, 0, message);
    }
}
