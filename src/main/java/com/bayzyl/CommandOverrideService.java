package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandOverrideService {
    private static final Map<String, Map<String, Command>> DISPLACED_COMMANDS = new LinkedHashMap<>();

    private CommandOverrideService() {
    }

    public static ClaimSummary claimPrimaryCommands(JavaPlugin plugin, List<CommandSpec> commands) {
        int claimed = 0;
        int replaced = 0;
        for (CommandSpec spec : commands) {
            ClaimResult result = claimPrimaryCommand(plugin, spec.name());
            if (result.claimed()) {
                claimed++;
                replaced += result.replacedEntries();
            }
        }
        return new ClaimSummary(claimed, replaced);
    }

    public static ClaimResult claimPrimaryCommand(JavaPlugin plugin, String commandName) {
        try {
            Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            if (knownCommandsField == null) {
                plugin.getLogger().warning("Could not claim /" + commandName + ": knownCommands field missing");
                return new ClaimResult(commandName, false, 0);
            }
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            String normalized = commandName.toLowerCase(Locale.ROOT);

            PluginCommand pluginCommand = findPluginCommand(plugin, commandName, knownCommands);
            if (pluginCommand == null) {
                plugin.getLogger().warning("Skipping claim for /" + normalized
                        + ": no PluginCommand registered for this plugin (plugin.yml mismatch?)");
                return new ClaimResult(normalized, false, 0);
            }

            int removed = 0;
            Map<String, Command> displaced = DISPLACED_COMMANDS.computeIfAbsent(normalized, ignored -> new LinkedHashMap<>());
            for (String key : new ArrayList<>(knownCommands.keySet())) {
                if (key.equals(normalized) || key.endsWith(":" + normalized)) {
                    Command existing = knownCommands.get(key);
                    if (existing != null && !isPluginCommand(existing, plugin)) {
                        displaced.putIfAbsent(key, existing);
                    }
                    knownCommands.remove(key);
                    removed++;
                }
            }
            knownCommands.put(normalized, pluginCommand);
            knownCommands.put(plugin.getName().toLowerCase(Locale.ROOT) + ":" + normalized, pluginCommand);
            syncCommands();
            plugin.getLogger().info("Claimed /" + normalized + " for Bayzyl"
                    + (removed > 0 ? " (replaced " + removed + " existing entr" + (removed == 1 ? "y" : "ies") + ")" : "")
                    + ".");
            return new ClaimResult(normalized, true, removed);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not claim /" + commandName + ": " + ex.getMessage());
            return new ClaimResult(commandName, false, 0);
        }
    }

    public static ReleaseSummary releaseContestedPrimaryCommands(JavaPlugin plugin, List<CommandSpec> commands) {
        try {
            Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            if (knownCommandsField == null) {
                plugin.getLogger().warning("Could not release command authority: knownCommands field missing");
                return new ReleaseSummary(0, 0);
            }
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            String pluginPrefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
            int released = 0;
            int restored = 0;
            for (CommandSpec spec : commands) {
                String normalized = spec.name().toLowerCase(Locale.ROOT);
                Map<String, Command> displaced = DISPLACED_COMMANDS.get(normalized);
                if (displaced == null || displaced.isEmpty()) {
                    continue;
                }

                Command primary = knownCommands.get(normalized);
                if (isPluginCommand(primary, plugin)) {
                    knownCommands.remove(normalized);
                    released++;
                }

                for (Map.Entry<String, Command> entry : displaced.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith(pluginPrefix)) {
                        continue;
                    }
                    knownCommands.put(key, entry.getValue());
                    restored++;
                }
            }
            syncCommands();
            plugin.getLogger().info("Released Bayzyl command authority for " + released
                    + " contested command" + (released == 1 ? "" : "s")
                    + " and restored " + restored + " displaced entr" + (restored == 1 ? "y" : "ies") + ".");
            return new ReleaseSummary(released, restored);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not release command authority: " + ex.getMessage());
            return new ReleaseSummary(0, 0);
        }
    }

    public static int displacedPrimaryCommandCount(List<CommandSpec> commands) {
        int count = 0;
        for (CommandSpec spec : commands) {
            Map<String, Command> displaced = DISPLACED_COMMANDS.get(spec.name().toLowerCase(Locale.ROOT));
            if (displaced != null && displaced.containsKey(spec.name().toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    public static PluginCommand findPluginCommand(JavaPlugin plugin, String commandName) {
        try {
            Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            if (knownCommandsField == null) {
                return plugin.getCommand(commandName);
            }
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            return findPluginCommand(plugin, commandName, knownCommands);
        } catch (ReflectiveOperationException ex) {
            return plugin.getCommand(commandName);
        }
    }

    private static PluginCommand findPluginCommand(JavaPlugin plugin, String commandName, Map<String, Command> knownCommands) {
        // Bukkit uses "<pluginname>:<name>" when another plugin owns the primary label.
        PluginCommand pluginCommand = plugin.getCommand(commandName);
        if (pluginCommand != null) {
            return pluginCommand;
        }

        String normalized = commandName.toLowerCase(Locale.ROOT);
        Command fallback = knownCommands.get(plugin.getName().toLowerCase(Locale.ROOT) + ":" + normalized);
        if (fallback instanceof PluginCommand pc && pc.getPlugin() == plugin) {
            return pc;
        }
        return null;
    }

    private static boolean isPluginCommand(Command command, JavaPlugin plugin) {
        return command instanceof PluginCommand pluginCommand && pluginCommand.getPlugin() == plugin;
    }

    private static Field findField(Class<?> type, String name) {
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

    private static void syncCommands() {
        try {
            Method syncCommands = Bukkit.getServer().getClass().getMethod("syncCommands");
            syncCommands.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // Not fatal. Some server implementations may not expose syncCommands.
        }
    }

    public record ClaimResult(String commandName, boolean claimed, int replacedEntries) {
    }

    public record ClaimSummary(int commandsClaimed, int replacedEntries) {
    }

    public record ReleaseSummary(int commandsReleased, int restoredEntries) {
    }
}
