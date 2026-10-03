package com.bayzyl.compat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompatMaterialsTest {

    @Test
    void prefersTheModernNameWhenBothExist() {
        Material resolved = CompatMaterials.resolveChain(
                name -> Map.of("IRON_CHAIN", Material.IRON_INGOT, "CHAIN", Material.STICK).get(name));
        assertEquals(Material.IRON_INGOT, resolved);
    }

    @Test
    void usesTheLegacyNameWhenOnlyItExists() {
        List<String> asked = new ArrayList<>();
        Material resolved = CompatMaterials.resolveChain(name -> {
            asked.add(name);
            return "CHAIN".equals(name) ? Material.STICK : null;
        });
        assertEquals(Material.STICK, resolved);
        assertEquals(List.of("IRON_CHAIN", "CHAIN"), asked);
    }

    @Test
    void usesTheModernNameWhenOnlyItExists() {
        assertEquals(Material.IRON_INGOT,
                CompatMaterials.resolveChain(name -> "IRON_CHAIN".equals(name) ? Material.IRON_INGOT : null));
    }

    @Test
    void fallsBackToIronBarsWhenNeitherNameExists() {
        assertEquals(Material.IRON_BARS, CompatMaterials.resolveChain(name -> null));
    }

    @Test
    void chainResolvesToANonNullBlockOnTheTestClasspath() {
        Material chain = CompatMaterials.chain();
        assertNotNull(chain);
        // The compile/test API is Paper 1.21, which only has CHAIN.
        assertEquals(Material.getMaterial("CHAIN"), chain);
    }
}
