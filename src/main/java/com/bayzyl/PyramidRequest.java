package com.bayzyl;

public record PyramidRequest(
        BlockDistribution distribution,
        int size,
        boolean hollow,
        ShapeAnchorMode anchorMode,
        boolean preview,
        boolean confirm,
        BlockMask mask
) {
}
