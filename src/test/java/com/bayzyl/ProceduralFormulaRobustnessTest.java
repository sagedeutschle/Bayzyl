package com.bayzyl;

import com.bayzyl.generation.CoordinateMode;
import com.bayzyl.generation.GenerateShapeRequest;
import com.bayzyl.generation.GeneratorResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Regression: unknown variables/functions and wrong arities only fail while the first block is evaluated, which
// happened outside the compile try/catch, so /generate threw out of the command instead of reporting the error.
final class ProceduralFormulaRobustnessTest {
    private final World world = mock(World.class);
    private final HistoryService history = mock(HistoryService.class);

    private GeneratorResult generate(String expression, Location pos2) {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        Location feet = new Location(world, 0, 0, 0);
        org.bukkit.block.Block feetBlock = mock(org.bukkit.block.Block.class);
        when(feetBlock.getLocation()).thenReturn(feet);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(feetBlock);
        when(player.getLocation()).thenReturn(feet);
        SelectionManager selections = new SelectionManager();
        selections.setCuboid(playerId, new Location(world, 0, 0, 0), pos2);
        ProceduralGenerationService service = new ProceduralGenerationService(
                history, selections, new ClipboardManager(), mock(Plugin.class));
        return service.generateShape(player,
                new GenerateShapeRequest(Material.STONE, expression, CoordinateMode.RAW, false, true));
    }

    @Test
    void unknownVariableIsReportedAsAnInvalidExpression() {
        GeneratorResult result = generate("w + 1", new Location(world, 3, 3, 3));

        assertFalse(result.success());
        assertTrue(result.message().startsWith("Invalid expression"), result.message());
        verifyNoInteractions(history);
    }

    @Test
    void unknownFunctionAndWrongArityAreReportedAsInvalidExpressions() {
        assertTrue(generate("frob(x)", new Location(world, 3, 3, 3)).message().startsWith("Invalid expression"));
        assertTrue(generate("sqrt(x, y)", new Location(world, 3, 3, 3)).message().startsWith("Invalid expression"));
    }

    @Test
    void expressionWorkBudgetRefusesAHugeVolumeTimesLongFormulaBeforeEvaluating() {
        // ~1,000,000 blocks x a few hundred nodes is hundreds of millions of evaluations on the main thread.
        String formula = "x" + "+x".repeat(400);

        GeneratorResult result = generate(formula, new Location(world, 99, 99, 99));

        assertFalse(result.success());
        assertTrue(result.message().contains("too expensive"), result.message());
        verifyNoInteractions(history);
    }
}
