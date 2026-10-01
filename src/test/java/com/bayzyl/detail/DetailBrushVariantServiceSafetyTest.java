package com.bayzyl.detail;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class DetailBrushVariantServiceSafetyTest {
    @TempDir
    Path tempDir;

    @Test
    void loadIsTypeStrictDefaultsOnlyMissingModeAndNeverRewritesInvalidStorage() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("variants.legacy.preset", "flame");
        yaml.set("variants.bad-preset.preset", 7);
        yaml.set("variants.bad-mode.preset", "flame");
        yaml.set("variants.bad-mode.mode", "future");
        yaml.set("variants.typed-mode.preset", "flame");
        yaml.set("variants.typed-mode.mode", 7);
        yaml.set("variants.scalar-params.preset", "flame");
        yaml.set("variants.scalar-params.params", "heat=0.7");
        yaml.set("variants.numeric-param.preset", "flame");
        yaml.set("variants.numeric-param.params.heat", 1);
        yaml.set("variants.duplicate.preset", "flame");
        yaml.set("variants.duplicate.params.Heat", "0.5");
        yaml.set("variants.duplicate.params.HEAT", "0.6");
        yaml.set("variants.unknown-string.preset", "cloud");
        yaml.set("variants.unknown-string.params.tint", "invisible");
        yaml.set("variants.spaced-root.preset", "vine");
        yaml.set("variants.spaced-root.params.length", "64");
        yaml.set("variants.spaced-root.params.species", "  Mangrove_Root  ");
        File file = save(yaml);
        String before = Files.readString(file.toPath());

        DetailBrushVariantService variants = service();
        DetailBrushSettings legacy = variants.load("legacy");
        assertNotNull(legacy);
        assertEquals(DetailBrushMode.STAMP, legacy.mode());
        DetailBrushSettings spacedRoot = variants.load("spaced-root");
        assertNotNull(spacedRoot);
        assertEquals("mangrove_root", spacedRoot.parameters().get("species", ""));
        DetailBrushSafety safety = new DetailBrushSafety(new DetailBrushPresetRegistry());
        assertEquals(684L, safety.assess(spacedRoot).workUnits());
        for (String invalid : new String[]{
                "bad-preset", "bad-mode", "typed-mode", "scalar-params",
                "numeric-param", "duplicate", "unknown-string"
        }) {
            assertNull(variants.load(invalid), invalid);
        }
        assertEquals(before, Files.readString(file.toPath()));
    }

    @Test
    void invalidSaveReturnsFalseWithoutClearingExistingVariantOrTouchingFile() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("variants.keep.preset", "flame");
        yaml.set("variants.keep.mode", "STROKE");
        yaml.set("variants.keep.params.heat", "0.5");
        File file = save(yaml);
        String before = Files.readString(file.toPath());

        DetailBrushVariantService variants = service();
        DetailBrushSettings invalid = new DetailBrushSettings(
                "flame", new DetailBrushParameters(Map.of("heat", "NaN")), DetailBrushMode.STAMP);
        assertFalse(variants.save("keep", invalid));
        assertEquals(before, Files.readString(file.toPath()));
        assertNotNull(variants.load("keep"));
        assertEquals(DetailBrushMode.STROKE, variants.load("keep").mode());
    }

    @Test
    void validSavePersistsCanonicalPresetAndParameterValues() throws Exception {
        DetailBrushVariantService variants = service();
        DetailBrushSettings spaced = new DetailBrushSettings(
                "VINE", new DetailBrushParameters(Map.of("species", "  Mangrove_Root  ")),
                DetailBrushMode.STROKE);

        assertEquals(true, variants.save("canonical", spaced));

        YamlConfiguration stored = YamlConfiguration.loadConfiguration(
                tempDir.resolve("detail-brushes.yml").toFile());
        assertEquals("vine", stored.getString("variants.canonical.preset"));
        assertEquals("mangrove_root", stored.getString("variants.canonical.params.species"));
    }

    private DetailBrushVariantService service() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
        return new DetailBrushVariantService(plugin, new DetailBrushSafety(registry));
    }

    private File save(YamlConfiguration yaml) throws Exception {
        File file = tempDir.resolve("detail-brushes.yml").toFile();
        yaml.save(file);
        return file;
    }
}
