package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ListMenuConfigService {
    private static final String RESOURCE_NAME = "list-menus.yml";

    private final YamlConfiguration configuration;
    private final YamlConfiguration defaults;
    private final MessageThemeService messageThemeService;

    public ListMenuConfigService(JavaPlugin plugin, MessageThemeService messageThemeService) {
        this.messageThemeService = messageThemeService;
        File file = new File(plugin.getDataFolder(), RESOURCE_NAME);
        boolean savedDefault = false;
        if (!file.exists() && plugin.getResource(RESOURCE_NAME) != null) {
            plugin.saveResource(RESOURCE_NAME, false);
            savedDefault = true;
        }
        if (!file.exists()) {
            plugin.getLogger().warning(RESOURCE_NAME + " is missing; generated list menus will use built-in fallbacks.");
        } else if (!savedDefault) {
            appendMissingDefaultSections(plugin, file);
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration loadedDefaults = new YamlConfiguration();
        InputStream resource = plugin.getResource(RESOURCE_NAME);
        if (resource != null) {
            loadedDefaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            this.configuration.setDefaults(loadedDefaults);
        }
        this.defaults = loadedDefaults;
    }

    public String value(String menuId, String path, String fallback) {
        String fullPath = fullPath(menuId, path);
        String value = configuration.getString(fullPath);
        if (value == null) {
            value = defaults.getString(fullPath);
        }
        if (value == null) {
            value = fallback;
        }
        return value == null ? "" : value;
    }

    public String format(String menuId, String path, String fallback, Map<String, String> tokens) {
        return colorize(applyTokens(value(menuId, path, fallback), tokens));
    }

    public List<String> stringList(String menuId, String path, List<String> fallback) {
        String fullPath = fullPath(menuId, path);
        List<String> values = configuration.getStringList(fullPath);
        if (values.isEmpty()) {
            values = defaults.getStringList(fullPath);
        }
        if (values.isEmpty()) {
            return fallback == null ? List.of() : new ArrayList<>(fallback);
        }
        return values;
    }

    public List<String> formatList(String menuId, String path, List<String> fallback, Map<String, String> tokens) {
        List<String> lines = stringList(menuId, path, fallback);
        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            formatted.add(colorize(applyTokens(line, tokens)));
        }
        return formatted;
    }

    public int intValue(String menuId, String path, int fallback, int min, int max) {
        String fullPath = fullPath(menuId, path);
        int value = configuration.contains(fullPath)
                ? configuration.getInt(fullPath)
                : defaults.getInt(fullPath, fallback);
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public Material material(String menuId, String path, Material fallback) {
        String raw = value(menuId, path, fallback.name());
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            return fallback;
        }
        return material;
    }

    public static Map<String, String> tokens(String... pairs) {
        Map<String, String> tokens = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            tokens.put(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return tokens;
    }

    private String fullPath(String menuId, String path) {
        return "menus." + menuId + "." + path;
    }

    private void appendMissingDefaultSections(JavaPlugin plugin, File file) {
        InputStream resource = plugin.getResource(RESOURCE_NAME);
        if (resource == null) {
            return;
        }
        try {
            String bundled = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration bundledConfig = YamlConfiguration.loadConfiguration(new StringReader(bundled));
            ConfigurationSection bundledMenus = bundledConfig.getConfigurationSection("menus");
            if (bundledMenus == null) {
                return;
            }

            List<String> missing = new ArrayList<>();
            for (String menuId : bundledMenus.getKeys(false)) {
                if (!current.isConfigurationSection("menus." + menuId)) {
                    missing.add(menuId);
                }
            }
            if (missing.isEmpty()) {
                return;
            }

            StringBuilder append = new StringBuilder();
            if (!current.isConfigurationSection("menus")) {
                append.append(System.lineSeparator()).append("menus:").append(System.lineSeparator());
            }
            for (String menuId : missing) {
                String section = extractMenuSection(bundled, menuId);
                if (!section.isBlank()) {
                    append.append(System.lineSeparator()).append(section.stripTrailing()).append(System.lineSeparator());
                }
            }
            Files.writeString(file.toPath(), append.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            plugin.getLogger().info("Added missing " + RESOURCE_NAME + " sections: " + String.join(", ", missing));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not update " + RESOURCE_NAME + " with missing default sections: " + ex.getMessage());
        }
    }

    private String extractMenuSection(String bundled, String menuId) {
        String[] lines = bundled.split("\\R", -1);
        String marker = "  " + menuId + ":";
        int sectionStart = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals(marker)) {
                sectionStart = i;
                break;
            }
        }
        if (sectionStart < 0) {
            return "";
        }

        int start = sectionStart;
        while (start > 0 && lines[start - 1].startsWith("  #")) {
            start--;
        }
        if (start > 0 && lines[start - 1].isBlank()) {
            start--;
        }

        int end = sectionStart + 1;
        while (end < lines.length) {
            String line = lines[end];
            if (line.startsWith("  #") || line.matches("^  [A-Za-z0-9_-]+:.*")) {
                break;
            }
            end++;
        }

        StringBuilder section = new StringBuilder();
        for (int i = start; i < end; i++) {
            section.append(lines[i]).append(System.lineSeparator());
        }
        return section.toString();
    }

    private String applyTokens(String value, Map<String, String> tokens) {
        String result = value == null ? "" : value;
        if (tokens == null || tokens.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private String colorize(String value) {
        String themed = messageThemeService.applyAccent(value == null ? "" : value);
        return ChatColor.translateAlternateColorCodes('&', themed);
    }
}
