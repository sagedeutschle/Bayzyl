package com.bayzyl;

public record RamAlertSettings(
        boolean enabled,
        int thresholdPercent,
        int intervalSeconds,
        int cooldownSeconds
) {
    public static RamAlertSettings defaults() {
        return new RamAlertSettings(false, 85, 30, 120);
    }
}
