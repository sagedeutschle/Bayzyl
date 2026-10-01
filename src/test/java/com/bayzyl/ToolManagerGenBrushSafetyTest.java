package com.bayzyl;

import com.bayzyl.gen.CaveSubtype;
import com.bayzyl.gen.GenBrushParameters;
import com.bayzyl.gen.GenBrushSafety;
import com.bayzyl.gen.GenBrushSettings;
import com.bayzyl.gen.GenBrushType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class ToolManagerGenBrushSafetyTest {
    @Test
    void validLegacyPdcUsesOnlyDocumentedMissingFieldDefaults() {
        ToolManager tools = tools();

        GenBrushSettings ridge = tools.readGenBrushSettings(genItem(values("RIDGE")));
        GenBrushSettings cave = tools.readGenBrushSettings(genItem(values("CAVE")));

        assertNotNull(ridge);
        assertEquals(10, ridge.radius());
        assertEquals(CaveSubtype.AUTO, ridge.caveSubtype());
        assertTrue(ridge.parameters().raw().isEmpty());
        assertTrue(ridge.adaptToEnvironment());

        assertNotNull(cave);
        assertEquals(8, cave.radius());
        assertEquals(CaveSubtype.AUTO, cave.caveSubtype());
    }

    @Test
    void presentInvalidPdcFieldsRejectInsteadOfBecomingLegacyDefaults() {
        ToolManager tools = tools();

        assertNull(tools.readGenBrushSettings(genItem(values("NOT_A_GEN_BRUSH"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_cave_subtype", "NOPE"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_cave_subtype", "NOODLE"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", 0))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", "10"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_params", "broken"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_params", "height=0"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_adapt", (byte) 2))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_adapt", "true"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "mask", ""))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "mask", " "))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "mask", "definitely_not_a_block"))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "mask", "x".repeat(1_025)))));
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", 36))));
    }

    @Test
    void pdcRadiusBoundariesMatchCommandAndServiceSafety() {
        ToolManager tools = tools();

        GenBrushSettings radius22 = tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", 22)));
        GenBrushSettings radius23 = tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", 23)));
        GenBrushSettings radius35 = tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", 35)));

        assertNotNull(radius22);
        assertNotNull(radius23);
        assertNotNull(radius35);
        assertFalse(GenBrushSafety.assess(radius22).confirmationRequired());
        assertTrue(GenBrushSafety.assess(radius23).confirmationRequired());
        assertTrue(GenBrushSafety.assess(radius35).confirmationRequired());
        assertNull(tools.readGenBrushSettings(genItem(values("RIDGE", "gen_radius", 36))));
    }

    @Test
    void rejectedBindAndResizeLeaveItemMetadataUntouched() {
        ToolManager tools = tools();
        ItemStack bindTarget = mock(ItemStack.class);
        GenBrushSettings hard = new GenBrushSettings(
                GenBrushType.RIDGE, CaveSubtype.AUTO, 36,
                GenBrushParameters.empty(), null, true, 1L);

        assertFalse(tools.bindGenBrush(bindTarget, hard, true));
        verifyNoInteractions(bindTarget);

        ItemStack resizeTarget = genItem(values("RIDGE", "gen_radius", 10));
        ItemMeta resizeMeta = resizeTarget.getItemMeta();
        PersistentDataContainer resizeData = resizeMeta.getPersistentDataContainer();
        assertFalse(tools.updateGenBrushSize(resizeTarget, 36));
        assertTrue(mockingDetails(resizeMeta).getInvocations().stream()
                .noneMatch(invocation -> invocation.getMethod().getName().startsWith("set")));
        assertTrue(mockingDetails(resizeData).getInvocations().stream()
                .noneMatch(invocation -> invocation.getMethod().getName().equals("set")
                        || invocation.getMethod().getName().equals("remove")));
        verify(resizeTarget, never()).setItemMeta(any());
    }

    private static ToolManager tools() {
        Bayzyl plugin = mock(Bayzyl.class);
        when(plugin.getName()).thenReturn("Bayzyl");
        var registry = new com.bayzyl.detail.DetailBrushPresetRegistry();
        return new ToolManager(plugin, new com.bayzyl.detail.DetailBrushSafety(registry));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack genItem(Map<String, Object> values) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        Set<NamespacedKey> keys = mock(Set.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(invocation -> {
            NamespacedKey key = invocation.getArgument(0);
            PersistentDataType<?, ?> requestedType = invocation.getArgument(1);
            Object value = values.get(key.getKey());
            if (requestedType == PersistentDataType.STRING && value instanceof String) {
                return value;
            }
            if (requestedType == PersistentDataType.INTEGER && value instanceof Integer) {
                return value;
            }
            if (requestedType == PersistentDataType.BYTE && value instanceof Byte) {
                return value;
            }
            if (requestedType == PersistentDataType.LONG && value instanceof Long) {
                return value;
            }
            return null;
        });
        when(data.getKeys()).thenReturn(keys);
        when(keys.contains(any())).thenAnswer(invocation -> {
            NamespacedKey key = invocation.getArgument(0);
            return values.containsKey(key.getKey());
        });
        return item;
    }

    private static Map<String, Object> values(String type, Object... overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("tool", ToolType.GEN_BRUSH.name());
        values.put("gen_type", type);
        for (int i = 0; i < overrides.length; i += 2) {
            values.put((String) overrides[i], overrides[i + 1]);
        }
        return values;
    }
}
