package com.bayzyl;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class BuilderProfileService {
    private static final int PROFILE_VERSION = 1;
    private static final int HOTBAR_SIZE = 9;

    private final JavaPlugin plugin;
    private final VisualizationManager visualizationManager;
    private final NightVisionService nightVisionService;
    private final AutoUnstickService autoUnstickService;
    private final GhostHandService ghostHandService;
    private final StackLookDirectionService stackLookDirectionService;
    private final NudgeSettingsService nudgeSettingsService;
    private final TabMenuSettingsService tabMenuSettingsService;
    private final RecentEditTrailService recentEditTrailService;
    private final TabInfoPanelService tabInfoPanelService;
    private final MessageThemeService messageThemeService;
    private final ToolManager toolManager;
    private final File file;
    private final YamlConfiguration yaml;

    public BuilderProfileService(JavaPlugin plugin,
                                 VisualizationManager visualizationManager,
                                 NightVisionService nightVisionService,
                                 AutoUnstickService autoUnstickService,
                                 GhostHandService ghostHandService,
                                 StackLookDirectionService stackLookDirectionService,
                                 NudgeSettingsService nudgeSettingsService,
                                 TabMenuSettingsService tabMenuSettingsService,
                                 RecentEditTrailService recentEditTrailService,
                                 TabInfoPanelService tabInfoPanelService,
                                 MessageThemeService messageThemeService,
                                 ToolManager toolManager) {
        this.plugin = plugin;
        this.visualizationManager = visualizationManager;
        this.nightVisionService = nightVisionService;
        this.autoUnstickService = autoUnstickService;
        this.ghostHandService = ghostHandService;
        this.stackLookDirectionService = stackLookDirectionService;
        this.nudgeSettingsService = nudgeSettingsService;
        this.tabMenuSettingsService = tabMenuSettingsService;
        this.recentEditTrailService = recentEditTrailService;
        this.tabInfoPanelService = tabInfoPanelService;
        this.messageThemeService = messageThemeService;
        this.toolManager = toolManager;
        this.file = new File(plugin.getDataFolder(), "profiles.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> listProfiles() {
        ConfigurationSection section = yaml.getConfigurationSection("profiles");
        if (section == null) {
            return List.of();
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(section.getKeys(false));
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public List<BuilderProfileSummary> listProfileSummaries() {
        return listProfiles().stream()
                .map(this::loadProfile)
                .filter(profile -> profile != null)
                .map(this::toSummary)
                .sorted(Comparator.comparing(BuilderProfileSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean saveProfile(Player player, String rawName, BuilderProfileType type, boolean overwrite) {
        String name = normalizeName(rawName);
        if (name == null) {
            return false;
        }
        if (!overwrite && exists(name)) {
            return false;
        }
        BuilderProfileType resolvedType = type == null ? BuilderProfileType.COMBINED : type;
        BuilderProfile profile = new BuilderProfile(
                name,
                PROFILE_VERSION,
                Instant.now().toEpochMilli(),
                resolvedType,
                resolvedType.hasConfig() ? captureConfig(player) : null,
                resolvedType.hasToolbar() ? captureToolbar(player) : List.of()
        );
        return writeProfile(profile);
    }

    public boolean updateProfile(Player player, String rawName, BuilderProfileLoadSection section) {
        BuilderProfile existing = loadProfile(rawName);
        if (existing == null) {
            return false;
        }
        BuilderProfileLoadSection resolvedSection = section == null ? BuilderProfileLoadSection.ALL : section;
        BuilderProfileConfig config = existing.config();
        List<BuilderProfile.ToolbarSlot> toolbarSlots = existing.toolbarSlots();
        BuilderProfileType type = existing.type();

        if (resolvedSection == BuilderProfileLoadSection.ALL || resolvedSection == BuilderProfileLoadSection.CONFIG) {
            config = captureConfig(player);
            type = type.hasToolbar() && (resolvedSection == BuilderProfileLoadSection.CONFIG)
                    ? BuilderProfileType.COMBINED
                    : (resolvedSection == BuilderProfileLoadSection.ALL ? BuilderProfileType.COMBINED : BuilderProfileType.CONFIG);
        }

        if (resolvedSection == BuilderProfileLoadSection.ALL || resolvedSection == BuilderProfileLoadSection.TOOLBAR) {
            toolbarSlots = captureToolbar(player);
            if (resolvedSection == BuilderProfileLoadSection.ALL) {
                type = BuilderProfileType.COMBINED;
            } else if (config != null) {
                type = BuilderProfileType.COMBINED;
            } else {
                type = BuilderProfileType.TOOLBAR;
            }
        }

        if (resolvedSection == BuilderProfileLoadSection.CONFIG && existing.type().hasToolbar()) {
            type = BuilderProfileType.COMBINED;
        }
        if (resolvedSection == BuilderProfileLoadSection.TOOLBAR && existing.config() != null) {
            type = BuilderProfileType.COMBINED;
        }

        BuilderProfile updated = new BuilderProfile(
                existing.name(),
                PROFILE_VERSION,
                Instant.now().toEpochMilli(),
                type,
                type.hasConfig() ? config : null,
                type.hasToolbar() ? toolbarSlots : List.of()
        );
        return writeProfile(updated);
    }

    public boolean renameProfile(String fromRaw, String toRaw, boolean overwrite) {
        String from = normalizeName(fromRaw);
        String to = normalizeName(toRaw);
        if (from == null || to == null || !exists(from)) {
            return false;
        }
        if (!overwrite && exists(to)) {
            return false;
        }
        BuilderProfile profile = loadProfile(from);
        if (profile == null) {
            return false;
        }
        BuilderProfile renamed = new BuilderProfile(
                to,
                profile.version(),
                Instant.now().toEpochMilli(),
                profile.type(),
                profile.config(),
                profile.toolbarSlots()
        );
        yaml.set("profiles." + from, null);
        return writeProfile(renamed);
    }

    public boolean duplicateProfile(String fromRaw, String toRaw, boolean overwrite) {
        String from = normalizeName(fromRaw);
        String to = normalizeName(toRaw);
        if (from == null || to == null || !exists(from)) {
            return false;
        }
        if (!overwrite && exists(to)) {
            return false;
        }
        BuilderProfile source = loadProfile(from);
        if (source == null) {
            return false;
        }
        BuilderProfile copy = new BuilderProfile(
                to,
                source.version(),
                Instant.now().toEpochMilli(),
                source.type(),
                source.config(),
                source.toolbarSlots()
        );
        return writeProfile(copy);
    }

    public boolean existsProfile(String rawName) {
        String name = normalizeName(rawName);
        return name != null && exists(name);
    }

    public BuilderProfileType defaultSaveType() {
        return BuilderProfileType.COMBINED;
    }

    public BuilderProfileLoadSection defaultLoadSection() {
        return BuilderProfileLoadSection.ALL;
    }

    private boolean exists(String normalizedName) {
        return yaml.contains("profiles." + normalizedName);
    }

    private boolean writeProfile(BuilderProfile profile) {
        String path = "profiles." + profile.name();
        yaml.set(path, null);
        yaml.set(path + ".version", profile.version());
        yaml.set(path + ".updated_at", profile.updatedAtEpochMillis());
        yaml.set(path + ".type", profile.type().key());
        writeConfig(path, profile.config());
        writeToolbar(path, profile.toolbarSlots());
        return saveFile();
    }

    public BuilderProfile loadProfile(String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            return null;
        }
        String path = "profiles." + name;
        if (!yaml.contains(path)) {
            return null;
        }
        if (!yaml.isString(path + ".type")) {
            return loadLegacyProfile(name, path);
        }

        BuilderProfileType type = BuilderProfileType.fromKey(yaml.getString(path + ".type"));
        if (type == null) {
            return null;
        }
        int version = yaml.getInt(path + ".version", PROFILE_VERSION);
        long updatedAt = yaml.getLong(path + ".updated_at", 0L);
        BuilderProfileConfig config = type.hasConfig() ? readConfig(path) : null;
        List<BuilderProfile.ToolbarSlot> toolbarSlots = type.hasToolbar() ? readToolbar(path) : List.of();
        return new BuilderProfile(name, version, updatedAt, type, config, toolbarSlots);
    }

    public BuilderProfileLoadResult applyProfile(Player player, String rawName, BuilderProfileLoadSection section) {
        BuilderProfile profile = loadProfile(rawName);
        if (profile == null) {
            return null;
        }

        BuilderProfileLoadSection resolvedSection = section == null ? BuilderProfileLoadSection.ALL : section;
        boolean appliedConfig = false;
        boolean appliedToolbar = false;
        int placed = 0;
        int cleared = 0;
        int skipped = 0;

        if ((resolvedSection == BuilderProfileLoadSection.ALL || resolvedSection == BuilderProfileLoadSection.CONFIG)
                && profile.config() != null) {
            applyConfig(player, profile.config());
            appliedConfig = true;
        }

        if ((resolvedSection == BuilderProfileLoadSection.ALL || resolvedSection == BuilderProfileLoadSection.TOOLBAR)
                && profile.type().hasToolbar()) {
            ToolbarApplyResult toolbarResult = applyToolbar(player, profile.toolbarSlots());
            appliedToolbar = true;
            placed = toolbarResult.placed();
            cleared = toolbarResult.cleared();
            skipped = toolbarResult.skipped();
        }

        return new BuilderProfileLoadResult(profile.name(), profile.type(), appliedConfig, appliedToolbar, placed, cleared, skipped);
    }

    public boolean deleteProfile(String rawName) {
        String name = normalizeName(rawName);
        if (name == null || !yaml.contains("profiles." + name)) {
            return false;
        }
        yaml.set("profiles." + name, null);
        return saveFile();
    }

    public BuilderProfileSummary summarize(String rawName) {
        BuilderProfile profile = loadProfile(rawName);
        return profile == null ? null : toSummary(profile);
    }

    private boolean saveFile() {
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save profiles.yml: " + ex.getMessage());
            return false;
        }
    }

    private BuilderProfileConfig captureConfig(Player player) {
        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        EnumMap<TabMenuModule, Boolean> tabMenuStates = new EnumMap<>(TabMenuModule.class);
        for (TabMenuModule module : TabMenuModule.values()) {
            tabMenuStates.put(module, tabMenuSettingsService.isEnabled(player.getUniqueId(), module));
        }
        return new BuilderProfileConfig(
                settings.isEnabled(),
                settings.getIntensity(),
                settings.getGridMode(),
                settings.getConsistency(),
                settings.getColor() == null ? null : settings.getColor().asRGB(),
                messageThemeService.accentValue(),
                nightVisionService.isEnabled(player),
                autoUnstickService.isEnabled(player),
                ghostHandService.isEnabled(player),
                stackLookDirectionService.isEnabled(player),
                nudgeSettingsService.get(player),
                tabMenuStates,
                recentEditTrailService.limit(player.getUniqueId())
        );
    }

    private void applyConfig(Player player, BuilderProfileConfig config) {
        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        settings.setEnabled(config.selectionParticlesEnabled());
        settings.setIntensity(config.intensity());
        settings.setGridMode(config.gridMode());
        settings.setConsistency(config.consistency());
        settings.setColor(config.colorRgb() == null ? null : Color.fromRGB(config.colorRgb()));
        messageThemeService.setAccent(config.menuAccent());

        nightVisionService.setEnabled(player, config.nightVisionEnabled());
        autoUnstickService.setEnabled(player, config.autoUnstickEnabled());
        ghostHandService.setEnabled(player, config.ghostHandEnabled());
        stackLookDirectionService.setEnabled(player, config.stackLookDirectionEnabled());
        nudgeSettingsService.set(player, config.nudgeSettings());

        for (TabMenuModule module : TabMenuModule.values()) {
            boolean enabled = config.tabMenuStates().getOrDefault(module, module.defaultEnabled());
            tabMenuSettingsService.setEnabled(player.getUniqueId(), module, enabled);
        }
        recentEditTrailService.setLimit(player.getUniqueId(), config.trailLimit());
        tabInfoPanelService.refreshPlayer(player);
    }

    private List<BuilderProfile.ToolbarSlot> captureToolbar(Player player) {
        List<BuilderProfile.ToolbarSlot> slots = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isSupportedToolbarItem(item)) {
                continue;
            }
            slots.add(new BuilderProfile.ToolbarSlot(slot, item.clone()));
        }
        return slots;
    }

    private ToolbarApplyResult applyToolbar(Player player, List<BuilderProfile.ToolbarSlot> storedSlots) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] desired = new ItemStack[HOTBAR_SIZE];
        if (storedSlots != null) {
            for (BuilderProfile.ToolbarSlot slot : storedSlots) {
                if (slot.slot() < 0 || slot.slot() >= HOTBAR_SIZE || slot.item() == null) {
                    continue;
                }
                desired[slot.slot()] = slot.item().clone();
            }
        }

        int placed = 0;
        int cleared = 0;
        int skipped = 0;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack target = desired[slot];
            if (!canModifyToolbarSlot(current)) {
                if (target != null || isBayzylItem(current)) {
                    skipped++;
                }
                continue;
            }

            if (target == null) {
                if (current != null && current.getType() != Material.AIR) {
                    inventory.setItem(slot, null);
                    cleared++;
                }
                continue;
            }

            inventory.setItem(slot, target);
            placed++;
        }
        return new ToolbarApplyResult(placed, cleared, skipped);
    }

    private void writeConfig(String path, BuilderProfileConfig config) {
        if (config == null) {
            yaml.set(path + ".config", null);
            return;
        }

        String configPath = path + ".config";
        yaml.set(configPath + ".selection_particles.enabled", config.selectionParticlesEnabled());
        yaml.set(configPath + ".selection_particles.intensity", config.intensity().name());
        yaml.set(configPath + ".selection_particles.grid", config.gridMode().name());
        yaml.set(configPath + ".selection_particles.consistency", config.consistency());
        yaml.set(configPath + ".selection_particles.color", config.colorRgb());
        yaml.set(configPath + ".menu_accent", config.menuAccent());
        yaml.set(configPath + ".night_vision", config.nightVisionEnabled());
        yaml.set(configPath + ".auto_unstick", config.autoUnstickEnabled());
        yaml.set(configPath + ".ghost_hand", config.ghostHandEnabled());
        yaml.set(configPath + ".stack_look_direction", config.stackLookDirectionEnabled());
        yaml.set(configPath + ".nudge.inverted", config.nudgeSettings().inverted());
        yaml.set(configPath + ".nudge.step", config.nudgeSettings().step());
        yaml.set(configPath + ".nudge.vertical", config.nudgeSettings().verticalMode().name());
        for (TabMenuModule module : TabMenuModule.values()) {
            yaml.set(configPath + ".tab_menu." + module.key(), config.tabMenuStates().getOrDefault(module, module.defaultEnabled()));
        }
        yaml.set(configPath + ".tab_menu.trail_limit", config.trailLimit());
    }

    private BuilderProfileConfig readConfig(String path) {
        String configPath = path + ".config";
        EnumMap<TabMenuModule, Boolean> tabMenuStates = new EnumMap<>(TabMenuModule.class);
        for (TabMenuModule module : TabMenuModule.values()) {
            tabMenuStates.put(module, yaml.getBoolean(configPath + ".tab_menu." + module.key(), module.defaultEnabled()));
        }
        return new BuilderProfileConfig(
                yaml.getBoolean(configPath + ".selection_particles.enabled", true),
                parseIntensity(yaml.getString(configPath + ".selection_particles.intensity"), VisualizationSettings.Intensity.MEDIUM),
                parseGridMode(yaml.getString(configPath + ".selection_particles.grid"), VisualizationSettings.GridMode.AUTO),
                yaml.getInt(configPath + ".selection_particles.consistency", 10),
                yaml.isSet(configPath + ".selection_particles.color") ? yaml.getInt(configPath + ".selection_particles.color") : null,
                yaml.getString(configPath + ".menu_accent", messageThemeService.accentValue()),
                yaml.getBoolean(configPath + ".night_vision", false),
                yaml.getBoolean(configPath + ".auto_unstick", false),
                yaml.getBoolean(configPath + ".ghost_hand", false),
                yaml.getBoolean(configPath + ".stack_look_direction", true),
                new NudgeSettings(
                        yaml.getBoolean(configPath + ".nudge.inverted", NudgeSettings.defaults().inverted()),
                        yaml.getInt(configPath + ".nudge.step", NudgeSettings.defaults().step()),
                        parseVerticalMode(yaml.getString(configPath + ".nudge.vertical"), NudgeSettings.defaults().verticalMode())
                ),
                tabMenuStates,
                yaml.getInt(configPath + ".tab_menu.trail_limit", recentEditTrailService.defaultLimit())
        );
    }

    private void writeToolbar(String path, List<BuilderProfile.ToolbarSlot> toolbarSlots) {
        String toolbarPath = path + ".toolbar.slots";
        yaml.set(toolbarPath, null);
        if (toolbarSlots == null) {
            return;
        }
        for (BuilderProfile.ToolbarSlot slot : toolbarSlots) {
            if (slot.item() == null) {
                continue;
            }
            String slotPath = toolbarPath + "." + slot.slot();
            yaml.set(slotPath + ".item", slot.item());
        }
    }

    private List<BuilderProfile.ToolbarSlot> readToolbar(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path + ".toolbar.slots");
        if (section == null) {
            return List.of();
        }
        List<BuilderProfile.ToolbarSlot> slots = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key + ".item");
                if (item == null || !isSupportedToolbarItem(item)) {
                    continue;
                }
                slots.add(new BuilderProfile.ToolbarSlot(slot, item));
            } catch (NumberFormatException ignored) {
            }
        }
        slots.sort(Comparator.comparingInt(BuilderProfile.ToolbarSlot::slot));
        return List.copyOf(slots);
    }

    private BuilderProfile loadLegacyProfile(String name, String path) {
        EnumMap<TabMenuModule, Boolean> tabMenuStates = new EnumMap<>(TabMenuModule.class);
        for (TabMenuModule module : TabMenuModule.values()) {
            tabMenuStates.put(module, module.defaultEnabled());
        }
        String intensity = yaml.getString(path + ".intensity", VisualizationSettings.Intensity.MEDIUM.name());
        String gridMode = yaml.getString(path + ".gridMode", VisualizationSettings.GridMode.AUTO.name());
        BuilderProfileConfig config = new BuilderProfileConfig(
                yaml.getBoolean(path + ".selectionParticlesEnabled", true),
                parseIntensity(intensity, VisualizationSettings.Intensity.MEDIUM),
                parseGridMode(gridMode, VisualizationSettings.GridMode.AUTO),
                yaml.getInt(path + ".consistency", 10),
                yaml.isSet(path + ".color") ? yaml.getInt(path + ".color") : null,
                messageThemeService.accentValue(),
                yaml.getBoolean(path + ".nightVisionEnabled", false),
                false,
                false,
                true,
                NudgeSettings.defaults(),
                tabMenuStates,
                recentEditTrailService.defaultLimit()
        );
        return new BuilderProfile(name, 0, 0L, BuilderProfileType.CONFIG, config, List.of());
    }

    private BuilderProfileSummary toSummary(BuilderProfile profile) {
        return new BuilderProfileSummary(
                profile.name(),
                profile.updatedAtEpochMillis(),
                profile.type(),
                profile.hasConfig(),
                profile.type().hasToolbar(),
                profile.toolbarSlots() == null ? 0 : profile.toolbarSlots().size()
        );
    }

    private boolean isSupportedToolbarItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && toolManager.getToolType(item) != null;
    }

    private boolean isBayzylItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && toolManager.getToolType(item) != null;
    }

    private boolean canModifyToolbarSlot(ItemStack current) {
        return current == null || current.getType() == Material.AIR || isBayzylItem(current);
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            return null;
        }
        return name;
    }

    private NudgeSettings.VerticalMode parseVerticalMode(String raw, NudgeSettings.VerticalMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return NudgeSettings.VerticalMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private VisualizationSettings.Intensity parseIntensity(String raw) {
        return parseIntensity(raw, VisualizationSettings.Intensity.MEDIUM);
    }

    private VisualizationSettings.Intensity parseIntensity(String raw, VisualizationSettings.Intensity fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return VisualizationSettings.Intensity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private VisualizationSettings.GridMode parseGridMode(String raw) {
        return parseGridMode(raw, VisualizationSettings.GridMode.AUTO);
    }

    private VisualizationSettings.GridMode parseGridMode(String raw, VisualizationSettings.GridMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return VisualizationSettings.GridMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private record ToolbarApplyResult(int placed, int cleared, int skipped) {
    }
}
