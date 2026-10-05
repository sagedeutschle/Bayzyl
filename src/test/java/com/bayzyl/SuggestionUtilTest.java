package com.bayzyl;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SuggestionUtilTest {
    /** Material.isBlock/isItem need a registry (a running server), so the real constants are only touched when legacy. */
    private static Material modern(String name) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name);
        when(material.isLegacy()).thenReturn(false);
        when(material.isBlock()).thenReturn(true);
        when(material.isItem()).thenReturn(true);
        return material;
    }

    @Test
    void blockAndItemSuggestionsSkipLegacyMaterials() {
        Material legacy = Material.LEGACY_STONE;
        Material stone = modern("STONE");
        try (MockedStatic<Material> materials = mockStatic(Material.class)) {
            materials.when(Material::values).thenReturn(new Material[]{legacy, stone});

            List<String> blocks = SuggestionUtil.blockSuggestions("");
            assertEquals(List.of("minecraft:stone", "stone"), blocks);
            assertFalse(blocks.stream().anyMatch(value -> value.contains("legacy")), blocks.toString());

            List<String> items = SuggestionUtil.itemSuggestions("");
            assertEquals(List.of("minecraft:stone", "stone"), items);
        }
    }
}
