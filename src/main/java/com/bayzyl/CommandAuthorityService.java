package com.bayzyl;

import org.bukkit.plugin.java.JavaPlugin;

public final class CommandAuthorityService {
    private final JavaPlugin plugin;
    private boolean bayzylPrimaryCommandsEnabled = true;

    public CommandAuthorityService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBayzylPrimaryCommandsEnabled() {
        return bayzylPrimaryCommandsEnabled;
    }

    public void setBayzylPrimaryCommandsEnabled(boolean enabled) {
        this.bayzylPrimaryCommandsEnabled = enabled;
    }

    public CommandOverrideService.ClaimSummary claimPrimaryCommands() {
        bayzylPrimaryCommandsEnabled = true;
        return CommandOverrideService.claimPrimaryCommands(plugin, CommandRegistry.getAllCommands());
    }

    public CommandOverrideService.ReleaseSummary releaseContestedPrimaryCommands() {
        bayzylPrimaryCommandsEnabled = false;
        return CommandOverrideService.releaseContestedPrimaryCommands(plugin, CommandRegistry.getAllCommands());
    }

    public int displacedPrimaryCommandCount() {
        return CommandOverrideService.displacedPrimaryCommandCount(CommandRegistry.getAllCommands());
    }
}
