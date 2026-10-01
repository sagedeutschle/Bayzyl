package com.bayzyl;

public final class EraserSettings {
    private final int radius;
    private final BlockMask mask;
    private final boolean surfaceOnly;
    private final boolean selectionOnly;
    private final boolean carveOnly;
    private final boolean editBedrock;

    public EraserSettings(int radius, BlockMask mask, boolean surfaceOnly, boolean selectionOnly, boolean carveOnly, boolean editBedrock) {
        this.radius = radius;
        this.mask = mask;
        this.surfaceOnly = surfaceOnly;
        this.selectionOnly = selectionOnly;
        this.carveOnly = carveOnly;
        this.editBedrock = editBedrock;
    }

    public int getRadius() {
        return radius;
    }

    public BlockMask getMask() {
        return mask;
    }

    public boolean isSurfaceOnly() {
        return surfaceOnly;
    }

    public boolean isSelectionOnly() {
        return selectionOnly;
    }

    public boolean isCarveOnly() {
        return carveOnly;
    }

    public boolean isEditBedrock() {
        return editBedrock;
    }
}
