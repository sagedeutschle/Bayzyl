package com.bayzyl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuilderKitOverwriteTest {
    // defaults_version 3 means the constructor does not seed the (ItemStack-serializing) default library.
    private static final String SEEDED = """
            meta:
              defaults_version: 3
            kits:
              mine:
                scope: hotbar
              other:
                scope: hotbar
                aliases:
                  - shortcut
            """;

    private BuilderKitService service(Path dir) throws IOException {
        Files.writeString(dir.resolve("kits.yml"), SEEDED);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return new BuilderKitService(plugin);
    }

    private Player emptyHanded() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getName()).thenReturn("tester");
        return player;
    }

    @Test
    void saveWithOverwriteReplacesAnExistingKitAndWithoutItRefuses() throws IOException {
        Path dir = Files.createTempDirectory("kits");
        BuilderKitService service = service(dir);

        assertFalse(service.saveKit(emptyHanded(), "mine", BuilderKitScope.HOTBAR, false));
        assertTrue(service.saveKit(emptyHanded(), "mine", BuilderKitScope.HOTBAR, true));
        // An alias of another kit is still not a label the caller may overwrite.
        assertFalse(service.saveKit(emptyHanded(), "shortcut", BuilderKitScope.HOTBAR, true));
        // Reserved command names stay refused.
        assertFalse(service.saveKit(emptyHanded(), "set", BuilderKitScope.HOTBAR, true));
    }

    @Test
    void duplicateAndRenameWithOverwriteReplaceTheTargetKit() throws IOException {
        Path dir = Files.createTempDirectory("kits");
        BuilderKitService service = service(dir);

        assertFalse(service.duplicateKit("mine", "other", false));
        assertTrue(service.duplicateKit("mine", "other", true));
        assertTrue(service.existsCanonicalKit("mine"));

        assertFalse(service.renameKit("mine", "other", false));
        assertTrue(service.renameKit("mine", "other", true));
        assertFalse(service.existsCanonicalKit("mine"));
        assertTrue(service.existsCanonicalKit("other"));
    }
}
