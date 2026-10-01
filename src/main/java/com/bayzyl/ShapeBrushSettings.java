package com.bayzyl;

public record ShapeBrushSettings(
        ShapeBrushType type,
        BlockDistribution distribution,
        int radiusX,
        int radiusY,
        int radiusZ,
        int height,
        int size,
        ShapeAnchorMode anchorMode,
        BlockMask mask,
        boolean confirm
) {
}
