package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import com.bayzyl.generation.GeneratorResult;
import com.bayzyl.generation.VanillaContentRegistry;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BayzylListener implements Listener {
    private final ToolManager toolManager;
    private final SelectionManager selectionManager;
    private final VisualizationManager visualizationManager;
    private final EraserService eraserService;
    private final ClipboardManager clipboardManager;
    private final EditHistory editHistory;
    private final HistoryService historyService;
    private final EditService editService;
    private final NightVisionService nightVisionService;
    private final AutoUnstickService autoUnstickService;
    private final GhostHandService ghostHandService;
    private final StackLookDirectionService stackLookDirectionService;
    private final StackAutoMoveService stackAutoMoveService;
    private final MovementAssistService movementAssistService;
    private final TerrainBrushService terrainBrushService;
    private final ProceduralGenerationService proceduralGenerationService;
    private final com.bayzyl.detail.DetailBrushService detailBrushService;
    private final ShapeService shapeService;
    private final SelectionService selectionService;
    private final NudgeSettingsService nudgeSettingsService;
    private final AdminModeService adminModeService;
    private final BayzylAccess bayzylAccess;
    private final CommandAccessPolicy commandAccessPolicy;
    private final RuntimePreferencesService runtimePreferencesService;
    private final TabMenuSettingsService tabMenuSettingsService;
    private final TabInfoPanelService tabInfoPanelService;
    private final RecentEditTrailService recentEditTrailService;
    private final DecoyPlayerCountService decoyPlayerCountService;
    private final DecoyTabListService decoyTabListService;
    private final KitMenuService kitMenuService;
    private final BrushMenuService brushMenuService;
    private com.bayzyl.gen.GenBrushService genBrushService;
    private final Map<UUID, Long> recentJumpIntent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentAutoUnstick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentDetailPaint = new ConcurrentHashMap<>();
    private final Map<UUID, BrushProgress> terrainBrushProgress = new ConcurrentHashMap<>();
    private final Map<UUID, BrushProgress> detailBrushProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentStructurePlace = new ConcurrentHashMap<>();

    public BayzylListener(ToolManager toolManager,
                          SelectionManager selectionManager,
                          VisualizationManager visualizationManager,
                          EraserService eraserService,
                          ClipboardManager clipboardManager,
                          EditHistory editHistory,
                          HistoryService historyService,
                          EditService editService,
                          NightVisionService nightVisionService,
                          AutoUnstickService autoUnstickService,
                          GhostHandService ghostHandService,
                          StackLookDirectionService stackLookDirectionService,
                          StackAutoMoveService stackAutoMoveService,
                          MovementAssistService movementAssistService,
                          TerrainBrushService terrainBrushService,
                          ProceduralGenerationService proceduralGenerationService,
                          com.bayzyl.detail.DetailBrushService detailBrushService,
                          ShapeService shapeService,
                          SelectionService selectionService,
                          NudgeSettingsService nudgeSettingsService,
                          AdminModeService adminModeService,
                          BayzylAccess bayzylAccess,
                          CommandAccessPolicy commandAccessPolicy,
                          RuntimePreferencesService runtimePreferencesService,
                          TabMenuSettingsService tabMenuSettingsService,
                          TabInfoPanelService tabInfoPanelService,
                          RecentEditTrailService recentEditTrailService,
                          DecoyPlayerCountService decoyPlayerCountService,
                          DecoyTabListService decoyTabListService,
                          KitMenuService kitMenuService,
                          BrushMenuService brushMenuService) {
        this.toolManager = toolManager;
        this.selectionManager = selectionManager;
        this.visualizationManager = visualizationManager;
        this.eraserService = eraserService;
        this.clipboardManager = clipboardManager;
        this.editHistory = editHistory;
        this.historyService = historyService;
        this.editService = editService;
        this.nightVisionService = nightVisionService;
        this.autoUnstickService = autoUnstickService;
        this.ghostHandService = ghostHandService;
        this.stackLookDirectionService = stackLookDirectionService;
        this.stackAutoMoveService = stackAutoMoveService;
        this.movementAssistService = movementAssistService;
        this.terrainBrushService = terrainBrushService;
        this.proceduralGenerationService = proceduralGenerationService;
        this.detailBrushService = detailBrushService;
        this.shapeService = shapeService;
        this.selectionService = selectionService;
        this.nudgeSettingsService = nudgeSettingsService;
        this.adminModeService = adminModeService;
        this.bayzylAccess = bayzylAccess;
        this.commandAccessPolicy = commandAccessPolicy;
        this.runtimePreferencesService = runtimePreferencesService;
        this.tabMenuSettingsService = tabMenuSettingsService;
        this.tabInfoPanelService = tabInfoPanelService;
        this.recentEditTrailService = recentEditTrailService;
        this.decoyPlayerCountService = decoyPlayerCountService;
        this.decoyTabListService = decoyTabListService;
        this.kitMenuService = kitMenuService;
        this.brushMenuService = brushMenuService;
    }

    /**
     * Setter-injection so {@link com.bayzyl.Bayzyl#onEnable()} can wire
     * the gen brush service after construction. Keeping it out of the
     * constructor avoids breaking the existing 26-argument signature.
     */
    public void setGenBrushService(com.bayzyl.gen.GenBrushService genBrushService) {
        this.genBrushService = genBrushService;
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        int decoys = decoyPlayerCountService.decoyCount();
        if (decoys <= 0) {
            return;
        }

        int boostedPlayers = event.getNumPlayers() + decoys;
        if (boostedPlayers > event.getMaxPlayers()) {
            event.setMaxPlayers(boostedPlayers);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ToolType toolType = toolManager.getToolType(item);
        if (toolType == null) {
            if (ghostHandService.isEnabled(player)
                    && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)
                    && event.getClickedBlock() != null
                    && event.getClickedBlock().getType().isInteractable()) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        if (!bayzylAccess.allowed(player, commandAccessPolicy.requiredToolCapability(toolType))) {
            event.setCancelled(true);
            sendActionBar(player, ChatColor.RED + "You do not have permission to use this Bayzyl tool.");
            return;
        }
        if (hasPendingEdit(player)) {
            event.setCancelled(true);
            sendActionBar(player, ChatColor.RED + "Wait for your current edit or undo/redo to finish.");
            return;
        }

        if (toolType == ToolType.WAND) {
            handleWand(event, player);
            return;
        }

        if (toolType == ToolType.ERASER) {
            handleEraser(event, player, item);
            return;
        }

        if (toolType == ToolType.SMOOTH_BRUSH || toolType == ToolType.TERRAIN_BRUSH) {
            handleTerrainBrush(event, player, item);
            return;
        }

        if (toolType == ToolType.SHAPE_BRUSH) {
            handleShapeBrush(event, player, item);
            return;
        }

        if (toolType == ToolType.STRUCTURE_BRUSH) {
            handleStructureBrush(event, player, item);
            return;
        }

        if (toolType == ToolType.CLIPBOARD_BRUSH) {
            handleClipboardBrush(event, player);
            return;
        }

        if (toolType == ToolType.PAINT_BRUSH) {
            handlePaintBrush(event, player, item);
            return;
        }

        if (toolType == ToolType.PATTERN_BRUSH) {
            handlePatternBrush(event, player, item);
            return;
        }

        if (toolType == ToolType.DETAIL_BRUSH) {
            handleDetailBrush(event, player, item);
            return;
        }

        if (toolType == ToolType.GEN_BRUSH) {
            handleGenBrush(event, player, item);
        }
    }

    private void handleGenBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        com.bayzyl.gen.GenBrushSettings settings = toolManager.readGenBrushSettings(item);
        if (settings == null) {
            sendActionBar(player, ChatColor.RED + "Invalid gen brush data. Rebind the brush before use.");
            return;
        }

        WorkEstimate estimate = com.bayzyl.gen.GenBrushSafety.assess(settings);
        if (estimate.hardRejected()) {
            sendActionBar(player, ChatColor.RED + estimate.reason());
            return;
        }
        boolean effectiveConfirmation = com.bayzyl.gen.GenBrushSafety.storedConfirmation(settings);
        if (estimate.confirmationRequired() && !effectiveConfirmation) {
            effectiveConfirmation = adminModeService.isActive(player, bayzylAccess);
        }
        if (!estimate.permits(effectiveConfirmation)) {
            sendActionBar(player, ChatColor.RED + estimate.reason() + " Rebind with confirm:true.");
            return;
        }
        if (genBrushService == null) {
            sendActionBar(player, ChatColor.RED + "Gen brush service unavailable.");
            return;
        }
        Location target = resolveBrushTarget(event, player);
        if (target == null) {
            sendActionBar(player, ChatColor.RED + "Look at a block within range.");
            return;
        }
        com.bayzyl.gen.GenBrushService.ApplyResult result = genBrushService.apply(
                player, target, settings, effectiveConfirmation);
        if (result.cooldown()) {
            return;
        }
        if (!result.success()) {
            sendActionBar(player, ChatColor.RED + result.message());
            return;
        }
        sendActionBar(player, ChatColor.DARK_GREEN + settings.type().displayName() + ChatColor.WHITE
                + " " + result.changedBlocks() + ChatColor.GRAY + " blocks"
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + settings.summary());
    }

    private void handleDetailBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR
                && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        com.bayzyl.detail.DetailBrushSettings settings = toolManager.readDetailBrushSettings(item);
        if (settings == null) {
            sendActionBar(player, ChatColor.RED + "Invalid detail brush data. Rebind the brush before using it.");
            return;
        }
        WorkEstimate safetyEstimate = detailBrushService.safety().assess(settings);
        if (safetyEstimate.hardRejected()) {
            sendActionBar(player, ChatColor.RED + safetyEstimate.reason());
            return;
        }

        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(settings.presetId());
        if (preset == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown detail brush preset: " + settings.presetId());
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(50L, preset.stampCooldownTicks() * 50L);
        Long last = recentDetailPaint.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        recentDetailPaint.put(player.getUniqueId(), now);

        DetailPaintInput input = (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)
                ? DetailPaintInput.EXTEND
                : DetailPaintInput.SURFACE;
        Location target = resolveDetailBrushTarget(player, preset, settings, input, 120.0);
        if (target == null) {
            ChatOutput.send(player, input == DetailPaintInput.EXTEND
                    ? ChatColor.RED + "Look at matching detail from this brush to extend it."
                    : ChatColor.RED + "Look at a block within range.");
            return;
        }

        com.bayzyl.detail.DetailBrushService.ApplyResult result = detailBrushService.apply(player, target, settings);
        if (!result.success()) {
            ChatOutput.send(player, ChatColor.RED + result.message());
            return;
        }
        int total = updateBrushProgress(detailBrushProgress, player.getUniqueId(), detailBrushSignature(settings), result.changedBlocks());
        sendActionBar(player, ChatColor.LIGHT_PURPLE + "Detail: " + ChatColor.WHITE + settings.presetId()
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + total + ChatColor.GRAY + " blocks total"
                + ChatColor.DARK_GRAY + " (" + ChatColor.WHITE + "+" + result.changedBlocks() + ChatColor.DARK_GRAY + " " + input.label() + ")");
    }

    private Location resolveDetailBrushTarget(
            Player player,
            com.bayzyl.detail.DetailBrushPreset preset,
            com.bayzyl.detail.DetailBrushSettings settings,
            DetailPaintInput input,
            double range
    ) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection().normalize();
        Location lastOpen = eye.clone();
        String lastOpenKey = blockKey(lastOpen);
        Block lastMatchingDetail = null;
        double step = 0.25;
        int steps = (int) Math.ceil(range / step);

        for (int i = 1; i <= steps; i++) {
            Location sample = eye.clone().add(direction.clone().multiply(i * step));
            if (sample.getWorld() == null) {
                return null;
            }
            Block block = sample.getBlock();
            boolean matchingDetail = detailBrushService.isMatchingPlacedDetail(block, preset, settings);
            if (block.getType().isAir()) {
                String key = blockKey(sample);
                if (!key.equals(lastOpenKey)) {
                    lastOpen = block.getLocation();
                    lastOpenKey = key;
                }
                if (input == DetailPaintInput.EXTEND && lastMatchingDetail != null) {
                    return block.getLocation();
                }
                continue;
            }
            if (matchingDetail) {
                lastMatchingDetail = block;
                if (input == DetailPaintInput.SURFACE) {
                    String key = blockKey(sample);
                    if (!key.equals(lastOpenKey)) {
                        lastOpen = block.getLocation();
                        lastOpenKey = key;
                    }
                }
                continue;
            }
            if (input == DetailPaintInput.EXTEND) {
                return lastMatchingDetail == null ? null : lastMatchingDetail.getLocation();
            }
            return lastOpen.getBlock().getLocation();
        }
        return input == DetailPaintInput.EXTEND && lastMatchingDetail != null ? lastMatchingDetail.getLocation() : null;
    }

    private String blockKey(Location location) {
        return location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private enum DetailPaintInput {
        SURFACE("painted"),
        EXTEND("extended");

        private final String label;

        DetailPaintInput(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private void handleClipboardBrush(PlayerInteractEvent event, Player player) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            ChatOutput.send(player, ChatColor.RED + "Clipboard is empty. Run /copy or /cut first.");
            return;
        }

        Location target = resolvePasteTarget(event, player);
        if (target == null) {
            ChatOutput.send(player, ChatColor.RED + "Look at a block within range.");
            return;
        }
        int changed = editService.pasteClipboard(player, clipboard, target, 0, false);
        if (changed <= 0) {
            ChatOutput.send(player, ChatColor.YELLOW + "Paste produced 0 changes (target may already match the clipboard).");
        }
    }

    private Location resolvePasteTarget(PlayerInteractEvent event, Player player) {
        if (event.getClickedBlock() != null) {
            org.bukkit.block.BlockFace face = event.getBlockFace();
            return face == null
                    ? event.getClickedBlock().getLocation()
                    : event.getClickedBlock().getRelative(face).getLocation();
        }
        RayTraceResult ray = player.rayTraceBlocks(120.0);
        if (ray != null && ray.getHitBlock() != null) {
            org.bukkit.block.BlockFace face = ray.getHitBlockFace();
            return face == null
                    ? ray.getHitBlock().getLocation()
                    : ray.getHitBlock().getRelative(face).getLocation();
        }
        return null;
    }

    private void handlePaintBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        PaintBrushSettings settings = toolManager.readPaintBrushSettings(item);
        if (settings == null || !com.bayzyl.safety.BrushSafety.isValidPaint(settings)) {
            sendActionBar(player, ChatColor.RED + "Invalid paint brush data. Rebind the brush before use.");
            return;
        }
        WorkEstimate estimate = com.bayzyl.safety.BrushSafety.assessPaint(settings.size());
        if (estimate.hardRejected()) {
            sendActionBar(player, ChatColor.RED + estimate.reason());
            return;
        }
        Location target = resolveBrushTarget(event, player);
        if (target == null) {
            ChatOutput.send(player, ChatColor.RED + "Look at a block within range.");
            return;
        }

        int radius = settings.size();
        BlockMask mask = settings.mask();
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();
        java.util.List<BlockChange> changes = new java.util.ArrayList<>();
        org.bukkit.World world = target.getWorld();
        if (world == null) {
            return;
        }
        int cx = target.getBlockX();
        int cz = target.getBlockZ();
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                if (random.nextDouble() > settings.density()) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int surfaceY = world.getHighestBlockYAt(x, z);
                Block surface = world.getBlockAt(x, surfaceY, z);
                if (mask != null && !mask.matches(surface.getType())) {
                    continue;
                }
                Block above = world.getBlockAt(x, surfaceY + 1, z);
                if (above.getType() != org.bukkit.Material.AIR) {
                    continue;
                }
                org.bukkit.block.data.BlockData before = above.getBlockData().clone();
                above.setType(settings.material(), false);
                changes.add(new BlockChange(above.getLocation(), before, above.getBlockData().clone()));
            }
        }
        if (!changes.isEmpty()) {
            historyService.record(player.getUniqueId(), changes, java.util.Collections.emptyList());
        }
    }

    private void handlePatternBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        PatternBrushSettings settings = toolManager.readPatternBrushSettings(item);
        if (settings == null || !com.bayzyl.safety.BrushSafety.isValidPattern(settings)) {
            sendActionBar(player, ChatColor.RED + "Invalid pattern brush data. Rebind the brush before use.");
            return;
        }
        EditAction restoreAction = null;
        long restoreChanges = 0L;
        if (settings.mode() == PatternBrushMode.RESTORE) {
            restoreAction = historyService.peekUndo(player.getUniqueId());
            restoreChanges = restoreAction == null || restoreAction.getChanges() == null
                    ? 0L : restoreAction.getChanges().size();
        }
        WorkEstimate estimate = com.bayzyl.safety.BrushSafety.assessPattern(
                settings.mode(), settings.size(), restoreChanges);
        if (estimate.hardRejected()) {
            sendActionBar(player, ChatColor.RED + estimate.reason());
            return;
        }
        Location target = resolveBrushTarget(event, player);
        if (target == null) {
            ChatOutput.send(player, ChatColor.RED + "Look at a block within range.");
            return;
        }
        org.bukkit.World world = target.getWorld();
        if (world == null) {
            return;
        }

        java.util.List<BlockChange> changes = new java.util.ArrayList<>();
        switch (settings.mode()) {
            case SPATTER, REPLACE, BLEND, NOISE, DECAY -> applyVolumePattern(world, target, settings, changes);
            case SURFACE -> applySurfacePattern(world, target, settings, changes);
            case VEGETATION -> applyVegetationPattern(world, target, settings, changes);
            case RESTORE -> applyRestorePattern(restoreAction, target, settings, changes);
        }
        if (!changes.isEmpty()) {
            historyService.record(player.getUniqueId(), changes, java.util.Collections.emptyList());
            sendActionBar(player, ChatColor.GREEN + settings.mode().displayName() + ChatColor.WHITE + " changed " + changes.size() + " blocks");
        } else {
            sendActionBar(player, ChatColor.YELLOW + settings.mode().displayName() + ChatColor.WHITE + " changed 0 blocks");
        }
    }

    private void applyVolumePattern(org.bukkit.World world, Location target, PatternBrushSettings settings, java.util.List<BlockChange> changes) {
        int radius = settings.size();
        int radiusSq = radius * radius;
        int cx = target.getBlockX();
        int cy = target.getBlockY();
        int cz = target.getBlockZ();
        org.bukkit.Material fallbackMask = world.getBlockAt(cx, cy, cz).getType();
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    if (random.nextDouble() > settings.density()) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    org.bukkit.Material current = block.getType();
                    org.bukkit.Material next = switch (settings.mode()) {
                        case SPATTER -> matchesMask(settings, current, fallbackMask) ? settings.to() : null;
                        case REPLACE -> settings.fromMask() != null && settings.fromMask().matches(current) ? settings.to() : null;
                        case BLEND, NOISE -> matchesMask(settings, current, fallbackMask) ? randomPalette(settings, random) : null;
                        case DECAY -> matchesMask(settings, current) ? decayTarget(settings, current, random) : null;
                        default -> null;
                    };
                    applyBlockChange(block, next, changes);
                }
            }
        }
    }

    private void applySurfacePattern(org.bukkit.World world, Location target, PatternBrushSettings settings, java.util.List<BlockChange> changes) {
        int radius = settings.size();
        int radiusSq = radius * radius;
        int cx = target.getBlockX();
        int cz = target.getBlockZ();
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq || random.nextDouble() > settings.density()) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                Block surface = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
                if (matchesMask(settings, surface.getType())) {
                    applyBlockChange(surface, settings.to(), changes);
                }
            }
        }
    }

    private void applyVegetationPattern(org.bukkit.World world, Location target, PatternBrushSettings settings, java.util.List<BlockChange> changes) {
        int radius = settings.size();
        int radiusSq = radius * radius;
        int cx = target.getBlockX();
        int cz = target.getBlockZ();
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq || random.nextDouble() > settings.density()) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                Block surface = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
                if (!matchesMask(settings, surface.getType()) || !surface.getType().isSolid()) {
                    continue;
                }
                Block above = surface.getRelative(org.bukkit.block.BlockFace.UP);
                if (above.getType() != org.bukkit.Material.AIR) {
                    continue;
                }
                applyBlockChange(above, randomPalette(settings, random), changes);
            }
        }
    }

    private void applyRestorePattern(EditAction action, Location target, PatternBrushSettings settings, java.util.List<BlockChange> changes) {
        if (action == null || action.getChanges().isEmpty()) {
            return;
        }
        int radius = settings.size();
        long radiusSq = (long) radius * radius;
        org.bukkit.World world = target.getWorld();
        for (BlockChange change : action.getChanges()) {
            Location location = change.getLocation();
            if (world == null || location.getWorld() == null || !world.getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            long dx = (long) location.getBlockX() - target.getBlockX();
            long dy = (long) location.getBlockY() - target.getBlockY();
            long dz = (long) location.getBlockZ() - target.getBlockZ();
            if (!withinSquaredRadius(dx, dy, dz, radiusSq)) {
                continue;
            }
            Block block = location.getBlock();
            org.bukkit.block.data.BlockData before = block.getBlockData().clone();
            if (before.matches(change.getBefore())) {
                continue;
            }
            block.setBlockData(change.getBefore(), false);
            changes.add(new BlockChange(block.getLocation(), before, block.getBlockData().clone()));
        }
    }

    private boolean matchesMask(PatternBrushSettings settings, org.bukkit.Material material) {
        return settings.mask() == null || settings.mask().matches(material);
    }

    private boolean matchesMask(PatternBrushSettings settings, org.bukkit.Material material, org.bukkit.Material fallbackMask) {
        if (settings.mask() != null) {
            return settings.mask().matches(material);
        }
        return material == fallbackMask;
    }

    private org.bukkit.Material randomPalette(PatternBrushSettings settings, java.util.Random random) {
        if (settings.palette() == null || settings.palette().isEmpty()) {
            return settings.to();
        }
        return settings.palette().get(random.nextInt(settings.palette().size()));
    }

    private org.bukkit.Material decayTarget(PatternBrushSettings settings, org.bukkit.Material current, java.util.Random random) {
        if (settings.to() != null) {
            return settings.to();
        }
        return switch (current) {
            case STONE_BRICKS -> random.nextBoolean() ? org.bukkit.Material.CRACKED_STONE_BRICKS : org.bukkit.Material.MOSSY_STONE_BRICKS;
            case COBBLESTONE -> org.bukkit.Material.MOSSY_COBBLESTONE;
            case STONE -> org.bukkit.Material.COBBLESTONE;
            case DIRT, GRASS_BLOCK, DIRT_PATH -> random.nextBoolean() ? org.bukkit.Material.COARSE_DIRT : org.bukkit.Material.GRAVEL;
            case SANDSTONE -> org.bukkit.Material.CHISELED_SANDSTONE;
            case OAK_PLANKS -> org.bukkit.Material.STRIPPED_OAK_WOOD;
            case SPRUCE_PLANKS -> org.bukkit.Material.STRIPPED_SPRUCE_WOOD;
            default -> null;
        };
    }

    private void applyBlockChange(Block block, org.bukkit.Material material, java.util.List<BlockChange> changes) {
        if (block == null || material == null || block.getType() == material) {
            return;
        }
        org.bukkit.block.data.BlockData before = block.getBlockData().clone();
        block.setType(material, false);
        if (!before.matches(block.getBlockData())) {
            changes.add(new BlockChange(block.getLocation(), before, block.getBlockData().clone()));
        }
    }

    private Location resolveBrushTarget(PlayerInteractEvent event, Player player) {
        if (event.getClickedBlock() != null) {
            return event.getClickedBlock().getLocation();
        }
        RayTraceResult ray = player.rayTraceBlocks(64.0);
        if (ray != null && ray.getHitBlock() != null) {
            return ray.getHitBlock().getLocation();
        }
        return null;
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack previousItem = player.getInventory().getItem(event.getPreviousSlot());
        ToolType previousToolType = toolManager.getToolType(previousItem);
        if (previousToolType == ToolType.WAND && player.isSneaking()) {
            if (hasPendingEdit(player)) {
                event.setCancelled(true);
                sendActionBar(player, ChatColor.RED + "Wait for your current edit or undo/redo to finish.");
                return;
            }
            Selection selection = selectionManager.get(player.getUniqueId());
            if (selection != null && selection.isComplete()) {
                int delta = normalizeHotbarDelta(event.getPreviousSlot(), event.getNewSlot());
                if (delta != 0) {
                    if (!bayzylAccess.allowed(player, commandAccessPolicy.requiredWandScrollCapability())) {
                        event.setCancelled(true);
                        sendActionBar(player, ChatColor.RED + "You do not have permission to nudge selections.");
                        return;
                    }
                    if (editService.requiresConfirm(selection, false)) {
                        event.setCancelled(true);
                        sendActionBar(player, ChatColor.RED + "Selection is too large for wand scrolling. Use /nudge with confirm.");
                        return;
                    }
                    NudgeSettings settings = nudgeSettingsService.get(player);
                    int[] baseDirection = resolveNudgeDirection(player, settings);
                    if (baseDirection == null) {
                        return;
                    }
                    int directionMultiplier = settings.inverted() ? -1 : 1;
                    if (delta < 0) {
                        directionMultiplier *= -1;
                    }
                    int[] moveDirection = new int[]{
                            baseDirection[0] * directionMultiplier,
                            baseDirection[1] * directionMultiplier,
                            baseDirection[2] * directionMultiplier
                    };
                    int changed = editService.nudgeSelection(player, selection, settings.step(), moveDirection);
                    event.setCancelled(true);
                    sendActionBar(player, ChatColor.GOLD + "Selection nudged "
                            + formatDirection(moveDirection, settings.step())
                            + ChatColor.WHITE + " (" + changed + " blocks)");
                    return;
                }
            }
        }

        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        ToolType toolType = toolManager.getToolType(item);
        if (toolType == null) {
            return;
        }

        if (toolType == ToolType.WAND) {
            sendActionBar(player, ChatColor.GOLD + "Bayzyl Wand" + ChatColor.WHITE + " - L:pos1 R:pos2");
            return;
        }

        if (toolType == ToolType.ERASER) {
            EraserSettings settings = toolManager.readEraserSettings(item);
            if (settings == null) {
                return;
            }
            sendEraserActionBar(player, settings);
            return;
        }

        if (toolType == ToolType.SMOOTH_BRUSH || toolType == ToolType.TERRAIN_BRUSH) {
            TerrainBrushSettings settings = toolManager.readTerrainBrushSettings(item);
            if (settings == null) {
                return;
            }
            sendActionBar(player, ChatColor.AQUA + settings.type().displayName() + ChatColor.WHITE + " r:" + settings.radius()
                    + " " + settings.type().powerLabel().toLowerCase() + ":" + settings.power()
                    + " bedrock:" + (settings.editBedrock() ? "on" : "safe"));
            return;
        }

        if (toolType == ToolType.SHAPE_BRUSH) {
            ShapeBrushSettings settings = toolManager.readShapeBrushSettings(item);
            if (settings == null) {
                return;
            }
            sendShapeBrushActionBar(player, settings);
            return;
        }

        if (toolType == ToolType.STRUCTURE_BRUSH) {
            StructureBrushSettings settings = toolManager.readStructureBrushSettings(item);
            if (settings == null) {
                return;
            }
            sendStructureBrushActionBar(player, settings, 0);
            return;
        }

        if (toolType == ToolType.PATTERN_BRUSH) {
            PatternBrushSettings settings = toolManager.readPatternBrushSettings(item);
            if (settings == null) {
                return;
            }
            sendActionBar(player, ChatColor.GREEN + settings.mode().displayName() + " Brush" + ChatColor.WHITE
                    + " r:" + settings.size()
                    + " density:" + String.format(java.util.Locale.ROOT, "%.2f", settings.density())
                    + " mask:" + (settings.mask() == null ? "any" : settings.mask().summary()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        adminModeService.beginSession(player.getUniqueId());
        historyService.loadPlayer(player.getUniqueId());
        runtimePreferencesService.loadPlayer(player, nightVisionService, autoUnstickService, ghostHandService, stackLookDirectionService, stackAutoMoveService, nudgeSettingsService, visualizationManager, tabMenuSettingsService, recentEditTrailService);
        nightVisionService.applyIfEnabled(player);
        tabInfoPanelService.refreshPlayer(player);
        decoyTabListService.refreshViewer(player);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (autoUnstickService.isEnabled(player)
                && movementAssistService.shouldUnstick(player)
                && shouldAutoUnstick(player)) {
            if (movementAssistService.unstick(player)) {
                recentAutoUnstick.put(player.getUniqueId(), System.currentTimeMillis());
            }
            return;
        }
        if (!player.isSneaking()) {
            return;
        }
        if (event.getTo().getY() - event.getFrom().getY() > 0.15) {
            recentJumpIntent.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        editService.cancelPasteTask(player.getUniqueId());
        historyService.savePlayer(player.getUniqueId());
        runtimePreferencesService.savePlayer(player, nightVisionService, autoUnstickService, ghostHandService, stackLookDirectionService, stackAutoMoveService, nudgeSettingsService, visualizationManager, tabMenuSettingsService, recentEditTrailService);
        adminModeService.disable(player);
        var id = player.getUniqueId();
        selectionManager.clear(id);
        visualizationManager.clear(id);
        clipboardManager.set(id, null);
        editHistory.clear(id);
        editService.clearNudgeSession(id);
        tabMenuSettingsService.clear(id);
        recentEditTrailService.clear(id);
        tabInfoPanelService.clearPlayer(id);
        recentJumpIntent.remove(id);
        recentStructurePlace.remove(id);
        recentAutoUnstick.remove(id);
        recentDetailPaint.remove(id);
        terrainBrushProgress.remove(id);
        detailBrushProgress.remove(id);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (kitMenuService.handleClick(event)) {
            return;
        }
        brushMenuService.handleClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (kitMenuService.handleDrag(event)) {
            return;
        }
        brushMenuService.handleDrag(event);
    }

    @EventHandler(ignoreCancelled = false)
    public void onAsyncChat(AsyncChatEvent event) {
        brushMenuService.handleChat(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String command = parts[0].toLowerCase();
        if (command.contains(":")) {
            return;
        }

        Player player = event.getPlayer();
        if (command.equals("undo")) {
            if (refusePendingHistory(event, player)) return;
            editService.clearNudgeSession(player.getUniqueId());
            if (!historyService.hasUndo(player)) {
                return;
            }
            event.setCancelled(true);
            int undone = historyService.undo(player, parseSteps(parts));
            recentEditTrailService.record(player.getUniqueId(), "undo " + undone);
            ChatOutput.send(player, ChatColor.WHITE + "Undid " + undone + " action(s).");
            return;
        }

        if (command.equals("redo")) {
            if (refusePendingHistory(event, player)) return;
            editService.clearNudgeSession(player.getUniqueId());
            if (!historyService.hasRedo(player)) {
                return;
            }
            event.setCancelled(true);
            int redone = historyService.redo(player, parseSteps(parts));
            recentEditTrailService.record(player.getUniqueId(), "redo " + redone);
            ChatOutput.send(player, ChatColor.WHITE + "Redid " + redone + " action(s).");
            return;
        }

        if (!(command.equals("nudge")
                || command.equals("ramalert")
                || (command.equals("bzl") && parts.length > 1
                && (parts[1].equalsIgnoreCase("nudge") || parts[1].equalsIgnoreCase("ramalert"))))) {
            editService.clearNudgeSession(player.getUniqueId());
        }
    }

    private boolean hasPendingEdit(Player player) {
        return editService.hasPasteTask(player.getUniqueId())
                || historyService.hasAsyncHistoryTask(player.getUniqueId());
    }

    private boolean refusePendingHistory(PlayerCommandPreprocessEvent event, Player player) {
        if (!hasPendingEdit(player)) return false;
        event.setCancelled(true);
        ChatOutput.send(player, ChatColor.RED + "Wait for your current edit or undo/redo to finish.");
        return true;
    }

    private void handleWand(PlayerInteractEvent event, Player player) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionManager.setPos1(player.getUniqueId(), block.getLocation());
            ChatOutput.send(player, ChatColor.WHITE + "Pos1 set to " + format(block.getLocation()));
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionManager.setPos2(player.getUniqueId(), block.getLocation());
            ChatOutput.send(player, ChatColor.WHITE + "Pos2 set to " + format(block.getLocation()));
            event.setCancelled(true);
        }
    }

    private void handleEraser(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);
        EraserSettings settings = toolManager.readEraserSettings(item);
        if (settings == null || !com.bayzyl.safety.BrushSafety.isValidEraser(settings)) {
            sendActionBar(player, ChatColor.RED + "Invalid eraser data. Rebind the eraser before use.");
            return;
        }

        WorkEstimate estimate = com.bayzyl.safety.BrushSafety.assessEraser(settings.getRadius());
        if (estimate.hardRejected()) {
            sendActionBar(player, ChatColor.RED + estimate.reason());
            return;
        }

        int maxRadius = eraserService.getMaxRadius(player);
        if (settings.getRadius() > maxRadius) {
            sendActionBar(player, ChatColor.RED + "Eraser radius exceeds your limit (" + maxRadius + ").");
            return;
        }

        if (settings.isSelectionOnly()) {
            Selection selection = selectionManager.get(player.getUniqueId());
            if (selection == null || !selection.isComplete()) {
                sendActionBar(player, ChatColor.RED + "Selection-only erase requires a complete selection.");
                return;
            }
        }

        if (settings.getRadius() >= EraserService.CONFIRM_RADIUS && !player.isSneaking()) {
            sendActionBar(player, ChatColor.RED + "Large eraser radius. Sneak-click to confirm.");
            return;
        }

        Location target = resolveTarget(event.getClickedBlock(), player);
        if (target == null) {
            return;
        }

        EraserService.EraserResult result = eraserService.apply(player, target, settings);
        if (!result.blockChanges().isEmpty() || !result.entityChanges().isEmpty()) {
            historyService.record(player.getUniqueId(), result.blockChanges(), result.entityChanges());
        }
        sendEraserActionBar(player, settings);
    }

    private void handleTerrainBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);
        TerrainBrushSettings settings = toolManager.readTerrainBrushSettings(item);
        if (settings == null || !com.bayzyl.safety.BrushSafety.isValidTerrain(settings)) {
            sendActionBar(player, ChatColor.RED + "Invalid terrain brush data. Rebind the brush before use.");
            return;
        }

        WorkEstimate estimate = com.bayzyl.safety.BrushSafety.assessTerrain(
                settings.type(), settings.radius(), settings.power());
        if (estimate.hardRejected()) {
            sendActionBar(player, ChatColor.RED + estimate.reason());
            return;
        }

        Location target = resolveTarget(event.getClickedBlock(), player);
        if (target == null) {
            return;
        }

        List<BlockChange> changes = terrainBrushService.apply(player, target, settings);
        historyService.record(player.getUniqueId(), changes);
        int total = updateBrushProgress(terrainBrushProgress, player.getUniqueId(), settings.summary(), changes.size());
        sendActionBar(player, ChatColor.AQUA + settings.type().displayName() + ChatColor.WHITE + " "
                + total + ChatColor.GRAY + " blocks total"
                + ChatColor.DARK_GRAY + " (" + ChatColor.WHITE + "+" + changes.size() + ChatColor.DARK_GRAY + ")"
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "r:" + settings.radius()
                + ChatColor.DARK_GRAY + " " + settings.type().powerLabel().toLowerCase() + ":" + settings.power()
                + ChatColor.DARK_GRAY + " bedrock:" + (settings.editBedrock() ? "on" : "safe"));
    }

    private void handleShapeBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ShapeBrushSettings settings = toolManager.readShapeBrushSettings(item);
        if (settings == null) {
            event.setCancelled(true);
            ChatOutput.send(player, ChatColor.RED + "This shape brush is invalid or exceeds the safe work limit. Rebind it before use.");
            return;
        }

        event.setCancelled(true);
        boolean confirmed = settings.confirm() || adminModeService.isActive(player, bayzylAccess);
        ShapeResult result = applyShapeBrush(player, settings, confirmed);
        if (!result.success()) {
            ChatOutput.send(player, ChatColor.RED + result.message());
        }
    }

    private void handleStructureBrush(PlayerInteractEvent event, Player player, ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        StructureBrushSettings settings = toolManager.readStructureBrushSettings(item);
        if (settings == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = recentStructurePlace.get(player.getUniqueId());
        if (last != null && now - last < 150L) {
            return;
        }
        recentStructurePlace.put(player.getUniqueId(), now);

        VanillaContentRegistry.Entry meta = VanillaContentRegistry.structureMeta(settings.structureId());
        if (!bayzylAccess.allowed(player, com.bayzyl.security.CommandCapability.GENSTRUCTURE)) {
            event.setCancelled(true);
            ChatOutput.send(player, ChatColor.RED + "You do not have permission to place structures.");
            return;
        }
        boolean bypassConfirm = adminModeService.isActive(player, bayzylAccess);
        WorkEstimate estimate = OperationLimits.estimateStructure(meta.footprintRadius(), meta.footprintHeight());
        if (estimate.hardRejected()) {
            event.setCancelled(true);
            ChatOutput.send(player, ChatColor.RED + estimate.reason());
            return;
        }
        if (!estimate.permits(settings.confirm() || bypassConfirm)) {
            event.setCancelled(true);
            ChatOutput.send(player, ChatColor.RED + "Large structure footprint (" + meta.description() + "). Rebind with confirm:true.");
            return;
        }
        WorkEstimate chunks = OperationLimits.estimateStructureChunks(meta.footprintRadius());
        if (chunks.hardRejected()) {
            event.setCancelled(true);
            ChatOutput.send(player, ChatColor.RED + chunks.reason());
            return;
        }

        event.setCancelled(true);
        GeneratorResult result = proceduralGenerationService.generateStructure(player,
                new com.bayzyl.generation.StructureGenRequest(settings.structureId(), settings.anchorMode(), settings.confirm()));
        if (!result.success()) {
            ChatOutput.send(player, ChatColor.RED + result.message());
            return;
        }
        sendStructureBrushActionBar(player, settings, result.changed());
    }

    private ShapeResult applyShapeBrush(Player player, ShapeBrushSettings settings, boolean confirmed) {
        return switch (settings.type()) {
            case SPHERE -> shapeService.createSphere(player, new SphereRequest(
                    settings.distribution(),
                    settings.radiusX(),
                    settings.radiusY(),
                    settings.radiusZ(),
                    false,
                    settings.anchorMode(),
                    1,
                    false,
                    false,
                    settings.confirm(),
                    settings.mask()
            ), confirmed);
            case HSPHERE -> shapeService.createSphere(player, new SphereRequest(
                    settings.distribution(),
                    settings.radiusX(),
                    settings.radiusY(),
                    settings.radiusZ(),
                    true,
                    settings.anchorMode(),
                    1,
                    false,
                    false,
                    settings.confirm(),
                    settings.mask()
            ), confirmed);
            case CYL -> shapeService.createCylinder(player, new CylinderRequest(
                    settings.distribution(),
                    settings.radiusX(),
                    settings.radiusZ(),
                    settings.height(),
                    false,
                    settings.anchorMode(),
                    1,
                    false,
                    false,
                    settings.confirm(),
                    settings.mask()
            ), confirmed);
            case HCYL -> shapeService.createCylinder(player, new CylinderRequest(
                    settings.distribution(),
                    settings.radiusX(),
                    settings.radiusZ(),
                    settings.height(),
                    true,
                    settings.anchorMode(),
                    1,
                    false,
                    false,
                    settings.confirm(),
                    settings.mask()
            ), confirmed);
            case PYRAMID -> shapeService.createPyramid(player, new PyramidRequest(
                    settings.distribution(),
                    settings.size(),
                    false,
                    settings.anchorMode(),
                    false,
                    settings.confirm(),
                    settings.mask()
            ), confirmed);
            case HPYRAMID -> shapeService.createPyramid(player, new PyramidRequest(
                    settings.distribution(),
                    settings.size(),
                    true,
                    settings.anchorMode(),
                    false,
                    settings.confirm(),
                    settings.mask()
            ), confirmed);
        };
    }

    private void sendShapeBrushActionBar(Player player, ShapeBrushSettings settings) {
        // Silent on use.
    }

    private void sendStructureBrushActionBar(Player player, StructureBrushSettings settings) {
        sendActionBar(player, ChatColor.DARK_AQUA + "Structure" + ChatColor.WHITE + " "
                + settings.structureId().substring(settings.structureId().indexOf(':') + 1)
                + ChatColor.GRAY + " @ " + ChatColor.WHITE + settings.anchorMode().name().toLowerCase(java.util.Locale.ROOT)
                + ChatColor.DARK_GRAY + " (ready)");
    }

    private void sendStructureBrushActionBar(Player player, StructureBrushSettings settings, int changedBlocks) {
        sendActionBar(player, ChatColor.DARK_AQUA + "Structure" + ChatColor.WHITE + " "
                + settings.structureId().substring(settings.structureId().indexOf(':') + 1)
                + ChatColor.GRAY + " @ " + ChatColor.WHITE + settings.anchorMode().name().toLowerCase(java.util.Locale.ROOT)
                + ChatColor.DARK_GRAY + " (" + ChatColor.WHITE + changedBlocks + ChatColor.DARK_GRAY + " blocks)");
    }

    private void sendEraserActionBar(Player player, EraserSettings settings) {
        String mode = settings.isCarveOnly() ? "carve" : "full";
        String surface = settings.isSurfaceOnly() ? "surface" : "all";
        String selection = settings.isSelectionOnly() ? "selection" : "any";
        String mask = settings.getMask() == null ? "any" : settings.getMask().summary();
        sendActionBar(player, ChatColor.LIGHT_PURPLE + "Eraser" + ChatColor.WHITE + " r:" + settings.getRadius()
                + " mask:" + mask + " " + mode + " " + surface + " " + selection
                + " bedrock:" + (settings.isEditBedrock() ? "on" : "safe"));
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private int updateBrushProgress(Map<UUID, BrushProgress> progressMap, UUID playerId, String signature, int delta) {
        long now = System.currentTimeMillis();
        BrushProgress current = progressMap.get(playerId);
        int normalizedDelta = Math.max(0, delta);
        if (current == null || !current.signature().equals(signature) || now - current.lastUse() > 2000L) {
            BrushProgress updated = new BrushProgress(signature, normalizedDelta, now);
            progressMap.put(playerId, updated);
            return updated.total();
        }
        BrushProgress updated = new BrushProgress(signature, current.total() + normalizedDelta, now);
        progressMap.put(playerId, updated);
        return updated.total();
    }

    private String detailBrushSignature(com.bayzyl.detail.DetailBrushSettings settings) {
        return settings.presetId() + "|" + settings.mode().name() + "|" + settings.parameters().serialize();
    }

    private boolean withinSquaredRadius(long dx, long dy, long dz, long radiusSquared) {
        try {
            long distanceSquared = Math.addExact(
                    Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dy, dy)),
                    Math.multiplyExact(dz, dz));
            return distanceSquared <= radiusSquared;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private Location resolveTarget(Block clicked, Player player) {
        if (clicked != null) {
            return clicked.getLocation();
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double step = 0.25;
        for (double distance = step; distance <= 120.0; distance += step) {
            Location sample = eye.clone().add(direction.clone().multiply(distance));
            Block block = sample.getBlock();
            if (!block.getType().isAir()) {
                return block.getLocation();
            }
        }
        RayTraceResult result = player.rayTraceBlocks(120);
        return result != null && result.getHitBlock() != null ? result.getHitBlock().getLocation() : null;
    }

    private String format(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private int parseSteps(String[] parts) {
        if (parts.length < 2) {
            return 1;
        }
        String token = parts[1];
        if (token.contains(":")) {
            String[] stepParts = token.split(":", 2);
            if (stepParts.length == 2 && stepParts[0].equalsIgnoreCase("steps")) {
                token = stepParts[1];
            }
        }
        try {
            return Math.max(1, Integer.parseInt(token));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private int[] resolveNudgeDirection(Player player, NudgeSettings settings) {
        return switch (settings.verticalMode()) {
            case LOOK -> {
                int[] lookDirection = DirectionUtil.resolveLookDirection(player);
                if (lookDirection[1] != 0) {
                    yield new int[]{0, lookDirection[1], 0};
                }
                yield lookDirection;
            }
            case JUMP -> isRecentJumpIntent(player) ? new int[]{0, 1, 0} : DirectionUtil.resolveLookDirection(player);
            case OFF -> DirectionUtil.resolveLookDirection(player);
        };
    }

    private record BrushProgress(String signature, int total, long lastUse) {
    }

    private boolean isRecentJumpIntent(Player player) {
        Long timestamp = recentJumpIntent.get(player.getUniqueId());
        return timestamp != null && (System.currentTimeMillis() - timestamp) <= 450L;
    }

    private int normalizeHotbarDelta(int previousSlot, int newSlot) {
        int raw = newSlot - previousSlot;
        if (raw > 4) {
            raw -= 9;
        } else if (raw < -4) {
            raw += 9;
        }
        return raw;
    }

    private boolean shouldAutoUnstick(Player player) {
        Long last = recentAutoUnstick.get(player.getUniqueId());
        return last == null || (System.currentTimeMillis() - last) > 1500L;
    }

    private String formatDirection(int[] direction, int amount) {
        int sign = amount >= 0 ? 1 : -1;
        if (direction[1] != 0) {
            return sign * direction[1] > 0 ? "up" : "down";
        }
        if (direction[0] != 0) {
            return sign * direction[0] > 0 ? "east" : "west";
        }
        return sign * direction[2] > 0 ? "south" : "north";
    }
}
