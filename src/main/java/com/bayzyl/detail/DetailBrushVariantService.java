package com.bayzyl.detail;

import com.bayzyl.CommandRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class DetailBrushVariantService {
    private static final Set<String> RESERVED_NAMES = buildReservedNames();
    private final File file;
    private final YamlConfiguration yaml;
    private final DetailBrushSafety safety;

    public DetailBrushVariantService(JavaPlugin plugin, DetailBrushSafety safety) {
        if (safety == null) {
            throw new IllegalArgumentException("Detail brush safety is required.");
        }
        this.safety = safety;
        this.file = new File(plugin.getDataFolder(), "detail-brushes.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean save(String rawName, DetailBrushSettings settings) {
        String name = normalize(rawName);
        if (name == null || isReservedName(name) || !isSavableName(name)) {
            return false;
        }
        try {
            settings = safety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String path = "variants." + name;
        yaml.set(path, null);
        yaml.set(path + ".preset", settings.presetId());
        yaml.set(path + ".mode", settings.mode().name());
        yaml.set(path + ".updated_at", System.currentTimeMillis());
        Map<String, String> rawParams = settings.parameters().raw();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            yaml.set(path + ".params." + entry.getKey(), entry.getValue());
        }
        return saveFile();
    }

    public DetailBrushSettings load(String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            return null;
        }
        String path = "variants." + name;
        if (!yaml.contains(path)) {
            return null;
        }
        Object presetRaw = yaml.get(path + ".preset");
        if (!(presetRaw instanceof String presetId)
                || presetId.isBlank() || presetId.length() > DetailBrushSafety.PRESET_ID_MAX) {
            return null;
        }
        DetailBrushMode mode = DetailBrushMode.STAMP;
        if (yaml.contains(path + ".mode")) {
            Object storedMode = yaml.get(path + ".mode");
            if (!(storedMode instanceof String modeRaw)) {
                return null;
            }
            try {
                mode = DetailBrushMode.valueOf(modeRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        Map<String, String> params = new LinkedHashMap<>();
        ConfigurationSection paramsSection = yaml.getConfigurationSection(path + ".params");
        if (yaml.contains(path + ".params") && paramsSection == null) {
            return null;
        }
        if (paramsSection != null) {
            Set<String> keys = paramsSection.getKeys(false);
            if (keys.size() > DetailBrushSafety.PARAMETER_COUNT_MAX) {
                return null;
            }
            for (String key : keys) {
                Object raw = paramsSection.get(key);
                if (!(raw instanceof String value)
                        || key == null || key.length() > DetailBrushSafety.PARAMETER_KEY_MAX
                        || value.length() > DetailBrushSafety.PARAMETER_VALUE_MAX) {
                    return null;
                }
                String normalizedKey = key.toLowerCase(Locale.ROOT);
                if (params.putIfAbsent(normalizedKey, value) != null) {
                    return null;
                }
            }
        }
        DetailBrushSettings settings = new DetailBrushSettings(
                presetId, new DetailBrushParameters(params), mode);
        try {
            return safety.requireValid(settings);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean delete(String rawName) {
        String name = normalize(rawName);
        if (name == null || !yaml.contains("variants." + name)) {
            return false;
        }
        yaml.set("variants." + name, null);
        return saveFile();
    }

    public boolean exists(String rawName) {
        String name = normalize(rawName);
        return name != null && yaml.contains("variants." + name);
    }

    public List<String> list() {
        ConfigurationSection section = yaml.getConfigurationSection("variants");
        if (section == null) {
            return List.of();
        }
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(section.getKeys(false));
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public String normalizeName(String raw) {
        return normalize(raw);
    }

    public boolean isReservedName(String rawName) {
        String name = normalize(rawName);
        return name == null || RESERVED_NAMES.contains(name);
    }

    /**
     * Returns a short human label naming who already claims this variant name,
     * or null if the name is free for use.
     */
    public String reservedNameOwner(String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            return "an empty or invalid name";
        }
        if (CommandRegistry.getTopLevel().stream().anyMatch(s -> s.name().equalsIgnoreCase(name))) {
            return "Bayzyl top-level command /" + name;
        }
        if (CommandRegistry.getBzlSubcommands().stream().anyMatch(s -> s.name().equalsIgnoreCase(name))) {
            return "Bayzyl /bzl subcommand '" + name + "'";
        }
        if (RESERVED_NAMES.contains(name)) {
            return "a reserved server/plugin command name '" + name + "'";
        }
        if (safety.registry().builtInVariant(name) != null) {
            return "the built-in detail brush variant '" + name + "'";
        }
        if (name.indexOf('.') >= 0) {
            return "an empty or invalid name";
        }
        return null;
    }

    /**
     * New variants are stored under a YAML path, so a '.' in the name would nest it inside (or wipe fields of)
     * another variant, and a name that only matches a built-in after normalization would shadow it.
     */
    private boolean isSavableName(String name) {
        return name.indexOf('.') < 0 && safety.registry().builtInVariant(name) == null;
    }

    private boolean saveFile() {
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private String normalize(String raw) {
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
        return builder.length() == 0 ? null : builder.toString();
    }

    private static Set<String> buildReservedNames() {
        Set<String> reserved = new HashSet<>();
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(reserved));
    }
}
