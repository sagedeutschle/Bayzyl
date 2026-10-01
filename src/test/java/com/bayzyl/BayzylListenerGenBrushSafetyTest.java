package com.bayzyl;

import com.bayzyl.detail.DetailBrushService;
import com.bayzyl.gen.CaveSubtype;
import com.bayzyl.gen.GenBrushParameters;
import com.bayzyl.gen.GenBrushService;
import com.bayzyl.gen.GenBrushSettings;
import com.bayzyl.gen.GenBrushType;
import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class BayzylListenerGenBrushSafetyTest {
    @Test
    void malformedSavedBrushStopsBeforeRayTrace() {
        Fixture fixture = fixture(Action.RIGHT_CLICK_AIR);
        when(fixture.tools.readGenBrushSettings(fixture.item)).thenReturn(null);

        fixture.listener.onInteract(fixture.event);

        verify(fixture.event).setCancelled(true);
        verify(fixture.player, never()).rayTraceBlocks(anyDouble());
        verify(fixture.spigot).sendMessage(
                eq(ChatMessageType.ACTION_BAR), any(BaseComponent[].class));
        verifyNoInteractions(fixture.genBrushService, fixture.adminModeService);
    }

    @Test
    void hardRefusalCannotBeBypassedAndStopsBeforeAdminOrRayTrace() {
        Fixture fixture = fixture(Action.RIGHT_CLICK_AIR);
        GenBrushSettings settings = settings(36, true);
        when(fixture.tools.readGenBrushSettings(fixture.item)).thenReturn(settings);
        when(fixture.adminModeService.isActive(fixture.player, fixture.access)).thenReturn(true);

        fixture.listener.onInteract(fixture.event);

        verify(fixture.player, never()).rayTraceBlocks(anyDouble());
        verify(fixture.adminModeService, never()).isActive(fixture.player, fixture.access);
        verifyNoInteractions(fixture.genBrushService);
    }

    @Test
    void unconfirmedSoftBrushStopsBeforeRayTraceAfterCheckingEffectiveAdminMode() {
        Fixture fixture = fixture(Action.RIGHT_CLICK_AIR);
        GenBrushSettings settings = settings(23, false);
        when(fixture.tools.readGenBrushSettings(fixture.item)).thenReturn(settings);
        when(fixture.adminModeService.isActive(fixture.player, fixture.access)).thenReturn(false);

        fixture.listener.onInteract(fixture.event);

        verify(fixture.adminModeService).isActive(fixture.player, fixture.access);
        verify(fixture.player, never()).rayTraceBlocks(anyDouble());
        verifyNoInteractions(fixture.genBrushService);
    }

    @Test
    void storedConfirmationPassesSoftGateWithoutConsultingAdminMode() {
        Fixture fixture = readyTargetFixture();
        GenBrushSettings settings = settings(23, true);
        when(fixture.tools.readGenBrushSettings(fixture.item)).thenReturn(settings);
        when(fixture.genBrushService.apply(
                fixture.player, fixture.target, settings, true)).thenReturn(
                GenBrushService.ApplyResult.success(0, mock(EnvironmentSample.class)));

        fixture.listener.onInteract(fixture.event);

        verify(fixture.adminModeService, never()).isActive(fixture.player, fixture.access);
        verify(fixture.genBrushService).apply(fixture.player, fixture.target, settings, true);
    }

    @Test
    void effectiveAdminModePassesOnlyTheSoftConfirmationGate() {
        Fixture fixture = readyTargetFixture();
        GenBrushSettings settings = settings(23, false);
        when(fixture.tools.readGenBrushSettings(fixture.item)).thenReturn(settings);
        when(fixture.adminModeService.isActive(fixture.player, fixture.access)).thenReturn(true);
        when(fixture.genBrushService.apply(
                fixture.player, fixture.target, settings, true)).thenReturn(
                GenBrushService.ApplyResult.success(0, mock(EnvironmentSample.class)));

        fixture.listener.onInteract(fixture.event);

        verify(fixture.adminModeService).isActive(fixture.player, fixture.access);
        verify(fixture.genBrushService).apply(fixture.player, fixture.target, settings, true);
    }

    @Test
    void ordinaryBrushDoesNotNeedStoredOrAdminConfirmation() {
        Fixture fixture = readyTargetFixture();
        GenBrushSettings settings = settings(22, false);
        when(fixture.tools.readGenBrushSettings(fixture.item)).thenReturn(settings);
        when(fixture.genBrushService.apply(
                fixture.player, fixture.target, settings, false)).thenReturn(
                GenBrushService.ApplyResult.success(0, mock(EnvironmentSample.class)));

        fixture.listener.onInteract(fixture.event);

        verify(fixture.adminModeService, never()).isActive(fixture.player, fixture.access);
        verify(fixture.genBrushService).apply(fixture.player, fixture.target, settings, false);
    }

    private static Fixture readyTargetFixture() {
        Fixture fixture = fixture(Action.RIGHT_CLICK_BLOCK);
        Block block = mock(Block.class);
        when(fixture.event.getClickedBlock()).thenReturn(block);
        when(block.getLocation()).thenReturn(fixture.target);
        return fixture;
    }

    private static Fixture fixture(Action action) {
        ToolManager tools = mock(ToolManager.class);
        GenBrushService genBrushService = mock(GenBrushService.class);
        AdminModeService adminModeService = mock(AdminModeService.class);
        BayzylAccess access = new BayzylAccess();
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Location target = mock(Location.class);

        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(action);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.spigot()).thenReturn(spigot);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(tools.getToolType(item)).thenReturn(ToolType.GEN_BRUSH);

        BayzylListener listener = new BayzylListener(
                tools, mock(SelectionManager.class), mock(VisualizationManager.class), mock(EraserService.class),
                mock(ClipboardManager.class), mock(EditHistory.class), mock(HistoryService.class), mock(EditService.class),
                mock(NightVisionService.class), mock(AutoUnstickService.class), mock(GhostHandService.class),
                mock(StackLookDirectionService.class), mock(StackAutoMoveService.class),
                mock(MovementAssistService.class), mock(TerrainBrushService.class),
                mock(ProceduralGenerationService.class), mock(DetailBrushService.class),
                mock(ShapeService.class), mock(SelectionService.class), mock(NudgeSettingsService.class),
                adminModeService, access, new CommandAccessPolicy(),
                mock(RuntimePreferencesService.class), mock(TabMenuSettingsService.class),
                mock(TabInfoPanelService.class), mock(RecentEditTrailService.class),
                mock(DecoyPlayerCountService.class), mock(DecoyTabListService.class),
                mock(KitMenuService.class), mock(BrushMenuService.class)
        );
        listener.setGenBrushService(genBrushService);
        return new Fixture(listener, tools, genBrushService, adminModeService,
                access, player, spigot, item, event, target);
    }

    private static GenBrushSettings settings(int radius, boolean confirmed) {
        return new GenBrushSettings(
                GenBrushType.RIDGE,
                CaveSubtype.AUTO,
                radius,
                new GenBrushParameters(Map.of("confirm", Boolean.toString(confirmed))),
                null,
                true,
                1L);
    }

    private record Fixture(
            BayzylListener listener,
            ToolManager tools,
            GenBrushService genBrushService,
            AdminModeService adminModeService,
            BayzylAccess access,
            Player player,
            Player.Spigot spigot,
            ItemStack item,
            PlayerInteractEvent event,
            Location target
    ) {
    }
}
