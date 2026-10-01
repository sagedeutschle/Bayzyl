package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SelectionBookmarkService {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public SelectionBookmarkService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "selection-bookmarks.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean save(UUID playerId, String rawName, Selection selection) {
        String name = normalizeName(rawName);
        if (name == null || selection == null || !selection.isComplete()) {
            return false;
        }
        String path = path(playerId, name);
        yaml.set(path + ".world", selection.getPos1().getWorld().getName());
        yaml.set(path + ".pos1.x", selection.getPos1().getBlockX());
        yaml.set(path + ".pos1.y", selection.getPos1().getBlockY());
        yaml.set(path + ".pos1.z", selection.getPos1().getBlockZ());
        yaml.set(path + ".pos2.x", selection.getPos2().getBlockX());
        yaml.set(path + ".pos2.y", selection.getPos2().getBlockY());
        yaml.set(path + ".pos2.z", selection.getPos2().getBlockZ());
        yaml.set(path + ".type", selection.getType().name());
        return saveFile();
    }

    public Selection load(UUID playerId, String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            return null;
        }
        String path = path(playerId, name);
        if (!yaml.contains(path + ".world")) {
            return null;
        }
        World world = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (world == null) {
            return null;
        }
        Location pos1 = new Location(
                world,
                yaml.getInt(path + ".pos1.x"),
                yaml.getInt(path + ".pos1.y"),
                yaml.getInt(path + ".pos1.z")
        );
        Location pos2 = new Location(
                world,
                yaml.getInt(path + ".pos2.x"),
                yaml.getInt(path + ".pos2.y"),
                yaml.getInt(path + ".pos2.z")
        );
        SelectionType type = SelectionType.CUBOID;
        String rawType = yaml.getString(path + ".type", SelectionType.CUBOID.name());
        try {
            type = SelectionType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        return new Selection(pos1, pos2, type);
    }

    public static String encodeShareCode(Selection selection) {
        if (selection == null || !selection.isComplete()) {
            return null;
        }
        Location p1 = selection.getPos1();
        Location p2 = selection.getPos2();
        return String.format(Locale.ROOT, "*1S(%s;%d,%d,%d;%d,%d,%d;%s)",
                p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ(),
                selection.getType().name());
    }

    public static Selection decodeShareCode(String rawCode, World fallbackWorld) {
        if (rawCode == null) {
            return null;
        }
        String code = rawCode.trim();
        if (!code.startsWith("*1S(") || !code.endsWith(")")) {
            return null;
        }
        String inner = code.substring(4, code.length() - 1);
        String[] parts = inner.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            world = fallbackWorld;
        }
        if (world == null) {
            return null;
        }
        String[] a = parts[1].split(",");
        String[] b = parts[2].split(",");
        if (a.length != 3 || b.length != 3) {
            return null;
        }
        try {
            Location pos1 = new Location(world,
                    Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            Location pos2 = new Location(world,
                    Integer.parseInt(b[0]), Integer.parseInt(b[1]), Integer.parseInt(b[2]));
            SelectionType type = SelectionType.CUBOID;
            try {
                type = SelectionType.valueOf(parts[3].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
            return new Selection(pos1, pos2, type);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public List<String> list(UUID playerId) {
        ConfigurationSection section = yaml.getConfigurationSection("players." + playerId);
        if (section == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(section.getKeys(false));
        Collections.sort(names);
        return names;
    }

    private String path(UUID playerId, String name) {
        return "players." + playerId + "." + name;
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[a-z0-9_-]{1,32}")) {
            return null;
        }
        return trimmed;
    }

    private boolean saveFile() {
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save selection-bookmarks.yml: " + ex.getMessage());
            return false;
        }
    }
}
