package com.bayzyl.detail;

public record DetailBrushSettings(
        String presetId,
        DetailBrushParameters parameters,
        DetailBrushMode mode
) {
    public DetailBrushSettings withMode(DetailBrushMode newMode) {
        return new DetailBrushSettings(presetId, parameters, newMode);
    }

    public DetailBrushSettings withParameters(DetailBrushParameters newParameters) {
        return new DetailBrushSettings(presetId, newParameters, mode);
    }
}
