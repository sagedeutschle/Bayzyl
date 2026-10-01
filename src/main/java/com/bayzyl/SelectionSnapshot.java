package com.bayzyl;

import org.bukkit.Location;

public record SelectionSnapshot(Location pos1, Location pos2, SelectionType type) {
    public static SelectionSnapshot from(Selection selection) {
        if (selection == null) {
            return null;
        }
        return new SelectionSnapshot(
                cloneLocation(selection.getPos1()),
                cloneLocation(selection.getPos2()),
                selection.getType()
        );
    }

    public Selection toSelection() {
        if (pos1 == null || pos2 == null || type == null) {
            return null;
        }
        return new Selection(cloneLocation(pos1), cloneLocation(pos2), type);
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }
}
