package com.bayzyl;

import com.bayzyl.safety.WorkEstimate;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class ToolManagerShapeSafetyTest {
    private MockedStatic<BlockDistribution> distributions;

    @BeforeEach
    void stubDistributionParsing() {
        distributions = org.mockito.Mockito.mockStatic(BlockDistribution.class);
        distributions.when(() -> BlockDistribution.parse("stone"))
                .thenReturn(mock(BlockDistribution.class));
    }

    @AfterEach
    void closeDistributionParsing() {
        distributions.close();
    }

    @Test
    void validLegacyShapesWithoutConfirmDecodeAsUnconfirmed() {
        ToolManager tools = tools();

        ShapeBrushSettings sphere = tools.readShapeBrushSettings(shapeItem(values("SPHERE")));
        ShapeBrushSettings cylinder = tools.readShapeBrushSettings(shapeItem(values("CYL")));
        ShapeBrushSettings pyramid = tools.readShapeBrushSettings(shapeItem(values("PYRAMID")));
        ShapeBrushSettings confirmed = tools.readShapeBrushSettings(
                shapeItem(values("SPHERE", "shape_confirm", (byte) 1)));

        assertNotNull(sphere);
        assertNotNull(cylinder);
        assertNotNull(pyramid);
        assertFalse(sphere.confirm());
        assertFalse(cylinder.confirm());
        assertFalse(pyramid.confirm());
        assertTrue(confirmed.confirm());
    }

    @Test
    void invalidActiveDimensionsEnumsConfirmAndHardSizeDecodeInvalid() {
        ToolManager tools = tools();

        assertNull(tools.readShapeBrushSettings(shapeItem(values("SPHERE", "shape_radius_y", 0))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("CYL", "shape_height", 0))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("PYRAMID", "shape_size", 0))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("NOT_A_SHAPE"))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("SPHERE", "shape_anchor", "SIDEWAYS"))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("SPHERE", "shape_confirm", (byte) 2))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("SPHERE", "shape_confirm", "true"))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("SPHERE", "shape_radius_x", "3"))));
        assertNull(tools.readShapeBrushSettings(shapeItem(values("SPHERE", "shape_radius_x", 51,
                "shape_radius_y", 51, "shape_radius_z", 51))));
    }

    @Test
    void unsafeBindAndResizeDoNotWriteItemMetadata() {
        ToolManager tools = tools();
        ItemStack bindTarget = mock(ItemStack.class);
        ShapeBrushSettings hard = new ShapeBrushSettings(
                ShapeBrushType.SPHERE, mock(BlockDistribution.class), 51, 51, 51, 0, 0,
                ShapeAnchorMode.TARGET, null, true);

        assertTrue(tools.estimateShapeBrush(hard).hardRejected());
        assertFalse(tools.bindShapeBrush(bindTarget, hard, true));
        verifyNoInteractions(bindTarget);

        ItemStack resizeTarget = shapeItem(values("SPHERE"));
        ItemMeta resizeMeta = resizeTarget.getItemMeta();
        assertFalse(tools.updateShapeBrushSize(resizeTarget, 51, null));
        assertTrue(mockingDetails(resizeMeta).getInvocations().stream()
                .noneMatch(invocation -> invocation.getMethod().getName().equals("setLore")));
        verify(resizeTarget, never()).setItemMeta(any());
    }

    @Test
    void validResizeWritesUpdatedMetadata() {
        ToolManager tools = tools();
        ItemStack item = shapeItem(values("CYL"));

        assertTrue(tools.updateShapeBrushSize(item, 8, 12));

        verify(item).setItemMeta(any());
    }

    @Test
    void resizeCandidateExposesSoftConfirmationAssessmentBeforeMutation() {
        ToolManager tools = tools();
        ShapeBrushSettings current = new ShapeBrushSettings(
                ShapeBrushType.SPHERE, mock(BlockDistribution.class),
                3, 3, 3, 0, 0, ShapeAnchorMode.TARGET, null, false);

        ShapeBrushSettings candidate = tools.resizedShapeBrush(current, 29, null);
        WorkEstimate estimate = tools.estimateShapeBrush(candidate);

        assertTrue(estimate.confirmationRequired());
        assertFalse(estimate.hardRejected());
    }

    private static ToolManager tools() {
        Bayzyl plugin = mock(Bayzyl.class);
        when(plugin.getName()).thenReturn("Bayzyl");
        var registry = new com.bayzyl.detail.DetailBrushPresetRegistry();
        return new ToolManager(plugin, new com.bayzyl.detail.DetailBrushSafety(registry));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack shapeItem(Map<String, Object> values) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        @SuppressWarnings("unchecked")
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
        values.put("tool", ToolType.SHAPE_BRUSH.name());
        values.put("shape_type", type);
        values.put("shape_material", "stone");
        values.put("shape_radius_x", type.contains("SPHERE") || type.contains("CYL") ? 3 : 0);
        values.put("shape_radius_y", type.contains("SPHERE") ? 4 : 0);
        values.put("shape_radius_z", type.contains("SPHERE") || type.contains("CYL") ? 5 : 0);
        values.put("shape_height", type.contains("CYL") ? 6 : 0);
        values.put("shape_size", type.contains("PYRAMID") ? 7 : 0);
        values.put("shape_anchor", ShapeAnchorMode.TARGET.name());
        for (int i = 0; i < overrides.length; i += 2) {
            values.put((String) overrides[i], overrides[i + 1]);
        }
        return values;
    }
}
