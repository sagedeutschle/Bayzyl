package com.bayzyl;

public record CylinderRequest(
        BlockDistribution distribution,
        int radiusX,
        int radiusZ,
        int height,
        boolean hollow,
        ShapeAnchorMode anchorMode,
        int thickness,
        boolean evenCenter,
        boolean preview,
        boolean confirm,
        BlockMask mask
) {
}
