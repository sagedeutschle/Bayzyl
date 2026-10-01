package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Schematic save, load, and listing.
 *
 * <p>Schematic files are read and written through WorldEdit's clipboard formats, so that code
 * lives in {@link WorldEditSchematics} and is created only when a WorldEdit-compatible plugin is
 * enabled. Without one, Bayzyl still enables normally and the schematic commands explain what is
 * missing instead of failing plugin startup.
 */
public final class SchematicService {
    static final String REQUIRES_WORLDEDIT =
            "Schematic files need WorldEdit or FastAsyncWorldEdit installed on the server.";

    private final JavaPlugin plugin;
    private final BooleanSupplier worldEditAvailable;
    private WorldEditSchematics worldEdit;

    public SchematicService(JavaPlugin plugin) {
        this(plugin, SchematicService::worldEditEnabled);
    }

    SchematicService(JavaPlugin plugin, BooleanSupplier worldEditAvailable) {
        this.plugin = plugin;
        this.worldEditAvailable = worldEditAvailable;
    }

    public SchematicSaveResult saveSelection(Player player,
                                             Selection selection,
                                             String rawName,
                                             String originMode,
                                             boolean includeEntities,
                                             boolean includeBiomes,
                                             boolean confirmOverwrite) {
        if (!worldEditAvailable.getAsBoolean()) {
            return SchematicSaveResult.failed(REQUIRES_WORLDEDIT);
        }
        return worldEdit().saveSelection(player, selection, rawName, originMode,
                includeEntities, includeBiomes, confirmOverwrite);
    }

    public LoadResult loadSchematic(Player player, String schematicName) {
        if (!worldEditAvailable.getAsBoolean()) {
            return LoadResult.failed(REQUIRES_WORLDEDIT);
        }
        return worldEdit().loadSchematic(player, schematicName);
    }

    public List<String> listSchematics(String filter) {
        if (!worldEditAvailable.getAsBoolean()) {
            return Collections.emptyList();
        }
        return worldEdit().listSchematics(filter);
    }

    private synchronized WorldEditSchematics worldEdit() {
        if (worldEdit == null) {
            worldEdit = new WorldEditSchematics(plugin);
        }
        return worldEdit;
    }

    private static boolean worldEditEnabled() {
        return isEnabled("FastAsyncWorldEdit") || isEnabled("WorldEdit");
    }

    private static boolean isEnabled(String name) {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
        return dependency != null && dependency.isEnabled();
    }

    /**
     * Result of a schematic load operation.
     */
    public static record LoadResult(
        boolean success,
        String message,
        com.bayzyl.Clipboard clipboard
    ) {
        public static LoadResult success(com.bayzyl.Clipboard clipboard, String message) {
            return new LoadResult(true, message, clipboard);
        }

        public static LoadResult failed(String message) {
            return new LoadResult(false, message, null);
        }
    }
}
