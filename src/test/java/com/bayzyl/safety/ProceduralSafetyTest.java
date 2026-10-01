package com.bayzyl;

import com.bayzyl.generation.CoordinateMode;
import com.bayzyl.generation.BiomeGeneratorMode;
import com.bayzyl.generation.GenerateBiomeRequest;
import com.bayzyl.generation.GenerateShapeRequest;
import com.bayzyl.generation.GeneratorResult;
import com.bayzyl.generation.ForestGenRequest;
import com.bayzyl.generation.PumpkinPatchRequest;
import com.bayzyl.generation.StructureGenRequest;
import com.bayzyl.generation.ExpressionEngine;
import com.bayzyl.ShapeAnchorMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ProceduralSafetyTest {
    @Test
    void sharedConfirmationGateUses200000AndNeverBypassesHardCap() {
        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), new SelectionManager(), new ClipboardManager(), mock(Plugin.class));

        assertFalse(service.requiresConfirm(200_000L, false));
        assertTrue(service.requiresConfirm(200_001L, false));
        assertFalse(service.requiresConfirm(200_001L, true));
        assertTrue(service.requiresConfirm(1_000_001L, true));
    }

    @Test
    void oversizedFormulaRefusesBeforeExpressionCompilation() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        SelectionManager selections = new SelectionManager();
        selections.setCuboid(playerId,
                new Location(world, 0, 0, 0),
                new Location(world, 100, 99, 100));

        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), selections, new ClipboardManager(), mock(Plugin.class),
                expression -> {
                    throw new AssertionError("expression compiler must not run");
                });

        GeneratorResult result = service.generateShape(player,
                new GenerateShapeRequest(Material.STONE, "1", CoordinateMode.NORMALIZED, false, true));

        assertFalse(result.success());
        assertTrue(result.message().contains("hard maximum"), result.message());
    }

    @Test
    void biomeSelectionAcross4097ChunksRefusesBeforeExpressionCompilation() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        SelectionManager selections = new SelectionManager();
        selections.setCuboid(playerId,
                new Location(world, 0, 64, 0),
                new Location(world, 65_536, 64, 0));

        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), selections, new ClipboardManager(), mock(Plugin.class),
                expression -> {
                    throw new AssertionError("expression compiler must not run");
                });

        GeneratorResult result = service.generateBiome(player,
                new GenerateBiomeRequest(Biome.PLAINS, BiomeGeneratorMode.EXPRESSION, "@",
                        CoordinateMode.NORMALIZED, false, true, true,
                        null, null, null, null, null, null));

        assertFalse(result.success());
        assertTrue(result.message().contains("Biome chunk footprint"), result.message());
    }

    @Test
    void forestRadius33RefusesBeforeAnchorAccessEvenWhenConfirmed() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenThrow(new AssertionError("anchor must not be read"));
        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), new SelectionManager(), new ClipboardManager(), mock(Plugin.class));

        GeneratorResult result = service.generateForest(player,
                new ForestGenRequest(33, TreeType.TREE, 5.0, ShapeAnchorMode.PLAYER, true));

        assertFalse(result.success());
        assertTrue(result.message().contains("hard maximum of 32"), result.message());
    }

    @Test
    void pumpkinRadius65RefusesBeforeAnchorAccessEvenWhenConfirmed() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenThrow(new AssertionError("anchor must not be read"));
        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), new SelectionManager(), new ClipboardManager(), mock(Plugin.class));

        GeneratorResult result = service.generatePumpkins(player,
                new PumpkinPatchRequest(65, ShapeAnchorMode.PLAYER, true));

        assertFalse(result.success());
        assertTrue(result.message().contains("hard maximum of 64"), result.message());
    }

    @Test
    void oversizedStructureRefusesBeforeAnchorChunkOrDispatchAccess() {
        Player player = mock(Player.class);
        when(player.rayTraceBlocks(64)).thenThrow(new AssertionError("anchor must not be read"));
        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), new SelectionManager(), new ClipboardManager(), mock(Plugin.class),
                ExpressionEngine::compile, poisonPlacementBackend());

        GeneratorResult result = service.generateStructure(player,
                new StructureGenRequest("minecraft:stronghold", ShapeAnchorMode.TARGET, true));

        assertFalse(result.success());
        assertTrue(result.message().contains("hard maximum"), result.message());
    }

    @Test
    void oversizedRerollRefusesBeforeChunkLoadOrPriorChangeRestore() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        List<BlockChange> poisonChanges = new AbstractList<>() {
            @Override
            public BlockChange get(int index) {
                throw new AssertionError("prior changes must not be read or restored");
            }

            @Override
            public int size() {
                return 200_000;
            }
        };
        Map<UUID, ProceduralGenerationService.LastStructureGeneration> state = new ConcurrentHashMap<>();
        state.put(playerId, new ProceduralGenerationService.LastStructureGeneration(
                "minecraft:not_a_real_structure", new Location(world, 0, 64, 0), poisonChanges));
        ProceduralGenerationService service = new ProceduralGenerationService(
                mock(HistoryService.class), new SelectionManager(), new ClipboardManager(), mock(Plugin.class),
                ExpressionEngine::compile, poisonPlacementBackend(), state);

        GeneratorResult result = service.regenerateStructure(player);

        assertFalse(result.success());
        assertTrue(result.message().contains("hard maximum"), result.message());
    }

    private static ProceduralGenerationService.VanillaPlacementBackend poisonPlacementBackend() {
        return new ProceduralGenerationService.VanillaPlacementBackend() {
            @Override
            public void loadChunk(World world, int chunkX, int chunkZ) {
                throw new AssertionError("chunk must not load");
            }

            @Override
            public boolean dispatch(World world, String commandPrefix, int x, int y, int z) {
                throw new AssertionError("command must not dispatch");
            }
        };
    }
}
