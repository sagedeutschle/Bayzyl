package com.bayzyl.edit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class EditAdapterResolver {
    private EditAdapterResolver() {
    }

    public static EditAdapter resolve(JavaPlugin plugin) {
        NativeEditAdapter nativeAdapter = new NativeEditAdapter();
        if (hasPlugin("FastAsyncWorldEdit") || hasPlugin("WorldEdit")) {
            plugin.getLogger().info("WorldEdit-compatible plugin detected. Core edit operations remain on Bayzyl native adapter.");
            return nativeAdapter;
        }

        plugin.getLogger().info("WorldEdit not detected. Using Bayzyl native edit adapter.");
        return nativeAdapter;
    }

    private static boolean hasPlugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }
}
