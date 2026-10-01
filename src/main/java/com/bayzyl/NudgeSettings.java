package com.bayzyl;

public record NudgeSettings(boolean inverted, int step, VerticalMode verticalMode) {
    public enum VerticalMode {
        JUMP,
        LOOK,
        OFF
    }

    public NudgeSettings {
        step = Math.max(1, step);
        verticalMode = verticalMode == null ? VerticalMode.JUMP : verticalMode;
    }

    public static NudgeSettings defaults() {
        return new NudgeSettings(true, 1, VerticalMode.JUMP);
    }
}
