package com.bayzyl.shape;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShapeAdapterResolver {
    private ShapeAdapterResolver() {
    }

    public static ShapeAdapter resolve(JavaPlugin plugin) {
        NativeShapeAdapter nativeAdapter = new NativeShapeAdapter();
        if (hasPlugin("FastAsyncWorldEdit") || hasPlugin("WorldEdit")) {
            plugin.getLogger().info("WorldEdit-compatible plugin detected. Using WorldEdit shape adapter.");
            return new WorldEditShapeAdapter(nativeAdapter);
        }

        plugin.getLogger().info("WorldEdit not detected. Using Bayzyl native shape adapter.");
        return nativeAdapter;
    }

    private static boolean hasPlugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }
}
