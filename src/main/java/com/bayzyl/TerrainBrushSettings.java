package com.bayzyl;

public record TerrainBrushSettings(TerrainBrushType type, int radius, int power, boolean editBedrock) {
    public String summary() {
        return type.displayName() + " r:" + radius + " " + type.powerLabel().toLowerCase() + ":" + power
                + " bedrock:" + (editBedrock ? "on" : "off");
    }
}
