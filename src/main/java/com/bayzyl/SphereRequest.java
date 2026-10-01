package com.bayzyl;

public record SphereRequest(
        BlockDistribution distribution,
        int radiusX,
        int radiusY,
        int radiusZ,
        boolean hollow,
        ShapeAnchorMode anchorMode,
        int thickness,
        boolean evenCenter,
        boolean preview,
        boolean confirm,
        BlockMask mask
) {
}
