package com.bayzyl;

import java.util.EnumMap;

public record BuilderProfileConfig(
        boolean selectionParticlesEnabled,
        VisualizationSettings.Intensity intensity,
        VisualizationSettings.GridMode gridMode,
        int consistency,
        Integer colorRgb,
        String menuAccent,
        boolean nightVisionEnabled,
        boolean autoUnstickEnabled,
        boolean ghostHandEnabled,
        boolean stackLookDirectionEnabled,
        NudgeSettings nudgeSettings,
        EnumMap<TabMenuModule, Boolean> tabMenuStates,
        int trailLimit
) {
}
