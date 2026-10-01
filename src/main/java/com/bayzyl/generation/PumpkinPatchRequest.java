package com.bayzyl.generation;

import com.bayzyl.ShapeAnchorMode;

public record PumpkinPatchRequest(
        int size,
        ShapeAnchorMode anchorMode,
        boolean confirm
) {
}
