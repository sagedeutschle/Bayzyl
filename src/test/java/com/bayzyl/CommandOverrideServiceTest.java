package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandOverrideServiceTest {
    /** CraftServer exposes syncCommands() beyond the Server interface; the service finds it reflectively. */
    public abstract static class FakeServer implements Server {
        public void syncCommands() {
        }
    }

    @Test
    void claimingManyLabelsRebuildsTheClientCommandTreeOnce() {
        FakeServer server = mock(FakeServer.class);
        SimpleCommandMap commandMap = new SimpleCommandMap(server, new HashMap<>());
        when(server.getCommandMap()).thenReturn(commandMap);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("Bayzyl");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        PluginCommand set = mock(PluginCommand.class);
        PluginCommand copy = mock(PluginCommand.class);
        PluginCommand kit = mock(PluginCommand.class);
        when(set.getPlugin()).thenReturn(plugin);
        when(copy.getPlugin()).thenReturn(plugin);
        when(kit.getPlugin()).thenReturn(plugin);
        when(plugin.getCommand("set")).thenReturn(set);
        when(plugin.getCommand("copy")).thenReturn(copy);
        when(plugin.getCommand("kit")).thenReturn(kit);
        Command essentialsKit = mock(Command.class);
        commandMap.getKnownCommands().put("kit", essentialsKit);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            CommandOverrideService.ClaimSummary summary = CommandOverrideService.claimPrimaryCommands(plugin, List.of(
                    new CommandSpec("set", "", ""),
                    new CommandSpec("copy", "", ""),
                    new CommandSpec("kit", "", "")));

            assertEquals(3, summary.commandsClaimed());
            assertSame(kit, commandMap.getKnownCommands().get("kit"));
            verify(server, times(1)).syncCommands();
        }
    }
}
