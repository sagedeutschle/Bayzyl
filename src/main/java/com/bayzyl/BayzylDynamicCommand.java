package com.bayzyl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;

public final class BayzylDynamicCommand extends Command implements PluginIdentifiableCommand {
    private final Plugin plugin;
    private final TabExecutor handler;

    public BayzylDynamicCommand(Plugin plugin, String name, String description, String usage, TabExecutor handler) {
        super(name, description, usage, List.of());
        this.plugin = plugin;
        this.handler = handler;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return handler.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> suggestions = handler.onTabComplete(sender, this, alias, args);
        return suggestions == null ? Collections.emptyList() : suggestions;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }
}
