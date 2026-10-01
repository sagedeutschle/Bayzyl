package com.bayzyl;

import org.bukkit.Location;

public final class SelectionService {
    private final SelectionManager selectionManager;

    public SelectionService(SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    public Selection expandSelection(Selection selection, int amount, int[] direction) {
        return resize(selection, amount, direction, true);
    }

    public Selection expandSelectionAll(Selection selection, int amount) {
        return resizeAll(selection, amount, true);
    }

    public Selection contractSelection(Selection selection, int amount, int[] direction) {
        return resize(selection, amount, direction, false);
    }

    public Selection contractSelectionAll(Selection selection, int amount) {
        return resizeAll(selection, amount, false);
    }

    public void applySelection(java.util.UUID playerId, Selection selection) {
        selectionManager.setCuboid(playerId, selection.getPos1(), selection.getPos2());
    }

    public Selection nudgeSelection(Selection selection, int[] direction, int amount) {
        if (selection == null || !selection.isComplete() || direction == null || amount == 0) {
            return selection;
        }
        return new Selection(
                selection.getPos1().clone().add(direction[0] * amount, direction[1] * amount, direction[2] * amount),
                selection.getPos2().clone().add(direction[0] * amount, direction[1] * amount, direction[2] * amount),
                selection.getType()
        );
    }

    private Selection resize(Selection selection, int amount, int[] direction, boolean expand) {
        if (selection == null || !selection.isComplete() || amount <= 0 || direction == null) {
            return selection;
        }

        int minX = selection.getMinX();
        int minY = selection.getMinY();
        int minZ = selection.getMinZ();
        int maxX = selection.getMaxX();
        int maxY = selection.getMaxY();
        int maxZ = selection.getMaxZ();

        int oldMinX = minX;
        int oldMinY = minY;
        int oldMinZ = minZ;

        minX = adjustMin(oldMinX, maxX, direction[0], amount, expand);
        maxX = adjustMax(oldMinX, maxX, direction[0], amount, expand);
        minY = adjustMin(oldMinY, maxY, direction[1], amount, expand);
        maxY = adjustMax(oldMinY, maxY, direction[1], amount, expand);
        minZ = adjustMin(oldMinZ, maxZ, direction[2], amount, expand);
        maxZ = adjustMax(oldMinZ, maxZ, direction[2], amount, expand);

        Location pos1 = new Location(selection.getPos1().getWorld(), minX, minY, minZ);
        Location pos2 = new Location(selection.getPos1().getWorld(), maxX, maxY, maxZ);
        return new Selection(pos1, pos2, SelectionType.CUBOID);
    }

    private Selection resizeAll(Selection selection, int amount, boolean expand) {
        if (selection == null || !selection.isComplete() || amount <= 0) {
            return selection;
        }

        int minX = selection.getMinX();
        int minY = selection.getMinY();
        int minZ = selection.getMinZ();
        int maxX = selection.getMaxX();
        int maxY = selection.getMaxY();
        int maxZ = selection.getMaxZ();

        if (expand) {
            minX -= amount;
            minY -= amount;
            minZ -= amount;
            maxX += amount;
            maxY += amount;
            maxZ += amount;
        } else {
            minX = Math.min(maxX, minX + amount);
            minY = Math.min(maxY, minY + amount);
            minZ = Math.min(maxZ, minZ + amount);
            maxX = Math.max(minX, maxX - amount);
            maxY = Math.max(minY, maxY - amount);
            maxZ = Math.max(minZ, maxZ - amount);
        }

        Location pos1 = new Location(selection.getPos1().getWorld(), minX, minY, minZ);
        Location pos2 = new Location(selection.getPos1().getWorld(), maxX, maxY, maxZ);
        return new Selection(pos1, pos2, SelectionType.CUBOID);
    }

    private int adjustMin(int min, int max, int axisDirection, int amount, boolean expand) {
        if (axisDirection >= 0) {
            return min;
        }
        if (expand) {
            return min - amount;
        }
        return Math.min(max, min + amount);
    }

    private int adjustMax(int min, int max, int axisDirection, int amount, boolean expand) {
        if (axisDirection <= 0) {
            return max;
        }
        if (expand) {
            return max + amount;
        }
        return Math.max(min, max - amount);
    }
}
