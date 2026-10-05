package com.bayzyl;

import com.bayzyl.detail.DetailBrushService;
import com.bayzyl.detail.DetailBrushMode;
import com.bayzyl.detail.DetailBrushParameters;
import com.bayzyl.detail.DetailBrushPresetRegistry;
import com.bayzyl.detail.DetailBrushSafety;
import com.bayzyl.detail.DetailBrushSettings;
import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class RemainingBrushExecutionSafetyTest {
    private static void assertHistoryUnchanged(HistoryService history) {
        org.junit.jupiter.api.Assertions.assertTrue(org.mockito.Mockito.mockingDetails(history).getInvocations()
                .stream().allMatch(call -> call.getMethod().getName().equals("hasAsyncHistoryTask")));
    }

    @Test
    void eraserHardRefusalCancelsBeforePermissionSelectionOrTargetResolution() {
        Fixture fixture = fixture(ToolType.ERASER);
        when(fixture.tools.readEraserSettings(fixture.item)).thenReturn(
                new EraserSettings(50, BlockMask.parse(null), false, false, false, false));

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        verifyNoInteractions(fixture.eraserService, fixture.selectionManager);
        assertHistoryUnchanged(fixture.historyService);
    }

    @Test
    void terrainHardRefusalCancelsBeforeTargetOrService() {
        Fixture fixture = fixture(ToolType.TERRAIN_BRUSH);
        when(fixture.tools.readTerrainBrushSettings(fixture.item)).thenReturn(
                new TerrainBrushSettings(TerrainBrushType.SMOOTH, 9, 1, false));

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        verifyNoInteractions(fixture.terrainBrushService);
        assertHistoryUnchanged(fixture.historyService);
    }

    @Test
    void paintNonfiniteRefusalCancelsBeforeTargetWorldOrHistory() {
        Fixture fixture = fixture(ToolType.PAINT_BRUSH);
        when(fixture.tools.readPaintBrushSettings(fixture.item)).thenReturn(
                new PaintBrushSettings(Material.AIR, 1, Double.NaN, null));

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        assertHistoryUnchanged(fixture.historyService);
    }

    @Test
    void volumeUnsafePatternCancelsBeforeTargetWorldOrHistory() {
        Fixture fixture = fixture(ToolType.PATTERN_BRUSH);
        when(fixture.tools.readPatternBrushSettings(fixture.item)).thenReturn(
                new PatternBrushSettings(PatternBrushMode.SPATTER, null, Material.AIR,
                        List.of(), 64, 1.0, null));

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        assertHistoryUnchanged(fixture.historyService);
    }

    @Test
    void oversizedRestoreHistoryRefusesBeforeTargetAndChangeListAllocation() {
        Fixture fixture = fixture(ToolType.PATTERN_BRUSH);
        PatternBrushSettings settings = new PatternBrushSettings(
                PatternBrushMode.RESTORE, null, null, List.of(), 64, 1.0, null);
        EditAction action = mock(EditAction.class);
        @SuppressWarnings("unchecked")
        List<BlockChange> changes = mock(List.class);
        when(changes.size()).thenReturn(1_000_001);
        when(action.getChanges()).thenReturn(changes);
        when(fixture.historyService.peekUndo(fixture.player.getUniqueId())).thenReturn(action);
        when(fixture.tools.readPatternBrushSettings(fixture.item)).thenReturn(settings);

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        verify(fixture.historyService, never()).record(any(), any(List.class), any(List.class));
    }

    @Test
    void invalidDetailSettingsCancelBeforePresetLookupCooldownOrTargetResolution() {
        Fixture fixture = fixture(ToolType.DETAIL_BRUSH);
        when(fixture.tools.readDetailBrushSettings(fixture.item)).thenReturn(
                new DetailBrushSettings("flame",
                        new DetailBrushParameters(Map.of("heat", "NaN")), DetailBrushMode.STROKE));

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        verify(fixture.detailBrushService, never()).registry();
        verify(fixture.detailBrushService, never()).apply(any(), any(), any());
    }

    @Test
    void malformedRecognizedDetailItemCancelsWithFeedbackBeforeSafetyOrCooldown() {
        Fixture fixture = fixture(ToolType.DETAIL_BRUSH);

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        verifyNoInteractions(fixture.detailBrushService);
    }

    @Test
    void ghostEnabledMalformedDetailNeverReadsClickedBlockBeforeStrictReader() {
        Fixture fixture = fixture(ToolType.DETAIL_BRUSH);
        when(fixture.ghostHandService.isEnabled(fixture.player)).thenReturn(true);
        when(fixture.event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(fixture.event.getClickedBlock()).thenReturn(fixture.clickedBlock);

        fixture.listener.onInteract(fixture.event);

        assertCancelledBeforeTarget(fixture);
        verify(fixture.clickedBlock, never()).getType();
        verify(fixture.event, never()).setUseInteractedBlock(any());
    }

    @Test
    void ghostHandStillDeniesInteractiveBlocksWhenNoBayzylToolIsHeld() {
        Fixture fixture = fixture(null);
        when(fixture.ghostHandService.isEnabled(fixture.player)).thenReturn(true);
        when(fixture.event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(fixture.event.getClickedBlock()).thenReturn(fixture.clickedBlock);
        Material interactable = mock(Material.class);
        when(interactable.isInteractable()).thenReturn(true);
        when(fixture.clickedBlock.getType()).thenReturn(interactable);

        fixture.listener.onInteract(fixture.event);

        verify(fixture.event).setUseInteractedBlock(Event.Result.DENY);
    }

    @Test
    void directServicesRejectBeforeLocationWorldPlayerOrCollaborators() {
        SelectionManager selections = mock(SelectionManager.class);
        EraserService eraser = new EraserService(selections);
        Player eraserPlayer = mock(Player.class);
        Location eraserCenter = mock(Location.class);
        eraser.apply(eraserPlayer, eraserCenter,
                new EraserSettings(50, BlockMask.parse(null), false, false, false, false));
        verifyNoInteractions(eraserPlayer, eraserCenter, selections);

        CleanupService cleanup = mock(CleanupService.class);
        NaturalizeService naturalize = mock(NaturalizeService.class);
        TerrainBrushService terrain = new TerrainBrushService(cleanup, naturalize);
        Player terrainPlayer = mock(Player.class);
        Location terrainCenter = mock(Location.class);
        terrain.apply(terrainPlayer, terrainCenter,
                new TerrainBrushSettings(TerrainBrushType.SMOOTH, 9, 1, false));
        verifyNoInteractions(terrainPlayer, terrainCenter, cleanup, naturalize);

        Location naturalizeCenter = mock(Location.class);
        Player naturalizePlayer = mock(Player.class);
        new NaturalizeService().naturalizeBrush(naturalizePlayer, naturalizeCenter,
                Integer.MAX_VALUE, 1, false);
        verifyNoInteractions(naturalizePlayer, naturalizeCenter);

        Location cleanupCenter = mock(Location.class);
        Player cleanupPlayer = mock(Player.class);
        new CleanupService().cleanupBrush(cleanupPlayer, cleanupCenter,
                new TerrainBrushSettings(TerrainBrushType.CLEANUP_FLOATING,
                        Integer.MAX_VALUE, 1, false));
        verifyNoInteractions(cleanupPlayer, cleanupCenter);
    }

    private static void assertCancelledBeforeTarget(Fixture fixture) {
        verify(fixture.event).setCancelled(true);
        verify(fixture.player, never()).getEyeLocation();
        verify(fixture.player, never()).rayTraceBlocks(anyDouble());
        verify(fixture.spigot).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent[].class));
    }

    private static Fixture fixture(ToolType type) {
        ToolManager tools = mock(ToolManager.class);
        SelectionManager selectionManager = mock(SelectionManager.class);
        EraserService eraserService = mock(EraserService.class);
        TerrainBrushService terrainBrushService = mock(TerrainBrushService.class);
        GhostHandService ghostHandService = mock(GhostHandService.class);
        DetailBrushService detailBrushService = mock(DetailBrushService.class);
        DetailBrushPresetRegistry detailRegistry = new DetailBrushPresetRegistry();
        when(detailBrushService.safety()).thenReturn(new DetailBrushSafety(detailRegistry));
        HistoryService historyService = mock(HistoryService.class);
        Player player = mock(Player.class);
        Player.Spigot spigot = mock(Player.Spigot.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        Block clickedBlock = mock(Block.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);

        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.spigot()).thenReturn(spigot);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(tools.getToolType(item)).thenReturn(type);

        BayzylListener listener = new BayzylListener(
                tools, selectionManager, mock(VisualizationManager.class), eraserService,
                mock(ClipboardManager.class), mock(EditHistory.class), historyService, mock(EditService.class),
                mock(NightVisionService.class), mock(AutoUnstickService.class), ghostHandService,
                mock(StackLookDirectionService.class), mock(StackAutoMoveService.class),
                mock(MovementAssistService.class), terrainBrushService,
                mock(ProceduralGenerationService.class), detailBrushService,
                mock(ShapeService.class), mock(SelectionService.class), mock(NudgeSettingsService.class),
                mock(AdminModeService.class), new BayzylAccess(), new CommandAccessPolicy(),
                mock(RuntimePreferencesService.class), mock(TabMenuSettingsService.class),
                mock(TabInfoPanelService.class), mock(RecentEditTrailService.class),
                mock(DecoyPlayerCountService.class), mock(DecoyTabListService.class),
                mock(KitMenuService.class), mock(BrushMenuService.class)
        );
        return new Fixture(listener, tools, selectionManager, eraserService, terrainBrushService,
                ghostHandService, detailBrushService, historyService, player, spigot, item, clickedBlock, event);
    }

    private record Fixture(
            BayzylListener listener,
            ToolManager tools,
            SelectionManager selectionManager,
            EraserService eraserService,
            TerrainBrushService terrainBrushService,
            GhostHandService ghostHandService,
            DetailBrushService detailBrushService,
            HistoryService historyService,
            Player player,
            Player.Spigot spigot,
            ItemStack item,
            Block clickedBlock,
            PlayerInteractEvent event
    ) {
    }
}
