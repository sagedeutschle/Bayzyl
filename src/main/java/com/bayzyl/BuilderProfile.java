package com.bayzyl;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record BuilderProfile(
        String name,
        int version,
        long updatedAtEpochMillis,
        BuilderProfileType type,
        BuilderProfileConfig config,
        List<ToolbarSlot> toolbarSlots
) {
    public boolean hasConfig() {
        return config != null;
    }

    public boolean hasToolbar() {
        return toolbarSlots != null && !toolbarSlots.isEmpty();
    }

    public record ToolbarSlot(int slot, ItemStack item) {
    }
}
