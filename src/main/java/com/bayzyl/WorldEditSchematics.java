package com.bayzyl;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.bayzyl.SchematicService.LoadResult;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Schematic file IO through WorldEdit clipboard formats. Only {@link SchematicService}
 * instantiates this, and only when a WorldEdit-compatible plugin is enabled, so plain
 * Paper servers never link the WorldEdit classes referenced here.
 */
final class WorldEditSchematics {
    private final JavaPlugin plugin;
    private volatile File schematicsDirectory;

    WorldEditSchematics(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public SchematicSaveResult saveSelection(Player player,
                                             Selection selection,
                                             String rawName,
                                             String originMode,
                                             boolean includeEntities,
                                             boolean includeBiomes,
                                             boolean confirmOverwrite) {
        if (player == null) {
            return SchematicSaveResult.failed("Players only.");
        }
        if (selection == null || !selection.isComplete()) {
            return SchematicSaveResult.failed("Selection is incomplete.");
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return SchematicSaveResult.failed("Selection world is unavailable.");
        }

        String name = normalizeName(rawName);
        if (name == null) {
            return SchematicSaveResult.failed("A schematic name is required.");
        }

        File directory = getSchematicsDirectory();
        if (directory == null) {
            return SchematicSaveResult.failed("Could not prepare the schematics folder.");
        }

        File file = new File(directory, name + ".schem");
        if (file.exists() && !confirmOverwrite) {
            return SchematicSaveResult.failed("Schematic '" + name + "' already exists at " + file.getPath()
                    + ". Re-run with confirm:true to overwrite.");
        }

        CuboidRegion region = new CuboidRegion(
                BukkitAdapter.adapt(world),
                BlockVector3.at(selection.getMinX(), selection.getMinY(), selection.getMinZ()),
                BlockVector3.at(selection.getMaxX(), selection.getMaxY(), selection.getMaxZ())
        );
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(resolveOrigin(player, selection, originMode));

        try {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    BukkitAdapter.adapt(world),
                    region,
                    region.getMinimumPoint(),
                    clipboard,
                    region.getMinimumPoint()
            );
            copy.setCopyingEntities(includeEntities);
            copy.setCopyingBiomes(includeBiomes);
            Operations.completeLegacy(copy);
        } catch (MaxChangedBlocksException ex) {
            return SchematicSaveResult.failed("Could not build schematic: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return SchematicSaveResult.failed("Could not build schematic: " + ex.getMessage());
        }

        ClipboardFormat format = ClipboardFormats.findByAlias("schem");
        if (format == null) {
            return SchematicSaveResult.failed("Schematic format is unavailable.");
        }

        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file));
             ClipboardWriter writer = format.getWriter(output)) {
            writer.write(clipboard);
        } catch (IOException ex) {
            return SchematicSaveResult.failed("Could not write schematic: " + ex.getMessage());
        }

        return SchematicSaveResult.success(file, "Saved schematic " + name + " to " + file.getPath());
    }

    public File getSchematicsDirectory() {
        File cached = schematicsDirectory;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (schematicsDirectory != null) {
                return schematicsDirectory;
            }

            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                return null;
            }

            File pluginsFolder = dataFolder.getParentFile();
            if (pluginsFolder == null) {
                pluginsFolder = new File(plugin.getServer().getWorldContainer(), "plugins");
            }
            if (!pluginsFolder.exists() && !pluginsFolder.mkdirs()) {
                return null;
            }

            File schematicsFolder = new File(pluginsFolder, "schematics");
            if (!schematicsFolder.exists() && !schematicsFolder.mkdirs()) {
                return null;
            }

            schematicsDirectory = schematicsFolder;
            return schematicsFolder;
        }
    }

    private BlockVector3 resolveOrigin(Player player, Selection selection, String originMode) {
        String mode = originMode == null ? "min" : originMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "player" -> {
                Location location = player.getLocation();
                yield BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            }
            case "center", "selection-center", "selection" -> BlockVector3.at(
                    (selection.getMinX() + selection.getMaxX()) / 2,
                    (selection.getMinY() + selection.getMaxY()) / 2,
                    (selection.getMinZ() + selection.getMaxZ()) / 2
            );
            case "min", "selection-min", "origin" -> BlockVector3.at(
                    selection.getMinX(),
                    selection.getMinY(),
                    selection.getMinZ()
            );
            default -> BlockVector3.at(
                    selection.getMinX(),
                    selection.getMinY(),
                    selection.getMinZ()
            );
        };
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            }
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * List available schematics with optional filter.
     */
    public List<String> listSchematics(String filter) {
        File directory = getSchematicsDirectory();
        if (directory == null || !directory.exists()) {
            return Collections.emptyList();
        }

        File[] files = directory.listFiles((dir, name) -> 
            name.toLowerCase(Locale.ROOT).endsWith(".schem") || name.toLowerCase(Locale.ROOT).endsWith(".schematic"));
        
        if (files == null) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();
        String f = filter != null ? filter.toLowerCase(Locale.ROOT) : "";
        for (File file : files) {
            String name = normalizeName(file.getName().replaceFirst("[.][^.]+$", ""));
            if (name != null && (f.isEmpty() || name.startsWith(f))) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    
    /**
     * Load a schematic file and convert it to a Bayzyl Clipboard.
     * 
     * @param player The player loading the schematic
     * @param schematicName The name of the schematic file (without extension)
     * @return A result containing the loaded Clipboard or an error message
     */
    public LoadResult loadSchematic(Player player, String schematicName) {
        if (player == null) {
            return LoadResult.failed("Players only.");
        }
        
        String name = normalizeName(schematicName);
        if (name == null) {
            return LoadResult.failed("A schematic name is required.");
        }
        
        File directory = getSchematicsDirectory();
        if (directory == null) {
            return LoadResult.failed("Could not access the schematics folder.");
        }
        
        // Try common schematic extensions
        File[] possibleFiles = {
            new File(directory, name + ".schem"),
            new File(directory, name + ".schematic"),
            new File(directory, name + ".schem")
        };
        
        File file = null;
        for (File f : possibleFiles) {
            if (f.exists() && f.isFile()) {
                file = f;
                break;
            }
        }
        
        if (file == null) {
            // Try to find any file with the name (case-insensitive)
            File[] allFiles = directory.listFiles();
            if (allFiles != null) {
                for (File f : allFiles) {
                    String fileName = f.getName().toLowerCase(Locale.ROOT);
                    if (fileName.startsWith(name.toLowerCase(Locale.ROOT)) && 
                        (fileName.endsWith(".schem") || fileName.endsWith(".schematic"))) {
                        file = f;
                        break;
                    }
                }
            }
        }
        
        if (file == null || !file.exists()) {
            return LoadResult.failed("Schematic '" + name + "' not found in schematics folder.");
        }
        
        try {
            // Determine the format from the file extension
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                return LoadResult.failed("Unsupported schematic format for file: " + file.getName());
            }
            
            // Read the schematic
            Clipboard worldEditClipboard;
            try (ClipboardReader reader = format.getReader(new java.io.FileInputStream(file))) {
                worldEditClipboard = reader.read();
            }
            
            // Convert WorldEdit clipboard to Bayzyl clipboard
            com.bayzyl.Clipboard bayzylClipboard = convertToBayzylClipboard(worldEditClipboard, player.getLocation());
            
            return LoadResult.success(bayzylClipboard, "Loaded schematic '" + name + "' (" + 
                worldEditClipboard.getDimensions().getX() + "x" +
                worldEditClipboard.getDimensions().getY() + "x" +
                worldEditClipboard.getDimensions().getZ() + ")");
                
        } catch (java.io.FileNotFoundException e) {
            return LoadResult.failed("Schematic file not found: " + e.getMessage());
        } catch (java.io.IOException e) {
            return LoadResult.failed("Could not read schematic: " + e.getMessage());
        } catch (Exception e) {
            return LoadResult.failed("Error loading schematic: " + e.getMessage());
        }
    }
    
    /**
     * Convert a WorldEdit Clipboard to a Bayzyl Clipboard.
     */
    private com.bayzyl.Clipboard convertToBayzylClipboard(Clipboard worldEditClipboard, Location playerLocation) {
        BlockVector3 min = worldEditClipboard.getMinimumPoint();
        BlockVector3 max = worldEditClipboard.getMaximumPoint();
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        org.bukkit.block.data.BlockData[] blockData = new org.bukkit.block.data.BlockData[sizeX * sizeY * sizeZ];
        org.bukkit.block.BlockState[] blockStates = new org.bukkit.block.BlockState[sizeX * sizeY * sizeZ];

        BlockVector3 originVec = worldEditClipboard.getOrigin();
        if (originVec == null) {
            originVec = min;
        }

        org.bukkit.Location origin = new org.bukkit.Location(
                playerLocation.getWorld(),
                originVec.getX(),
                originVec.getY(),
                originVec.getZ()
        );

        int index = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockVector3 position = BlockVector3.at(min.getX() + x, min.getY() + y, min.getZ() + z);
                    com.sk89q.worldedit.world.block.BaseBlock block = worldEditClipboard.getFullBlock(position);
                    if (block == null || block.getBlockType().getMaterial().isAir()) {
                        blockData[index] = org.bukkit.Material.AIR.createBlockData();
                        blockStates[index] = null;
                    } else {
                        blockData[index] = BukkitAdapter.adapt(block);
                        blockStates[index] = null;
                    }
                    index++;
                }
            }
        }

        int minOffsetX = min.getX() - originVec.getX();
        int minOffsetY = min.getY() - originVec.getY();
        int minOffsetZ = min.getZ() - originVec.getZ();

        java.util.List<ClipboardEntity> entities = new java.util.ArrayList<>();

        return new com.bayzyl.Clipboard(
                sizeX, sizeY, sizeZ,
                blockData, blockStates, entities,
                origin, minOffsetX, minOffsetY, minOffsetZ
        );
    }
}
