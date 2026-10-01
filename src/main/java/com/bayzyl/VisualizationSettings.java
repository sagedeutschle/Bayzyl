package com.bayzyl;

public final class VisualizationSettings {
    public enum Intensity {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum GridMode {
        OFF,
        ON,
        AUTO
    }

    private boolean enabled;
    private Intensity intensity;
    private GridMode gridMode;
    private int consistency;
    private org.bukkit.Color color;

    public VisualizationSettings() {
        this.enabled = true;
        this.intensity = Intensity.MEDIUM;
        this.gridMode = GridMode.AUTO;
        this.consistency = 10;
        this.color = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Intensity getIntensity() {
        return intensity;
    }

    public void setIntensity(Intensity intensity) {
        this.intensity = intensity;
    }

    public GridMode getGridMode() {
        return gridMode;
    }

    public void setGridMode(GridMode gridMode) {
        this.gridMode = gridMode;
    }

    public int getConsistency() {
        return consistency;
    }

    public void setConsistency(int consistency) {
        this.consistency = Math.max(1, Math.min(10, consistency));
    }

    public org.bukkit.Color getColor() {
        return color;
    }

    public void setColor(org.bukkit.Color color) {
        this.color = color;
    }
}
