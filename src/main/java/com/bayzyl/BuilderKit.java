package com.bayzyl;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record BuilderKit(
        String name,
        int version,
        long updatedAtEpochMillis,
        BuilderKitScope scope,
        boolean builtIn,
        String theme,
        String note,
        String iconMaterialKey,
        String author,
        List<String> aliases,
        List<SlotItem> items
) {
    public int itemCount() {
        return items == null ? 0 : items.size();
    }

    public record SlotItem(int slot, ItemStack item) {
    }
}
