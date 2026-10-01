package com.bayzyl;

import com.bayzyl.detail.DetailBrushMode;
import com.bayzyl.detail.DetailBrushParameters;
import com.bayzyl.detail.DetailBrushPresetRegistry;
import com.bayzyl.detail.DetailBrushSafety;
import com.bayzyl.detail.DetailBrushSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class ToolManagerDetailBrushSafetyTest {
    @Test
    void strictReaderPreservesMissingModeLegacyAndStrokeCompatibility() {
        ToolManager tools = tools();

        DetailBrushSettings legacy = tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", "flame")));
        assertNotNull(legacy);
        assertEquals(DetailBrushMode.STAMP, legacy.mode());

        DetailBrushSettings stroke = tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", "flame",
                "detail_params", "height=12;heat=0.5", "detail_mode", "STROKE")));
        assertNotNull(stroke);
        assertEquals(DetailBrushMode.STROKE, stroke.mode());
        assertEquals("12", stroke.parameters().get("height", ""));
    }

    @Test
    void strictReaderRejectsWrongTypesUnknownPresetAndInvalidMode() {
        ToolManager tools = tools();

        assertNull(tools.readDetailBrushSettings(item(values(
                "tool", ToolType.PAINT_BRUSH.name(), "detail_preset", "flame"))));
        assertNull(tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name()))));
        assertNull(tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", 7))));
        assertNull(tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", "missing"))));
        assertNull(tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", "flame", "detail_mode", 1))));
        assertNull(tools.readDetailBrushSettings(item(values(
                "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", "flame", "detail_mode", "future"))));
    }

    @Test
    void strictReaderRejectsMalformedDuplicateOversizedAndUnsafeParameters() {
        ToolManager tools = tools();
        for (Object raw : new Object[]{
                7,
                "broken",
                "height=8;HEIGHT=9",
                "unknown=1",
                "heat=NaN",
                "height=65",
                "height=8;",
                "x".repeat(DetailBrushSafety.PARAMETER_TEXT_MAX + 1)
        }) {
            assertNull(tools.readDetailBrushSettings(item(values(
                    "tool", ToolType.DETAIL_BRUSH.name(), "detail_preset", "flame",
                    "detail_params", raw))), String.valueOf(raw));
        }
    }

    @Test
    void rejectedCreateAndBindStopBeforeItemOrMetadataMutation() {
        ToolManager tools = tools();
        DetailBrushSettings invalid = new DetailBrushSettings(
                "flame", new DetailBrushParameters(Map.of("heat", "NaN")), DetailBrushMode.STROKE);
        ItemStack held = mock(ItemStack.class);

        assertNull(tools.createDetailBrush(invalid, "bad"));
        assertFalse(tools.bindDetailBrush(held, invalid, "bad", true));
        verifyNoInteractions(held);
    }

    @Test
    void validBindPersistsTheCanonicalSettingsReturnedByCentralSafety() {
        ToolManager tools = tools();
        ItemStack held = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(held.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        DetailBrushSettings spaced = new DetailBrushSettings(
                "VINE", new DetailBrushParameters(Map.of("species", "  Mangrove_Root  ")),
                DetailBrushMode.STROKE);

        assertEquals(true, tools.bindDetailBrush(held, spaced, "root", false));

        verify(data).set(org.mockito.ArgumentMatchers.argThat(key -> key.getKey().equals("detail_preset")),
                org.mockito.ArgumentMatchers.eq(PersistentDataType.STRING),
                org.mockito.ArgumentMatchers.eq("vine"));
        verify(data).set(org.mockito.ArgumentMatchers.argThat(key -> key.getKey().equals("detail_params")),
                org.mockito.ArgumentMatchers.eq(PersistentDataType.STRING),
                org.mockito.ArgumentMatchers.eq("species=mangrove_root"));
    }

    private static ToolManager tools() {
        Bayzyl plugin = mock(Bayzyl.class);
        when(plugin.getName()).thenReturn("Bayzyl");
        DetailBrushPresetRegistry registry = new DetailBrushPresetRegistry();
        return new ToolManager(plugin, new DetailBrushSafety(registry));
    }

    private static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1]);
        }
        return values;
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
