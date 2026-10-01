package com.bayzyl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

final class ToolManagerRemainingBrushSafetyTest {
    @Test
    void eraserReaderEnforcesExactSchemaFlagsMaskAndCubeCap() {
        ToolManager tools = tools();

        EraserSettings legacy = tools.readEraserSettings(item(values(ToolType.ERASER,
                "radius", 49)));
        assertNotNull(legacy);
        assertEquals(49, legacy.getRadius());
        assertFalse(legacy.isSurfaceOnly());
        assertFalse(legacy.isSelectionOnly());
        assertFalse(legacy.isCarveOnly());
        assertFalse(legacy.isEditBedrock());

        assertNull(tools.readEraserSettings(item(values(ToolType.PAINT_BRUSH, "radius", 1))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", "49"))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 50))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1, "surface_only", (byte) -1))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1, "selection_only", (byte) 2))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1, "carve_only", "true"))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1, "mask", ""))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1, "mask", "not_a_block"))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1,
                "mask", "air,not_a_block"))));
        assertNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1,
                "mask", "x".repeat(com.bayzyl.safety.BrushSafety.MASK_TEXT_MAX + 1)))));
        assertNotNull(tools.readEraserSettings(item(values(ToolType.ERASER, "radius", 1, "mask", "air"))));
    }

    @Test
    void terrainReaderPreservesLegacySmoothAndEnforcesExactActiveSchema() {
        ToolManager tools = tools();

        TerrainBrushSettings legacy = tools.readTerrainBrushSettings(item(values(ToolType.SMOOTH_BRUSH,
                "radius", 8, "iterations", 1)));
        assertNotNull(legacy);
        assertEquals(TerrainBrushType.SMOOTH, legacy.type());
        assertFalse(legacy.editBedrock());

        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.SMOOTH_BRUSH,
                "radius", 9, "iterations", 1))));
        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.ERASER,
                "radius", 1, "iterations", 1))));
        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.TERRAIN_BRUSH,
                "radius", 1, "iterations", 1))));
        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.TERRAIN_BRUSH,
                "brush_type", "NOPE", "radius", 1, "iterations", 1))));
        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.TERRAIN_BRUSH,
                "brush_type", "RAISE", "radius", 0, "iterations", 1))));
        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.TERRAIN_BRUSH,
                "brush_type", "RAISE", "radius", 1, "iterations", 0))));
        assertNull(tools.readTerrainBrushSettings(item(values(ToolType.TERRAIN_BRUSH,
                "brush_type", "RAISE", "radius", 1, "iterations", 1, "edit_bedrock", (byte) 2))));
    }

    @Test
    void paintReaderRejectsNonfiniteOutOfRangeAndMalformedOptionalState() {
        ToolManager tools = tools();
        assertNotNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                "shape_material", "AIR", "paint_size", 64, "paint_density", "1.0"))));

        for (String density : List.of("NaN", "Infinity", "-Infinity", "-0.1", "1.1", "broken")) {
            assertNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                    "shape_material", "AIR", "paint_size", 1, "paint_density", density))));
        }
        assertNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                "shape_material", "NOT_A_BLOCK", "paint_size", 1, "paint_density", "1"))));
        assertNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                "shape_material", "AIR", "paint_size", 65, "paint_density", "1"))));
        assertNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                "shape_material", "AIR", "paint_size", 1, "paint_density", "1", "mask", " "))));
        assertNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                "shape_material", "AIR", "paint_size", 1, "paint_density", "1", "mask", 7))));
        assertNull(tools.readPaintBrushSettings(item(values(ToolType.PAINT_BRUSH,
                "shape_material", "AIR", "paint_size", 1, "paint_density", "1",
                "mask", "air,not_a_block"))));
    }

    @Test
    void patternReaderEnforcesModeSpecificRequiredAndForbiddenFields() {
        ToolManager tools = tools();
        assertNotNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SPATTER,
                "pattern_to", "AIR")));
        assertNotNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.REPLACE,
                "pattern_from", "air", "pattern_to", "AIR")));
        assertNotNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "AIR,AIR,AIR")));
        assertNotNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.RESTORE)));
        assertNotNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.RESTORE,
                "pattern_from", "")));

        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SPATTER)));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SPATTER,
                "pattern_to", "AIR", "pattern_from", "air")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.REPLACE,
                "pattern_from", "", "pattern_to", "AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.REPLACE,
                "pattern_from", "air")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "AIR", "pattern_to", "AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.RESTORE,
                "pattern_palette", "AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.DECAY,
                "pattern_from", "air")));
    }

    @Test
    void patternReaderValidatesFiniteDensityPaletteBoundsAndModeWork() {
        ToolManager tools = tools();
        assertNotNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SURFACE,
                "paint_size", 64, "pattern_to", "AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SPATTER,
                "paint_size", 64, "pattern_to", "AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "AIR,,AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "AIR,".repeat(64) + "AIR")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "x".repeat(129))));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "x".repeat(4_097))));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.BLEND,
                "pattern_palette", "AIR,NOT_A_BLOCK")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SURFACE,
                "pattern_to", "AIR", "mask", "air,not_a_block")));
        assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.REPLACE,
                "pattern_from", "air,not_a_block", "pattern_to", "AIR")));
        for (String density : List.of("NaN", "Infinity", "-Infinity", "-0.01", "1.01")) {
            assertNull(tools.readPatternBrushSettings(pattern(PatternBrushMode.SURFACE,
                    "pattern_to", "AIR", "paint_density", density)));
        }
    }

    @Test
    void everyRejectedBindStopsBeforeMetadataMutation() {
        ToolManager tools = tools();
        ItemStack eraser = mock(ItemStack.class);
        ItemStack terrain = mock(ItemStack.class);
        ItemStack paint = mock(ItemStack.class);
        ItemStack pattern = mock(ItemStack.class);

        assertFalse(tools.bindEraser(eraser,
                new EraserSettings(50, BlockMask.parse(null), false, false, false, false), true));
        assertFalse(tools.bindTerrainBrush(terrain,
                new TerrainBrushSettings(TerrainBrushType.SMOOTH, 9, 1, false), true));
        assertFalse(tools.bindPaintBrush(paint,
                new PaintBrushSettings(Material.AIR, 1, Double.NaN, null), true));
        assertFalse(tools.bindPatternBrush(pattern,
                new PatternBrushSettings(PatternBrushMode.RESTORE, null, Material.AIR,
                        List.of(), 1, 1.0, null), true));

        verifyNoInteractions(eraser, terrain, paint, pattern);
        assertNull(tools.createEraser(
                new EraserSettings(50, BlockMask.parse(null), false, false, false, false)));
        assertNull(tools.createTerrainBrush(TerrainBrushType.SMOOTH, 9, 1, false));
    }

    @Test
    void rejectedUpdatesReadButNeverRewriteExistingMetadataOrLore() {
        ToolManager tools = tools();
        ItemStack eraser = item(values(ToolType.ERASER, "radius", 1));
        ItemStack terrain = item(values(ToolType.SMOOTH_BRUSH, "radius", 8, "iterations", 1));
        ItemStack paint = item(values(ToolType.PAINT_BRUSH,
                "shape_material", "AIR", "paint_size", 1, "paint_density", "1"));
        ItemStack pattern = pattern(PatternBrushMode.SPATTER, "pattern_to", "AIR");
        ItemStack palettePattern = pattern(PatternBrushMode.BLEND, "pattern_palette", "AIR");
        ItemStack restorePattern = pattern(PatternBrushMode.RESTORE);

        assertFalse(tools.updateEraserSize(eraser, 50));
        assertFalse(tools.updateTerrainBrushSize(terrain, 9, 1));
        assertFalse(tools.updatePaintBrushDensity(paint, Double.NaN));
        assertFalse(tools.updatePatternBrushSize(pattern, 64));
        assertFalse(tools.updatePatternBrushMaterial(palettePattern, Material.AIR));
        assertFalse(tools.updatePatternBrushMaterial(restorePattern, Material.AIR));

        for (ItemStack item : List.of(eraser, terrain, paint, pattern, palettePattern, restorePattern)) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            verify(item, never()).setItemMeta(any());
            verify(meta, never()).setLore(any());
            verify(data, never()).set(any(), any(), any());
            verify(data, never()).remove(any());
        }
    }

    private static ToolManager tools() {
        Bayzyl plugin = mock(Bayzyl.class);
        when(plugin.getName()).thenReturn("Bayzyl");
        var registry = new com.bayzyl.detail.DetailBrushPresetRegistry();
        return new ToolManager(plugin, new com.bayzyl.detail.DetailBrushSafety(registry));
    }

    private static ItemStack pattern(PatternBrushMode mode, Object... overrides) {
        Map<String, Object> values = values(ToolType.PATTERN_BRUSH,
                "pattern_mode", mode.name(), "paint_size", 5, "paint_density", "1.0");
        put(values, overrides);
        return item(values);
    }

    private static Map<String, Object> values(ToolType type, Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        values.put("tool", type.name());
        put(values, pairs);
        return values;
    }

    private static void put(Map<String, Object> values, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1]);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack item(Map<String, Object> values) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        Set<NamespacedKey> keys = mock(Set.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(invocation -> {
            NamespacedKey key = invocation.getArgument(0);
            PersistentDataType<?, ?> requested = invocation.getArgument(1);
            Object value = values.get(key.getKey());
            if (requested == PersistentDataType.STRING && value instanceof String) return value;
            if (requested == PersistentDataType.INTEGER && value instanceof Integer) return value;
            if (requested == PersistentDataType.BYTE && value instanceof Byte) return value;
            return null;
        });
        when(data.getKeys()).thenReturn(keys);
        when(keys.contains(any())).thenAnswer(invocation ->
                values.containsKey(((NamespacedKey) invocation.getArgument(0)).getKey()));
        return item;
    }
}
