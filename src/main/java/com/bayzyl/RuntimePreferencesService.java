package com.bayzyl;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class RuntimePreferencesService {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration configuration;

    public RuntimePreferencesService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "runtime-preferences.yml");
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public void loadPlayer(Player player,
                           NightVisionService nightVisionService,
                           AutoUnstickService autoUnstickService,
                           GhostHandService ghostHandService,
                           StackLookDirectionService stackLookDirectionService,
                           StackAutoMoveService stackAutoMoveService,
                           NudgeSettingsService nudgeSettingsService,
                           VisualizationManager visualizationManager,
                           TabMenuSettingsService tabMenuSettingsService,
                           RecentEditTrailService recentEditTrailService) {
        String path = "players." + player.getUniqueId();
        if (!configuration.contains(path)) {
            stackLookDirectionService.setEnabled(player, true);
            return;
        }

        nightVisionService.setEnabled(player, configuration.getBoolean(path + ".night_vision", false));
        autoUnstickService.setEnabled(player, configuration.getBoolean(path + ".auto_unstick", false));
        ghostHandService.setEnabled(player, configuration.getBoolean(path + ".ghost_hand", false));
        stackLookDirectionService.setEnabled(player, configuration.getBoolean(path + ".stack_look_direction", true));
        stackAutoMoveService.setEnabled(player.getUniqueId(), configuration.getBoolean(path + ".stack_auto_move", true));

        boolean inverted = configuration.getBoolean(path + ".nudge.inverted", NudgeSettings.defaults().inverted());
        int step = configuration.getInt(path + ".nudge.step", NudgeSettings.defaults().step());
        NudgeSettings.VerticalMode verticalMode = parseVerticalMode(configuration.getString(path + ".nudge.vertical"), NudgeSettings.defaults().verticalMode());
        nudgeSettingsService.set(player, new NudgeSettings(inverted, step, verticalMode));

        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        settings.setEnabled(configuration.getBoolean(path + ".selection_particles.enabled", true));
        settings.setIntensity(parseIntensity(configuration.getString(path + ".selection_particles.intensity"), VisualizationSettings.Intensity.MEDIUM));
        settings.setGridMode(parseGridMode(configuration.getString(path + ".selection_particles.grid"), VisualizationSettings.GridMode.AUTO));
        settings.setConsistency(configuration.getInt(path + ".selection_particles.consistency", 10));
        String colorValue = configuration.getString(path + ".selection_particles.color");
        settings.setColor(parseColor(colorValue));

        for (TabMenuModule module : TabMenuModule.values()) {
            boolean enabled = configuration.getBoolean(path + ".tab_menu." + module.key(), module.defaultEnabled());
            tabMenuSettingsService.setEnabled(player.getUniqueId(), module, enabled);
        }
        recentEditTrailService.setLimit(player.getUniqueId(),
                configuration.getInt(path + ".tab_menu.trail_limit", recentEditTrailService.defaultLimit()));
    }

    public void savePlayer(Player player,
                           NightVisionService nightVisionService,
                           AutoUnstickService autoUnstickService,
                           GhostHandService ghostHandService,
                           StackLookDirectionService stackLookDirectionService,
                           StackAutoMoveService stackAutoMoveService,
                           NudgeSettingsService nudgeSettingsService,
                           VisualizationManager visualizationManager,
                           TabMenuSettingsService tabMenuSettingsService,
                           RecentEditTrailService recentEditTrailService) {
        String path = "players." + player.getUniqueId();
        configuration.set(path + ".admin_mode", null);
        configuration.set(path + ".night_vision", nightVisionService.isEnabled(player));
        configuration.set(path + ".auto_unstick", autoUnstickService.isEnabled(player));
        configuration.set(path + ".ghost_hand", ghostHandService.isEnabled(player));
        configuration.set(path + ".stack_look_direction", stackLookDirectionService.isEnabled(player));
        configuration.set(path + ".stack_auto_move", stackAutoMoveService.isEnabled(player.getUniqueId()));

        NudgeSettings nudge = nudgeSettingsService.get(player);
        configuration.set(path + ".nudge.inverted", nudge.inverted());
        configuration.set(path + ".nudge.step", nudge.step());
        configuration.set(path + ".nudge.vertical", nudge.verticalMode().name());

        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        configuration.set(path + ".selection_particles.enabled", settings.isEnabled());
        configuration.set(path + ".selection_particles.intensity", settings.getIntensity().name());
        configuration.set(path + ".selection_particles.grid", settings.getGridMode().name());
        configuration.set(path + ".selection_particles.consistency", settings.getConsistency());
        configuration.set(path + ".selection_particles.color", formatColor(settings.getColor()));
        for (TabMenuModule module : TabMenuModule.values()) {
            configuration.set(path + ".tab_menu." + module.key(), tabMenuSettingsService.isEnabled(player.getUniqueId(), module));
        }
        configuration.set(path + ".tab_menu.trail_limit", recentEditTrailService.limit(player.getUniqueId()));
        save();
    }

    public void loadGlobal(RamAlertService ramAlertService) {
        ConfigurationSection section = configuration.getConfigurationSection("global.ram_alert");
        if (section == null) {
            return;
        }
        RamAlertSettings current = ramAlertService.getSettings();
        RamAlertSettings loaded = new RamAlertSettings(
                section.getBoolean("enabled", current.enabled()),
                section.getInt("threshold", current.thresholdPercent()),
                section.getInt("interval", current.intervalSeconds()),
                section.getInt("cooldown", current.cooldownSeconds())
        );
        ramAlertService.update(loaded);
    }

    public void loadGlobal(RamAlertService ramAlertService, MessageThemeService messageThemeService) {
        loadGlobal(ramAlertService);
        ConfigurationSection section = configuration.getConfigurationSection("global.message_theme");
        if (section == null) {
            return;
        }
        messageThemeService.setAccent(section.getString("accent", messageThemeService.accentValue()));
    }

    public void loadGlobal(RamAlertService ramAlertService,
                           MessageThemeService messageThemeService,
                           CommandAuthorityService commandAuthorityService) {
        loadGlobal(ramAlertService, messageThemeService);
        ConfigurationSection section = configuration.getConfigurationSection("global.command_authority");
        if (section == null) {
            commandAuthorityService.setBayzylPrimaryCommandsEnabled(true);
            return;
        }
        commandAuthorityService.setBayzylPrimaryCommandsEnabled(section.getBoolean("bayzyl_primary_commands", true));
    }

    public void saveGlobal(RamAlertService ramAlertService, MessageThemeService messageThemeService) {
        RamAlertSettings settings = ramAlertService.getSettings();
        String path = "global.ram_alert";
        configuration.set(path + ".enabled", settings.enabled());
        configuration.set(path + ".threshold", settings.thresholdPercent());
        configuration.set(path + ".interval", settings.intervalSeconds());
        configuration.set(path + ".cooldown", settings.cooldownSeconds());
        configuration.set("global.message_theme.accent", messageThemeService.accentValue());
        save();
    }

    public void saveGlobal(RamAlertService ramAlertService,
                           MessageThemeService messageThemeService,
                           CommandAuthorityService commandAuthorityService) {
        RamAlertSettings settings = ramAlertService.getSettings();
        String path = "global.ram_alert";
        configuration.set(path + ".enabled", settings.enabled());
        configuration.set(path + ".threshold", settings.thresholdPercent());
        configuration.set(path + ".interval", settings.intervalSeconds());
        configuration.set(path + ".cooldown", settings.cooldownSeconds());
        configuration.set("global.message_theme.accent", messageThemeService.accentValue());
        configuration.set("global.command_authority.bayzyl_primary_commands",
                commandAuthorityService.isBayzylPrimaryCommandsEnabled());
        save();
    }

    public void saveCommandAuthority(CommandAuthorityService commandAuthorityService) {
        configuration.set("global.command_authority.bayzyl_primary_commands",
                commandAuthorityService.isBayzylPrimaryCommandsEnabled());
        save();
    }

    private void save() {
        try {
            configuration.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save runtime preferences: " + ex.getMessage());
        }
    }

    private NudgeSettings.VerticalMode parseVerticalMode(String value, NudgeSettings.VerticalMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return NudgeSettings.VerticalMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private VisualizationSettings.Intensity parseIntensity(String value, VisualizationSettings.Intensity fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return VisualizationSettings.Intensity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private VisualizationSettings.GridMode parseGridMode(String value, VisualizationSettings.GridMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return VisualizationSettings.GridMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Color parseColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("#") && value.length() == 7) {
            try {
                int rgb = Integer.parseInt(value.substring(1), 16);
                return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String formatColor(Color color) {
        if (color == null) {
            return null;
        }
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
