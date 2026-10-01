package com.bayzyl;

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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class BuilderKitService {
    private static final int KIT_VERSION = 1;
    private static final int DEFAULT_LIBRARY_VERSION = 3;
    private static final Set<String> RESERVED_KIT_NAMES = buildReservedNames();

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public BuilderKitService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
        ensureDefaultKits();
    }

    public List<String> listKitNames() {
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section == null) {
            return List.of();
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(section.getKeys(false));
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    public List<BuilderKitSummary> listKitSummaries() {
        return listKitNames().stream()
                .map(this::loadKit)
                .filter(kit -> kit != null)
                .map(this::toSummary)
                .sorted(Comparator.comparing(BuilderKitSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean existsKit(String rawName) {
        return resolveKitName(rawName) != null;
    }

    public boolean existsCanonicalKit(String rawName) {
        String name = normalizeName(rawName);
        return name != null && yaml.contains("kits." + name);
    }

    public boolean isReservedName(String rawName) {
        String normalized = normalizeName(rawName);
        return normalized != null && RESERVED_KIT_NAMES.contains(normalized);
    }

    public BuilderKitScope defaultSaveScope() {
        return BuilderKitScope.HOTBAR;
    }

    public List<String> listLoadLabels() {
        Set<String> labels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        labels.addAll(listKitNames());
        for (String name : listKitNames()) {
            BuilderKit kit = loadKit(name);
            if (kit == null) {
                continue;
            }
            labels.addAll(kit.aliases());
        }
        return List.copyOf(labels);
    }

    public List<String> listShortcutLabels() {
        return listLoadLabels();
    }

    public String resolveKitName(String rawName) {
        String name = normalizeName(rawName);
        if (name == null) {
            return null;
        }
        if (yaml.contains("kits." + name)) {
            return name;
        }
        for (String kitName : listKitNames()) {
            BuilderKit kit = loadKit(kitName);
            if (kit != null && kit.aliases().contains(name)) {
                return kit.name();
            }
        }
        return null;
    }

    public boolean saveKit(Player player, String rawName, BuilderKitScope scope, boolean overwrite) {
        String name = normalizeName(rawName);
        if (name == null || !canUseLabel(name, null)) {
            return false;
        }
        if (!overwrite && existsCanonicalKit(name)) {
            return false;
        }

        BuilderKit resolved = new BuilderKit(
                name,
                KIT_VERSION,
                Instant.now().toEpochMilli(),
                scope == null ? BuilderKitScope.HOTBAR : scope,
                false,
                "Custom Kits",
                "",
                inferIconMaterialKey(captureItems(player, scope == null ? BuilderKitScope.HOTBAR : scope)),
                player == null ? "" : player.getName(),
                List.of(),
                captureItems(player, scope == null ? BuilderKitScope.HOTBAR : scope)
        );
        return writeKit(resolved);
    }

    public boolean updateKit(Player player, String rawName) {
        BuilderKit existing = loadKit(rawName);
        if (existing == null) {
            return false;
        }
        BuilderKit updated = new BuilderKit(
                existing.name(),
                KIT_VERSION,
                Instant.now().toEpochMilli(),
                existing.scope(),
                existing.builtIn(),
                existing.theme(),
                existing.note(),
                existing.iconMaterialKey(),
                existing.author(),
                existing.aliases(),
                captureItems(player, existing.scope())
        );
        return writeKit(updated);
    }

    public boolean renameKit(String fromRawName, String toRawName, boolean overwrite) {
        BuilderKit existing = loadKit(fromRawName);
        String targetName = normalizeName(toRawName);
        if (existing == null || targetName == null || !canUseLabel(targetName, existing.name())) {
            return false;
        }

        String sourceName = existing.name();
        if (sourceName.equals(targetName)) {
            return true;
        }
        if (!overwrite && !sourceName.equals(targetName) && existsKit(targetName)) {
            return false;
        }

        BuilderKit renamed = new BuilderKit(
                targetName,
                KIT_VERSION,
                Instant.now().toEpochMilli(),
                existing.scope(),
                existing.builtIn(),
                existing.theme(),
                existing.note(),
                existing.iconMaterialKey(),
                existing.author(),
                sanitizeAliases(existing.aliases(), targetName, sourceName),
                cloneItems(existing.items())
        );
        writeKitData(renamed);
        if (!sourceName.equals(targetName)) {
            yaml.set("kits." + sourceName, null);
        }
        return saveFile();
    }

    public boolean duplicateKit(String fromRawName, String toRawName, boolean overwrite) {
        BuilderKit existing = loadKit(fromRawName);
        String targetName = normalizeName(toRawName);
        if (existing == null || targetName == null || !canUseLabel(targetName, null)) {
            return false;
        }
        if (existing.name().equals(targetName)) {
            return false;
        }
        if (!overwrite && existsKit(targetName)) {
            return false;
        }

        BuilderKit duplicate = new BuilderKit(
                targetName,
                KIT_VERSION,
                Instant.now().toEpochMilli(),
                existing.scope(),
                false,
                existing.theme(),
                existing.note(),
                existing.iconMaterialKey(),
                existing.author(),
                List.of(),
                cloneItems(existing.items())
        );
        return writeKit(duplicate);
    }

    public boolean updateKitTheme(String rawName, String rawTheme) {
        BuilderKit existing = loadKit(rawName);
        if (existing == null) {
            return false;
        }
        String theme = sanitizeTheme(rawTheme);
        if (theme == null) {
            return false;
        }
        return writeKit(updated(existing, theme, existing.note(), existing.iconMaterialKey(), existing.author(), existing.aliases(), existing.items()));
    }

    public boolean updateKitNote(String rawName, String rawNote) {
        BuilderKit existing = loadKit(rawName);
        if (existing == null) {
            return false;
        }
        String note = sanitizeNote(rawNote);
        if (note == null) {
            return false;
        }
        return writeKit(updated(existing, existing.theme(), note, existing.iconMaterialKey(), existing.author(), existing.aliases(), existing.items()));
    }

    public boolean updateKitIcon(String rawName, Material icon) {
        BuilderKit existing = loadKit(rawName);
        if (existing == null) {
            return false;
        }
        String iconMaterialKey = icon == null || !icon.isItem() ? "" : icon.name();
        return writeKit(updated(existing, existing.theme(), existing.note(), iconMaterialKey, existing.author(), existing.aliases(), existing.items()));
    }

    public boolean addAlias(String rawName, String rawAlias) {
        BuilderKit existing = loadKit(rawName);
        String alias = normalizeName(rawAlias);
        if (existing == null || alias == null || !canUseLabel(alias, existing.name())) {
            return false;
        }
        if (existing.name().equals(alias) || existing.aliases().contains(alias)) {
            return false;
        }
        List<String> aliases = new ArrayList<>(existing.aliases());
        aliases.add(alias);
        Collections.sort(aliases, String.CASE_INSENSITIVE_ORDER);
        return writeKit(updated(existing, existing.theme(), existing.note(), existing.iconMaterialKey(), existing.author(), List.copyOf(aliases), existing.items()));
    }

    public boolean removeAlias(String rawName, String rawAlias) {
        BuilderKit existing = loadKit(rawName);
        String alias = normalizeName(rawAlias);
        if (existing == null || alias == null || !existing.aliases().contains(alias)) {
            return false;
        }
        List<String> aliases = new ArrayList<>(existing.aliases());
        aliases.removeIf(value -> value.equalsIgnoreCase(alias));
        return writeKit(updated(existing, existing.theme(), existing.note(), existing.iconMaterialKey(), existing.author(), List.copyOf(aliases), existing.items()));
    }

    public BuilderKit loadKit(String rawName) {
        String name = resolveKitName(rawName);
        if (name == null) {
            return null;
        }
        String path = "kits." + name;
        if (!yaml.contains(path)) {
            return null;
        }

        BuilderKitScope scope = BuilderKitScope.fromKey(yaml.getString(path + ".scope"));
        if (scope == null) {
            return null;
        }
        int version = yaml.getInt(path + ".version", KIT_VERSION);
        long updatedAt = yaml.getLong(path + ".updated_at", 0L);
        boolean builtIn = yaml.getBoolean(path + ".built_in", false);
        List<BuilderKit.SlotItem> items = readItems(path, scope);
        String theme = yaml.getString(path + ".theme", builtIn ? defaultTheme(name) : "Custom Kits");
        String note = yaml.getString(path + ".note", builtIn ? defaultNote(name) : "");
        String iconMaterialKey = yaml.getString(path + ".icon", inferIconMaterialKey(items));
        String author = yaml.getString(path + ".author", builtIn ? "Bayzyl" : "");
        List<String> aliases = sanitizeAliases(yaml.getStringList(path + ".aliases"), name, null);
        return new BuilderKit(
                name,
                version,
                updatedAt,
                scope,
                builtIn,
                theme,
                note,
                iconMaterialKey,
                author,
                aliases,
                items
        );
    }

    public BuilderKitLoadResult applyKit(Player player, String rawName) {
        BuilderKit kit = loadKit(rawName);
        if (kit == null) {
            return null;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] desired = new ItemStack[kit.scope().endSlotExclusive()];
        for (BuilderKit.SlotItem slotItem : kit.items()) {
            if (slotItem.slot() < kit.scope().startSlot() || slotItem.slot() >= kit.scope().endSlotExclusive()) {
                continue;
            }
            if (slotItem.item() == null || slotItem.item().getType() == Material.AIR) {
                continue;
            }
            desired[slotItem.slot()] = slotItem.item().clone();
        }

        int placed = 0;
        int cleared = 0;
        for (int slot = kit.scope().startSlot(); slot < kit.scope().endSlotExclusive(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack target = desired[slot];
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
        return new BuilderKitLoadResult(kit.name(), kit.scope(), placed, cleared);
    }

    public boolean deleteKit(String rawName) {
        String name = resolveKitName(rawName);
        if (name == null || !yaml.contains("kits." + name)) {
            return false;
        }
        yaml.set("kits." + name, null);
        return saveFile();
    }

    public int restoreDefaultKits() {
        int restored = 0;
        for (DefaultKitDefinition definition : defaultKitDefinitions()) {
            if (writeDefaultKit(definition)) {
                restored++;
            }
        }
        yaml.set("meta.defaults_version", DEFAULT_LIBRARY_VERSION);
        yaml.set("meta.defaults_seeded", true);
        saveFile();
        return restored;
    }

    public String normalizeName(String rawName) {
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

    private BuilderKitSummary toSummary(BuilderKit kit) {
        return new BuilderKitSummary(
                kit.name(),
                kit.updatedAtEpochMillis(),
                kit.scope(),
                kit.builtIn(),
                kit.itemCount(),
                kit.theme(),
                kit.note(),
                kit.iconMaterialKey(),
                kit.aliases()
        );
    }

    private boolean writeKit(BuilderKit kit) {
        writeKitData(kit);
        return saveFile();
    }

    private void writeKitData(BuilderKit kit) {
        String path = "kits." + kit.name();
        yaml.set(path, null);
        yaml.set(path + ".version", kit.version());
        yaml.set(path + ".updated_at", kit.updatedAtEpochMillis());
        yaml.set(path + ".scope", kit.scope().key());
        yaml.set(path + ".built_in", kit.builtIn());
        yaml.set(path + ".theme", sanitizeTheme(kit.theme()));
        yaml.set(path + ".note", sanitizeNote(kit.note()));
        yaml.set(path + ".icon", sanitizeIconMaterialKey(kit.iconMaterialKey()));
        yaml.set(path + ".author", sanitizeAuthor(kit.author()));
        yaml.set(path + ".aliases", sanitizeAliases(kit.aliases(), kit.name(), null));
        yaml.set(path + ".items", null);
        for (BuilderKit.SlotItem slotItem : kit.items()) {
            if (slotItem.item() == null || slotItem.item().getType() == Material.AIR) {
                continue;
            }
            String slotPath = path + ".items." + slotItem.slot();
            yaml.set(slotPath + ".item", slotItem.item());
        }
    }

    private List<BuilderKit.SlotItem> captureItems(Player player, BuilderKitScope scope) {
        List<BuilderKit.SlotItem> items = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = scope.startSlot(); slot < scope.endSlotExclusive(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            items.add(new BuilderKit.SlotItem(slot, item.clone()));
        }
        return List.copyOf(items);
    }

    private List<BuilderKit.SlotItem> cloneItems(List<BuilderKit.SlotItem> sourceItems) {
        List<BuilderKit.SlotItem> cloned = new ArrayList<>();
        for (BuilderKit.SlotItem slotItem : sourceItems) {
            if (slotItem.item() == null || slotItem.item().getType() == Material.AIR) {
                continue;
            }
            cloned.add(new BuilderKit.SlotItem(slotItem.slot(), slotItem.item().clone()));
        }
        return List.copyOf(cloned);
    }

    private List<BuilderKit.SlotItem> readItems(String path, BuilderKitScope scope) {
        ConfigurationSection section = yaml.getConfigurationSection(path + ".items");
        if (section == null) {
            return List.of();
        }
        List<BuilderKit.SlotItem> items = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                if (slot < scope.startSlot() || slot >= scope.endSlotExclusive()) {
                    continue;
                }
                ItemStack item = section.getItemStack(key + ".item");
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                items.add(new BuilderKit.SlotItem(slot, item));
            } catch (NumberFormatException ignored) {
            }
        }
        items.sort(Comparator.comparingInt(BuilderKit.SlotItem::slot));
        return List.copyOf(items);
    }

    private void ensureDefaultKits() {
        int seededVersion = yaml.getInt("meta.defaults_version", yaml.getBoolean("meta.defaults_seeded", false) ? 1 : 0);
        if (seededVersion >= DEFAULT_LIBRARY_VERSION) {
            return;
        }
        for (DefaultKitDefinition kit : defaultKitDefinitions()) {
            ensureDefaultKit(kit);
        }
        yaml.set("meta.defaults_version", DEFAULT_LIBRARY_VERSION);
        yaml.set("meta.defaults_seeded", true);
        saveFile();
    }

    private void ensureDefaultKit(DefaultKitDefinition definition) {
        if (yaml.contains("kits." + definition.name())) {
            return;
        }
        writeDefaultKit(definition);
    }

    private boolean writeDefaultKit(DefaultKitDefinition definition) {
        List<BuilderKit.SlotItem> items = new ArrayList<>();
        List<Material> materials = definition.materials();
        for (int slot = 0; slot < materials.size() && slot < definition.scope().endSlotExclusive(); slot++) {
            items.add(new BuilderKit.SlotItem(slot, new ItemStack(materials.get(slot))));
        }
        BuilderKit defaultKit = new BuilderKit(
                definition.name(),
                KIT_VERSION,
                Instant.now().toEpochMilli(),
                definition.scope(),
                true,
                defaultTheme(definition.name()),
                defaultNote(definition.name()),
                definition.materials().isEmpty() ? "" : definition.materials().get(0).name(),
                "Bayzyl",
                defaultAliases(definition.name()),
                List.copyOf(items)
        );
        if (!writeKit(defaultKit)) {
            plugin.getLogger().warning("Failed to write default kit " + definition.name() + ".");
            return false;
        }
        return true;
    }

    private List<DefaultKitDefinition> defaultKitDefinitions() {
        return List.of(
                hotbar("stonebricks", Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_SLAB, Material.STONE_BRICK_WALL, Material.COBBLESTONE, Material.ANDESITE),
                hotbar("castle", Material.STONE_BRICKS, Material.STONE, Material.DEEPSLATE_BRICKS, Material.ANDESITE, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.DARK_OAK_LOG, Material.DARK_OAK_PLANKS, Material.COBBLESTONE_WALL),
                hotbar("deepslate", Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.CHISELED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_WALL),
                hotbar("deepslatebricks", Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_WALL, Material.POLISHED_DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.IRON_BARS),
                hotbar("deepslatetiles", Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_WALL, Material.POLISHED_DEEPSLATE, Material.BASALT, Material.SOUL_LANTERN, Material.CHAIN),
                hotbar("blackstone", Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_STAIRS, Material.POLISHED_BLACKSTONE_SLAB, Material.POLISHED_BLACKSTONE_WALL, Material.GILDED_BLACKSTONE),
                hotbar("blackstonecastle", Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE, Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_BRICK_SLAB, Material.POLISHED_BLACKSTONE_BRICK_WALL, Material.DARK_OAK_LOG, Material.NETHER_BRICKS),
                hotbar("evilcastle", Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.CRYING_OBSIDIAN, Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.CHAIN, Material.SOUL_LANTERN, Material.RED_STAINED_GLASS),
                hotbar("wizardtower", Material.DEEPSLATE_BRICKS, Material.POLISHED_BASALT, Material.AMETHYST_BLOCK, Material.TINTED_GLASS, Material.WARPED_PLANKS, Material.WARPED_STAIRS, Material.PURPLE_STAINED_GLASS, Material.END_ROD, Material.CHAIN),
                hotbar("stonekeep", Material.STONE, Material.STONE_BRICKS, Material.SMOOTH_STONE, Material.ANDESITE, Material.POLISHED_ANDESITE, Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE, Material.STONE_BRICK_STAIRS, Material.IRON_BARS),
                hotbar("mossyruins", Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ROOTED_DIRT, Material.AZALEA_LEAVES, Material.VINE, Material.STONE_BRICK_WALL, Material.LANTERN),
                hotbar("cathedral", Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.QUARTZ_PILLAR, Material.WHITE_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.DARK_OAK_STAIRS, Material.STONE_BRICK_WALL, Material.LANTERN, Material.CHAIN),
                hotbar("dwarven", Material.DEEPSLATE_BRICKS, Material.TUFF_BRICKS, Material.POLISHED_ANDESITE, Material.COPPER_BLOCK, Material.CHISELED_COPPER, Material.IRON_BARS, Material.LANTERN, Material.BLACKSTONE, Material.STONE_BRICKS),
                hotbar("basaltfort", Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK, Material.SOUL_LANTERN, Material.CHAIN),
                hotbar("sandstone", Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, Material.SANDSTONE_STAIRS, Material.SANDSTONE_SLAB, Material.SANDSTONE_WALL, Material.BIRCH_PLANKS, Material.DEAD_BUSH),
                hotbar("redsandstone", Material.RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE, Material.CUT_RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, Material.RED_SANDSTONE_STAIRS, Material.RED_SANDSTONE_SLAB, Material.RED_SANDSTONE_WALL, Material.ACACIA_PLANKS, Material.COPPER_BLOCK),
                hotbar("landscaping", Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.DIRT_PATH, Material.SHORT_GRASS, Material.FERN, Material.AZALEA_LEAVES, Material.WATER_BUCKET),
                hotbar("treeshaping", Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.JUNGLE_LEAVES, Material.VINE, Material.MOSS_BLOCK, Material.ROOTED_DIRT),
                hotbar("hedgegarden", Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.OAK_FENCE, Material.OAK_TRAPDOOR, Material.LANTERN),
                hotbar("flowergarden", Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.PINK_PETALS, Material.SPORE_BLOSSOM, Material.PEONY, Material.ROSE_BUSH, Material.LILAC, Material.SUNFLOWER, Material.FLOWERING_AZALEA_LEAVES),
                hotbar("meadow", Material.GRASS_BLOCK, Material.SHORT_GRASS, Material.DIRT_PATH, Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.AZURE_BLUET, Material.WHITE_TULIP),
                hotbar("swampgarden", Material.MUD, Material.MUD_BRICKS, Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS, Material.MANGROVE_LEAVES, Material.VINE, Material.LILY_PAD, Material.WATER_BUCKET, Material.SEA_PICKLE),
                hotbar("pondedge", Material.WATER_BUCKET, Material.CLAY, Material.GRAVEL, Material.MOSS_BLOCK, Material.ROOTED_DIRT, Material.LILY_PAD, Material.BIG_DRIPLEAF, Material.MANGROVE_ROOTS, Material.SEA_PICKLE),
                hotbar("cliffmix", Material.STONE, Material.COBBLESTONE, Material.ANDESITE, Material.TUFF, Material.DEEPSLATE, Material.CALCITE, Material.MOSS_BLOCK, Material.DRIPSTONE_BLOCK, Material.GRAVEL),
                hotbar("pathing", Material.DIRT_PATH, Material.COARSE_DIRT, Material.GRAVEL, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, Material.ANDESITE, Material.ROOTED_DIRT, Material.OAK_TRAPDOOR),
                hotbar("roadkit", Material.STONE, Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.COBBLESTONE, Material.TUFF, Material.DEEPSLATE_TILES, Material.IRON_TRAPDOOR),
                hotbar("rooftiles", Material.SPRUCE_STAIRS, Material.DARK_OAK_STAIRS, Material.DEEPSLATE_TILE_STAIRS, Material.BRICK_STAIRS, Material.MANGROVE_STAIRS, Material.NETHER_BRICK_STAIRS, Material.SPRUCE_SLAB, Material.DEEPSLATE_TILE_SLAB, Material.DARK_OAK_TRAPDOOR),
                hotbar("windowkit", Material.GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.BROWN_STAINED_GLASS_PANE, Material.TINTED_GLASS, Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.IRON_BARS, Material.END_ROD),
                hotbar("oaktree", Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD, Material.OAK_PLANKS, Material.OAK_STAIRS, Material.OAK_SLAB, Material.OAK_FENCE, Material.OAK_LEAVES, Material.VINE),
                hotbar("sprucetree", Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_WOOD, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.SPRUCE_FENCE, Material.SPRUCE_LEAVES, Material.VINE),
                hotbar("birchtree", Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG, Material.BIRCH_WOOD, Material.BIRCH_PLANKS, Material.BIRCH_STAIRS, Material.BIRCH_SLAB, Material.BIRCH_FENCE, Material.BIRCH_LEAVES, Material.MOSS_BLOCK),
                hotbar("jungletree", Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_WOOD, Material.JUNGLE_PLANKS, Material.JUNGLE_STAIRS, Material.JUNGLE_SLAB, Material.JUNGLE_FENCE, Material.JUNGLE_LEAVES, Material.VINE),
                hotbar("acaciatree", Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.ACACIA_WOOD, Material.ACACIA_PLANKS, Material.ACACIA_STAIRS, Material.ACACIA_SLAB, Material.ACACIA_FENCE, Material.ACACIA_LEAVES, Material.FLOWERING_AZALEA_LEAVES),
                hotbar("darkoaktree", Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.DARK_OAK_PLANKS, Material.DARK_OAK_STAIRS, Material.DARK_OAK_SLAB, Material.DARK_OAK_FENCE, Material.DARK_OAK_LEAVES, Material.VINE),
                hotbar("mangrovetree", Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_WOOD, Material.MANGROVE_PLANKS, Material.MANGROVE_STAIRS, Material.MANGROVE_SLAB, Material.MANGROVE_FENCE, Material.MANGROVE_LEAVES, Material.MANGROVE_ROOTS),
                hotbar("cherrytree", Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_WOOD, Material.CHERRY_PLANKS, Material.CHERRY_STAIRS, Material.CHERRY_SLAB, Material.CHERRY_FENCE, Material.CHERRY_LEAVES, Material.PINK_PETALS),
                hotbar("bamboobuild", Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK, Material.BAMBOO_PLANKS, Material.BAMBOO_MOSAIC, Material.BAMBOO_STAIRS, Material.BAMBOO_MOSAIC_STAIRS, Material.BAMBOO_FENCE, Material.BAMBOO_TRAPDOOR, Material.BAMBOO_DOOR),
                hotbar("crimsonbuild", Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.CRIMSON_PLANKS, Material.CRIMSON_STAIRS, Material.CRIMSON_SLAB, Material.CRIMSON_FENCE, Material.NETHER_WART_BLOCK, Material.SHROOMLIGHT),
                hotbar("warpedbuild", Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM, Material.WARPED_HYPHAE, Material.WARPED_PLANKS, Material.WARPED_STAIRS, Material.WARPED_SLAB, Material.WARPED_FENCE, Material.WARPED_WART_BLOCK, Material.SHROOMLIGHT),
                inventory("library", Material.BOOKSHELF, Material.CHISELED_BOOKSHELF, Material.LECTERN, Material.OAK_PLANKS, Material.OAK_STAIRS, Material.OAK_SLAB, Material.LADDER, Material.LANTERN, Material.CHAIN, Material.CANDLE, Material.DARK_OAK_TRAPDOOR, Material.BARREL, Material.ENCHANTING_TABLE, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, Material.DARK_OAK_PLANKS, Material.COBWEB, Material.FLOWER_POT),
                inventory("studykit", Material.DARK_OAK_PLANKS, Material.OAK_PLANKS, Material.BOOKSHELF, Material.CHISELED_BOOKSHELF, Material.LECTERN, Material.BARREL, Material.SPRUCE_TRAPDOOR, Material.OAK_TRAPDOOR, Material.CANDLE, Material.LANTERN, Material.CHAIN, Material.LADDER, Material.CARTOGRAPHY_TABLE, Material.DARK_OAK_STAIRS, Material.SPRUCE_STAIRS, Material.FLOWER_POT, Material.BROWN_CARPET, Material.ITEM_FRAME),
                inventory("tavern", Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.STRIPPED_SPRUCE_LOG, Material.BARREL, Material.OAK_FENCE, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.DARK_OAK_TRAPDOOR, Material.LANTERN, Material.CHAIN, Material.BRICKS, Material.FLOWER_POT, Material.CAMPFIRE, Material.SMOKER, Material.OAK_TRAPDOOR, Material.DARK_OAK_STAIRS, Material.BROWN_CARPET, Material.GLASS_PANE),
                inventory("blacksmith", Material.STONE_BRICKS, Material.COBBLESTONE, Material.DEEPSLATE_BRICKS, Material.IRON_BARS, Material.CHAIN, Material.LANTERN, Material.BRICKS, Material.SMITHING_TABLE, Material.BLAST_FURNACE, Material.ANVIL, Material.CAULDRON, Material.SPRUCE_PLANKS, Material.DARK_OAK_LOG, Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.IRON_TRAPDOOR, Material.STONE_SLAB, Material.POLISHED_ANDESITE),
                hotbar("throne", Material.RED_CARPET, Material.GOLD_BLOCK, Material.CHISELED_STONE_BRICKS, Material.STONE_BRICKS, Material.DARK_OAK_STAIRS, Material.DARK_OAK_SLAB, Material.RED_WOOL, Material.LANTERN, Material.CHAIN),
                hotbar("chapel", Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.QUARTZ_PILLAR, Material.WHITE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.CANDLE, Material.LANTERN, Material.CHAIN, Material.DARK_OAK_STAIRS),
                inventory("greenhouse", Material.GLASS, Material.GLASS_PANE, Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.FLOWERING_AZALEA_LEAVES, Material.AZALEA_LEAVES, Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.MANGROVE_ROOTS, Material.FLOWER_POT, Material.LANTERN, Material.CHAIN, Material.GRASS_BLOCK, Material.SHORT_GRASS, Material.PEONY, Material.SUNFLOWER, Material.ROOTED_DIRT, Material.OAK_FENCE),
                inventory("alchemy", Material.DEEPSLATE_BRICKS, Material.CHISELED_BOOKSHELF, Material.CAULDRON, Material.BREWING_STAND, Material.SOUL_LANTERN, Material.CHAIN, Material.PURPLE_STAINED_GLASS, Material.BLACKSTONE, Material.WARPED_PLANKS, Material.AMETHYST_BLOCK, Material.CANDLE, Material.SPRUCE_TRAPDOOR, Material.COBWEB, Material.FLOWER_POT, Material.TINTED_GLASS, Material.DARK_OAK_PLANKS, Material.LADDER, Material.LECTERN),
                inventory("kitchen", Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.SMOOTH_STONE, Material.BARREL, Material.SMOKER, Material.BLAST_FURNACE, Material.CAULDRON, Material.IRON_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.OAK_TRAPDOOR, Material.LANTERN, Material.WHITE_TERRACOTTA, Material.BRICKS, Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.CRAFTING_TABLE, Material.BROWN_CARPET, Material.FLOWER_POT),
                inventory("bedroom", Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BOOKSHELF, Material.WHITE_WOOL, Material.LIGHT_GRAY_WOOL, Material.RED_CARPET, Material.DARK_OAK_TRAPDOOR, Material.SPRUCE_STAIRS, Material.LANTERN, Material.BARREL, Material.WHITE_BED, Material.CHEST, Material.CANDLE, Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.LADDER, Material.FLOWER_POT, Material.PAINTING),
                inventory("deserttown", Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, Material.RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE, Material.CUT_RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.ACACIA_LOG, Material.ACACIA_PLANKS, Material.ACACIA_STAIRS, Material.ACACIA_TRAPDOOR, Material.COPPER_BLOCK, Material.CUT_COPPER, Material.DEAD_BUSH),
                inventory("medievaltown", Material.STONE_BRICKS, Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ANDESITE, Material.DEEPSLATE_BRICKS, Material.DARK_OAK_LOG, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.DARK_OAK_TRAPDOOR, Material.COBBLESTONE_WALL, Material.STONE_BRICK_WALL, Material.GLASS_PANE, Material.LANTERN, Material.BARREL),
                inventory("moderncity", Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_CONCRETE, Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.QUARTZ_PILLAR, Material.GLASS, Material.TINTED_GLASS, Material.IRON_BLOCK, Material.SEA_LANTERN, Material.DEEPSLATE_TILES, Material.POLISHED_ANDESITE, Material.COPPER_BLOCK, Material.CUT_COPPER, Material.IRON_TRAPDOOR, Material.LIGHT_BLUE_STAINED_GLASS, Material.SMOOTH_STONE),
                inventory("industrialcity", Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.POLISHED_BASALT, Material.IRON_BLOCK, Material.IRON_BARS, Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER, Material.CHISELED_COPPER, Material.COPPER_GRATE, Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER, Material.LIGHT_GRAY_STAINED_GLASS),
                inventory("seasideport", Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.OAK_LOG, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.BARREL, Material.CHAIN, Material.LANTERN, Material.COBBLESTONE, Material.STONE_BRICKS, Material.WHITE_WOOL, Material.BLUE_WOOL, Material.OAK_FENCE, Material.SPRUCE_TRAPDOOR, Material.PRISMARINE_BRICKS, Material.LADDER, Material.CAMPFIRE),
                inventory("fishingvillage", Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.OAK_PLANKS, Material.BARREL, Material.LADDER, Material.OAK_FENCE, Material.SPRUCE_TRAPDOOR, Material.COBBLESTONE, Material.COBBLESTONE_WALL, Material.LANTERN, Material.CAMPFIRE, Material.SMOKER, Material.WHITE_WOOL, Material.BLUE_WOOL, Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.CHAIN, Material.GRAVEL),
                inventory("mountainvillage", Material.STONE, Material.COBBLESTONE, Material.TUFF, Material.ANDESITE, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, Material.COBBLESTONE_WALL, Material.MOSSY_COBBLESTONE, Material.LANTERN, Material.WHITE_WOOL, Material.DARK_OAK_PLANKS, Material.STONE_BRICKS, Material.POLISHED_ANDESITE, Material.SPRUCE_TRAPDOOR, Material.LADDER, Material.GRAVEL, Material.CAMPFIRE),
                inventory("snowyvillage", Material.SPRUCE_LOG, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.STONE_BRICKS, Material.COBBLESTONE, Material.SNOW_BLOCK, Material.WHITE_TERRACOTTA, Material.LANTERN, Material.SPRUCE_STAIRS, Material.SPRUCE_TRAPDOOR, Material.PACKED_ICE, Material.BLUE_ICE, Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL, Material.CHAIN, Material.GLASS_PANE, Material.CAMPFIRE, Material.BARREL),
                inventory("marketstreet", Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.STRIPPED_OAK_LOG, Material.BARREL, Material.SPRUCE_FENCE, Material.OAK_FENCE, Material.RED_WOOL, Material.WHITE_WOOL, Material.YELLOW_WOOL, Material.GREEN_WOOL, Material.LANTERN, Material.CHAIN, Material.COBBLESTONE, Material.STONE_BRICKS, Material.HAY_BLOCK, Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.GRAVEL),
                hotbar("copper", Material.COPPER_BLOCK, Material.CHISELED_COPPER, Material.CUT_COPPER, Material.CUT_COPPER_STAIRS, Material.CUT_COPPER_SLAB, Material.COPPER_DOOR, Material.COPPER_TRAPDOOR, Material.COPPER_GRATE, Material.COPPER_BULB),
                hotbar("oxidizedcopper", Material.OXIDIZED_COPPER, Material.OXIDIZED_CHISELED_COPPER, Material.OXIDIZED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_SLAB, Material.OXIDIZED_COPPER_DOOR, Material.OXIDIZED_COPPER_TRAPDOOR, Material.OXIDIZED_COPPER_GRATE, Material.OXIDIZED_COPPER_BULB),
                hotbar("prismarine", Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.PRISMARINE_STAIRS, Material.PRISMARINE_SLAB, Material.PRISMARINE_WALL, Material.PRISMARINE_BRICK_STAIRS, Material.PRISMARINE_BRICK_SLAB, Material.SEA_LANTERN),
                hotbar("quartz", Material.QUARTZ_BLOCK, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, Material.SMOOTH_QUARTZ, Material.SMOOTH_QUARTZ_STAIRS, Material.SMOOTH_QUARTZ_SLAB, Material.CHISELED_QUARTZ_BLOCK, Material.CALCITE, Material.WHITE_CONCRETE),
                hotbar("bricktown", Material.BRICKS, Material.BRICK_STAIRS, Material.BRICK_SLAB, Material.BRICK_WALL, Material.GRANITE, Material.POLISHED_GRANITE, Material.ANDESITE, Material.DARK_OAK_PLANKS, Material.IRON_BARS),
                hotbar("mudbrick", Material.MUD, Material.MUD_BRICKS, Material.MUD_BRICK_STAIRS, Material.MUD_BRICK_SLAB, Material.MUD_BRICK_WALL, Material.PACKED_MUD, Material.ROOTED_DIRT, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS),
                hotbar("tuffbricks", Material.TUFF, Material.POLISHED_TUFF, Material.TUFF_BRICKS, Material.CHISELED_TUFF, Material.CHISELED_TUFF_BRICKS, Material.TUFF_BRICK_STAIRS, Material.TUFF_BRICK_SLAB, Material.TUFF_BRICK_WALL, Material.IRON_BARS),
                hotbar("terracottawarm", Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA, Material.PINK_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA, Material.YELLOW_GLAZED_TERRACOTTA, Material.RED_SANDSTONE),
                hotbar("terracottacool", Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA, Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Material.TINTED_GLASS),
                hotbar("netherfort", Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS, Material.NETHER_BRICK_SLAB, Material.NETHER_BRICK_WALL, Material.CHISELED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS, Material.BASALT, Material.SHROOMLIGHT),
                hotbar("whitecastle", Material.CALCITE, Material.QUARTZ_BLOCK, Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, Material.WHITE_CONCRETE, Material.CHISELED_QUARTZ_BLOCK, Material.SMOOTH_QUARTZ_STAIRS, Material.WHITE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS),
                hotbar("gothic", Material.DEEPSLATE_TILES, Material.BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.RED_STAINED_GLASS, Material.CHAIN, Material.SOUL_LANTERN, Material.DARK_OAK_STAIRS, Material.NETHER_BRICKS, Material.IRON_BARS),
                hotbar("farmstead", Material.OAK_LOG, Material.SPRUCE_PLANKS, Material.HAY_BLOCK, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.COARSE_DIRT, Material.OAK_FENCE, Material.SPRUCE_STAIRS, Material.BARREL),
                hotbar("orchard", Material.GRASS_BLOCK, Material.DIRT_PATH, Material.OAK_LEAVES, Material.BIRCH_LEAVES, Material.CHERRY_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.OAK_FENCE, Material.LANTERN, Material.WATER_BUCKET),
                hotbar("graveyard", Material.COBBLESTONE, Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.IRON_BARS, Material.SOUL_LANTERN, Material.CHAIN, Material.MOSS_BLOCK, Material.CRACKED_STONE_BRICKS),
                hotbar("frozentower", Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK, Material.QUARTZ_BLOCK, Material.CALCITE, Material.LIGHT_BLUE_STAINED_GLASS, Material.WHITE_CONCRETE, Material.SEA_LANTERN, Material.SPRUCE_LOG),
                hotbar("amethyst", Material.AMETHYST_BLOCK, Material.CALCITE, Material.SMOOTH_BASALT, Material.TINTED_GLASS, Material.PURPLE_STAINED_GLASS, Material.QUARTZ_BLOCK, Material.END_ROD, Material.CHAIN, Material.SEA_LANTERN)
        );
    }

    private DefaultKitDefinition hotbar(String name, Material... materials) {
        return new DefaultKitDefinition(name, BuilderKitScope.HOTBAR, List.of(materials));
    }

    private DefaultKitDefinition inventory(String name, Material... materials) {
        return new DefaultKitDefinition(name, BuilderKitScope.INVENTORY, List.of(materials));
    }

    private record DefaultKitDefinition(String name, BuilderKitScope scope, List<Material> materials) {
    }

    private BuilderKit updated(BuilderKit existing,
                               String theme,
                               String note,
                               String iconMaterialKey,
                               String author,
                               List<String> aliases,
                               List<BuilderKit.SlotItem> items) {
        return new BuilderKit(
                existing.name(),
                KIT_VERSION,
                Instant.now().toEpochMilli(),
                existing.scope(),
                existing.builtIn(),
                theme,
                note,
                iconMaterialKey,
                author,
                aliases,
                cloneItems(items)
        );
    }

    private boolean canUseLabel(String rawLabel, String ownerName) {
        String label = normalizeName(rawLabel);
        if (label == null || isReservedName(label)) {
            return false;
        }
        String claimedBy = resolveKitName(label);
        return claimedBy == null || (ownerName != null && claimedBy.equals(ownerName));
    }

    private String inferIconMaterialKey(List<BuilderKit.SlotItem> items) {
        for (BuilderKit.SlotItem slotItem : items) {
            if (slotItem.item() != null && slotItem.item().getType() != Material.AIR) {
                return slotItem.item().getType().name();
            }
        }
        return "";
    }

    private List<String> sanitizeAliases(List<String> rawAliases, String ownerName, String movingFromName) {
        if (rawAliases == null || rawAliases.isEmpty()) {
            return List.of();
        }
        Set<String> aliases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String rawAlias : rawAliases) {
            String alias = normalizeName(rawAlias);
            if (alias == null || alias.equals(ownerName) || (movingFromName != null && alias.equals(movingFromName))) {
                continue;
            }
            if (isReservedName(alias)) {
                continue;
            }
            aliases.add(alias);
        }
        return List.copyOf(aliases);
    }

    private String sanitizeTheme(String rawTheme) {
        if (rawTheme == null) {
            return "Custom Kits";
        }
        String trimmed = rawTheme.trim();
        if (trimmed.isEmpty()) {
            return "Custom Kits";
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private String sanitizeNote(String rawNote) {
        if (rawNote == null) {
            return "";
        }
        String trimmed = rawNote.trim();
        return trimmed.length() > 140 ? trimmed.substring(0, 140) : trimmed;
    }

    private String sanitizeAuthor(String rawAuthor) {
        if (rawAuthor == null) {
            return "";
        }
        String trimmed = rawAuthor.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private String sanitizeIconMaterialKey(String rawIconMaterialKey) {
        if (rawIconMaterialKey == null || rawIconMaterialKey.isBlank()) {
            return "";
        }
        Material material = Material.matchMaterial(rawIconMaterialKey);
        if (material == null || !material.isItem()) {
            return "";
        }
        return material.name();
    }

    private String defaultTheme(String name) {
        return switch (name) {
            case "stonebricks", "castle", "deepslate", "deepslatebricks", "deepslatetiles", "stonekeep", "cathedral",
                    "dwarven", "whitecastle", "bricktown", "tuffbricks" -> "Masonry & Castle";
            case "blackstone", "blackstonecastle", "evilcastle", "wizardtower", "basaltfort", "netherfort", "gothic",
                    "graveyard", "frozentower", "amethyst", "throne", "chapel" -> "Dark & Fantasy";
            case "landscaping", "treeshaping", "hedgegarden", "flowergarden", "meadow", "swampgarden", "pondedge",
                    "cliffmix", "pathing", "farmstead", "orchard" -> "Organic & Landscaping";
            case "oaktree", "sprucetree", "birchtree", "jungletree", "acaciatree", "darkoaktree", "mangrovetree",
                    "cherrytree", "bamboobuild", "crimsonbuild", "warpedbuild" -> "Trees & Timber";
            case "library", "studykit", "tavern", "blacksmith", "greenhouse", "alchemy", "kitchen", "bedroom",
                    "windowkit", "rooftiles" -> "Interiors & Workspaces";
            case "deserttown", "medievaltown", "moderncity", "industrialcity", "seasideport", "fishingvillage",
                    "mountainvillage", "snowyvillage", "marketstreet", "roadkit" -> "Towns & Streets";
            default -> "Specialty Palettes";
        };
    }

    private String defaultNote(String name) {
        return switch (defaultTheme(name)) {
            case "Masonry & Castle" -> "Stone-forward structural palette for keeps, towers, arches, and clean masonry shells.";
            case "Dark & Fantasy" -> "High-contrast fantasy palette suited for ominous silhouettes, dramatic accents, and themed focal builds.";
            case "Organic & Landscaping" -> "Organic terrain kit for hand-blending paths, foliage, roots, and natural transitions.";
            case "Trees & Timber" -> "Tree and timber palette built for trunks, branch structure, canopy support, and wood variation.";
            case "Interiors & Workspaces" -> "Interior detailing set for believable rooms, furniture shells, and finishing passes.";
            case "Towns & Streets" -> "Settlement-scale palette for streets, facades, roofs, and supporting townscape detail work.";
            default -> "Focused material study kit for exploring one palette family without hunting blocks one by one.";
        };
    }

    private List<String> defaultAliases(String name) {
        return switch (name) {
            case "stonebricks" -> List.of("stonebrick");
            case "deepslatebricks" -> List.of("deepslatebrick");
            case "deepslatetiles" -> List.of("deepslatetile");
            default -> List.of();
        };
    }

    private static Set<String> buildReservedNames() {
        Set<String> reserved = new HashSet<>();
        reserved.addAll(List.of(
                "list", "load", "inspect", "delete", "restoredefaults", "rename", "duplicate", "update", "confirm",
                "menu", "note", "theme", "icon", "alias", "add", "remove", "help",
                "bzl", "bayzyl", "bzlhelp", "kit", "kitmake", "kithelp", "kitlist", "kitupdate", "kitconfirm",
                "profile", "tabmenu", "nudge", "ramalert", "bzltoggle", "eraser", "wand", "select",
                "minecraft", "bukkit", "paper", "spigot", "fawe", "worldedit", "we", "essentials"
        ));
        CommandRegistry.getTopLevel().forEach(spec -> reserved.add(spec.name()));
        CommandRegistry.getBzlSubcommands().forEach(spec -> reserved.add(spec.name()));
        reserved.addAll(List.of(
                "help", "stop", "reload", "kick", "ban", "banip", "pardon", "pardonip", "whitelist",
                "op", "deop", "gamemode", "defaultgamemode", "difficulty", "time", "weather", "tp", "teleport",
                "give", "clear", "summon", "kill", "setblock", "fill", "clone", "data", "execute",
                "say", "me", "msg", "tell", "w", "tellraw", "title", "team", "bossbar", "trigger",
                "function", "schedule", "scoreboard", "tag", "advancement", "recipe", "attribute", "effect",
                "enchant", "xp", "experience", "loot", "item", "particle", "playsound", "place", "locate",
                "locatebiome", "spreadplayers", "gamerule", "worldborder", "saveall", "saveon", "saveoff",
                "save-all", "save-on", "save-off", "ban-ip", "pardon-ip",
                "publish", "seed", "perf", "version", "plugins", "pl", "teammsg", "tm", "debug"
        ));
        return Collections.unmodifiableSet(new LinkedHashSet<>(reserved));
    }

    private boolean saveFile() {
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save kits.yml: " + ex.getMessage());
            return false;
        }
    }
}
