package com.bayzyl.detail;

public record DetailBrushVariant(
        String name,
        String presetId,
        String displayName,
        String description,
        DetailBrushSettings settings
) {
}
