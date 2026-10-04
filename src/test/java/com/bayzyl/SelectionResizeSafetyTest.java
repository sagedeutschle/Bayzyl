package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

final class SelectionResizeSafetyTest {
    private final SelectionService service = new SelectionService(new SelectionManager());

    private static Selection cube(int x1, int y1, int z1, int x2, int y2, int z2) {
        World world = mock(World.class);
        return new Selection(new Location(world, x1, y1, z1), new Location(world, x2, y2, z2), SelectionType.CUBOID);
    }

    @Test
    void hugeExpandAllSaturatesInsteadOfWrappingToTheOtherSide() {
        Selection grown = service.expandSelectionAll(cube(0, 0, 0, 10, 10, 10), Integer.MAX_VALUE);

        assertEquals(-Integer.MAX_VALUE, grown.getMinX());
        assertEquals(Integer.MAX_VALUE, grown.getMaxX());
        assertTrue(grown.getVolume() > 0);
    }

    @Test
    void hugeDirectionalExpandKeepsTheOriginalCornerInside() {
        Selection grown = service.expandSelection(cube(0, 0, 0, 10, 10, 10), Integer.MAX_VALUE, new int[]{1, 0, 0});

        assertEquals(0, grown.getMinX());
        assertEquals(Integer.MAX_VALUE, grown.getMaxX());
    }

    @Test
    void hugeContractCollapsesWithoutInverting() {
        Selection shrunk = service.contractSelection(cube(0, 0, 0, 10, 10, 10), Integer.MAX_VALUE, new int[]{-1, 0, 0});

        assertEquals(10, shrunk.getMinX());
        assertEquals(10, shrunk.getMaxX());
    }

    @Test
    void unknownResizeDirectionIsRejectedInsteadOfSilentlyMeaningForward() {
        assertThrows(IllegalArgumentException.class,
                () -> SelectionCommandParser.parseResize("expand", new String[]{"5", "upp"}));
        assertEquals("up", SelectionCommandParser.parseResize("expand", new String[]{"5", "UP"}).direction());
        assertEquals("north-east", SelectionCommandParser.parseResize("expand", new String[]{"5", "dir:north-east"}).direction());
        assertEquals("forward", SelectionCommandParser.parseResize("expand", new String[]{"5"}).direction());
    }
}
