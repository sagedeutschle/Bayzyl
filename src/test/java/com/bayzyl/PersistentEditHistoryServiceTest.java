package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

final class PersistentEditHistoryServiceTest {
    @TempDir
    File dataFolder;

    @Test
    void saveDuringPluginDisableWritesSynchronouslyInsteadOfThrowing() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        BlockData stone = mock(BlockData.class);
        when(stone.getAsString()).thenReturn("minecraft:stone");
        BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        EditAction action = new EditAction(List.of(new BlockChange(new Location(world, 1, 2, 3), air, stone)));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            // Bukkit throws this (a plain RuntimeException) for tasks registered from onDisable.
            when(scheduler.runTaskAsynchronously(any(JavaPlugin.class), any(Runnable.class)))
                    .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            new PersistentEditHistoryService(plugin).save(UUID.randomUUID(), List.of(action), List.of());
        }

        File file = new File(dataFolder, "edit-history.yml");
        assertTrue(file.isFile());
        assertTrue(Files.readString(file.toPath()).contains("minecraft:stone"));
        assertFalse(new File(dataFolder, "edit-history.yml.tmp").exists());
    }

    @Test
    void oversizeStubEndsTheLoadedChainSoOlderEntriesAreNotAppliedOutOfOrder() throws Exception {
        UUID playerId = UUID.randomUUID();
        YamlConfiguration yaml = new YamlConfiguration();
        String root = "players." + playerId + ".undo.";
        for (int slot : new int[]{0, 2}) {
            yaml.set(root + slot + ".blocks.0.location.world", "world");
            yaml.set(root + slot + ".blocks.0.location.x", slot);
            yaml.set(root + slot + ".blocks.0.location.y", 64);
            yaml.set(root + slot + ".blocks.0.location.z", 0);
            yaml.set(root + slot + ".blocks.0.before", "minecraft:air");
            yaml.set(root + slot + ".blocks.0.after", "minecraft:stone");
        }
        yaml.set(root + "1.tooLargeToPersist", true);
        yaml.set(root + "1.blockCount", 99_999);
        yaml.save(new File(dataFolder, "edit-history.yml"));

        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        World world = mock(World.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData(anyString())).thenReturn(mock(BlockData.class));

            PersistentEditHistoryService.LoadedHistory loaded =
                    new PersistentEditHistoryService(plugin).load(playerId);

            // Slot 2 is older than the stub in slot 1; undoing it would skip the stubbed edit.
            assertEquals(1, loaded.undoActions().size());
            assertEquals(0, loaded.undoActions().get(0).getChanges().get(0).getLocation().getBlockX());
        }
    }
}
