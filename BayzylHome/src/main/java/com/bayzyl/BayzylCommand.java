package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.bayzyl.generation.ForestGenRequest;
import com.bayzyl.generation.GenerateBiomeRequest;
import com.bayzyl.generation.GenerateShapeRequest;
import com.bayzyl.generation.GeneratorCommandParser;
import com.bayzyl.generation.GeneratorResult;
import com.bayzyl.generation.PumpkinPatchRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BayzylCommand implements TabExecutor {
    private static final int NUDGE_HELP_PAGE = 11;
    private static final int PROFILES_HELP_PAGE = 12;
    private static final int KITS_HELP_PAGE = 13;
    private static final int TABMENU_HELP_PAGE = 14;
    private static final String KIT_LIST_MENU = "kit-list";
    private static final String SELECTION_BOOKMARKS_MENU = "selection-bookmarks";
    private static final String PROFILE_LIST_MENU = "profile-list";
    private static final String PROFILE_INSPECT_MENU = "profile-inspect";
    private static final String KIT_INSPECT_MENU = "kit-inspect";
    private static final String KIT_ALIAS_LIST_MENU = "kit-alias-list";
    private static final String PALETTE_ANALYSIS_MENU = "palette-analysis";
    private static final String BIOME_INFO_MENU = "biome-info";
    private static final String TABMENU_STATUS_MENU = "tabmenu-status";
    private static final String RAMALERT_HELP_MENU = "ramalert-help";
    private static final List<String> DEFAULT_KIT_THEME_ORDER = List.of(
            "Masonry & Castle",
            "Dark & Fantasy",
            "Organic & Landscaping",
            "Trees & Timber",
            "Interiors & Workspaces",
            "Towns & Streets",
            "Specialty Palettes",
            "Custom Kits"
    );
    private static final DateTimeFormatter PROFILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withLocale(Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final long KIT_CONFIRM_WINDOW_MILLIS = 5L * 60L * 1000L;

    private final ToolManager toolManager;
    private final EraserService eraserService;
    private final VisualizationManager visualizationManager;
    private final SelectionManager selectionManager;
    private final ClipboardManager clipboardManager;
    private final HistoryService historyService;
    private final EditService editService;
    private final SelectionService selectionService;
    private final ShapeService shapeService;
    private final ProceduralGenerationService proceduralGenerationService;
    private final NightVisionService nightVisionService;
    private final AutoUnstickService autoUnstickService;
    private final GhostHandService ghostHandService;
    private final MovementAssistService movementAssistService;
    private final CleanupService cleanupService;
    private final NaturalizeService naturalizeService;
    private final PaletteService paletteService;
    private final BrushPresetService brushPresetService;
    private final BuilderProfileService builderProfileService;
    private final BuilderKitService builderKitService;
    private final KitShortcutRegistry kitShortcutRegistry;
    private final KitMenuService kitMenuService;
    private final NudgeSettingsService nudgeSettingsService;
    private final AdminModeService adminModeService;
    private final RamAlertService ramAlertService;
    private final HelpContentService helpContentService;
    private final ListMenuConfigService listMenuConfigService;
    private final RuntimePreferencesService runtimePreferencesService;
    private final SelectionBookmarkService selectionBookmarkService;
    private final TabMenuSettingsService tabMenuSettingsService;
    private final TabInfoPanelService tabInfoPanelService;
    private final RecentEditTrailService recentEditTrailService;
    private final DecoyPlayerCountService decoyPlayerCountService;
    private final DecoyTabListService decoyTabListService;
    private final Map<UUID, PendingKitUpdate> pendingKitUpdates = new HashMap<>();

    public BayzylCommand(ToolManager toolManager,
                         EraserService eraserService,
                         VisualizationManager visualizationManager,
                         SelectionManager selectionManager,
                         ClipboardManager clipboardManager,
                         HistoryService historyService,
                         EditService editService,
                         SelectionService selectionService,
                         ShapeService shapeService,
                         ProceduralGenerationService proceduralGenerationService,
                         NightVisionService nightVisionService,
                         AutoUnstickService autoUnstickService,
                         GhostHandService ghostHandService,
                         MovementAssistService movementAssistService,
                         CleanupService cleanupService,
                         NaturalizeService naturalizeService,
                         PaletteService paletteService,
                         BrushPresetService brushPresetService,
                         BuilderProfileService builderProfileService,
                         BuilderKitService builderKitService,
                         KitShortcutRegistry kitShortcutRegistry,
                         KitMenuService kitMenuService,
                         NudgeSettingsService nudgeSettingsService,
                         AdminModeService adminModeService,
                         RamAlertService ramAlertService,
                         HelpContentService helpContentService,
                         ListMenuConfigService listMenuConfigService,
                         RuntimePreferencesService runtimePreferencesService,
                         SelectionBookmarkService selectionBookmarkService,
                         TabMenuSettingsService tabMenuSettingsService,
                         TabInfoPanelService tabInfoPanelService,
                         RecentEditTrailService recentEditTrailService,
                         DecoyPlayerCountService decoyPlayerCountService,
                         DecoyTabListService decoyTabListService) {
        this.toolManager = toolManager;
        this.eraserService = eraserService;
        this.visualizationManager = visualizationManager;
        this.selectionManager = selectionManager;
        this.clipboardManager = clipboardManager;
        this.historyService = historyService;
        this.editService = editService;
        this.selectionService = selectionService;
        this.shapeService = shapeService;
        this.proceduralGenerationService = proceduralGenerationService;
        this.nightVisionService = nightVisionService;
        this.autoUnstickService = autoUnstickService;
        this.ghostHandService = ghostHandService;
        this.movementAssistService = movementAssistService;
        this.cleanupService = cleanupService;
        this.naturalizeService = naturalizeService;
        this.paletteService = paletteService;
        this.brushPresetService = brushPresetService;
        this.builderProfileService = builderProfileService;
        this.builderKitService = builderKitService;
        this.kitShortcutRegistry = kitShortcutRegistry;
        this.kitMenuService = kitMenuService;
        this.nudgeSettingsService = nudgeSettingsService;
        this.adminModeService = adminModeService;
        this.ramAlertService = ramAlertService;
        this.helpContentService = helpContentService;
        this.listMenuConfigService = listMenuConfigService;
        this.runtimePreferencesService = runtimePreferencesService;
        this.selectionBookmarkService = selectionBookmarkService;
        this.tabMenuSettingsService = tabMenuSettingsService;
        this.tabInfoPanelService = tabInfoPanelService;
        this.recentEditTrailService = recentEditTrailService;
        this.decoyPlayerCountService = decoyPlayerCountService;
        this.decoyTabListService = decoyTabListService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("bzl") || cmd.equals("bayzyl")) {
            return handleBzl(sender, args);
        }
        if (cmd.equals("bzlhelp") || cmd.equals("bayzylhelp") || cmd.equals("bzyzylhelp")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "help";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleBzl(sender, remapped);
        }
        if (cmd.equals("kithelp")) {
            return handleKitHelpRoot(sender, args);
        }
        if (cmd.equals("kitlist")) {
            return handleKitListRoot(sender, args);
        }
        if (cmd.equals("kitupdate")) {
            return handleKitUpdateRoot(sender, args);
        }
        if (cmd.equals("kitconfirm")) {
            return handleKitConfirmRoot(sender, args);
        }
        if (cmd.equals("bzltoggle")) {
            return handleBzlToggle(sender, args);
        }
        if (cmd.equals("ramalert")) {
            return handleRamAlertRoot(sender, args);
        }
        if (cmd.equals("nudge")) {
            return handleNudgeRoot(sender, args);
        }
        if (cmd.equals("tabmenu")) {
            return handleTabMenuRoot(sender, args);
        }
        if (cmd.equals("profile")) {
            return handleProfileRoot(sender, args);
        }
        if (cmd.equals("kit")) {
            return handleKitRoot(sender, args);
        }
        if (cmd.equals("kitmake")) {
            return handleKitMakeRoot(sender, args);
        }
        if (cmd.equals("cleanup")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "cleanup";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleBzl(sender, remapped);
        }
        if (cmd.equals("palette")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "palette";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleBzl(sender, remapped);
        }
        if (cmd.equals("brush")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "brush";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleBzl(sender, remapped);
        }
        if (kitShortcutRegistry.isShortcutRegistered(cmd)) {
            return handleKitShortcutRoot(sender, cmd, args);
        }

        return handleTopLevel(sender, cmd, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("bzlhelp") || cmd.equals("bayzylhelp") || cmd.equals("bzyzylhelp")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "help";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return SuggestionUtil.suggest("bzlhelp", remapped);
        }
        if (cmd.equals("kithelp")) {
            return SuggestionUtil.suggest("bzlhelp", args);
        }
        if (cmd.equals("kitlist")) {
            return suggestKitListArgs(args);
        }
        if (cmd.equals("kitupdate")) {
            return suggestKitUpdateArgs(args);
        }
        if (cmd.equals("kitconfirm")) {
            return Collections.emptyList();
        }
        if (cmd.equals("profile")) {
            return suggestProfileArgs(args);
        }
        if (cmd.equals("kit")) {
            return suggestKitArgs(args);
        }
        if (cmd.equals("kitmake")) {
            return suggestKitMakeArgs(args);
        }
        if (cmd.equals("cleanup")) {
            return suggestCleanupArgs(args);
        }
        if (cmd.equals("palette")) {
            return SuggestionUtil.suggest("palette", args);
        }
        if (cmd.equals("brush")) {
            return suggestBrushRootArgs(args);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("brush")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestBrushRootArgs(remapped);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("profile")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestProfileArgs(remapped);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kit")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestKitArgs(remapped);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kitmake")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestKitMakeArgs(remapped);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kitupdate")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestKitUpdateArgs(remapped);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kitconfirm")) {
            return Collections.emptyList();
        }
        if (cmd.equals("bzl") || cmd.equals("bayzyl")) {
            return suggestBzlArgs(args);
        }
        if (kitShortcutRegistry.isShortcutRegistered(cmd)) {
            return Collections.emptyList();
        }
        if (cmd.equals("selload") && sender instanceof Player player && args.length == 1) {
            return filterPrefix(selectionBookmarkService.list(player.getUniqueId()), args[0]);
        }
        return SuggestionUtil.suggest(cmd, args);
    }

    private boolean handleBzl(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHeader(sender);
            sender.sendMessage(ChatColor.WHITE + "Use /bzl help for commands.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            if (args.length == 1) {
                sendHelpPage(sender, 1);
                return true;
            }

            String topic = args[1].toLowerCase(Locale.ROOT);
            Integer topicPage = helpTopicPage(topic);
            if (topicPage != null) {
                sendHelpPage(sender, topicPage);
                return true;
            }
            if (topic.matches("\\d+")) {
                int page = Integer.parseInt(topic);
                int pageCount = helpContentService.getPageCount();
                if (page >= 1 && page <= pageCount) {
                    sendHelpPage(sender, page);
                    return true;
                }
                sender.sendMessage(ChatColor.RED + "Help page must be 1-" + pageCount + ".");
                return true;
            }

            Optional<CommandSpec> top = CommandRegistry.getTopLevel().stream()
                    .filter(spec -> spec.name().equals(topic))
                    .findFirst();
            if (top.isPresent()) {
                sendUsage(sender, top.get());
                return true;
            }

            Optional<CommandSpec> bzl = CommandRegistry.getBzlSubcommands().stream()
                    .filter(spec -> spec.name().equals(topic))
                    .findFirst();
            if (bzl.isPresent()) {
                sendUsage(sender, bzl.get());
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Unknown help topic: " + topic);
            return true;
        }

        if (sub.equals("select")) {
            return handleSelectNamespace(sender, args);
        }

        if (sub.equals("selectionparticles")) {
            return handleSelectionParticlesNamespace(sender, args);
        }

        if (sub.equals("tool")) {
            return handleToolNamespace(sender, args);
        }

        if (sub.equals("cleanup")) {
            return handleCleanupNamespace(sender, args);
        }

        if (sub.equals("palette")) {
            return handlePaletteNamespace(sender, args);
        }

        if (sub.equals("naturalize")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return handleNaturalize(sender, remapped);
        }

        if (sub.equals("brush")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return handleBrush(sender, remapped);
        }

        if (sub.equals("env")) {
            return handleEnvNamespace(sender, args);
        }

        if (sub.equals("debug")) {
            return handleDebugNamespace(sender, args);
        }

        if (sub.equals("nudge")) {
            return handleNudgeNamespace(sender, args);
        }

        if (sub.equals("profile")) {
            return handleProfileNamespace(sender, args);
        }
        if (sub.equals("kit")) {
            return handleKitNamespace(sender, args);
        }
        if (sub.equals("kitmake")) {
            return handleKitMakeNamespace(sender, args);
        }
        if (sub.equals("kitupdate")) {
            return handleKitUpdateNamespace(sender, args);
        }
        if (sub.equals("kitconfirm")) {
            return handleKitConfirmNamespace(sender, args);
        }

        if (sub.equals("ramalert")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return handleRamAlertRoot(sender, remapped);
        }

        if (sub.equals("tabmenu")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return handleTabMenuRoot(sender, remapped);
        }

        if (sub.equals("unstick")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return handleUnstick(sender, remapped);
        }

        if (builderKitService.existsKit(sub)) {
            return handleKitShortcutRoot(sender, sub, new String[0]);
        }

        sender.sendMessage(ChatColor.YELLOW + "Not implemented yet: /bzl " + sub);
        sender.sendMessage(ChatColor.WHITE + "Use /bzl help to see available topics.");
        return true;
    }

    private boolean handleTopLevel(CommandSender sender, String cmd, String[] args) {
        Optional<CommandSpec> spec = CommandRegistry.getTopLevel().stream()
                .filter(item -> item.name().equals(cmd))
                .findFirst();
        if (spec.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Unknown command.");
            return true;
        }

        if (cmd.equals("wand")) {
            return handleWand(sender);
        }

        if (cmd.equals("eraser")) {
            return handleEraser(sender, args);
        }

        if (cmd.equals("nightvision")) {
            return handleNightvision(sender, args);
        }
        if (cmd.equals("kit")) {
            return handleKitRoot(sender, args);
        }
        if (cmd.equals("kitmake")) {
            return handleKitMakeRoot(sender, args);
        }
        if (cmd.equals("kitupdate")) {
            return handleKitUpdateRoot(sender, args);
        }
        if (cmd.equals("kitconfirm")) {
            return handleKitConfirmRoot(sender, args);
        }
        if (cmd.equals("unstick")) {
            return handleUnstick(sender, args);
        }
        if (cmd.equals("measure")) {
            return handleMeasure(sender);
        }
        if (cmd.equals("ruler")) {
            return handleRuler(sender);
        }
        if (cmd.equals("whereami")) {
            return handleWhereAmI(sender);
        }
        if (cmd.equals("surface")) {
            return handleSurface(sender);
        }
        if (cmd.equals("naturalize")) {
            return handleNaturalize(sender, args);
        }
        if (cmd.equals("ascend")) {
            return handleAscend(sender);
        }
        if (cmd.equals("descend")) {
            return handleDescend(sender);
        }
        if (cmd.equals("align")) {
            return handleAlign(sender, args);
        }
        if (cmd.equals("ceil")) {
            return handleCeil(sender);
        }
        if (cmd.equals("centerme")) {
            return handleCenterMe(sender);
        }
        if (cmd.equals("ghosthand")) {
            return handleGhostHand(sender, args);
        }
        if (cmd.equals("clipboardinfo")) {
            return handleClipboardInfo(sender);
        }
        if (cmd.equals("trailclear")) {
            return handleTrailClear(sender);
        }
        if (cmd.equals("selcorners")) {
            return handleSelCorners(sender);
        }
        if (cmd.equals("selswap")) {
            return handleSelSwap(sender);
        }
        if (cmd.equals("selsave")) {
            return handleSelSave(sender, args);
        }
        if (cmd.equals("selload")) {
            return handleSelLoad(sender, args);
        }
        if (cmd.equals("selcenter")) {
            return handleSelCenter(sender);
        }
        if (cmd.equals("thru")) {
            return handleThru(sender);
        }
        if (cmd.equals("oops")) {
            return handleOops(sender, args);
        }
        if (cmd.equals("particlevisualtoggle") || cmd.equals("pvt")) {
            return handleParticleVisualToggle(sender);
        }
        if (cmd.equals("set")) {
            return handleSet(sender, args);
        }
        if (cmd.equals("replace")) {
            return handleReplace(sender, args);
        }
        if (cmd.equals("copy")) {
            return handleCopy(sender, args);
        }
        if (cmd.equals("cut")) {
            return handleCut(sender, args);
        }
        if (cmd.equals("paste")) {
            return handlePaste(sender, args);
        }
        if (cmd.equals("move")) {
            return handleMove(sender, args);
        }
        if (cmd.equals("rotate")) {
            return handleRotate(sender, args);
        }
        if (cmd.equals("flip")) {
            return handleFlip(sender, args);
        }
        if (cmd.equals("sphere")) {
            return handleSphere(sender, args, false);
        }
        if (cmd.equals("hsphere")) {
            return handleSphere(sender, args, true);
        }
        if (cmd.equals("dome")) {
            return handleDome(sender, args, false, false);
        }
        if (cmd.equals("hdome")) {
            return handleDome(sender, args, true, false);
        }
        if (cmd.equals("bowl")) {
            return handleDome(sender, args, false, true);
        }
        if (cmd.equals("hbowl")) {
            return handleDome(sender, args, true, true);
        }
        if (cmd.equals("cyl")) {
            return handleCylinder(sender, args, false);
        }
        if (cmd.equals("hcyl")) {
            return handleCylinder(sender, args, true);
        }
        if (cmd.equals("pyramid")) {
            return handlePyramid(sender, args, false);
        }
        if (cmd.equals("hpyramid")) {
            return handlePyramid(sender, args, true);
        }
        if (cmd.equals("brush")) {
            return handleBrush(sender, args);
        }
        if (cmd.equals("mask")) {
            return handleBrushMaskCommand(sender, args);
        }
        if (cmd.equals("material")) {
            return handleBrushMaterialCommand(sender, args);
        }
        if (cmd.equals("size")) {
            return handleBrushSizeCommand(sender, args);
        }
        if (cmd.equals("density")) {
            return handleBrushDensityCommand(sender, args);
        }
        if (cmd.equals("none")) {
            if (sender instanceof Player p) {
                return handleBrushNone(p);
            }
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (cmd.equals("palette")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "palette";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleBzl(sender, remapped);
        }
        if (cmd.equals("generate")) {
            return handleGenerate(sender, args);
        }
        if (cmd.equals("generatebiome")) {
            return handleGenerateBiome(sender, args);
        }
        if (cmd.equals("biomeinfo")) {
            return handleBiomeInfo(sender);
        }
        if (cmd.equals("forestgen")) {
            return handleForestGen(sender, args);
        }
        if (cmd.equals("pumpkins")) {
            return handlePumpkins(sender, args);
        }
        if (cmd.equals("walls")) {
            return handleWalls(sender, args);
        }
        if (cmd.equals("overlay")) {
            return handleOverlay(sender, args);
        }
        if (cmd.equals("smooth")) {
            return handleSmooth(sender, args);
        }
        if (cmd.equals("stack")) {
            return handleStack(sender, args);
        }
        if (cmd.equals("undo")) {
            return handleUndo(sender, args);
        }
        if (cmd.equals("redo")) {
            return handleRedo(sender, args);
        }
        if (cmd.equals("expand")) {
            return handleExpand(sender, args);
        }
        if (cmd.equals("contract")) {
            return handleContract(sender, args);
        }
        if (cmd.equals("select")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "select";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleSelectNamespace(sender, remapped);
        }

        sender.sendMessage(ChatColor.YELLOW + "Bayzyl command stub: " + spec.get().name());
        sender.sendMessage(ChatColor.WHITE + spec.get().usage());
        return true;
    }

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        equipInMainHand(player, toolManager.createWand(), "Bayzyl wand equipped in your hand.");
        return true;
    }

    private boolean handleEraser(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            EraserSettings settings = new EraserSettings(5, BlockMask.parse(null), false, false, true, false);
            equipInMainHand(player, toolManager.createEraser(settings),
                    "Bayzyl eraser equipped in your hand: radius 5, carve air, bedrock protected.");
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Radius must be a number.");
            return true;
        }

        String mask = null;
        boolean surfaceOnly = false;
        boolean selectionOnly = false;
        boolean carveOnly = false;
        boolean editBedrock = false;

        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (!token.contains(":")) {
                if (mask == null) {
                    mask = token;
                }
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
            switch (key) {
                case "mask":
                    mask = value;
                    break;
                case "surface":
                    surfaceOnly = value.equals("on") || value.equals("true") || value.equals("surface");
                    break;
                case "selection":
                    selectionOnly = value.equals("only") || value.equals("true") || value.equals("selection");
                    break;
                case "carve":
                    carveOnly = value.equals("air") || value.equals("carve") || value.equals("true");
                    break;
                case "bedrock":
                    editBedrock = value.equals("on") || value.equals("true") || value.equals("edit");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown option: " + key);
                    return true;
            }
        }

        int maxRadius = eraserService.getMaxRadius(player);
        if (radius > maxRadius && maxRadius < Integer.MAX_VALUE) {
            sender.sendMessage(ChatColor.RED + "Max eraser radius is " + maxRadius + ".");
            return true;
        }

        BlockMask parsed = BlockMask.parse(mask);
        EraserSettings settings = new EraserSettings(radius, parsed, surfaceOnly, selectionOnly, carveOnly, editBedrock);
        equipInMainHand(player, toolManager.createEraser(settings),
                "Bayzyl eraser equipped in your hand: radius " + radius + ", bedrock " + (editBedrock ? "on" : "protected") + ".");
        return true;
    }

    private boolean handleNightvision(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "toggle";
        boolean enable;
        switch (mode) {
            case "on":
                enable = true;
                break;
            case "off":
                enable = false;
                break;
            default:
                enable = !nightVisionService.isEnabled(player);
                break;
        }
        nightVisionService.setEnabled(player, enable);
        savePlayerRuntime(player);
        if (enable) {
            sender.sendMessage(ChatColor.YELLOW + "Lights on!");
        } else {
            sender.sendMessage(ChatColor.DARK_BLUE + "Lights Off!");
        }
        return true;
    }

    private boolean handleUnstick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("auto")) {
            if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
                sender.sendMessage(ChatColor.WHITE + "Auto-unstick is " + (autoUnstickService.isEnabled(player) ? ChatColor.GREEN + "on" : ChatColor.RED + "off") + ChatColor.WHITE + ".");
                return true;
            }
            String value = args[1].toLowerCase(Locale.ROOT);
            if (!value.equals("on") && !value.equals("off")) {
                sender.sendMessage(ChatColor.RED + "Usage: /unstick auto <on|off>");
                return true;
            }
            boolean enabled = value.equals("on");
            autoUnstickService.setEnabled(player, enabled);
            savePlayerRuntime(player);
            sender.sendMessage(ChatColor.WHITE + "Auto-unstick " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
            return true;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            sender.sendMessage(ChatColor.RED + "Unstick is disabled in spectator mode.");
            return true;
        }
        if (movementAssistService.unstick(player)) {
            sender.sendMessage(ChatColor.WHITE + "Unstuck.");
        } else {
            sender.sendMessage(ChatColor.RED + "Could not find a safe place to unstick you.");
        }
        return true;
    }

    private boolean handleMeasure(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        int width = selection.getMaxX() - selection.getMinX() + 1;
        int height = selection.getMaxY() - selection.getMinY() + 1;
        int length = selection.getMaxZ() - selection.getMinZ() + 1;
        long footprint = (long) width * length;
        long surface = 2L * ((long) width * height + (long) width * length + (long) height * length);
        long volume = selection.getVolume();

        sendHeader(sender);
        sender.sendMessage(ChatColor.GOLD + "Selection" + ChatColor.DARK_GRAY + " [" + ChatColor.WHITE + selection.getMinX() + "," + selection.getMinY() + "," + selection.getMinZ()
                + ChatColor.DARK_GRAY + " → " + ChatColor.WHITE + selection.getMaxX() + "," + selection.getMaxY() + "," + selection.getMaxZ() + ChatColor.DARK_GRAY + "]");
        sender.sendMessage(ChatColor.WHITE + "Dimensions: " + ChatColor.AQUA + width + "W " + ChatColor.AQUA + height + "H " + ChatColor.AQUA + length + "L");
        sender.sendMessage(ChatColor.WHITE + "Footprint: " + ChatColor.GOLD + footprint);
        sender.sendMessage(ChatColor.WHITE + "Surface: " + ChatColor.GOLD + surface);
        sender.sendMessage(ChatColor.WHITE + "Volume: " + ChatColor.GOLD + volume);
        return true;
    }

    private boolean handleRuler(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        double distance = movementAssistService.rulerDistance(player);
        if (distance < 0.0D) {
            sender.sendMessage(ChatColor.RED + "No target block in sight.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Ruler: " + ChatColor.AQUA + String.format(Locale.ROOT, "%.2f", distance)
                + ChatColor.WHITE + " blocks to the target block.");
        return true;
    }

    private boolean handleWhereAmI(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + movementAssistService.whereAmI(player));
        return true;
    }

    private boolean handleSurface(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.surface(player)) {
            sender.sendMessage(ChatColor.RED + "Could not find a safe surface above you.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Teleported to the surface.");
        return true;
    }

    private boolean handleAscend(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.ascend(player)) {
            sender.sendMessage(ChatColor.RED + "Could not find a safe floor above you.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Ascended to the next safe floor.");
        return true;
    }

    private boolean handleDescend(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.descend(player)) {
            sender.sendMessage(ChatColor.RED + "Could not find a safe floor below you.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Descended to the nearest safe floor.");
        return true;
    }

    private boolean handleAlign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String direction = args.length > 0 ? args[0] : null;
        movementAssistService.align(player, direction);
        sender.sendMessage(ChatColor.WHITE + "Aligned.");
        return true;
    }

    private boolean handleCeil(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        int clearance = movementAssistService.ceilingClearance(player);
        if (clearance < 0) {
            sender.sendMessage(ChatColor.AQUA + "No ceiling found above you in this world height range.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Ceiling clearance: " + ChatColor.GOLD + clearance + ChatColor.WHITE + " block(s).");
        return true;
    }

    private boolean handleCenterMe(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.centerMe(player)) {
            sender.sendMessage(ChatColor.RED + "Could not center you.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Centered.");
        return true;
    }

    private boolean handleGhostHand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        boolean enabled;
        if (args.length == 0) {
            enabled = ghostHandService.toggle(player);
        } else {
            String mode = args[0].toLowerCase(Locale.ROOT);
            if (mode.equals("status")) {
                sender.sendMessage(ChatColor.WHITE + "Ghost hand is " + (ghostHandService.isEnabled(player) ? ChatColor.GREEN + "on" : ChatColor.RED + "off") + ChatColor.WHITE + ".");
                return true;
            }
            if (!mode.equals("on") && !mode.equals("off")) {
                sender.sendMessage(ChatColor.RED + "Usage: /ghosthand [on|off|status]");
                return true;
            }
            enabled = mode.equals("on");
            ghostHandService.setEnabled(player, enabled);
        }
        savePlayerRuntime(player);
        sender.sendMessage(ChatColor.WHITE + "Ghost hand " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleClipboardInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            sender.sendMessage(ChatColor.RED + "Clipboard is empty.");
            return true;
        }
        sendHeader(sender);
        sender.sendMessage(ChatColor.GOLD + "Clipboard" + ChatColor.DARK_GRAY + " • "
                + ChatColor.AQUA + clipboard.getSizeX() + "W " + ChatColor.AQUA + clipboard.getSizeY() + "H " + ChatColor.AQUA + clipboard.getSizeZ() + "L");
        if (clipboard.getOrigin() != null) {
            sender.sendMessage(ChatColor.WHITE + "Origin: " + ChatColor.GOLD
                    + clipboard.getOrigin().getBlockX() + "," + clipboard.getOrigin().getBlockY() + "," + clipboard.getOrigin().getBlockZ());
        }
        sender.sendMessage(ChatColor.WHITE + "Offset: " + ChatColor.GOLD
                + clipboard.getMinOffsetX() + "," + clipboard.getMinOffsetY() + "," + clipboard.getMinOffsetZ());
        return true;
    }

    private boolean handleTrailClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        recentEditTrailService.clearEntries(player.getUniqueId());
        sender.sendMessage(ChatColor.WHITE + "Recent edit trail cleared.");
        return true;
    }

    private boolean handleSelCorners(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "Pos1: " + ChatColor.AQUA
                + selection.getPos1().getBlockX() + "," + selection.getPos1().getBlockY() + "," + selection.getPos1().getBlockZ());
        sender.sendMessage(ChatColor.GOLD + "Pos2: " + ChatColor.AQUA
                + selection.getPos2().getBlockX() + "," + selection.getPos2().getBlockY() + "," + selection.getPos2().getBlockZ());
        return true;
    }

    private boolean handleSelSwap(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!selectionManager.swap(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Swapped selection corners.");
        return true;
    }

    private boolean handleSelSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /selsave <name>");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        if (!selectionBookmarkService.save(player.getUniqueId(), args[0], selection)) {
            sender.sendMessage(ChatColor.RED + "Could not save selection. Use letters, numbers, _ or -, max 32 chars.");
            return true;
        }
        sender.sendMessage(ChatColor.WHITE + "Saved selection " + ChatColor.WHITE + args[0].toLowerCase(Locale.ROOT) + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleSelLoad(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            List<String> saved = selectionBookmarkService.list(player.getUniqueId());
            if (saved.isEmpty()) {
                sender.sendMessage(listMenuConfigService.format(
                        SELECTION_BOOKMARKS_MENU,
                        "messages.empty",
                        "&fNo saved selections yet.",
                        ListMenuConfigService.tokens()
                ));
            } else {
                String separator = listMenuConfigService.format(
                        SELECTION_BOOKMARKS_MENU,
                        "separator",
                        "&8, &f",
                        ListMenuConfigService.tokens()
                );
                sender.sendMessage(listMenuConfigService.format(
                        SELECTION_BOOKMARKS_MENU,
                        "messages.line",
                        "&fSaved selections: {names}",
                        ListMenuConfigService.tokens("names", String.join(separator, saved))
                ));
            }
            return true;
        }
        Selection loaded = selectionBookmarkService.load(player.getUniqueId(), args[0]);
        if (loaded == null || !loaded.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Saved selection not found or its world is unavailable.");
            return true;
        }
        selectionManager.setCuboid(player.getUniqueId(), loaded.getPos1(), loaded.getPos2());
        sender.sendMessage(ChatColor.WHITE + "Loaded selection " + ChatColor.WHITE + args[0].toLowerCase(Locale.ROOT) + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleSelCenter(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        boolean enabled;
        if (visualizationManager.isSelectionCenterVisible(player.getUniqueId())) {
            visualizationManager.clearSelectionCenter(player.getUniqueId());
            enabled = false;
        } else {
            visualizationManager.showSelectionCenter(player.getUniqueId());
            enabled = true;
        }
        sender.sendMessage(ChatColor.WHITE + "Selection center marker " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean handleThru(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (movementAssistService.thru(player)) {
            sender.sendMessage(ChatColor.AQUA + "Zoom!");
        } else {
            sender.sendMessage(ChatColor.RED + "Could not find a safe spot through that wall.");
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            syntaxError(sender, "/set <BLOCK> [mask:<BLOCKS>] [if:air|solid|any] [confirm:true]");
            return true;
        }
        BlockDistribution distribution;
        try {
            distribution = BlockDistribution.parse(args[0]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid block distribution: " + ex.getMessage());
            return true;
        }
        OptionState options = parseOptions(args, 1);
        if (!isValidIfMode(options.ifMode)) {
            sender.sendMessage(ChatColor.RED + "Invalid if: mode. Use if:air, if:solid, or if:any");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (editService.requiresConfirm(selection, options.confirm)) {
            if (shouldBypassConfirm(player, options.confirm)) {
                options.confirm = true;
            }
        }
        if (editService.requiresConfirm(selection, options.confirm)) {
            sender.sendMessage(ChatColor.RED + "Large selection. Re-run with confirm:true");
            return true;
        }
        BlockMask mask = BlockMask.parse(options.mask);

        int changed = editService.setBlocks(player, selection, distribution, mask, options.ifMode);
        recentEditTrailService.record(player.getUniqueId(), "set " + args[0]);
        sender.sendMessage(ChatColor.WHITE + "Set " + changed + " blocks.");
        return true;
    }

    private boolean handleReplace(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 2) {
            syntaxError(sender, "/replace <BLOCKS> <BLOCK> [mask:<BLOCKS>] [confirm:true]");
            return true;
        }
        BlockMask from = BlockMask.parse(args[0]);
        BlockDistribution toDistribution;
        try {
            toDistribution = BlockDistribution.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid block distribution: " + ex.getMessage());
            return true;
        }
        OptionState options = parseOptions(args, 2);
        if (!isValidIfMode(options.ifMode)) {
            sender.sendMessage(ChatColor.RED + "Invalid if: mode. Use if:air, if:solid, or if:any");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (editService.requiresConfirm(selection, options.confirm)) {
            if (shouldBypassConfirm(player, options.confirm)) {
                options.confirm = true;
            }
        }
        if (editService.requiresConfirm(selection, options.confirm)) {
            sender.sendMessage(ChatColor.RED + "Large selection. Re-run with confirm:true");
            return true;
        }
        BlockMask mask = BlockMask.parse(options.mask);

        int changed = editService.replaceBlocks(player, selection, from, toDistribution, mask);
        recentEditTrailService.record(player.getUniqueId(), "replace " + toDistribution.toString().toLowerCase(Locale.ROOT));
        sender.sendMessage(ChatColor.WHITE + "Replaced " + changed + " blocks.");
        return true;
    }

    private boolean handleCopy(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        OptionState options = parseOptions(args, 0);
        BlockMask mask = BlockMask.parse(options.mask);
        Selection selection = selectionManager.get(player.getUniqueId());
        Clipboard clipboard = editService.copySelection(player, selection, mask);
        if (clipboard != null) {
            recentEditTrailService.record(player.getUniqueId(), "copy " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ());
            sender.sendMessage(ChatColor.WHITE + "Copied " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ() + ".");
        }
        return true;
    }

    private boolean handleCut(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        OptionState options = parseOptions(args, 0);
        Selection selection = selectionManager.get(player.getUniqueId());
        if (editService.requiresConfirm(selection, options.confirm)) {
            if (shouldBypassConfirm(player, options.confirm)) {
                options.confirm = true;
            }
        }
        if (editService.requiresConfirm(selection, options.confirm)) {
            sender.sendMessage(ChatColor.RED + "Large selection. Re-run with confirm:true");
            return true;
        }
        BlockMask mask = BlockMask.parse(options.mask);
        int changed = editService.cutSelection(player, selection, mask);
        recentEditTrailService.record(player.getUniqueId(), "cut");
        sender.sendMessage(ChatColor.WHITE + "Cut " + changed + " blocks.");
        return true;
    }

    private boolean handlePaste(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        ClipboardPasteRequest request = ClipboardCommandParser.parsePaste(args);
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            sender.sendMessage(ChatColor.RED + "Clipboard is empty.");
            return true;
        }
        int rotation = request.rotation();
        Location targetOrigin;
        switch (request.at()) {
            case "origin" -> {
                if (clipboard.getOrigin() == null || clipboard.getOrigin().getWorld() == null) {
                    sender.sendMessage(ChatColor.RED + "Clipboard origin unavailable.");
                    return true;
                }
                targetOrigin = clipboard.getOrigin();
            }
            case "target" -> {
                var result = player.rayTraceBlocks(120);
                if (result == null || result.getHitBlock() == null) {
                    sender.sendMessage(ChatColor.RED + "No target block in range.");
                    return true;
                }
                var face = result.getHitBlockFace();
                targetOrigin = face == null
                        ? result.getHitBlock().getLocation()
                        : result.getHitBlock().getRelative(face).getLocation();
            }
            default -> targetOrigin = resolvePlayerPasteAnchor(player);
        }

        Selection pasteSelection = EditUtil.getPasteSelection(clipboard, targetOrigin, rotation);
        boolean confirm = shouldBypassConfirm(player, request.confirm());
        if (editService.requiresConfirm(pasteSelection, confirm)) {
            sender.sendMessage(ChatColor.RED + "Large paste. Re-run with confirm:true");
            return true;
        }
        if (pasteSelection == null) {
            sender.sendMessage(ChatColor.RED + "Could not resolve paste destination.");
            return true;
        }

        if (request.previewOnly()) {
            selectionManager.setCuboid(player.getUniqueId(), pasteSelection.getPos1(), pasteSelection.getPos2());
            sender.sendMessage(ChatColor.WHITE + "Paste preview selected.");
            return true;
        }

        int changed = editService.pasteClipboard(player, clipboard, targetOrigin, rotation, request.ignoreAir());
        recentEditTrailService.record(player.getUniqueId(), "paste " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ());
        if (request.selectAfterPaste()) {
            selectionManager.setCuboid(player.getUniqueId(), pasteSelection.getPos1(), pasteSelection.getPos2());
        }
        sender.sendMessage(ChatColor.WHITE + "Pasted " + changed + " blocks.");
        return true;
    }

    private boolean handleMove(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        ClipboardMoveRequest request;
        try {
            request = ClipboardCommandParser.parseMove(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        int[] direction = DirectionUtil.resolve(player, request.direction());
        long totalVolume = selection.getVolume() * 2L;
        if (totalVolume > EditUtil.CONFIRM_VOLUME && !shouldBypassConfirm(player, request.confirm())) {
            sender.sendMessage(ChatColor.RED + "Large move. Re-run with confirm:true");
            return true;
        }

        int changed = editService.moveSelection(player, selection, request.distance(), direction, request.ignoreAir());
        recentEditTrailService.record(player.getUniqueId(), "move " + request.distance() + " " + request.direction());
        sender.sendMessage(ChatColor.WHITE + "Moved selection " + request.distance() + " block(s) " + request.direction() + " and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleSphere(CommandSender sender, String[] args, boolean hollow) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String verb = hollow ? "hsphere" : "sphere";
        SphereRequest request;
        try {
            request = ShapeCommandParser.parseSphere(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        long estimate = shapeService.estimateSphere(request);
        if (shapeService.requiresConfirm(estimate, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large " + verb + ". Re-run with confirm:true");
            return true;
        }

        ShapeResult result = shapeService.createSphere(player, request);
        sendShapeResult(sender, result, hollow ? "hollow sphere" : "sphere");
        return true;
    }

    private boolean handleDome(CommandSender sender, String[] args, boolean hollow, boolean bowl) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String verb = bowl ? (hollow ? "hbowl" : "bowl") : (hollow ? "hdome" : "dome");
        SphereRequest request;
        try {
            request = ShapeCommandParser.parseSphere(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        long estimate = Math.max(1L, shapeService.estimateSphere(request) / 2L);
        if (shapeService.requiresConfirm(estimate, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large " + verb + ". Re-run with confirm:true");
            return true;
        }

        ShapeResult result = shapeService.createDome(player, request, bowl);
        sendShapeResult(sender, result, bowl ? (hollow ? "hollow bowl" : "bowl") : (hollow ? "hollow dome" : "dome"));
        return true;
    }

    private boolean handleCylinder(CommandSender sender, String[] args, boolean hollow) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String verb = hollow ? "hcyl" : "cyl";
        CylinderRequest request;
        try {
            request = ShapeCommandParser.parseCylinder(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        long estimate = shapeService.estimateCylinder(request);
        if (shapeService.requiresConfirm(estimate, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large " + verb + ". Re-run with confirm:true");
            return true;
        }

        ShapeResult result = shapeService.createCylinder(player, request);
        sendShapeResult(sender, result, hollow ? "hollow cylinder" : "cylinder");
        return true;
    }

    private boolean handlePyramid(CommandSender sender, String[] args, boolean hollow) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String verb = hollow ? "hpyramid" : "pyramid";
        PyramidRequest request;
        try {
            request = ShapeCommandParser.parsePyramid(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        long estimate = shapeService.estimatePyramid(request);
        if (shapeService.requiresConfirm(estimate, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large " + verb + ". Re-run with confirm:true");
            return true;
        }

        ShapeResult result = shapeService.createPyramid(player, request);
        sendShapeResult(sender, result, hollow ? "hollow pyramid" : "pyramid");
        return true;
    }

    private boolean handleBrush(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("none")) {
            return handleBrushNone(player);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            return handleBrushInfo(player);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("save")) {
            return handleBrushSave(player, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("load")) {
            return handleBrushLoad(player, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            return handleBrushList(player);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            return handleBrushDelete(player, args);
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("clipboard") || args[0].equalsIgnoreCase("clip"))) {
            return handleBrushClipboard(player);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("paint")) {
            return handleBrushPaint(player, args);
        }
        if (args.length >= 1) {
            PatternBrushMode patternMode = PatternBrushMode.parse(args[0]);
            if (patternMode != null) {
                return handleBrushPattern(player, patternMode, args);
            }
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("erase") || args[0].equalsIgnoreCase("eraser"))) {
            return handleBrushErase(player, args);
        }
        if (args.length >= 1) {
            TerrainBrushType terrainBrushType = TerrainBrushType.parse(args[0]);
            if (terrainBrushType != null) {
                return handleBrushTerrain(player, terrainBrushType, args);
            }
        }

        ShapeBrushSettings settings;
        try {
            settings = ShapeCommandParser.parseBrush(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        long estimate = estimateBrush(settings);
        if (shapeService.requiresConfirm(estimate, shouldBypassConfirm(player, settings.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large brush. Re-run with confirm:true");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createShapeBrush(settings);
            equipBrushInMainHand(player, item);
            sender.sendMessage(ChatColor.WHITE + "Created " + settings.type().displayName() + " brush and equipped it in your hand.");
        } else {
            toolManager.bindShapeBrush(held, settings, false);
            player.getInventory().setItemInMainHand(held);
            sender.sendMessage(ChatColor.WHITE + "Bound " + settings.type().displayName() + " brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "Material: " + ChatColor.WHITE + settings.material().name().toLowerCase(Locale.ROOT)
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + describeBrushShape(settings)
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (settings.mask() == null ? "any" : settings.mask().summary())
                + ChatColor.DARK_GRAY + " | anchor: " + ChatColor.WHITE + settings.anchorMode().name().toLowerCase(Locale.ROOT));
        sender.sendMessage(ChatColor.DARK_GRAY + "Right-click to build. Tune with /mask, /material, /size, or /brush none to unbind.");
        return true;
    }

    private boolean handleBrushSave(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /brush save <name>");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!brushPresetService.isSavableBrush(held) || toolManager.readShapeBrushSettings(held) == null) {
            player.sendMessage(ChatColor.RED + "Hold a Bayzyl shape brush to save it.");
            return true;
        }
        if (!brushPresetService.saveBrush(player, args[1], held)) {
            player.sendMessage(ChatColor.RED + "Could not save brush.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Saved brush preset " + ChatColor.GOLD + args[1] + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBrushLoad(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /brush load <name>");
            return true;
        }
        ItemStack item = brushPresetService.loadBrush(args[1]);
        if (item == null) {
            player.sendMessage(ChatColor.RED + "Brush preset not found.");
            return true;
        }
        equipBrushInMainHand(player, item);
        player.sendMessage(ChatColor.WHITE + "Loaded brush preset " + ChatColor.GOLD + args[1] + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBrushList(Player player) {
        List<String> brushes = brushPresetService.listBrushes();
        if (brushes.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No brush presets saved.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Brush presets: " + ChatColor.GOLD + String.join(ChatColor.GRAY + ", " + ChatColor.GOLD, brushes));
        return true;
    }

    private boolean handleBrushDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /brush delete <name>");
            return true;
        }
        if (!brushPresetService.deleteBrush(args[1])) {
            player.sendMessage(ChatColor.RED + "Brush preset not found.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Deleted brush preset " + ChatColor.GOLD + args[1] + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBrushNone(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold the brush you want to unbind.");
            return true;
        }
        if (!toolManager.unbindAnyBrush(held)) {
            player.sendMessage(ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(ChatColor.WHITE + "Brush unbound.");
        return true;
    }

    private boolean handleBrushClipboard(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = new ItemStack(Material.BRUSH);
            toolManager.bindClipboardBrush(item, true);
            equipBrushInMainHand(player, item);
            player.sendMessage(ChatColor.WHITE + "Created clipboard brush and equipped it in your hand.");
        } else {
            toolManager.bindClipboardBrush(held, false);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(ChatColor.WHITE + "Bound clipboard brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Right-click a block to paste your clipboard at that point.");
        return true;
    }

    private boolean handleBrushPaint(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /brush paint <block> [density:0.3] [size:5] [mask:<blocks>]");
            return true;
        }
        Material material = EditUtil.parseBlock(args[1]);
        if (material == null) {
            player.sendMessage(ChatColor.RED + "Unknown block: " + args[1]);
            return true;
        }
        int size = 5;
        double density = 0.3;
        BlockMask mask = null;
        for (int i = 2; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (token.startsWith("size:")) {
                try {
                    size = Math.max(1, Math.min(64, Integer.parseInt(token.substring(5))));
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatColor.RED + "Invalid size: " + args[i]);
                    return true;
                }
            } else if (token.startsWith("density:")) {
                try {
                    density = Math.max(0.0, Math.min(1.0, Double.parseDouble(token.substring(8))));
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatColor.RED + "Invalid density: " + args[i]);
                    return true;
                }
            } else if (token.startsWith("mask:")) {
                mask = BlockMask.parse(args[i].substring(5));
            }
        }
        PaintBrushSettings settings = new PaintBrushSettings(material, size, density, mask);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = new ItemStack(Material.BRUSH);
            toolManager.bindPaintBrush(item, settings, true);
            equipBrushInMainHand(player, item);
            player.sendMessage(ChatColor.WHITE + "Created paint brush and equipped it in your hand.");
        } else {
            toolManager.bindPaintBrush(held, settings, false);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(ChatColor.WHITE + "Bound paint brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Block: " + ChatColor.WHITE + material.name().toLowerCase(Locale.ROOT)
                + ChatColor.DARK_GRAY + " | size: " + ChatColor.WHITE + size
                + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", density)
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (mask == null ? "any" : mask.summary()));
        return true;
    }

    private boolean handleBrushPattern(Player player, PatternBrushMode mode, String[] args) {
        PatternBrushSettings settings;
        try {
            settings = parsePatternBrush(mode, args);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = new ItemStack(Material.BRUSH);
            toolManager.bindPatternBrush(item, settings, true);
            equipBrushInMainHand(player, item);
            player.sendMessage(ChatColor.WHITE + "Created " + mode.displayName().toLowerCase(Locale.ROOT) + " brush and equipped it in your hand.");
        } else {
            toolManager.bindPatternBrush(held, settings, false);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(ChatColor.WHITE + "Bound " + mode.displayName().toLowerCase(Locale.ROOT) + " brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Size: " + ChatColor.WHITE + settings.size()
                + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", settings.density())
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (settings.mask() == null ? "any" : settings.mask().summary()));
        return true;
    }

    private PatternBrushSettings parsePatternBrush(PatternBrushMode mode, String[] args) {
        int size = 5;
        double density = switch (mode) {
            case SPATTER, NOISE, VEGETATION, DECAY -> 0.35;
            default -> 1.0;
        };
        BlockMask mask = null;
        Material from = null;
        Material to = null;
        List<Material> palette = List.of();

        switch (mode) {
            case SPATTER, SURFACE -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Usage: /brush " + mode.commandName() + " <block> [size:5] [density:0.35] [mask:<blocks>]");
                }
                to = requireBlock(args[1]);
            }
            case REPLACE -> {
                if (args.length < 3) {
                    throw new IllegalArgumentException("Usage: /brush replace <from> <to> [size:5]");
                }
                from = requireBlock(args[1]);
                to = requireBlock(args[2]);
                mask = BlockMask.parse(from.name().toLowerCase(Locale.ROOT));
            }
            case BLEND, NOISE -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Usage: /brush " + mode.commandName() + " <block,block,...> [size:5] [density:0.35] [mask:<blocks>]");
                }
                palette = parsePaletteArg(args[1]);
            }
            case RESTORE -> {
            }
            case VEGETATION -> {
                if (args.length >= 2 && !args[1].contains(":")) {
                    palette = vegetationPalette(args[1]);
                } else {
                    palette = vegetationPalette("grass");
                }
            }
            case DECAY -> {
                if (args.length >= 2 && !args[1].contains(":")) {
                    to = requireBlock(args[1]);
                }
            }
        }

        int optionStart = switch (mode) {
            case REPLACE -> 3;
            case RESTORE -> 1;
            case VEGETATION, DECAY -> args.length >= 2 && !args[1].contains(":") ? 2 : 1;
            default -> 2;
        };
        for (int i = optionStart; i < args.length; i++) {
            String token = args[i];
            if (!token.contains(":")) {
                throw new IllegalArgumentException("Unknown brush option: " + token);
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "r", "radius", "size" -> size = parseBoundedInt(value, "Size", 1, 64);
                case "density", "strength", "chance" -> density = parseDensity(value);
                case "mask" -> mask = BlockMask.parse(value);
                case "palette" -> palette = parsePaletteArg(value);
                default -> throw new IllegalArgumentException("Unknown brush option: " + key);
            }
        }
        if ((mode == PatternBrushMode.BLEND || mode == PatternBrushMode.NOISE || mode == PatternBrushMode.VEGETATION) && palette.isEmpty()) {
            throw new IllegalArgumentException("Palette must contain at least one block.");
        }
        return new PatternBrushSettings(mode, from, to, palette, size, density, mask);
    }

    private Material requireBlock(String raw) {
        Material material = EditUtil.parseBlock(raw);
        if (material == null) {
            throw new IllegalArgumentException("Unknown block: " + raw);
        }
        return material;
    }

    private List<Material> parsePaletteArg(String raw) {
        List<Material> materials = new ArrayList<>();
        for (String part : raw.split(",")) {
            materials.add(requireBlock(part));
        }
        return materials;
    }

    private List<Material> vegetationPalette(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "grass", "meadow" -> List.of(Material.SHORT_GRASS, Material.FERN);
            case "flowers", "flower" -> List.of(Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER);
            case "mushroom", "mushrooms" -> List.of(Material.BROWN_MUSHROOM, Material.RED_MUSHROOM);
            case "sapling", "saplings" -> List.of(Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.SPRUCE_SAPLING, Material.JUNGLE_SAPLING);
            default -> parsePaletteArg(raw);
        };
    }

    private int parseBoundedInt(String raw, String label, int min, int max) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private double parseDensity(String raw) {
        try {
            double value = Double.parseDouble(raw);
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Density must be between 0.0 and 1.0.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Density must be a number between 0.0 and 1.0.");
        }
    }

    private boolean handleBrushErase(Player player, String[] args) {
        int radius = 5;
        String mask = null;
        boolean surfaceOnly = false;
        boolean selectionOnly = false;
        boolean carveOnly = true;
        boolean editBedrock = false;

        if (args.length >= 2 && !args[1].contains(":")) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Radius must be a number.");
                return true;
            }
        }

        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
            switch (key) {
                case "r", "radius", "size" -> {
                    try {
                        radius = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(ChatColor.RED + "Radius must be a number.");
                        return true;
                    }
                }
                case "mask" -> mask = value;
                case "surface" -> surfaceOnly = value.equals("on") || value.equals("true") || value.equals("surface");
                case "selection" -> selectionOnly = value.equals("only") || value.equals("true") || value.equals("selection");
                case "carve" -> carveOnly = value.equals("air") || value.equals("carve") || value.equals("true") || value.equals("on");
                case "bedrock" -> editBedrock = value.equals("on") || value.equals("true") || value.equals("edit");
                default -> {
                    player.sendMessage(ChatColor.RED + "Unknown eraser option: " + key);
                    return true;
                }
            }
        }

        int maxRadius = eraserService.getMaxRadius(player);
        if (radius > maxRadius && maxRadius < Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "Max eraser radius is " + maxRadius + ".");
            return true;
        }
        EraserSettings settings = new EraserSettings(radius, BlockMask.parse(mask), surfaceOnly, selectionOnly, carveOnly, editBedrock);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createEraser(settings);
            equipBrushInMainHand(player, item);
            player.sendMessage(ChatColor.WHITE + "Created eraser brush and equipped it in your hand.");
        } else {
            toolManager.bindEraser(held, settings, false);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(ChatColor.WHITE + "Bound eraser brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + radius
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (settings.getMask() == null ? "any" : settings.getMask().summary())
                + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (editBedrock ? "on" : "protected"));
        return true;
    }

    private boolean handleBrushTerrain(Player player, TerrainBrushType type, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /brush " + type.commandName() + " <radius> [power] [bedrock:on|off]");
            return true;
        }
        int radius;
        int power = type == TerrainBrushType.NATURALIZE ? 3 : type.isCleanupMode() ? 2 : 1;
        boolean editBedrock = false;
        try {
            radius = Integer.parseInt(args[1]);
            if (args.length >= 3 && !args[2].contains(":")) {
                power = Integer.parseInt(args[2]);
            }
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Radius and " + type.powerLabel().toLowerCase(Locale.ROOT) + " must be numbers.");
            return true;
        }
        for (int i = 2; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "power", "strength", "iterations", "reach", "aggression" -> {
                    try {
                        power = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(ChatColor.RED + type.powerLabel() + " must be a number.");
                        return true;
                    }
                }
                case "bedrock" -> editBedrock = value.equals("on") || value.equals("true") || value.equals("edit");
                default -> {
                    player.sendMessage(ChatColor.RED + "Unknown brush option: " + key);
                    return true;
                }
            }
        }
        if (radius <= 0 || power <= 0) {
            player.sendMessage(ChatColor.RED + "Radius and " + type.powerLabel().toLowerCase(Locale.ROOT) + " must be greater than 0.");
            return true;
        }

        TerrainBrushSettings settings = new TerrainBrushSettings(type, radius, power, editBedrock);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createTerrainBrush(type, radius, power, editBedrock);
            equipBrushInMainHand(player, item);
            player.sendMessage(ChatColor.WHITE + "Created " + type.displayName() + " and equipped it in your hand.");
        } else {
            toolManager.bindTerrainBrush(held, settings, false);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(ChatColor.WHITE + "Bound " + type.displayName() + " to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + radius
                + ChatColor.DARK_GRAY + " | " + type.powerLabel().toLowerCase(Locale.ROOT) + ": " + ChatColor.WHITE + power
                + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (editBedrock ? "on" : "protected"));
        return true;
    }

    private boolean handleBrushMaskCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (args.length == 0) {
            ShapeBrushSettings shape = held == null ? null : toolManager.readShapeBrushSettings(held);
            PaintBrushSettings paint = held == null ? null : toolManager.readPaintBrushSettings(held);
            PatternBrushSettings pattern = held == null ? null : toolManager.readPatternBrushSettings(held);
            EraserSettings eraser = held == null ? null : toolManager.readEraserSettings(held);
            ToolType toolType = held == null ? null : toolManager.getToolType(held);
            BlockMask current = shape != null ? shape.mask() : paint != null ? paint.mask() : pattern != null ? pattern.mask() : eraser != null && toolType == ToolType.ERASER ? eraser.getMask() : null;
            if (shape == null && paint == null && pattern == null && toolType != ToolType.ERASER) {
                player.sendMessage(ChatColor.RED + "No brush bound to held item. Usage: /mask <blocks|none>");
                return true;
            }
            player.sendMessage(ChatColor.WHITE + "Mask: " + (current == null ? "any" : current.summary()));
            return true;
        }

        String raw = args[0];
        if (raw.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            raw = raw.substring("mask:".length());
        }
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold a brush to set its mask.");
            return true;
        }
        BlockMask mask = (raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("off")
                || raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("any") || raw.isBlank())
                ? null
                : BlockMask.parse(raw);
        boolean updated = toolManager.updateShapeBrushMask(held, mask)
                || toolManager.updatePaintBrushMask(held, mask)
                || toolManager.updatePatternBrushMask(held, mask)
                || toolManager.updateEraserMask(held, mask);
        if (!updated) {
            player.sendMessage(ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(ChatColor.WHITE + "Mask: " + (mask == null ? "any" : mask.summary()));
        return true;
    }

    private boolean handleBrushMaterialCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /material <block>");
            return true;
        }
        Material material = EditUtil.parseBlock(args[0]);
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Unknown block: " + args[0]);
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold a brush to set its material.");
            return true;
        }
        boolean updated = toolManager.updateShapeBrushMaterial(held, material)
                || toolManager.updatePaintBrushMaterial(held, material)
                || toolManager.updatePatternBrushMaterial(held, material);
        if (!updated) {
            player.sendMessage(ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(ChatColor.WHITE + "Material: " + material.name().toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean handleBrushSizeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /size <radius> [height]");
            return true;
        }
        int size;
        Integer height = null;
        try {
            size = Integer.parseInt(args[0]);
            if (args.length >= 2) {
                height = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Size must be a number.");
            return true;
        }
        if (size < 1 || size > 64) {
            player.sendMessage(ChatColor.RED + "Size must be between 1 and 64.");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold a brush to set its size.");
            return true;
        }
        boolean updatedAny = toolManager.updateShapeBrushSize(held, size, height)
                || toolManager.updatePaintBrushSize(held, size)
                || toolManager.updatePatternBrushSize(held, size)
                || toolManager.updateEraserSize(held, size)
                || toolManager.updateTerrainBrushSize(held, size, height);
        if (!updatedAny) {
            player.sendMessage(ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ShapeBrushSettings updated = toolManager.readShapeBrushSettings(held);
        TerrainBrushSettings terrain = toolManager.readTerrainBrushSettings(held);
        player.sendMessage(ChatColor.WHITE + "Size: " + size
                + (updated != null && (updated.type() == ShapeBrushType.CYL || updated.type() == ShapeBrushType.HCYL)
                        ? " | height: " + updated.height() : "")
                + (terrain != null && height != null && height > 0 ? " | " + terrain.type().powerLabel().toLowerCase(Locale.ROOT) + ": " + terrain.power() : ""));
        return true;
    }

    private boolean handleBrushDensityCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /density <0.0-1.0>");
            return true;
        }
        double density;
        try {
            density = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Density must be a number between 0.0 and 1.0.");
            return true;
        }
        if (density < 0.0 || density > 1.0) {
            player.sendMessage(ChatColor.RED + "Density must be between 0.0 and 1.0.");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold a paint or pattern brush to set its density.");
            return true;
        }
        if (!toolManager.updatePaintBrushDensity(held, density)
                && !toolManager.updatePatternBrushDensity(held, density)) {
            player.sendMessage(ChatColor.RED + "Held item has no paint or pattern brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        player.sendMessage(ChatColor.WHITE + "Density: " + String.format(Locale.ROOT, "%.2f", density));
        return true;
    }

    private boolean handleBrushInfo(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold an item to inspect its brush binding.");
            return true;
        }
        ShapeBrushSettings shape = toolManager.readShapeBrushSettings(held);
        if (shape != null) {
            player.sendMessage(ChatColor.GOLD + "Brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.DARK_GRAY + "Type: " + ChatColor.WHITE + shape.type().displayName()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + describeBrushShape(shape));
            player.sendMessage(ChatColor.DARK_GRAY + "Material: " + ChatColor.WHITE + shape.material().name().toLowerCase(Locale.ROOT)
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (shape.mask() == null ? "any" : shape.mask().summary())
                    + ChatColor.DARK_GRAY + " | anchor: " + ChatColor.WHITE + shape.anchorMode().name().toLowerCase(Locale.ROOT));
            return true;
        }
        if (toolManager.isClipboardBrush(held)) {
            player.sendMessage(ChatColor.GOLD + "Clipboard brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.DARK_GRAY + "Right-click a block to paste your clipboard there.");
            return true;
        }
        PaintBrushSettings paint = toolManager.readPaintBrushSettings(held);
        if (paint != null) {
            player.sendMessage(ChatColor.GOLD + "Paint brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.DARK_GRAY + "Block: " + ChatColor.WHITE + paint.material().name().toLowerCase(Locale.ROOT)
                    + ChatColor.DARK_GRAY + " | size: " + ChatColor.WHITE + paint.size()
                    + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", paint.density())
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (paint.mask() == null ? "any" : paint.mask().summary()));
            return true;
        }
        PatternBrushSettings pattern = toolManager.readPatternBrushSettings(held);
        if (pattern != null) {
            player.sendMessage(ChatColor.GOLD + pattern.mode().displayName() + " brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.DARK_GRAY + "Size: " + ChatColor.WHITE + pattern.size()
                    + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", pattern.density())
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (pattern.mask() == null ? "any" : pattern.mask().summary()));
            return true;
        }
        ToolType toolType = toolManager.getToolType(held);
        if (toolType == ToolType.ERASER) {
            EraserSettings eraser = toolManager.readEraserSettings(held);
            player.sendMessage(ChatColor.GOLD + "Eraser brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + eraser.getRadius()
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (eraser.getMask() == null ? "any" : eraser.getMask().summary())
                    + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (eraser.isEditBedrock() ? "on" : "protected"));
            return true;
        }
        TerrainBrushSettings terrain = toolManager.readTerrainBrushSettings(held);
        if (terrain != null) {
            player.sendMessage(ChatColor.GOLD + terrain.type().displayName() + " brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            player.sendMessage(ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + terrain.radius()
                    + ChatColor.DARK_GRAY + " | " + terrain.type().powerLabel().toLowerCase(Locale.ROOT) + ": " + ChatColor.WHITE + terrain.power()
                    + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (terrain.editBedrock() ? "on" : "protected"));
            return true;
        }
        player.sendMessage(ChatColor.GRAY + "No brush bound to held " + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        return true;
    }

    private boolean handleGenerate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        GenerateShapeRequest request;
        try {
            request = GeneratorCommandParser.parseGenerate(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        long volume = selection == null ? 0L : selection.getVolume();
        if (proceduralGenerationService.requiresConfirm(volume, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large generate selection. Re-run with confirm:true");
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generateShape(player, request), "generated shape");
        return true;
    }

    private boolean handleGenerateBiome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        GenerateBiomeRequest request;
        try {
            request = GeneratorCommandParser.parseGenerateBiome(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        long volume = selection == null ? 0L : selection.getVolume();
        if (proceduralGenerationService.requiresConfirm(volume, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large biome generate selection. Re-run with confirm:true");
            return true;
        }
        GeneratorResult result = proceduralGenerationService.generateBiome(player, request);
        sendGeneratorResult(sender, result, "generated biome shape");
        return true;
    }

    private boolean handleForestGen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        ForestGenRequest request;
        try {
            request = GeneratorCommandParser.parseForest(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        long area = (long) (request.size() * 2 + 1) * (request.size() * 2 + 1);
        if (proceduralGenerationService.requiresConfirm(area * Math.max(1L, Math.round(request.density())), shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large forest generation. Re-run with confirm:true");
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generateForest(player, request), "forest");
        return true;
    }

    private boolean handlePumpkins(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        PumpkinPatchRequest request;
        try {
            request = GeneratorCommandParser.parsePumpkins(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        long area = (long) (request.size() * 2 + 1) * (request.size() * 2 + 1);
        if (proceduralGenerationService.requiresConfirm(area, shouldBypassConfirm(player, request.confirm()))) {
            sender.sendMessage(ChatColor.RED + "Large pumpkin generation. Re-run with confirm:true");
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generatePumpkins(player, request), "pumpkin patch");
        return true;
    }

    private boolean handleWalls(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 1) {
            syntaxError(sender, "/walls <BLOCK> [mask:<BLOCKS>] [confirm:true]");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        Material material = EditUtil.parseBlock(args[0]);
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Unknown block: " + args[0]);
            return true;
        }
        String maskValue = null;
        boolean confirm = false;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (token.equalsIgnoreCase("confirm:true")) {
                confirm = true;
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                maskValue = token.substring(5);
            }
        }
        if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            sender.sendMessage(ChatColor.RED + "Large walls operation. Re-run with confirm:true");
            return true;
        }
        BlockDistribution distribution = BlockDistribution.parse(material.name().toLowerCase(Locale.ROOT));
        int changed = editService.makeWalls(player, selection, distribution, BlockMask.parse(maskValue));
        recentEditTrailService.record(player.getUniqueId(), "walls " + material.name().toLowerCase(Locale.ROOT));
        sender.sendMessage(ChatColor.WHITE + "Created walls and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleOverlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 1) {
            syntaxError(sender, "/overlay <BLOCK> [mask:<BLOCKS>] [confirm:true]");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        Material material = EditUtil.parseBlock(args[0]);
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Unknown block: " + args[0]);
            return true;
        }
        String maskValue = null;
        boolean confirm = false;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (token.equalsIgnoreCase("confirm:true")) {
                confirm = true;
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                maskValue = token.substring(5);
            }
        }
        if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            sender.sendMessage(ChatColor.RED + "Large overlay operation. Re-run with confirm:true");
            return true;
        }
        int changed = editService.overlaySelection(player, selection, material, BlockMask.parse(maskValue));
        recentEditTrailService.record(player.getUniqueId(), "overlay " + material.name().toLowerCase(Locale.ROOT));
        sender.sendMessage(ChatColor.WHITE + "Created overlay and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleSmooth(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        int iterations = 1;
        boolean confirm = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("confirm:true")) {
                confirm = true;
                continue;
            }
            if (arg.toLowerCase(Locale.ROOT).startsWith("iterations:")) {
                try {
                    iterations = Integer.parseInt(arg.substring("iterations:".length()));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Iterations must be a number.");
                    return true;
                }
                continue;
            }
            try {
                iterations = Integer.parseInt(arg);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Usage: /smooth [iterations|iterations:<n>] [confirm:true]");
                return true;
            }
        }
        if (iterations <= 0) {
            sender.sendMessage(ChatColor.RED + "Iterations must be greater than 0.");
            return true;
        }
        if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            sender.sendMessage(ChatColor.RED + "Large smooth operation. Re-run with confirm:true");
            return true;
        }
        int changed = editService.smoothSelection(player, selection, iterations);
        recentEditTrailService.record(player.getUniqueId(), "smooth " + iterations);
        sender.sendMessage(ChatColor.WHITE + "Smoothed selection with " + iterations + " iteration(s) and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleNaturalize(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        int depth = 3;
        boolean editBedrock = false;
        boolean confirm = false;
        for (String arg : args) {
            String token = arg.toLowerCase(Locale.ROOT);
            if (token.equals("confirm:true")) {
                confirm = true;
                continue;
            }
            if (token.startsWith("depth:") || token.startsWith("power:")) {
                String value = arg.substring(arg.indexOf(':') + 1);
                try {
                    depth = Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Depth must be a number.");
                    return true;
                }
                continue;
            }
            if (token.startsWith("bedrock:")) {
                String value = token.substring("bedrock:".length());
                editBedrock = value.equals("on") || value.equals("true") || value.equals("edit");
                continue;
            }
            try {
                depth = Integer.parseInt(arg);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Usage: /naturalize [depth:<n>] [bedrock:on|off] [confirm:true]");
                return true;
            }
        }

        if (depth <= 0) {
            sender.sendMessage(ChatColor.RED + "Depth must be greater than 0.");
            return true;
        }
        if (EditUtil.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            sender.sendMessage(ChatColor.RED + "Large naturalize operation. Re-run with confirm:true");
            return true;
        }

        List<BlockChange> changes = naturalizeService.naturalizeSelection(player, selection, depth, editBedrock);
        int changed = changes.size();
        historyService.record(player.getUniqueId(), changes);
        recentEditTrailService.record(player.getUniqueId(), "naturalize depth " + depth);
        sender.sendMessage(ChatColor.WHITE + "Naturalized selection and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleRotate(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        try {
            ClipboardRotateRequest request = ClipboardCommandParser.parseRotate(args);
            if (request.live()) {
                Selection selection = selectionManager.get(player.getUniqueId());
                if (selection == null || !selection.isComplete()) {
                    sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
                    return true;
                }
                long totalVolume = selection.getVolume() * 2L;
                if (totalVolume > EditUtil.CONFIRM_VOLUME) {
                    sender.sendMessage(ChatColor.RED + "Large live rotate. Re-run after shrinking the selection.");
                    return true;
                }
                int changed = editService.rotateSelectionLive(player, selection, request.rotation());
                recentEditTrailService.record(player.getUniqueId(), "rotate live " + request.rotation());
                sender.sendMessage(ChatColor.WHITE + "Selection rotated " + request.rotation() + " degrees live and changed " + changed + " blocks.");
                return true;
            }
            Clipboard clipboard = clipboardManager.get(player.getUniqueId());
            if (clipboard == null) {
                sender.sendMessage(ChatColor.RED + "Clipboard is empty.");
                return true;
            }
            Clipboard rotated = ClipboardTransforms.rotateY(clipboard, request.rotation());
            clipboardManager.set(player.getUniqueId(), rotated);
            recentEditTrailService.record(player.getUniqueId(), "rotate " + request.rotation());
            sender.sendMessage(ChatColor.WHITE + "Clipboard rotated " + request.rotation() + " degrees.");
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        return true;
    }

    private boolean handleFlip(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            sender.sendMessage(ChatColor.RED + "Clipboard is empty.");
            return true;
        }

        try {
            ClipboardFlipRequest request = ClipboardCommandParser.parseFlip(player, args);
            Clipboard flipped = ClipboardTransforms.flip(clipboard, request.axis());
            clipboardManager.set(player.getUniqueId(), flipped);
            recentEditTrailService.record(player.getUniqueId(), "flip " + request.axis());
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        sender.sendMessage(ChatColor.WHITE + "Clipboard flipped.");
        return true;
    }

    private boolean handleStack(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        ClipboardStackRequest request;
        try {
            request = ClipboardCommandParser.parseStack(args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        long totalVolume = selection.getVolume() * Math.max(1, request.count());
        if (totalVolume > EditUtil.CONFIRM_VOLUME && !shouldBypassConfirm(player, request.confirm())) {
            sender.sendMessage(ChatColor.RED + "Large stack. Re-run with confirm:true");
            return true;
        }

        int changed;
        if (request.random()) {
            changed = editService.stackSelectionRandom(player, selection, request.count(), request.spreadX(), request.spreadY(), request.spreadZ(), request.ignoreAir());
            recentEditTrailService.record(player.getUniqueId(), "stack rnd " + request.count());
            sender.sendMessage(ChatColor.WHITE + "Random-stacked " + request.count() + " copy/copies and changed " + changed + " blocks.");
            return true;
        }

        int[] direction = DirectionUtil.resolve(player, request.direction());
        changed = editService.stackSelection(player, selection, request.count(), direction, request.ignoreAir());
        recentEditTrailService.record(player.getUniqueId(), "stack " + request.count() + " " + request.direction());
        sender.sendMessage(ChatColor.WHITE + "Stacked " + request.count() + " copy/copies and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleUndo(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        int steps = parseSteps(args);
        int undone = historyService.undo(player, steps);
        recentEditTrailService.record(player.getUniqueId(), "undo " + undone);
        sender.sendMessage(ChatColor.WHITE + "Undid " + undone + " action(s).");
        return true;
    }

    private boolean handleOops(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        int undone = historyService.undo(player, parseSteps(args));
        recentEditTrailService.record(player.getUniqueId(), "undo " + undone);
        sender.sendMessage(ChatColor.WHITE + "Undid " + undone + " action(s).");
        if (undone > 0) {
            Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " made an oopsie!!!");
        }
        return true;
    }

    private boolean handleRedo(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        int steps = parseSteps(args);
        int redone = historyService.redo(player, steps);
        recentEditTrailService.record(player.getUniqueId(), "redo " + redone);
        sender.sendMessage(ChatColor.WHITE + "Redid " + redone + " action(s).");
        return true;
    }

    private boolean handleExpand(CommandSender sender, String[] args) {
        return handleResize(sender, args, true);
    }

    private boolean handleContract(CommandSender sender, String[] args) {
        return handleResize(sender, args, false);
    }

    private boolean handleResize(CommandSender sender, String[] args, boolean expand) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        String verb = expand ? "expand" : "contract";
        SelectionResizeRequest request;
        try {
            request = SelectionCommandParser.parseResize(verb, args);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }

        int[] direction = DirectionUtil.resolve(player, request.direction());
        Selection updated = expand
                ? (request.allDirections()
                ? selectionService.expandSelectionAll(selection, request.amount())
                : selectionService.expandSelection(selection, request.amount(), direction))
                : (request.allDirections()
                ? selectionService.contractSelectionAll(selection, request.amount())
                : selectionService.contractSelection(selection, request.amount(), direction));

        if (updated == null || !updated.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection could not be updated.");
            return true;
        }

        selectionService.applySelection(player.getUniqueId(), updated);
        String directionLabel = request.allDirections() ? "in all directions" : request.direction();
        sender.sendMessage(ChatColor.WHITE + (expand ? "Expanded" : "Contracted") + " selection " + request.amount() + " block(s) " + directionLabel + ".");
        return true;
    }

    private boolean handleBiomeInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        World world = selection.getPos1().getWorld();
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Selection world is unavailable.");
            return true;
        }

        Map<Biome, Integer> counts = new HashMap<>();
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Biome biome = world.getBiome(x, y, z);
                    if (biome == null) {
                        continue;
                    }
                    counts.merge(biome, 1, Integer::sum);
                }
            }
        }

        if (counts.isEmpty()) {
            sender.sendMessage(listMenuConfigService.format(
                    BIOME_INFO_MENU,
                    "empty",
                    "&cNo biome data found for that selection.",
                    ListMenuConfigService.tokens()
            ));
            return true;
        }

        long total = selection.getVolume();
        Map<String, String> baseTokens = ListMenuConfigService.tokens(
                "min", selection.getMinX() + "," + selection.getMinY() + "," + selection.getMinZ(),
                "max", selection.getMaxX() + "," + selection.getMaxY() + "," + selection.getMaxZ(),
                "unique", Integer.toString(counts.size()),
                "total", Long.toString(total)
        );
        sendMenuLines(sender, BIOME_INFO_MENU, "header", List.of(
                "&6Bayzyl&a - Builder tools",
                "&6Biome Info &8[&f{min}&8 -> &f{max}&8]"
        ), baseTokens);
        sendMenuLine(sender, BIOME_INFO_MENU, "summary",
                "&fUnique biomes: &6{unique}&8 | &fTotal blocks: &6{total}",
                baseTokens);
        Biome centerBiome = world.getBiome(
                (selection.getMinX() + selection.getMaxX()) / 2,
                (selection.getMinY() + selection.getMaxY()) / 2,
                (selection.getMinZ() + selection.getMaxZ()) / 2
        );
        if (centerBiome != null) {
            sendMenuLine(sender, BIOME_INFO_MENU, "center",
                    "&fCenter: &b{center}",
                    ListMenuConfigService.tokens("center", centerBiome.name().toLowerCase(Locale.ROOT)));
        }
        Biome centerColumnBiome = world.getBiome(
                (selection.getMinX() + selection.getMaxX()) / 2,
                (selection.getMinZ() + selection.getMaxZ()) / 2
        );
        if (centerColumnBiome != null) {
            sendMenuLine(sender, BIOME_INFO_MENU, "column-center",
                    "&fColumn center: &b{column_center}",
                    ListMenuConfigService.tokens("column_center", centerColumnBiome.name().toLowerCase(Locale.ROOT)));
        }
        sendMenuLine(sender, BIOME_INFO_MENU, "divider", "&8--------------", ListMenuConfigService.tokens());
        counts.entrySet().stream()
                .sorted(Map.Entry.<Biome, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().name()))
                .forEach(entry -> {
                    double percent = total <= 0 ? 0.0D : (entry.getValue() * 100.0D) / total;
                    sendMenuLine(sender, BIOME_INFO_MENU, "row",
                            "{color}{biome}&f -> &6{count}&8 ({percent})",
                            ListMenuConfigService.tokens(
                                    "color", getBiomeColor(entry.getKey()).toString(),
                                    "biome", entry.getKey().name().toLowerCase(Locale.ROOT),
                                    "count", Integer.toString(entry.getValue()),
                                    "percent", String.format(Locale.ROOT, "%.1f%%", percent)
                            ));
                });
        return true;
    }

    private int parseSteps(String[] args) {
        if (args.length == 0) {
            return 1;
        }
        String token = args[0];
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            if (parts.length == 2 && parts[0].equalsIgnoreCase("steps")) {
                token = parts[1];
            }
        }
        try {
            int value = Integer.parseInt(token);
            return Math.max(1, value);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private OptionState parseOptions(String[] args, int startIndex) {
        OptionState options = new OptionState();
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i];
            if (token.startsWith("-") && token.length() > 1) {
                for (int flagIndex = 1; flagIndex < token.length(); flagIndex++) {
                    switch (Character.toLowerCase(token.charAt(flagIndex))) {
                        case 'a':
                            options.ignoreAir = true;
                            break;
                        case 'o':
                            options.pasteAtOriginal = true;
                            break;
                        case 'p':
                            options.pasteAtOriginal = false;
                            break;
                        case 's':
                            options.selectAfterPaste = true;
                            break;
                        case 'n':
                            options.previewOnly = true;
                            break;
                        default:
                            break;
                    }
                }
                continue;
            }
            if (!token.contains(":")) {
                if (options.mask == null) {
                    options.mask = token;
                }
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
            switch (key) {
                case "mask":
                    options.mask = value;
                    break;
                case "if":
                    options.ifMode = value;
                    break;
                case "confirm":
                    options.confirm = value.equals("true");
                    break;
                case "rotation":
                    try {
                        options.rotation = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case "at":
                    options.at = value;
                    break;
                default:
                    break;
            }
        }
        return options;
    }

    private static final class OptionState {
        private String mask;
        private String ifMode = "any";
        private boolean confirm = false;
        private int rotation = 0;
        private String at = "here";
        private boolean ignoreAir = false;
        private boolean pasteAtOriginal = false;
        private boolean selectAfterPaste = false;
        private boolean previewOnly = false;
    }

    private boolean handleSelectNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /select cube <size> at:<player|target>");
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl select viz <on|off> [intensity:low|medium|high] [grid:on|off|auto] [consistency:1-10] [color:red|green|yellow|blue|purple|bayzyl|#RRGGBB]");
            return true;
        }

        if (args[1].equalsIgnoreCase("cube")) {
            return handleSelectCube(player, args);
        }

        if (!args[1].equalsIgnoreCase("viz")) {
            sender.sendMessage(ChatColor.RED + "Unknown /bzl select subcommand.");
            return true;
        }

        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        if (args.length >= 3) {
            String toggle = args[2].toLowerCase(Locale.ROOT);
            if (toggle.equals("on")) {
                settings.setEnabled(true);
            } else if (toggle.equals("off")) {
                settings.setEnabled(false);
            }
        }
        for (int i = 3; i < args.length; i++) {
            String token = args[i];
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
            switch (key) {
                case "intensity":
                    if (value.equals("low")) {
                        settings.setIntensity(VisualizationSettings.Intensity.LOW);
                    } else if (value.equals("medium")) {
                        settings.setIntensity(VisualizationSettings.Intensity.MEDIUM);
                    } else if (value.equals("high")) {
                        settings.setIntensity(VisualizationSettings.Intensity.HIGH);
                    }
                    break;
                case "grid":
                    if (value.equals("on")) {
                        settings.setGridMode(VisualizationSettings.GridMode.ON);
                    } else if (value.equals("off")) {
                        settings.setGridMode(VisualizationSettings.GridMode.OFF);
                    } else if (value.equals("auto")) {
                        settings.setGridMode(VisualizationSettings.GridMode.AUTO);
                    }
                    break;
                case "consistency":
                    try {
                        settings.setConsistency(Integer.parseInt(value));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Consistency must be 1-10.");
                        return true;
                    }
                    break;
                case "color":
                    org.bukkit.Color color = parseColor(value);
                    if (color == null) {
                        sender.sendMessage(ChatColor.RED + "Unknown color. Use red|green|yellow|blue|purple|bayzyl|#RRGGBB.");
                        return true;
                    }
                    settings.setColor(color);
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown option: " + key);
                    return true;
            }
        }
        savePlayerRuntime(player);
        sender.sendMessage(ChatColor.WHITE + "Selection visualization updated.");
        return true;
    }

    private boolean handleSelectCube(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /select cube <size> at:<player|target>");
            return true;
        }

        int size;
        try {
            size = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Cube size must be a number.");
            return true;
        }
        if (size <= 0) {
            player.sendMessage(ChatColor.RED + "Cube size must be greater than 0.");
            return true;
        }

        String anchor = "player";
        for (int i = 3; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            if (parts[0].equals("at")) {
                anchor = parts.length > 1 ? parts[1] : anchor;
            }
        }

        Location center;
        if (anchor.equals("target")) {
            var result = player.rayTraceBlocks(120);
            if (result == null || result.getHitBlock() == null) {
                player.sendMessage(ChatColor.RED + "No target block in range.");
                return true;
            }
            center = result.getHitBlock().getLocation();
        } else if (anchor.equals("player")) {
            center = player.getLocation().getBlock().getLocation();
        } else {
            player.sendMessage(ChatColor.RED + "Unknown anchor. Use at:player or at:target.");
            return true;
        }

        int lower = (size - 1) / 2;
        int upper = size / 2;
        Location pos1 = center.clone().add(-lower, -lower, -lower);
        Location pos2 = center.clone().add(upper, upper, upper);
        selectionManager.setCuboid(player.getUniqueId(), pos1, pos2);

        if (size % 2 == 0) {
            player.sendMessage(ChatColor.WHITE + "Selected " + size + "x" + size + "x" + size + " cube at " + anchor + " anchor."
                    + ChatColor.DARK_GRAY + " Even sizes are center-biased by one block.");
            return true;
        }

        player.sendMessage(ChatColor.WHITE + "Selected " + size + "x" + size + "x" + size + " cube at " + anchor + " anchor.");
        return true;
    }

    private boolean handleSelectionParticlesNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl selectionparticles color <bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB>");
            return true;
        }

        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on":
                settings.setEnabled(true);
                sender.sendMessage(ChatColor.WHITE + "Selection particles enabled.");
                savePlayerRuntime(player);
                return true;
            case "off":
                settings.setEnabled(false);
                sender.sendMessage(ChatColor.WHITE + "Selection particles disabled.");
                savePlayerRuntime(player);
                return true;
            case "color":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bzl selectionparticles color <bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB>");
                    return true;
                }
                org.bukkit.Color color = parseColor(args[2].toLowerCase(Locale.ROOT));
                if (color == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown color. Use bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB.");
                    return true;
                }
                settings.setEnabled(true);
                settings.setColor(color);
                sender.sendMessage(ChatColor.WHITE + "Selection particle color updated.");
                savePlayerRuntime(player);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown selection particle setting.");
                return true;
        }
    }

    private boolean handleToolNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl tool <smooth|raise|lower|flatten> <radius> [power] [bedrock:on|off]");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        TerrainBrushType brushType = TerrainBrushType.parse(sub);
        if (brushType == null || !brushType.isTerrainMode()) {
            sender.sendMessage(ChatColor.YELLOW + "Not implemented yet: /bzl tool " + sub);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /bzl tool " + brushType.commandName() + " <radius> [power] [bedrock:on|off]");
            return true;
        }
        int radius;
        int power = 1;
        boolean editBedrock = false;
        try {
            radius = Integer.parseInt(args[2]);
            if (args.length >= 4 && !args[3].contains(":")) {
                power = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Radius and power must be numbers.");
            return true;
        }
        for (int i = 3; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            if (parts[0].equals("bedrock")) {
                String value = parts.length > 1 ? parts[1] : "";
                editBedrock = value.equals("on") || value.equals("true") || value.equals("edit");
            }
        }
        if (radius <= 0 || power <= 0) {
            sender.sendMessage(ChatColor.RED + "Radius and power must be greater than 0.");
            return true;
        }
        player.getInventory().addItem(toolManager.createTerrainBrush(brushType, radius, power, editBedrock));
        sender.sendMessage(ChatColor.WHITE + brushType.displayName() + " brush added: radius " + radius
                + ", " + brushType.powerLabel().toLowerCase() + " " + power
                + ", bedrock " + (editBedrock ? "on" : "protected") + ".");
        return true;
    }

    private boolean handleCleanupNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|brush> ...");
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("brush")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /bzl cleanup brush <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> <radius> [power]");
                return true;
            }
            TerrainBrushType type = TerrainBrushType.parse(args[2]);
            if (type == null || !type.isCleanupMode()) {
                sender.sendMessage(ChatColor.RED + "Unknown cleanup brush type: " + args[2]);
                return true;
            }
            int radius;
            int power = 2;
            try {
                radius = Integer.parseInt(args[3]);
                if (args.length >= 5 && !args[4].contains(":")) {
                    power = Integer.parseInt(args[4]);
                }
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Radius and power must be numbers.");
                return true;
            }
            if (radius <= 0 || power <= 0) {
                sender.sendMessage(ChatColor.RED + "Radius and power must be greater than 0.");
                return true;
            }
            player.getInventory().addItem(toolManager.createTerrainBrush(type, radius, power, false));
            sender.sendMessage(ChatColor.GOLD + type.displayName() + ChatColor.WHITE + " created " + ChatColor.DARK_GRAY + "[r:" + ChatColor.AQUA + radius + ChatColor.DARK_GRAY + " " + type.powerLabel().toLowerCase() + ":" + ChatColor.AQUA + power + ChatColor.DARK_GRAY + "]");
            return true;
        }

        TerrainBrushType type = TerrainBrushType.parse(sub);
        if (type == null || !type.isCleanupMode()) {
            sender.sendMessage(ChatColor.RED + "Unknown cleanup type: " + sub);
            return true;
        }

        boolean confirm = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("confirm:true")) {
                confirm = true;
            }
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            sender.sendMessage(ChatColor.RED + "Large cleanup selection. Re-run with confirm:true");
            return true;
        }

        List<BlockChange> changes = cleanupService.cleanupSelection(player, selection, type);
        historyService.record(player.getUniqueId(), changes);
        sendHeader(sender);
        sender.sendMessage(ChatColor.GOLD + type.displayName() + ChatColor.DARK_GRAY + " cleanup completed");
        sender.sendMessage(ChatColor.WHITE + "Cleaned " + ChatColor.GOLD + changes.size() + ChatColor.WHITE + " blocks.");
        if (changes.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "No matching targets found in the current selection.");
        } else {
            sender.sendMessage(ChatColor.DARK_GRAY + "Tip: use /bzl cleanup brush " + type.commandName() + " <radius> for localized passes.");
        }
        return true;
    }

    private boolean handlePaletteNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl palette <analyze|swap> ...");
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            sender.sendMessage(ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        if (sub.equals("analyze")) {
            PaletteService.AnalysisResult analysis = paletteService.analyzeSelection(player, selection);
            if (analysis == null) {
                return true;
            }
            sendPaletteAnalysis(player, selection, analysis);
            return true;
        }

        if (sub.equals("swap")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /bzl palette swap <from> <to> [confirm:true]");
                return true;
            }
            Material from = EditUtil.parseBlock(args[2]);
            Material to = EditUtil.parseBlock(args[3]);
            if (from == null || to == null) {
                sender.sendMessage(ChatColor.RED + "Palette swap needs valid block names.");
                return true;
            }
            boolean confirm = false;
            for (int i = 4; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("confirm:true")) {
                    confirm = true;
                }
            }
            if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
                sender.sendMessage(ChatColor.RED + "Large palette swap. Re-run with confirm:true");
                return true;
            }
            List<BlockChange> changes = paletteService.swapSelection(player, selection, from, to);
            historyService.record(player.getUniqueId(), changes);
            sendHeader(sender);
            sender.sendMessage(ChatColor.WHITE + "Palette swap "
                    + ChatColor.WHITE + from.name().toLowerCase(Locale.ROOT)
                    + ChatColor.DARK_GRAY + " -> "
                    + ChatColor.WHITE + to.name().toLowerCase(Locale.ROOT)
                    + ChatColor.WHITE + " changed "
                    + ChatColor.GOLD + changes.size()
                    + ChatColor.WHITE + " blocks.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown palette command: " + sub);
        return true;
    }

    private boolean handleDebugNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl debug entities <on|off|status>");
            return true;
        }
        String topic = args[1].toLowerCase(Locale.ROOT);
        if (!topic.equals("entities")) {
            sender.sendMessage(ChatColor.RED + "Unknown debug topic: " + topic);
            return true;
        }
        String mode = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (mode) {
            case "on" -> {
                EditUtil.setEntityDebug(player.getUniqueId(), true);
                sender.sendMessage(ChatColor.GREEN + "Entity debug logging " + ChatColor.WHITE + "enabled" + ChatColor.GREEN + ".");
            }
            case "off" -> {
                EditUtil.setEntityDebug(player.getUniqueId(), false);
                sender.sendMessage(ChatColor.GREEN + "Entity debug logging " + ChatColor.WHITE + "disabled" + ChatColor.GREEN + ".");
            }
            case "status" -> {
                boolean enabled = EditUtil.isEntityDebug(player.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Entity debug logging is " + ChatColor.WHITE + (enabled ? "ON" : "OFF") + ChatColor.GREEN + ".");
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /bzl debug entities <on|off|status>");
        }
        return true;
    }

    private boolean handleEnvNamespace(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: /bzl env <history> ...");
            return true;
        }
        if (args[1].equalsIgnoreCase("history")) {
            if (args.length == 2) {
                sender.sendMessage(ChatColor.WHITE + "Bayzyl undo history is set to " + historyService.getMaxHistory() + " actions.");
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "History count must be a number.");
                return true;
            }
            if (count < 1) {
                sender.sendMessage(ChatColor.RED + "History count must be at least 1.");
                return true;
            }
            historyService.setMaxHistory(count);
            sender.sendMessage(ChatColor.WHITE + "Bayzyl undo history set to " + historyService.getMaxHistory() + " actions.");
            if (count > 50) {
                sender.sendMessage(ChatColor.YELLOW + "Warning: history above 50 may increase memory use or cause issues on large edits.");
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("nudge")) {
            String[] remapped = new String[Math.max(1, args.length - 1)];
            remapped[0] = "nudge";
            if (args.length > 2) {
                System.arraycopy(args, 2, remapped, 1, args.length - 2);
            }
            return handleNudgeNamespace(sender, remapped);
        }
        sender.sendMessage(ChatColor.YELLOW + "Not implemented yet: /bzl env " + args[1]);
        return true;
    }

    private boolean handleNudgeNamespace(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("help")) {
            sendHelpPage(sender, NUDGE_HELP_PAGE);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        NudgeSettings current = nudgeSettingsService.get(player);
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("status"))) {
            sender.sendMessage(ChatColor.WHITE + "Nudge settings: invert=" + current.inverted()
                    + ", step=" + current.step()
                    + ", vertical=" + current.verticalMode().name().toLowerCase(Locale.ROOT));
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        switch (key) {
            case "invert" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bzl nudge invert <on|off>");
                    return true;
                }
                    boolean inverted = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
                    nudgeSettingsService.set(player, new NudgeSettings(inverted, current.step(), current.verticalMode()));
                    savePlayerRuntime(player);
                    sender.sendMessage(ChatColor.WHITE + "Nudge invert set to " + inverted + ".");
                    return true;
                }
            case "step" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bzl nudge step <amount>");
                    return true;
                }
                int step;
                try {
                    step = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Step must be a number.");
                    return true;
                }
                if (step < 1 || step > 16) {
                    sender.sendMessage(ChatColor.RED + "Step must be between 1 and 16.");
                    return true;
                    }
                    nudgeSettingsService.set(player, new NudgeSettings(current.inverted(), step, current.verticalMode()));
                    savePlayerRuntime(player);
                    sender.sendMessage(ChatColor.WHITE + "Nudge step set to " + step + ".");
                    return true;
                }
            case "vertical" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /bzl nudge vertical <jump|look|off>");
                    return true;
                }
                NudgeSettings.VerticalMode mode;
                try {
                    mode = NudgeSettings.VerticalMode.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(ChatColor.RED + "Vertical mode must be jump, look, or off.");
                    return true;
                    }
                    nudgeSettingsService.set(player, new NudgeSettings(current.inverted(), current.step(), mode));
                    savePlayerRuntime(player);
                    sender.sendMessage(ChatColor.WHITE + "Nudge vertical mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
                    return true;
                }
                case "reset" -> {
                    nudgeSettingsService.reset(player);
                    savePlayerRuntime(player);
                    sender.sendMessage(ChatColor.WHITE + "Nudge settings reset to defaults.");
                    return true;
                }
            default -> {
                syntaxError(sender, "/bzl nudge <status|invert|step|vertical|reset>");
                return true;
            }
        }
    }

    private boolean handleNudgeRoot(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelpPage(sender, NUDGE_HELP_PAGE);
            return true;
        }
        String[] remapped = new String[Math.max(1, args.length + 1)];
        remapped[0] = "nudge";
        if (args.length > 0) {
            System.arraycopy(args, 0, remapped, 1, args.length);
        }
        return handleNudgeNamespace(sender, remapped);
    }

    private boolean handleBzlToggle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bayzyl.toggle") && !sender.hasPermission("bayzyl.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use Bayzyl toggles.");
            return true;
        }
        if (args.length == 0) {
            syntaxError(sender, "/bzltoggle <admin|ramalert> ...");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            boolean enabled = adminModeService.toggle(player);
            savePlayerRuntime(player);
            if (enabled) {
                sender.sendMessage(ChatColor.YELLOW + "Warning: Bayzyl admin mode is on. Large commands will skip confirm:true while this is enabled.");
            } else {
                sender.sendMessage(ChatColor.WHITE + "Bayzyl admin mode disabled. Large commands require confirm:true again.");
            }
            return true;
        }

        if (sub.equals("ramalert")) {
            return handleRamAlertToggle(sender, args);
        }

        syntaxError(sender, "/bzltoggle <admin|ramalert> ...");
        return true;
    }

    private boolean handleRamAlertRoot(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bayzyl.toggle") && !sender.hasPermission("bayzyl.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use RAM alert controls.");
            return true;
        }
        return handleRamAlertCommand(sender, args, "/ramalert");
    }

    private boolean handleRamAlertToggle(CommandSender sender, String[] args) {
        String[] remapped = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, remapped, 0, args.length - 1);
        }
        return handleRamAlertCommand(sender, remapped, "/bzltoggle ramalert");
    }

    private boolean handleRamAlertCommand(CommandSender sender, String[] args, String usageBase) {
        RamAlertSettings current = ramAlertService.getSettings();
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            ramAlertService.statusLines().forEach(sender::sendMessage);
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("help")) {
            ramAlertService.helpLines().forEach(sender::sendMessage);
            sender.sendMessage(listMenuConfigService.format(
                    RAMALERT_HELP_MENU,
                    "syntax",
                    "&aSyntax: &6{usage} <on|off|status|help> [threshold:<percent>] [interval:<seconds>] [cooldown:<seconds>]",
                    ListMenuConfigService.tokens("usage", usageBase)
            ));
            return true;
        }
        if (mode.equals("off")) {
            ramAlertService.update(new RamAlertSettings(false, current.thresholdPercent(), current.intervalSeconds(), current.cooldownSeconds()));
            runtimePreferencesService.saveGlobal(ramAlertService);
            sender.sendMessage(ChatColor.GREEN + "RAM alert disabled.");
            return true;
        }
        if (!mode.equals("on")) {
            syntaxError(sender, usageBase + " <on|off|status|help> [threshold:<percent>] [interval:<seconds>] [cooldown:<seconds>]");
            return true;
        }

        int threshold = current.thresholdPercent();
        int interval = current.intervalSeconds();
        int cooldown = current.cooldownSeconds();
        for (int i = 1; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "threshold" -> {
                    try {
                        threshold = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        syntaxError(sender, usageBase + " on threshold:<percent> interval:<30s|2m> cooldown:<120s|2m>");
                        return true;
                    }
                }
                case "interval" -> {
                    Integer parsed = parseDurationSeconds(value);
                    if (parsed == null) {
                        syntaxError(sender, usageBase + " on threshold:<percent> interval:<30s|2m> cooldown:<120s|2m>");
                        return true;
                    }
                    interval = parsed;
                }
                case "cooldown" -> {
                    Integer parsed = parseDurationSeconds(value);
                    if (parsed == null) {
                        syntaxError(sender, usageBase + " on threshold:<percent> interval:<30s|2m> cooldown:<120s|2m>");
                        return true;
                    }
                    cooldown = parsed;
                }
                default -> {
                    syntaxError(sender, usageBase + " on threshold:<percent> interval:<30s|2m> cooldown:<120s|2m>");
                    return true;
                }
            }
        }

        if (threshold < 1 || threshold > 99) {
            sender.sendMessage(ChatColor.RED + "Threshold must be between 1 and 99. Example: " + ChatColor.GOLD + usageBase + " on threshold:85");
            return true;
        }
        if (interval < 5) {
            sender.sendMessage(ChatColor.RED + "Interval must be at least 5 seconds. Example: " + ChatColor.GOLD + usageBase + " on interval:30s");
            return true;
        }
        if (cooldown < 5) {
            sender.sendMessage(ChatColor.RED + "Cooldown must be at least 5 seconds. Example: " + ChatColor.GOLD + usageBase + " on cooldown:2m");
            return true;
        }

        ramAlertService.update(new RamAlertSettings(true, threshold, interval, cooldown));
        runtimePreferencesService.saveGlobal(ramAlertService);
        sender.sendMessage(ChatColor.GREEN + "RAM alert enabled: threshold " + ChatColor.GOLD + threshold + "%"
                + ChatColor.GREEN + ", interval " + ChatColor.GOLD + interval + "s"
                + ChatColor.GREEN + ", cooldown " + ChatColor.GOLD + cooldown + "s.");
        sender.sendMessage(ChatColor.GOLD + "Use " + usageBase + " status" + ChatColor.GREEN + " to inspect current load and top memory consumers.");
        return true;
    }

    private boolean handleTabMenuRoot(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelpPage(sender, TABMENU_HELP_PAGE);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleTabMenuCommand(player, args);
    }

    private boolean handleProfileRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleProfileCommand(player, args, "/profile");
    }

    private boolean handleKitHelpRoot(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelpPage(sender, KITS_HELP_PAGE);
            return true;
        }
        String[] remapped = new String[args.length + 1];
        remapped[0] = "help";
        System.arraycopy(args, 0, remapped, 1, args.length);
        return handleBzl(sender, remapped);
    }

    private boolean handleKitListRoot(CommandSender sender, String[] args) {
        return handleKitList(sender, args, "/kitlist");
    }

    private boolean handleKitUpdateRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitUpdateCommand(player, args, "/kitupdate");
    }

    private boolean handleKitConfirmRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitConfirmCommand(player, args, "/kitconfirm");
    }

    private boolean handleKitRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitCommand(player, args, "/kit");
    }

    private boolean handleKitMakeRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitMakeCommand(player, args, "/kitmake");
    }

    private boolean handleKitShortcutRoot(CommandSender sender, String kitName, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitShortcut(player, kitName, args, "/" + kitName);
    }

    private boolean handleTabMenuCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendTabMenuStatus(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            tabMenuSettingsService.setAllEnabled(player.getUniqueId(), false);
            savePlayerRuntime(player);
            tabInfoPanelService.refreshPlayer(player);
            player.sendMessage(ChatColor.WHITE + "All tab menu modules " + ChatColor.RED + "disabled" + ChatColor.WHITE + ".");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            return handleTabMenuAll(player, args);
        }

        if (args[0].equalsIgnoreCase("trail")) {
            return handleTabMenuTrail(player, args);
        }

        TabMenuModule module = TabMenuModule.fromKey(args[0]);
        if (module == null) {
            syntaxError(player, "/tabmenu <all|ram|clipboard|selection|trail|status> [on|off|status|count]");
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            player.sendMessage(ChatColor.WHITE + "Tab menu " + module.key() + ": "
                    + (tabMenuSettingsService.isEnabled(player.getUniqueId(), module) ? ChatColor.GREEN + "on" : ChatColor.RED + "off"));
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (!mode.equals("on") && !mode.equals("off")) {
            syntaxError(player, "/tabmenu <all|ram|clipboard|selection|trail|status> [on|off|status|count]");
            return true;
        }

        boolean enabled = mode.equals("on");
        tabMenuSettingsService.setEnabled(player.getUniqueId(), module, enabled);
        savePlayerRuntime(player);
        tabInfoPanelService.refreshPlayer(player);
        player.sendMessage(ChatColor.WHITE + "Tab menu " + module.key() + " " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean handleTabMenuAll(Player player, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            int enabledModules = 0;
            for (TabMenuModule module : TabMenuModule.values()) {
                if (tabMenuSettingsService.isEnabled(player.getUniqueId(), module)) {
                    enabledModules++;
                }
            }
            String state = enabledModules == 0
                    ? ChatColor.RED + "off"
                    : enabledModules == TabMenuModule.values().length
                    ? ChatColor.GREEN + "on"
                    : ChatColor.YELLOW + "mixed";
            player.sendMessage(ChatColor.WHITE + "Tab menu all: " + state);
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (!mode.equals("on") && !mode.equals("off")) {
            syntaxError(player, "/tabmenu all <on|off|status>");
            return true;
        }

        boolean enabled = mode.equals("on");
        tabMenuSettingsService.setAllEnabled(player.getUniqueId(), enabled);
        savePlayerRuntime(player);
        tabInfoPanelService.refreshPlayer(player);
        player.sendMessage(ChatColor.WHITE + "All tab menu modules " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean handleTabMenuTrail(Player player, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            player.sendMessage(ChatColor.WHITE + "Tab menu trail: "
                    + (tabMenuSettingsService.isEnabled(player.getUniqueId(), TabMenuModule.TRAIL) ? ChatColor.GREEN + "on" : ChatColor.RED + "off")
                    + ChatColor.WHITE + " | count "
                    + ChatColor.GOLD + recentEditTrailService.limit(player.getUniqueId()));
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("on") || mode.equals("off")) {
            boolean enabled = mode.equals("on");
            tabMenuSettingsService.setEnabled(player.getUniqueId(), TabMenuModule.TRAIL, enabled);
            savePlayerRuntime(player);
            tabInfoPanelService.refreshPlayer(player);
            player.sendMessage(ChatColor.WHITE + "Tab menu trail " + (enabled ? "enabled." : "disabled."));
            return true;
        }

        if (mode.equals("count")) {
            if (args.length < 3) {
                senderSyntaxTrail(player);
                return true;
            }
            int limit;
            try {
                limit = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                senderSyntaxTrail(player);
                return true;
            }
            if (limit < 1 || limit > recentEditTrailService.maxLimit()) {
                player.sendMessage(ChatColor.RED + "Trail count must be between 1 and " + recentEditTrailService.maxLimit() + ".");
                return true;
            }
            recentEditTrailService.setLimit(player.getUniqueId(), limit);
            savePlayerRuntime(player);
            tabInfoPanelService.refreshPlayer(player);
            player.sendMessage(ChatColor.WHITE + "Tab menu trail count set to " + ChatColor.GOLD + limit + ChatColor.WHITE + ".");
            return true;
        }

        senderSyntaxTrail(player);
        return true;
    }

    private void senderSyntaxTrail(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /tabmenu trail <on|off|status|count <1-" + recentEditTrailService.maxLimit() + ">>");
    }

    private void sendTabMenuStatus(Player player) {
        sendMenuLines(player, TABMENU_STATUS_MENU, "header", List.of("&6Bayzyl&a - Builder tools"), ListMenuConfigService.tokens());
        for (TabMenuModule module : TabMenuModule.values()) {
            boolean enabled = tabMenuSettingsService.isEnabled(player.getUniqueId(), module);
            String extra = module == TabMenuModule.TRAIL
                    ? listMenuConfigService.format(
                            TABMENU_STATUS_MENU,
                            "trail-extra",
                            "&f | count &6{count}",
                            ListMenuConfigService.tokens("count", Integer.toString(recentEditTrailService.limit(player.getUniqueId())))
                    )
                    : "";
            sendMenuLine(player, TABMENU_STATUS_MENU, "row",
                    "&fTab menu {module}: {state}{extra}",
                    ListMenuConfigService.tokens(
                            "module", module.key(),
                            "state", enabled
                                    ? listMenuConfigService.format(TABMENU_STATUS_MENU, "states.on", "&aon", ListMenuConfigService.tokens())
                                    : listMenuConfigService.format(TABMENU_STATUS_MENU, "states.off", "&coff", ListMenuConfigService.tokens()),
                            "extra", extra
                    ));
        }
    }

    private boolean handleProfileNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String[] remapped = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, remapped, 0, args.length - 1);
        }
        return handleProfileCommand(player, remapped, "/bzl profile");
    }

    private boolean handleKitNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String[] remapped = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, remapped, 0, args.length - 1);
        }
        return handleKitCommand(player, remapped, "/bzl kit");
    }

    private boolean handleKitMakeNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String[] remapped = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, remapped, 0, args.length - 1);
        }
        return handleKitMakeCommand(player, remapped, "/bzl kitmake");
    }

    private boolean handleKitUpdateNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String[] remapped = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, remapped, 0, args.length - 1);
        }
        return handleKitUpdateCommand(player, remapped, "/bzl kitupdate");
    }

    private boolean handleKitConfirmNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        String[] remapped = new String[Math.max(0, args.length - 1)];
        if (args.length > 1) {
            System.arraycopy(args, 1, remapped, 0, args.length - 1);
        }
        return handleKitConfirmCommand(player, remapped, "/bzl kitconfirm");
    }

    private boolean handleProfileCommand(Player player, String[] args, String usageBase) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.WHITE + "Usage: " + usageBase + " <save|update|load|inspect|list|delete|rename|duplicate> [name]");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<BuilderProfileSummary> profiles = builderProfileService.listProfileSummaries();
                if (profiles.isEmpty()) {
                    player.sendMessage(listMenuConfigService.format(
                            PROFILE_LIST_MENU,
                            "empty",
                            "&fNo Bayzyl profiles saved yet.",
                            ListMenuConfigService.tokens()
                    ));
                    return true;
                }
                sendMenuLines(player, PROFILE_LIST_MENU, "header", List.of("&fProfiles:"), ListMenuConfigService.tokens("count", Integer.toString(profiles.size())));
                for (BuilderProfileSummary summary : profiles) {
                    player.sendMessage(listMenuConfigService.format(
                            PROFILE_LIST_MENU,
                            "row",
                            "&6{name}&8 - &f{type}&8 | &f{contents}&8 | &fupdated {updated}",
                            ListMenuConfigService.tokens(
                                    "name", summary.name(),
                                    "type", formatProfileType(summary.type()),
                                    "contents", summarizeProfileContents(summary),
                                    "updated", formatProfileTime(summary.updatedAtEpochMillis())
                            )
                    ));
                }
                return true;
            }
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " save <name> [type:config|toolbar|combined] [overwrite:true]");
                    return true;
                }
                BuilderProfileType type = parseProfileTypeArg(args, 2, builderProfileService.defaultSaveType());
                if (type == null) {
                    player.sendMessage(ChatColor.RED + "Unknown profile type. Use type:config, type:toolbar, or type:combined.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 2);
                if (!overwrite && builderProfileService.existsProfile(args[1])) {
                    player.sendMessage(ChatColor.RED + "Profile already exists. Re-run with overwrite:true or use "
                            + ChatColor.GOLD + usageBase + " update " + args[1] + ChatColor.RED + ".");
                    return true;
                }
                if (!builderProfileService.saveProfile(player, args[1], type, overwrite)) {
                    player.sendMessage(ChatColor.RED + "Could not save profile. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                player.sendMessage(ChatColor.WHITE + "Saved " + formatProfileType(type) + " profile "
                        + ChatColor.WHITE + args[1] + ChatColor.WHITE + ".");
                return true;
            }
            case "update" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " update <name> [section:config|toolbar|all]");
                    return true;
                }
                if (!builderProfileService.existsProfile(args[1])) {
                    player.sendMessage(ChatColor.RED + "Profile not found.");
                    return true;
                }
                BuilderProfileLoadSection section = parseProfileSectionArg(args, 2, builderProfileService.defaultLoadSection());
                if (section == null) {
                    player.sendMessage(ChatColor.RED + "Unknown update section. Use section:config, section:toolbar, or section:all.");
                    return true;
                }
                if (!builderProfileService.updateProfile(player, args[1], section)) {
                    player.sendMessage(ChatColor.RED + "Could not update profile.");
                    return true;
                }
                player.sendMessage(ChatColor.WHITE + "Updated profile " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " from your current " + ChatColor.WHITE + section.key() + ChatColor.WHITE + " state.");
                return true;
            }
            case "load" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " load <name> [section:config|toolbar|all]");
                    return true;
                }
                BuilderProfileLoadSection section = parseProfileSectionArg(args, 2, builderProfileService.defaultLoadSection());
                if (section == null) {
                    player.sendMessage(ChatColor.RED + "Unknown load section. Use section:config, section:toolbar, or section:all.");
                    return true;
                }
                BuilderProfileLoadResult result = builderProfileService.applyProfile(player, args[1], section);
                if (result == null) {
                    player.sendMessage(ChatColor.RED + "Profile not found.");
                    return true;
                }
                savePlayerRuntime(player);
                player.sendMessage(ChatColor.WHITE + "Loaded profile " + ChatColor.WHITE + args[1] + ChatColor.WHITE
                        + " (" + formatProfileType(result.type()) + ").");
                if (result.appliedConfig()) {
                    player.sendMessage(ChatColor.DARK_GRAY + "Config restored.");
                }
                if (result.appliedToolbar()) {
                    player.sendMessage(ChatColor.DARK_GRAY + "Toolbar placed " + ChatColor.WHITE + result.toolbarPlaced()
                            + ChatColor.DARK_GRAY + ", cleared " + ChatColor.WHITE + result.toolbarCleared()
                            + ChatColor.DARK_GRAY + ", skipped " + ChatColor.WHITE + result.toolbarSkipped()
                            + ChatColor.DARK_GRAY + " protected slots.");
                }
                if (!result.appliedConfig() && !result.appliedToolbar()) {
                    player.sendMessage(ChatColor.DARK_GRAY + "Nothing in that profile matched the requested section.");
                }
                return true;
            }
            case "inspect" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " inspect <name>");
                    return true;
                }
                BuilderProfile profile = builderProfileService.loadProfile(args[1]);
                if (profile == null) {
                    player.sendMessage(ChatColor.RED + "Profile not found.");
                    return true;
                }
                sendProfileInspect(player, profile);
                return true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " delete <name>");
                    return true;
                }
                if (!builderProfileService.deleteProfile(args[1])) {
                    player.sendMessage(ChatColor.RED + "Profile not found.");
                    return true;
                }
                player.sendMessage(ChatColor.WHITE + "Deleted profile " + ChatColor.WHITE + args[1] + ChatColor.WHITE + ".");
                return true;
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " rename <from> <to> [overwrite:true]");
                    return true;
                }
                if (!builderProfileService.existsProfile(args[1])) {
                    player.sendMessage(ChatColor.RED + "Source profile not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderProfileService.existsProfile(args[2])) {
                    player.sendMessage(ChatColor.RED + "Target profile already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderProfileService.renameProfile(args[1], args[2], overwrite)) {
                    player.sendMessage(ChatColor.RED + "Could not rename profile. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                player.sendMessage(ChatColor.WHITE + "Renamed profile " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + args[2] + ChatColor.WHITE + ".");
                return true;
            }
            case "duplicate" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " duplicate <from> <to> [overwrite:true]");
                    return true;
                }
                if (!builderProfileService.existsProfile(args[1])) {
                    player.sendMessage(ChatColor.RED + "Source profile not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderProfileService.existsProfile(args[2])) {
                    player.sendMessage(ChatColor.RED + "Target profile already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderProfileService.duplicateProfile(args[1], args[2], overwrite)) {
                    player.sendMessage(ChatColor.RED + "Could not duplicate profile. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                player.sendMessage(ChatColor.WHITE + "Duplicated profile " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + args[2] + ChatColor.WHITE + ".");
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown profile subcommand.");
                return true;
            }
        }
    }

    private boolean handleKitCommand(Player player, String[] args, String usageBase) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            String[] pageArgs = args.length <= 1 ? new String[0] : new String[]{args[1]};
            return handleKitList(player, pageArgs, usageBase + " list");
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "load" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " load <name>");
                    return true;
                }
                return loadKitIntoPlayer(player, args[1]);
            }
            case "inspect" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " inspect <name>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[1]);
                if (kit == null) {
                    player.sendMessage(ChatColor.RED + "Kit not found.");
                    return true;
                }
                sendKitInspect(player, kit);
                return true;
            }
            case "menu" -> {
                String[] pageArgs = args.length <= 1 ? new String[0] : new String[]{args[1]};
                return handleKitMenu(player, pageArgs, usageBase + " menu");
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " delete <name>");
                    return true;
                }
                if (!canManageServerKits(player, "bayzyl.kit.delete")) {
                    player.sendMessage(ChatColor.RED + "Only server operators can delete shared kits.");
                    return true;
                }
                if (!builderKitService.deleteKit(args[1])) {
                    player.sendMessage(ChatColor.RED + "Kit not found.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                player.sendMessage(ChatColor.WHITE + "Deleted shared kit " + ChatColor.WHITE + args[1] + ChatColor.WHITE + ".");
                player.sendMessage(ChatColor.AQUA + "Use /kit restoredefaults to bring back Bayzyl's default kit library.");
                return true;
            }
            case "note" -> {
                return handleKitNoteCommand(player, args, usageBase + " note");
            }
            case "theme" -> {
                return handleKitThemeCommand(player, args, usageBase + " theme");
            }
            case "icon" -> {
                return handleKitIconCommand(player, args, usageBase + " icon");
            }
            case "alias" -> {
                return handleKitAliasCommand(player, args, usageBase + " alias");
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " rename <from> <to> [overwrite:true]");
                    return true;
                }
                if (!canManageServerKits(player, "bayzyl.kit.create")) {
                    player.sendMessage(ChatColor.RED + "Only server operators can rename shared kits.");
                    return true;
                }
                if (!builderKitService.existsKit(args[1])) {
                    player.sendMessage(ChatColor.RED + "Source kit not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderKitService.existsKit(args[2])) {
                    player.sendMessage(ChatColor.RED + "Target kit already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderKitService.renameKit(args[1], args[2], overwrite)) {
                    player.sendMessage(ChatColor.RED + "Could not rename kit. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                clearPendingKitUpdate(player, builderKitService.normalizeName(args[1]));
                player.sendMessage(ChatColor.WHITE + "Renamed shared kit " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + builderKitService.normalizeName(args[2]) + ChatColor.WHITE + ".");
                return true;
            }
            case "duplicate" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " duplicate <from> <to> [overwrite:true]");
                    return true;
                }
                if (!canManageServerKits(player, "bayzyl.kit.create")) {
                    player.sendMessage(ChatColor.RED + "Only server operators can duplicate shared kits.");
                    return true;
                }
                if (!builderKitService.existsKit(args[1])) {
                    player.sendMessage(ChatColor.RED + "Source kit not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderKitService.existsKit(args[2])) {
                    player.sendMessage(ChatColor.RED + "Target kit already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderKitService.duplicateKit(args[1], args[2], overwrite)) {
                    player.sendMessage(ChatColor.RED + "Could not duplicate kit. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                player.sendMessage(ChatColor.WHITE + "Duplicated shared kit " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + builderKitService.normalizeName(args[2]) + ChatColor.WHITE + ".");
                return true;
            }
            case "update" -> {
                String[] remapped = new String[Math.max(0, args.length - 1)];
                if (args.length > 1) {
                    System.arraycopy(args, 1, remapped, 0, args.length - 1);
                }
                return handleKitUpdateCommand(player, remapped, usageBase + " update");
            }
            case "confirm" -> {
                String[] remapped = new String[Math.max(0, args.length - 1)];
                if (args.length > 1) {
                    System.arraycopy(args, 1, remapped, 0, args.length - 1);
                }
                return handleKitConfirmCommand(player, remapped, usageBase + " confirm");
            }
            case "restoredefaults" -> {
                if (!canManageServerKits(player, "bayzyl.kit.create")) {
                    player.sendMessage(ChatColor.RED + "Only server operators can restore default kits.");
                    return true;
                }
                int restored = builderKitService.restoreDefaultKits();
                kitShortcutRegistry.refresh();
                player.sendMessage(ChatColor.WHITE + "Restored " + ChatColor.GOLD + restored
                        + ChatColor.WHITE + " default Bayzyl kits.");
                player.sendMessage(ChatColor.AQUA + "Custom kits were left alone. Use /kit list to browse the refreshed library.");
                return true;
            }
            default -> {
                if (args.length > 1) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <name>");
                    player.sendMessage(ChatColor.WHITE + "Or use " + ChatColor.GOLD + usageBase + " list" + ChatColor.WHITE + " to browse kits.");
                    return true;
                }
                return loadKitIntoPlayer(player, args[0]);
            }
        }
    }

    private boolean handleKitMenu(Player player, String[] args, String usageBase) {
        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " [page]");
            return true;
        }
        int requestedPage = 1;
        if (args.length == 1 && !args[0].isBlank()) {
            try {
                requestedPage = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " [page]");
                return true;
            }
        }
        return kitMenuService.openMenu(player, requestedPage);
    }

    private boolean handleKitMakeCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can create shared kits.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <hotbar|inventory> <name> [overwrite:true]");
            return true;
        }

        BuilderKitScope scope = BuilderKitScope.fromKey(args[0]);
        if (scope == null) {
            player.sendMessage(ChatColor.RED + "Unknown kit scope. Use hotbar or inventory.");
            return true;
        }

        String normalizedName = builderKitService.normalizeName(args[1]);
        if (normalizedName == null) {
            player.sendMessage(ChatColor.RED + "Kit names must use letters, numbers, _ or -, max 32 chars.");
            return true;
        }
        if (builderKitService.isReservedName(normalizedName)) {
            player.sendMessage(ChatColor.RED + "That name is reserved for Bayzyl, Minecraft, or server command paths. Pick a different kit name.");
            return true;
        }

        boolean overwrite = parseOverwriteArg(args, 2);
        if (!overwrite && builderKitService.existsKit(normalizedName)) {
            player.sendMessage(ChatColor.RED + "Kit already exists. Re-run with overwrite:true.");
            return true;
        }
        if (!builderKitService.saveKit(player, normalizedName, scope, overwrite)) {
            player.sendMessage(ChatColor.RED + "Could not save kit.");
            return true;
        }

        kitShortcutRegistry.refresh();
        player.sendMessage(ChatColor.WHITE + "Saved " + formatKitScope(scope) + " "
                + ChatColor.WHITE + normalizedName + ChatColor.WHITE + " to shared server kits.");
        player.sendMessage(ChatColor.WHITE + "Load it with "
                + ChatColor.WHITE + "/kit " + normalizedName
                + ChatColor.WHITE + " or "
                + ChatColor.WHITE + "/bzl " + normalizedName + ChatColor.WHITE + ".");
        if (kitShortcutRegistry.isShortcutRegistered(normalizedName)) {
            player.sendMessage(ChatColor.WHITE + "Direct shortcut ready: " + ChatColor.WHITE + "/" + normalizedName);
        } else {
            player.sendMessage(ChatColor.WHITE + "Direct shortcut unavailable because another command already uses "
                    + ChatColor.WHITE + "/" + normalizedName + ChatColor.WHITE + ".");
        }
        return true;
    }

    private boolean handleKitUpdateCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can update shared kits.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <name>");
            return true;
        }

        BuilderKit kit = builderKitService.loadKit(args[0]);
        if (kit == null) {
            player.sendMessage(ChatColor.RED + "Kit not found.");
            return true;
        }

        KitUpdateCheck check = analyzeKitUpdate(player, kit);
        if (check.missingStoredSlots() > 0) {
            pendingKitUpdates.put(player.getUniqueId(), new PendingKitUpdate(kit.name(), System.currentTimeMillis()));
            player.sendMessage(ChatColor.GOLD + "Warning, your current inventory/hotbar is missing items from the kit you are attempting to update. Confirm with /kitconfirm to update anyway");
            player.sendMessage(ChatColor.WHITE + "Missing stored slots: " + ChatColor.WHITE + check.missingStoredSlots()
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.WHITE + "extra filled slots: " + ChatColor.WHITE + check.extraFilledSlots());
            player.sendMessage(ChatColor.AQUA + "Load the kit again first for a clean update, or use /kitconfirm to overwrite "
                    + ChatColor.WHITE + kit.name() + ChatColor.AQUA + " from your current " + scopeNoun(kit.scope()) + ".");
            return true;
        }

        return applyKitUpdate(player, kit);
    }

    private boolean handleKitConfirmCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can confirm shared kit updates.");
            return true;
        }
        if (args.length > 0) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase);
            return true;
        }

        PendingKitUpdate pending = pendingKitUpdates.get(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(ChatColor.RED + "No pending kit update to confirm.");
            return true;
        }
        if (System.currentTimeMillis() - pending.createdAtEpochMillis() > KIT_CONFIRM_WINDOW_MILLIS) {
            pendingKitUpdates.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "Your pending kit update expired. Run /kitupdate <name> again.");
            return true;
        }

        BuilderKit kit = builderKitService.loadKit(pending.kitName());
        if (kit == null) {
            pendingKitUpdates.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "That kit no longer exists.");
            return true;
        }
        return applyKitUpdate(player, kit);
    }

    private boolean applyKitUpdate(Player player, BuilderKit kit) {
        int previousCount = kit.itemCount();
        if (!builderKitService.updateKit(player, kit.name())) {
            player.sendMessage(ChatColor.RED + "Could not update kit.");
            return true;
        }

        pendingKitUpdates.remove(player.getUniqueId());
        kitShortcutRegistry.refresh();
        BuilderKit updated = builderKitService.loadKit(kit.name());
        int newCount = updated == null ? previousCount : updated.itemCount();
        player.sendMessage(ChatColor.WHITE + "Updated " + formatKitScope(kit.scope()) + " "
                + ChatColor.WHITE + kit.name() + ChatColor.WHITE + " from your current " + scopeNoun(kit.scope()) + ".");
        player.sendMessage(ChatColor.DARK_GRAY + "Stored " + ChatColor.WHITE + newCount
                + ChatColor.DARK_GRAY + " items (" + formatSignedDelta(newCount - previousCount) + ChatColor.DARK_GRAY + ").");
        if (kit.builtIn()) {
            player.sendMessage(ChatColor.AQUA + "This currently overrides Bayzyl's default version. Use /kit restoredefaults to revert it.");
        }
        return true;
    }

    private boolean handleKitNoteCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can edit kit metadata.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <name> <sentence...|clear>");
            return true;
        }
        BuilderKit kit = builderKitService.loadKit(args[1]);
        if (kit == null) {
            player.sendMessage(ChatColor.RED + "Kit not found.");
            return true;
        }
        String note = joinArgs(args, 2);
        if (note.equalsIgnoreCase("clear")) {
            note = "";
        }
        if (!builderKitService.updateKitNote(kit.name(), note)) {
            player.sendMessage(ChatColor.RED + "Could not update the kit note.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Updated note for " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleKitThemeCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can edit kit metadata.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <name> <theme...>");
            return true;
        }
        BuilderKit kit = builderKitService.loadKit(args[1]);
        if (kit == null) {
            player.sendMessage(ChatColor.RED + "Kit not found.");
            return true;
        }
        String theme = joinArgs(args, 2);
        if (!builderKitService.updateKitTheme(kit.name(), theme)) {
            player.sendMessage(ChatColor.RED + "Could not update the kit theme.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Updated theme for " + ChatColor.WHITE + kit.name()
                + ChatColor.WHITE + " to " + ChatColor.WHITE + theme + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleKitIconCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can edit kit metadata.");
            return true;
        }
        if (args.length != 3) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <name> <material|clear>");
            return true;
        }
        BuilderKit kit = builderKitService.loadKit(args[1]);
        if (kit == null) {
            player.sendMessage(ChatColor.RED + "Kit not found.");
            return true;
        }
        Material material = null;
        if (!args[2].equalsIgnoreCase("clear")) {
            material = Material.matchMaterial(args[2]);
            if (material == null || !material.isItem()) {
                player.sendMessage(ChatColor.RED + "Unknown icon material.");
                return true;
            }
        }
        if (!builderKitService.updateKitIcon(kit.name(), material)) {
            player.sendMessage(ChatColor.RED + "Could not update the kit icon.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Updated icon for " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleKitAliasCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            player.sendMessage(ChatColor.RED + "Only server operators can manage kit aliases.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <list|add|remove> ...");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                if (args.length != 3) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " list <name>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[2]);
                if (kit == null) {
                    player.sendMessage(ChatColor.RED + "Kit not found.");
                    return true;
                }
                String aliases;
                if (kit.aliases().isEmpty()) {
                    aliases = listMenuConfigService.value(KIT_ALIAS_LIST_MENU, "none-label", "none");
                } else {
                    String separator = listMenuConfigService.format(
                            KIT_ALIAS_LIST_MENU,
                            "separator",
                            ", ",
                            ListMenuConfigService.tokens()
                    );
                    aliases = String.join(separator, kit.aliases());
                }
                player.sendMessage(listMenuConfigService.format(
                        KIT_ALIAS_LIST_MENU,
                        "line",
                        "&fAliases for &f{name}&f: &f{aliases}",
                        ListMenuConfigService.tokens("name", kit.name(), "aliases", aliases)
                ));
                return true;
            }
            case "add" -> {
                if (args.length != 4) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " add <name> <alias>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[2]);
                if (kit == null) {
                    player.sendMessage(ChatColor.RED + "Kit not found.");
                    return true;
                }
                if (!builderKitService.addAlias(kit.name(), args[3])) {
                    player.sendMessage(ChatColor.RED + "Could not add alias. That label may be reserved or already claimed.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                player.sendMessage(ChatColor.WHITE + "Added alias " + ChatColor.WHITE + builderKitService.normalizeName(args[3])
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
                return true;
            }
            case "remove" -> {
                if (args.length != 4) {
                    player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " remove <name> <alias>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[2]);
                if (kit == null) {
                    player.sendMessage(ChatColor.RED + "Kit not found.");
                    return true;
                }
                if (!builderKitService.removeAlias(kit.name(), args[3])) {
                    player.sendMessage(ChatColor.RED + "Alias not found on that kit.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                player.sendMessage(ChatColor.WHITE + "Removed alias " + ChatColor.WHITE + builderKitService.normalizeName(args[3])
                        + ChatColor.WHITE + " from " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Usage: " + usageBase + " <list|add|remove> ...");
                return true;
            }
        }
    }

    private boolean handleKitShortcut(Player player, String kitName, String[] args, String usageBase) {
        if (args.length > 0) {
            player.sendMessage(ChatColor.RED + "Usage: " + usageBase);
            return true;
        }
        return loadKitIntoPlayer(player, kitName);
    }

    private boolean loadKitIntoPlayer(Player player, String rawName) {
        BuilderKitLoadResult result = builderKitService.applyKit(player, rawName);
        if (result == null) {
            player.sendMessage(ChatColor.RED + "Kit not found.");
            return true;
        }
        player.sendMessage(ChatColor.WHITE + "Loaded " + formatKitScope(result.scope()) + " "
                + ChatColor.WHITE + result.name() + ChatColor.WHITE + ".");
        player.sendMessage(ChatColor.DARK_GRAY + "Placed " + ChatColor.WHITE + result.placed()
                + ChatColor.DARK_GRAY + ", cleared " + ChatColor.WHITE + result.cleared()
                + ChatColor.DARK_GRAY + " slots.");
        if (result.scope() == BuilderKitScope.INVENTORY) {
            player.sendMessage(ChatColor.DARK_GRAY + "Armor and offhand were left alone.");
        }
        return true;
    }

    private boolean handleKitList(CommandSender sender, String[] args, String usageBase) {
        List<KitListPage> pages = buildKitListPages();
        if (pages.isEmpty()) {
            sender.sendMessage(listMenuConfigService.format(
                    KIT_LIST_MENU,
                    "messages.empty",
                    "&fNo shared Bayzyl kits saved yet.",
                    Map.of()
            ));
            return true;
        }

        if (args.length > 1) {
            sender.sendMessage(listMenuConfigService.format(
                    KIT_LIST_MENU,
                    "messages.usage",
                    "&cUsage: {usage} [page]",
                    ListMenuConfigService.tokens("usage", usageBase)
            ));
            return true;
        }

        int requestedPage = 1;
        if (args.length >= 1 && !args[0].isBlank()) {
            try {
                requestedPage = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(listMenuConfigService.format(
                        KIT_LIST_MENU,
                        "messages.usage",
                        "&cUsage: {usage} [page]",
                        ListMenuConfigService.tokens("usage", usageBase)
                ));
                return true;
            }
        }

        if (requestedPage < 1 || requestedPage > pages.size()) {
            sender.sendMessage(listMenuConfigService.format(
                    KIT_LIST_MENU,
                    "messages.page-range",
                    "&cKit list page must be 1-{page_count}.",
                    ListMenuConfigService.tokens("page_count", Integer.toString(pages.size()))
            ));
            return true;
        }

        KitListPage page = pages.get(requestedPage - 1);
        Map<String, String> pageTokens = ListMenuConfigService.tokens(
                "usage", usageBase,
                "page", Integer.toString(requestedPage),
                "page_count", Integer.toString(pages.size()),
                "title", page.title()
        );
        for (String line : listMenuConfigService.formatList(
                KIT_LIST_MENU,
                "messages.header",
                List.of("&a&lBayzyl", "&aKits &8| &6Page {page}/{page_count} &8| &f{title}"),
                pageTokens
        )) {
            sender.sendMessage(line);
        }
        String tip = listMenuConfigService.format(
                KIT_LIST_MENU,
                "messages.tip",
                "&bTip: /kit inspect <name> previews a kit. /kit restoredefaults resets Bayzyl defaults.",
                pageTokens
        );
        if (!tip.isBlank()) {
            sender.sendMessage(tip);
        }
        for (BuilderKitSummary summary : page.kits()) {
            sender.sendMessage(listMenuConfigService.format(
                    KIT_LIST_MENU,
                    "messages.row",
                    "&6{name} &8- &f{scope}&8, &f{item_count} items&8, &f{kind}&8, &f{theme} &8| &b{shortcut}",
                    kitListTokens(summary)
            ));
        }
        if (pages.size() > 1) {
            if (requestedPage < pages.size()) {
                sender.sendMessage(listMenuConfigService.format(
                        KIT_LIST_MENU,
                        "messages.next",
                        "&bNext: &f{usage} {next_page}",
                        ListMenuConfigService.tokens(
                                "usage", usageBase,
                                "next_page", Integer.toString(requestedPage + 1)
                        )
                ));
            } else {
                sender.sendMessage(listMenuConfigService.format(
                        KIT_LIST_MENU,
                        "messages.last",
                        "&bYou are on the last kit page.",
                        pageTokens
                ));
            }
        }
        return true;
    }

    private boolean handleParticleVisualToggle(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        settings.setEnabled(!settings.isEnabled());
        boolean enabled = settings.isEnabled();

        savePlayerRuntime(player);
        sender.sendMessage(ChatColor.WHITE + "Selection visualization " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private org.bukkit.Color parseColor(String value) {
        switch (value) {
            case "bayzyl":
                return org.bukkit.Color.fromRGB(120, 255, 165);
            case "orange":
                return org.bukkit.Color.fromRGB(255, 170, 60);
            case "green":
                return org.bukkit.Color.fromRGB(80, 255, 120);
            case "red":
                return org.bukkit.Color.fromRGB(255, 90, 90);
            case "yellow":
                return org.bukkit.Color.fromRGB(255, 230, 90);
            case "blue":
                return org.bukkit.Color.fromRGB(80, 160, 255);
            case "purple":
                return org.bukkit.Color.fromRGB(190, 90, 255);
            default:
                break;
        }

        if (value.startsWith("#") && value.length() == 7) {
            try {
                int rgb = Integer.parseInt(value.substring(1), 16);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                return org.bukkit.Color.fromRGB(r, g, b);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private void sendHelpPage(CommandSender sender, int page) {
        sendHeader(sender);
        helpContentService.sendPage(sender, page, editService.getBackendName(), shapeService.getBackendName());
    }

    private Integer helpTopicPage(String topic) {
        return switch (topic) {
            case "overview" -> 1;
            case "selection" -> 2;
            case "selectiontools", "selection-tools" -> 3;
            case "visuals", "qol" -> 4;
            case "navigation" -> 5;
            case "editing" -> 6;
            case "clipboard" -> 7;
            case "flags", "keywords" -> 8;
            case "shapes" -> 9;
            case "tools", "generation", "cleanup", "palette" -> 10;
            case "nudge" -> NUDGE_HELP_PAGE;
            case "profile", "profiles" -> PROFILES_HELP_PAGE;
            case "kit", "kits" -> KITS_HELP_PAGE;
            case "runtime", "tabmenu" -> TABMENU_HELP_PAGE;
            case "brush", "brushes" -> 15;
            default -> null;
        };
    }

    private void sendHeader(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Bayzyl" + ChatColor.GREEN + " - Builder tools");
    }

    private boolean shouldBypassConfirm(Player player, boolean explicitConfirm) {
        return explicitConfirm || adminModeService.isEnabled(player);
    }

    private void sendUsage(CommandSender sender, CommandSpec spec) {
        sender.sendMessage(ChatColor.GOLD + spec.name());
        sender.sendMessage(ChatColor.WHITE + spec.description());
        if (spec.name().equals("paste")) {
            sender.sendMessage(ChatColor.AQUA + "/paste [-aons] [rotation:<0|90|180|270>] [confirm:true]");
            return;
        }
        if (spec.name().equals("stack")) {
            sender.sendMessage(ChatColor.AQUA + "/stack <count> [direction] [-a] [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/stack random <count> [spread:<n>|x:<n>|y:<n>|z:<n>] [-a] [confirm:true]");
            return;
        }
        if (spec.name().equals("rotate")) {
            sender.sendMessage(ChatColor.AQUA + "/rotate <0|90|180|270|left|right|back>");
            return;
        }
        if (spec.name().equals("selectionparticles")) {
            sender.sendMessage(ChatColor.AQUA + "/bzl selectionparticles color <bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB>");
            sender.sendMessage(ChatColor.AQUA + "/bzl selectionparticles <on|off>");
            return;
        }
        if (spec.name().equals("tool")) {
            sender.sendMessage(ChatColor.GREEN + "Terrain brushes shape terrain. Cleanup brushes are localized erasers.");
            sender.sendMessage(ChatColor.AQUA + "/bzl tool <smooth|raise|lower|flatten> <radius> [power] [bedrock:on|off]");
            sender.sendMessage(ChatColor.AQUA + "/bzl cleanup brush <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> <radius> [power]");
            return;
        }
        if (spec.name().equals("cleanup")) {
            sender.sendMessage(ChatColor.GREEN + "Cleanup commands erase clutter from a selection; cleanup brushes are localized erasers.");
            sender.sendMessage(ChatColor.AQUA + "/bzl cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/bzl cleanup brush <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> <radius> [power]");
            sender.sendMessage(ChatColor.AQUA + "foliagecleanup erases plant clutter; lightcleanup erases torches/lanterns/end rods.");
            return;
        }
        if (spec.name().equals("palette")) {
            sender.sendMessage(ChatColor.GREEN + "Palette tools inspect and reshape the dominant block mix in your current selection.");
            sender.sendMessage(ChatColor.AQUA + "/bzl palette analyze");
            sender.sendMessage(ChatColor.AQUA + "/bzl palette swap <from> <to> [confirm:true]");
            return;
        }
        if (spec.name().equals("nudge")) {
            sender.sendMessage(ChatColor.GREEN + "Use scroll-nudging to move a selected build a few blocks at a time.");
            sender.sendMessage(ChatColor.AQUA + "/nudge status");
            sender.sendMessage(ChatColor.AQUA + "/nudge invert <on|off>");
            sender.sendMessage(ChatColor.AQUA + "/nudge step <amount>");
            sender.sendMessage(ChatColor.AQUA + "/nudge vertical <jump|look|off>");
            sender.sendMessage(ChatColor.AQUA + "/nudge reset");
            return;
        }
        if (spec.name().equals("ramalert")) {
            sender.sendMessage(ChatColor.GREEN + "Watch server JVM memory and warn staff before memory pressure gets dangerous.");
            sender.sendMessage(ChatColor.AQUA + "/ramalert status");
            sender.sendMessage(ChatColor.AQUA + "/ramalert help");
            sender.sendMessage(ChatColor.AQUA + "/ramalert on threshold:<percent> interval:<seconds|minutes> cooldown:<seconds|minutes>");
            sender.sendMessage(ChatColor.AQUA + "/ramalert off");
            return;
        }
        if (spec.name().equals("profile")) {
            sender.sendMessage(ChatColor.GREEN + "Save and restore Bayzyl config presets, toolbar presets, or combined workflow profiles.");
            sender.sendMessage(ChatColor.AQUA + "/profile save <name> [type:config|toolbar|combined] [overwrite:true]");
            sender.sendMessage(ChatColor.AQUA + "/profile update <name> [section:config|toolbar|all]");
            sender.sendMessage(ChatColor.AQUA + "/profile load <name> [section:config|toolbar|all]");
            sender.sendMessage(ChatColor.AQUA + "/profile inspect <name>");
            sender.sendMessage(ChatColor.AQUA + "/profile list");
            sender.sendMessage(ChatColor.AQUA + "/profile rename <from> <to> [overwrite:true]");
            sender.sendMessage(ChatColor.AQUA + "/profile duplicate <from> <to> [overwrite:true]");
            sender.sendMessage(ChatColor.AQUA + "/profile delete <name>");
            return;
        }
        if (spec.name().equals("kit")) {
            sender.sendMessage(ChatColor.GREEN + "Shared builder kits restore a saved hotbar or full main inventory for any player.");
            sender.sendMessage(ChatColor.AQUA + "/kit list [page]");
            sender.sendMessage(ChatColor.AQUA + "/kit menu [page]");
            sender.sendMessage(ChatColor.AQUA + "/kit <name>  |  /kit load <name>  |  /bzl <kitname>");
            sender.sendMessage(ChatColor.AQUA + "/kit inspect <name>  |  /kit update <name>  |  /kit confirm");
            sender.sendMessage(ChatColor.AQUA + "/kit rename <from> <to> [overwrite:true]");
            sender.sendMessage(ChatColor.AQUA + "/kit duplicate <from> <to> [overwrite:true]  |  /kit delete <name>");
            sender.sendMessage(ChatColor.AQUA + "/kit note <name> <sentence...>  |  /kit theme <name> <theme...>");
            sender.sendMessage(ChatColor.AQUA + "/kit icon <name> <material|clear>  |  /kit alias <list|add|remove> ...");
            sender.sendMessage(ChatColor.AQUA + "/kit restoredefaults  |  /kitlist [page]");
            return;
        }
        if (spec.name().equals("kitmake")) {
            sender.sendMessage(ChatColor.GREEN + "Capture your current hotbar or inventory as a shared server kit.");
            sender.sendMessage(ChatColor.AQUA + "/kitmake <hotbar|inventory> <name> [overwrite:true]");
            sender.sendMessage(ChatColor.AQUA + "/bzl kitmake <hotbar|inventory> <name> [overwrite:true]");
            return;
        }
        if (spec.name().equals("kitupdate")) {
            sender.sendMessage(ChatColor.GREEN + "Update an existing shared kit from your current hotbar or inventory state.");
            sender.sendMessage(ChatColor.AQUA + "/kitupdate <name>");
            sender.sendMessage(ChatColor.AQUA + "/bzl kitupdate <name>");
            sender.sendMessage(ChatColor.AQUA + "/kitconfirm");
            return;
        }
        if (spec.name().equals("kitconfirm")) {
            sender.sendMessage(ChatColor.GREEN + "Confirm an overwrite-style kit update after Bayzyl warns about missing stored items.");
            sender.sendMessage(ChatColor.AQUA + "/kitconfirm");
            sender.sendMessage(ChatColor.AQUA + "/kit confirm");
            return;
        }
        if (spec.name().equals("kitlist")) {
            sender.sendMessage(ChatColor.GREEN + "Browse the shared kit library in themed pages.");
            sender.sendMessage(ChatColor.AQUA + "/kitlist [page]");
            sender.sendMessage(ChatColor.AQUA + "/kit list [page]");
            return;
        }
        if (spec.name().equals("kithelp")) {
            sender.sendMessage(ChatColor.GREEN + "Open the Bayzyl kits help page or jump to another help topic.");
            sender.sendMessage(ChatColor.AQUA + "/kithelp");
            sender.sendMessage(ChatColor.AQUA + "/kithelp kits");
            sender.sendMessage(ChatColor.AQUA + "/bzlhelp kits");
            return;
        }
        if (spec.name().equals("tabmenu")) {
            sender.sendMessage(ChatColor.GREEN + "Toggle builder info modules shown beneath the player list.");
            sender.sendMessage(ChatColor.AQUA + "/tabmenu status");
            sender.sendMessage(ChatColor.AQUA + "/tabmenu all <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/tabmenu ram <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/tabmenu clipboard <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/tabmenu selection <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/tabmenu trail <on|off|status|count <1-" + recentEditTrailService.maxLimit() + ">>");
            sender.sendMessage(ChatColor.AQUA + "/bzl tabmenu all <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/bzl tabmenu ram <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/bzl tabmenu clipboard <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/bzl tabmenu selection <on|off|status>");
            sender.sendMessage(ChatColor.AQUA + "/bzl tabmenu trail <on|off|status|count <1-" + recentEditTrailService.maxLimit() + ">>");
            return;
        }
        if (spec.name().equals("flip")) {
            sender.sendMessage(ChatColor.AQUA + "/flip <x|y|z|left-right|front-back|up-down|left|right|forward|back>");
            return;
        }
        if (spec.name().equals("sphere") || spec.name().equals("hsphere")
                || spec.name().equals("dome") || spec.name().equals("hdome")
                || spec.name().equals("bowl") || spec.name().equals("hbowl")) {
            sender.sendMessage(ChatColor.AQUA + "/" + spec.name() + " <block> <radius|x,y,z> [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("cyl") || spec.name().equals("hcyl")) {
            sender.sendMessage(ChatColor.AQUA + "/" + spec.name() + " <block> <radius|x,z> <height> [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("pyramid") || spec.name().equals("hpyramid")) {
            sender.sendMessage(ChatColor.AQUA + "/" + spec.name() + " <block> <size> [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("generate")) {
            sender.sendMessage(ChatColor.AQUA + "/generate <block> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/g stone (0.75-sqrt(x^2+y^2))^2+z^2 < 0.25^2");
            return;
        }
        if (spec.name().equals("generatebiome")) {
            sender.sendMessage(ChatColor.AQUA + "/generatebiome <biome> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/genbiome cherry_grove (x*x+z*z) < 0.5");
            return;
        }
        if (spec.name().equals("biomeinfo")) {
            sender.sendMessage(ChatColor.AQUA + "/biomeinfo");
            return;
        }
        if (spec.name().equals("forestgen")) {
            sender.sendMessage(ChatColor.AQUA + "/forestgen [size] [type] [density] [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("pumpkins")) {
            sender.sendMessage(ChatColor.AQUA + "/pumpkins [size] [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("walls")) {
            sender.sendMessage(ChatColor.AQUA + "/walls <block> [mask:<blocks>] [confirm:true]");
            return;
        }
        if (spec.name().equals("overlay")) {
            sender.sendMessage(ChatColor.AQUA + "/overlay <block> [mask:<blocks>] [confirm:true]");
            return;
        }
        if (spec.name().equals("naturalize")) {
            sender.sendMessage(ChatColor.AQUA + "/naturalize [depth:<n>] [bedrock:on|off] [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/bzl naturalize [depth:<n>] [bedrock:on|off] [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/brush naturalize <radius> [depth] [bedrock:on|off]");
            return;
        }
        if (spec.name().equals("brush")) {
            sender.sendMessage(ChatColor.AQUA + "/brush <sphere|hsphere|cyl|hcyl|pyramid|hpyramid|naturalize> <block> <args...> [mask:<blocks>] [confirm:true]");
            sender.sendMessage(ChatColor.AQUA + "/brush <paint|spatter|replace|blend|surface|noise|restore|vegetation|decay> ...");
            sender.sendMessage(ChatColor.AQUA + "/brush <clipboard|paint|erase|smooth|raise|lower|flatten|naturalize|floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|none|info> ...");
            sender.sendMessage(ChatColor.AQUA + "/brush <save|load|list|delete> ...");
            sender.sendMessage(ChatColor.GREEN + "Binds the brush to your held item. Empty hand creates a Bayzyl brush item.");
            sender.sendMessage(ChatColor.GRAY + "Shortcut: if you finish with a bare block name like 'air', it is treated as the mask.");
            return;
        }
        if (spec.name().equals("smooth")) {
            sender.sendMessage(ChatColor.AQUA + "/smooth [iterations|iterations:<n>] [confirm:true]");
            return;
        }
        sender.sendMessage(ChatColor.AQUA + spec.usage());
    }

    private void sendShapeResult(CommandSender sender, ShapeResult result, String label) {
        if (!result.success()) {
            sender.sendMessage(ChatColor.RED + result.message());
            return;
        }
        sender.sendMessage(ChatColor.DARK_GRAY + result.message());
        if (result.preview()) {
            sender.sendMessage(ChatColor.WHITE + "Previewed " + label + ".");
            return;
        }
        sender.sendMessage(ChatColor.WHITE + "Created " + label + " and changed " + result.changed() + " blocks.");
    }

    private void sendGeneratorResult(CommandSender sender, GeneratorResult result, String label) {
        if (!result.success()) {
            sender.sendMessage(ChatColor.RED + result.message());
            return;
        }
        sender.sendMessage(ChatColor.DARK_GRAY + result.message());
        if (result.preview()) {
            sender.sendMessage(ChatColor.WHITE + "Previewed " + label + ".");
            return;
        }
        sender.sendMessage(ChatColor.WHITE + "Completed " + label + " and changed " + result.changed() + " blocks.");
    }

    private String formatCommandList(List<CommandSpec> specs) {
        List<String> names = new ArrayList<>();
        for (CommandSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            names.add("/" + spec.name());
        }
        return ChatColor.WHITE + String.join(ChatColor.DARK_GRAY + ", " + ChatColor.WHITE, names);
    }

    private String formatHelpLine(String label, String commands) {
        return ChatColor.WHITE + label + ChatColor.DARK_GRAY + " -> " + ChatColor.WHITE + commands;
    }

    private CommandSpec spec(String name) {
        return CommandRegistry.getTopLevel().stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private Location resolvePlayerPasteAnchor(Player player) {
        Location location = player.getLocation();
        return new Location(
                player.getWorld(),
                Math.floor(location.getX()),
                Math.floor(location.getY()),
                Math.floor(location.getZ())
        );
    }

    private List<String> filterPrefix(List<String> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return source;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                results.add(value);
            }
        }
        return results;
    }

    private void syntaxError(CommandSender sender, String syntax) {
        sender.sendMessage(ChatColor.RED + "Syntax: " + ChatColor.GOLD + syntax);
    }

    private void savePlayerRuntime(Player player) {
        runtimePreferencesService.savePlayer(player, adminModeService, nightVisionService, autoUnstickService, ghostHandService, nudgeSettingsService, visualizationManager, tabMenuSettingsService, recentEditTrailService);
    }

    private BuilderProfileType parseProfileTypeArg(String[] args, int startIndex, BuilderProfileType fallback) {
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.startsWith("type:")) {
                continue;
            }
            return BuilderProfileType.fromKey(token.substring("type:".length()));
        }
        return fallback;
    }

    private BuilderProfileLoadSection parseProfileSectionArg(String[] args, int startIndex, BuilderProfileLoadSection fallback) {
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.startsWith("section:")) {
                continue;
            }
            return BuilderProfileLoadSection.fromKey(token.substring("section:".length()));
        }
        return fallback;
    }

    private boolean parseOverwriteArg(String[] args, int startIndex) {
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("overwrite:true")) {
                return true;
            }
        }
        return false;
    }

    private void sendProfileInspect(Player player, BuilderProfile profile) {
        Map<String, String> baseTokens = ListMenuConfigService.tokens(
                "name", profile.name(),
                "type", formatProfileType(profile.type()),
                "updated", formatProfileTime(profile.updatedAtEpochMillis())
        );
        sendMenuLines(player, PROFILE_INSPECT_MENU, "header", List.of(
                "&6Bayzyl&a - Builder tools",
                "&6{name} &8[&b{type}&8]"
        ), baseTokens);
        if (profile.updatedAtEpochMillis() > 0L) {
            sendMenuLine(player, PROFILE_INSPECT_MENU, "updated",
                    "&8Updated: &f{updated}",
                    baseTokens);
        }
        if (profile.config() != null) {
            BuilderProfileConfig config = profile.config();
            sendMenuLine(player, PROFILE_INSPECT_MENU, "features-title", "&bFeatures:", ListMenuConfigService.tokens());
            sendMenuLine(player, PROFILE_INSPECT_MENU, "features-primary",
                    "&f  viz {viz}&8 | &fnightvision {nightvision}&8 | &fadmin {admin}",
                    ListMenuConfigService.tokens(
                            "viz", boolWord(config.selectionParticlesEnabled()),
                            "nightvision", boolWord(config.nightVisionEnabled()),
                            "admin", boolWord(config.adminModeEnabled())
                    ));
            sendMenuLine(player, PROFILE_INSPECT_MENU, "features-secondary",
                    "&f  unstick {unstick}&8 | &fghosthand {ghosthand}",
                    ListMenuConfigService.tokens(
                            "unstick", boolWord(config.autoUnstickEnabled()),
                            "ghosthand", boolWord(config.ghostHandEnabled())
                    ));
            sendMenuLine(player, PROFILE_INSPECT_MENU, "nudge-title", "&bNudge Settings:", ListMenuConfigService.tokens());
            sendMenuLine(player, PROFILE_INSPECT_MENU, "nudge-line",
                    "&f  step &6{step}&8 | &finvert {invert}&8 | &fvertical &6{vertical}",
                    ListMenuConfigService.tokens(
                            "step", Integer.toString(config.nudgeSettings().step()),
                            "invert", boolWord(config.nudgeSettings().inverted()),
                            "vertical", config.nudgeSettings().verticalMode().name().toLowerCase(Locale.ROOT)
                    ));
            sendMenuLine(player, PROFILE_INSPECT_MENU, "tabmenu-title", "&bTab Menu & Trail:", ListMenuConfigService.tokens());
            sendMenuLine(player, PROFILE_INSPECT_MENU, "tabmenu-line",
                    "&f  {tabmenu}&8 | &ftrail limit &6{trail_limit}",
                    ListMenuConfigService.tokens(
                            "tabmenu", summarizeTabMenuStates(config.tabMenuStates()),
                            "trail_limit", Integer.toString(config.trailLimit())
                    ));
        }
        if (profile.type().hasToolbar()) {
            sendMenuLine(player, PROFILE_INSPECT_MENU, "toolbar-title", "&bToolbar:", ListMenuConfigService.tokens());
            if (profile.toolbarSlots().isEmpty()) {
                sendMenuLine(player, PROFILE_INSPECT_MENU, "toolbar-empty", "&f  (empty)", ListMenuConfigService.tokens());
            } else {
                sendMenuLine(player, PROFILE_INSPECT_MENU, "toolbar-line",
                        "&f  {toolbar}",
                        ListMenuConfigService.tokens("toolbar", formatToolbarSlots(profile.toolbarSlots())));
            }
        }
    }

    private void sendMenuLine(CommandSender sender, String menuId, String path, String fallback, Map<String, String> tokens) {
        sender.sendMessage(listMenuConfigService.format(menuId, path, fallback, tokens));
    }

    private void sendMenuLines(CommandSender sender, String menuId, String path, List<String> fallback, Map<String, String> tokens) {
        for (String line : listMenuConfigService.formatList(menuId, path, fallback, tokens)) {
            sender.sendMessage(line);
        }
    }

    private String formatProfileType(BuilderProfileType type) {
        return switch (type) {
            case CONFIG -> "config";
            case TOOLBAR -> "toolbar";
            case COMBINED -> "combined";
        };
    }

    private String formatKitScope(BuilderKitScope scope) {
        return switch (scope) {
            case HOTBAR -> "hotbar kit";
            case INVENTORY -> "inventory kit";
        };
    }

    private String formatProfileTime(long epochMillis) {
        if (epochMillis <= 0L) {
            return "legacy";
        }
        return PROFILE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private String summarizeProfileContents(BuilderProfileSummary summary) {
        List<String> parts = new ArrayList<>();
        if (summary.hasConfig()) {
            parts.add("config");
        }
        if (summary.hasToolbar()) {
            parts.add("toolbar " + summary.toolbarSlots() + " slots");
        }
        if (parts.isEmpty()) {
            return "empty";
        }
        return String.join(ChatColor.DARK_GRAY + ", " + ChatColor.WHITE, parts);
    }

    private String boolWord(boolean value) {
        return value ? ChatColor.GREEN + "on" + ChatColor.WHITE : ChatColor.RED + "off" + ChatColor.WHITE;
    }

    private String summarizeTabMenuStates(Map<TabMenuModule, Boolean> states) {
        List<String> parts = new ArrayList<>();
        for (TabMenuModule module : TabMenuModule.values()) {
            boolean enabled = states.getOrDefault(module, module.defaultEnabled());
            parts.add(module.key() + ":" + (enabled ? ChatColor.GREEN + "on" + ChatColor.WHITE : ChatColor.RED + "off" + ChatColor.WHITE));
        }
        return String.join(ChatColor.DARK_GRAY + ", " + ChatColor.WHITE, parts);
    }

    private String formatToolbarSlots(List<BuilderProfile.ToolbarSlot> slots) {
        List<String> parts = new ArrayList<>();
        for (BuilderProfile.ToolbarSlot slot : slots) {
            ToolType toolType = toolManager.getToolType(slot.item());
            String label = toolType == null ? slot.item().getType().name().toLowerCase(Locale.ROOT) : toolType.name().toLowerCase(Locale.ROOT);
            parts.add((slot.slot() + 1) + ":" + label);
        }
        return String.join(ChatColor.DARK_GRAY + ", " + ChatColor.WHITE, parts);
    }

    private void sendPaletteAnalysis(Player player, Selection selection, PaletteService.AnalysisResult analysis) {
        Map<String, String> baseTokens = ListMenuConfigService.tokens(
                "size", (selection.getMaxX() - selection.getMinX() + 1) + "x"
                        + (selection.getMaxY() - selection.getMinY() + 1) + "x"
                        + (selection.getMaxZ() - selection.getMinZ() + 1),
                "filled", Long.toString(analysis.filledBlocks()),
                "air", Long.toString(analysis.totalBlocks() - analysis.filledBlocks()),
                "unique", Integer.toString(analysis.uniqueMaterialCount())
        );
        sendMenuLines(player, PALETTE_ANALYSIS_MENU, "header", List.of(
                "&6Bayzyl&a - Builder tools",
                "&aPalette&8 | &f{size}"
        ), baseTokens);
        sendMenuLine(player, PALETTE_ANALYSIS_MENU, "summary",
                "&fFilled: &f{filled}&8 | &fAir: &f{air}&8 | &fUnique: &f{unique}",
                baseTokens);

        List<String> topMaterials = new ArrayList<>();
        int materialLimit = Math.min(
                listMenuConfigService.intValue(PALETTE_ANALYSIS_MENU, "top-blocks.limit", 8, 1, 32),
                analysis.materials().size()
        );
        for (int i = 0; i < materialLimit; i++) {
            PaletteService.MaterialCount entry = analysis.materials().get(i);
            topMaterials.add(listMenuConfigService.format(
                    PALETTE_ANALYSIS_MENU,
                    "top-blocks.item",
                    "&6{material}&8 &f{count}&f ({percent})",
                    ListMenuConfigService.tokens(
                            "material", entry.displayName(),
                            "count", Integer.toString(entry.count()),
                            "percent", percent(entry.count(), analysis.filledBlocks())
                    )
            ));
        }
        if (topMaterials.isEmpty()) {
            sendMenuLine(player, PALETTE_ANALYSIS_MENU, "top-blocks.empty",
                    "&fTop blocks: &8selection is all air",
                    ListMenuConfigService.tokens());
        } else {
            String separator = listMenuConfigService.format(PALETTE_ANALYSIS_MENU, "top-blocks.separator", "&8 | ", ListMenuConfigService.tokens());
            sendMenuLine(player, PALETTE_ANALYSIS_MENU, "top-blocks.line",
                    "&fTop blocks: {items}",
                    ListMenuConfigService.tokens("items", String.join(separator, topMaterials)));
        }

        List<String> familyLines = new ArrayList<>();
        int familyLimit = Math.min(
                listMenuConfigService.intValue(PALETTE_ANALYSIS_MENU, "families.limit", 5, 1, 32),
                analysis.families().size()
        );
        for (int i = 0; i < familyLimit; i++) {
            PaletteService.FamilyCount family = analysis.families().get(i);
            familyLines.add(listMenuConfigService.format(
                    PALETTE_ANALYSIS_MENU,
                    "families.item",
                    "&b{family}&8 &f{count}&f ({percent})",
                    ListMenuConfigService.tokens(
                            "family", family.family(),
                            "count", Integer.toString(family.count()),
                            "percent", percent(family.count(), analysis.filledBlocks())
                    )
            ));
        }
        if (!familyLines.isEmpty()) {
            String separator = listMenuConfigService.format(PALETTE_ANALYSIS_MENU, "families.separator", "&8 | ", ListMenuConfigService.tokens());
            sendMenuLine(player, PALETTE_ANALYSIS_MENU, "families.line",
                    "&fFamilies: {items}",
                    ListMenuConfigService.tokens("items", String.join(separator, familyLines)));
        }
        sendMenuLine(player, PALETTE_ANALYSIS_MENU, "tip",
                "&bUse /bzl palette swap <from> <to> to push the selection toward a cleaner target palette.",
                ListMenuConfigService.tokens());
    }

    private void sendKitInspect(Player player, BuilderKit kit) {
        Map<String, String> tokens = ListMenuConfigService.tokens(
                "name", kit.name(),
                "scope", formatKitScope(kit.scope()),
                "kind", kit.builtIn() ? "default" : "custom",
                "item_count", Integer.toString(kit.itemCount()),
                "updated", formatProfileTime(kit.updatedAtEpochMillis()),
                "theme", displayKitTheme(kit),
                "author", displayKitAuthor(kit)
        );
        sendMenuLines(player, KIT_INSPECT_MENU, "header", List.of(
                "&6Bayzyl&a - Builder tools",
                "&6{name} &8[&b{scope}&8]"
        ), tokens);
        sendMenuLine(player, KIT_INSPECT_MENU, "summary",
                "&fType: &b{kind}&8 | &fStored: &6{item_count} items",
                tokens);
        sendMenuLine(player, KIT_INSPECT_MENU, "updated",
                "&fUpdated: &b{updated}",
                tokens);
        sendMenuLine(player, KIT_INSPECT_MENU, "theme-author",
                "&fTheme: &6{theme}&8 | &fAuthor: &6{author}",
                tokens);
        String note = displayKitNote(kit);
        if (!note.isEmpty()) {
            sendMenuLine(player, KIT_INSPECT_MENU, "note",
                    "&bNote: &f{note}",
                    ListMenuConfigService.tokens("note", note));
        }
        sendMenuLine(player, KIT_INSPECT_MENU, "load-targets-title", "&bLoad Targets:", ListMenuConfigService.tokens());
        sendMenuLine(player, KIT_INSPECT_MENU, "load-targets-line",
                "&f  {load_targets}",
                ListMenuConfigService.tokens("load_targets", formatKitLoadTargets(kit)));
        if (!kit.aliases().isEmpty()) {
            sendMenuLine(player, KIT_INSPECT_MENU, "aliases-title",
                    "&bAliases: &6{aliases}",
                    ListMenuConfigService.tokens("aliases", String.join(", ", kit.aliases())));
            sendMenuLine(player, KIT_INSPECT_MENU, "aliases-loads-line",
                    "&f  Loads: {alias_loads}",
                    ListMenuConfigService.tokens("alias_loads", formatKitAliasLoads(kit)));
        }
        sendMenuLine(player, KIT_INSPECT_MENU, "contents-title", "&bContents:", ListMenuConfigService.tokens());
        List<String> contentLines = formatKitInspectLines(kit);
        if (contentLines.isEmpty()) {
            sendMenuLine(player, KIT_INSPECT_MENU, "contents-empty", "&f  &8empty", ListMenuConfigService.tokens());
        } else {
            for (String line : contentLines) {
                sendMenuLine(player, KIT_INSPECT_MENU, "contents-row",
                        "&f  {items}",
                        ListMenuConfigService.tokens("items", line));
            }
        }
        if (kit.builtIn()) {
            sendMenuLine(player, KIT_INSPECT_MENU, "built-in-footer",
                    "&8This default kit can be customized, deleted, or restored with /kit restoredefaults.",
                    ListMenuConfigService.tokens());
        }
    }

    private KitUpdateCheck analyzeKitUpdate(Player player, BuilderKit kit) {
        Map<Integer, org.bukkit.inventory.ItemStack> storedBySlot = new HashMap<>();
        for (BuilderKit.SlotItem slotItem : kit.items()) {
            if (slotItem.item() == null || slotItem.item().getType() == Material.AIR) {
                continue;
            }
            storedBySlot.put(slotItem.slot(), slotItem.item());
        }

        int missingStoredSlots = 0;
        int extraFilledSlots = 0;
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        for (int slot = kit.scope().startSlot(); slot < kit.scope().endSlotExclusive(); slot++) {
            org.bukkit.inventory.ItemStack current = inventory.getItem(slot);
            org.bukkit.inventory.ItemStack expected = storedBySlot.get(slot);
            if (expected != null) {
                if (!kitItemsMatch(current, expected)) {
                    missingStoredSlots++;
                }
                continue;
            }
            if (current != null && current.getType() != Material.AIR) {
                extraFilledSlots++;
            }
        }
        return new KitUpdateCheck(missingStoredSlots, extraFilledSlots);
    }

    private boolean kitItemsMatch(org.bukkit.inventory.ItemStack current, org.bukkit.inventory.ItemStack expected) {
        if (expected == null || expected.getType() == Material.AIR) {
            return current == null || current.getType() == Material.AIR;
        }
        if (current == null || current.getType() == Material.AIR) {
            return false;
        }
        return current.isSimilar(expected);
    }

    private void clearPendingKitUpdate(Player player, String kitName) {
        PendingKitUpdate pending = pendingKitUpdates.get(player.getUniqueId());
        if (pending != null && pending.kitName().equals(kitName)) {
            pendingKitUpdates.remove(player.getUniqueId());
        }
    }

    private List<String> formatKitInspectLines(BuilderKit kit) {
        if (kit.items().isEmpty()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (BuilderKit.SlotItem slotItem : kit.items()) {
            entries.add(ChatColor.GOLD + String.valueOf(slotItem.slot() + 1)
                    + ChatColor.DARK_GRAY + ":"
                    + ChatColor.WHITE + itemLabel(slotItem.item()));
        }
        List<String> lines = new ArrayList<>();
        final int chunkSize = 5;
        for (int index = 0; index < entries.size(); index += chunkSize) {
            List<String> chunk = entries.subList(index, Math.min(entries.size(), index + chunkSize));
            lines.add(String.join(ChatColor.DARK_GRAY + " | ", chunk));
        }
        return List.copyOf(lines);
    }

    private String formatKitItems(BuilderKit kit) {
        if (kit.items().isEmpty()) {
            return ChatColor.DARK_GRAY + "empty";
        }
        List<String> parts = new ArrayList<>();
        for (BuilderKit.SlotItem slotItem : kit.items()) {
            parts.add((slotItem.slot() + 1) + ":" + itemLabel(slotItem.item()));
        }
        return String.join(ChatColor.DARK_GRAY + ", " + ChatColor.WHITE, parts);
    }

    private String itemLabel(org.bukkit.inventory.ItemStack item) {
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String displayKitTheme(BuilderKit kit) {
        return (kit.theme() == null || kit.theme().isBlank()) ? "Custom Kits" : kit.theme();
    }

    private String displayKitAuthor(BuilderKit kit) {
        return (kit.author() == null || kit.author().isBlank()) ? (kit.builtIn() ? "Bayzyl" : "unknown") : kit.author();
    }

    private String displayKitNote(BuilderKit kit) {
        if (kit.note() != null && !kit.note().isBlank()) {
            return kit.note();
        }
        return kit.builtIn()
                ? "Default Bayzyl build kit for fast palette loading and builder workflow setup."
                : "Custom shared " + scopeNoun(kit.scope()) + " captured from a live builder loadout.";
    }

    private String formatKitLoadTargets(BuilderKit kit) {
        List<String> loads = new ArrayList<>();
        loads.add(ChatColor.WHITE + "/kit " + kit.name());
        loads.add(ChatColor.WHITE + "/bzl " + kit.name());
        if (kitShortcutRegistry.isShortcutRegistered(kit.name())) {
            loads.add(ChatColor.WHITE + "/" + kit.name());
        }
        return String.join(ChatColor.DARK_GRAY + " | ", loads);
    }

    private String formatKitAliasLoads(BuilderKit kit) {
        List<String> loads = new ArrayList<>();
        for (String alias : kit.aliases()) {
            StringBuilder value = new StringBuilder(ChatColor.WHITE + "/kit " + alias);
            if (kitShortcutRegistry.isShortcutRegistered(alias)) {
                value.append(ChatColor.DARK_GRAY).append(" | ").append(ChatColor.WHITE).append("/").append(alias);
            }
            loads.add(value.toString());
        }
        return String.join(ChatColor.DARK_GRAY + " | ", loads);
    }

    private Map<String, String> kitListTokens(BuilderKitSummary summary) {
        String shortcut = kitShortcutRegistry.isShortcutRegistered(summary.name())
                ? "/" + summary.name()
                : "/kit " + summary.name();
        return ListMenuConfigService.tokens(
                "name", summary.name(),
                "scope", formatKitScope(summary.scope()),
                "item_count", Integer.toString(summary.itemCount()),
                "kind", summary.builtIn() ? "default" : "custom",
                "theme", kitTheme(summary.theme(), KIT_LIST_MENU),
                "shortcut", shortcut
        );
    }

    private List<KitListPage> buildKitListPages() {
        List<BuilderKitSummary> allKits = builderKitService.listKitSummaries();
        if (allKits.isEmpty()) {
            return List.of();
        }

        List<String> themeOrder = listMenuConfigService.stringList(KIT_LIST_MENU, "theme-order", DEFAULT_KIT_THEME_ORDER);
        int pageSize = listMenuConfigService.intValue(KIT_LIST_MENU, "page-size", 12, 1, 100);
        Map<String, List<BuilderKitSummary>> grouped = new LinkedHashMap<>();
        for (String theme : themeOrder) {
            grouped.put(theme, new ArrayList<>());
        }
        for (BuilderKitSummary summary : allKits) {
            String theme = kitTheme(summary.theme(), KIT_LIST_MENU);
            grouped.computeIfAbsent(theme, ignored -> new ArrayList<>()).add(summary);
        }

        List<KitListPage> pages = new ArrayList<>();
        grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .sorted(Comparator.<Map.Entry<String, List<BuilderKitSummary>>>comparingInt(entry -> {
                    int index = themeOrder.indexOf(entry.getKey());
                    return index >= 0 ? index : 1000;
                }).thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    List<BuilderKitSummary> kits = entry.getValue().stream()
                            .sorted(Comparator.comparing(BuilderKitSummary::name, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    for (int start = 0; start < kits.size(); start += pageSize) {
                        int pageIndex = (start / pageSize) + 1;
                        List<BuilderKitSummary> chunk = kits.subList(start, Math.min(kits.size(), start + pageSize));
                        Map<String, String> titleTokens = ListMenuConfigService.tokens(
                                "theme", entry.getKey(),
                                "theme_page", Integer.toString(pageIndex)
                        );
                        String titlePath = kits.size() <= pageSize ? "title-single" : "title-split";
                        String titleFallback = kits.size() <= pageSize ? "{theme}" : "{theme} {theme_page}";
                        pages.add(new KitListPage(
                                listMenuConfigService.format(KIT_LIST_MENU, titlePath, titleFallback, titleTokens),
                                List.copyOf(chunk)
                        ));
                    }
                });
        return pages;
    }

    private String kitTheme(String theme, String menuId) {
        if (theme == null || theme.isBlank()) {
            return listMenuConfigService.value(menuId, "default-theme", "Custom Kits");
        }
        return theme;
    }

    private String joinArgs(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return "";
        }
        return String.join(" ", List.of(args).subList(startIndex, args.length)).trim();
    }

    private void equipInMainHand(Player player, ItemStack item, String message) {
        player.getInventory().setItemInMainHand(item);
        player.sendMessage(ChatColor.WHITE + message);
    }

    private void equipBrushInMainHand(Player player, ItemStack item) {
        ItemStack previous = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(item);
        if (previous != null && previous.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(previous);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    private boolean canManageServerKits(Player player, String permission) {
        return player.isOp() || player.hasPermission("bayzyl.admin") || player.hasPermission(permission);
    }

    private String scopeNoun(BuilderKitScope scope) {
        return scope == BuilderKitScope.INVENTORY ? "inventory" : "hotbar";
    }

    private String formatSignedDelta(int delta) {
        if (delta > 0) {
            return ChatColor.GREEN + "+" + delta;
        }
        if (delta < 0) {
            return ChatColor.RED + String.valueOf(delta);
        }
        return ChatColor.WHITE + "0";
    }

    private String percent(long count, long total) {
        if (total <= 0L) {
            return "0%";
        }
        double value = (count * 100.0D) / total;
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private List<String> suggestProfileArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("save", "update", "load", "inspect", "list", "delete", "rename", "duplicate"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("load") || sub.equals("inspect") || sub.equals("delete") || sub.equals("update")
                    || sub.equals("rename") || sub.equals("duplicate")) {
                return filterPrefix(builderProfileService.listProfiles(), args[1]);
            }
            return Collections.emptyList();
        }

        if (args.length == 3) {
            if (sub.equals("save")) {
                return filterPrefix(List.of("type:combined", "type:config", "type:toolbar", "overwrite:true"), args[2]);
            }
            if (sub.equals("load") || sub.equals("update")) {
                return filterPrefix(List.of("section:all", "section:config", "section:toolbar"), args[2]);
            }
            if (sub.equals("rename") || sub.equals("duplicate")) {
                return Collections.emptyList();
            }
        }

        if (args.length >= 4) {
            if (sub.equals("save")) {
                return filterPrefix(List.of("type:combined", "type:config", "type:toolbar", "overwrite:true"), args[args.length - 1]);
            }
            if (sub.equals("load") || sub.equals("update")) {
                return filterPrefix(List.of("section:all", "section:config", "section:toolbar"), args[args.length - 1]);
            }
            if (sub.equals("rename") || sub.equals("duplicate")) {
                return filterPrefix(List.of("overwrite:true"), args[args.length - 1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> suggestKitArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(filterPrefix(List.of("list", "menu", "inspect", "load", "delete", "rename", "duplicate", "update", "confirm", "restoredefaults", "note", "theme", "icon", "alias"), args[0]));
            suggestions.addAll(filterPrefix(builderKitService.listLoadLabels(), args[0]));
            return mergeSuggestions(suggestions);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("list") || sub.equals("menu"))) {
            return suggestKitListArgs(new String[]{args[1]});
        }
        if (args.length == 2 && (sub.equals("inspect") || sub.equals("load") || sub.equals("delete")
                || sub.equals("update") || sub.equals("rename") || sub.equals("duplicate")
                || sub.equals("note") || sub.equals("theme") || sub.equals("icon"))) {
            return filterPrefix(builderKitService.listKitNames(), args[1]);
        }
        if (args.length == 2 && sub.equals("alias")) {
            return filterPrefix(List.of("list", "add", "remove"), args[1]);
        }
        if (args.length == 3 && sub.equals("icon")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("clear");
            suggestions.addAll(filterPrefix(SuggestionUtil.suggest("set", new String[]{args[2]}), args[2]));
            return mergeSuggestions(suggestions);
        }
        if (args.length == 3 && sub.equals("alias") && args[1].equalsIgnoreCase("list")) {
            return filterPrefix(builderKitService.listKitNames(), args[2]);
        }
        if (args.length == 3 && sub.equals("alias") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return filterPrefix(builderKitService.listKitNames(), args[2]);
        }
        if (args.length == 4 && sub.equals("alias") && args[1].equalsIgnoreCase("remove")) {
            BuilderKit kit = builderKitService.loadKit(args[2]);
            return kit == null ? Collections.emptyList() : filterPrefix(kit.aliases(), args[3]);
        }
        if (args.length == 4 && sub.equals("alias") && args[1].equalsIgnoreCase("add")) {
            return Collections.emptyList();
        }
        if (args.length >= 4 && (sub.equals("rename") || sub.equals("duplicate"))) {
            return filterPrefix(List.of("overwrite:true"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestKitListArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        int pages = buildKitListPages().size();
        if (pages <= 0) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            options.add(String.valueOf(page));
        }
        return filterPrefix(options, args[0]);
    }

    private List<String> suggestKitMakeArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("hotbar", "inventory"), args[0]);
        }
        if (args.length == 2) {
            return filterPrefix(builderKitService.listKitNames(), args[1]);
        }
        if (args.length >= 3) {
            return filterPrefix(List.of("overwrite:true"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestKitUpdateArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(builderKitService.listKitNames(), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestCleanupArgs(String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup", "brush"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("brush")) {
            return filterPrefix(List.of("floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("brush")) {
            return filterPrefix(List.of("3", "5", "8"), args[2]);
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("brush")) {
            return filterPrefix(List.of("1", "2", "3", "4"), args[args.length - 1]);
        }
        return filterPrefix(List.of("confirm:true"), args[args.length - 1]);
    }

    private List<String> suggestBrushRootArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of(
                    "save", "load", "list", "delete",
                    "sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid", "naturalize",
                    "clipboard", "paint", "spatter", "replace", "blend", "surface", "noise", "restore", "vegetation", "decay",
                    "erase", "smooth", "raise", "lower", "flatten",
                    "floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup", "none", "info"
            ), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("delete")) {
                return filterPrefix(brushPresetService.listBrushes(), args[1]);
            }
            if (args[0].equalsIgnoreCase("save")) {
                return Collections.emptyList();
            }
            if (args[0].equalsIgnoreCase("none") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("clipboard") || args[0].equalsIgnoreCase("clip")) {
                return Collections.emptyList();
            }
            if (args[0].equalsIgnoreCase("erase") || args[0].equalsIgnoreCase("eraser")
                    || TerrainBrushType.parse(args[0]) != null) {
                return filterPrefix(List.of("3", "5", "8", "12"), args[1]);
            }
            PatternBrushMode patternMode = PatternBrushMode.parse(args[0]);
            if (patternMode != null) {
                if (patternMode == PatternBrushMode.RESTORE) {
                    return filterPrefix(List.of("size:5", "size:8", "size:12"), args[1]);
                }
                if (patternMode == PatternBrushMode.VEGETATION) {
                    return filterPrefix(List.of("grass", "flowers", "mushrooms", "saplings", "size:5", "density:0.35", "mask:"), args[1]);
                }
                return SuggestionUtil.suggest("set", new String[]{args[1]});
            }
            return SuggestionUtil.suggest("set", new String[]{args[1]});
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("replace")) {
            return SuggestionUtil.suggest("set", new String[]{args[2]});
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("paint")) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                return SuggestionUtil.maskSuggestions(current);
            }
            return filterPrefix(List.of("size:5", "size:8", "size:12", "density:0.2", "density:0.3", "density:0.5", "density:0.8", "mask:"), current);
        }
        if (args.length >= 3 && PatternBrushMode.parse(args[0]) != null) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                return SuggestionUtil.maskSuggestions(current);
            }
            return filterPrefix(List.of("size:5", "size:8", "size:12", "density:0.2", "density:0.35", "density:0.5", "density:0.8", "mask:", "palette:"), current);
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("erase") || args[0].equalsIgnoreCase("eraser"))) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                return SuggestionUtil.maskSuggestions(current);
            }
            return filterPrefix(List.of("mask:", "surface:on", "surface:off", "selection:only", "selection:any", "carve:on", "carve:off", "bedrock:on", "bedrock:off"), current);
        }
        if (args.length >= 3 && TerrainBrushType.parse(args[0]) != null) {
            return filterPrefix(List.of("1", "2", "3", "bedrock:on", "bedrock:off"), args[args.length - 1]);
        }
        if (args.length >= 3) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                return SuggestionUtil.maskSuggestions(current);
            }
            List<String> suggestions = new ArrayList<>();
            suggestions.add("mask:");
            suggestions.add("at:target");
            suggestions.add("at:selection-center");
            suggestions.add("confirm:true");
            return filterPrefix(suggestions, current);
        }
        return Collections.emptyList();
    }

    private long estimateBrush(ShapeBrushSettings settings) {
        return switch (settings.type()) {
            case SPHERE, HSPHERE -> Math.max(1L, shapeService.estimateSphere(new SphereRequest(
                    settings.material(),
                    settings.radiusX(),
                    settings.radiusY(),
                    settings.radiusZ(),
                    settings.type().hollow(),
                    settings.anchorMode(),
                    1,
                    false,
                    false,
                    settings.confirm(),
                    settings.mask()
            )));
            case CYL, HCYL -> shapeService.estimateCylinder(new CylinderRequest(
                    settings.material(),
                    settings.radiusX(),
                    settings.radiusZ(),
                    settings.height(),
                    settings.type().hollow(),
                    settings.anchorMode(),
                    1,
                    false,
                    false,
                    settings.confirm(),
                    settings.mask()
            ));
            case PYRAMID, HPYRAMID -> shapeService.estimatePyramid(new PyramidRequest(
                    settings.material(),
                    settings.size(),
                    settings.type().hollow(),
                    settings.anchorMode(),
                    false,
                    settings.confirm(),
                    settings.mask()
            ));
        };
    }

    private String describeBrushShape(ShapeBrushSettings settings) {
        return switch (settings.type()) {
            case SPHERE, HSPHERE ->
                    settings.type().displayName() + " r:" + settings.radiusX();
            case CYL, HCYL ->
                    settings.type().displayName() + " r:" + settings.radiusX() + " h:" + settings.height();
            case PYRAMID, HPYRAMID ->
                    settings.type().displayName() + " size:" + settings.size();
        };
    }

    private List<String> suggestBzlArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(SuggestionUtil.suggest("bzl", args));
            suggestions.addAll(filterPrefix(builderKitService.listLoadLabels(), args[0]));
            return mergeSuggestions(suggestions);
        }
        if (args[0].equalsIgnoreCase("help")) {
            if (args.length == 2) {
                return SuggestionUtil.suggest("bzl", new String[]{"help", args[1]});
            }
            return Collections.emptyList();
        }
        if (args[0].equalsIgnoreCase("cleanup")) {
            if (args.length == 2) {
                return filterPrefix(List.of("floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup", "brush"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("brush")) {
                return filterPrefix(List.of("floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup"), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("brush")) {
                return filterPrefix(List.of("3", "5", "8"), args[3]);
            }
            if (args.length >= 5 && args[1].equalsIgnoreCase("brush")) {
                return filterPrefix(List.of("1", "2", "3", "4"), args[args.length - 1]);
            }
            return filterPrefix(List.of("confirm:true"), args[args.length - 1]);
        }
        if (args[0].equalsIgnoreCase("brush")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestBrushRootArgs(remapped);
        }
        if (args[0].equalsIgnoreCase("palette")) {
            if (args.length == 2) {
                return filterPrefix(List.of("analyze", "swap"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("swap")) {
                return SuggestionUtil.suggest("set", new String[]{args[2]});
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("swap")) {
                return SuggestionUtil.suggest("set", new String[]{args[3]});
            }
            if (args.length >= 5 && args[1].equalsIgnoreCase("swap")) {
                return filterPrefix(List.of("confirm:true"), args[args.length - 1]);
            }
        }
        if (args[0].equalsIgnoreCase("kitupdate")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return suggestKitUpdateArgs(remapped);
        }
        if (args[0].equalsIgnoreCase("kitconfirm")) {
            return Collections.emptyList();
        }
        if (builderKitService.existsKit(args[0])) {
            return Collections.emptyList();
        }
        return SuggestionUtil.suggest("bzl", args);
    }

    private List<String> mergeSuggestions(List<String> suggestions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(suggestions);
        return new ArrayList<>(merged);
    }

    private record KitListPage(String title, List<BuilderKitSummary> kits) {
    }

    private record PendingKitUpdate(String kitName, long createdAtEpochMillis) {
    }

    private record KitUpdateCheck(int missingStoredSlots, int extraFilledSlots) {
    }

    private Integer parseDurationSeconds(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.toLowerCase(Locale.ROOT).trim();
        try {
            if (value.endsWith("ms")) {
                return Math.max(1, Integer.parseInt(value.substring(0, value.length() - 2)) / 1000);
            }
            if (value.endsWith("min")) {
                return Integer.parseInt(value.substring(0, value.length() - 3)) * 60;
            }
            if (value.endsWith("m")) {
                return Integer.parseInt(value.substring(0, value.length() - 1)) * 60;
            }
            if (value.endsWith("sec")) {
                return Integer.parseInt(value.substring(0, value.length() - 3));
            }
            if (value.endsWith("s")) {
                return Integer.parseInt(value.substring(0, value.length() - 1));
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ChatColor getBiomeColor(Biome biome) {
        String name = biome.name().toLowerCase(Locale.ROOT);
        if (name.contains("desert")) return ChatColor.GOLD;
        if (name.contains("forest") || name.contains("jungle") || name.contains("taiga")) return ChatColor.DARK_GREEN;
        if (name.contains("ocean") || name.contains("sea") || name.contains("river")) return ChatColor.AQUA;
        if (name.contains("mountain") || name.contains("peak")) return ChatColor.GRAY;
        if (name.contains("plains") || name.contains("meadow")) return ChatColor.GREEN;
        if (name.contains("savanna")) return ChatColor.YELLOW;
        if (name.contains("swamp") || name.contains("mangrove")) return ChatColor.DARK_GREEN;
        if (name.contains("snow") || name.contains("ice") || name.contains("frozen")) return ChatColor.AQUA;
        if (name.contains("badlands")) return ChatColor.RED;
        if (name.contains("nether")) return ChatColor.DARK_RED;
        if (name.contains("end")) return ChatColor.LIGHT_PURPLE;
        return ChatColor.WHITE;
    }

    private boolean isValidIfMode(String mode) {
        return mode != null && (mode.equals("any") || mode.equals("air") || mode.equals("solid"));
    }
}
