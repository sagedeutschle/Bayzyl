package com.bayzyl;

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

public final class KitShortcutRegistry {
    private final JavaPlugin plugin;
    private final BuilderKitService builderKitService;
    private final Set<String> registeredShortcuts = new LinkedHashSet<>();
    private TabExecutor handler;

    public KitShortcutRegistry(JavaPlugin plugin, BuilderKitService builderKitService) {
        this.plugin = plugin;
        this.builderKitService = builderKitService;
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
                plugin.getLogger().warning("Could not refresh kit shortcuts: knownCommands");
                return;
            }
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            removeOldCommands(knownCommands);

            Set<String> newShortcuts = new LinkedHashSet<>();
            for (String label : builderKitService.listShortcutLabels()) {
                if (!canRegisterShortcut(label, knownCommands)) {
                    continue;
                }
                BayzylDynamicCommand command = new BayzylDynamicCommand(
                        plugin,
                        label,
                        "Bayzyl builder kit shortcut",
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
            plugin.getLogger().warning("Could not refresh kit shortcuts: " + ex.getMessage());
        }
    }

    public boolean isShortcutRegistered(String rawName) {
        String normalized = builderKitService.normalizeName(rawName);
        return normalized != null && registeredShortcuts.contains(normalized);
    }

    public List<String> listShortcutNames() {
        return Collections.unmodifiableList(new ArrayList<>(registeredShortcuts));
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
        if (builderKitService.isReservedName(name)) {
            return false;
        }
        Command current = knownCommands.get(name);
        return current == null || current instanceof BayzylDynamicCommand;
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
