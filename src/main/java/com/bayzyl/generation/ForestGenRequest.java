package com.bayzyl.generation;

import com.bayzyl.ShapeAnchorMode;
import org.bukkit.TreeType;

public record ForestGenRequest(
        int size,
        TreeType treeType,
        double density,
        ShapeAnchorMode anchorMode,
        boolean confirm
) {
}
