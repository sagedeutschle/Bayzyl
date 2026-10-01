package com.bayzyl;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HelpContentService {
    private final FileConfiguration configuration;
    private final MessageThemeService messageThemeService;

    public HelpContentService(JavaPlugin plugin, MessageThemeService messageThemeService) {
        this.messageThemeService = messageThemeService;
        InputStream resource = plugin.getResource("help-pages.yml");
        if (resource == null) {
            plugin.getLogger().warning("help-pages.yml is missing from the Bayzyl jar; /bzlhelp will return no content.");
            configuration = new YamlConfiguration();
            return;
        }
        configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }

    public void sendPage(CommandSender sender, int page, String editBackend, String shapeBackend) {
        List<String> lines = configuration.getStringList("pages." + page);
        if (lines.isEmpty()) {
            ChatOutput.error(sender, "Unknown help page.");
            return;
        }
        for (String line : lines) {
            String formatted = line
                    .replace("%EDIT_BACKEND%", editBackend)
                    .replace("%SHAPE_BACKEND%", shapeBackend);
            ChatOutput.rail(sender, messageThemeService.applyAccent(formatted));
        }
    }

    public List<String> getPageLines(int page) {
        List<String> lines = configuration.getStringList("pages." + page);
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(lines);
    }

    public boolean mentionsCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        var section = configuration.getConfigurationSection("pages");
        if (section == null) {
            return false;
        }
        String needle = "/" + command.toLowerCase(java.util.Locale.ROOT);
        for (String pageKey : section.getKeys(false)) {
            for (String line : configuration.getStringList("pages." + pageKey)) {
                if (containsCommandToken(line, needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getPageCount() {
        var section = configuration.getConfigurationSection("pages");
        return section == null ? 0 : section.getKeys(false).size();
    }

    private boolean containsCommandToken(String rawLine, String needle) {
        if (rawLine == null || rawLine.isBlank()) {
            return false;
        }
        String line = rawLine.toLowerCase(java.util.Locale.ROOT);
        int index = line.indexOf(needle);
        while (index >= 0) {
            int next = index + needle.length();
            if (next >= line.length() || !isCommandNameCharacter(line.charAt(next))) {
                return true;
            }
            index = line.indexOf(needle, next);
        }
        return false;
    }

    private boolean isCommandNameCharacter(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9')
                || value == '_'
                || value == '-';
    }
}
