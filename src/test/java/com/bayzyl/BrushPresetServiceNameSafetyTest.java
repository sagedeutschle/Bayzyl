package com.bayzyl;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class BrushPresetServiceNameSafetyTest {
    @TempDir
    Path tempDir;

    @Test
    void dottedNamesAreRefusedBecauseTheyWouldNestInsideAnotherSavedBrushPath() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        BrushPresetService presets = new BrushPresetService(plugin);

        assertEquals("an empty or invalid name", presets.reservedNameOwner("keep.item"));
        assertEquals("an empty or invalid name", presets.reservedNameOwner("a.b"));
        assertNull(presets.reservedNameOwner("keep-item"));
        assertNotNull(presets.reservedNameOwner("give"));
    }
}
