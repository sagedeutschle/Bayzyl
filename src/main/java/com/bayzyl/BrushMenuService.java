package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-game brush menu (v3).
 *
 * Pages:
 *   Home          → Pattern Brushes / Brush Builder / Detail Brushes tiles
 *   Builder Home  → Friendly walkthrough explaining how to design + save
 *   Pattern List  → Browse brush types, toggle to "Saved" tab
 *   Detail List   → Browse built-in + saved variants
 *   Pattern Design → Steppers, material picker, Bind / Save & Equip
 *   Detail Design  → Override cyclers, Bind / Save & Equip
 *
 * "Save & Equip" closes the inventory, prompts the player to type a name in
 * chat, intercepts that next message, then on the main thread: switches to a
 * free hotbar slot, executes the bind command (which fills the slot with the
 * configured brush), and runs /brush save (or /db save) to persist it.
 */
public final class BrushMenuService {
    private static final int LIST_PAGE_SIZE = 36;
    private static final int MATERIAL_PICKER_PAGE_SIZE = 28;
    private static final long PENDING_SAVE_TTL_MS = 60_000L;
    private static final long PENDING_SEARCH_TTL_MS = 60_000L;

    private final JavaPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey dataKey;
    private final NamespacedKey extraKey;

    private final BrushPresetService brushPresetService;
    private final com.bayzyl.detail.DetailBrushService detailBrushService;
    private final com.bayzyl.detail.DetailBrushVariantService detailBrushVariantService;
    private final MessageThemeService messageThemeService;

    private final Map<UUID, PatternSession> patternSessions = new ConcurrentHashMap<>();
    private final Map<UUID, DetailSession> detailSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSave> pendingSaves = new ConcurrentHashMap<>();
    private final Map<UUID, PendingMaterialSearch> pendingMaterialSearches = new ConcurrentHashMap<>();

    private static final List<PatternSchema> PATTERN_SCHEMAS = List.of(
            new PatternSchema("sphere", "Sphere", Material.STONE, true, true, false, false, 5, 0, 0.7),
            new PatternSchema("hsphere", "Hollow Sphere", Material.GLASS, true, true, false, false, 5, 0, 0.7),
            new PatternSchema("cyl", "Cylinder", Material.OAK_LOG, true, true, true, false, 4, 4, 0.7),
            new PatternSchema("hcyl", "Hollow Cylinder", Material.OAK_FENCE, true, true, true, false, 4, 4, 0.7),
            new PatternSchema("pyramid", "Pyramid", Material.SANDSTONE, true, true, false, false, 5, 0, 0.7),
            new PatternSchema("hpyramid", "Hollow Pyramid", Material.CHISELED_SANDSTONE, true, true, false, false, 5, 0, 0.7),
            new PatternSchema("paint", "Paint", Material.GREEN_DYE, true, true, false, true, 5, 0, 0.4),
            new PatternSchema("surface", "Surface", Material.GRASS_BLOCK, true, true, false, true, 5, 0, 0.7),
            new PatternSchema("spatter", "Spatter", Material.MUD, true, true, false, true, 4, 0, 0.5),
            new PatternSchema("decay", "Decay", Material.COBBLESTONE, false, true, false, true, 5, 0, 0.7),
            new PatternSchema("restore", "Restore", Material.LEATHER, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("erase", "Erase", Material.BARRIER, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("naturalize", "Naturalize", Material.PODZOL, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("smooth", "Smooth", Material.SMOOTH_STONE, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("raise", "Raise", Material.DIRT, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("lower", "Lower", Material.COARSE_DIRT, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("flatten", "Flatten", Material.SMOOTH_STONE_SLAB, false, true, false, false, 5, 0, 0.0),
            new PatternSchema("vegetation", "Vegetation", Material.FERN, true, true, false, true, 5, 0, 0.5),
            new PatternSchema("clipboard", "Clipboard", Material.PAPER, false, false, false, false, 0, 0, 0.0),
            new PatternSchema("structure", "Structure", Material.STRUCTURE_BLOCK, false, false, false, false, 0, 0, 0.0)
    );

    private static final List<Material> MATERIAL_PALETTE = List.of(
            Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.DEEPSLATE,
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL,
            Material.SAND, Material.GRAVEL, Material.SANDSTONE,
            Material.OAK_LOG, Material.OAK_PLANKS, Material.OAK_LEAVES,
            Material.GLASS, Material.NETHERRACK, Material.GLOWSTONE
    );

    public BrushMenuService(JavaPlugin plugin,
                            BrushPresetService brushPresetService,
                            com.bayzyl.detail.DetailBrushService detailBrushService,
                            com.bayzyl.detail.DetailBrushVariantService detailBrushVariantService,
                            MessageThemeService messageThemeService) {
        this.plugin = plugin;
        this.brushPresetService = brushPresetService;
        this.detailBrushService = detailBrushService;
        this.detailBrushVariantService = detailBrushVariantService;
        this.messageThemeService = messageThemeService;
        this.actionKey = new NamespacedKey(plugin, "brush_menu_action");
        this.dataKey = new NamespacedKey(plugin, "brush_menu_data");
        this.extraKey = new NamespacedKey(plugin, "brush_menu_extra");
    }

    // ---------- Open: Home ----------

    public boolean openMenu(Player player) {
        return openHome(player);
    }

    public boolean openHome(Player player) {
        Inventory inv = Bukkit.createInventory(new HomeHolder(), 54,
                ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Bayzyl Brush Menu");
        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(4, simpleItem(Material.BRUSH, ChatColor.GREEN + "" + ChatColor.BOLD + "Brush Menu",
                List.of(ChatColor.GRAY + "Choose a category."), "header", null));
        inv.setItem(8, closeItem());

        inv.setItem(19, navTile(Material.STONE,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Pattern Brushes",
                List.of(
                        ChatColor.GRAY + "Sphere, cyl, paint, surface, decay…",
                        ChatColor.GRAY + "Browse types and saved brushes.",
                        "",
                        ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to open."
                ),
                "open-pattern", null));

        inv.setItem(22, navTile(Material.WRITABLE_BOOK,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Brush Builder",
                List.of(
                        ChatColor.GRAY + "Step-by-step walkthrough to design",
                        ChatColor.GRAY + "and save a custom brush. Saves to your",
                        ChatColor.GRAY + "hotbar so you can use it instantly.",
                        "",
                        ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to start."
                ),
                "open-builder", null));

        inv.setItem(25, navTile(Material.CAMPFIRE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Detail Brushes",
                List.of(
                        ChatColor.GRAY + "Flame, cloud, lightning, vine, bark…",
                        ChatColor.GRAY + "Built-in and saved variants.",
                        "",
                        ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to open."
                ),
                "open-detail", null));

        inv.setItem(31, navTile(Material.WHITE_DYE,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Menu Theme",
                List.of(
                        ChatColor.GRAY + "Pick the menu accent color.",
                        ChatColor.GRAY + "Current: " + ChatColor.WHITE + messageThemeService.accentValue(),
                        "",
                        ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to open."
                ),
                "open-theme", null));

        player.openInventory(inv);
        return true;
    }

    public boolean openThemePicker(Player player) {
        Inventory inv = Bukkit.createInventory(new ThemePickerHolder(), 54,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Menu Theme");
        fillBorder(inv, Material.WHITE_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Home", "home", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(Material.WHITE_DYE,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Menu Theme",
                List.of(
                        ChatColor.GRAY + "Click a color to set the menu accent.",
                        ChatColor.GRAY + "Current: " + ChatColor.WHITE + messageThemeService.accentValue()
                ),
                "header", null));

        int[] slots = {19, 20, 21, 22, 23, 24, 25,
                       28, 29, 30, 31, 32, 33, 34,
                       38, 42};
        String[] names = {"red", "gold", "yellow", "green", "aqua", "blue", "light_purple",
                          "dark_red", "dark_purple", "dark_blue", "dark_aqua", "dark_green", "dark_gray", "gray",
                          "white", "black"};
        Material[] mats = {Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL,
                Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.PINK_WOOL,
                Material.RED_TERRACOTTA, Material.PURPLE_WOOL, Material.BLUE_TERRACOTTA,
                Material.CYAN_WOOL, Material.GREEN_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL,
                Material.WHITE_WOOL, Material.BLACK_WOOL};

        String currentAccent = messageThemeService.accentValue();
        for (int i = 0; i < slots.length; i++) {
            boolean active = names[i].equalsIgnoreCase(currentAccent);
            ItemStack swatch = navTile(mats[i],
                    ChatColor.WHITE + names[i].replace('_', ' '),
                    List.of(active ? ChatColor.GREEN + "✔ Active" : ChatColor.GRAY + "Click to apply."),
                    "theme-set", names[i]);
            if (active) {
                ItemMeta meta = swatch.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    swatch.setItemMeta(meta);
                }
            }
            inv.setItem(slots[i], swatch);
        }

        inv.setItem(40, navTile(Material.BARRIER,
                ChatColor.WHITE + "Reset to default",
                List.of(ChatColor.GRAY + "Restore the default Bayzyl green."),
                "theme-reset", null));

        player.openInventory(inv);
        return true;
    }

    public boolean openBuilderHome(Player player) {
        Inventory inv = Bukkit.createInventory(new BuilderHomeHolder(), 54,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Brush Builder");
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Home", "home", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(Material.WRITABLE_BOOK,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Brush Builder",
                List.of(
                        ChatColor.GRAY + "How it works:",
                        ChatColor.WHITE + "1. " + ChatColor.GRAY + "Choose a brush family below.",
                        ChatColor.WHITE + "2. " + ChatColor.GRAY + "Pick a brush type from the list.",
                        ChatColor.WHITE + "3. " + ChatColor.GRAY + "Tweak parameters in the design page.",
                        ChatColor.WHITE + "4. " + ChatColor.GRAY + "Click " + ChatColor.GREEN + "Save & Equip" + ChatColor.GRAY + "; type a name in chat;",
                        ChatColor.WHITE + "   " + ChatColor.GRAY + "the brush lands in your hotbar."
                ),
                "header", null));

        inv.setItem(20, navTile(Material.STONE,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Pattern Brush",
                List.of(
                        ChatColor.GRAY + "Shape brushes with material and",
                        ChatColor.GRAY + "radius/height/density tuning."
                ),
                "open-pattern", null));

        inv.setItem(24, navTile(Material.CAMPFIRE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Detail Brush",
                List.of(
                        ChatColor.GRAY + "Flame, cloud, lightning, etc.",
                        ChatColor.GRAY + "Tune presets and overrides."
                ),
                "open-detail", null));

        inv.setItem(40, simpleItem(Material.PAPER,
                ChatColor.GRAY + "Tip",
                List.of(
                        ChatColor.DARK_GRAY + "You can also use " + ChatColor.WHITE + "Bind to Held Item"
                                + ChatColor.DARK_GRAY + " on the design page if you don't want to save."
                ),
                "header", null));

        player.openInventory(inv);
        return true;
    }

    // ---------- Open: Lists ----------

    public boolean openPatternList(Player player, int page, boolean savedTab) {
        if (savedTab) return openSavedPatternList(player, page);

        int totalPages = Math.max(1, (int) Math.ceil(PATTERN_SCHEMAS.size() / (double) LIST_PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));
        Inventory inv = Bukkit.createInventory(new PatternListHolder(page, false), 54,
                ChatColor.GOLD + "Pattern Brushes " + ChatColor.GRAY + page + "/" + totalPages);
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Home", "home", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(Material.STONE, ChatColor.GOLD + "Brush Types",
                List.of(ChatColor.GRAY + "Click a brush to design it."),
                "header", null));
        inv.setItem(7, simpleItem(Material.BOOK,
                ChatColor.WHITE + "Switch to: " + ChatColor.AQUA + "Saved",
                List.of(ChatColor.GRAY + "Show your saved pattern brushes."),
                "tab-pattern-saved", null));

        int from = (page - 1) * LIST_PAGE_SIZE;
        int to = Math.min(PATTERN_SCHEMAS.size(), from + LIST_PAGE_SIZE);
        int slot = 9;
        for (int i = from; i < to; i++) {
            inv.setItem(slot++, patternListItem(PATTERN_SCHEMAS.get(i)));
        }
        addPager(inv, page, totalPages, "page-pattern");
        player.openInventory(inv);
        return true;
    }

    private boolean openSavedPatternList(Player player, int page) {
        List<String> saved = brushPresetService.listBrushes();
        int totalPages = Math.max(1, (int) Math.ceil(saved.size() / (double) LIST_PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));
        Inventory inv = Bukkit.createInventory(new PatternListHolder(page, true), 54,
                ChatColor.AQUA + "Saved Pattern Brushes " + ChatColor.GRAY + page + "/" + totalPages);
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Home", "home", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(Material.CHEST,
                ChatColor.AQUA + "Saved Pattern Brushes",
                List.of(ChatColor.GRAY + "Your saved brushes from /brush save."),
                "header", null));
        inv.setItem(7, simpleItem(Material.BOOK,
                ChatColor.WHITE + "Switch to: " + ChatColor.GOLD + "Types",
                List.of(ChatColor.GRAY + "Show all built-in brush types."),
                "tab-pattern-types", null));

        if (saved.isEmpty()) {
            inv.setItem(22, simpleItem(Material.BARRIER,
                    ChatColor.YELLOW + "No saved pattern brushes yet",
                    List.of(
                            ChatColor.GRAY + "Design one in the Brush Builder, or run",
                            ChatColor.WHITE + "/brush save <name>" + ChatColor.GRAY + " on a held brush."
                    ),
                    "header", null));
        } else {
            int from = (page - 1) * LIST_PAGE_SIZE;
            int to = Math.min(saved.size(), from + LIST_PAGE_SIZE);
            int slot = 9;
            for (int i = from; i < to; i++) {
                inv.setItem(slot++, savedPatternItem(saved.get(i)));
            }
        }
        addPager(inv, page, totalPages, "page-pattern-saved");
        player.openInventory(inv);
        return true;
    }

    public boolean openDetailList(Player player, int page) {
        List<DetailEntry> entries = detailEntries();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) LIST_PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));
        Inventory inv = Bukkit.createInventory(new DetailListHolder(page), 54,
                ChatColor.LIGHT_PURPLE + "Detail Brushes " + ChatColor.GRAY + page + "/" + totalPages);
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Home", "home", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(Material.CAMPFIRE, ChatColor.LIGHT_PURPLE + "Detail Brushes",
                List.of(ChatColor.GRAY + "Click a variant to design overrides."),
                "header", null));

        int from = (page - 1) * LIST_PAGE_SIZE;
        int to = Math.min(entries.size(), from + LIST_PAGE_SIZE);
        int slot = 9;
        for (int i = from; i < to; i++) {
            inv.setItem(slot++, detailListItem(entries.get(i)));
        }
        addPager(inv, page, totalPages, "page-detail");
        player.openInventory(inv);
        return true;
    }

    // ---------- Open: Design pages ----------

    public boolean openPatternDesign(Player player, String brushId) {
        PatternSchema schema = patternSchema(brushId);
        if (schema == null) {
            player.closeInventory();
            ChatOutput.send(player, ChatColor.RED + "Unknown brush: " + brushId);
            return false;
        }
        PatternSession session = patternSessions.computeIfAbsent(player.getUniqueId(),
                id -> new PatternSession(schema));
        if (!session.brushId.equals(schema.id())) {
            session = new PatternSession(schema);
            patternSessions.put(player.getUniqueId(), session);
        }

        Inventory inv = Bukkit.createInventory(new PatternDesignHolder(schema.id()), 54,
                ChatColor.GOLD + "Design: " + schema.label());
        renderPatternDesign(inv, schema, session);

        player.openInventory(inv);
        return true;
    }

    public boolean openDetailDesign(Player player, String variantName) {
        com.bayzyl.detail.DetailBrushSettings base = detailBrushVariantService.load(variantName);
        boolean builtIn = false;
        String displayName = variantName;
        String presetId;
        if (base == null) {
            if (detailBrushVariantService.exists(variantName)) {
                player.closeInventory();
                ChatOutput.send(player, ChatColor.RED + "Saved variant " + variantName
                        + " is invalid and was left unchanged.");
                return false;
            }
            com.bayzyl.detail.DetailBrushVariant b = detailBrushService.registry().builtInVariant(variantName);
            if (b == null) {
                player.closeInventory();
                ChatOutput.send(player, ChatColor.RED + "Unknown variant: " + variantName);
                return false;
            }
            base = b.settings();
            displayName = b.displayName();
            builtIn = true;
        }
        presetId = base.presetId();
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(presetId);
        if (preset == null) {
            player.closeInventory();
            ChatOutput.send(player, ChatColor.RED + "Unknown preset: " + presetId);
            return false;
        }

        DetailSession session = detailSessions.computeIfAbsent(player.getUniqueId(),
                id -> new DetailSession(variantName));
        if (!session.variantName.equalsIgnoreCase(variantName)) {
            session = new DetailSession(variantName);
            detailSessions.put(player.getUniqueId(), session);
        }

        Inventory inv = Bukkit.createInventory(new DetailDesignHolder(variantName), 54,
                ChatColor.LIGHT_PURPLE + "Design: " + displayName);
        renderDetailDesign(inv, preset, displayName, builtIn, base, session);

        player.openInventory(inv);
        return true;
    }

    // ---------- Click router ----------

    public boolean handleClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isOurHolder(holder)) return false;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return true;
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return true;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return true;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action == null) return true;
        String data = pdc.get(dataKey, PersistentDataType.STRING);
        Integer extra = pdc.get(extraKey, PersistentDataType.INTEGER);

        switch (action) {
            case "close" -> player.closeInventory();
            case "home" -> openHome(player);
            case "open-builder" -> openBuilderHome(player);
            case "open-pattern" -> openPatternList(player, 1, false);
            case "open-detail" -> openDetailList(player, 1);
            case "open-theme" -> openThemePicker(player);
            case "theme-set" -> {
                if (data != null && messageThemeService.setAccent(data)) {
                    ChatOutput.send(player, ChatColor.WHITE + "Menu accent set to "
                            + ChatColor.WHITE + data.replace('_', ' ') + ".");
                }
                openThemePicker(player);
            }
            case "theme-reset" -> {
                messageThemeService.resetAccent();
                ChatOutput.send(player, ChatColor.WHITE + "Menu accent reset to default.");
                openThemePicker(player);
            }
            case "tab-pattern-saved" -> openPatternList(player, 1, true);
            case "tab-pattern-types" -> openPatternList(player, 1, false);
            case "page-pattern" -> openPatternList(player, parseInt(data, 1), false);
            case "page-pattern-saved" -> openPatternList(player, parseInt(data, 1), true);
            case "page-detail" -> openDetailList(player, parseInt(data, 1));
            case "select-pattern" -> openPatternDesign(player, data);
            case "select-saved-pattern" -> {
                player.closeInventory();
                if (data != null) player.performCommand("brush load " + data);
            }
            case "select-detail" -> openDetailDesign(player, data);
            case "design-step" -> {
                int direction = extra == null ? 1 : extra;
                int magnitude = event.isShiftClick() ? 5 : 1;
                stepPatternParam(player, data, direction * magnitude);
                refreshPatternDesign(player);
            }
            case "design-material" -> {
                setPatternMaterial(player, data);
                refreshPatternDesign(player);
            }
            case "material-more" -> startMaterialSearchPrompt(player, null);
            case "material-search" -> startMaterialSearchPrompt(player, currentMaterialSearch(player));
            case "material-clear-search" -> openMaterialPicker(player, null, 1);
            case "material-select" -> {
                if (data != null && !data.isBlank()) {
                    setPatternMaterial(player, data);
                    openPatternDesign(player, currentPatternId(player));
                }
            }
            case "page-material" -> openMaterialPicker(player, currentMaterialSearch(player), parseInt(data, 1));
            case "design-reset" -> {
                String id = currentPatternId(player);
                if (id != null) {
                    PatternSchema schema = patternSchema(id);
                    if (schema != null) {
                        patternSessions.put(player.getUniqueId(), new PatternSession(schema));
                        refreshPatternDesign(player);
                    }
                }
            }
            case "design-bind" -> {
                player.closeInventory();
                if (data != null && !data.isBlank()) {
                    String cmd = data.startsWith("/") ? data.substring(1) : data;
                    player.performCommand(cmd);
                }
            }
            case "detail-cycle" -> {
                int direction = event.isRightClick() ? -1 : 1;
                cycleDetailOverride(player, data, direction);
                refreshDetailDesign(player);
            }
            case "detail-reset" -> {
                String v = currentDetailVariant(player);
                if (v != null) {
                    detailSessions.put(player.getUniqueId(), new DetailSession(v));
                    refreshDetailDesign(player);
                }
            }
            case "detail-bind" -> {
                player.closeInventory();
                if (data != null && !data.isBlank()) {
                    String cmd = data.startsWith("/") ? data.substring(1) : data;
                    player.performCommand(cmd);
                }
            }
            case "save-equip" -> startSaveAndEquipPrompt(player, data, pdc.get(extraKey, PersistentDataType.INTEGER), pdc);
            default -> { /* ignore */ }
        }
        return true;
    }

    public boolean handleDrag(InventoryDragEvent event) {
        if (!isOurHolder(event.getView().getTopInventory().getHolder())) return false;
        event.setCancelled(true);
        return true;
    }

    /**
     * Routed from BayzylListener's AsyncChatEvent handler. If the player has a
     * pending Save & Equip request, intercept the message as the brush name
     * and apply the save+equip flow on the main thread.
     *
     * Returns true if the chat message was consumed.
     */
    public boolean handleChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player == null) return false;
        String message = event.signedMessage().message().trim();

        PendingMaterialSearch pendingSearch = pendingMaterialSearches.get(player.getUniqueId());
        if (pendingSearch != null) {
            if (System.currentTimeMillis() > pendingSearch.expiresAt) {
                pendingMaterialSearches.remove(player.getUniqueId());
                return false;
            }
            event.setCancelled(true);
            pendingMaterialSearches.remove(player.getUniqueId());
            if (message.isBlank() || message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abort")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String query = pendingSearch.query();
                    if (query != null && !query.isBlank()) {
                        openMaterialPicker(player, query, 1);
                    } else {
                        openPatternDesign(player, currentPatternId(player));
                    }
                });
                return true;
            }
            String query = message;
            Bukkit.getScheduler().runTask(plugin, () -> openMaterialPicker(player, query, 1));
            return true;
        }

        PendingSave pending = pendingSaves.get(player.getUniqueId());
        if (pending == null) return false;
        if (System.currentTimeMillis() > pending.expiresAt) {
            pendingSaves.remove(player.getUniqueId());
            return false;
        }
        event.setCancelled(true);
        pendingSaves.remove(player.getUniqueId());

        if (message.isBlank() || message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abort")) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    ChatOutput.send(player, ChatColor.YELLOW + "Brush save cancelled."));
            return true;
        }

        String safeName = normalizeBrushName(message);
        if (safeName == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    ChatOutput.send(player, ChatColor.RED + "That name has no usable characters. Save cancelled."));
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> applySaveAndEquip(player, pending, safeName));
        return true;
    }

    // ---------- Save & Equip flow ----------

    private void startSaveAndEquipPrompt(Player player, String previewCmd, Integer ignored, PersistentDataContainer pdc) {
        // Encode kind and ref via the data + extra slot. We piggy-back on dataKey
        // for the command, and on a second pdc string we don't currently expose,
        // so we keep both kinds inside `data` itself: format = "kind|ref|cmd".
        if (previewCmd == null || previewCmd.isBlank()) return;
        String[] parts = previewCmd.split("\\|", 3);
        if (parts.length != 3) return;
        String kind = parts[0];
        String ref = parts[1];
        String command = parts[2];

        pendingSaves.put(player.getUniqueId(),
                new PendingSave(kind, ref, command, System.currentTimeMillis() + PENDING_SAVE_TTL_MS));
        player.closeInventory();
        ChatOutput.send(player, ChatColor.AQUA + "Type a name in chat to save this brush.");
        ChatOutput.send(player, ChatColor.GRAY + "Allowed: letters, digits, hyphen, underscore. Type "
                + ChatColor.WHITE + "cancel" + ChatColor.GRAY + " to skip. (60s)");
    }

    private void applySaveAndEquip(Player player, PendingSave pending, String name) {
        // 1) Find a free hotbar slot (or use main hand if empty).
        int targetSlot = -1;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        if (isEmpty(inv.getItemInMainHand())) {
            targetSlot = inv.getHeldItemSlot();
        } else {
            for (int i = 0; i < 9; i++) {
                if (isEmpty(inv.getItem(i))) {
                    targetSlot = i;
                    break;
                }
            }
        }
        if (targetSlot < 0) {
            ChatOutput.send(player, ChatColor.RED + "No empty hotbar slot. Free a slot and try again.");
            return;
        }
        inv.setHeldItemSlot(targetSlot);

        // 2) Bind the brush to the now-held empty slot.
        String bindCommand = pending.command.startsWith("/") ? pending.command.substring(1) : pending.command;
        boolean bound = player.performCommand(bindCommand);
        if (!bound) {
            ChatOutput.send(player, ChatColor.RED + "Could not bind brush. Save cancelled.");
            return;
        }

        // 3) Save the held brush by kind.
        String saveCommand;
        if ("pattern".equals(pending.kind)) {
            saveCommand = "brush save " + name;
        } else if ("detail".equals(pending.kind)) {
            saveCommand = "db save " + name;
        } else {
            ChatOutput.send(player, ChatColor.RED + "Unknown brush kind: " + pending.kind);
            return;
        }
        boolean saved = player.performCommand(saveCommand);
        if (saved) {
            ChatOutput.send(player, ChatColor.GREEN + "Saved as " + ChatColor.WHITE + name
                    + ChatColor.GREEN + " and equipped to hotbar slot " + (targetSlot + 1) + ".");
        } else {
            ChatOutput.send(player, ChatColor.YELLOW + "Brush bound, but save failed (name may be reserved).");
        }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private String normalizeBrushName(String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder();
        for (char c : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                out.append(c);
            } else if (c == ' ') {
                out.append('-');
            }
        }
        if (out.length() == 0) return null;
        if (out.length() > 32) return out.substring(0, 32);
        return out.toString();
    }

    // ---------- Session mutators ----------

    private void stepPatternParam(Player player, String paramName, int delta) {
        PatternSession s = patternSessions.get(player.getUniqueId());
        if (s == null || paramName == null) return;
        switch (paramName) {
            case "radius" -> s.radius = clamp(s.radius + delta, 1, 32);
            case "height" -> s.height = clamp(s.height + delta, 1, 32);
            case "density" -> {
                double d = s.density + delta * 0.05;
                s.density = Math.max(0.05, Math.min(1.0, Math.round(d * 100.0) / 100.0));
            }
            default -> {}
        }
    }

    private void refreshPatternDesign(Player player) {
        PatternSession session = patternSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof PatternDesignHolder holder)) {
            return;
        }
        PatternSchema schema = patternSchema(holder.brushId());
        if (schema == null) {
            return;
        }
        renderPatternDesign(top, schema, session);
    }

    private void renderPatternDesign(Inventory inv, PatternSchema schema, PatternSession session) {
        inv.clear();
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Pattern List", "open-pattern", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(schema.icon(),
                ChatColor.GOLD + schema.label(),
                List.of(ChatColor.GRAY + "Tweak, then bind or save."), "header", null));

        if (schema.wantsRadius()) {
            inv.setItem(10, stepperButton("radius", -1, ChatColor.RED + "− Radius"));
            inv.setItem(11, valueDisplay(Material.SLIME_BALL,
                    ChatColor.YELLOW + "Radius: " + ChatColor.WHITE + session.radius,
                    List.of(ChatColor.GRAY + "Range 1..32",
                            ChatColor.DARK_GRAY + "L-click +1 / R-click −1 / Shift ±5")));
            inv.setItem(12, stepperButton("radius", 1, ChatColor.GREEN + "+ Radius"));
        }
        if (schema.wantsHeight()) {
            inv.setItem(14, stepperButton("height", -1, ChatColor.RED + "− Height"));
            inv.setItem(15, valueDisplay(Material.LADDER,
                    ChatColor.YELLOW + "Height: " + ChatColor.WHITE + session.height,
                    List.of(ChatColor.GRAY + "Range 1..32",
                            ChatColor.DARK_GRAY + "L-click +1 / R-click −1 / Shift ±5")));
            inv.setItem(16, stepperButton("height", 1, ChatColor.GREEN + "+ Height"));
        }
        if (schema.wantsDensity()) {
            inv.setItem(19, stepperButton("density", -1, ChatColor.RED + "− Density"));
            inv.setItem(20, valueDisplay(Material.HONEY_BLOCK,
                    ChatColor.YELLOW + "Density: " + ChatColor.WHITE + formatDensity(session.density),
                    List.of(ChatColor.GRAY + "Range 0.05..1.0",
                            ChatColor.DARK_GRAY + "L-click +0.05 / R-click −0.05 / Shift ±0.25")));
            inv.setItem(21, stepperButton("density", 1, ChatColor.GREEN + "+ Density"));
        }

        if (schema.wantsMaterial()) {
            for (int i = 0; i < MATERIAL_PALETTE.size() && i < 17; i++) {
                Material mat = MATERIAL_PALETTE.get(i);
                boolean selected = session.material == mat;
                inv.setItem(27 + i, materialPickerItem(mat, selected));
            }
            inv.setItem(44, simpleItem(Material.BOOKSHELF,
                    ChatColor.GOLD + "" + ChatColor.BOLD + "More...",
                    List.of(
                            ChatColor.GRAY + "Search the full block catalog.",
                            ChatColor.GRAY + "Type a block name in chat."
                    ),
                    "material-more", null));
        } else {
            inv.setItem(31, simpleItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    ChatColor.GRAY + "No material needed",
                    List.of(ChatColor.DARK_GRAY + "This brush operates on existing terrain."),
                    "header", null));
        }

        String previewCmd = buildPatternCommand(schema, session);
        inv.setItem(45, simpleItem(Material.SPONGE,
                ChatColor.YELLOW + "Reset to Defaults",
                List.of(ChatColor.DARK_GRAY + "Clears your in-progress tweaks."),
                "design-reset", null));
        inv.setItem(49, simpleItem(Material.WRITABLE_BOOK,
                ChatColor.AQUA + "Preview",
                List.of(ChatColor.GRAY + previewCmd),
                "header", null));
        inv.setItem(51, bindButton(previewCmd, schema.label(), false));
        inv.setItem(53, saveAndEquipButton(previewCmd, "pattern", schema.id()));
    }

    public void openMaterialPicker(Player player, String query, int page) {
        List<Material> materials = materialPickerMaterials(query);
        int totalPages = Math.max(1, (int) Math.ceil(materials.size() / (double) MATERIAL_PICKER_PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));
        Inventory inv = Bukkit.createInventory(new MaterialPickerHolder(page, query), 54,
                ChatColor.GOLD + "Block Picker " + ChatColor.GRAY + page + "/" + totalPages);
        fillBorder(inv, Material.WHITE_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Design", "open-pattern", null));
        inv.setItem(4, simpleItem(Material.GRASS_BLOCK,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Block Picker",
                List.of(
                        ChatColor.GRAY + "Filter: " + ChatColor.WHITE + (query == null || query.isBlank() ? "all blocks" : query),
                        ChatColor.GRAY + "Click a block to select it."
                ),
                "header", null));
        inv.setItem(8, closeItem());
        if (page > 1) {
            inv.setItem(45, simpleItem(Material.SPECTRAL_ARROW,
                    ChatColor.WHITE + "← Previous", List.of(), "page-material", Integer.toString(page - 1)));
        }
        inv.setItem(47, simpleItem(Material.NAME_TAG,
                ChatColor.WHITE + "Search", List.of(ChatColor.GRAY + "Type a block name in chat."),
                "material-search", query));
        inv.setItem(49, simpleItem(Material.PAPER,
                ChatColor.GRAY + "Page " + ChatColor.WHITE + page + ChatColor.GRAY + "/" + totalPages,
                List.of(), "header", null));
        if (query != null && !query.isBlank()) {
            inv.setItem(51, simpleItem(Material.BARRIER,
                    ChatColor.RED + "Clear Filter", List.of(ChatColor.GRAY + "Show every block."), "material-clear-search", null));
        }
        if (page < totalPages) {
            inv.setItem(53, simpleItem(Material.SPECTRAL_ARROW,
                    ChatColor.WHITE + "Next →", List.of(), "page-material", Integer.toString(page + 1)));
        }

        int from = (page - 1) * MATERIAL_PICKER_PAGE_SIZE;
        int to = Math.min(materials.size(), from + MATERIAL_PICKER_PAGE_SIZE);
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        if (materials.isEmpty()) {
            inv.setItem(22, simpleItem(Material.BARRIER,
                    ChatColor.YELLOW + "No blocks match",
                    List.of(ChatColor.GRAY + "Try a broader search."),
                    "header", null));
        } else {
            for (int i = from, slotIndex = 0; i < to && slotIndex < slots.length; i++, slotIndex++) {
                Material material = materials.get(i);
                boolean selected = currentPatternMaterial(player) == material;
                inv.setItem(slots[slotIndex], materialPickerItem(material, selected));
            }
        }
        player.openInventory(inv);
    }

    private void setPatternMaterial(Player player, String materialName) {
        PatternSession s = patternSessions.get(player.getUniqueId());
        if (s == null || materialName == null) return;
        Material m = Material.matchMaterial(materialName);
        if (m != null) s.material = m;
    }

    private Material currentPatternMaterial(Player player) {
        PatternSession s = patternSessions.get(player.getUniqueId());
        return s == null ? null : s.material;
    }

    private void startMaterialSearchPrompt(Player player, String currentQuery) {
        pendingMaterialSearches.put(player.getUniqueId(),
                new PendingMaterialSearch(currentQuery, System.currentTimeMillis() + PENDING_SEARCH_TTL_MS));
        player.closeInventory();
        ChatOutput.send(player, ChatColor.AQUA + "Type a block name to filter the picker.");
        ChatOutput.send(player, ChatColor.GRAY + "Use " + ChatColor.WHITE + "cancel" + ChatColor.GRAY + " to stop. Search supports partial names.");
    }

    private String currentMaterialSearch(Player player) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof MaterialPickerHolder materialPickerHolder) {
            return materialPickerHolder.query();
        }
        return null;
    }

    private List<Material> materialPickerMaterials(String query) {
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isBlock() || material == Material.AIR) {
                continue;
            }
            materials.add(material);
        }
        materials.sort(Comparator.comparing(material -> prettyMaterial(material), String.CASE_INSENSITIVE_ORDER));
        if (query == null || query.isBlank()) {
            return materials;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        String compact = needle.replace(' ', '_');
        List<Material> filtered = new ArrayList<>();
        for (Material material : materials) {
            String name = material.name().toLowerCase(Locale.ROOT);
            String key = material.getKey().getKey().toLowerCase(Locale.ROOT);
            String pretty = prettyMaterial(material).toLowerCase(Locale.ROOT);
            if (name.contains(needle) || name.contains(compact) || key.contains(needle) || key.contains(compact) || pretty.contains(needle)) {
                filtered.add(material);
            }
        }
        return filtered;
    }

    private void cycleDetailOverride(Player player, String paramName, int direction) {
        DetailSession s = detailSessions.get(player.getUniqueId());
        if (s == null || paramName == null) return;
        com.bayzyl.detail.DetailBrushSettings settings = detailBrushVariantService.load(s.variantName);
        if (settings == null) {
            if (detailBrushVariantService.exists(s.variantName)) {
                return;
            }
            com.bayzyl.detail.DetailBrushVariant builtIn = detailBrushService.registry().builtInVariant(s.variantName);
            settings = builtIn == null ? null : builtIn.settings();
        }
        com.bayzyl.detail.DetailBrushPreset preset = settings == null
                ? null : detailBrushService.registry().get(settings.presetId());
        List<String> options = preset == null ? List.of()
                : detailBrushService.safety().allowedValues(preset.id(), paramName);
        if (options.isEmpty()) return;
        String current = s.overrides.getOrDefault(paramName, options.get(0));
        int idx = options.indexOf(current.toLowerCase(Locale.ROOT));
        if (idx < 0) idx = 0;
        idx = (idx + direction + options.size()) % options.size();
        s.overrides.put(paramName, options.get(idx));
    }

    private void refreshDetailDesign(Player player) {
        DetailSession session = detailSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof DetailDesignHolder holder)) {
            return;
        }
        String variantName = holder.variantName();
        com.bayzyl.detail.DetailBrushSettings base = detailBrushVariantService.load(variantName);
        boolean builtIn = false;
        String displayName = variantName;
        if (base == null) {
            if (detailBrushVariantService.exists(variantName)) {
                return;
            }
            com.bayzyl.detail.DetailBrushVariant b = detailBrushService.registry().builtInVariant(variantName);
            if (b == null) {
                return;
            }
            base = b.settings();
            displayName = b.displayName();
            builtIn = true;
        }
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(base.presetId());
        if (preset == null) {
            return;
        }
        renderDetailDesign(top, preset, displayName, builtIn, base, session);
    }

    private void renderDetailDesign(Inventory inv,
                                    com.bayzyl.detail.DetailBrushPreset preset,
                                    String displayName,
                                    boolean builtIn,
                                    com.bayzyl.detail.DetailBrushSettings base,
                                    DetailSession session) {
        inv.clear();
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);
        inv.setItem(0, navItem(Material.ARROW, ChatColor.WHITE + "Back to Detail List", "open-detail", null));
        inv.setItem(8, closeItem());
        inv.setItem(4, simpleItem(detailIcon(base.presetId()),
                ChatColor.LIGHT_PURPLE + displayName + (builtIn ? " " + ChatColor.AQUA + "[built-in]" : " " + ChatColor.GREEN + "[saved]"),
                List.of(
                        ChatColor.GRAY + "Preset: " + ChatColor.WHITE + base.presetId(),
                        ChatColor.GRAY + "Cycle override values, then bind or save."
                ), "header", null));

        int slot = 19;
        for (com.bayzyl.detail.DetailBrushParameterSpec spec : preset.parameterSpecs()) {
            if (spec.type() != com.bayzyl.detail.DetailBrushParameterType.STRING) continue;
            List<String> options = detailBrushService.safety().allowedValues(preset.id(), spec.name());
            if (options.isEmpty()) continue;
            String current = session.overrides.getOrDefault(spec.name(),
                    base.parameters().get(spec.name(), spec.defaultValue()));
            inv.setItem(slot, overrideCyclerItem(spec.name(), current, options));
            slot++;
            if (slot == 26) slot = 28;
            if (slot >= 35) break;
        }

        String previewCmd = "/" + buildDetailCommand(session);
        inv.setItem(45, simpleItem(Material.SPONGE,
                ChatColor.YELLOW + "Clear Overrides",
                List.of(ChatColor.DARK_GRAY + "Reset to variant defaults."),
                "detail-reset", null));
        inv.setItem(49, simpleItem(Material.WRITABLE_BOOK,
                ChatColor.AQUA + "Preview",
                List.of(ChatColor.GRAY + previewCmd),
                "header", null));
        inv.setItem(51, bindButton(previewCmd, displayName, true));
        inv.setItem(53, saveAndEquipButton(previewCmd, "detail", session.variantName));
    }

    private String currentPatternId(Player player) {
        PatternSession s = patternSessions.get(player.getUniqueId());
        return s == null ? null : s.brushId;
    }

    private String currentDetailVariant(Player player) {
        DetailSession s = detailSessions.get(player.getUniqueId());
        return s == null ? null : s.variantName;
    }

    // ---------- Command builders ----------

    private String buildPatternCommand(PatternSchema schema, PatternSession s) {
        StringBuilder cmd = new StringBuilder("/brush ").append(schema.id());
        if (schema.wantsMaterial()) cmd.append(' ').append(s.material.getKey().getKey());
        if (schema.wantsRadius()) cmd.append(' ').append(s.radius);
        if (schema.wantsHeight()) cmd.append(' ').append(s.height);
        if (schema.wantsDensity()) cmd.append(" density:").append(formatDensity(s.density));
        return cmd.toString();
    }

    private String buildDetailCommand(DetailSession s) {
        StringBuilder cmd = new StringBuilder("db ").append(s.variantName);
        for (Map.Entry<String, String> entry : s.overrides.entrySet()) {
            cmd.append(' ').append(entry.getValue());
        }
        return cmd.toString();
    }

    // ---------- Helpers ----------

    private boolean isOurHolder(InventoryHolder h) {
        return h instanceof HomeHolder
                || h instanceof BuilderHomeHolder
                || h instanceof PatternListHolder
                || h instanceof DetailListHolder
                || h instanceof PatternDesignHolder
                || h instanceof DetailDesignHolder
                || h instanceof MaterialPickerHolder
                || h instanceof ThemePickerHolder;
    }

    private List<DetailEntry> detailEntries() {
        List<DetailEntry> entries = new ArrayList<>();
        for (com.bayzyl.detail.DetailBrushPreset preset : detailBrushService.registry().all()) {
            for (com.bayzyl.detail.DetailBrushVariant v
                    : detailBrushService.registry().builtInVariants(preset.id())) {
                entries.add(new DetailEntry(v.name(), v.displayName(), preset.id(), true));
            }
        }
        for (String name : detailBrushVariantService.list()) {
            entries.add(new DetailEntry(name, name, "saved", false));
        }
        return entries;
    }

    private PatternSchema patternSchema(String id) {
        if (id == null) return null;
        for (PatternSchema s : PATTERN_SCHEMAS) {
            if (s.id().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return fallback; }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String formatDensity(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    // ---------- Item builders ----------

    private ItemStack simpleItem(Material mat, String name, List<String> lore, String action, String data) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (data != null) meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(Material mat, String name, String action, String data) {
        return simpleItem(mat, name, List.of(), action, data);
    }

    private ItemStack navTile(Material mat, String name, List<String> lore, String action, String data) {
        return simpleItem(mat, name, lore, action, data);
    }

    private ItemStack closeItem() {
        return simpleItem(Material.BARRIER, ChatColor.RED + "Close", List.of(), "close", null);
    }

    private ItemStack patternListItem(PatternSchema schema) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Pattern brush type.");
        StringBuilder params = new StringBuilder();
        if (schema.wantsMaterial()) params.append("material ");
        if (schema.wantsRadius()) params.append("radius ");
        if (schema.wantsHeight()) params.append("height ");
        if (schema.wantsDensity()) params.append("density ");
        if (params.length() == 0) params.append("(no params)");
        lore.add(ChatColor.DARK_GRAY + "Params: " + params.toString().trim());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to design.");
        return simpleItem(schema.icon(), ChatColor.GOLD + schema.label(), lore, "select-pattern", schema.id());
    }

    private ItemStack savedPatternItem(String name) {
        return simpleItem(Material.BRUSH,
                ChatColor.AQUA + name,
                List.of(
                        ChatColor.GRAY + "Saved pattern brush.",
                        "",
                        ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to load onto held item."
                ),
                "select-saved-pattern", name);
    }

    private ItemStack detailListItem(DetailEntry entry) {
        String tag = entry.builtIn() ? ChatColor.AQUA + "[built-in]" : ChatColor.GREEN + "[saved]";
        return simpleItem(detailIcon(entry.presetId()),
                ChatColor.LIGHT_PURPLE + entry.displayName() + " " + tag,
                List.of(
                        ChatColor.GRAY + "Detail brush: " + ChatColor.WHITE + entry.presetId(),
                        "",
                        ChatColor.YELLOW + "Click " + ChatColor.GRAY + "to design overrides."
                ),
                "select-detail", entry.id());
    }

    private ItemStack stepperButton(String paramName, int direction, String displayName) {
        Material mat = direction > 0 ? Material.LIME_DYE : Material.RED_DYE;
        ItemStack item = simpleItem(mat, displayName,
                List.of(ChatColor.DARK_GRAY + "L-click "
                        + (direction > 0 ? "+1" : "−1")
                        + ChatColor.DARK_GRAY + " | Shift-L "
                        + (direction > 0 ? "+5" : "−5")),
                "design-step", paramName);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(extraKey, PersistentDataType.INTEGER, direction);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack valueDisplay(Material mat, String name, List<String> lore) {
        return simpleItem(mat, name, lore, "header", null);
    }

    private ItemStack materialPickerItem(Material mat, boolean selected) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((selected ? ChatColor.GREEN + "✔ " : ChatColor.WHITE + "")
                + prettyMaterial(mat));
        meta.setLore(List.of(
                ChatColor.GRAY + (selected ? "Currently selected." : "Click to select."),
                ChatColor.DARK_GRAY + mat.getKey().toString()
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "design-material");
        meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, mat.getKey().getKey());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (selected) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack overrideCyclerItem(String paramName, String currentValue, List<String> options) {
        return simpleItem(Material.ENDER_EYE,
                ChatColor.YELLOW + paramName + ": " + ChatColor.WHITE + currentValue,
                List.of(
                        ChatColor.GRAY + "Options: " + ChatColor.WHITE + String.join(", ", options),
                        "",
                        ChatColor.DARK_GRAY + "L-click cycle next | R-click cycle back"
                ),
                "detail-cycle", paramName);
    }

    private ItemStack bindButton(String command, String label, boolean isDetail) {
        return simpleItem(Material.IRON_BLOCK,
                ChatColor.WHITE + "" + ChatColor.BOLD + "Bind to Held Item",
                List.of(
                        ChatColor.GRAY + "Run: " + ChatColor.WHITE + command,
                        ChatColor.DARK_GRAY + "(no save; just binds)"
                ),
                isDetail ? "detail-bind" : "design-bind",
                command);
    }

    private ItemStack saveAndEquipButton(String command, String kind, String ref) {
        // Pack kind|ref|command into the data slot for the click handler.
        String packed = kind + "|" + ref + "|" + command;
        return simpleItem(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "" + ChatColor.BOLD + "Save & Equip",
                List.of(
                        ChatColor.GRAY + "Type a name in chat after clicking.",
                        ChatColor.GRAY + "Will go to a free hotbar slot and save",
                        ChatColor.GRAY + "as a named " + kind + " brush.",
                        ChatColor.DARK_GRAY + "Run: " + ChatColor.WHITE + command
                ),
                "save-equip", packed);
    }

    private void fillBorder(Inventory inv, Material pane) {
        ItemStack filler = simpleItem(pane, " ", List.of(), "header", null);
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        for (int i = 9; i < 45; i += 9) {
            inv.setItem(i, filler);
            inv.setItem(i + 8, filler);
        }
    }

    private void addPager(Inventory inv, int page, int totalPages, String pageAction) {
        if (page > 1) {
            inv.setItem(45, simpleItem(Material.SPECTRAL_ARROW,
                    ChatColor.WHITE + "← Previous", List.of(), pageAction, Integer.toString(page - 1)));
        }
        inv.setItem(49, simpleItem(Material.PAPER,
                ChatColor.GRAY + "Page " + ChatColor.WHITE + page + ChatColor.GRAY + "/" + totalPages,
                List.of(), "header", null));
        if (page < totalPages) {
            inv.setItem(53, simpleItem(Material.SPECTRAL_ARROW,
                    ChatColor.WHITE + "Next →", List.of(), pageAction, Integer.toString(page + 1)));
        }
    }

    private static Material detailIcon(String presetId) {
        if (presetId == null) return Material.BRUSH;
        return switch (presetId.toLowerCase(Locale.ROOT)) {
            case "flame" -> Material.CAMPFIRE;
            case "cloud" -> Material.WHITE_CONCRETE;
            case "lightning" -> Material.LIGHTNING_ROD;
            case "vine" -> Material.VINE;
            case "bark" -> Material.OAK_LOG;
            case "hearts" -> Material.PINK_CONCRETE;
            case "rainbow" -> Material.AMETHYST_CLUSTER;
            default -> Material.BRUSH;
        };
    }

    private static String prettyMaterial(Material mat) {
        String s = mat.getKey().getKey().replace('_', ' ');
        StringBuilder out = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            out.append(cap ? Character.toUpperCase(c) : c);
            cap = c == ' ';
        }
        return out.toString();
    }

    // ---------- Records ----------

    private record PatternSchema(
            String id, String label, Material icon,
            boolean wantsMaterial, boolean wantsRadius, boolean wantsHeight, boolean wantsDensity,
            int defaultRadius, int defaultHeight, double defaultDensity
    ) {}

    private record DetailEntry(String id, String displayName, String presetId, boolean builtIn) {}

    private record PendingSave(String kind, String ref, String command, long expiresAt) {}

    private record PendingMaterialSearch(String query, long expiresAt) {}

    private static final class PatternSession {
        final String brushId;
        Material material;
        int radius;
        int height;
        double density;

        PatternSession(PatternSchema schema) {
            this.brushId = schema.id();
            this.material = schema.icon().isBlock() ? schema.icon() : Material.STONE;
            this.radius = Math.max(1, schema.defaultRadius());
            this.height = Math.max(1, schema.defaultHeight());
            this.density = schema.defaultDensity() <= 0 ? 0.7 : schema.defaultDensity();
        }
    }

    private static final class DetailSession {
        final String variantName;
        final Map<String, String> overrides = new LinkedHashMap<>();

        DetailSession(String variantName) {
            this.variantName = variantName;
        }
    }

    private record HomeHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private record BuilderHomeHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private record PatternListHolder(int page, boolean savedTab) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private record DetailListHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private record PatternDesignHolder(String brushId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private record DetailDesignHolder(String variantName) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ThemePickerHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record MaterialPickerHolder(int page, String query) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
