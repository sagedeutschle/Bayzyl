package com.bayzyl.generation;

import com.bayzyl.ShapeAnchorMode;

public final class FeatureGenRequest {
    private final String featureId;
    private final ShapeAnchorMode anchorMode;
    private final boolean confirm;

    public FeatureGenRequest(String featureId, ShapeAnchorMode anchorMode, boolean confirm) {
        this.featureId = featureId;
        this.anchorMode = anchorMode;
        this.confirm = confirm;
    }

    public String featureId() {
        return featureId;
    }

    public ShapeAnchorMode anchorMode() {
        return anchorMode;
    }

    public boolean confirm() {
        return confirm;
    }
}
