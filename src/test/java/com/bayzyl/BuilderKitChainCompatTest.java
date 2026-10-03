package com.bayzyl;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class BuilderKitChainCompatTest {

    @Test
    void builderKitServiceHasNoCompileTimeReferenceToMaterialChain() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/bayzyl/BuilderKitService.java"));
        assertFalse(source.contains("Material.CHAIN"), "chain must be resolved by name via CompatMaterials");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyDefaultKitMaterialResolvesToANonNullMaterial() throws Exception {
        // ItemStack construction needs a running server, so read the kit tables without constructing the service.
        BuilderKitService service = mock(BuilderKitService.class, CALLS_REAL_METHODS);
        Method definitions = BuilderKitService.class.getDeclaredMethod("defaultKitDefinitions");
        definitions.setAccessible(true);
        List<Object> kits = (List<Object>) definitions.invoke(service);
        assertFalse(kits.isEmpty());

        Material chain = Material.getMaterial("CHAIN");
        assertNotNull(chain, "the 1.21 test API has CHAIN");
        int chainSlots = 0;
        for (Object kit : kits) {
            Method nameAccessor = kit.getClass().getDeclaredMethod("name");
            Method materialsAccessor = kit.getClass().getDeclaredMethod("materials");
            nameAccessor.setAccessible(true);
            materialsAccessor.setAccessible(true);
            String name = (String) nameAccessor.invoke(kit);
            List<Material> materials = (List<Material>) materialsAccessor.invoke(kit);
            assertFalse(materials.isEmpty(), name);
            for (Material material : materials) {
                assertNotNull(material, name);
                if (material == chain) {
                    chainSlots++;
                }
            }
        }
        assertEquals(20, chainSlots, "all 20 chain slots must resolve to the chain block");
    }
}
