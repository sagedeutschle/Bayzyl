package com.bayzyl;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// Regression: Bayzyl failed to enable on servers without WorldEdit because constructing
// SchematicService linked WorldEdit classes. WorldEdit is compileOnly, so it is absent from
// the test runtime classpath — exactly the situation on a plain Paper server.
final class SchematicServiceWithoutWorldEditTest {
    @Test
    void testRuntimeHasNoWorldEditClasses() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.sk89q.worldedit.world.block.BlockStateHolder"));
    }

    @Test
    void constructsWithoutWorldEdit() {
        new SchematicService(mock(JavaPlugin.class), () -> false);
    }

    @Test
    void saveExplainsWorldEditRequirementInsteadOfCrashing() {
        SchematicService service = new SchematicService(mock(JavaPlugin.class), () -> false);

        SchematicSaveResult result = service.saveSelection(
                mock(Player.class), null, "castle", "min", false, false, false);

        assertFalse(result.success());
        assertEquals(SchematicService.REQUIRES_WORLDEDIT, result.message());
    }

    @Test
    void loadExplainsWorldEditRequirementInsteadOfCrashing() {
        SchematicService service = new SchematicService(mock(JavaPlugin.class), () -> false);

        SchematicService.LoadResult result = service.loadSchematic(mock(Player.class), "castle");

        assertFalse(result.success());
        assertNull(result.clipboard());
        assertEquals(SchematicService.REQUIRES_WORLDEDIT, result.message());
    }

    @Test
    void listIsEmptyWithoutWorldEdit() {
        SchematicService service = new SchematicService(mock(JavaPlugin.class), () -> false);

        assertTrue(service.listSchematics(null).isEmpty());
    }
}
