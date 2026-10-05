package com.bayzyl;

import com.bayzyl.detail.DetailBrushService;
import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class BayzylListenerSelectionSafetyTest {
    private final ToolManager tools = mock(ToolManager.class);
    private final SelectionManager selections = mock(SelectionManager.class);
    private final EditService editService = mock(EditService.class);
    private final NudgeSettingsService nudgeSettings = mock(NudgeSettingsService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final Player player = mock(Player.class);
    private final UUID id = UUID.randomUUID();
    private final BayzylListener listener = new BayzylListener(
            tools, selections, mock(VisualizationManager.class), mock(EraserService.class),
            mock(ClipboardManager.class), mock(EditHistory.class), history, editService,
            mock(NightVisionService.class), mock(AutoUnstickService.class), mock(GhostHandService.class),
            mock(StackLookDirectionService.class), mock(StackAutoMoveService.class),
            mock(MovementAssistService.class), mock(TerrainBrushService.class),
            mock(ProceduralGenerationService.class), mock(DetailBrushService.class),
            mock(ShapeService.class), mock(SelectionService.class), nudgeSettings,
            new AdminModeService(), new BayzylAccess(), new CommandAccessPolicy(),
            mock(RuntimePreferencesService.class), mock(TabMenuSettingsService.class),
            mock(TabInfoPanelService.class), mock(RecentEditTrailService.class),
            mock(DecoyPlayerCountService.class), mock(DecoyTabListService.class),
            mock(KitMenuService.class), mock(BrushMenuService.class));

    BayzylListenerSelectionSafetyTest() {
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        when(nudgeSettings.get(player)).thenReturn(NudgeSettings.defaults());
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
    }

    @Test
    void wandScrollNudgeRefusesASelectionThatNeedsConfirmation() {
        World world = mock(World.class);
        Selection huge = new Selection(new Location(world, 0, 0, 0), new Location(world, 5000, 200, 5000), SelectionType.CUBOID);
        ItemStack wand = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(inventory.getItem(0)).thenReturn(wand);
        when(tools.getToolType(wand)).thenReturn(ToolType.WAND);
        when(selections.get(id)).thenReturn(huge);
        when(editService.requiresConfirm(any(), anyBoolean())).thenReturn(true);
        PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getPreviousSlot()).thenReturn(0);
        when(event.getNewSlot()).thenReturn(1);

        listener.onItemHeld(event);

        verify(event).setCancelled(true);
        verify(editService, never()).nudgeSelection(any(), any(), anyInt(), any());
    }

    @Test
    void quitForgetsJumpIntentAndStructurePlaceTimestamps() throws Exception {
        Map<UUID, Long> jump = map("recentJumpIntent");
        Map<UUID, Long> structure = map("recentStructurePlace");
        jump.put(id, 1L);
        structure.put(id, 1L);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onQuit(event);

        var ordered = org.mockito.Mockito.inOrder(editService, history);
        ordered.verify(editService).cancelPasteTask(id);
        ordered.verify(history).savePlayer(id);

        assertFalse(jump.containsKey(id));
        assertFalse(structure.containsKey(id));
    }

    @Test
    void undoWaitsUntilThePlayersChunkedEditHasFinished() {
        when(editService.hasPasteTask(id)).thenReturn(true);
        when(history.hasUndo(player)).thenReturn(true);
        var event = mock(org.bukkit.event.player.PlayerCommandPreprocessEvent.class);
        when(event.getMessage()).thenReturn("/undo");
        when(event.getPlayer()).thenReturn(player);
        listener.onCommandPreprocess(event);
        verify(event).setCancelled(true);
        verify(history, never()).undo(any(), anyInt());
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> map(String name) throws Exception {
        Field field = BayzylListener.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(listener);
    }
}
