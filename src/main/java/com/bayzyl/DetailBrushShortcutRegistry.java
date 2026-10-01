package com.bayzyl;

import com.bayzyl.detail.DetailBrushPresetRegistry;
import com.bayzyl.detail.DetailBrushVariantService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DetailBrushShortcutRegistry {
    private static final Set<String> DISABLED_BUILT_IN_SHORTCUTS = Set.of(
            "pastel-arc"
    );

    private final JavaPlugin plugin;
    private final DetailBrushPresetRegistry presetRegistry;
    private final DetailBrushVariantService variantService;
    private final Set<String> registeredShortcuts = new LinkedHashSet<>();
    private TabExecutor handler;

    public DetailBrushShortcutRegistry(JavaPlugin plugin, DetailBrushPresetRegistry presetRegistry, DetailBrushVariantService variantService) {
        this.plugin = plugin;
        this.presetRegistry = presetRegistry;
        this.variantService = variantService;
    }

    public void bind(TabExecutor handler) {
        this.handler = handler;
        refresh();
    }

    public void refresh() {
        if (handler == null) {
            return;
        }

        try {
            Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            if (knownCommandsField == null) {
                plugin.getLogger().warning("Could not refresh detail brush shortcuts: knownCommands");
                return;
            }
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            removeOldCommands(knownCommands);

            Set<String> newShortcuts = new LinkedHashSet<>();
            for (String label : listCandidateLabels()) {
                if (!canRegisterShortcut(label, knownCommands)) {
                    continue;
                }
                BayzylDynamicCommand command = new BayzylDynamicCommand(
                        plugin,
                        label,
                        "Bayzyl detail brush variant shortcut",
                        "/" + label,
                        handler
                );
                knownCommands.put(label, command);
                knownCommands.put(plugin.getName().toLowerCase(Locale.ROOT) + ":" + label, command);
                newShortcuts.add(label);
            }
            registeredShortcuts.clear();
            registeredShortcuts.addAll(newShortcuts);
            syncCommands();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not refresh detail brush shortcuts: " + ex.getMessage());
        }
    }

    public boolean isShortcutRegistered(String rawName) {
        String normalized = variantService.normalizeName(rawName);
        return normalized != null && registeredShortcuts.contains(normalized);
    }

    public List<String> listShortcutNames() {
        return Collections.unmodifiableList(new ArrayList<>(registeredShortcuts));
    }

    private List<String> listCandidateLabels() {
        Set<String> labels = new LinkedHashSet<>(presetRegistry.builtInVariantNames());
        labels.removeAll(DISABLED_BUILT_IN_SHORTCUTS);
        labels.addAll(variantService.list());
        return List.copyOf(labels);
    }

    private void removeOldCommands(Map<String, Command> knownCommands) {
        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            Command command = entry.getValue();
            if (!(command instanceof BayzylDynamicCommand)) {
                continue;
            }
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String label = command.getName().toLowerCase(Locale.ROOT);
            if (registeredShortcuts.contains(label) || registeredShortcuts.contains(trimNamespace(key))) {
                keysToRemove.add(entry.getKey());
            }
        }
        for (String key : keysToRemove) {
            knownCommands.remove(key);
        }
    }

    private boolean canRegisterShortcut(String name, Map<String, Command> knownCommands) {
        String normalized = variantService.normalizeName(name);
        if (normalized == null || variantService.isReservedName(normalized)) {
            return false;
        }
        Command current = knownCommands.get(normalized);
        return current == null;
    }

    private String trimNamespace(String key) {
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void syncCommands() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // Some server implementations do not expose syncCommands.
        }
    }
}
