package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KitMenuService {
    private static final String MENU_ID = "kit-menu";
    private static final List<String> DEFAULT_THEME_ORDER = List.of(
            "Masonry & Castle",
            "Dark & Fantasy",
            "Organic & Landscaping",
            "Trees & Timber",
            "Interiors & Workspaces",
            "Towns & Streets",
            "Specialty Palettes",
            "Custom Kits"
    );

    private final BuilderKitService builderKitService;
    private final ListMenuConfigService listMenuConfigService;
    private final NamespacedKey actionKey;
    private final NamespacedKey pageKey;
    private final NamespacedKey kitKey;

    public KitMenuService(JavaPlugin plugin, BuilderKitService builderKitService, ListMenuConfigService listMenuConfigService) {
        this.builderKitService = builderKitService;
        this.listMenuConfigService = listMenuConfigService;
        this.actionKey = new NamespacedKey(plugin, "kit_menu_action");
        this.pageKey = new NamespacedKey(plugin, "kit_menu_page");
        this.kitKey = new NamespacedKey(plugin, "kit_menu_name");
    }

    public boolean openMenu(Player player, int requestedPage) {
        List<MenuPage> pages = buildPages();
        if (pages.isEmpty()) {
            ChatOutput.send(player, listMenuConfigService.format(
                    MENU_ID,
                    "empty-message",
                    "&fNo shared Bayzyl kits saved yet.",
                    Map.of()
            ));
            return false;
        }

        int page = Math.max(1, Math.min(requestedPage, pages.size()));
        MenuPage current = pages.get(page - 1);
        Map<String, String> pageTokens = ListMenuConfigService.tokens(
                "page", Integer.toString(page),
                "page_count", Integer.toString(pages.size()),
                "title", current.title(),
                "kit_count", Integer.toString(current.kits().size())
        );
        Inventory inventory = Bukkit.createInventory(
                new KitMenuHolder(page),
                54,
                listMenuConfigService.format(MENU_ID, "inventory-title", "&2BZL Kits {page}/{page_count}", pageTokens)
        );

        int pageSize = pageSize();
        for (int slot = 0; slot < current.kits().size() && slot < pageSize; slot++) {
            inventory.setItem(slot, createKitItem(current.kits().get(slot)));
        }

        if (page > 1) {
            inventory.setItem(45, createPageButton("previous", page - 1));
        }
        inventory.setItem(49, createInfoItem(current, page, pages.size()));
        if (page < pages.size()) {
            inventory.setItem(53, createPageButton("next", page + 1));
        }

        player.openInventory(inventory);
        return true;
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof KitMenuHolder)) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return true;
        }

        if (action.equals("page")) {
            Integer page = meta.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
            if (page != null) {
                openMenu(player, page);
            }
            return true;
        }

        if (!action.equals("kit")) {
            return true;
        }

        String kitName = meta.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (kitName == null || kitName.isBlank()) {
            return true;
        }

        if (event.isRightClick()) {
            player.performCommand("kit inspect " + kitName);
            return true;
        }

        player.closeInventory();
        player.performCommand("kit " + kitName);
        return true;
    }

    public boolean handleDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof KitMenuHolder)) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    private List<MenuPage> buildPages() {
        List<BuilderKitSummary> summaries = builderKitService.listKitSummaries();
        if (summaries.isEmpty()) {
            return List.of();
        }

        List<String> themeOrder = themeOrder();
        Map<String, List<BuilderKitSummary>> grouped = new LinkedHashMap<>();
        for (String theme : themeOrder) {
            grouped.put(theme, new ArrayList<>());
        }
        for (BuilderKitSummary summary : summaries) {
            String theme = normalizedTheme(summary.theme());
            grouped.computeIfAbsent(theme, ignored -> new ArrayList<>()).add(summary);
        }

        List<MenuPage> pages = new ArrayList<>();
        int pageSize = pageSize();
        grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .sorted(Comparator.<Map.Entry<String, List<BuilderKitSummary>>>comparingInt(entry -> themeSortIndex(entry.getKey(), themeOrder))
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    List<BuilderKitSummary> kits = entry.getValue().stream()
                            .sorted(Comparator.comparing(BuilderKitSummary::name, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    for (int start = 0; start < kits.size(); start += pageSize) {
                        int chunkIndex = (start / pageSize) + 1;
                        List<BuilderKitSummary> chunk = kits.subList(start, Math.min(kits.size(), start + pageSize));
                        Map<String, String> titleTokens = ListMenuConfigService.tokens(
                                "theme", entry.getKey(),
                                "theme_page", Integer.toString(chunkIndex)
                        );
                        String titlePath = kits.size() <= pageSize ? "title-single" : "title-split";
                        String titleFallback = kits.size() <= pageSize ? "{theme}" : "{theme} {theme_page}";
                        String title = listMenuConfigService.format(MENU_ID, titlePath, titleFallback, titleTokens);
                        pages.add(new MenuPage(title, List.copyOf(chunk)));
                    }
                });
        return List.copyOf(pages);
    }

    private ItemStack createKitItem(BuilderKitSummary summary) {
        Material icon = Material.matchMaterial(summary.iconMaterialKey());
        if (icon == null || !icon.isItem()) {
            icon = summary.scope() == BuilderKitScope.INVENTORY ? Material.CHEST : Material.BUNDLE;
        }
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        Map<String, String> tokens = kitTokens(summary);
        meta.setDisplayName(listMenuConfigService.format(MENU_ID, "kit-item.name", "&6{name}", tokens));
        List<String> lore = nonBlankLines(listMenuConfigService.formatList(
                MENU_ID,
                "kit-item.lore",
                List.of(
                        "&f{note}",
                        "&fTheme: &b{theme}",
                        "&fScope: &f{scope}",
                        "&fType: &f{kind}",
                        "&fStored: &f{item_count} items",
                        "{aliases_line}",
                        "&9Left-click to load",
                        "&9Right-click to inspect"
                ),
                tokens
        ));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "kit");
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, summary.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageButton(String section, int page) {
        String path = "page-buttons." + section;
        Material material = listMenuConfigService.material(MENU_ID, path + ".material", Material.ARROW);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Map<String, String> tokens = ListMenuConfigService.tokens("target_page", Integer.toString(page));
        meta.setDisplayName(listMenuConfigService.format(
                MENU_ID,
                path + ".name",
                section.equals("previous") ? "&6Previous Page" : "&6Next Page",
                tokens
        ));
        meta.setLore(nonBlankLines(listMenuConfigService.formatList(
                MENU_ID,
                path + ".lore",
                List.of("&9Open page {target_page}"),
                tokens
        )));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page");
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(MenuPage page, int currentPage, int pageCount) {
        ItemStack item = new ItemStack(listMenuConfigService.material(MENU_ID, "info-item.material", Material.BOOK));
        ItemMeta meta = item.getItemMeta();
        Map<String, String> tokens = ListMenuConfigService.tokens(
                "title", page.title(),
                "page", Integer.toString(currentPage),
                "page_count", Integer.toString(pageCount),
                "kit_count", Integer.toString(page.kits().size())
        );
        meta.setDisplayName(listMenuConfigService.format(MENU_ID, "info-item.name", "&aKit Browser", tokens));
        meta.setLore(nonBlankLines(listMenuConfigService.formatList(
                MENU_ID,
                "info-item.lore",
                List.of(
                        "&f{title}",
                        "&fPage: &6{page}/{page_count}",
                        "&fKits: &f{kit_count}",
                        "&9Use /kit list for the chat view"
                ),
                tokens
        )));
        item.setItemMeta(meta);
        return item;
    }

    private String normalizedTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return listMenuConfigService.value(MENU_ID, "default-theme", "Custom Kits");
        }
        return theme;
    }

    private String scopeLabel(BuilderKitScope scope) {
        return scope == BuilderKitScope.INVENTORY ? "inventory kit" : "hotbar kit";
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int themeSortIndex(String theme, List<String> themeOrder) {
        int index = themeOrder.indexOf(theme);
        return index >= 0 ? index : 1000 + theme.toLowerCase(Locale.ROOT).hashCode();
    }

    private int pageSize() {
        return listMenuConfigService.intValue(MENU_ID, "page-size", 45, 1, 45);
    }

    private List<String> themeOrder() {
        return listMenuConfigService.stringList(MENU_ID, "theme-order", DEFAULT_THEME_ORDER);
    }

    private Map<String, String> kitTokens(BuilderKitSummary summary) {
        String aliases = String.join(", ", summary.aliases());
        String aliasesLine = summary.aliases().isEmpty()
                ? ""
                : listMenuConfigService.format(
                        MENU_ID,
                        "kit-item.aliases-line",
                        "&fAliases: &f{aliases}",
                        ListMenuConfigService.tokens("aliases", aliases)
                );
        String noteFallback = listMenuConfigService.value(MENU_ID, "kit-item.note-fallback", "Shared builder kit");
        return ListMenuConfigService.tokens(
                "name", summary.name(),
                "note", nullSafe(summary.note(), noteFallback),
                "theme", normalizedTheme(summary.theme()),
                "scope", scopeLabel(summary.scope()),
                "kind", summary.builtIn() ? "default" : "custom",
                "item_count", Integer.toString(summary.itemCount()),
                "aliases", aliases,
                "aliases_line", aliasesLine
        );
    }

    private List<String> nonBlankLines(List<String> lines) {
        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && !stripped.isBlank()) {
                filtered.add(line);
            }
        }
        return filtered;
    }

    private record MenuPage(String title, List<BuilderKitSummary> kits) {
    }

    private record KitMenuHolder(int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
