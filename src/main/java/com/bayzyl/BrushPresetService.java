package com.bayzyl;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class BrushPresetService {
    private static final int PROFILE_VERSION = 1;
    private static final Set<String> RESERVED_NAMES = buildReservedNames();

    private final File file;
    private final YamlConfiguration yaml;

    public BrushPresetService(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "brushes.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> listBrushes() {
        var section = yaml.getConfigurationSection("brushes");
        if (section == null) {
            return List.of();
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(section.getKeys(false));
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public boolean saveBrush(Player player, String rawName, ItemStack item) {
        String name = normalizeName(rawName);
        if (name == null || item == null || !isSavableBrush(item) || reservedNameOwner(name) != null) {
            return false;
        }

        String path = "brushes." + name;
        yaml.set(path, null);
        yaml.set(path + ".version", PROFILE_VERSION);
        yaml.set(path + ".updated_at", System.currentTimeMillis());
        yaml.set(path + ".item", item.clone());
        return saveFile();
    }

    public ItemStack loadBrush(String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            return null;
        }
        String path = "brushes." + name;
        if (!yaml.contains(path + ".item")) {
            return null;
        }
        ItemStack item = yaml.getItemStack(path + ".item");
        if (item == null) {
            return null;
        }
        return item.clone();
    }

    public boolean deleteBrush(String rawName) {
        String name = normalizeName(rawName);
        if (name == null || !yaml.contains("brushes." + name)) {
            return false;
        }
        yaml.set("brushes." + name, null);
        return saveFile();
    }

    public boolean existsBrush(String rawName) {
        String name = normalizeName(rawName);
        return name != null && yaml.contains("brushes." + name);
    }

    public boolean isSavableBrush(ItemStack item) {
        return item != null && item.getType().isItem() && item.getType() == org.bukkit.Material.BRUSH;
    }

    public String reservedNameOwner(String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            return "an empty or invalid name";
        }
        if (CommandRegistry.getTopLevel().stream().anyMatch(spec -> spec.name().equalsIgnoreCase(name))) {
            return "Bayzyl top-level command /" + name;
        }
        if (CommandRegistry.getBzlSubcommands().stream().anyMatch(spec -> spec.name().equalsIgnoreCase(name))) {
            return "Bayzyl /bzl subcommand '" + name + "'";
        }
        if (RESERVED_NAMES.contains(name)) {
            return "a reserved server/plugin command name '" + name + "'";
        }
        if (name.indexOf('.') >= 0) {
            // The name becomes part of a YAML path, so "a.item" would overwrite brush "a".
            return "an empty or invalid name";
        }
        return null;
    }

    private boolean saveFile() {
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (char c : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            }
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    private static Set<String> buildReservedNames() {
        Set<String> reserved = new java.util.HashSet<>();
        reserved.addAll(List.of(
                "tool", "give", "get", "set", "param", "info", "describe", "presets", "list",
                "save", "load", "variants", "saved", "delete", "remove", "mode", "none", "unbind",
                "bzl", "bayzyl", "bzlhelp", "detailbrush", "db", "kit", "kitmake", "kithelp",
                "kitlist", "kitupdate", "kitconfirm", "profile", "tabmenu", "nudge", "ramalert",
                "bzltoggle", "eraser", "wand", "select", "minecraft", "bukkit", "paper",
                "spigot", "fawe", "worldedit", "we", "essentials"
        ));
        CommandRegistry.getTopLevel().forEach(spec -> reserved.add(spec.name()));
        CommandRegistry.getBzlSubcommands().forEach(spec -> reserved.add(spec.name()));
        reserved.addAll(List.of(
                "help", "stop", "reload", "restart", "kick", "ban", "banip", "pardon", "pardonip",
                "whitelist", "op", "deop", "gamemode", "defaultgamemode", "difficulty", "time",
                "weather", "tp", "teleport", "give", "clear", "summon", "kill", "setblock", "fill",
                "clone", "data", "execute", "say", "me", "msg", "tell", "w", "tellraw", "title",
                "team", "bossbar", "trigger", "function", "schedule", "scoreboard", "tag",
                "advancement", "recipe", "attribute", "effect", "enchant", "xp", "experience",
                "loot", "item", "particle", "playsound", "place", "locate", "locatebiome",
                "spreadplayers", "gamerule", "worldborder", "saveall", "saveon", "saveoff",
                "save-all", "save-on", "save-off", "ban-ip", "pardon-ip", "publish", "seed",
                "perf", "version", "plugins", "pl", "teammsg", "tm", "debug"
        ));
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(reserved));
    }
}
