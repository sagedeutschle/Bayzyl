package com.bayzyl.gen;

import com.bayzyl.BlockMask;

/**
 * Bound state for one parametric gen brush. Lives in the held item's PDC
 * and is reconstructed on every click via {@link com.bayzyl.ToolManager}.
 */
public record GenBrushSettings(
        GenBrushType type,
        CaveSubtype caveSubtype,
        int radius,
        GenBrushParameters parameters,
        BlockMask mask,
        boolean adaptToEnvironment,
        long seed
) {
    public GenBrushSettings {
        if (type == null) {
            throw new IllegalArgumentException("Gen brush type required.");
        }
        if (caveSubtype == null) {
            caveSubtype = CaveSubtype.AUTO;
        }
        if (parameters == null) {
            parameters = GenBrushParameters.empty();
        }
        if (radius < 1) {
            throw new IllegalArgumentException("Gen brush radius must be positive.");
        }
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(type.displayName());
        if (type == GenBrushType.CAVE && caveSubtype != CaveSubtype.AUTO) {
            sb.append('/').append(caveSubtype.commandName());
        }
        sb.append(" r:").append(radius);
        sb.append(" adapt:").append(adaptToEnvironment ? "on" : "off");
        if (mask != null && mask.getRaw() != null && !mask.getRaw().isBlank()) {
            sb.append(" mask:").append(mask.summary());
        }
        return sb.toString();
    }
}
