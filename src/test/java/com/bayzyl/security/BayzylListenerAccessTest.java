package com.bayzyl.security;

import com.bayzyl.AdminModeService;
import com.bayzyl.AutoUnstickService;
import com.bayzyl.BrushMenuService;
import com.bayzyl.ClipboardManager;
import com.bayzyl.DecoyPlayerCountService;
import com.bayzyl.DecoyTabListService;
import com.bayzyl.EditHistory;
import com.bayzyl.EditService;
import com.bayzyl.EraserService;
import com.bayzyl.GhostHandService;
import com.bayzyl.HistoryService;
import com.bayzyl.KitMenuService;
import com.bayzyl.MovementAssistService;
import com.bayzyl.NightVisionService;
import com.bayzyl.NudgeSettingsService;
import com.bayzyl.ProceduralGenerationService;
import com.bayzyl.RecentEditTrailService;
import com.bayzyl.RuntimePreferencesService;
import com.bayzyl.Selection;
import com.bayzyl.SelectionManager;
import com.bayzyl.SelectionService;
import com.bayzyl.ShapeService;
import com.bayzyl.ShapeAnchorMode;
import com.bayzyl.StackAutoMoveService;
import com.bayzyl.StackLookDirectionService;
import com.bayzyl.TabInfoPanelService;
import com.bayzyl.TabMenuSettingsService;
import com.bayzyl.TerrainBrushService;
import com.bayzyl.ToolManager;
import com.bayzyl.ToolType;
import com.bayzyl.StructureBrushSettings;
import com.bayzyl.VisualizationManager;
import com.bayzyl.BayzylListener;
import com.bayzyl.generation.GeneratorResult;
import com.bayzyl.detail.DetailBrushService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class BayzylListenerAccessTest {
    @Test
    void unauthorizedSneakingWandScrollCancelsBeforeNudge() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack wand = mock(ItemStack.class);
        PlayerItemHeldEvent event = mock(PlayerItemHeldEvent.class);
        ToolManager tools = mock(ToolManager.class);
        SelectionManager selections = mock(SelectionManager.class);
        Selection selection = mock(Selection.class);
        EditService edits = mock(EditService.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getPreviousSlot()).thenReturn(0);
        when(event.getNewSlot()).thenReturn(1);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission(any(String.class))).thenReturn(false);
        when(player.spigot()).thenReturn(spigot);
        when(inventory.getItem(0)).thenReturn(wand);
        when(tools.getToolType(wand)).thenReturn(ToolType.WAND);
        when(selections.get(playerId)).thenReturn(selection);
        when(selection.isComplete()).thenReturn(true);

        listener(tools, selections, edits).onItemHeld(event);

        verify(event).setCancelled(true);
        verify(edits, never()).nudgeSelection(any(), any(), anyInt(), any());
    }

    @Test
    void oversizedStructureBrushRefusesBeforeAnchorOrBackendEvenWhenConfirmed() {
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack brush = mock(ItemStack.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        ToolManager tools = mock(ToolManager.class);
        ProceduralGenerationService procedural = mock(ProceduralGenerationService.class);

        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.spigot()).thenReturn(spigot);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(inventory.getItemInMainHand()).thenReturn(brush);
        when(tools.getToolType(brush)).thenReturn(ToolType.STRUCTURE_BRUSH);
        when(tools.readStructureBrushSettings(brush)).thenReturn(
                new StructureBrushSettings("minecraft:stronghold", ShapeAnchorMode.TARGET, true));
        when(procedural.generateStructure(any(), any())).thenReturn(GeneratorResult.success(1, "placed"));

        listener(tools, mock(SelectionManager.class), mock(EditService.class), procedural).onInteract(event);

        verify(event).setCancelled(true);
        verify(player, never()).getLocation();
        verify(player, never()).rayTraceBlocks(64);
        verify(procedural, never()).generateStructure(any(), any());
    }

    private static BayzylListener listener(ToolManager tools,
                                           SelectionManager selections,
                                           EditService edits) {
        return listener(tools, selections, edits, mock(ProceduralGenerationService.class));
    }

    private static BayzylListener listener(ToolManager tools,
                                           SelectionManager selections,
                                           EditService edits,
                                           ProceduralGenerationService procedural) {
        return new BayzylListener(
                tools, selections, mock(VisualizationManager.class), mock(EraserService.class),
                mock(ClipboardManager.class), mock(EditHistory.class), mock(HistoryService.class), edits,
                mock(NightVisionService.class), mock(AutoUnstickService.class), mock(GhostHandService.class),
                mock(StackLookDirectionService.class), mock(StackAutoMoveService.class),
                mock(MovementAssistService.class), mock(TerrainBrushService.class),
                procedural, mock(DetailBrushService.class),
                mock(ShapeService.class), mock(SelectionService.class), mock(NudgeSettingsService.class),
                new AdminModeService(), new BayzylAccess(), new CommandAccessPolicy(),
                mock(RuntimePreferencesService.class), mock(TabMenuSettingsService.class),
                mock(TabInfoPanelService.class), mock(RecentEditTrailService.class),
                mock(DecoyPlayerCountService.class), mock(DecoyTabListService.class),
                mock(KitMenuService.class), mock(BrushMenuService.class)
        );
    }
}
