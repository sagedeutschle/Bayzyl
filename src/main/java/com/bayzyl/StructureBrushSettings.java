package com.bayzyl;

public record StructureBrushSettings(
        String structureId,
        ShapeAnchorMode anchorMode,
        boolean confirm
) {
}
