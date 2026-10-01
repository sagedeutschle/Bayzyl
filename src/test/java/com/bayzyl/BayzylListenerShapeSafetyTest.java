package com.bayzyl;

import com.bayzyl.detail.DetailBrushService;
import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class BayzylListenerShapeSafetyTest {
    @Test
    void invalidRecognizedShapeBrushClickIsCancelled() {
        Fixture fixture = fixture();
        when(fixture.tools.readShapeBrushSettings(fixture.item)).thenReturn(null);

        fixture.listener.onInteract(fixture.event);

        verify(fixture.event).setCancelled(true);
    }

    @Test
    void shapeBrushClickPreservesStoredConfirmation() {
        Fixture fixture = fixture();
        ShapeBrushSettings settings = new ShapeBrushSettings(
                ShapeBrushType.SPHERE, mock(BlockDistribution.class),
                29, 29, 29, 0, 0, ShapeAnchorMode.TARGET, null, true);
        when(fixture.tools.readShapeBrushSettings(fixture.item)).thenReturn(settings);
        when(fixture.shapes.createSphere(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(ShapeResult.created(1, "created"));

        fixture.listener.onInteract(fixture.event);

        verify(fixture.shapes).createSphere(
                org.mockito.ArgumentMatchers.eq(fixture.player),
                argThat(SphereRequest::confirm),
                org.mockito.ArgumentMatchers.eq(true));
        verify(fixture.event).setCancelled(true);
    }

    private static Fixture fixture() {
        ToolManager tools = mock(ToolManager.class);
        ShapeService shapes = mock(ShapeService.class);
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);

        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.spigot()).thenReturn(spigot);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(tools.getToolType(item)).thenReturn(ToolType.SHAPE_BRUSH);

        BayzylListener listener = new BayzylListener(
                tools, mock(SelectionManager.class), mock(VisualizationManager.class), mock(EraserService.class),
                mock(ClipboardManager.class), mock(EditHistory.class), mock(HistoryService.class), mock(EditService.class),
                mock(NightVisionService.class), mock(AutoUnstickService.class), mock(GhostHandService.class),
                mock(StackLookDirectionService.class), mock(StackAutoMoveService.class),
                mock(MovementAssistService.class), mock(TerrainBrushService.class),
                mock(ProceduralGenerationService.class), mock(DetailBrushService.class),
                shapes, mock(SelectionService.class), mock(NudgeSettingsService.class),
                new AdminModeService(), new BayzylAccess(), new CommandAccessPolicy(),
                mock(RuntimePreferencesService.class), mock(TabMenuSettingsService.class),
                mock(TabInfoPanelService.class), mock(RecentEditTrailService.class),
                mock(DecoyPlayerCountService.class), mock(DecoyTabListService.class),
                mock(KitMenuService.class), mock(BrushMenuService.class)
        );
        return new Fixture(listener, tools, shapes, player, item, event);
    }

    private record Fixture(
            BayzylListener listener,
            ToolManager tools,
            ShapeService shapes,
            Player player,
            ItemStack item,
            PlayerInteractEvent event
    ) {
    }
}
