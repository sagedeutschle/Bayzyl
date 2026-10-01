package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import com.bayzyl.edit.EditAdapter;
import com.bayzyl.edit.EditAdapterResolver;
import com.bayzyl.shape.ShapeAdapter;
import com.bayzyl.shape.ShapeAdapterResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Bayzyl extends JavaPlugin {
    private SelectionManager selectionManager;
    private VisualizationManager visualizationManager;
    private ToolManager toolManager;
    private EraserService eraserService;
    private ClipboardManager clipboardManager;
    private EditHistory editHistory;
    private EditService editService;
    private HistoryService historyService;
    private SelectionService selectionService;
    private ShapeService shapeService;
    private ProceduralGenerationService proceduralGenerationService;
    private NightVisionService nightVisionService;
    private AutoUnstickService autoUnstickService;
    private GhostHandService ghostHandService;
    private StackLookDirectionService stackLookDirectionService;
    private StackAutoMoveService stackAutoMoveService;
    private MovementAssistService movementAssistService;
    private TerrainBrushService terrainBrushService;
    private NaturalizeService naturalizeService;
    private CleanupService cleanupService;
    private PaletteService paletteService;
    private SchematicService schematicService;
    private BrushPresetService brushPresetService;
    private BuilderProfileService builderProfileService;
    private BuilderKitService builderKitService;
    private KitShortcutRegistry kitShortcutRegistry;
    private DetailBrushShortcutRegistry detailBrushShortcutRegistry;
    private KitMenuService kitMenuService;
    private BrushMenuService brushMenuService;
    private NudgeSettingsService nudgeSettingsService;
    private AdminModeService adminModeService;
    private BayzylAccess bayzylAccess;
    private CommandAccessPolicy commandAccessPolicy;
    private RamAlertService ramAlertService;
    private HelpContentService helpContentService;
    private ListMenuConfigService listMenuConfigService;
    private RuntimePreferencesService runtimePreferencesService;
    private SelectionBookmarkService selectionBookmarkService;
    private PersistentEditHistoryService persistentEditHistoryService;
    private TabMenuSettingsService tabMenuSettingsService;
    private TabInfoPanelService tabInfoPanelService;
    private RecentEditTrailService recentEditTrailService;
    private DecoyPlayerCountService decoyPlayerCountService;
    private DecoyTabListService decoyTabListService;
    private MessageThemeService messageThemeService;
    private CommandAuthorityService commandAuthorityService;
    private com.bayzyl.detail.DetailBrushService detailBrushService;
    private com.bayzyl.detail.DetailBrushVariantService detailBrushVariantService;
    private com.bayzyl.gen.GenBrushService genBrushService;
    private CrashRecoveryService crashRecoveryService;
    private com.bayzyl.redstone.RedstoneAuditMarkerService redstoneAuditMarkers;
    private GlobalMaskService globalMaskService;

    @Override
    public void onEnable() {
        getLogger().info("Bayzyl enabled — local test target verified at 2026-05-12");

        selectionManager = new SelectionManager();
        visualizationManager = new VisualizationManager(selectionManager);
        com.bayzyl.detail.DetailBrushPresetRegistry detailRegistry =
                new com.bayzyl.detail.DetailBrushPresetRegistry();
        com.bayzyl.detail.DetailBrushSafety detailSafety =
                new com.bayzyl.detail.DetailBrushSafety(detailRegistry);
        toolManager = new ToolManager(this, detailSafety);
        eraserService = new EraserService(selectionManager);
        clipboardManager = new ClipboardManager();
        editHistory = new EditHistory();
        persistentEditHistoryService = new PersistentEditHistoryService(this);
        globalMaskService = new GlobalMaskService();
        historyService = new HistoryService(editHistory, selectionManager, persistentEditHistoryService, this);
        historyService.setGlobalMaskService(globalMaskService);
        selectionService = new SelectionService(selectionManager);
        nightVisionService = new NightVisionService();
        autoUnstickService = new AutoUnstickService();
        ghostHandService = new GhostHandService();
        stackLookDirectionService = new StackLookDirectionService();
        stackAutoMoveService = new StackAutoMoveService();
        movementAssistService = new MovementAssistService();
        cleanupService = new CleanupService();
        paletteService = new PaletteService();
        schematicService = new SchematicService(this);
        brushPresetService = new BrushPresetService(this);
        naturalizeService = new NaturalizeService();
        terrainBrushService = new TerrainBrushService(cleanupService, naturalizeService);
        nudgeSettingsService = new NudgeSettingsService();
        adminModeService = new AdminModeService();
        bayzylAccess = new BayzylAccess();
        commandAccessPolicy = new CommandAccessPolicy();
        messageThemeService = new MessageThemeService();
        helpContentService = new HelpContentService(this, messageThemeService);
        listMenuConfigService = new ListMenuConfigService(this, messageThemeService);
        ramAlertService = new RamAlertService(this, listMenuConfigService, messageThemeService);
        runtimePreferencesService = new RuntimePreferencesService(this);
        commandAuthorityService = new CommandAuthorityService(this);
        selectionBookmarkService = new SelectionBookmarkService(this);
        tabMenuSettingsService = new TabMenuSettingsService();
        recentEditTrailService = new RecentEditTrailService();
        decoyPlayerCountService = new DecoyPlayerCountService();
        decoyTabListService = new DecoyTabListService(this, decoyPlayerCountService);
        tabInfoPanelService = new TabInfoPanelService(this, ramAlertService, tabMenuSettingsService, decoyPlayerCountService, clipboardManager, selectionManager, recentEditTrailService);
        // Crash recovery system (must be before services that depend on it)
        crashRecoveryService = openCrashRecovery(() -> new CrashRecoveryService(this), getLogger());
        builderProfileService = new BuilderProfileService(this, visualizationManager, nightVisionService, autoUnstickService, ghostHandService, stackLookDirectionService, nudgeSettingsService, tabMenuSettingsService, recentEditTrailService, tabInfoPanelService, messageThemeService, toolManager);
        builderKitService = new BuilderKitService(this);
        kitShortcutRegistry = new KitShortcutRegistry(this, builderKitService);
        kitMenuService = new KitMenuService(this, builderKitService, listMenuConfigService);
        runtimePreferencesService.loadGlobal(ramAlertService, messageThemeService, commandAuthorityService);
        ShapeAdapter shapeAdapter = ShapeAdapterResolver.resolve(this);
        shapeService = new ShapeService(historyService, shapeAdapter, selectionManager, this);
        proceduralGenerationService = new ProceduralGenerationService(historyService, selectionManager, clipboardManager, this);
        EditAdapter editAdapter = EditAdapterResolver.resolve(this);
        editService = new EditService(this, editAdapter, clipboardManager, historyService, selectionManager);
        editService.setAdminModeService(adminModeService);
        
        // Connect crash recovery to services
        clipboardManager.setCrashRecoveryService(crashRecoveryService);
        editService.setCrashRecoveryService(crashRecoveryService);
        
        // Check for crash recovery
        if (crashRecoveryService != null && crashRecoveryService.migratedLegacyFile()) {
            getLogger().info("Crash recovery data was migrated from the 0.1 format; interrupted sessions will be offered on player join.");
        } else if (crashRecoveryService != null && crashRecoveryService.wasCrashDetected()) {
            getLogger().warning("Crash detected! Bayzyl will attempt to recover sessions on player join.");
        }

        detailBrushService = new com.bayzyl.detail.DetailBrushService(detailRegistry, detailSafety, historyService);
        detailBrushVariantService = new com.bayzyl.detail.DetailBrushVariantService(this, detailSafety);
        detailBrushShortcutRegistry = new DetailBrushShortcutRegistry(this, detailRegistry, detailBrushVariantService);
        brushMenuService = new BrushMenuService(this, brushPresetService, detailBrushService, detailBrushVariantService, messageThemeService);

        com.bayzyl.gen.GenBrushRegistry genRegistry = new com.bayzyl.gen.GenBrushRegistry();
        genBrushService = new com.bayzyl.gen.GenBrushService(genRegistry, historyService, this);

        BayzylCommand handler = new BayzylCommand(
                this,
                toolManager,
                eraserService,
                visualizationManager,
                selectionManager,
                clipboardManager,
                historyService,
                editService,
                selectionService,
                shapeService,
                proceduralGenerationService,
                nightVisionService,
                autoUnstickService,
                ghostHandService,
                stackLookDirectionService,
                stackAutoMoveService,
                movementAssistService,
                cleanupService,
                naturalizeService,
                paletteService,
                schematicService,
                brushPresetService,
                detailBrushService,
                detailBrushVariantService,
                builderProfileService,
                builderKitService,
                kitShortcutRegistry,
                detailBrushShortcutRegistry,
                kitMenuService,
                brushMenuService,
                nudgeSettingsService,
                adminModeService,
                bayzylAccess,
                commandAccessPolicy,
                ramAlertService,
                helpContentService,
                listMenuConfigService,
                runtimePreferencesService,
                selectionBookmarkService,
                tabMenuSettingsService,
                tabInfoPanelService,
                recentEditTrailService,
                decoyPlayerCountService,
                decoyTabListService,
                messageThemeService,
                commandAuthorityService,
                crashRecoveryService
        );
        handler.setGenBrushService(genBrushService);
        redstoneAuditMarkers = new com.bayzyl.redstone.RedstoneAuditMarkerService((tick, period) -> {
            org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, tick, period, period);
            return task::cancel;
        }, com.bayzyl.redstone.RedstoneAuditMarkerService.dustParticles());
        com.bayzyl.redstone.RedstoneAuditCommand redstoneAudit = new com.bayzyl.redstone.RedstoneAuditCommand(
                com.bayzyl.redstone.RedstoneAuditService.standard(),
                new RedstoneAuditSelectionCapture(selectionManager),
                redstoneAuditMarkers);
        handler.setRedstoneAuditCommand(redstoneAudit);
        Bukkit.getPluginManager().registerEvents(new com.bayzyl.redstone.RedstoneAuditListener(redstoneAudit), this);
        handler.setGlobalMaskService(globalMaskService);
        for (CommandSpec spec : CommandRegistry.getAllCommands()) {
            PluginCommand pluginCommand = CommandOverrideService.findPluginCommand(this, spec.name());
            if (pluginCommand == null) {
                getLogger().warning("Command not registered in plugin.yml: " + spec.name());
                continue;
            }
            pluginCommand.setExecutor(handler);
            pluginCommand.setTabCompleter(handler);
        }

        kitShortcutRegistry.bind(handler);
        detailBrushShortcutRegistry.bind(handler);

        if (commandAuthorityService.isBayzylPrimaryCommandsEnabled()) {
            commandAuthorityService.claimPrimaryCommands();
        } else {
            getLogger().info("Bayzyl command authority is disabled; contested primary command labels remain with other plugins.");
        }

        BayzylListener listener = new BayzylListener(toolManager, selectionManager, visualizationManager, eraserService, clipboardManager, editHistory, historyService, editService, nightVisionService, autoUnstickService, ghostHandService, stackLookDirectionService, stackAutoMoveService, movementAssistService, terrainBrushService, proceduralGenerationService, detailBrushService, shapeService, selectionService, nudgeSettingsService, adminModeService, bayzylAccess, commandAccessPolicy, runtimePreferencesService, tabMenuSettingsService, tabInfoPanelService, recentEditTrailService, decoyPlayerCountService, decoyTabListService, kitMenuService, brushMenuService);
        listener.setGenBrushService(genBrushService);
        Bukkit.getPluginManager().registerEvents(listener, this);
        
        // Add crash recovery listener
        if (crashRecoveryService != null) {
            Bukkit.getPluginManager().registerEvents(new CrashRecoveryListener(crashRecoveryService), this);
        }

        tabInfoPanelService.start();

        // Evict in-memory undo/redo entries older than 20 minutes. Persistent
        // on-disk history is unaffected and remains the source of truth for
        // restored sessions.
        historyService.startTtlEvictionTask(20L * 60L * 1000L);

        // Periodic background compaction: pool duplicate BlockData states
        // across uncompacted undo/redo actions. Cheap walk; significant heap
        // savings on builds with low state diversity (typical pastes).
        historyService.startCompactionTask(5L);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                visualizationManager.render(player);
            }
        }, 10L, 10L);
    }

@Override
    public void onDisable() {
        // Cancel any in-flight chunked paste/cut/copy tasks before saving anything else,
        // so they can't continue running against a half-disabled plugin.
        boolean quiesced = quiesce(List.of(
                () -> { if (editService != null) editService.cancelAllAsyncTasks(); },
                () -> { if (historyService != null) historyService.cancelAllAsyncTasks(); }));
        runtimePreferencesService.saveGlobal(ramAlertService, messageThemeService, commandAuthorityService);
        for (var player : Bukkit.getOnlinePlayers()) {
            historyService.savePlayer(player.getUniqueId());
            runtimePreferencesService.savePlayer(player, nightVisionService, autoUnstickService, ghostHandService, stackLookDirectionService, stackAutoMoveService, nudgeSettingsService, visualizationManager, tabMenuSettingsService, recentEditTrailService);
        }
        ramAlertService.shutdown();
        decoyTabListService.clearAll();
        tabInfoPanelService.stop();
        if (redstoneAuditMarkers != null) {
            redstoneAuditMarkers.clearAll();
        }
        if (crashRecoveryService != null) {
            // CLEAN is written only when in-flight edits stopped cleanly and every recovery write succeeded.
            crashRecoveryService.disable(quiesced);
        }
        getLogger().info("Bayzyl disabled");
    }

    /** Runs every canceller; true only when none of them threw. */
    static boolean quiesce(List<Runnable> cancellers) {
        boolean clean = true;
        for (Runnable canceller : cancellers) {
            try {
                canceller.run();
            } catch (Throwable failure) {
                clean = false;
            }
        }
        return clean;
    }

    /**
     * Opens crash recovery, or returns null with a SEVERE log when its persisted state is unreadable. The files are
     * left untouched for inspection and the rest of Bayzyl keeps working without recovery.
     */
    static CrashRecoveryService openCrashRecovery(Supplier<CrashRecoveryService> factory, Logger logger) {
        try {
            return factory.get();
        } catch (IllegalStateException failure) {
            logger.log(Level.SEVERE, "Crash recovery is disabled for this run: " + failure.getMessage());
            return null;
        }
    }
}
