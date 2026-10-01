package com.bayzyl.generation;

import com.bayzyl.ShapeAnchorMode;

public final class StructureGenRequest {
    private final String structureId;
    private final ShapeAnchorMode anchorMode;
    private final boolean confirm;

    public StructureGenRequest(String structureId, ShapeAnchorMode anchorMode, boolean confirm) {
        this.structureId = structureId;
        this.anchorMode = anchorMode;
        this.confirm = confirm;
    }

    public String structureId() {
        return structureId;
    }

    public ShapeAnchorMode anchorMode() {
        return anchorMode;
    }

    public boolean confirm() {
        return confirm;
    }
}
