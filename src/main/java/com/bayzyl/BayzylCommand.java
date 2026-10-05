package com.bayzyl;

import com.bayzyl.security.BayzylAccess;
import com.bayzyl.security.CommandAccessPolicy;
import com.bayzyl.security.CommandCapability;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.BrushSafety;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Cat;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.bayzyl.generation.FeatureGenRequest;
import com.bayzyl.generation.ForestGenRequest;
import com.bayzyl.generation.StructureGenRequest;
import com.bayzyl.generation.GenerateBiomeRequest;
import com.bayzyl.generation.GenerateShapeRequest;
import com.bayzyl.generation.GeneratorCommandParser;
import com.bayzyl.generation.GeneratorResult;
import com.bayzyl.generation.PumpkinPatchRequest;
import com.bayzyl.generation.VanillaContentRegistry;

import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.ThreadLocalRandom;

public final class BayzylCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private static final int NUDGE_HELP_PAGE = 11;
    private static final int PROFILES_HELP_PAGE = 12;
    private static final int KITS_HELP_PAGE = 13;
    private static final int TABMENU_HELP_PAGE = 16;
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

    private static final Map<String, String> UNSORTED_DESCRIPTIONS = buildUnsortedDescriptions();

    private static Map<String, String> buildUnsortedDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("kithelp", "Open the Bayzyl kits help page. Pass a topic name (kits, profiles, etc.) to jump elsewhere.");
        map.put("ramalert", "Watch server JVM memory and warn staff before pressure gets dangerous. Configure threshold, interval, and cooldown.");
        map.put("step", "Move yourself forward through any blocks in the way. Pass a block count to control distance.");
        map.put("clearhistory", "Wipe your personal Bayzyl undo and redo history. Frees memory tied to your past edits.");
        map.put("dome", "Build a solid dome (top half of a sphere) at the chosen anchor. Pick block, radius, and target.");
        map.put("hdome", "Build a hollow dome at the chosen anchor. Good for roofs and arenas without filling the interior.");
        map.put("bowl", "Build a solid bowl (bottom half of a sphere) at the chosen anchor. Pick block, radius, and target.");
        map.put("hbowl", "Build a hollow bowl at the chosen anchor. Good for pools, craters, or amphitheater shells.");
        map.put("brushgen", "Bind a parametric terrain brush (ridge, plateau, valley, cave, etc.). Tunable size, intensity, height, and seed.");
        map.put("gmask", "Set a global mask that filters every edit you perform, not just one brush. Run /gmask with no args to toggle on/off; pass none to clear it.");
        map.put("genfeature", "Place a vanilla configured feature (tree, ore vein, etc.) at an anchor. Useful for sprinkling in worldgen detail.");
        map.put("genstructure", "Place a vanilla structure (village, fortress, etc.) at an anchor. Defaults to your target block.");
        map.put("regen", "Re-run the last /genstructure or /genfeature in the same place. Use this to reroll a randomized result.");
        map.put("schematic", "Save, load, list, or orient .schem files. Use this to share builds across worlds or servers.");
        map.put("brushmenu", "Open the in-game GUI for browsing saved brushes. Faster than typing /brush load by name.");
        map.put("clearclipboard", "Empty your Bayzyl clipboard while keeping undo history intact. Frees memory without losing your edit log.");
        map.put("trailclear", "Clear your recent edit trail breadcrumbs. The tab menu trail panel resets to empty.");
        map.put("pos1", "Set selection corner 1 with an anchor option. Pass at:player, at:target, or at:selection-center.");
        map.put("pos2", "Set selection corner 2 with an anchor option. Pass at:player, at:target, or at:selection-center.");
        map.put("jail", "Trap a player in a small cell as an admin-mode prank. Release them with /liberate.");
        map.put("liberate", "Free a previously jailed player and restore their position. Counterpart to /jail.");
        map.put("bubu", "Broadcast a cute pink chat phrase. Pure flavor command with no gameplay effect.");
        map.put("lol", "Broadcast haha as a deploy smoke test. Confirms the plugin reloaded and chat output works.");
        map.put("susu", "Spawn a calico cat named Susu. Friend command, just for fun.");
        map.put("artie", "Spawn a tuxedo cat named Artie. Friend command, just for fun.");
        map.put("detailbrush", "Bind a preset detail brush (flame, cloud, lightning, vine, bark). Supports presets, variants, and code save/load.");
        map.put("resume", "Retry an interrupted copy using its saved selection. Other command types are not resumable.");
        map.put("agitate", "Trigger block physics updates on stuck fluids in your selection. Use it when water or lava refuses to flow.");
        return Collections.unmodifiableMap(map);
    }

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
    private final StackLookDirectionService stackLookDirectionService;
    private final StackAutoMoveService stackAutoMoveService;
    private final MovementAssistService movementAssistService;
    private final CleanupService cleanupService;
    private final NaturalizeService naturalizeService;
    private final PaletteService paletteService;
    private final SchematicService schematicService;
    private final BrushPresetService brushPresetService;
    private final com.bayzyl.detail.DetailBrushService detailBrushService;
    private final com.bayzyl.detail.DetailBrushVariantService detailBrushVariantService;
    private final BuilderProfileService builderProfileService;
    private final BuilderKitService builderKitService;
    private final KitShortcutRegistry kitShortcutRegistry;
    private final DetailBrushShortcutRegistry detailBrushShortcutRegistry;
    private final KitMenuService kitMenuService;
    private final BrushMenuService brushMenuService;
    private final NudgeSettingsService nudgeSettingsService;
    private final AdminModeService adminModeService;
    private final BayzylAccess bayzylAccess;
    private final CommandAccessPolicy commandAccessPolicy;
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
    private final MessageThemeService messageThemeService;
    private final CommandAuthorityService commandAuthorityService;
    private final CrashRecoveryService crashRecoveryService;
    private com.bayzyl.redstone.RedstoneAuditCommand redstoneAuditCommand;
    private com.bayzyl.gen.GenBrushService genBrushService;
    private GlobalMaskService globalMaskService;
    private final Map<UUID, PendingKitUpdate> pendingKitUpdates = new HashMap<>();
    private final Map<UUID, JailSnapshot> activeJailSnapshots = new HashMap<>();

    public BayzylCommand(JavaPlugin plugin,
                         ToolManager toolManager,
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
                         StackLookDirectionService stackLookDirectionService,
                         StackAutoMoveService stackAutoMoveService,
                         MovementAssistService movementAssistService,
                         CleanupService cleanupService,
                         NaturalizeService naturalizeService,
                         PaletteService paletteService,
                         SchematicService schematicService,
                         BrushPresetService brushPresetService,
                         com.bayzyl.detail.DetailBrushService detailBrushService,
                         com.bayzyl.detail.DetailBrushVariantService detailBrushVariantService,
                         BuilderProfileService builderProfileService,
                         BuilderKitService builderKitService,
                         KitShortcutRegistry kitShortcutRegistry,
                         DetailBrushShortcutRegistry detailBrushShortcutRegistry,
                         KitMenuService kitMenuService,
                         BrushMenuService brushMenuService,
                         NudgeSettingsService nudgeSettingsService,
                         AdminModeService adminModeService,
                         BayzylAccess bayzylAccess,
                         CommandAccessPolicy commandAccessPolicy,
                         RamAlertService ramAlertService,
                         HelpContentService helpContentService,
                         ListMenuConfigService listMenuConfigService,
                         RuntimePreferencesService runtimePreferencesService,
                         SelectionBookmarkService selectionBookmarkService,
                         TabMenuSettingsService tabMenuSettingsService,
                         TabInfoPanelService tabInfoPanelService,
                         RecentEditTrailService recentEditTrailService,
                         DecoyPlayerCountService decoyPlayerCountService,
DecoyTabListService decoyTabListService,
                         MessageThemeService messageThemeService,
                         CommandAuthorityService commandAuthorityService,
                          CrashRecoveryService crashRecoveryService) {
        this.plugin = plugin;
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
        this.stackLookDirectionService = stackLookDirectionService;
        this.stackAutoMoveService = stackAutoMoveService;
        this.movementAssistService = movementAssistService;
        this.cleanupService = cleanupService;
        this.naturalizeService = naturalizeService;
        this.paletteService = paletteService;
        this.schematicService = schematicService;
        this.brushPresetService = brushPresetService;
        this.detailBrushService = detailBrushService;
        this.detailBrushVariantService = detailBrushVariantService;
        this.builderProfileService = builderProfileService;
        this.builderKitService = builderKitService;
        this.kitShortcutRegistry = kitShortcutRegistry;
        this.detailBrushShortcutRegistry = detailBrushShortcutRegistry;
        this.kitMenuService = kitMenuService;
        this.brushMenuService = brushMenuService;
        this.nudgeSettingsService = nudgeSettingsService;
        this.adminModeService = adminModeService;
        this.bayzylAccess = bayzylAccess;
        this.commandAccessPolicy = commandAccessPolicy;
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
        this.messageThemeService = messageThemeService;
        this.commandAuthorityService = commandAuthorityService;
        this.crashRecoveryService = crashRecoveryService;
        if (crashRecoveryService != null) {
            crashRecoveryService.registerResumeHandler("copy", new CopyResumeHandler(this::runCopy));
        }
    }

    /**
     * Setter-injection for the gen brush service. Avoids touching the
     * already-large constructor signature.
     */
    public void setGenBrushService(com.bayzyl.gen.GenBrushService genBrushService) {
        this.genBrushService = genBrushService;
    }

    public void setGlobalMaskService(GlobalMaskService globalMaskService) {
        this.globalMaskService = globalMaskService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && (editService.hasPasteTask(player.getUniqueId())
                || historyService.hasAsyncHistoryTask(player.getUniqueId()))) {
            ChatOutput.send(sender, ChatColor.RED + "Wait for your current edit or undo/redo to finish.");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        CommandCapability capability = requiredCapability(cmd, args);
        if (!bayzylAccess.allowed(sender, capability)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to use this Bayzyl command.");
            return true;
        }
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
        if (cmd.equals("memreset")) {
            return handleMemResetRoot(sender, args);
        }
        if (cmd.equals("clearclipboard")) {
            return handleClearClipboard(sender, args);
        }
        if (cmd.equals("accent") || cmd.equals("bzlaccent")) {
            return handleAccentRoot(sender, args);
        }
        if (cmd.equals("authority") || cmd.equals("bzlauthority") || cmd.equals("commandauthority")) {
            return handleAuthorityRoot(sender, args);
        }
        if (cmd.equals("tool") || cmd.equals("bzltool")) {
            return handleToolRoot(sender, args);
        }
        if (cmd.equals("floatingcleanup") || cmd.equals("foliagecleanup")
                || cmd.equals("liquidcleanup") || cmd.equals("snowcleanup") || cmd.equals("lightcleanup")) {
            return handleCleanupTypeRoot(sender, cmd, args);
        }
        if (cmd.equals("wetoggle")) {
            return handleWetoggle(sender, args);
        }
        if (cmd.equals("step")) {
            return handleStepRoot(sender, args);
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
        if (cmd.equals("schematic") || cmd.equals("schem")) {
            return handleSchematicRoot(sender, args);
        }
        if (cmd.equals("brush")) {
            String[] remapped = new String[args.length + 1];
            remapped[0] = "brush";
            if (args.length > 0) {
                System.arraycopy(args, 0, remapped, 1, args.length);
            }
            return handleBzl(sender, remapped);
        }
        if (cmd.equals("detailbrush") || cmd.equals("db")) {
            return handleDetailBrushRoot(sender, args);
        }
        if (cmd.equals("brushmenu") || cmd.equals("bm")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            return brushMenuService.openMenu(player);
        }
        if (detailBrushShortcutRegistry.isShortcutRegistered(cmd)) {
            return handleDetailBrushShortcutRoot(sender, cmd, args);
        }
        if (kitShortcutRegistry.isShortcutRegistered(cmd)) {
            return handleKitShortcutRoot(sender, cmd, args);
        }

        return handleTopLevel(sender, cmd, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        boolean candidateFiltered = cmd.equals("bzl") || cmd.equals("bayzyl")
                || cmd.equals("brush") || cmd.equals("bzltoggle")
                || cmd.equals("kit") || cmd.equals("kitmake") || cmd.equals("kitupdate")
                || cmd.equals("kitconfirm");
        if (!candidateFiltered && !bayzylAccess.allowed(sender, requiredCapability(cmd, args))) {
            return Collections.emptyList();
        }
        if (cmd.equals("bzltoggle")) {
            List<String> suggestions = commandAccessPolicy.toggleSuggestions(args);
            return filterProspectiveSuggestions(sender, cmd, args, suggestions);
        }
        if (cmd.equals("bzlhelp") || cmd.equals("bayzylhelp") || cmd.equals("bzyzylhelp")) {
            return suggestBzlHelpArgs(args);
        }
        if (cmd.equals("kithelp")) {
            return suggestBzlHelpArgs(args);
        }
        if (cmd.equals("kitlist")) {
            return suggestKitListArgs(args);
        }
        if (cmd.equals("kitupdate")) {
            return filterProspectiveSuggestions(sender, cmd, args, suggestKitUpdateArgs(args));
        }
        if (cmd.equals("kitconfirm")) {
            return Collections.emptyList();
        }
        if (cmd.equals("profile")) {
            return suggestProfileArgs(args);
        }
        if (cmd.equals("kit")) {
            return filterProspectiveSuggestions(sender, cmd, args, suggestKitArgs(args));
        }
        if (cmd.equals("kitmake")) {
            return filterProspectiveSuggestions(sender, cmd, args, suggestKitMakeArgs(args));
        }
        if (cmd.equals("cleanup")) {
            return suggestCleanupArgs(args);
        }
        if (cmd.equals("palette")) {
            return suggestPaletteArgs(args);
        }
        if (cmd.equals("schematic") || cmd.equals("schem")) {
            return suggestSchematicArgs(args);
        }
        if (cmd.equals("brush")) {
            return filterProspectiveSuggestions(sender, cmd, args, suggestBrushRootArgs(args));
        }
        if (cmd.equals("brushgen") || cmd.equals("genbrush")) {
            return suggestBrushGenRoot(args);
        }
        if (cmd.equals("detailbrush") || cmd.equals("db")) {
            return suggestDetailBrushArgs(sender, args);
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("brush")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return filterProspectiveSuggestions(sender, cmd, args, suggestBrushRootArgs(remapped));
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
            return filterProspectiveSuggestions(sender, cmd, args, suggestKitArgs(remapped));
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kitmake")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return filterProspectiveSuggestions(sender, cmd, args, suggestKitMakeArgs(remapped));
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kitupdate")) {
            String[] remapped = new String[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 0, args.length - 1);
            }
            return filterProspectiveSuggestions(sender, cmd, args, suggestKitUpdateArgs(remapped));
        }
        if ((cmd.equals("bzl") || cmd.equals("bayzyl")) && args.length >= 1 && args[0].equalsIgnoreCase("kitconfirm")) {
            return Collections.emptyList();
        }
        if (cmd.equals("bzl") || cmd.equals("bayzyl")) {
            return filterProspectiveSuggestions(sender, cmd, args, suggestBzlArgs(args));
        }
        if (detailBrushShortcutRegistry.isShortcutRegistered(cmd) || kitShortcutRegistry.isShortcutRegistered(cmd)) {
            return Collections.emptyList();
        }
        if (cmd.equals("selload") && sender instanceof Player player && args.length == 1) {
            return filterPrefix(selectionBookmarkService.list(player.getUniqueId()), args[0]);
        }
        if (isTopLevelSuggestionCommand(cmd)) {
            return suggestTopLevelArgs(cmd, args);
        }
        return SuggestionUtil.suggest(cmd, args);
    }

    private boolean handleBzl(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHeader(sender);
            ChatOutput.send(sender, ChatColor.WHITE + "Use /bzl help for commands.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            if (args.length == 1) {
                sendHelpPage(sender, 1);
                return true;
            }

            String topic = args[1].toLowerCase(Locale.ROOT);
            if (topic.equals("unsorted")) {
                sendUnsortedHelpCommands(sender);
                return true;
            }
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
                ChatOutput.send(sender, ChatColor.RED + "Help page must be 1-" + pageCount + ".");
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

            ChatOutput.send(sender, ChatColor.RED + "Unknown help topic: " + topic);
            return true;
        }

        if (sub.equals("test")) {
            ChatOutput.send(sender, ChatColor.LIGHT_PURPLE + "nya meow anins the best");
            return true;
        }

        if (sub.equals("audit")) {
            return handleRedstoneAudit(sender, Arrays.copyOfRange(args, 1, args.length));
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

        if (sub.equals("authority") || sub.equals("commandauthority")) {
            return handleCommandAuthorityNamespace(sender, args);
        }

        if (sub.equals("debug")) {
            return handleDebugNamespace(sender, args);
        }

        if (sub.equals("nudge")) {
            return handleNudgeNamespace(sender, args);
        }

        if (sub.equals("stacklook")) {
            return handleStackLook(sender, args.length > 1 ? new String[]{args[1]} : new String[0]);
        }

        if (sub.equals("stackautomove")) {
            return handleStackAutoMove(sender, args.length > 1 ? new String[]{args[1]} : new String[0]);
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

        ChatOutput.send(sender, ChatColor.YELLOW + "Not implemented yet: /bzl " + sub);
        ChatOutput.send(sender, ChatColor.WHITE + "Use /bzl help to see available topics.");
        return true;
    }

    private boolean handleTopLevel(CommandSender sender, String cmd, String[] args) {
        Optional<CommandSpec> spec = CommandRegistry.getTopLevel().stream()
                .filter(item -> item.name().equals(cmd))
                .findFirst();
        if (spec.isEmpty()) {
            ChatOutput.send(sender, ChatColor.RED + "Unknown command.");
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
        if (cmd.equals("stacklook")) {
            return handleStackLook(sender, args);
        }
        if (cmd.equals("clipboardinfo")) {
            return handleClipboardInfo(sender);
        }
        if (cmd.equals("clearclipboard")) {
            return handleClearClipboard(sender, args);
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
        if (cmd.equals("pos1")) {
            return handlePos1(sender, args);
        }
        if (cmd.equals("pos2")) {
            return handlePos2(sender, args);
        }
        if (cmd.equals("jail")) {
            return handleJail(sender, args);
        }
        if (cmd.equals("liberate")) {
            return handleLiberate(sender, args);
        }
        if (cmd.equals("bubu")) {
            return handleBubu(sender);
        }
        if (cmd.equals("lol")) {
            return handleLol(sender);
        }
        if (cmd.equals("susu")) {
            return handleCatCommand(sender, "Susu", "CALICO", "TUXEDO", "BLACK", "ALL_BLACK");
        }
        if (cmd.equals("artie")) {
            return handleCatCommand(sender, "Artie", "TUXEDO", "BLACK", "ALL_BLACK", "CALICO");
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
        if (cmd.equals("brushgen") || cmd.equals("genbrush")) {
            if (!(sender instanceof Player p)) {
                ChatOutput.send(sender, ChatColor.RED + "Players only.");
                return true;
            }
            if (args.length < 1) {
                ChatOutput.send(p, ChatColor.RED + "Usage: /brushgen <"
                        + listGenTypes() + "> [opts]");
                return true;
            }
            com.bayzyl.gen.GenBrushType genType = com.bayzyl.gen.GenBrushType.parse(args[0]);
            if (genType == null) {
                ChatOutput.send(p, ChatColor.RED + "Unknown gen brush type: " + args[0]
                        + ". Options: " + listGenTypes());
                return true;
            }
            return handleBrushGen(p, genType, args, 1);
        }
        if (cmd.equals("mask")) {
            return handleBrushMaskCommand(sender, args);
        }
        if (cmd.equals("gmask")) {
            return handleGlobalMaskCommand(sender, args);
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
        if (cmd.equals("genfeature")) {
            return handleGenFeature(sender, args);
        }
        if (cmd.equals("genstructure")) {
            return handleStructureGen(sender, args);
        }
        if (cmd.equals("regen")) {
            return handleRegen(sender, args);
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
        if (cmd.equals("agitate")) {
            return handleAgitate(sender);
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
        if (cmd.equals("clearhistory") || cmd.equals("historyclear")) {
            return handleClearHistory(sender);
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

        if (cmd.equals("resume")) {
            return handleResume(sender);
        }

        if (cmd.equals("redstoneaudit")) {
            return handleRedstoneAudit(sender, args);
        }

        ChatOutput.send(sender, ChatColor.YELLOW + "Bayzyl command stub: " + spec.get().name());
        ChatOutput.send(sender, ChatColor.WHITE + spec.get().usage());
        return true;
    }

    /** Connects the read-only redstone audit; until then the audit commands report that it is unavailable. */
    public void setRedstoneAuditCommand(com.bayzyl.redstone.RedstoneAuditCommand redstoneAuditCommand) {
        this.redstoneAuditCommand = redstoneAuditCommand;
    }

    private boolean handleRedstoneAudit(CommandSender sender, String[] args) {
        if (redstoneAuditCommand == null) {
            ChatOutput.send(sender, ChatColor.RED + "Redstone audit is not available.");
            return true;
        }
        return redstoneAuditCommand.handle(sender, args);
    }

    private boolean handleResume(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        
        if (crashRecoveryService == null) {
            ChatOutput.send(sender, ChatColor.RED + "Crash recovery service not available.");
            return true;
        }
        
        if (!crashRecoveryService.hasInterruptedSession(player.getUniqueId())) {
            ChatOutput.send(sender, ChatColor.YELLOW + "No interrupted commands to resume.");
            return true;
        }
        
        crashRecoveryService.resumeSession(player);
        return true;
    }

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        equipInMainHand(player, toolManager.createWand(), "Bayzyl wand equipped in your hand.");
        return true;
    }

    private boolean handleEraser(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            EraserSettings settings = new EraserSettings(5, BlockMask.parse(null), false, false, true, false);
            ItemStack item = toolManager.createEraser(settings);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Eraser settings exceed the safe work limit.");
                return true;
            }
            equipInMainHand(player, item, "Bayzyl eraser equipped in your hand: radius 5, carve air, bedrock protected.");
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            ChatOutput.send(sender, ChatColor.RED + "Radius must be a number.");
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
                case "surface": {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(sender, ChatColor.RED + "Invalid surface option: " + value);
                        return true;
                    }
                    surfaceOnly = parsedFlag;
                    break;
                }
                case "selection": {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(sender, ChatColor.RED + "Invalid selection option: " + value);
                        return true;
                    }
                    selectionOnly = parsedFlag;
                    break;
                }
                case "carve": {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(sender, ChatColor.RED + "Invalid carve option: " + value);
                        return true;
                    }
                    carveOnly = parsedFlag;
                    break;
                }
                case "bedrock": {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(sender, ChatColor.RED + "Invalid bedrock option: " + value);
                        return true;
                    }
                    editBedrock = parsedFlag;
                    break;
                }
                default:
                    ChatOutput.send(sender, ChatColor.RED + "Unknown option: " + key);
                    return true;
            }
        }

        int maxRadius = eraserService.getMaxRadius(player);
        if (radius > maxRadius && maxRadius < Integer.MAX_VALUE) {
            ChatOutput.send(sender, ChatColor.RED + "Max eraser radius is " + maxRadius + ".");
            return true;
        }
        WorkEstimate eraserEstimate = BrushSafety.assessEraser(radius);
        if (eraserEstimate.hardRejected()) {
            ChatOutput.send(sender, ChatColor.RED + eraserEstimate.reason());
            return true;
        }
        if (mask != null && !BrushSafety.isValidMaskRaw(mask)) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid mask. Use at most "
                    + BrushSafety.MASK_TEXT_MAX + " characters and valid block or block-tag tokens.");
            return true;
        }

        BlockMask parsed = mask == null ? null : BlockMask.parse(mask);
        EraserSettings settings = new EraserSettings(radius, parsed, surfaceOnly, selectionOnly, carveOnly, editBedrock);
        ItemStack item = toolManager.createEraser(settings);
        if (item == null) {
            ChatOutput.send(player, ChatColor.RED + "Eraser settings are invalid or exceed the safe work limit.");
            return true;
        }
        equipInMainHand(player, item,
                "Bayzyl eraser equipped in your hand: radius " + radius + ", bedrock " + (editBedrock ? "on" : "protected") + ".");
        return true;
    }

    private boolean handleNightvision(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.YELLOW + "Lights on!");
        } else {
            ChatOutput.send(sender, ChatColor.DARK_BLUE + "Lights Off!");
        }
        return true;
    }

    private boolean handleUnstick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("auto")) {
            if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
                ChatOutput.send(sender, ChatColor.WHITE + "Auto-unstick is " + (autoUnstickService.isEnabled(player) ? ChatColor.GREEN + "on" : ChatColor.RED + "off") + ChatColor.WHITE + ".");
                return true;
            }
            String value = args[1].toLowerCase(Locale.ROOT);
            if (!value.equals("on") && !value.equals("off")) {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /unstick auto <on|off>");
                return true;
            }
            boolean enabled = value.equals("on");
            autoUnstickService.setEnabled(player, enabled);
            savePlayerRuntime(player);
            ChatOutput.send(sender, ChatColor.WHITE + "Auto-unstick " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
            return true;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            ChatOutput.send(sender, ChatColor.RED + "Unstick is disabled in spectator mode.");
            return true;
        }
        if (movementAssistService.unstick(player)) {
            ChatOutput.send(sender, ChatColor.WHITE + "Unstuck.");
        } else {
            ChatOutput.send(sender, ChatColor.RED + "Could not find a safe place to unstick you.");
        }
        return true;
    }

    private boolean handleMeasure(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        int width = selection.getMaxX() - selection.getMinX() + 1;
        int height = selection.getMaxY() - selection.getMinY() + 1;
        int length = selection.getMaxZ() - selection.getMinZ() + 1;
        long footprint = (long) width * length;
        long surface = 2L * ((long) width * height + (long) width * length + (long) height * length);
        long volume = selection.getVolume();

        sendHeader(sender);
        ChatOutput.send(sender, ChatColor.GOLD + "Selection" + ChatColor.DARK_GRAY + " [" + ChatColor.WHITE + selection.getMinX() + "," + selection.getMinY() + "," + selection.getMinZ()
                + ChatColor.DARK_GRAY + " → " + ChatColor.WHITE + selection.getMaxX() + "," + selection.getMaxY() + "," + selection.getMaxZ() + ChatColor.DARK_GRAY + "]");
        ChatOutput.send(sender, ChatColor.WHITE + "Dimensions: " + ChatColor.AQUA + width + "W " + ChatColor.AQUA + height + "H " + ChatColor.AQUA + length + "L");
        ChatOutput.send(sender, ChatColor.WHITE + "Footprint: " + ChatColor.GOLD + footprint);
        ChatOutput.send(sender, ChatColor.WHITE + "Surface: " + ChatColor.GOLD + surface);
        ChatOutput.send(sender, ChatColor.WHITE + "Volume: " + ChatColor.GOLD + volume);
        return true;
    }

    private boolean handleRuler(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        double distance = movementAssistService.rulerDistance(player);
        if (distance < 0.0D) {
            ChatOutput.send(sender, ChatColor.RED + "No target block in sight.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Ruler: " + ChatColor.AQUA + String.format(Locale.ROOT, "%.2f", distance)
                + ChatColor.WHITE + " blocks to the target block.");
        return true;
    }

    private boolean handleWhereAmI(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + movementAssistService.whereAmI(player));
        return true;
    }

    private boolean handleSurface(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.surface(player)) {
            ChatOutput.send(sender, ChatColor.RED + "Could not find a safe surface above you.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Teleported to the surface.");
        return true;
    }

    private boolean handleAscend(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.ascend(player)) {
            ChatOutput.send(sender, ChatColor.RED + "Could not find a safe floor above you.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Ascended to the next safe floor.");
        return true;
    }

    private boolean handleDescend(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.descend(player)) {
            ChatOutput.send(sender, ChatColor.RED + "Could not find a safe floor below you.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Descended to the nearest safe floor.");
        return true;
    }

    private boolean handleAlign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        String direction = args.length > 0 ? args[0] : null;
        movementAssistService.align(player, direction);
        ChatOutput.send(sender, ChatColor.WHITE + "Aligned.");
        return true;
    }

    private boolean handleCeil(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        int clearance = movementAssistService.ceilingClearance(player);
        if (clearance < 0) {
            ChatOutput.send(sender, ChatColor.AQUA + "No ceiling found above you in this world height range.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Ceiling clearance: " + ChatColor.GOLD + clearance + ChatColor.WHITE + " block(s).");
        return true;
    }

    private boolean handleCenterMe(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!movementAssistService.centerMe(player)) {
            ChatOutput.send(sender, ChatColor.RED + "Could not center you.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Centered.");
        return true;
    }

    private boolean handleGhostHand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        boolean enabled;
        if (args.length == 0) {
            enabled = ghostHandService.toggle(player);
        } else {
            String mode = args[0].toLowerCase(Locale.ROOT);
            if (mode.equals("status")) {
                ChatOutput.send(sender, ChatColor.WHITE + "Ghost hand is " + (ghostHandService.isEnabled(player) ? ChatColor.GREEN + "on" : ChatColor.RED + "off") + ChatColor.WHITE + ".");
                return true;
            }
            if (!mode.equals("on") && !mode.equals("off")) {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /ghosthand [on|off|status]");
                return true;
            }
            enabled = mode.equals("on");
            ghostHandService.setEnabled(player, enabled);
        }
        savePlayerRuntime(player);
        ChatOutput.send(sender, ChatColor.WHITE + "Ghost hand " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleStackLook(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            boolean enabled = stackLookDirectionService.toggle(player);
            savePlayerRuntime(player);
            ChatOutput.send(sender, ChatColor.WHITE + "Stack look direction " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("status")) {
            ChatOutput.send(sender, ChatColor.WHITE + "Stack look direction is "
                    + (stackLookDirectionService.isEnabled(player) ? ChatColor.GREEN + "on" : ChatColor.RED + "off")
                    + ChatColor.WHITE + ".");
            return true;
        }
        if (!mode.equals("on") && !mode.equals("off")) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl stacklook [on|off|status]");
            return true;
        }
        boolean enabled = mode.equals("on");
        stackLookDirectionService.setEnabled(player, enabled);
        savePlayerRuntime(player);
        ChatOutput.send(sender, ChatColor.WHITE + "Stack look direction " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleStackAutoMove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            boolean currentState = stackAutoMoveService.isEnabled(player.getUniqueId());
            stackAutoMoveService.setEnabled(player.getUniqueId(), !currentState);
            savePlayerRuntime(player);
            ChatOutput.send(sender, ChatColor.WHITE + "Stack auto-move " + (!currentState ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("status")) {
            ChatOutput.send(sender, ChatColor.WHITE + "Stack auto-move is "
                    + (stackAutoMoveService.isEnabled(player.getUniqueId()) ? ChatColor.GREEN + "on" : ChatColor.RED + "off")
                    + ChatColor.WHITE + ".");
            return true;
        }
        if (!mode.equals("on") && !mode.equals("off")) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl stackautomove [on|off|status|toggle]");
            return true;
        }
        boolean enabled = mode.equals("on");
        stackAutoMoveService.setEnabled(player.getUniqueId(), enabled);
        savePlayerRuntime(player);
        ChatOutput.send(sender, ChatColor.WHITE + "Stack auto-move " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleClipboardInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            ChatOutput.send(sender, ChatColor.RED + "Clipboard is empty.");
            return true;
        }
        sendHeader(sender);
        ChatOutput.send(sender, ChatColor.GOLD + "Clipboard" + ChatColor.DARK_GRAY + " • "
                + ChatColor.AQUA + clipboard.getSizeX() + "W " + ChatColor.AQUA + clipboard.getSizeY() + "H " + ChatColor.AQUA + clipboard.getSizeZ() + "L");
        if (clipboard.getOrigin() != null) {
            ChatOutput.send(sender, ChatColor.WHITE + "Origin: " + ChatColor.GOLD
                    + clipboard.getOrigin().getBlockX() + "," + clipboard.getOrigin().getBlockY() + "," + clipboard.getOrigin().getBlockZ());
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Offset: " + ChatColor.GOLD
                + clipboard.getMinOffsetX() + "," + clipboard.getMinOffsetY() + "," + clipboard.getMinOffsetZ());
        return true;
    }

    private boolean handleClearClipboard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length > 0) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /clearclipboard");
            return true;
        }
        UUID id = player.getUniqueId();
        Clipboard clipboard = clipboardManager.get(id);
        if (clipboard == null) {
            ChatOutput.send(sender, ChatColor.YELLOW + "Clipboard is already empty.");
            return true;
        }
        clipboardManager.set(id, null);
        ChatOutput.send(sender, ChatColor.WHITE + "Clipboard cleared.");
        return true;
    }

    private boolean handleTrailClear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        recentEditTrailService.clearEntries(player.getUniqueId());
        ChatOutput.send(sender, ChatColor.WHITE + "Recent edit trail cleared.");
        return true;
    }

    private boolean handleSelCorners(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.GOLD + "Pos1: " + ChatColor.AQUA
                + selection.getPos1().getBlockX() + "," + selection.getPos1().getBlockY() + "," + selection.getPos1().getBlockZ());
        ChatOutput.send(sender, ChatColor.GOLD + "Pos2: " + ChatColor.AQUA
                + selection.getPos2().getBlockX() + "," + selection.getPos2().getBlockY() + "," + selection.getPos2().getBlockZ());
        return true;
    }

    private boolean handleSelSwap(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!selectionManager.swap(player.getUniqueId())) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Swapped selection corners.");
        return true;
    }

    private boolean handlePos1(CommandSender sender, String[] args) {
        return setSelectionCorner(sender, true, parseAnchorMode(args));
    }

    private boolean handlePos2(CommandSender sender, String[] args) {
        return setSelectionCorner(sender, false, parseAnchorMode(args));
    }

    private boolean setSelectionCorner(CommandSender sender, boolean firstCorner, ShapeAnchorMode anchorMode) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Location location = resolvePosAnchor(player, anchorMode);
        if (location == null) {
            ChatOutput.send(sender, ChatColor.RED + "Could not resolve " + (firstCorner ? "pos1" : "pos2") + " anchor.");
            return true;
        }
        if (firstCorner) {
            selectionManager.setPos1(player.getUniqueId(), location);
        } else {
            selectionManager.setPos2(player.getUniqueId(), location);
        }
        ChatOutput.send(player, ChatColor.WHITE + (firstCorner ? "Pos1" : "Pos2") + " set to "
                + ChatColor.GOLD + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        return true;
    }

    private ShapeAnchorMode parseAnchorMode(String[] args) {
        if (args.length == 0) {
            return ShapeAnchorMode.PLAYER;
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (!current.startsWith("at:")) {
            return ShapeAnchorMode.PLAYER;
        }
        return switch (current.substring(3)) {
            case "player", "feet", "here" -> ShapeAnchorMode.PLAYER;
            case "target", "look" -> ShapeAnchorMode.TARGET;
            case "selection", "selection-center", "sel", "center" -> ShapeAnchorMode.SELECTION_CENTER;
            default -> null;
        };
    }

    private Location resolvePosAnchor(Player player, ShapeAnchorMode anchorMode) {
        ShapeAnchorMode mode = anchorMode == null ? ShapeAnchorMode.TARGET : anchorMode;
        return switch (mode) {
            case PLAYER -> player.getLocation().clone();
            case TARGET -> {
                Block block = player.getTargetBlockExact(120);
                yield block == null ? null : block.getLocation();
            }
            case SELECTION_CENTER -> {
                Selection selection = selectionManager.get(player.getUniqueId());
                if (selection == null || !selection.isComplete()) {
                    yield null;
                }
                yield new Location(
                        selection.getPos1().getWorld(),
                        (selection.getMinX() + selection.getMaxX() + 1) / 2.0,
                        (selection.getMinY() + selection.getMaxY() + 1) / 2.0,
                        (selection.getMinZ() + selection.getMaxZ() + 1) / 2.0,
                        player.getLocation().getYaw(),
                        player.getLocation().getPitch());
            }
            case EYES -> player.getEyeLocation().clone();
        };
    }

    private boolean handleJail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!bayzylAccess.allowed(player, CommandCapability.JAIL)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to jail players.");
            return true;
        }
        if (!adminModeService.isActive(player, bayzylAccess)) {
            ChatOutput.send(sender, ChatColor.RED + "Enable Bayzyl admin mode first.");
            return true;
        }
        if (args.length != 1) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /jail <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            ChatOutput.send(sender, ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        Location jailCenter = resolveJailCenter(player);
        JailSnapshot snapshot = activeJailSnapshots.computeIfAbsent(target.getUniqueId(), id ->
                captureJailSnapshot(target, jailCenter)
        );

        target.setGameMode(GameMode.SURVIVAL);
        if (target.isOp()) {
            target.setOp(false);
        }

        buildJailCell(jailCenter, player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline() || activeJailSnapshots.get(target.getUniqueId()) != snapshot) {
                return;
            }
            Location teleport = jailCenter.clone().add(0.5, 1.0, 0.5);
            target.teleport(teleport);
            Bukkit.broadcastMessage(ChatColor.WHITE + target.getName() + ChatColor.GOLD + " has been jailed, naughty naughty!!");
        }, 4L);

        ChatOutput.send(sender, ChatColor.WHITE + "Jail built for " + ChatColor.GOLD + target.getName() + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleLiberate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!bayzylAccess.allowed(player, CommandCapability.JAIL)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to liberate players.");
            return true;
        }
        if (!adminModeService.isActive(player, bayzylAccess)) {
            ChatOutput.send(sender, ChatColor.RED + "Enable Bayzyl admin mode first.");
            return true;
        }
        if (args.length != 1) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /liberate <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            ChatOutput.send(sender, ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        JailSnapshot snapshot = activeJailSnapshots.remove(target.getUniqueId());
        if (snapshot == null) {
            ChatOutput.send(sender, ChatColor.RED + "No active jail record found for " + target.getName() + ".");
            return true;
        }

        if (snapshot.returnLocation() != null && snapshot.returnLocation().getWorld() != null) {
            target.teleport(snapshot.returnLocation().clone());
        }
        target.setGameMode(snapshot.previousGameMode());
        target.setOp(snapshot.wasOp());
        restoreJailCell(snapshot);

        Bukkit.broadcastMessage(ChatColor.WHITE + target.getName() + ChatColor.GOLD + " has been liberated. Back to chaos.");
        ChatOutput.send(sender, ChatColor.WHITE + "Liberated " + ChatColor.GOLD + target.getName() + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBubu(CommandSender sender) {
        List<String> phrases = List.of(
                "Mew!",
                "Nya!",
                "I wanna go home!",
                "Its coming!",
                "lets get chimmy for dimmy on chrimmy",
                "Help meeee!",
                "Are we fremmies? :3"
        );
        List<String> emoticons = List.of(":3", "^_^", "owo", "uwu", "(>w<)", "(=^･^=)");
        String phrase = phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));
        String emoticon = emoticons.get(ThreadLocalRandom.current().nextInt(emoticons.size()));
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + phrase + " " + emoticon);
        return true;
    }

    private boolean handleLol(CommandSender sender) {
        Bukkit.broadcastMessage("haha");
        return true;
    }

    private boolean handleWetoggle(CommandSender sender, String[] args) {
        if (!bayzylAccess.allowed(sender, CommandCapability.WETOGGLE)) {
            ChatOutput.send(sender, ChatColor.RED + "You don't have permission to toggle WorldEdit.");
            return true;
        }
        PluginManager pm = Bukkit.getPluginManager();
        Plugin we = pm.getPlugin("WorldEdit");
        if (we == null) {
            ChatOutput.send(sender, ChatColor.RED + "WorldEdit is not installed on this server.");
            return true;
        }
        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        boolean enabled = we.isEnabled();
        switch (action) {
            case "status" -> {
                ChatOutput.send(sender, ChatColor.AQUA + "WorldEdit is " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.AQUA + ".");
                return true;
            }
            case "on" -> {
                if (enabled) {
                    ChatOutput.send(sender, ChatColor.YELLOW + "WorldEdit is already enabled.");
                    return true;
                }
                pm.enablePlugin(we);
                ChatOutput.send(sender, ChatColor.GREEN + "WorldEdit enabled.");
                return true;
            }
            case "off" -> {
                if (!enabled) {
                    ChatOutput.send(sender, ChatColor.YELLOW + "WorldEdit is already disabled.");
                    return true;
                }
                pm.disablePlugin(we);
                ChatOutput.send(sender, ChatColor.GREEN + "WorldEdit disabled.");
                return true;
            }
            case "toggle" -> {
                if (enabled) {
                    pm.disablePlugin(we);
                    ChatOutput.send(sender, ChatColor.GREEN + "WorldEdit disabled.");
                } else {
                    pm.enablePlugin(we);
                    ChatOutput.send(sender, ChatColor.GREEN + "WorldEdit enabled.");
                }
                return true;
            }
            default -> {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /wetoggle [on|off|toggle|status]");
                return true;
            }
        }
    }

    private boolean handleCatCommand(CommandSender sender, String name, String preferredType, String... fallbacks) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        Cat cat = player.getWorld().spawn(player.getLocation(), Cat.class, spawned -> {
            spawned.setCustomName(ChatColor.LIGHT_PURPLE + name);
            spawned.setCustomNameVisible(true);
            spawned.setAdult();
            spawned.setTamed(true);
            spawned.setOwner(player);
            spawned.setCatType(resolveCatType(preferredType, fallbacks));
        });

        ChatOutput.send(sender, ChatColor.WHITE + "Spawned " + ChatColor.LIGHT_PURPLE + name + ChatColor.WHITE + ".");
        return true;
    }

    private Cat.Type resolveCatType(String preferredType, String... fallbacks) {
        for (String candidate : mergeTypeCandidates(preferredType, fallbacks)) {
            for (Cat.Type type : Cat.Type.values()) {
                if (type.name().equalsIgnoreCase(candidate)) {
                    return type;
                }
            }
        }
        return Cat.Type.values()[0];
    }

    private List<String> mergeTypeCandidates(String preferredType, String... fallbacks) {
        List<String> types = new ArrayList<>();
        if (preferredType != null && !preferredType.isBlank()) {
            types.add(preferredType);
        }
        if (fallbacks != null) {
            types.addAll(List.of(fallbacks));
        }
        return types;
    }

    private Location resolveJailCenter(Player player) {
        Location base = player.getLocation().getBlock().getLocation();
        float yaw = player.getLocation().getYaw();
        int facing = Math.floorMod(Math.round((yaw + 45.0f) / 90.0f), 4);
        int dx = 0;
        int dz = 0;
        switch (facing) {
            case 0 -> dz = 1;
            case 1 -> dx = -1;
            case 2 -> dz = -1;
            default -> dx = 1;
        }
        return base.add(dx * 5.0D, 0.0D, dz * 5.0D);
    }

    private JailSnapshot captureJailSnapshot(Player target, Location jailCenter) {
        Location returnLocation = target.getLocation().clone();
        List<JailBlockSnapshot> blocks = new ArrayList<>();
        World world = jailCenter.getWorld();
        if (world != null) {
            int centerX = jailCenter.getBlockX();
            int centerY = jailCenter.getBlockY();
            int centerZ = jailCenter.getBlockZ();
            for (int y = centerY; y <= centerY + 3; y++) {
                for (int x = centerX - 1; x <= centerX + 1; x++) {
                    for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        blocks.add(new JailBlockSnapshot(x, y, z, block.getState()));
                    }
                }
            }
        }
        return new JailSnapshot(
                target.getUniqueId(),
                target.getName(),
                returnLocation,
                target.getGameMode(),
                target.isOp(),
                jailCenter.clone(),
                List.copyOf(blocks)
        );
    }

    private void restoreJailCell(JailSnapshot snapshot) {
        if (snapshot == null || snapshot.jailCenter().getWorld() == null) {
            return;
        }
        for (JailBlockSnapshot blockSnapshot : snapshot.blockSnapshots()) {
            blockSnapshot.state().update(true, false);
        }
    }

    private void buildJailCell(Location center, Player player) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        int minX = centerX - 1;
        int maxX = centerX + 1;
        int minY = centerY;
        int maxY = centerY + 3;
        int minZ = centerZ - 1;
        int maxZ = centerZ + 1;

        int barsX = centerX;
        int barsZ = centerZ;
        float yaw = player.getLocation().getYaw();
        int facing = Math.floorMod(Math.round((yaw + 45.0f) / 90.0f), 4);
        switch (facing) {
            case 0 -> barsZ = minZ;
            case 1 -> barsX = maxX;
            case 2 -> barsZ = maxZ;
            default -> barsX = minX;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (!shell) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        continue;
                    }
                    if (x == barsX && z == barsZ && y == minY + 2) {
                        world.getBlockAt(x, y, z).setType(Material.IRON_BARS, false);
                    } else {
                        world.getBlockAt(x, y, z).setType(Material.BEDROCK, false);
                    }
                }
            }
        }
    }

    private boolean handleSelSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /selsave <name>");
            ChatOutput.send(sender, ChatColor.GRAY + "Or: /selsave share " + ChatColor.DARK_GRAY + "(prints a share code for the current selection)");
            return true;
        }
        if (args[0].equalsIgnoreCase("share")) {
            Selection current = selectionManager.get(player.getUniqueId());
            if (current == null || !current.isComplete()) {
                ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
                return true;
            }
            String code = SelectionBookmarkService.encodeShareCode(current);
            if (code == null) {
                ChatOutput.send(sender, ChatColor.RED + "Could not encode selection.");
                return true;
            }
            ChatOutput.send(sender, ChatColor.WHITE + "Selection share code:");
            ChatOutput.send(sender, ChatColor.YELLOW + code);
            ChatOutput.send(sender, ChatColor.GRAY + "Apply with " + ChatColor.WHITE + "/selload share <code>" + ChatColor.GRAY + ".");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        if (!selectionBookmarkService.save(player.getUniqueId(), args[0], selection)) {
            ChatOutput.send(sender, ChatColor.RED + "Could not save selection. Use letters, numbers, _ or -, max 32 chars.");
            return true;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Saved selection " + ChatColor.WHITE + args[0].toLowerCase(Locale.ROOT) + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleSelLoad(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            List<String> saved = selectionBookmarkService.list(player.getUniqueId());
            sendMenuLines(player, SELECTION_BOOKMARKS_MENU, "header", List.of(
                    "&a&lBZL",
                    "&aSelection Bookmarks"
            ), ListMenuConfigService.tokens());
            if (saved.isEmpty()) {
                ChatOutput.send(sender, listMenuConfigService.format(
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
                ChatOutput.send(sender, listMenuConfigService.format(
                        SELECTION_BOOKMARKS_MENU,
                        "messages.line",
                        "&fSaved selections &8| &f{names}",
                        ListMenuConfigService.tokens("names", String.join(separator, saved))
                ));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("share")) {
            if (args.length < 2) {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /selload share <code>");
                return true;
            }
            StringBuilder codeBuilder = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) {
                codeBuilder.append(" ").append(args[i]);
            }
            Selection decoded = SelectionBookmarkService.decodeShareCode(codeBuilder.toString(), player.getWorld());
            if (decoded == null || !decoded.isComplete()) {
                ChatOutput.send(sender, ChatColor.RED + "Invalid or unreadable share code.");
                return true;
            }
            selectionManager.setCuboid(player.getUniqueId(), decoded.getPos1(), decoded.getPos2());
            ChatOutput.send(sender, ChatColor.WHITE + "Loaded selection from share code.");
            return true;
        }
        Selection loaded = selectionBookmarkService.load(player.getUniqueId(), args[0]);
        if (loaded == null || !loaded.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Saved selection not found or its world is unavailable.");
            return true;
        }
        selectionManager.setCuboid(player.getUniqueId(), loaded.getPos1(), loaded.getPos2());
        ChatOutput.send(sender, ChatColor.WHITE + "Loaded selection " + ChatColor.WHITE + args[0].toLowerCase(Locale.ROOT) + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleSelCenter(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
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
        ChatOutput.send(sender, ChatColor.WHITE + "Selection center marker " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean handleThru(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (movementAssistService.thru(player)) {
            ChatOutput.send(sender, ChatColor.AQUA + "Zoom!");
        } else {
            ChatOutput.send(sender, ChatColor.RED + "Could not find a safe spot through that wall.");
        }
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            syntaxError(sender, "/set <BLOCK|DISTRIBUTION> [mask:<BLOCKS>] [if:air|solid|any] [confirm:true]");
            return true;
        }
        BlockDistribution distribution;
        try {
            distribution = BlockDistribution.parse(args[0]);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid block distribution: " + ex.getMessage());
            return true;
        }
        OptionState options = parseOptions(args, 1);
        if (!isValidIfMode(options.ifMode)) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid if: mode. Use if:air, if:solid, or if:any");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (editService.requiresConfirm(selection, options.confirm)) {
            if (shouldBypassConfirm(player, options.confirm)) {
                options.confirm = true;
            }
        }
        if (editService.requiresConfirm(selection, options.confirm)) {
            ChatOutput.send(sender, ChatColor.RED + "Large selection. Re-run with confirm:true");
            return true;
        }
        BlockMask mask = BlockMask.parse(options.mask);

        int changed = editService.setBlocks(player, selection, distribution, mask, options.ifMode);
        recentEditTrailService.record(player.getUniqueId(), "set " + distribution);
        ChatOutput.send(sender, ChatColor.WHITE + "Set " + changed + " blocks.");
        return true;
    }

    private boolean handleReplace(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 2) {
            syntaxError(sender, "/replace <BLOCKS> <BLOCK|DISTRIBUTION> [mask:<BLOCKS>] [confirm:true]");
            return true;
        }
        BlockMask from = BlockMask.parse(args[0]);
        BlockDistribution toDistribution;
        try {
            toDistribution = BlockDistribution.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid block distribution: " + ex.getMessage());
            return true;
        }
        OptionState options = parseOptions(args, 2);
        if (!isValidIfMode(options.ifMode)) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid if: mode. Use if:air, if:solid, or if:any");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (editService.requiresConfirm(selection, options.confirm)) {
            if (shouldBypassConfirm(player, options.confirm)) {
                options.confirm = true;
            }
        }
        if (editService.requiresConfirm(selection, options.confirm)) {
            ChatOutput.send(sender, ChatColor.RED + "Large selection. Re-run with confirm:true");
            return true;
        }
        BlockMask mask = BlockMask.parse(options.mask);

        int changed = editService.replaceBlocks(player, selection, from, toDistribution, mask);
        recentEditTrailService.record(player.getUniqueId(), "replace " + toDistribution.toString().toLowerCase(Locale.ROOT));
        ChatOutput.send(sender, ChatColor.WHITE + "Replaced " + changed + " blocks.");
        return true;
    }

    private boolean handleCopy(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        OptionState options = parseOptions(args, 0);
        BlockMask mask = BlockMask.parse(options.mask);
        Selection selection = selectionManager.get(player.getUniqueId());
        runCopy(player, selection, mask);
        return true;
    }

    /** Copies {@code selection} for {@code player}; also the resume path for a copy a restart interrupted. */
    private void runCopy(org.bukkit.entity.Player player, Selection selection, BlockMask mask) {
        CommandSender sender = player;
        UUID playerId = player.getUniqueId();
        // Validate before tracking: a copy that never starts must not leave an "interrupted command" behind.
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return;
        }
        if (editService.hasCopyTask(playerId)) {
            ChatOutput.send(sender, ChatColor.RED + "A copy is already running. Wait for it to finish before starting another.");
            return;
        }
        // Track copy session for crash recovery
        boolean tracked = false;
        if (crashRecoveryService != null) {
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("selection", selection);
            sessionData.put("mask", mask);
            crashRecoveryService.startSession(playerId, "copy", "starting", sessionData,
                session -> {
                    // Resumed within the same run (the callback does not survive a restart; see CopyResumeHandler)
                    player.sendMessage("§6[Bayzyl] §7Resuming copy operation...");
                    runCopy(player, selection, mask);
                });
            tracked = true;
        }

        // The session ends here on every path except a copy handed to the responsive task, which ends it itself.
        boolean handedOff = false;
        try {
            if (editService.shouldCopyResponsively(selection)) {
                handedOff = editService.startResponsiveCopy(player, selection, mask, clipboard -> {
                    recentEditTrailService.record(playerId, "copy " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ());
                    ChatOutput.send(player, ChatColor.WHITE + "Copied " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ() + ".");
                    // Complete session on success
                    if (crashRecoveryService != null) {
                        crashRecoveryService.completeSession(playerId);
                    }
                });
                return;
            }
            Clipboard clipboard = editService.copySelection(player, selection, mask);
            if (clipboard != null) {
                recentEditTrailService.record(playerId, "copy " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ());
                ChatOutput.send(sender, ChatColor.WHITE + "Copied " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ() + ".");
            }
        } finally {
            if (tracked && !handedOff) {
                crashRecoveryService.completeSession(playerId);
            }
        }
    }

    private boolean handleCut(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.RED + "Large selection. Re-run with confirm:true");
            return true;
        }
        BlockMask mask = BlockMask.parse(options.mask);
        int changed = editService.cutSelection(player, selection, mask, options.confirm);
        if (changed == EditService.REFUSED) {
            // Safety check refused; user already messaged.
            return true;
        }
        recentEditTrailService.record(player.getUniqueId(), "cut");
        if (changed != EditService.DEFERRED) {
            ChatOutput.send(sender, ChatColor.WHITE + "Cut " + changed + " blocks.");
        }
        return true;
    }

    private boolean handlePaste(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        
        // Check for async flag
        boolean asyncPaste = false;
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equalsIgnoreCase("async")) {
                asyncPaste = true;
            } else {
                filteredArgs.add(arg);
            }
        }
        
        ClipboardPasteRequest request = ClipboardCommandParser.parsePaste(filteredArgs.toArray(new String[0]));
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            ChatOutput.send(sender, ChatColor.RED + "Clipboard is empty.");
            return true;
        }
        int rotation = request.rotation();
        Location targetOrigin;
        switch (request.at()) {
            case "origin" -> {
                if (clipboard.getOrigin() == null || clipboard.getOrigin().getWorld() == null) {
                    ChatOutput.send(sender, ChatColor.RED + "Clipboard origin unavailable.");
                    return true;
                }
                targetOrigin = clipboard.getOrigin();
            }
            case "target" -> {
                var result = player.rayTraceBlocks(120);
                if (result == null || result.getHitBlock() == null) {
                    ChatOutput.send(sender, ChatColor.RED + "No target block in range.");
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
        if (pasteSelection == null) {
            ChatOutput.send(sender, ChatColor.RED + "Could not resolve paste destination.");
            return true;
        }
        boolean confirm = shouldBypassConfirm(player, request.confirm());
        if (editService.requiresConfirm(pasteSelection, confirm)) {
            ClipboardPlacementSummary.describe(clipboard, targetOrigin, rotation)
                    .forEach(line -> ChatOutput.send(sender, line));
            ChatOutput.send(sender, ChatColor.RED + "Large paste. Re-run with confirm:true");
            return true;
        }

        if (request.previewOnly()) {
            selectionManager.setCuboid(player.getUniqueId(), pasteSelection.getPos1(), pasteSelection.getPos2());
            ChatOutput.send(sender, ChatColor.WHITE + "Paste preview selected.");
            ClipboardPlacementSummary.describe(clipboard, targetOrigin, rotation)
                    .forEach(line -> ChatOutput.send(sender, line));
            return true;
        }

        if (asyncPaste) {
            // Use async paste
            if (editService.pasteClipboardAsync(player, clipboard, targetOrigin, rotation, request.ignoreAir())) {
                ChatOutput.send(sender, ChatColor.GREEN + "Async paste started. Progress will be shown.");
                recentEditTrailService.record(player.getUniqueId(), "paste async " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ());
            }
        } else {
            // Sync paste auto-switches to chunked async for large pastes (returns DEFERRED).
            // For deferred pastes, set the selection up-front so the user gets immediate visual
            // feedback of the paste region; the AsyncPasteTask will send the completion message.
            if (request.selectAfterPaste()) {
                selectionManager.setCuboid(player.getUniqueId(), pasteSelection.getPos1(), pasteSelection.getPos2());
            }
            int changed = editService.pasteClipboard(player, clipboard, targetOrigin, rotation,
                    request.ignoreAir(), confirm);
            if (changed == EditService.REFUSED) {
                // Safety check refused; user already messaged. Skip recording history hint.
                return true;
            }
            recentEditTrailService.record(player.getUniqueId(), "paste " + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ());
            if (changed != EditService.DEFERRED) {
                ChatOutput.send(sender, ChatColor.WHITE + "Pasted " + changed + " blocks.");
            }
        }
        return true;
    }

    private boolean handleMove(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        ClipboardMoveRequest request;
        try {
            request = ClipboardCommandParser.parseMove(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        int[] direction = DirectionUtil.resolve(player, request.direction());
        long totalVolume = selection.getVolume() * 2L;
        if (totalVolume > EditUtil.CONFIRM_VOLUME && !shouldBypassConfirm(player, request.confirm())) {
            ChatOutput.send(sender, ChatColor.RED + "Large move. Re-run with confirm:true");
            return true;
        }

        int changed = editService.moveSelection(player, selection, request.distance(), direction, request.ignoreAir());
        recentEditTrailService.record(player.getUniqueId(), "move " + request.distance() + " " + request.direction());
        ChatOutput.send(sender, ChatColor.WHITE + "Moved selection " + request.distance() + " block(s) " + request.direction() + " and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleSphere(CommandSender sender, String[] args, boolean hollow) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        String verb = hollow ? "hsphere" : "sphere";
        SphereRequest request;
        try {
            request = ShapeCommandParser.parseSphere(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        ShapeResult result = shapeService.createSphere(player, request,
                shouldBypassConfirm(player, request.confirm()));
        sendShapeResult(sender, result, hollow ? "hollow sphere" : "sphere");
        return true;
    }

    private boolean handleDome(CommandSender sender, String[] args, boolean hollow, boolean bowl) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        String verb = bowl ? (hollow ? "hbowl" : "bowl") : (hollow ? "hdome" : "dome");
        SphereRequest request;
        try {
            request = ShapeCommandParser.parseSphere(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        ShapeResult result = shapeService.createDome(player, request, bowl,
                shouldBypassConfirm(player, request.confirm()));
        sendShapeResult(sender, result, bowl ? (hollow ? "hollow bowl" : "bowl") : (hollow ? "hollow dome" : "dome"));
        return true;
    }

    private boolean handleCylinder(CommandSender sender, String[] args, boolean hollow) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        String verb = hollow ? "hcyl" : "cyl";
        CylinderRequest request;
        try {
            request = ShapeCommandParser.parseCylinder(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        ShapeResult result = shapeService.createCylinder(player, request,
                shouldBypassConfirm(player, request.confirm()));
        sendShapeResult(sender, result, hollow ? "hollow cylinder" : "cylinder");
        return true;
    }

    private boolean handlePyramid(CommandSender sender, String[] args, boolean hollow) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        String verb = hollow ? "hpyramid" : "pyramid";
        PyramidRequest request;
        try {
            request = ShapeCommandParser.parsePyramid(verb, args, hollow);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        ShapeResult result = shapeService.createPyramid(player, request,
                shouldBypassConfirm(player, request.confirm()));
        sendShapeResult(sender, result, hollow ? "hollow pyramid" : "pyramid");
        return true;
    }

    private boolean handleBrush(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("structure")) {
            return handleBrushStructure(player, args);
        }
        // /brush gen <type> [opts] — explicit parametric gen brush.
        if (args.length >= 2 && (args[0].equalsIgnoreCase("gen") || args[0].equalsIgnoreCase("noisegen"))) {
            com.bayzyl.gen.GenBrushType genType = com.bayzyl.gen.GenBrushType.parse(args[1]);
            if (genType != null) {
                return handleBrushGen(player, genType, args, 2);
            }
            ChatOutput.send(player, ChatColor.RED + "Unknown gen brush type: " + args[1]);
            return true;
        }
        // /brush noise <gen-type> [opts] — smart-dispatch when first arg is a known gen type.
        // Otherwise fall through to the existing pattern brush behavior (which takes a block).
        if (args.length >= 2 && args[0].equalsIgnoreCase("noise")) {
            com.bayzyl.gen.GenBrushType genType = com.bayzyl.gen.GenBrushType.parse(args[1]);
            if (genType != null) {
                return handleBrushGen(player, genType, args, 2);
            }
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
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        WorkEstimate estimate = estimateBrush(settings);
        if (estimate.hardRejected()) {
            ChatOutput.send(sender, ChatColor.RED + "Brush refused: " + estimate.reason());
            return true;
        }
        if (!estimate.permits(shouldBypassConfirm(player, settings.confirm()))) {
            ChatOutput.send(sender, ChatColor.RED + "Large brush. Re-run with confirm:true");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createShapeBrush(settings);
            if (item == null) {
                ChatOutput.send(sender, ChatColor.RED + "Shape brush settings are invalid or exceed the safe work limit.");
                return true;
            }
            equipBrushInMainHand(player, item);
            ChatOutput.send(sender, ChatColor.WHITE + "Created " + settings.type().displayName() + " brush and equipped it in your hand.");
        } else {
            if (!toolManager.bindShapeBrush(held, settings, false)) {
                ChatOutput.send(sender, ChatColor.RED + "Shape brush settings are invalid or exceed the safe work limit.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(sender, ChatColor.WHITE + "Bound " + settings.type().displayName() + " brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        ChatOutput.send(sender, ChatColor.DARK_GRAY + "Blocks: " + ChatColor.WHITE + settings.distribution()
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + describeBrushShape(settings)
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (settings.mask() == null ? "any" : settings.mask().summary())
                + ChatColor.DARK_GRAY + " | anchor: " + ChatColor.WHITE + settings.anchorMode().name().toLowerCase(Locale.ROOT));
        ChatOutput.send(sender, ChatColor.DARK_GRAY + "Right-click to build. Tune with /mask, /material, /size, or /brush none to unbind.");
        return true;
    }

    private boolean handleBrushSave(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /brush save <name>");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!brushPresetService.isSavableBrush(held)
                || (toolManager.readShapeBrushSettings(held) == null && toolManager.readStructureBrushSettings(held) == null)) {
            ChatOutput.send(player, ChatColor.RED + "Hold a Bayzyl brush to save it.");
            return true;
        }
        String reservedBy = brushPresetService.reservedNameOwner(args[1]);
        if (reservedBy != null) {
            if ("an empty or invalid name".equals(reservedBy)) {
                ChatOutput.send(player, ChatColor.RED + "Brush names must use letters, numbers, _ or -, max 32 chars.");
                return true;
            }
            ChatOutput.send(player, ChatColor.RED + "Name '" + args[1] + "' is reserved by " + reservedBy
                    + ". Pick a different name.");
            return true;
        }
        if (!brushPresetService.saveBrush(player, args[1], held)) {
            ChatOutput.send(player, ChatColor.RED + "Could not save brush.");
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + "Saved brush preset " + ChatColor.GOLD + args[1] + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBrushLoad(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /brush load <name>");
            return true;
        }
        ItemStack item = brushPresetService.loadBrush(args[1]);
        if (item == null) {
            ChatOutput.send(player, ChatColor.RED + "Brush preset not found.");
            return true;
        }
        ToolType savedType = toolManager.getToolType(item);
        boolean valid = savedType != null && switch (savedType) {
            case SHAPE_BRUSH -> toolManager.readShapeBrushSettings(item) != null;
            case STRUCTURE_BRUSH -> toolManager.readStructureBrushSettings(item) != null;
            case ERASER -> toolManager.readEraserSettings(item) != null;
            case SMOOTH_BRUSH, TERRAIN_BRUSH -> toolManager.readTerrainBrushSettings(item) != null;
            case PAINT_BRUSH -> toolManager.readPaintBrushSettings(item) != null;
            case PATTERN_BRUSH -> toolManager.readPatternBrushSettings(item) != null;
            case DETAIL_BRUSH -> toolManager.readDetailBrushSettings(item) != null;
            case GEN_BRUSH -> toolManager.readGenBrushSettings(item) != null;
            case CLIPBOARD_BRUSH -> toolManager.isClipboardBrush(item);
            case WAND -> false;
        };
        if (!valid) {
            ChatOutput.send(player, ChatColor.RED + "Saved brush is invalid or exceeds the safe work limit. It was not equipped or changed.");
            return true;
        }
        equipBrushInMainHand(player, item);
        ChatOutput.send(player, ChatColor.WHITE + "Loaded brush preset " + ChatColor.GOLD + args[1] + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBrushList(Player player) {
        List<String> brushes = brushPresetService.listBrushes();
        sendHeader(player);
        ChatOutput.send(player, messageThemeService.applyAccent("&aBrush Presets &8| &f" + brushes.size()));
        if (brushes.isEmpty()) {
            ChatOutput.send(player, ChatColor.GRAY + "No brush presets saved.");
            return true;
        }
        for (String brush : brushes) {
            ChatOutput.send(player, ChatColor.GOLD + "- " + ChatColor.WHITE + brush);
        }
        return true;
    }

    private boolean handleBrushDelete(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /brush delete <name>");
            return true;
        }
        if (!brushPresetService.deleteBrush(args[1])) {
            ChatOutput.send(player, ChatColor.RED + "Brush preset not found.");
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + "Deleted brush preset " + ChatColor.GOLD + args[1] + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleBrushNone(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ChatOutput.send(player, ChatColor.RED + "Hold the brush you want to unbind.");
            return true;
        }
        if (!toolManager.unbindAnyBrush(held)) {
            ChatOutput.send(player, ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ChatOutput.send(player, ChatColor.WHITE + "Brush unbound.");
        return true;
    }

    private boolean handleSchematicRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        if (args.length == 0) {
        ChatOutput.send(player, ChatColor.GREEN + "" + ChatColor.BOLD + "BZL" + ChatColor.WHITE + " Schematics" + ChatColor.GRAY + " | " + ChatColor.GOLD + "Page 1/1");
        ChatOutput.send(player, ChatColor.GREEN + "▌ " + ChatColor.GRAY + "save <name> [origin] [entities] [biomes] [confirm]");
        ChatOutput.send(player, ChatColor.GREEN + "▌ " + ChatColor.GRAY + "load <name> [preview:true] [rotation:<0|90|180|270>] [at:<player|target|origin>]");
        ChatOutput.send(player, ChatColor.GREEN + "▌ " + ChatColor.GRAY + "orient [rotation:<0|90|180|270>] [at:<player|target|origin>]");
        ChatOutput.send(player, ChatColor.GREEN + "▌ " + ChatColor.GRAY + "list [filter] [page]");
        ChatOutput.send(player, ChatColor.GRAY + "Saves/loads .schem files in plugins/schematics.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("save")) {
            return handleSchematicSave(player, args);
        } else if (sub.equals("load")) {
            return handleSchematicLoad(player, args);
        } else if (sub.equals("orient") || sub.equals("orientation") || sub.equals("preview")) {
            return handleSchematicOrient(player, args);
        } else if (sub.equals("list")) {
            return handleSchematicList(player, args);
        } else {
            ChatOutput.send(player, ChatColor.RED + "Usage: /schematic <save|load|list> ...");
            ChatOutput.send(player, ChatColor.GRAY + "Save: /schematic save <name> [origin:<min|center|player>] [entities:on|off] [biomes:on|off] [confirm:true]");
            ChatOutput.send(player, ChatColor.GRAY + "Load: /schematic load <name> [preview:true] [rotation:<0|90|180|270>] [at:<player|target|origin>]");
            ChatOutput.send(player, ChatColor.GRAY + "Orient: /schematic orient [rotation:<0|90|180|270>] [at:<player|target|origin>]");
            ChatOutput.send(player, ChatColor.GRAY + "List: /schematic list [filter:<name>] [page:<n>]");
            return true;
        }
    }

    private boolean handleSchematicSave(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /schematic save <name> [origin:<min|center|player>] [entities:on|off] [biomes:on|off] [confirm:true]");
            return true;
        }

        String name = args[1];
        String origin = "min";
        boolean includeEntities = true;
        boolean includeBiomes = true;
        boolean confirm = false;

        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            if (!token.contains(":")) {
                ChatOutput.send(player, ChatColor.RED + "Unknown schematic option: " + token);
                return true;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "origin", "at" -> origin = value;
                case "entities" -> includeEntities = parseBoolean(value, "entities");
                case "biomes" -> includeBiomes = parseBoolean(value, "biomes");
                case "confirm" -> confirm = parseBoolean(value, "confirm");
                default -> {
                    ChatOutput.send(player, ChatColor.RED + "Unknown schematic option: " + key);
                    return true;
                }
            }
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        SchematicSaveResult result = schematicService.saveSelection(player, selection, name, origin, includeEntities, includeBiomes, confirm);
        if (!result.success()) {
            ChatOutput.send(player, ChatColor.RED + result.message());
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + result.message());
        return true;
    }
    
    private boolean handleSchematicLoad(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /schematic load <name> [preview:true] [rotation:<0|90|180|270>] [at:<player|target|origin>]");
            ChatOutput.send(player, ChatColor.GRAY + "Loads a schematic from the server plugins/schematics folder.");
            return true;
        }
        
        String name = args[1];
        SchematicOrientationOptions options;
        try {
            options = parseSchematicOrientationOptions(args, 2);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            return true;
        }
        
        // Load the schematic
        SchematicService.LoadResult result = schematicService.loadSchematic(player, name);
        if (!result.success()) {
            ChatOutput.send(player, ChatColor.RED + result.message());
            return true;
        }
        
        // Set the loaded schematic to player's clipboard
        com.bayzyl.Clipboard loadedClipboard = result.clipboard();
        if (loadedClipboard != null) {
            clipboardManager.set(player.getUniqueId(), loadedClipboard);
            ChatOutput.send(player, ChatColor.WHITE + result.message());
            ChatOutput.send(player, ChatColor.GRAY + "Schematic loaded to clipboard. Use /schematic orient to inspect placement, or /paste to place it.");
            showSchematicPlacement(player, loadedClipboard, options, options.preview());
        } else {
            ChatOutput.send(player, ChatColor.RED + "Failed to load schematic: clipboard is null");
        }
        
        return true;
    }

    private boolean handleSchematicOrient(Player player, String[] args) {
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            ChatOutput.send(player, ChatColor.RED + "Clipboard is empty. Load a schematic first with /schematic load <name>.");
            return true;
        }
        SchematicOrientationOptions options;
        try {
            options = parseSchematicOrientationOptions(args, 1);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            return true;
        }
        showSchematicPlacement(player, clipboard, options, true);
        return true;
    }

    private void showSchematicPlacement(Player player, Clipboard clipboard, SchematicOrientationOptions options, boolean selectPreview) {
        Location anchor = resolvePasteTarget(player, clipboard, options.at());
        if (anchor == null) {
            return;
        }
        Selection selection = EditUtil.getPasteSelection(clipboard, anchor, options.rotation());
        if (selectPreview && selection != null) {
            selectionManager.setCuboid(player.getUniqueId(), selection.getPos1(), selection.getPos2());
            ChatOutput.send(player, ChatColor.WHITE + "Schematic placement preview selected.");
        }
        for (String line : ClipboardPlacementSummary.describe(clipboard, anchor, options.rotation())) {
            ChatOutput.send(player, line);
        }
        ChatOutput.send(player, ChatColor.GRAY + "Paste with /paste rotation:" + options.rotation() + " at:" + options.at()
                + " or inspect another angle with /schematic orient rotation:<0|90|180|270>.");
    }

    private SchematicOrientationOptions parseSchematicOrientationOptions(String[] args, int startIndex) {
        int rotation = 0;
        String at = "player";
        boolean preview = false;
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                if (token.equals("preview") || token.equals("select")) {
                    preview = true;
                    continue;
                }
                throw new IllegalArgumentException("Unknown schematic orientation option: " + args[i]);
            }
            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "rotation", "rotate" -> {
                    try {
                        rotation = Integer.parseInt(DirectionUtil.normalizeRotationKeyword(value));
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Rotation must be 0, 90, 180, 270, left, right, or back.");
                    }
                    rotation = ((rotation % 360) + 360) % 360;
                    if (rotation % 90 != 0) {
                        throw new IllegalArgumentException("Rotation must be a 90-degree step.");
                    }
                }
                case "at" -> at = normalizePasteAt(value);
                case "preview", "select" -> preview = parseBoolean(value, key);
                default -> throw new IllegalArgumentException("Unknown schematic orientation option: " + key);
            }
        }
        return new SchematicOrientationOptions(rotation, at, preview);
    }

    private String normalizePasteAt(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (value.equals("origin") || value.equals("original")) {
            return "origin";
        }
        if (value.equals("target") || value.equals("look")) {
            return "target";
        }
        if (value.equals("player") || value.equals("here") || value.isBlank()) {
            return "player";
        }
        throw new IllegalArgumentException("at must be player, target, or origin.");
    }

    private boolean handleSchematicList(Player player, String[] args) {
        String filter = null;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (token.startsWith("filter:")) {
                filter = token.substring(7);
            } else if (token.startsWith("page:")) {
                try {
                    page = Integer.parseInt(token.substring(5));
                    if (page < 1) page = 1;
                } catch (NumberFormatException e) {
                    ChatOutput.send(player, ChatColor.RED + "Invalid page: " + token.substring(5));
                    return true;
                }
            }
        }

        List<String> schems = schematicService.listSchematics(filter);
        int perPage = 8;
        int totalPages = (schems.size() + perPage - 1) / perPage;
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, schems.size());

        ChatOutput.send(player, ChatColor.GREEN + "" + ChatColor.BOLD + "BZL" + ChatColor.WHITE + " Schematic List" + 
                        ChatColor.GRAY + " | " + ChatColor.GOLD + "Page " + page + "/" + totalPages);
        
        ChatOutput.send(player, ChatColor.GREEN + "▌ " + schems.size() + ChatColor.GRAY + (filter != null ? " matching '" + filter + "'" : " total"));

        if (schems.isEmpty()) {
            ChatOutput.send(player, ChatColor.YELLOW + "No schematics found.");
            ChatOutput.send(player, ChatColor.GRAY + "Use /schematic save <name> to create one.");
        } else {
            for (int i = start; i < end; i++) {
                String name = schems.get(i);
                ChatOutput.send(player, ChatColor.AQUA + "" + (i + 1) + ". " + ChatColor.WHITE + name + ChatColor.GRAY + " (.schem)");
            }
        }

        ChatOutput.send(player, ChatColor.GRAY + "page:<n> | filter:<name> | /schematic list");
        return true;
    }

    private boolean handleDetailBrushRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            sendDetailBrushUsage(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!isDetailBrushSubcommand(sub)) {
            String[] overrideTokens = args.length >= 2 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
            return loadDetailBrushVariant(player, sub, overrideTokens);
        }
        return switch (sub) {
            case "tool", "give", "get" -> handleDetailBrushTool(player, args);
            case "set", "param" -> handleDetailBrushSet(player, args);
            case "info", "describe" -> handleDetailBrushInfo(player, args);
            case "presets", "list" -> handleDetailBrushList(player);
            case "save" -> handleDetailBrushSave(player, args);
            case "load" -> handleDetailBrushLoad(player, args);
            case "code" -> handleDetailBrushCode(player, args);
            case "getcode" -> handleDetailBrushGetCode(player);
            case "variants", "saved" -> handleDetailBrushVariants(player, args);
            case "delete", "remove" -> handleDetailBrushDelete(player, args);
            case "mode" -> handleDetailBrushMode(player, args);
            case "undo-last", "undolast" -> handleDetailBrushUndoLast(player, args);
            case "none", "unbind" -> handleBrushNone(player);
            default -> {
                sendDetailBrushUsage(player);
                yield true;
            }
        };
    }

    private void sendDetailBrushUsage(Player player) {
        sendHeader(player);
        ChatOutput.send(player, messageThemeService.applyAccent("&aDetail Brushes &8| &fUsage"));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush tool <preset> [variant] &8| &fGet a brush bound to a preset or built-in variant."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush set <param> <value> &8| &fTweak a parameter on the held brush."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush info <preset> &8| &fDescribe a preset and its parameters."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush presets &8| &fList available presets."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush save <name> &8| &fSave the held brush as a named variant."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush load <name> &8| &fLoad a saved variant onto the held brush."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/db getcode &8| &fGet a share code for the held detail brush."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/db code <code> &8| &fLoad a shared detail brush code."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/db <variant> &8| &fQuick-load an unclaimed variant."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush variants [preset] &8| &fList built-in and saved variants."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush delete <name> &8| &fDelete a saved variant."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/detailbrush none &8| &fUnbind the held brush."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/db undo-last [n] &8| &fUndo the last n detail-brush stamps (default 1)."));
        ChatOutput.send(player, messageThemeService.applyAccent("&6/db <variant> [override ...] &8| &fQuick-load with one or more override tokens (e.g. /db thunderbolt red blue)."));
    }

    private boolean handleDetailBrushTool(Player player, String[] args) {
        String presetId = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "flame";
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(presetId);
        if (preset == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown preset: " + presetId
                    + ". Try " + String.join(", ", detailBrushService.registry().ids()));
            return true;
        }
        com.bayzyl.detail.DetailBrushSettings settings;
        String displayName = preset.displayName();
        String boundMessage = preset.displayName();
        if (args.length >= 3) {
            String variantName = args[2].toLowerCase(Locale.ROOT);
            com.bayzyl.detail.DetailBrushVariant variant = detailBrushService.registry().builtInVariant(preset.id(), variantName);
            if (variant == null) {
                ChatOutput.send(player, ChatColor.RED + "Unknown built-in " + preset.id() + " variant: " + variantName
                        + ". Try " + String.join(", ", detailBrushService.registry().builtInVariantNames(preset.id())));
                return true;
            }
            settings = variant.settings();
            displayName = variant.displayName();
            boundMessage = preset.displayName() + " / " + variant.displayName();
        } else {
            settings = new com.bayzyl.detail.DetailBrushSettings(
                    preset.id(), preset.defaults(), com.bayzyl.detail.DetailBrushMode.STAMP
            );
        }
        if (args.length >= 4) {
            String[] overrideTokens = Arrays.copyOfRange(args, 3, args.length);
            List<String> applied = new ArrayList<>();
            for (String token : overrideTokens) {
                String paramName = detailBrushOverrideParamFor(preset, token);
                if (paramName == null) {
                    ChatOutput.send(player, ChatColor.RED + "Unknown override token: " + token
                            + ". Valid: " + String.join(", ", detailBrushOverrideTokens(preset)));
                    return true;
                }
                settings = applyDetailBrushOverride(preset, settings, token);
                applied.add(paramName + "=" + token.toLowerCase(Locale.ROOT));
            }
            if (!applied.isEmpty()) {
                boundMessage = boundMessage + " (" + String.join(", ", applied) + ")";
            }
        }
        WorkEstimate safetyEstimate = detailBrushService.safety().assess(settings);
        if (safetyEstimate.hardRejected()) {
            ChatOutput.send(player, ChatColor.RED + "Detail brush refused: " + safetyEstimate.reason());
            return true;
        }
        int estimate = preset.estimatedStampBlockCount(settings.parameters().mergeDefaults(preset.parameterSpecs()));
        if (estimate > 0) {
            boundMessage = boundMessage + " — ~" + estimate + " blocks/stamp";
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ItemStack item = toolManager.createDetailBrush(settings, displayName);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Detail brush settings are invalid or exceed the safe work limit.");
                return true;
            }
            equipInMainHand(player, item, "Detail brush bound: " + boundMessage);
        } else {
            if (!toolManager.bindDetailBrush(held, settings, displayName, true)) {
                ChatOutput.send(player, ChatColor.RED + "Detail brush settings are invalid. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Detail brush bound to held item: " + boundMessage);
        }
        return true;
    }

    private boolean handleDetailBrushSet(Player player, String[] args) {
        if (args.length < 3) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /detailbrush set <param> <value>");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        com.bayzyl.detail.DetailBrushSettings current = toolManager.readDetailBrushSettings(held);
        if (current == null) {
            ChatOutput.send(player, ChatColor.RED + "Hold a detail brush to tweak parameters.");
            return true;
        }
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(current.presetId());
        if (preset == null) {
            ChatOutput.send(player, ChatColor.RED + "Held brush references unknown preset.");
            return true;
        }
        String paramName = args[1].toLowerCase(Locale.ROOT);
        StringBuilder valueBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) valueBuilder.append(' ');
            valueBuilder.append(args[i]);
        }
        String rawValue = valueBuilder.toString().trim();
        com.bayzyl.detail.DetailBrushParameterSpec match = null;
        for (var spec : preset.parameterSpecs()) {
            if (spec.name().equalsIgnoreCase(paramName)) {
                match = spec;
                break;
            }
        }
        if (match == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown parameter " + paramName + " for preset " + preset.id() + ".");
            return true;
        }
        com.bayzyl.detail.DetailBrushSettings updated;
        try {
            updated = detailBrushService.safety().withParameter(current, match.name(), rawValue);
            rawValue = updated.parameters().get(match.name(), rawValue);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            return true;
        }
        if (!toolManager.bindDetailBrush(held, updated, preset.displayName(), false)) {
            ChatOutput.send(player, ChatColor.RED + "Detail brush settings are invalid. The held item was not changed.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ChatOutput.send(player, ChatColor.WHITE + paramName + " = " + rawValue);
        return true;
    }

    private boolean handleDetailBrushInfo(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /detailbrush info <preset>");
            return true;
        }
        String presetId = args[1].toLowerCase(Locale.ROOT);
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(presetId);
        if (preset == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown preset: " + presetId);
            return true;
        }
        sendHeader(player);
        ChatOutput.send(player, messageThemeService.applyAccent("&a" + preset.displayName() + " &8| &f" + titleCase(preset.family().name())));
        int defaultEstimate = preset.estimatedStampBlockCount(preset.defaults());
        if (defaultEstimate > 0) {
            ChatOutput.send(player, ChatColor.GRAY + "Default stamp size: " + ChatColor.WHITE + "~" + defaultEstimate + " blocks"
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "cooldown " + preset.stampCooldownTicks() + "t");
        }
        ChatOutput.send(player, ChatColor.AQUA + "Parameters:");
        for (var spec : preset.parameterSpecs()) {
            String range = "";
            if (spec.type() == com.bayzyl.detail.DetailBrushParameterType.FLOAT
                    || spec.type() == com.bayzyl.detail.DetailBrushParameterType.INT) {
                range = " [" + spec.min() + ".." + spec.max() + "]";
            }
            ChatOutput.send(player, ChatColor.GRAY + "  " + spec.name() + range
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "default " + ChatColor.GOLD + spec.defaultValue()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + spec.description());
        }
        return true;
    }

    private boolean handleDetailBrushList(Player player) {
        var presets = detailBrushService.registry().all();
        sendHeader(player);
        ChatOutput.send(player, messageThemeService.applyAccent("&aDetail Brushes &8| &fPresets"));
        if (presets.isEmpty()) {
            ChatOutput.send(player, ChatColor.YELLOW + "No detail brush presets are registered.");
            return true;
        }
        for (var preset : presets) {
            ChatOutput.send(player, ChatColor.GOLD + "- " + ChatColor.WHITE + preset.id()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + preset.displayName()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + titleCase(preset.family().name()));
        }
        return true;
    }

    private boolean handleDetailBrushSave(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /detailbrush save <name>");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        com.bayzyl.detail.DetailBrushSettings settings = toolManager.readDetailBrushSettings(held);
        if (settings == null) {
            ChatOutput.send(player, ChatColor.RED + "Hold a detail brush to save it.");
            return true;
        }
        com.bayzyl.detail.DetailBrushVariant builtIn = detailBrushService.registry().builtInVariant(args[1]);
        if (builtIn != null) {
            ChatOutput.send(player, ChatColor.RED + "Name '" + args[1] + "' is already claimed by built-in variant '"
                    + builtIn.displayName() + "' (preset " + builtIn.presetId() + "). Pick a different name.");
            return true;
        }
        String reservedBy = detailBrushVariantService.reservedNameOwner(args[1]);
        if (reservedBy != null) {
            ChatOutput.send(player, ChatColor.RED + "Name '" + args[1] + "' is reserved by " + reservedBy
                    + ". Pick a different name.");
            return true;
        }
        if (!detailBrushVariantService.save(args[1], settings)) {
            ChatOutput.send(player, ChatColor.RED + "Could not save variant.");
            return true;
        }
        detailBrushShortcutRegistry.refresh();
        ChatOutput.send(player, ChatColor.WHITE + "Variant saved as " + args[1] + ".");
        return true;
    }

    private boolean handleDetailBrushLoad(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /detailbrush load <name> [override ...]");
            return true;
        }
        String[] overrideTokens = args.length >= 3 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];
        return loadDetailBrushVariant(player, args[1], overrideTokens);
    }

    private boolean handleDetailBrushUndoLast(Player player, String[] args) {
        int n = 1;
        if (args.length >= 2) {
            try {
                n = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                ChatOutput.send(player, ChatColor.RED + "Usage: /db undo-last [n]");
                return true;
            }
            if (n < 1 || n > 32) {
                ChatOutput.send(player, ChatColor.RED + "n must be between 1 and 32.");
                return true;
            }
        }
        int undone = historyService.undo(player, n);
        if (undone == 0) {
            ChatOutput.send(player, ChatColor.YELLOW + "Nothing to undo.");
        } else {
            ChatOutput.send(player, ChatColor.WHITE + "Undid " + undone + " detail-brush stamp" + (undone == 1 ? "." : "s."));
        }
        return true;
    }

    private boolean handleDetailBrushGetCode(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        com.bayzyl.detail.DetailBrushSettings settings = toolManager.readDetailBrushSettings(held);
        if (settings == null) {
            ChatOutput.send(player, ChatColor.RED + "Hold a detail brush to get its code.");
            return true;
        }
        try {
            String code = new com.bayzyl.detail.DetailBrushCodeCodec(detailBrushService.safety()).encode(settings);
            com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(settings.presetId());
            String title = preset == null ? settings.presetId() : preset.displayName();
            sendHeader(player);
            ChatOutput.send(player, messageThemeService.applyAccent("&aDetail Brush Code &8| &f" + title));
            ChatOutput.send(player, ChatColor.WHITE + "Code: " + ChatColor.AQUA + code);
            ChatOutput.send(player, ChatColor.WHITE + "Share it with " + ChatColor.GOLD + "/db code " + code);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
        }
        return true;
    }

    private boolean handleDetailBrushCode(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /db code <code>");
            return true;
        }
        StringBuilder codeBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) codeBuilder.append(' ');
            codeBuilder.append(args[i]);
        }
        com.bayzyl.detail.DetailBrushSettings loaded;
        try {
            loaded = new com.bayzyl.detail.DetailBrushCodeCodec(detailBrushService.safety()).decode(codeBuilder.toString());
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            return true;
        }
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(loaded.presetId());
        String displayName = preset == null ? "Detail Brush" : preset.displayName();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ItemStack item = toolManager.createDetailBrush(loaded, displayName);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Detail brush code is invalid or exceeds the safe work limit.");
                return true;
            }
            equipInMainHand(player, item, "Detail brush code loaded: " + displayName);
        } else {
            if (!toolManager.bindDetailBrush(held, loaded, displayName, true)) {
                ChatOutput.send(player, ChatColor.RED + "Detail brush code was refused. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Detail brush code loaded onto held item: " + displayName);
        }
        return true;
    }

    private boolean loadDetailBrushVariant(Player player, String rawName) {
        return loadDetailBrushVariant(player, rawName, new String[0]);
    }

    private boolean loadDetailBrushVariant(Player player, String rawName, String overrideToken) {
        return loadDetailBrushVariant(player, rawName, overrideToken == null ? new String[0] : new String[]{overrideToken});
    }

    private boolean loadDetailBrushVariant(Player player, String rawName, String[] overrideTokens) {
        com.bayzyl.detail.DetailBrushSettings loaded = detailBrushVariantService.load(rawName);
        String displayName = rawName;
        String source = "Variant ";
        if (loaded == null) {
            if (detailBrushVariantService.exists(rawName)) {
                ChatOutput.send(player, ChatColor.RED + "Saved variant " + rawName
                        + " is invalid and was left unchanged.");
                return true;
            }
            com.bayzyl.detail.DetailBrushVariant builtIn = detailBrushService.registry().builtInVariant(rawName);
            if (builtIn == null) {
                ChatOutput.send(player, ChatColor.RED + "No saved or built-in variant named " + rawName + ".");
                return true;
            }
            loaded = builtIn.settings();
            displayName = builtIn.displayName();
            source = "Built-in variant ";
        }
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(loaded.presetId());
        if (preset != null && source.startsWith("Variant ")) {
            displayName = preset.displayName();
        }
        if (preset != null && overrideTokens != null && overrideTokens.length > 0) {
            List<String> applied = new ArrayList<>();
            for (String overrideToken : overrideTokens) {
                if (overrideToken == null || overrideToken.isBlank()) {
                    continue;
                }
                String paramName = detailBrushOverrideParamFor(preset, overrideToken);
                if (paramName == null) {
                    ChatOutput.send(player, ChatColor.RED + "Unknown override token: " + overrideToken
                            + ". Valid: " + String.join(", ", detailBrushOverrideTokens(preset)));
                    return true;
                }
                loaded = applyDetailBrushOverride(preset, loaded, overrideToken);
                applied.add(paramName + "=" + overrideToken.toLowerCase(Locale.ROOT));
            }
            if (!applied.isEmpty()) {
                displayName = displayName + " (" + String.join(", ", applied) + ")";
            }
        }
        WorkEstimate safetyEstimate = detailBrushService.safety().assess(loaded);
        if (safetyEstimate.hardRejected()) {
            ChatOutput.send(player, ChatColor.RED + "Detail brush variant refused: " + safetyEstimate.reason());
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ItemStack item = toolManager.createDetailBrush(loaded, displayName);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Detail brush variant is invalid or exceeds the safe work limit.");
                return true;
            }
            equipInMainHand(player, item, source + "loaded: " + rawName);
        } else {
            if (!toolManager.bindDetailBrush(held, loaded, displayName, true)) {
                ChatOutput.send(player, ChatColor.RED + "Detail brush variant was refused. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + source + rawName + " loaded onto held item.");
        }
        return true;
    }

    private boolean handleDetailBrushShortcutRoot(CommandSender sender, String commandName, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length > 0) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /" + commandName);
            return true;
        }
        return loadDetailBrushVariant(player, commandName);
    }

    private boolean handleDetailBrushVariants(Player player, String[] args) {
        String presetId = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        if (presetId != null && detailBrushService.registry().get(presetId) == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown preset: " + presetId);
            return true;
        }
        sendHeader(player);
        ChatOutput.send(player, messageThemeService.applyAccent("&aDetail Brush Variants &8| &f" + (presetId == null ? "all" : presetId)));
        var builtIns = detailBrushService.registry().builtInVariants(presetId);
        if (!builtIns.isEmpty()) {
            ChatOutput.send(player, ChatColor.AQUA + "Built-in:");
            for (var variant : builtIns) {
                ChatOutput.send(player, ChatColor.GOLD + "- " + ChatColor.WHITE + variant.name()
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + variant.displayName()
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + variant.description());
            }
        }
        var names = detailBrushVariantService.list();
        if (names.isEmpty()) {
            if (builtIns.isEmpty()) {
                ChatOutput.send(player, ChatColor.YELLOW + "No detail brush variants saved yet.");
            }
            return true;
        }
        ChatOutput.send(player, ChatColor.AQUA + "Saved:");
        for (String name : names) {
            if (presetId != null) {
                com.bayzyl.detail.DetailBrushSettings saved = detailBrushVariantService.load(name);
                if (saved == null || !saved.presetId().equalsIgnoreCase(presetId)) {
                    continue;
                }
            }
            ChatOutput.send(player, ChatColor.GOLD + "- " + ChatColor.WHITE + name);
        }
        return true;
    }

    private boolean handleDetailBrushDelete(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /detailbrush delete <name>");
            return true;
        }
        if (!detailBrushVariantService.delete(args[1])) {
            ChatOutput.send(player, ChatColor.RED + "No variant named " + args[1] + ".");
            return true;
        }
        detailBrushShortcutRegistry.refresh();
        ChatOutput.send(player, ChatColor.WHITE + "Variant " + args[1] + " deleted.");
        return true;
    }

    private boolean isDetailBrushSubcommand(String value) {
        return Set.of(
                "tool", "give", "get", "set", "param", "info", "describe", "presets", "list",
                "save", "load", "code", "getcode", "variants", "saved", "delete", "remove", "mode", "none", "unbind",
                "undo-last", "undolast"
        ).contains(value);
    }

    /** All STRING-enum tokens valid for any STRING param of this preset, palette categories first. */
    private List<String> detailBrushOverrideTokens(com.bayzyl.detail.DetailBrushPreset preset) {
        if (preset == null) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        for (com.bayzyl.detail.DetailBrushParameterSpec spec : detailBrushOverrideStringSpecs(preset)) {
            tokens.addAll(detailBrushService.safety().allowedValues(preset.id(), spec.name()));
        }
        return tokens;
    }

    /** If token matches a STRING-enum value on this preset, returns the matching param name; else null. */
    private String detailBrushOverrideParamFor(com.bayzyl.detail.DetailBrushPreset preset, String token) {
        if (preset == null || token == null) return null;
        String t = token.toLowerCase(Locale.ROOT);
        for (com.bayzyl.detail.DetailBrushParameterSpec spec : detailBrushOverrideStringSpecs(preset)) {
            if (detailBrushService.safety().allowedValues(preset.id(), spec.name()).contains(t)) {
                return spec.name();
            }
        }
        return null;
    }

    private List<com.bayzyl.detail.DetailBrushParameterSpec> detailBrushOverrideStringSpecs(
            com.bayzyl.detail.DetailBrushPreset preset) {
        return preset.parameterSpecs().stream()
                .filter(s -> s.type() == com.bayzyl.detail.DetailBrushParameterType.STRING)
                .sorted(java.util.Comparator.comparingInt(s -> detailBrushOverrideCategoryRank(s.name())))
                .toList();
    }

    private static int detailBrushOverrideCategoryRank(String paramName) {
        if (paramName == null) return 99;
        return switch (paramName.toLowerCase(Locale.ROOT)) {
            case "color", "tint", "tone", "palette" -> 0;
            case "direction", "rotation", "heading" -> 1;
            default -> 2;
        };
    }

    private com.bayzyl.detail.DetailBrushSettings applyDetailBrushOverride(
            com.bayzyl.detail.DetailBrushPreset preset,
            com.bayzyl.detail.DetailBrushSettings settings,
            String token
    ) {
        if (token == null || token.isBlank()) return settings;
        String paramName = detailBrushOverrideParamFor(preset, token);
        if (paramName == null) return settings;
        return settings.withParameters(settings.parameters().with(paramName, token.toLowerCase(Locale.ROOT)));
    }

    private List<String> suggestDetailBrushArgs(CommandSender sender, String[] args) {
        List<String> variants = listDetailBrushVariantNames();
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of(
                    "tool", "set", "info", "presets", "save", "load", "code", "getcode", "variants", "delete", "mode", "none", "undo-last"
            ));
            suggestions.addAll(variants);
            return filterPrefix(suggestions, args[0]);
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (first.equals("tool") || first.equals("get") || first.equals("give") || first.equals("info") || first.equals("describe")) {
                return filterPrefix(detailBrushService.registry().ids(), args[1]);
            }
            if (first.equals("load")) {
                return filterPrefix(variants, args[1]);
            }
            if (first.equals("undo-last") || first.equals("undolast")) {
                return filterPrefix(List.of("1", "2", "3", "5", "10"), args[1]);
            }
            if (!isDetailBrushSubcommand(first) && variants.contains(first)) {
                // /db <variant> [override-token ...]
                com.bayzyl.detail.DetailBrushPreset preset = presetForVariant(first);
                return filterPrefix(detailBrushOverrideTokens(preset), args[1]);
            }
            if (first.equals("code")) {
                String code = currentDetailBrushCode(sender);
                return code.isBlank() ? Collections.emptyList() : filterPrefix(List.of(code), args[1]);
            }
            if (first.equals("variants") || first.equals("saved")) {
                return filterPrefix(detailBrushService.registry().ids(), args[1]);
            }
            if (first.equals("set") || first.equals("param")) {
                return filterPrefix(detailBrushParameterNames(sender), args[1]);
            }
            if (first.equals("delete") || first.equals("remove")) {
                return filterPrefix(detailBrushVariantService.list(), args[1]);
            }
            if (first.equals("mode")) {
                return filterPrefix(List.of("stamp", "stroke"), args[1]);
            }
        }
        if (args.length > 2) {
            if (first.equals("set") || first.equals("param")) {
                String param = args[1].toLowerCase(Locale.ROOT);
                return filterPrefix(detailBrushValueSuggestions(sender, param), args[args.length - 1]);
            }
            if (first.equals("tool") || first.equals("get") || first.equals("give")) {
                if (args.length == 3) {
                    return filterPrefix(detailBrushService.registry().builtInVariantNames(args[1]), args[2]);
                }
                com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(args[1]);
                return filterPrefix(detailBrushOverrideTokens(preset), args[args.length - 1]);
            }
            if (first.equals("load")) {
                com.bayzyl.detail.DetailBrushPreset preset = presetForVariant(args[1]);
                return filterPrefix(detailBrushOverrideTokens(preset), args[args.length - 1]);
            }
            if (!isDetailBrushSubcommand(first) && variants.contains(first)) {
                com.bayzyl.detail.DetailBrushPreset preset = presetForVariant(first);
                return filterPrefix(detailBrushOverrideTokens(preset), args[args.length - 1]);
            }
        }
        return Collections.emptyList();
    }

    private com.bayzyl.detail.DetailBrushPreset presetForVariant(String name) {
        if (name == null) return null;
        com.bayzyl.detail.DetailBrushSettings saved = detailBrushVariantService.load(name);
        if (saved != null) {
            return detailBrushService.registry().get(saved.presetId());
        }
        if (detailBrushVariantService.exists(name)) {
            return null;
        }
        com.bayzyl.detail.DetailBrushVariant builtIn = detailBrushService.registry().builtInVariant(name);
        return builtIn == null ? null : detailBrushService.registry().get(builtIn.presetId());
    }

    private String currentDetailBrushCode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return "";
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        com.bayzyl.detail.DetailBrushSettings settings = toolManager.readDetailBrushSettings(held);
        if (settings == null) {
            return "";
        }
        try {
            return new com.bayzyl.detail.DetailBrushCodeCodec(detailBrushService.safety()).encode(settings);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private List<String> detailBrushParameterNames(CommandSender sender) {
        com.bayzyl.detail.DetailBrushPreset current = currentDetailBrushPreset(sender);
        if (current != null) {
            return current.parameterSpecs().stream()
                    .map(com.bayzyl.detail.DetailBrushParameterSpec::name)
                    .toList();
        }
        Set<String> names = new LinkedHashSet<>();
        for (com.bayzyl.detail.DetailBrushPreset preset : detailBrushService.registry().all()) {
            for (com.bayzyl.detail.DetailBrushParameterSpec spec : preset.parameterSpecs()) {
                names.add(spec.name());
            }
        }
        return List.copyOf(names);
    }

    private List<String> detailBrushValueSuggestions(CommandSender sender, String param) {
        com.bayzyl.detail.DetailBrushParameterSpec spec = detailBrushParameterSpec(sender, param);
        if (spec == null) {
            return Collections.emptyList();
        }
        if (spec.type() == com.bayzyl.detail.DetailBrushParameterType.STRING) {
            com.bayzyl.detail.DetailBrushPreset current = currentDetailBrushPreset(sender);
            if (current != null) {
                return detailBrushService.safety().allowedValues(current.id(), spec.name());
            }
            Set<String> values = new LinkedHashSet<>();
            for (com.bayzyl.detail.DetailBrushPreset preset : detailBrushService.registry().all()) {
                values.addAll(detailBrushService.safety().allowedValues(preset.id(), spec.name()));
            }
            return List.copyOf(values);
        }
        Set<String> values = new LinkedHashSet<>();
        values.add(spec.defaultValue());
        if (spec.type() == com.bayzyl.detail.DetailBrushParameterType.INT) {
            values.add(Integer.toString((int) Math.round(spec.min())));
            values.add(Integer.toString((int) Math.round(spec.max())));
            for (int candidate : List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 24, 32, 64)) {
                if (candidate >= spec.min() && candidate <= spec.max()) {
                    values.add(Integer.toString(candidate));
                }
            }
        } else {
            values.add(formatDetailBrushNumber(spec.min()));
            values.add(formatDetailBrushNumber(spec.max()));
            for (double candidate : List.of(-1.0, -0.5, 0.0, 0.25, 0.5, 0.75, 1.0)) {
                if (candidate >= spec.min() && candidate <= spec.max()) {
                    values.add(formatDetailBrushNumber(candidate));
                }
            }
        }
        return List.copyOf(values);
    }

    private com.bayzyl.detail.DetailBrushParameterSpec detailBrushParameterSpec(CommandSender sender, String param) {
        com.bayzyl.detail.DetailBrushPreset current = currentDetailBrushPreset(sender);
        if (current != null) {
            for (com.bayzyl.detail.DetailBrushParameterSpec spec : current.parameterSpecs()) {
                if (spec.name().equalsIgnoreCase(param)) {
                    return spec;
                }
            }
        }
        for (com.bayzyl.detail.DetailBrushPreset preset : detailBrushService.registry().all()) {
            for (com.bayzyl.detail.DetailBrushParameterSpec spec : preset.parameterSpecs()) {
                if (spec.name().equalsIgnoreCase(param)) {
                    return spec;
                }
            }
        }
        return null;
    }

    private com.bayzyl.detail.DetailBrushPreset currentDetailBrushPreset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        com.bayzyl.detail.DetailBrushSettings settings = toolManager.readDetailBrushSettings(held);
        return settings == null ? null : detailBrushService.registry().get(settings.presetId());
    }

    private String formatDetailBrushNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return Double.toString(value);
    }

    private List<String> listDetailBrushVariantNames() {
        Set<String> names = new LinkedHashSet<>(detailBrushService.registry().builtInVariantNames());
        names.addAll(detailBrushVariantService.list());
        return List.copyOf(names);
    }

    private boolean handleDetailBrushMode(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /detailbrush mode <stamp|stroke>");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        com.bayzyl.detail.DetailBrushSettings current = toolManager.readDetailBrushSettings(held);
        if (current == null) {
            ChatOutput.send(player, ChatColor.RED + "Hold a detail brush.");
            return true;
        }
        com.bayzyl.detail.DetailBrushMode newMode;
        try {
            newMode = com.bayzyl.detail.DetailBrushMode.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + "Mode must be stamp or stroke.");
            return true;
        }
        com.bayzyl.detail.DetailBrushPreset preset = detailBrushService.registry().get(current.presetId());
        String displayName = preset == null ? current.presetId() : preset.displayName();
        if (!toolManager.bindDetailBrush(held, current.withMode(newMode), displayName, false)) {
            ChatOutput.send(player, ChatColor.RED + "Detail brush mode was refused. The held item was not changed.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ChatOutput.send(player, ChatColor.WHITE + "Mode set to " + newMode.name().toLowerCase(Locale.ROOT) + ".");
        return true;
    }

    private boolean handleBrushClipboard(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = new ItemStack(Material.BRUSH);
            toolManager.bindClipboardBrush(item, true);
            equipBrushInMainHand(player, item);
            ChatOutput.send(player, ChatColor.WHITE + "Created clipboard brush and equipped it in your hand.");
        } else {
            toolManager.bindClipboardBrush(held, false);
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Bound clipboard brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Right-click a block to paste your clipboard at that point.");
        return true;
    }

    private boolean handleBrushPaint(Player player, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /brush paint <block> [density:0.3] [size:5] [mask:<blocks>]");
            return true;
        }
        Material material = EditUtil.parseBlock(args[1]);
        if (material == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown block: " + args[1]);
            return true;
        }
        int size = 5;
        double density = 0.3;
        String maskRaw = null;
        for (int i = 2; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (token.startsWith("size:")) {
                try {
                    size = Integer.parseInt(token.substring(5));
                    if (size < 1 || size > BrushSafety.PAINT_SIZE_MAX) {
                        throw new NumberFormatException("out of range");
                    }
                } catch (NumberFormatException ex) {
                    ChatOutput.send(player, ChatColor.RED + "Invalid size: " + args[i]);
                    return true;
                }
            } else if (token.startsWith("density:")) {
                try {
                    density = Double.parseDouble(token.substring(8));
                    if (!Double.isFinite(density) || density < 0.0 || density > 1.0) {
                        throw new NumberFormatException("out of range");
                    }
                } catch (NumberFormatException ex) {
                    ChatOutput.send(player, ChatColor.RED + "Invalid density: " + args[i]);
                    return true;
                }
            } else if (token.startsWith("mask:")) {
                maskRaw = args[i].substring(5);
            } else {
                ChatOutput.send(player, ChatColor.RED + "Unknown paint option: " + args[i]);
                return true;
            }
        }
        WorkEstimate paintEstimate = BrushSafety.assessPaint(size);
        if (paintEstimate.hardRejected()) {
            ChatOutput.send(player, ChatColor.RED + paintEstimate.reason());
            return true;
        }
        if (maskRaw != null && !BrushSafety.isValidMaskRaw(maskRaw)) {
            ChatOutput.send(player, ChatColor.RED + "Invalid mask. Use at most "
                    + BrushSafety.MASK_TEXT_MAX + " characters and valid block or block-tag tokens.");
            return true;
        }
        BlockMask mask = maskRaw == null ? null : BlockMask.parse(maskRaw);
        PaintBrushSettings settings = new PaintBrushSettings(material, size, density, mask);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = new ItemStack(Material.BRUSH);
            if (!toolManager.bindPaintBrush(item, settings, true)) {
                ChatOutput.send(player, ChatColor.RED + "Paint brush settings are invalid or exceed the safe work limit.");
                return true;
            }
            equipBrushInMainHand(player, item);
            ChatOutput.send(player, ChatColor.WHITE + "Created paint brush and equipped it in your hand.");
        } else {
            if (!toolManager.bindPaintBrush(held, settings, false)) {
                ChatOutput.send(player, ChatColor.RED + "Paint brush settings are invalid. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Bound paint brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Block: " + ChatColor.WHITE + material.name().toLowerCase(Locale.ROOT)
                + ChatColor.DARK_GRAY + " | size: " + ChatColor.WHITE + size
                + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", density)
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (mask == null ? "any" : mask.summary()));
        return true;
    }

    private boolean handleBrushStructure(Player player, String[] args) {
        if (!bayzylAccess.allowed(player, CommandCapability.GENSTRUCTURE)) {
            ChatOutput.send(player, ChatColor.RED + "You do not have permission to create structure brushes.");
            return true;
        }
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /brush structure <structure_id> [at:<player|target|center|selection-center>] [confirm:true]");
            return true;
        }

        String[] requestArgs = Arrays.copyOfRange(args, 1, args.length);
        com.bayzyl.generation.StructureGenRequest request;
        try {
            request = GeneratorCommandParser.parseStructureGen(requestArgs);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            return true;
        }

        VanillaContentRegistry.Entry meta = VanillaContentRegistry.structureMeta(request.structureId());
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        StructureBrushSettings settings = new StructureBrushSettings(request.structureId(), request.anchorMode(), request.confirm());
        if (handEmpty) {
            ItemStack item = toolManager.createStructureBrush(settings);
            equipBrushInMainHand(player, item);
            ChatOutput.send(player, ChatColor.WHITE + "Created structure brush and equipped it in your hand.");
        } else {
            toolManager.bindStructureBrush(held, settings, false);
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Bound structure brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Structure: " + ChatColor.WHITE + request.structureId()
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + meta.description()
                + ChatColor.DARK_GRAY + " | anchor: " + ChatColor.WHITE + (request.anchorMode() == ShapeAnchorMode.PLAYER ? "player" : request.anchorMode().name().toLowerCase(Locale.ROOT))
                + ChatColor.DARK_GRAY + " | confirm: " + ChatColor.WHITE + (request.confirm() ? "yes" : "no"));
        WorkEstimate estimate = OperationLimits.estimateStructure(meta.footprintRadius(), meta.footprintHeight());
        if (!estimate.hardRejected() && !estimate.permits(request.confirm())) {
            ChatOutput.send(player, ChatColor.YELLOW + "This structure will require confirm:true when you place it.");
        }
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Right-click to place the structure with safety rails.");
        return true;
    }

    private boolean handleBrushPattern(Player player, PatternBrushMode mode, String[] args) {
        PatternBrushSettings settings;
        try {
            settings = parsePatternBrush(mode, args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = new ItemStack(Material.BRUSH);
            if (!toolManager.bindPatternBrush(item, settings, true)) {
                ChatOutput.send(player, ChatColor.RED + "Pattern brush settings are invalid or exceed the safe work limit.");
                return true;
            }
            equipBrushInMainHand(player, item);
            ChatOutput.send(player, ChatColor.WHITE + "Created " + mode.displayName().toLowerCase(Locale.ROOT) + " brush and equipped it in your hand.");
        } else {
            if (!toolManager.bindPatternBrush(held, settings, false)) {
                ChatOutput.send(player, ChatColor.RED + "Pattern brush settings are invalid. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Bound " + mode.displayName().toLowerCase(Locale.ROOT) + " brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Size: " + ChatColor.WHITE + settings.size()
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
        BlockMask fromMask = null;
        String maskRaw = null;
        String fromMaskRaw = null;
        Material to = null;
        List<Material> palette = List.of();
        int optionStart = switch (mode) {
            case REPLACE -> -1;
            case RESTORE -> 1;
            case VEGETATION, DECAY -> args.length >= 2 && !args[1].contains(":") ? 2 : 1;
            default -> 2;
        };

        switch (mode) {
            case SPATTER, SURFACE -> {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Usage: /brush " + mode.commandName() + " <block> [size:5] [density:0.35] [mask:<blocks>]");
                }
                to = requireBlock(args[1]);
            }
            case REPLACE -> {
                optionStart = args.length;
                for (int i = 1; i < args.length; i++) {
                    if (args[i].contains(":")) {
                        optionStart = i;
                        break;
                    }
                }
                if (optionStart < 3) {
                    throw new IllegalArgumentException("Usage: /brush replace <from...> <to> [size:5]");
                }
                fromMaskRaw = String.join(",", java.util.Arrays.copyOfRange(args, 1, optionStart - 1));
                to = requireBlock(args[optionStart - 1]);
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
                case "mask" -> maskRaw = value;
                case "palette" -> palette = parsePaletteArg(value);
                default -> throw new IllegalArgumentException("Unknown brush option: " + key);
            }
        }
        if ((mode == PatternBrushMode.BLEND || mode == PatternBrushMode.NOISE || mode == PatternBrushMode.VEGETATION) && palette.isEmpty()) {
            throw new IllegalArgumentException("Palette must contain at least one block.");
        }
        WorkEstimate estimate = BrushSafety.assessPattern(mode, size, 0L);
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
        if (fromMaskRaw != null) {
            if (!BrushSafety.isValidMaskRaw(fromMaskRaw)) {
                throw new IllegalArgumentException("Replace mask must contain only valid block or block-tag tokens.");
            }
            fromMask = BlockMask.parse(fromMaskRaw);
        }
        if (maskRaw != null) {
            if (!BrushSafety.isValidMaskRaw(maskRaw)) {
                throw new IllegalArgumentException("Mask must contain only valid block or block-tag tokens.");
            }
            mask = BlockMask.parse(maskRaw);
        }
        return new PatternBrushSettings(mode, fromMask, to, palette, size, density, mask);
    }

    private Material requireBlock(String raw) {
        Material material = EditUtil.parseBlock(raw);
        if (material == null) {
            throw new IllegalArgumentException("Unknown block: " + raw);
        }
        return material;
    }

    private List<Material> parsePaletteArg(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 4_096
                || commaSeparatedEntryCount(raw) > 64) {
            throw new IllegalArgumentException("Palette must contain 1 to 64 bounded block names.");
        }
        List<Material> materials = new ArrayList<>();
        for (String part : raw.split(",", -1)) {
            if (part.isBlank() || part.length() > 128) {
                throw new IllegalArgumentException("Palette block names must be 1 to 128 characters.");
            }
            materials.add(requireBlock(part));
        }
        return materials;
    }

    private static int commaSeparatedEntryCount(String raw) {
        int entries = 1;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ',') {
                entries++;
                if (entries > 64) {
                    return entries;
                }
            }
        }
        return entries;
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
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Density must be between 0.0 and 1.0.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Density must be a number between 0.0 and 1.0.");
        }
    }

    private Boolean parseEraserFlag(String option, String rawValue) {
        String value = rawValue == null ? "" : rawValue.toLowerCase(Locale.ROOT);
        return switch (option) {
            case "surface" -> switch (value) {
                case "on", "true", "surface" -> true;
                case "off", "false", "all" -> false;
                default -> null;
            };
            case "selection" -> switch (value) {
                case "only", "true", "selection" -> true;
                case "any", "false", "off" -> false;
                default -> null;
            };
            case "carve" -> switch (value) {
                case "air", "carve", "true", "on" -> true;
                case "full", "false", "off" -> false;
                default -> null;
            };
            case "bedrock" -> switch (value) {
                case "on", "true", "edit" -> true;
                case "off", "false", "protected", "safe" -> false;
                default -> null;
            };
            default -> null;
        };
    }

    private static String listGenTypes() {
        StringBuilder sb = new StringBuilder();
        for (com.bayzyl.gen.GenBrushType type : com.bayzyl.gen.GenBrushType.values()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(type.commandName());
        }
        return sb.toString();
    }

    private boolean handleBrushGen(Player player, com.bayzyl.gen.GenBrushType type,
                                   String[] fullArgs, int startIndex) {
        if (genBrushService == null) {
            ChatOutput.send(player, ChatColor.RED + "Gen brush service not initialized.");
            return true;
        }
        String[] tail = new String[Math.max(0, fullArgs.length - startIndex)];
        if (tail.length > 0) {
            System.arraycopy(fullArgs, startIndex, tail, 0, tail.length);
        }
        com.bayzyl.gen.GenBrushSettings settings;
        try {
            settings = com.bayzyl.gen.GenBrushCommandParser.parse(type, tail);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(player, ChatColor.RED + ex.getMessage());
            ChatOutput.send(player, ChatColor.GRAY + "Usage: /brush gen " + type.commandName()
                    + (type == com.bayzyl.gen.GenBrushType.CAVE ? " [subtype]" : "")
                    + " [size:<n>] [intensity:<0..1>] [height:<n>] [mask:<blocks>] [adapt:on|off] [seed:<n>]");
            return true;
        }
        if (settings.radius() > 48) {
            ChatOutput.send(player, ChatColor.RED + "Gen brush radius capped at 48 (got " + settings.radius() + ").");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createGenBrush(settings);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Gen brush binding was refused by the safety policy.");
                return true;
            }
            equipBrushInMainHand(player, item);
        } else {
            if (!toolManager.bindGenBrush(held, settings, true)) {
                ChatOutput.send(player, ChatColor.RED + "Gen brush binding was refused; the held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
        }
        ChatOutput.send(player, ChatColor.WHITE + "Bound " + ChatColor.DARK_GREEN + type.displayName()
                + ChatColor.WHITE + " gen brush.");
        ChatOutput.send(player, ChatColor.DARK_GRAY + settings.summary());
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Right click terrain to stamp. "
                + "/mask, /size, or /brush none to tune.");
        return true;
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
                ChatOutput.send(player, ChatColor.RED + "Radius must be a number.");
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
                        ChatOutput.send(player, ChatColor.RED + "Radius must be a number.");
                        return true;
                    }
                }
                case "mask" -> mask = value;
                case "surface" -> {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(player, ChatColor.RED + "Invalid surface option: " + value);
                        return true;
                    }
                    surfaceOnly = parsedFlag;
                }
                case "selection" -> {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(player, ChatColor.RED + "Invalid selection option: " + value);
                        return true;
                    }
                    selectionOnly = parsedFlag;
                }
                case "carve" -> {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(player, ChatColor.RED + "Invalid carve option: " + value);
                        return true;
                    }
                    carveOnly = parsedFlag;
                }
                case "bedrock" -> {
                    Boolean parsedFlag = parseEraserFlag(key, value);
                    if (parsedFlag == null) {
                        ChatOutput.send(player, ChatColor.RED + "Invalid bedrock option: " + value);
                        return true;
                    }
                    editBedrock = parsedFlag;
                }
                default -> {
                    ChatOutput.send(player, ChatColor.RED + "Unknown eraser option: " + key);
                    return true;
                }
            }
        }

        int maxRadius = eraserService.getMaxRadius(player);
        if (radius > maxRadius && maxRadius < Integer.MAX_VALUE) {
            ChatOutput.send(player, ChatColor.RED + "Max eraser radius is " + maxRadius + ".");
            return true;
        }
        WorkEstimate eraserEstimate = BrushSafety.assessEraser(radius);
        if (eraserEstimate.hardRejected()) {
            ChatOutput.send(player, ChatColor.RED + eraserEstimate.reason());
            return true;
        }
        if (mask != null && !BrushSafety.isValidMaskRaw(mask)) {
            ChatOutput.send(player, ChatColor.RED + "Invalid mask. Use at most "
                    + BrushSafety.MASK_TEXT_MAX + " characters and valid block or block-tag tokens.");
            return true;
        }
        EraserSettings settings = new EraserSettings(radius, mask == null ? null : BlockMask.parse(mask),
                surfaceOnly, selectionOnly, carveOnly, editBedrock);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createEraser(settings);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Eraser settings are invalid or exceed the safe work limit.");
                return true;
            }
            equipBrushInMainHand(player, item);
            ChatOutput.send(player, ChatColor.WHITE + "Created eraser brush and equipped it in your hand.");
        } else {
            if (!toolManager.bindEraser(held, settings, false)) {
                ChatOutput.send(player, ChatColor.RED + "Eraser settings are invalid. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Bound eraser brush to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + radius
                + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (settings.getMask() == null ? "any" : settings.getMask().summary())
                + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (editBedrock ? "on" : "protected"));
        return true;
    }

    private boolean handleBrushTerrain(Player player, TerrainBrushType type, String[] args) {
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /brush " + type.commandName() + " <radius> [power] [bedrock:on|off]");
            return true;
        }
        int radius;
        int power = type == TerrainBrushType.NATURALIZE ? 3 : type.isCleanupMode() ? 2 : 1;
        boolean editBedrock = false;
        boolean positionalPower = args.length >= 3 && !args[2].contains(":");
        try {
            radius = Integer.parseInt(args[1]);
            if (positionalPower) {
                power = Integer.parseInt(args[2]);
            }
        } catch (NumberFormatException ex) {
            ChatOutput.send(player, ChatColor.RED + "Radius and " + type.powerLabel().toLowerCase(Locale.ROOT) + " must be numbers.");
            return true;
        }
        for (int i = 2; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                if (i == 2 && positionalPower) {
                    continue;
                }
                ChatOutput.send(player, ChatColor.RED + "Unknown terrain option: " + args[i]);
                return true;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "power", "strength", "iterations", "reach", "aggression" -> {
                    try {
                        power = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        ChatOutput.send(player, ChatColor.RED + type.powerLabel() + " must be a number.");
                        return true;
                    }
                }
                case "bedrock" -> {
                    Boolean parsedFlag = parseEraserFlag("bedrock", value);
                    if (parsedFlag == null) {
                        ChatOutput.send(player, ChatColor.RED + "Invalid bedrock option: " + value);
                        return true;
                    }
                    editBedrock = parsedFlag;
                }
                default -> {
                    ChatOutput.send(player, ChatColor.RED + "Unknown brush option: " + key);
                    return true;
                }
            }
        }
        if (radius <= 0 || power <= 0) {
            ChatOutput.send(player, ChatColor.RED + "Radius and " + type.powerLabel().toLowerCase(Locale.ROOT) + " must be greater than 0.");
            return true;
        }

        TerrainBrushSettings settings = new TerrainBrushSettings(type, radius, power, editBedrock);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean handEmpty = held == null || held.getType() == Material.AIR;
        if (handEmpty) {
            ItemStack item = toolManager.createTerrainBrush(type, radius, power, editBedrock);
            if (item == null) {
                ChatOutput.send(player, ChatColor.RED + "Terrain brush settings exceed the safe work limit.");
                return true;
            }
            equipBrushInMainHand(player, item);
            ChatOutput.send(player, ChatColor.WHITE + "Created " + type.displayName() + " and equipped it in your hand.");
        } else {
            if (!toolManager.bindTerrainBrush(held, settings, false)) {
                ChatOutput.send(player, ChatColor.RED + "Terrain brush settings are invalid. The held item was not changed.");
                return true;
            }
            player.getInventory().setItemInMainHand(held);
            ChatOutput.send(player, ChatColor.WHITE + "Bound " + type.displayName() + " to your held "
                    + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        }
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + radius
                + ChatColor.DARK_GRAY + " | " + type.powerLabel().toLowerCase(Locale.ROOT) + ": " + ChatColor.WHITE + power
                + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (editBedrock ? "on" : "protected"));
        return true;
    }

    private boolean handleBrushMaskCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
                ChatOutput.send(player, ChatColor.RED + "No brush bound to held item. Usage: /mask <blocks|none>");
                return true;
            }
            ChatOutput.send(player, ChatColor.WHITE + "Mask: " + (current == null ? "any" : current.summary()));
            return true;
        }

        String raw = args[0];
        if (raw.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            raw = raw.substring("mask:".length());
        }
        if (held == null || held.getType() == Material.AIR) {
            ChatOutput.send(player, ChatColor.RED + "Hold a brush to set its mask.");
            return true;
        }
        boolean clear = raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("off")
                || raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("any") || raw.isBlank();
        if (!clear && !BrushSafety.isValidMaskRaw(raw)) {
            ChatOutput.send(player, ChatColor.RED + "Invalid mask. Use at most "
                    + BrushSafety.MASK_TEXT_MAX + " characters and valid block or block-tag tokens.");
            return true;
        }
        BlockMask mask = clear ? null : BlockMask.parse(raw);
        boolean updated = toolManager.updateShapeBrushMask(held, mask)
                || toolManager.updatePaintBrushMask(held, mask)
                || toolManager.updatePatternBrushMask(held, mask)
                || toolManager.updateEraserMask(held, mask)
                || toolManager.updateGenBrushMask(held, mask);
        if (!updated) {
            ChatOutput.send(player, ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ChatOutput.send(player, ChatColor.WHITE + "Mask: " + (mask == null ? "any" : mask.summary()));
        return true;
    }

    private boolean handleGlobalMaskCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (globalMaskService == null) {
            ChatOutput.send(player, ChatColor.RED + "Global mask service unavailable.");
            return true;
        }
        UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            Boolean nowEnabled = globalMaskService.toggle(playerId);
            if (nowEnabled == null) {
                ChatOutput.send(player, ChatColor.WHITE + "Global mask: none");
                ChatOutput.send(player, ChatColor.GRAY + "Set: /gmask <blocks>  Clear: /gmask none");
                return true;
            }
            BlockMask configured = globalMaskService.getConfigured(playerId);
            String summary = configured == null ? "none" : configured.summary();
            ChatOutput.send(player, ChatColor.WHITE + "Global mask " + (nowEnabled ? "on" : "off")
                    + ": " + summary);
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            BlockMask configured = globalMaskService.getConfigured(playerId);
            if (configured == null || configured.isAny()) {
                ChatOutput.send(player, ChatColor.WHITE + "Global mask: none");
            } else {
                String state = globalMaskService.isEnabled(playerId) ? "on" : "off";
                ChatOutput.send(player, ChatColor.WHITE + "Global mask " + state + ": " + configured.summary());
            }
            ChatOutput.send(player, ChatColor.GRAY + "Set: /gmask <blocks>  Toggle: /gmask  Clear: /gmask none");
            return true;
        }
        String raw = args[0];
        if (raw.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            raw = raw.substring("mask:".length());
        }
        if (raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("off")
                || raw.equalsIgnoreCase("clear") || raw.equalsIgnoreCase("any")
                || raw.isBlank()) {
            globalMaskService.clear(playerId);
            ChatOutput.send(player, ChatColor.WHITE + "Global mask cleared.");
            return true;
        }
        BlockMask mask = BlockMask.parse(raw);
        if (mask.isAny()) {
            ChatOutput.send(player, ChatColor.RED + "Could not parse any blocks from: " + raw);
            return true;
        }
        globalMaskService.set(playerId, mask);
        ChatOutput.send(player, ChatColor.WHITE + "Global mask on: " + mask.summary());
        return true;
    }

    private boolean handleBrushMaterialCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /material <block|distribution>");
            return true;
        }
        BlockDistribution distribution;
        try {
            distribution = BlockDistribution.parse(args[0]);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid block distribution: " + ex.getMessage());
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ChatOutput.send(player, ChatColor.RED + "Hold a brush to set its material.");
            return true;
        }
        boolean updated = toolManager.updateShapeBrushDistribution(held, distribution);
        if (!updated && distribution.isSingleMaterial()) {
            Material material = distribution.getSoleMaterial();
            updated = toolManager.updatePaintBrushMaterial(held, material)
                    || toolManager.updatePatternBrushMaterial(held, material)
                    || toolManager.updateShapeBrushMaterial(held, material);
        }
        if (!updated) {
            ChatOutput.send(player, ChatColor.RED + "Held item has no matching brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ChatOutput.send(player, ChatColor.WHITE + "Blocks: " + distribution);
        return true;
    }

    private boolean handleBrushSizeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /size <radius> [height]");
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
            ChatOutput.send(player, ChatColor.RED + "Size must be a number.");
            return true;
        }
        if (size < 1 || size > 64) {
            ChatOutput.send(player, ChatColor.RED + "Size must be between 1 and 64.");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ChatOutput.send(player, ChatColor.RED + "Hold a brush to set its size.");
            return true;
        }
        ToolType heldToolType = toolManager.getToolType(held);
        boolean updatedAny;
        if (heldToolType == ToolType.SHAPE_BRUSH) {
            ShapeBrushSettings current = toolManager.readShapeBrushSettings(held);
            ShapeBrushSettings candidate = toolManager.resizedShapeBrush(current, size, height);
            if (candidate == null) {
                ChatOutput.send(player, ChatColor.RED + "Shape brush is invalid. The brush was not changed.");
                return true;
            }
            WorkEstimate resizeEstimate = toolManager.estimateShapeBrush(candidate);
            if (resizeEstimate.hardRejected()) {
                ChatOutput.send(player, ChatColor.RED + "Shape brush resize refused: " + resizeEstimate.reason());
                return true;
            }
            if (!resizeEstimate.permits(shouldBypassConfirm(player, candidate.confirm()))) {
                ChatOutput.send(player, ChatColor.RED + "Large shape brush resize. Rebind with confirm:true before using this size.");
                return true;
            }
            updatedAny = toolManager.updateShapeBrushSize(held, size, height);
            if (!updatedAny) {
                ChatOutput.send(player, ChatColor.RED + "Shape brush size is invalid or exceeds the safe work limit. The brush was not changed.");
                return true;
            }
        } else {
            updatedAny = toolManager.updatePaintBrushSize(held, size)
                    || toolManager.updatePatternBrushSize(held, size)
                    || toolManager.updateEraserSize(held, size)
                    || toolManager.updateTerrainBrushSize(held, size, height)
                    || toolManager.updateGenBrushSize(held, size);
        }
        if (!updatedAny) {
            ChatOutput.send(player, ChatColor.RED + "Held item has no brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ShapeBrushSettings updated = toolManager.readShapeBrushSettings(held);
        TerrainBrushSettings terrain = toolManager.readTerrainBrushSettings(held);
        ChatOutput.send(player, ChatColor.WHITE + "Size: " + size
                + (updated != null && (updated.type() == ShapeBrushType.CYL || updated.type() == ShapeBrushType.HCYL)
                        ? " | height: " + updated.height() : "")
                + (terrain != null && height != null && height > 0 ? " | " + terrain.type().powerLabel().toLowerCase(Locale.ROOT) + ": " + terrain.power() : ""));
        return true;
    }

    private boolean handleBrushDensityCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 0) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /density <0.0-1.0>");
            return true;
        }
        double density;
        try {
            density = Double.parseDouble(args[0]);
        } catch (NumberFormatException ex) {
            ChatOutput.send(player, ChatColor.RED + "Density must be a number between 0.0 and 1.0.");
            return true;
        }
        if (!Double.isFinite(density) || density < 0.0 || density > 1.0) {
            ChatOutput.send(player, ChatColor.RED + "Density must be between 0.0 and 1.0.");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ChatOutput.send(player, ChatColor.RED + "Hold a paint or pattern brush to set its density.");
            return true;
        }
        if (!toolManager.updatePaintBrushDensity(held, density)
                && !toolManager.updatePatternBrushDensity(held, density)) {
            ChatOutput.send(player, ChatColor.RED + "Held item has no paint or pattern brush bound.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        ChatOutput.send(player, ChatColor.WHITE + "Density: " + String.format(Locale.ROOT, "%.2f", density));
        return true;
    }

    private boolean handleBrushInfo(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            ChatOutput.send(player, ChatColor.RED + "Hold an item to inspect its brush binding.");
            return true;
        }
        ShapeBrushSettings shape = toolManager.readShapeBrushSettings(held);
        if (shape != null) {
            ChatOutput.send(player, ChatColor.GOLD + "Brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Type: " + ChatColor.WHITE + shape.type().displayName()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + describeBrushShape(shape));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Blocks: " + ChatColor.WHITE + shape.distribution()
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (shape.mask() == null ? "any" : shape.mask().summary())
                    + ChatColor.DARK_GRAY + " | anchor: " + ChatColor.WHITE + shape.anchorMode().name().toLowerCase(Locale.ROOT));
            return true;
        }
        if (toolManager.isClipboardBrush(held)) {
            ChatOutput.send(player, ChatColor.GOLD + "Clipboard brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Right-click a block to paste your clipboard there.");
            return true;
        }
        StructureBrushSettings structure = toolManager.readStructureBrushSettings(held);
        if (structure != null) {
            ChatOutput.send(player, ChatColor.GOLD + "Structure brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Structure: " + ChatColor.WHITE + structure.structureId()
                    + ChatColor.DARK_GRAY + " | anchor: " + ChatColor.WHITE + structure.anchorMode().name().toLowerCase(Locale.ROOT)
                    + ChatColor.DARK_GRAY + " | confirm: " + ChatColor.WHITE + (structure.confirm() ? "yes" : "no"));
            return true;
        }
        PaintBrushSettings paint = toolManager.readPaintBrushSettings(held);
        if (paint != null) {
            ChatOutput.send(player, ChatColor.GOLD + "Paint brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Block: " + ChatColor.WHITE + paint.material().name().toLowerCase(Locale.ROOT)
                    + ChatColor.DARK_GRAY + " | size: " + ChatColor.WHITE + paint.size()
                    + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", paint.density())
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (paint.mask() == null ? "any" : paint.mask().summary()));
            return true;
        }
        PatternBrushSettings pattern = toolManager.readPatternBrushSettings(held);
        if (pattern != null) {
            ChatOutput.send(player, ChatColor.GOLD + pattern.mode().displayName() + " brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Size: " + ChatColor.WHITE + pattern.size()
                    + ChatColor.DARK_GRAY + " | density: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", pattern.density())
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (pattern.mask() == null ? "any" : pattern.mask().summary()));
            return true;
        }
        ToolType toolType = toolManager.getToolType(held);
        if (toolType == ToolType.ERASER) {
            EraserSettings eraser = toolManager.readEraserSettings(held);
            ChatOutput.send(player, ChatColor.GOLD + "Eraser brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + eraser.getRadius()
                    + ChatColor.DARK_GRAY + " | mask: " + ChatColor.WHITE + (eraser.getMask() == null ? "any" : eraser.getMask().summary())
                    + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (eraser.isEditBedrock() ? "on" : "protected"));
            return true;
        }
        TerrainBrushSettings terrain = toolManager.readTerrainBrushSettings(held);
        if (terrain != null) {
            ChatOutput.send(player, ChatColor.GOLD + terrain.type().displayName() + " brush bound to " + held.getType().name().toLowerCase(Locale.ROOT));
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Radius: " + ChatColor.WHITE + terrain.radius()
                    + ChatColor.DARK_GRAY + " | " + terrain.type().powerLabel().toLowerCase(Locale.ROOT) + ": " + ChatColor.WHITE + terrain.power()
                    + ChatColor.DARK_GRAY + " | bedrock: " + ChatColor.WHITE + (terrain.editBedrock() ? "on" : "protected"));
            return true;
        }
        ChatOutput.send(player, ChatColor.GRAY + "No brush bound to held " + held.getType().name().toLowerCase(Locale.ROOT) + ".");
        return true;
    }

    private boolean handleGenerate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        GenerateShapeRequest request;
        try {
            request = GeneratorCommandParser.parseGenerate(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        WorkEstimate estimate = estimateSelectionWork(selection);
        if (rejectGenerationEstimate(sender, estimate, shouldBypassConfirm(player, request.confirm()),
                "Large generate selection. Re-run with confirm:true")) {
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generateShape(player, request), "generated shape");
        return true;
    }

    private boolean handleGenerateBiome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        GenerateBiomeRequest request;
        try {
            request = GeneratorCommandParser.parseGenerateBiome(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        boolean confirmed = shouldBypassConfirm(player, request.confirm());
        WorkEstimate selectionEstimate = estimateSelectionWork(selection);
        if (rejectGenerationEstimate(sender, selectionEstimate, confirmed,
                "Large biome generate selection. Re-run with confirm:true")) {
            return true;
        }
        if (selection != null && selection.isComplete()) {
            WorkEstimate chunkEstimate = OperationLimits.estimateBiomeChunks(
                    selection.getMinX(), selection.getMaxX(), selection.getMinZ(), selection.getMaxZ());
            if (rejectGenerationEstimate(sender, chunkEstimate, true,
                    "Large biome chunk footprint.")) {
                return true;
            }
        }
        GeneratorResult result = proceduralGenerationService.generateBiome(player, request);
        sendGeneratorResult(sender, result, "generated biome shape");
        return true;
    }

    private boolean handleGenFeature(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        FeatureGenRequest request;
        try {
            request = GeneratorCommandParser.parseFeatureGen(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        VanillaContentRegistry.Entry meta = VanillaContentRegistry.featureMeta(request.featureId());
        WorkEstimate estimate = OperationLimits.estimateStructure(meta.footprintRadius(), meta.footprintHeight());
        if (rejectGenerationEstimate(sender, estimate, shouldBypassConfirm(player, request.confirm()),
                "Large feature footprint (" + meta.description() + "). Re-run with confirm:true")) {
            return true;
        }
        WorkEstimate chunks = OperationLimits.estimateStructureChunks(meta.footprintRadius());
        if (rejectGenerationEstimate(sender, chunks, true, "Large feature chunk footprint.")) {
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generateFeature(player, request), "generated feature");
        return true;
    }

    private boolean handleStructureGen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!bayzylAccess.allowed(player, CommandCapability.GENSTRUCTURE)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to place structures.");
            return true;
        }
        StructureGenRequest request;
        try {
            request = GeneratorCommandParser.parseStructureGen(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        VanillaContentRegistry.Entry meta = VanillaContentRegistry.structureMeta(request.structureId());
        WorkEstimate estimate = OperationLimits.estimateStructure(meta.footprintRadius(), meta.footprintHeight());
        if (rejectGenerationEstimate(sender, estimate, shouldBypassConfirm(player, request.confirm()),
                "Large structure footprint (" + meta.description() + "). Re-run with confirm:true")) {
            return true;
        }
        WorkEstimate chunks = OperationLimits.estimateStructureChunks(meta.footprintRadius());
        if (rejectGenerationEstimate(sender, chunks, true, "Large structure chunk footprint.")) {
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generateStructure(player, request), "generated structure");
        return true;
    }

    private boolean handleRegen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (!bayzylAccess.allowed(player, CommandCapability.GENSTRUCTURE)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to reroll structures.");
            return true;
        }
        if (args.length > 0) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /regen");
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.regenerateStructure(player), "regenerated structure");
        return true;
    }


    private boolean handleForestGen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        ForestGenRequest request;
        try {
            request = GeneratorCommandParser.parseForest(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        WorkEstimate estimate = OperationLimits.estimateForest(request.size(), request.density());
        if (rejectGenerationEstimate(sender, estimate, shouldBypassConfirm(player, request.confirm()),
                "Large forest generation. Re-run with confirm:true")) {
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generateForest(player, request), "forest");
        return true;
    }

    private boolean handlePumpkins(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        PumpkinPatchRequest request;
        try {
            request = GeneratorCommandParser.parsePumpkins(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        WorkEstimate estimate = OperationLimits.estimatePumpkins(request.size());
        if (rejectGenerationEstimate(sender, estimate, shouldBypassConfirm(player, request.confirm()),
                "Large pumpkin generation. Re-run with confirm:true")) {
            return true;
        }
        sendGeneratorResult(sender, proceduralGenerationService.generatePumpkins(player, request), "pumpkin patch");
        return true;
    }

    private boolean handleWalls(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 1) {
            syntaxError(sender, "/walls <BLOCK|DISTRIBUTION> [mask:<BLOCKS>] [confirm:true]");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        BlockDistribution distribution;
        try {
            distribution = BlockDistribution.parse(args[0]);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + "Invalid block distribution: " + ex.getMessage());
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
            ChatOutput.send(sender, ChatColor.RED + "Large walls operation. Re-run with confirm:true");
            return true;
        }
        int changed = editService.makeWalls(player, selection, distribution, BlockMask.parse(maskValue));
        recentEditTrailService.record(player.getUniqueId(), "walls " + distribution);
        ChatOutput.send(sender, ChatColor.WHITE + "Created walls and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleOverlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 1) {
            syntaxError(sender, "/overlay <BLOCK> [mask:<BLOCKS>] [confirm:true]");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        Material material = EditUtil.parseBlock(args[0]);
        if (material == null) {
            ChatOutput.send(sender, ChatColor.RED + "Unknown block: " + args[0]);
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
            ChatOutput.send(sender, ChatColor.RED + "Large overlay operation. Re-run with confirm:true");
            return true;
        }
        int changed = editService.overlaySelection(player, selection, material, BlockMask.parse(maskValue));
        recentEditTrailService.record(player.getUniqueId(), "overlay " + material.name().toLowerCase(Locale.ROOT));
        ChatOutput.send(sender, ChatColor.WHITE + "Created overlay and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleSmooth(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
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
                    ChatOutput.send(sender, ChatColor.RED + "Iterations must be a number.");
                    return true;
                }
                continue;
            }
            try {
                iterations = Integer.parseInt(arg);
            } catch (NumberFormatException ex) {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /smooth [iterations|iterations:<n>] [confirm:true]");
                return true;
            }
        }
        if (iterations <= 0) {
            ChatOutput.send(sender, ChatColor.RED + "Iterations must be greater than 0.");
            return true;
        }
        if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            ChatOutput.send(sender, ChatColor.RED + "Large smooth operation. Re-run with confirm:true");
            return true;
        }
        int changed = editService.smoothSelection(player, selection, iterations);
        recentEditTrailService.record(player.getUniqueId(), "smooth " + iterations);
        ChatOutput.send(sender, ChatColor.WHITE + "Smoothed selection with " + iterations + " iteration(s) and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleAgitate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return true;
        }
        int count = 0;
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == Material.WATER || type == Material.LAVA) {
                        block.setType(type, true);
                        count++;
                    } else if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) {
                        block.setBlockData(block.getBlockData().clone(), true);
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            ChatOutput.send(player, ChatColor.YELLOW + "No stuck fluids found in selection.");
        } else {
            ChatOutput.send(player, ChatColor.WHITE + "Agitated " + count + " fluid block(s).");
        }
        return true;
    }

    private boolean handleNaturalize(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
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
                    ChatOutput.send(sender, ChatColor.RED + "Depth must be a number.");
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
                ChatOutput.send(sender, ChatColor.RED + "Usage: /naturalize [depth:<n>] [bedrock:on|off] [confirm:true]");
                return true;
            }
        }

        if (depth <= 0) {
            ChatOutput.send(sender, ChatColor.RED + "Depth must be greater than 0.");
            return true;
        }
        if (EditUtil.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            ChatOutput.send(sender, ChatColor.RED + "Large naturalize operation. Re-run with confirm:true");
            return true;
        }

        List<BlockChange> changes = naturalizeService.naturalizeSelection(player, selection, depth, editBedrock);
        int changed = changes.size();
        historyService.record(player.getUniqueId(), changes);
        recentEditTrailService.record(player.getUniqueId(), "naturalize depth " + depth);
        ChatOutput.send(sender, ChatColor.WHITE + "Naturalized selection and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleRotate(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        try {
            ClipboardRotateRequest request = ClipboardCommandParser.parseRotate(args);
            if (request.live()) {
                Selection selection = selectionManager.get(player.getUniqueId());
                if (selection == null || !selection.isComplete()) {
                    ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
                    return true;
                }
                long totalVolume = selection.getVolume() * 2L;
                if (totalVolume > EditUtil.CONFIRM_VOLUME) {
                    ChatOutput.send(sender, ChatColor.RED + "Large live rotate. Re-run after shrinking the selection.");
                    return true;
                }
                int changed = editService.rotateSelectionLive(player, selection, request.rotation());
                recentEditTrailService.record(player.getUniqueId(), "rotate live " + request.rotation());
                ChatOutput.send(sender, ChatColor.WHITE + "Selection rotated " + request.rotation() + " degrees live and changed " + changed + " blocks.");
                return true;
            }
            Clipboard clipboard = clipboardManager.get(player.getUniqueId());
            if (clipboard == null) {
                ChatOutput.send(sender, ChatColor.RED + "Clipboard is empty.");
                return true;
            }
            Clipboard rotated = ClipboardTransforms.rotateY(clipboard, request.rotation());
            clipboardManager.set(player.getUniqueId(), rotated);
            recentEditTrailService.record(player.getUniqueId(), "rotate " + request.rotation());
            ChatOutput.send(sender, ChatColor.WHITE + "Clipboard rotated " + request.rotation() + " degrees.");
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }
        return true;
    }

    private boolean handleFlip(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            ChatOutput.send(sender, ChatColor.RED + "Clipboard is empty.");
            return true;
        }

        try {
            ClipboardFlipRequest request = ClipboardCommandParser.parseFlip(player, args);
            Clipboard flipped = ClipboardTransforms.flip(clipboard, request.axis());
            clipboardManager.set(player.getUniqueId(), flipped);
            recentEditTrailService.record(player.getUniqueId(), "flip " + request.axis());
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        ChatOutput.send(sender, ChatColor.WHITE + "Clipboard flipped.");
        return true;
    }

    private boolean handleStack(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        ClipboardStackRequest request;
        try {
            request = ClipboardCommandParser.parseStack(args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        SelectionSnapshot beforeSelection = SelectionSnapshot.from(selection);

        long totalVolume = selection.getVolume() * Math.max(1, request.count());
        if (totalVolume > EditUtil.CONFIRM_VOLUME && !shouldBypassConfirm(player, request.confirm())) {
            ChatOutput.send(sender, ChatColor.RED + "Large stack. Re-run with confirm:true");
            return true;
        }

        int changed;
        if (request.random()) {
            changed = editService.stackSelectionRandom(player, selection, request.count(), request.spreadX(), request.spreadY(), request.spreadZ(), request.ignoreAir(),
                    beforeSelection, beforeSelection);
            recentEditTrailService.record(player.getUniqueId(), "stack rnd " + request.count());
            ChatOutput.send(sender, ChatColor.WHITE + "Random-stacked " + request.count() + " copy/copies and changed " + changed + " blocks.");
            return true;
        }

        boolean defaultDirection = request.direction() == null;
        int[] direction = defaultDirection
                ? DirectionUtil.resolveStackDefault(player, stackLookDirectionService.isEnabled(player))
                : DirectionUtil.resolve(player, request.direction());
        Selection stackedSelection = expandSelectionForStack(selection, direction, request.count());
        SelectionSnapshot afterSelection = stackAutoMoveService.isEnabled(player.getUniqueId())
                ? SelectionSnapshot.from(stackedSelection)
                : beforeSelection;
        changed = editService.stackSelection(player, selection, request.count(), direction, request.ignoreAir(), beforeSelection, afterSelection);

        if (stackAutoMoveService.isEnabled(player.getUniqueId())) {
            selectionManager.setCuboid(player.getUniqueId(), stackedSelection.getPos1(), stackedSelection.getPos2());
        }

        recentEditTrailService.record(player.getUniqueId(), "stack " + request.count() + " " + (defaultDirection ? "default" : request.direction()));
        ChatOutput.send(sender, ChatColor.WHITE + "Stacked " + request.count() + " copy/copies "
                + ChatColor.GRAY + "(" + (defaultDirection ? (stackLookDirectionService.isEnabled(player) ? "look-based" : "forward") : request.direction()) + ")"
                + ChatColor.WHITE + " and changed " + changed + " blocks.");
        return true;
    }

    private boolean handleUndo(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        int steps = parseSteps(args);
        int undone = historyService.undo(player, steps);
        recentEditTrailService.record(player.getUniqueId(), "undo " + undone);
        ChatOutput.send(sender, ChatColor.WHITE + "Undid " + undone + " action(s).");
        return true;
    }

    private boolean handleOops(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        int undone = historyService.undo(player, parseSteps(args));
        recentEditTrailService.record(player.getUniqueId(), "undo " + undone);
        ChatOutput.send(sender, ChatColor.WHITE + "Undid " + undone + " action(s).");
        if (undone > 0) {
            Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " made an oopsie!!!");
        }
        return true;
    }

    private boolean handleRedo(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        int steps = parseSteps(args);
        int redone = historyService.redo(player, steps);
        recentEditTrailService.record(player.getUniqueId(), "redo " + redone);
        ChatOutput.send(sender, ChatColor.WHITE + "Redid " + redone + " action(s).");
        return true;
    }

    private boolean handleClearHistory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (historyService.hasAsyncHistoryTask(player.getUniqueId())) {
            ChatOutput.send(sender, ChatColor.RED + "Already performing an undo/redo. Wait for it to finish before clearing history.");
            return true;
        }
        boolean hadHistory = historyService.clearPlayerHistory(player.getUniqueId());
        recentEditTrailService.record(player.getUniqueId(), "clear history");
        if (hadHistory) {
            ChatOutput.send(sender, ChatColor.WHITE + "Cleared your Bayzyl undo/redo history.");
        } else {
            ChatOutput.send(sender, ChatColor.YELLOW + "Your Bayzyl undo/redo history is already empty.");
        }
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        String verb = expand ? "expand" : "contract";
        SelectionResizeRequest request;
        try {
            request = SelectionCommandParser.parseResize(verb, args);
        } catch (IllegalArgumentException ex) {
            ChatOutput.send(sender, ChatColor.RED + ex.getMessage());
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
            ChatOutput.send(sender, ChatColor.RED + "Selection could not be updated.");
            return true;
        }

        selectionService.applySelection(player.getUniqueId(), updated);
        String directionLabel = request.allDirections() ? "in all directions" : request.direction();
        ChatOutput.send(sender, ChatColor.WHITE + (expand ? "Expanded" : "Contracted") + " selection " + request.amount() + " block(s) " + directionLabel + ".");
        return true;
    }

    private boolean handleBiomeInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }

        World world = selection.getPos1().getWorld();
        if (world == null) {
            ChatOutput.send(sender, ChatColor.RED + "Selection world is unavailable.");
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
            sendMenuLines(sender, BIOME_INFO_MENU, "header", List.of(
                    "&a&lBZL",
                    "&aBiome Info &8| &f{min} &8-> &f{max}"
            ), ListMenuConfigService.tokens(
                    "min", selection.getMinX() + "," + selection.getMinY() + "," + selection.getMinZ(),
                    "max", selection.getMaxX() + "," + selection.getMaxY() + "," + selection.getMaxZ()
            ));
            ChatOutput.send(sender, listMenuConfigService.format(
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
                "&a&lBZL",
                "&aBiome Info &8| &f{min} &8-> &f{max}"
        ), baseTokens);
        sendMenuLine(sender, BIOME_INFO_MENU, "summary",
                "&fUnique biomes &8| &6{unique}&8 | &fTotal blocks &8| &6{total}",
                baseTokens);
        Biome centerBiome = world.getBiome(
                (selection.getMinX() + selection.getMaxX()) / 2,
                (selection.getMinY() + selection.getMaxY()) / 2,
                (selection.getMinZ() + selection.getMaxZ()) / 2
        );
        if (centerBiome != null) {
            sendMenuLine(sender, BIOME_INFO_MENU, "center",
                    "&fCenter &8| &b{center}",
                    ListMenuConfigService.tokens("center", centerBiome.name().toLowerCase(Locale.ROOT)));
        }
        Biome centerColumnBiome = world.getBiome(
                (selection.getMinX() + selection.getMaxX()) / 2,
                (selection.getMinZ() + selection.getMaxZ()) / 2
        );
        if (centerColumnBiome != null) {
            sendMenuLine(sender, BIOME_INFO_MENU, "column-center",
                    "&fColumn center &8| &b{column_center}",
                    ListMenuConfigService.tokens("column_center", centerColumnBiome.name().toLowerCase(Locale.ROOT)));
        }
        sendMenuLine(sender, BIOME_INFO_MENU, "divider", "&8--------------", ListMenuConfigService.tokens());
        counts.entrySet().stream()
                .sorted(Map.Entry.<Biome, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().name()))
                .forEach(entry -> {
                    double percent = total <= 0 ? 0.0D : (entry.getValue() * 100.0D) / total;
                    sendMenuLine(sender, BIOME_INFO_MENU, "row",
                            "{color}{biome} &8| &6{count}&8 | &f{percent}",
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

    private Selection expandSelectionForStack(Selection selection, int[] direction, int count) {
        if (selection == null || !selection.isComplete()) {
            return selection;
        }
        int offsetX = direction[0] * selection.getSizeX() * Math.max(0, count);
        int offsetY = direction[1] * selection.getSizeY() * Math.max(0, count);
        int offsetZ = direction[2] * selection.getSizeZ() * Math.max(0, count);

        int minX = selection.getMinX();
        int minY = selection.getMinY();
        int minZ = selection.getMinZ();
        int maxX = selection.getMaxX();
        int maxY = selection.getMaxY();
        int maxZ = selection.getMaxZ();

        if (offsetX > 0) {
            maxX += offsetX;
        } else if (offsetX < 0) {
            minX += offsetX;
        }
        if (offsetY > 0) {
            maxY += offsetY;
        } else if (offsetY < 0) {
            minY += offsetY;
        }
        if (offsetZ > 0) {
            maxZ += offsetZ;
        } else if (offsetZ < 0) {
            minZ += offsetZ;
        }

        return new Selection(
                new Location(selection.getPos1().getWorld(), minX, minY, minZ),
                new Location(selection.getPos1().getWorld(), maxX, maxY, maxZ),
                SelectionType.CUBOID
        );
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /select cube <size> at:<player|target>");
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl select viz <on|off> [intensity:low|medium|high] [grid:on|off|auto] [consistency:1-10] [color:red|green|yellow|blue|purple|bayzyl|#RRGGBB]");
            return true;
        }

        if (args[1].equalsIgnoreCase("cube")) {
            return handleSelectCube(player, args);
        }

        if (!args[1].equalsIgnoreCase("viz")) {
            ChatOutput.send(sender, ChatColor.RED + "Unknown /bzl select subcommand.");
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
                        ChatOutput.send(sender, ChatColor.RED + "Consistency must be 1-10.");
                        return true;
                    }
                    break;
                case "color":
                    org.bukkit.Color color = parseColor(value);
                    if (color == null) {
                        ChatOutput.send(sender, ChatColor.RED + "Unknown color. Use red|green|yellow|blue|purple|bayzyl|#RRGGBB.");
                        return true;
                    }
                    settings.setColor(color);
                    break;
                default:
                    ChatOutput.send(sender, ChatColor.RED + "Unknown option: " + key);
                    return true;
            }
        }
        savePlayerRuntime(player);
        ChatOutput.send(sender, ChatColor.WHITE + "Selection visualization updated.");
        return true;
    }

    private boolean handleSelectCube(Player player, String[] args) {
        if (args.length < 3) {
            ChatOutput.send(player, ChatColor.RED + "Usage: /select cube <size> at:<player|target>");
            return true;
        }

        int size;
        try {
            size = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            ChatOutput.send(player, ChatColor.RED + "Cube size must be a number.");
            return true;
        }
        if (size <= 0) {
            ChatOutput.send(player, ChatColor.RED + "Cube size must be greater than 0.");
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
                ChatOutput.send(player, ChatColor.RED + "No target block in range.");
                return true;
            }
            center = result.getHitBlock().getLocation();
        } else if (anchor.equals("player")) {
            center = player.getLocation().getBlock().getLocation();
        } else {
            ChatOutput.send(player, ChatColor.RED + "Unknown anchor. Use at:player or at:target.");
            return true;
        }

        int lower = (size - 1) / 2;
        int upper = size / 2;
        Location pos1 = center.clone().add(-lower, -lower, -lower);
        Location pos2 = center.clone().add(upper, upper, upper);
        selectionManager.setCuboid(player.getUniqueId(), pos1, pos2);

        if (size % 2 == 0) {
            ChatOutput.send(player, ChatColor.WHITE + "Selected " + size + "x" + size + "x" + size + " cube at " + anchor + " anchor."
                    + ChatColor.DARK_GRAY + " Even sizes are center-biased by one block.");
            return true;
        }

        ChatOutput.send(player, ChatColor.WHITE + "Selected " + size + "x" + size + "x" + size + " cube at " + anchor + " anchor.");
        return true;
    }

    private boolean handleSelectionParticlesNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl selectionparticles color <bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB>");
            return true;
        }

        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on":
                settings.setEnabled(true);
                ChatOutput.send(sender, ChatColor.WHITE + "Selection particles enabled.");
                savePlayerRuntime(player);
                return true;
            case "off":
                settings.setEnabled(false);
                ChatOutput.send(sender, ChatColor.WHITE + "Selection particles disabled.");
                savePlayerRuntime(player);
                return true;
            case "color":
                if (args.length < 3) {
                    ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl selectionparticles color <bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB>");
                    return true;
                }
                org.bukkit.Color color = parseColor(args[2].toLowerCase(Locale.ROOT));
                if (color == null) {
                    ChatOutput.send(sender, ChatColor.RED + "Unknown color. Use bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB.");
                    return true;
                }
                settings.setEnabled(true);
                settings.setColor(color);
                ChatOutput.send(sender, ChatColor.WHITE + "Selection particle color updated.");
                savePlayerRuntime(player);
                return true;
            default:
                ChatOutput.send(sender, ChatColor.RED + "Unknown selection particle setting.");
                return true;
        }
    }

    private boolean handleToolNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl tool <smooth|raise|lower|flatten> <radius> [power] [bedrock:on|off]");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        TerrainBrushType brushType = TerrainBrushType.parse(sub);
        if (brushType == null || !brushType.isTerrainMode()) {
            ChatOutput.send(sender, ChatColor.YELLOW + "Not implemented yet: /bzl tool " + sub);
            return true;
        }
        if (args.length < 3) {
            ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl tool " + brushType.commandName() + " <radius> [power] [bedrock:on|off]");
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
            ChatOutput.send(sender, ChatColor.RED + "Radius and power must be numbers.");
            return true;
        }
        for (int i = 3; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (!token.contains(":")) {
                if (i == 3 && !args[3].contains(":")) {
                    continue;
                }
                ChatOutput.send(sender, ChatColor.RED + "Unknown terrain option: " + args[i]);
                return true;
            }
            String[] parts = token.split(":", 2);
            if (!parts[0].equals("bedrock")) {
                ChatOutput.send(sender, ChatColor.RED + "Unknown terrain option: " + parts[0]);
                return true;
            }
            String value = parts.length > 1 ? parts[1] : "";
            Boolean parsedFlag = parseEraserFlag("bedrock", value);
            if (parsedFlag == null) {
                ChatOutput.send(sender, ChatColor.RED + "Invalid bedrock option: " + value);
                return true;
            }
            editBedrock = parsedFlag;
        }
        if (radius <= 0 || power <= 0) {
            ChatOutput.send(sender, ChatColor.RED + "Radius and power must be greater than 0.");
            return true;
        }
        ItemStack brush = toolManager.createTerrainBrush(brushType, radius, power, editBedrock);
        if (brush == null) {
            WorkEstimate estimate = BrushSafety.assessTerrain(brushType, radius, power);
            ChatOutput.send(sender, ChatColor.RED + "Terrain brush refused: " + estimate.reason());
            return true;
        }
        player.getInventory().addItem(brush);
        ChatOutput.send(sender, ChatColor.WHITE + brushType.displayName() + " brush added: radius " + radius
                + ", " + brushType.powerLabel().toLowerCase() + " " + power
                + ", bedrock " + (editBedrock ? "on" : "protected") + ".");
        return true;
    }

    private boolean handleCleanupNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|brush> ...");
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("brush")) {
            if (args.length < 4) {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl cleanup brush <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> <radius> [power]");
                return true;
            }
            TerrainBrushType type = TerrainBrushType.parse(args[2]);
            if (type == null || !type.isCleanupMode()) {
                ChatOutput.send(sender, ChatColor.RED + "Unknown cleanup brush type: " + args[2]);
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
                ChatOutput.send(sender, ChatColor.RED + "Radius and power must be numbers.");
                return true;
            }
            if (radius <= 0 || power <= 0) {
                ChatOutput.send(sender, ChatColor.RED + "Radius and power must be greater than 0.");
                return true;
            }
            ItemStack brush = toolManager.createTerrainBrush(type, radius, power, false);
            if (brush == null) {
                WorkEstimate estimate = BrushSafety.assessTerrain(type, radius, power);
                ChatOutput.send(sender, ChatColor.RED + "Cleanup brush refused: " + estimate.reason());
                return true;
            }
            player.getInventory().addItem(brush);
            ChatOutput.send(sender, ChatColor.GOLD + type.displayName() + ChatColor.WHITE + " created " + ChatColor.DARK_GRAY + "[r:" + ChatColor.AQUA + radius + ChatColor.DARK_GRAY + " " + type.powerLabel().toLowerCase() + ":" + ChatColor.AQUA + power + ChatColor.DARK_GRAY + "]");
            return true;
        }

        TerrainBrushType type = TerrainBrushType.parse(sub);
        if (type == null || !type.isCleanupMode()) {
            ChatOutput.send(sender, ChatColor.RED + "Unknown cleanup type: " + sub);
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
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
            return true;
        }
        if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
            ChatOutput.send(sender, ChatColor.RED + "Large cleanup selection. Re-run with confirm:true");
            return true;
        }

        List<BlockChange> changes = cleanupService.cleanupSelection(player, selection, type);
        historyService.record(player.getUniqueId(), changes);
        sendHeader(sender);
        ChatOutput.send(sender, ChatColor.GOLD + type.displayName() + ChatColor.DARK_GRAY + " cleanup completed");
        ChatOutput.send(sender, ChatColor.WHITE + "Cleaned " + ChatColor.GOLD + changes.size() + ChatColor.WHITE + " blocks.");
        if (changes.isEmpty()) {
            ChatOutput.send(sender, ChatColor.DARK_GRAY + "No matching targets found in the current selection.");
        } else {
            ChatOutput.send(sender, ChatColor.DARK_GRAY + "Tip: use /bzl cleanup brush " + type.commandName() + " <radius> for localized passes.");
        }
        return true;
    }

    private boolean handlePaletteNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length == 1) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl palette <analyze|swap> ...");
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(sender, ChatColor.RED + "Selection is incomplete.");
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
                ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl palette swap <from> <to> [confirm:true]");
                return true;
            }
            Material from = EditUtil.parseBlock(args[2]);
            Material to = EditUtil.parseBlock(args[3]);
            if (from == null || to == null) {
                ChatOutput.send(sender, ChatColor.RED + "Palette swap needs valid block names.");
                return true;
            }
            boolean confirm = false;
            for (int i = 4; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("confirm:true")) {
                    confirm = true;
                }
            }
            if (editService.requiresConfirm(selection, shouldBypassConfirm(player, confirm))) {
                ChatOutput.send(sender, ChatColor.RED + "Large palette swap. Re-run with confirm:true");
                return true;
            }
            List<BlockChange> changes = paletteService.swapSelection(player, selection, from, to);
            historyService.record(player.getUniqueId(), changes);
            sendHeader(sender);
            ChatOutput.send(sender, ChatColor.WHITE + "Palette swap "
                    + ChatColor.WHITE + from.name().toLowerCase(Locale.ROOT)
                    + ChatColor.DARK_GRAY + " -> "
                    + ChatColor.WHITE + to.name().toLowerCase(Locale.ROOT)
                    + ChatColor.WHITE + " changed "
                    + ChatColor.GOLD + changes.size()
                    + ChatColor.WHITE + " blocks.");
            return true;
        }

        ChatOutput.send(sender, ChatColor.RED + "Unknown palette command: " + sub);
        return true;
    }

    private boolean handleDebugNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        if (args.length < 2) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl debug entities <on|off|status>");
            return true;
        }
        String topic = args[1].toLowerCase(Locale.ROOT);
        if (!topic.equals("entities")) {
            ChatOutput.send(sender, ChatColor.RED + "Unknown debug topic: " + topic);
            return true;
        }
        String mode = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (mode) {
            case "on" -> {
                EditUtil.setEntityDebug(player.getUniqueId(), true);
                ChatOutput.send(sender, ChatColor.GREEN + "Entity debug logging " + ChatColor.WHITE + "enabled" + ChatColor.GREEN + ".");
            }
            case "off" -> {
                EditUtil.setEntityDebug(player.getUniqueId(), false);
                ChatOutput.send(sender, ChatColor.GREEN + "Entity debug logging " + ChatColor.WHITE + "disabled" + ChatColor.GREEN + ".");
            }
            case "status" -> {
                boolean enabled = EditUtil.isEntityDebug(player.getUniqueId());
                ChatOutput.send(sender, ChatColor.GREEN + "Entity debug logging is " + ChatColor.WHITE + (enabled ? "ON" : "OFF") + ChatColor.GREEN + ".");
            }
            default -> ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl debug entities <on|off|status>");
        }
        return true;
    }

    private boolean handleEnvNamespace(CommandSender sender, String[] args) {
        if (args.length == 1) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /bzl env <history|accent|memreset> ...");
            return true;
        }
        if (args[1].equalsIgnoreCase("memreset")) {
            return handleEnvMemReset(sender, args);
        }
        if (args[1].equalsIgnoreCase("history")) {
            if (args.length == 2) {
                ChatOutput.send(sender, ChatColor.WHITE + "Bayzyl undo history is set to " + historyService.getMaxHistory() + " actions.");
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                ChatOutput.send(sender, ChatColor.RED + "History count must be a number.");
                return true;
            }
            if (count < 1) {
                ChatOutput.send(sender, ChatColor.RED + "History count must be at least 1.");
                return true;
            }
            historyService.setMaxHistory(count);
            ChatOutput.send(sender, ChatColor.WHITE + "Bayzyl undo history set to " + historyService.getMaxHistory() + " actions.");
            if (count > 50) {
                ChatOutput.send(sender, ChatColor.YELLOW + "Warning: history above 50 may increase memory use or cause issues on large edits.");
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("accent")) {
            if (args.length == 2 || args[2].equalsIgnoreCase("status")) {
                ChatOutput.send(sender, ChatColor.WHITE + "Menu accent is " + messageThemeService.accentValue()
                        + ChatColor.WHITE + ". " + messageThemeService.applyAccent("&aSample accent text"));
                return true;
            }
            String value = args[2].toLowerCase(Locale.ROOT);
            if (value.equals("reset") || value.equals("default")) {
                messageThemeService.resetAccent();
                runtimePreferencesService.saveGlobal(ramAlertService, messageThemeService);
                ChatOutput.send(sender, ChatColor.WHITE + "Menu accent reset to " + messageThemeService.accentValue() + ".");
                return true;
            }
            if (!messageThemeService.setAccent(value)) {
                ChatOutput.send(sender, ChatColor.RED + "Unknown accent color. Use a chat color name or #RRGGBB.");
                return true;
            }
            runtimePreferencesService.saveGlobal(ramAlertService, messageThemeService);
            ChatOutput.send(sender, ChatColor.WHITE + "Menu accent set to " + messageThemeService.accentValue()
                    + ChatColor.WHITE + ". " + messageThemeService.applyAccent("&aSample accent text"));
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
        ChatOutput.send(sender, ChatColor.YELLOW + "Not implemented yet: /bzl env " + args[1]);
        return true;
    }

    private boolean handleMemResetRoot(CommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("global"))) {
            ChatOutput.send(sender, ChatColor.WHITE + "Usage: /memreset [global]");
            return true;
        }
        String[] remapped;
        if (args.length == 1) {
            remapped = new String[]{"env", "memreset", "global"};
        } else {
            remapped = new String[]{"env", "memreset"};
        }
        return handleEnvMemReset(sender, remapped);
    }

    private boolean handleAccentRoot(CommandSender sender, String[] args) {
        String[] remapped = new String[args.length + 2];
        remapped[0] = "env";
        remapped[1] = "accent";
        if (args.length > 0) {
            System.arraycopy(args, 0, remapped, 2, args.length);
        }
        return handleEnvNamespace(sender, remapped);
    }

    private boolean handleAuthorityRoot(CommandSender sender, String[] args) {
        String[] remapped = new String[args.length + 1];
        remapped[0] = "authority";
        if (args.length > 0) {
            System.arraycopy(args, 0, remapped, 1, args.length);
        }
        return handleCommandAuthorityNamespace(sender, remapped);
    }

    private boolean handleToolRoot(CommandSender sender, String[] args) {
        String[] remapped = new String[args.length + 1];
        remapped[0] = "tool";
        if (args.length > 0) {
            System.arraycopy(args, 0, remapped, 1, args.length);
        }
        return handleToolNamespace(sender, remapped);
    }

    private boolean handleCleanupTypeRoot(CommandSender sender, String cleanupType, String[] args) {
        String[] remapped = new String[args.length + 2];
        remapped[0] = "cleanup";
        remapped[1] = cleanupType;
        if (args.length > 0) {
            System.arraycopy(args, 0, remapped, 2, args.length);
        }
        return handleCleanupNamespace(sender, remapped);
    }

    private boolean handleEnvMemReset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        boolean global = args.length >= 3 && args[2].equalsIgnoreCase("global");
        if (global && !adminModeService.isActive(player, bayzylAccess)) {
            ChatOutput.send(sender, ChatColor.RED + "Global memreset requires admin mode (/bzltoggle admin).");
            return true;
        }
        if (historyService.hasAsyncHistoryTask(player.getUniqueId()) || editService.hasPasteTask(player.getUniqueId())) {
            ChatOutput.send(sender, ChatColor.RED + "Wait for your current paste/undo/redo to finish before running memreset.");
            return true;
        }

        Runtime rt = Runtime.getRuntime();
        long beforeUsed = rt.totalMemory() - rt.freeMemory();
        long maxHeap = rt.maxMemory();

        long[] compactResult = historyService.compactStaleActions();

        if (global) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                clipboardManager.set(online.getUniqueId(), null);
                historyService.clearPlayerHistory(online.getUniqueId());
                recentEditTrailService.clear(online.getUniqueId());
                visualizationManager.clear(online.getUniqueId());
            }
        } else {
            UUID id = player.getUniqueId();
            clipboardManager.set(id, null);
            historyService.clearPlayerHistory(id);
            recentEditTrailService.clear(id);
            visualizationManager.clear(id);
        }

        System.gc();

        long afterUsed = rt.totalMemory() - rt.freeMemory();
        long reclaimedMb = Math.max(0L, (beforeUsed - afterUsed)) / 1_000_000L;
        long usedMb = afterUsed / 1_000_000L;
        long maxMb = maxHeap / 1_000_000L;
        int usedPct = (int) ((afterUsed * 100L) / Math.max(1L, maxHeap));

        ChatOutput.send(sender, ChatColor.GREEN + "Memory reset"
                + (global ? " (global)" : "") + " complete.");
        ChatOutput.send(sender, ChatColor.GRAY + "Compacted " + compactResult[0] + " action(s), "
                + "deduped " + compactResult[1] + " block change(s) into " + compactResult[2] + " distinct state(s).");
        ChatOutput.send(sender, ChatColor.GRAY + "Heap: " + usedMb + "MB used / " + maxMb + "MB max ("
                + usedPct + "%); reclaimed ~" + reclaimedMb + "MB this pass.");
        ChatOutput.send(sender, ChatColor.DARK_GRAY + "Note: the JVM may not return reclaimed heap to the OS, "
                + "but the freed budget is available for the next operation.");
        return true;
    }

    private boolean handleNudgeNamespace(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("help")) {
            sendHelpPage(sender, NUDGE_HELP_PAGE);
            return true;
        }
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        NudgeSettings current = nudgeSettingsService.get(player);
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("status"))) {
            ChatOutput.send(sender, ChatColor.WHITE + "Nudge settings: invert=" + current.inverted()
                    + ", step=" + current.step()
                    + ", vertical=" + current.verticalMode().name().toLowerCase(Locale.ROOT));
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        switch (key) {
            case "invert" -> {
                if (args.length < 3) {
                    ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl nudge invert <on|off>");
                    return true;
                }
                    boolean inverted = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
                    nudgeSettingsService.set(player, new NudgeSettings(inverted, current.step(), current.verticalMode()));
                    savePlayerRuntime(player);
                    ChatOutput.send(sender, ChatColor.WHITE + "Nudge invert set to " + inverted + ".");
                    return true;
                }
            case "step" -> {
                if (args.length < 3) {
                    ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl nudge step <amount>");
                    return true;
                }
                int step;
                try {
                    step = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    ChatOutput.send(sender, ChatColor.RED + "Step must be a number.");
                    return true;
                }
                if (step < 1 || step > 16) {
                    ChatOutput.send(sender, ChatColor.RED + "Step must be between 1 and 16.");
                    return true;
                    }
                    nudgeSettingsService.set(player, new NudgeSettings(current.inverted(), step, current.verticalMode()));
                    savePlayerRuntime(player);
                    ChatOutput.send(sender, ChatColor.WHITE + "Nudge step set to " + step + ".");
                    return true;
                }
            case "vertical" -> {
                if (args.length < 3) {
                    ChatOutput.send(sender, ChatColor.RED + "Usage: /bzl nudge vertical <jump|look|off>");
                    return true;
                }
                NudgeSettings.VerticalMode mode;
                try {
                    mode = NudgeSettings.VerticalMode.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    ChatOutput.send(sender, ChatColor.RED + "Vertical mode must be jump, look, or off.");
                    return true;
                    }
                    nudgeSettingsService.set(player, new NudgeSettings(current.inverted(), current.step(), mode));
                    savePlayerRuntime(player);
                    ChatOutput.send(sender, ChatColor.WHITE + "Nudge vertical mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
                    return true;
                }
                case "reset" -> {
                    nudgeSettingsService.reset(player);
                    savePlayerRuntime(player);
                    ChatOutput.send(sender, ChatColor.WHITE + "Nudge settings reset to defaults.");
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

    private boolean handleStepRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }

        int blocks = 1;
        if (args.length > 0 && !args[0].isBlank()) {
            try {
                blocks = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                ChatOutput.send(sender, ChatColor.RED + "Usage: /step [blocks]");
                return true;
            }
        }

        if (blocks < 1 || blocks > 16) {
            ChatOutput.send(sender, ChatColor.RED + "Blocks must be between 1 and 16.");
            return true;
        }

        if (!movementAssistService.stepForward(player, blocks)) {
            ChatOutput.send(sender, ChatColor.RED + "Could not step forward.");
            return true;
        }

        ChatOutput.send(sender, ChatColor.WHITE + "Stepped forward " + ChatColor.GOLD + blocks + ChatColor.WHITE + " block" + (blocks == 1 ? "" : "s") + ".");
        return true;
    }

    private boolean handleCommandAuthorityNamespace(CommandSender sender, String[] args) {
        if (!bayzylAccess.allowed(sender, CommandCapability.RUNTIME)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to change Bayzyl command authority.");
            return true;
        }

        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            sendCommandAuthorityStatus(sender);
            return true;
        }

        if (action.equals("claim") || action.equals("bayzyl") || action.equals("on")) {
            CommandOverrideService.ClaimSummary summary = commandAuthorityService.claimPrimaryCommands();
            runtimePreferencesService.saveCommandAuthority(commandAuthorityService);
            ChatOutput.send(sender, ChatColor.WHITE + "Bayzyl command authority enabled. Claimed "
                    + summary.commandsClaimed() + " primary labels.");
            ChatOutput.send(sender, ChatColor.GRAY + "Use /bzl authority giveup to let other plugins own contested labels again.");
            return true;
        }

        if (action.equals("giveup") || action.equals("release") || action.equals("worldedit") || action.equals("off")) {
            CommandOverrideService.ReleaseSummary summary = commandAuthorityService.releaseContestedPrimaryCommands();
            runtimePreferencesService.saveCommandAuthority(commandAuthorityService);
            ChatOutput.send(sender, ChatColor.WHITE + "Bayzyl command authority disabled for contested labels.");
            if (summary.commandsReleased() > 0) {
                ChatOutput.send(sender, ChatColor.GRAY + "Restored " + summary.restoredEntries()
                        + " displaced command-map entries. Bayzyl direct labels remain available as /bayzyl:<command>.");
            } else {
                ChatOutput.send(sender, ChatColor.GRAY + "No displaced commands were restored in this session. On restart, Bayzyl will not claim contested labels.");
            }
            return true;
        }

        syntaxError(sender, "/bzl authority <status|claim|giveup>");
        return true;
    }

    private void sendCommandAuthorityStatus(CommandSender sender) {
        boolean enabled = commandAuthorityService.isBayzylPrimaryCommandsEnabled();
        ChatOutput.send(sender, ChatColor.GOLD + "Command authority: "
                + (enabled ? ChatColor.GREEN + "Bayzyl claims primary labels" : ChatColor.YELLOW + "Bayzyl gives up contested labels"));
        ChatOutput.send(sender, ChatColor.GRAY + "Contested labels remembered this session: "
                + commandAuthorityService.displacedPrimaryCommandCount());
        ChatOutput.send(sender, ChatColor.GRAY + "Commands: /bzl authority claim, /bzl authority giveup, /bzl authority status");
    }

    private boolean handleBzlToggle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            syntaxError(sender, "/bzltoggle <admin|ramalert|authority> ...");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            if (!bayzylAccess.allowed(sender, CommandCapability.ADMIN_MODE)) {
                ChatOutput.send(sender, ChatColor.RED + "You do not have permission to use Bayzyl admin mode.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                ChatOutput.send(sender, ChatColor.RED + "Players only.");
                return true;
            }
            boolean enabled = adminModeService.toggle(player);
            if (enabled) {
                ChatOutput.send(sender, ChatColor.YELLOW + "Warning: Bayzyl admin mode is on. Large commands will skip confirm:true while this is enabled.");
            } else {
                ChatOutput.send(sender, ChatColor.WHITE + "Bayzyl admin mode disabled. Large commands require confirm:true again.");
            }
            return true;
        }

        if (sub.equals("ramalert")) {
            return handleRamAlertToggle(sender, args);
        }

        if (sub.equals("authority")) {
            String[] remapped = new String[Math.max(1, args.length)];
            remapped[0] = "authority";
            if (args.length > 1) {
                System.arraycopy(args, 1, remapped, 1, args.length - 1);
            }
            return handleCommandAuthorityNamespace(sender, remapped);
        }

        syntaxError(sender, "/bzltoggle <admin|ramalert|authority> ...");
        return true;
    }

    private boolean handleRamAlertRoot(CommandSender sender, String[] args) {
        if (!bayzylAccess.allowed(sender, CommandCapability.RUNTIME)) {
            ChatOutput.send(sender, ChatColor.RED + "You do not have permission to use RAM alert controls.");
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
            ramAlertService.statusLines().forEach(line -> ChatOutput.send(sender, line));
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("help")) {
            ramAlertService.helpLines().forEach(line -> ChatOutput.send(sender, line));
            ChatOutput.send(sender, listMenuConfigService.format(
                    RAMALERT_HELP_MENU,
                    "syntax",
                    "&aSyntax: &6{usage} <on|off|status|help> [threshold:<percent>] [interval:<seconds>] [cooldown:<seconds>]",
                    ListMenuConfigService.tokens("usage", usageBase)
            ));
            return true;
        }
        if (mode.equals("off")) {
            ramAlertService.update(new RamAlertSettings(false, current.thresholdPercent(), current.intervalSeconds(), current.cooldownSeconds()));
            runtimePreferencesService.saveGlobal(ramAlertService, messageThemeService);
            ChatOutput.send(sender, ChatColor.GREEN + "RAM alert disabled.");
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
            ChatOutput.send(sender, ChatColor.RED + "Threshold must be between 1 and 99. Example: " + ChatColor.GOLD + usageBase + " on threshold:85");
            return true;
        }
        if (interval < 5) {
            ChatOutput.send(sender, ChatColor.RED + "Interval must be at least 5 seconds. Example: " + ChatColor.GOLD + usageBase + " on interval:30s");
            return true;
        }
        if (cooldown < 5) {
            ChatOutput.send(sender, ChatColor.RED + "Cooldown must be at least 5 seconds. Example: " + ChatColor.GOLD + usageBase + " on cooldown:2m");
            return true;
        }

        ramAlertService.update(new RamAlertSettings(true, threshold, interval, cooldown));
        runtimePreferencesService.saveGlobal(ramAlertService, messageThemeService);
        ChatOutput.send(sender, ChatColor.GREEN + "RAM alert enabled: threshold " + ChatColor.GOLD + threshold + "%"
                + ChatColor.GREEN + ", interval " + ChatColor.GOLD + interval + "s"
                + ChatColor.GREEN + ", cooldown " + ChatColor.GOLD + cooldown + "s.");
        ChatOutput.send(sender, ChatColor.GOLD + "Use " + usageBase + " status" + ChatColor.GREEN + " to inspect current load and top memory consumers.");
        return true;
    }

    private boolean handleTabMenuRoot(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelpPage(sender, TABMENU_HELP_PAGE);
            return true;
        }
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        return handleTabMenuCommand(player, args);
    }

    private boolean handleProfileRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitUpdateCommand(player, args, "/kitupdate");
    }

    private boolean handleKitConfirmRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitConfirmCommand(player, args, "/kitconfirm");
    }

    private boolean handleKitRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitCommand(player, args, "/kit");
    }

    private boolean handleKitMakeRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        return handleKitMakeCommand(player, args, "/kitmake");
    }

    private boolean handleKitShortcutRoot(CommandSender sender, String kitName, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(player, ChatColor.WHITE + "All tab menu modules " + ChatColor.RED + "disabled" + ChatColor.WHITE + ".");
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
            ChatOutput.send(player, ChatColor.WHITE + "Tab menu " + module.key() + ": "
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
        ChatOutput.send(player, ChatColor.WHITE + "Tab menu " + module.key() + " " + (enabled ? "enabled." : "disabled."));
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
            ChatOutput.send(player, ChatColor.WHITE + "Tab menu all: " + state);
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
        ChatOutput.send(player, ChatColor.WHITE + "All tab menu modules " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean handleTabMenuTrail(Player player, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            ChatOutput.send(player, ChatColor.WHITE + "Tab menu trail: "
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
            ChatOutput.send(player, ChatColor.WHITE + "Tab menu trail " + (enabled ? "enabled." : "disabled."));
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
                ChatOutput.send(player, ChatColor.RED + "Trail count must be between 1 and " + recentEditTrailService.maxLimit() + ".");
                return true;
            }
            recentEditTrailService.setLimit(player.getUniqueId(), limit);
            savePlayerRuntime(player);
            tabInfoPanelService.refreshPlayer(player);
            ChatOutput.send(player, ChatColor.WHITE + "Tab menu trail count set to " + ChatColor.GOLD + limit + ChatColor.WHITE + ".");
            return true;
        }

        senderSyntaxTrail(player);
        return true;
    }

    private void senderSyntaxTrail(Player player) {
        ChatOutput.send(player, ChatColor.RED + "Usage: /tabmenu trail <on|off|status|count <1-" + recentEditTrailService.maxLimit() + ">>");
    }

    private void sendTabMenuStatus(Player player) {
        sendMenuLines(player, TABMENU_STATUS_MENU, "header", List.of("&a&lBZL", "&aTab Menu &8| &fStatus"), ListMenuConfigService.tokens());
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
                    "&f{module} &8| &f{state}{extra}",
                    ListMenuConfigService.tokens(
                            "module", titleCase(module.key()),
                            "state", enabled
                                    ? listMenuConfigService.format(TABMENU_STATUS_MENU, "states.on", "&aon", ListMenuConfigService.tokens())
                                    : listMenuConfigService.format(TABMENU_STATUS_MENU, "states.off", "&coff", ListMenuConfigService.tokens()),
                            "extra", extra
                    ));
        }
    }

    private boolean handleProfileNamespace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
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
            ChatOutput.send(player, ChatColor.WHITE + "Usage: " + usageBase + " <save|update|load|inspect|list|delete|rename|duplicate> [name]");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<BuilderProfileSummary> profiles = builderProfileService.listProfileSummaries();
                sendMenuLines(player, PROFILE_LIST_MENU, "header", List.of("&a&lBZL", "&aProfiles &8| &6{count}"), ListMenuConfigService.tokens("count", Integer.toString(profiles.size())));
                if (profiles.isEmpty()) {
                    ChatOutput.send(player, listMenuConfigService.format(
                            PROFILE_LIST_MENU,
                            "empty",
                            "&fNo Bayzyl profiles saved yet.",
                            ListMenuConfigService.tokens()
                    ));
                    return true;
                }
                for (BuilderProfileSummary summary : profiles) {
                    ChatOutput.send(player, listMenuConfigService.format(
                            PROFILE_LIST_MENU,
                            "row",
                            "&6{name} &8| &b{type} &8| &f{contents} &8| &fupdated {updated}",
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
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " save <name> [type:config|toolbar|combined] [overwrite:true]");
                    return true;
                }
                BuilderProfileType type = parseProfileTypeArg(args, 2, builderProfileService.defaultSaveType());
                if (type == null) {
                    ChatOutput.send(player, ChatColor.RED + "Unknown profile type. Use type:config, type:toolbar, or type:combined.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 2);
                if (!overwrite && builderProfileService.existsProfile(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Profile '" + args[1] + "' already exists. Re-run with overwrite:true or use "
                            + ChatColor.GOLD + usageBase + " update " + args[1] + ChatColor.RED + ".");
                    return true;
                }
                if (!builderProfileService.saveProfile(player, args[1], type, overwrite)) {
                    ChatOutput.send(player, ChatColor.RED + "Could not save profile. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                ChatOutput.send(player, ChatColor.WHITE + "Saved " + formatProfileType(type) + " profile "
                        + ChatColor.WHITE + args[1] + ChatColor.WHITE + ".");
                return true;
            }
            case "update" -> {
                if (args.length < 2) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " update <name> [section:config|toolbar|all]");
                    return true;
                }
                if (!builderProfileService.existsProfile(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Profile not found.");
                    return true;
                }
                BuilderProfileLoadSection section = parseProfileSectionArg(args, 2, builderProfileService.defaultLoadSection());
                if (section == null) {
                    ChatOutput.send(player, ChatColor.RED + "Unknown update section. Use section:config, section:toolbar, or section:all.");
                    return true;
                }
                if (!builderProfileService.updateProfile(player, args[1], section)) {
                    ChatOutput.send(player, ChatColor.RED + "Could not update profile.");
                    return true;
                }
                ChatOutput.send(player, ChatColor.WHITE + "Updated profile " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " from your current " + ChatColor.WHITE + section.key() + ChatColor.WHITE + " state.");
                return true;
            }
            case "load" -> {
                if (args.length < 2) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " load <name> [section:config|toolbar|all]");
                    return true;
                }
                BuilderProfileLoadSection section = parseProfileSectionArg(args, 2, builderProfileService.defaultLoadSection());
                if (section == null) {
                    ChatOutput.send(player, ChatColor.RED + "Unknown load section. Use section:config, section:toolbar, or section:all.");
                    return true;
                }
                BuilderProfileLoadResult result = builderProfileService.applyProfile(player, args[1], section);
                if (result == null) {
                    ChatOutput.send(player, ChatColor.RED + "Profile not found.");
                    return true;
                }
                savePlayerRuntime(player);
                runtimePreferencesService.saveGlobal(ramAlertService, messageThemeService);
                ChatOutput.send(player, ChatColor.WHITE + "Loaded profile " + ChatColor.WHITE + args[1] + ChatColor.WHITE
                        + " (" + formatProfileType(result.type()) + ").");
                if (result.appliedConfig()) {
                    ChatOutput.send(player, ChatColor.DARK_GRAY + "Config restored.");
                }
                if (result.appliedToolbar()) {
                    ChatOutput.send(player, ChatColor.DARK_GRAY + "Toolbar placed " + ChatColor.WHITE + result.toolbarPlaced()
                            + ChatColor.DARK_GRAY + ", cleared " + ChatColor.WHITE + result.toolbarCleared()
                            + ChatColor.DARK_GRAY + ", skipped " + ChatColor.WHITE + result.toolbarSkipped()
                            + ChatColor.DARK_GRAY + " protected slots.");
                }
                if (!result.appliedConfig() && !result.appliedToolbar()) {
                    ChatOutput.send(player, ChatColor.DARK_GRAY + "Nothing in that profile matched the requested section.");
                }
                return true;
            }
            case "inspect" -> {
                if (args.length < 2) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " inspect <name>");
                    return true;
                }
                BuilderProfile profile = builderProfileService.loadProfile(args[1]);
                if (profile == null) {
                    ChatOutput.send(player, ChatColor.RED + "Profile not found.");
                    return true;
                }
                sendProfileInspect(player, profile);
                return true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " delete <name>");
                    return true;
                }
                if (!builderProfileService.deleteProfile(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Profile not found.");
                    return true;
                }
                ChatOutput.send(player, ChatColor.WHITE + "Deleted profile " + ChatColor.WHITE + args[1] + ChatColor.WHITE + ".");
                return true;
            }
            case "rename" -> {
                if (args.length < 3) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " rename <from> <to> [overwrite:true]");
                    return true;
                }
                if (!builderProfileService.existsProfile(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Source profile not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderProfileService.existsProfile(args[2])) {
                    ChatOutput.send(player, ChatColor.RED + "Target profile already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderProfileService.renameProfile(args[1], args[2], overwrite)) {
                    ChatOutput.send(player, ChatColor.RED + "Could not rename profile. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                ChatOutput.send(player, ChatColor.WHITE + "Renamed profile " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + args[2] + ChatColor.WHITE + ".");
                return true;
            }
            case "duplicate" -> {
                if (args.length < 3) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " duplicate <from> <to> [overwrite:true]");
                    return true;
                }
                if (!builderProfileService.existsProfile(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Source profile not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderProfileService.existsProfile(args[2])) {
                    ChatOutput.send(player, ChatColor.RED + "Target profile already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderProfileService.duplicateProfile(args[1], args[2], overwrite)) {
                    ChatOutput.send(player, ChatColor.RED + "Could not duplicate profile. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                ChatOutput.send(player, ChatColor.WHITE + "Duplicated profile " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + args[2] + ChatColor.WHITE + ".");
                return true;
            }
            default -> {
                ChatOutput.send(player, ChatColor.RED + "Unknown profile subcommand.");
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
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " load <name>");
                    return true;
                }
                return loadKitIntoPlayer(player, args[1]);
            }
            case "inspect" -> {
                if (args.length < 2) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " inspect <name>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[1]);
                if (kit == null) {
                    ChatOutput.send(player, ChatColor.RED + "Kit not found.");
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
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " delete <name>");
                    return true;
                }
                if (!canManageServerKits(player, "bayzyl.kit.delete")) {
                    ChatOutput.send(player, ChatColor.RED + "Only server operators can delete shared kits.");
                    return true;
                }
                if (!builderKitService.deleteKit(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Kit not found.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                ChatOutput.send(player, ChatColor.WHITE + "Deleted shared kit " + ChatColor.WHITE + args[1] + ChatColor.WHITE + ".");
                ChatOutput.send(player, ChatColor.AQUA + "Use /kit restoredefaults to bring back Bayzyl's default kit library.");
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
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " rename <from> <to> [overwrite:true]");
                    return true;
                }
                if (!canManageServerKits(player, "bayzyl.kit.create")) {
                    ChatOutput.send(player, ChatColor.RED + "Only server operators can rename shared kits.");
                    return true;
                }
                if (!builderKitService.existsKit(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Source kit not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderKitService.existsKit(args[2])) {
                    ChatOutput.send(player, ChatColor.RED + "Target kit already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderKitService.renameKit(args[1], args[2], overwrite)) {
                    ChatOutput.send(player, ChatColor.RED + "Could not rename kit. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                clearPendingKitUpdate(player, builderKitService.normalizeName(args[1]));
                ChatOutput.send(player, ChatColor.WHITE + "Renamed shared kit " + ChatColor.WHITE + args[1]
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + builderKitService.normalizeName(args[2]) + ChatColor.WHITE + ".");
                return true;
            }
            case "duplicate" -> {
                if (args.length < 3) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " duplicate <from> <to> [overwrite:true]");
                    return true;
                }
                if (!canManageServerKits(player, "bayzyl.kit.create")) {
                    ChatOutput.send(player, ChatColor.RED + "Only server operators can duplicate shared kits.");
                    return true;
                }
                if (!builderKitService.existsKit(args[1])) {
                    ChatOutput.send(player, ChatColor.RED + "Source kit not found.");
                    return true;
                }
                boolean overwrite = parseOverwriteArg(args, 3);
                if (!overwrite && builderKitService.existsKit(args[2])) {
                    ChatOutput.send(player, ChatColor.RED + "Target kit already exists. Re-run with overwrite:true.");
                    return true;
                }
                if (!builderKitService.duplicateKit(args[1], args[2], overwrite)) {
                    ChatOutput.send(player, ChatColor.RED + "Could not duplicate kit. Use letters, numbers, _ or -, max 32 chars.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                ChatOutput.send(player, ChatColor.WHITE + "Duplicated shared kit " + ChatColor.WHITE + args[1]
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
                    ChatOutput.send(player, ChatColor.RED + "Only server operators can restore default kits.");
                    return true;
                }
                int restored = builderKitService.restoreDefaultKits();
                kitShortcutRegistry.refresh();
                ChatOutput.send(player, ChatColor.WHITE + "Restored " + ChatColor.GOLD + restored
                        + ChatColor.WHITE + " default Bayzyl kits.");
                ChatOutput.send(player, ChatColor.AQUA + "Custom kits were left alone. Use /kit list to browse the refreshed library.");
                return true;
            }
            default -> {
                if (args.length > 1) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <name>");
                    ChatOutput.send(player, ChatColor.WHITE + "Or use " + ChatColor.GOLD + usageBase + " list" + ChatColor.WHITE + " to browse kits.");
                    return true;
                }
                return loadKitIntoPlayer(player, args[0]);
            }
        }
    }

    private boolean handleKitMenu(Player player, String[] args, String usageBase) {
        if (args.length > 1) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " [page]");
            return true;
        }
        int requestedPage = 1;
        if (args.length == 1 && !args[0].isBlank()) {
            try {
                requestedPage = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " [page]");
                return true;
            }
        }
        return kitMenuService.openMenu(player, requestedPage);
    }

    private boolean handleKitMakeCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can create shared kits.");
            return true;
        }
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <hotbar|inventory> <name> [overwrite:true]");
            return true;
        }

        BuilderKitScope scope = BuilderKitScope.fromKey(args[0]);
        if (scope == null) {
            ChatOutput.send(player, ChatColor.RED + "Unknown kit scope. Use hotbar or inventory.");
            return true;
        }

        String normalizedName = builderKitService.normalizeName(args[1]);
        if (normalizedName == null) {
            ChatOutput.send(player, ChatColor.RED + "Kit names must use letters, numbers, _ or -, max 32 chars.");
            return true;
        }
        if (builderKitService.isReservedName(normalizedName)) {
            ChatOutput.send(player, ChatColor.RED + "That name is reserved for Bayzyl, Minecraft, or server command paths. Pick a different kit name.");
            return true;
        }

        boolean overwrite = parseOverwriteArg(args, 2);
        if (!overwrite && builderKitService.existsKit(normalizedName)) {
            String claimedBy = builderKitService.resolveKitName(normalizedName);
            if (claimedBy == null) {
                claimedBy = normalizedName;
            }
            ChatOutput.send(player, ChatColor.RED + "Kit '" + normalizedName + "' is already claimed by kit '" + claimedBy
                    + "'. Re-run with overwrite:true.");
            return true;
        }
        if (!builderKitService.saveKit(player, normalizedName, scope, overwrite)) {
            ChatOutput.send(player, ChatColor.RED + "Could not save kit.");
            return true;
        }

        kitShortcutRegistry.refresh();
        ChatOutput.send(player, ChatColor.WHITE + "Saved " + formatKitScope(scope) + " "
                + ChatColor.WHITE + normalizedName + ChatColor.WHITE + " to shared server kits.");
        ChatOutput.send(player, ChatColor.WHITE + "Load it with "
                + ChatColor.WHITE + "/kit " + normalizedName
                + ChatColor.WHITE + " or "
                + ChatColor.WHITE + "/bzl " + normalizedName + ChatColor.WHITE + ".");
        if (kitShortcutRegistry.isShortcutRegistered(normalizedName)) {
            ChatOutput.send(player, ChatColor.WHITE + "Direct shortcut ready: " + ChatColor.WHITE + "/" + normalizedName);
        } else {
            ChatOutput.send(player, ChatColor.WHITE + "Direct shortcut unavailable because another command already uses "
                    + ChatColor.WHITE + "/" + normalizedName + ChatColor.WHITE + ".");
        }
        return true;
    }

    private boolean handleKitUpdateCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can update shared kits.");
            return true;
        }
        if (args.length != 1) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <name>");
            return true;
        }

        BuilderKit kit = builderKitService.loadKit(args[0]);
        if (kit == null) {
            ChatOutput.send(player, ChatColor.RED + "Kit not found.");
            return true;
        }

        KitUpdateCheck check = analyzeKitUpdate(player, kit);
        if (check.missingStoredSlots() > 0) {
            pendingKitUpdates.put(player.getUniqueId(), new PendingKitUpdate(kit.name(), System.currentTimeMillis()));
            ChatOutput.send(player, ChatColor.GOLD + "Warning, your current inventory/hotbar is missing items from the kit you are attempting to update. Confirm with /kitconfirm to update anyway");
            ChatOutput.send(player, ChatColor.WHITE + "Missing stored slots: " + ChatColor.WHITE + check.missingStoredSlots()
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.WHITE + "extra filled slots: " + ChatColor.WHITE + check.extraFilledSlots());
            ChatOutput.send(player, ChatColor.AQUA + "Load the kit again first for a clean update, or use /kitconfirm to overwrite "
                    + ChatColor.WHITE + kit.name() + ChatColor.AQUA + " from your current " + scopeNoun(kit.scope()) + ".");
            return true;
        }

        return applyKitUpdate(player, kit);
    }

    private boolean handleKitConfirmCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can confirm shared kit updates.");
            return true;
        }
        if (args.length > 0) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase);
            return true;
        }

        PendingKitUpdate pending = pendingKitUpdates.get(player.getUniqueId());
        if (pending == null) {
            ChatOutput.send(player, ChatColor.RED + "No pending kit update to confirm.");
            return true;
        }
        if (System.currentTimeMillis() - pending.createdAtEpochMillis() > KIT_CONFIRM_WINDOW_MILLIS) {
            pendingKitUpdates.remove(player.getUniqueId());
            ChatOutput.send(player, ChatColor.RED + "Your pending kit update expired. Run /kitupdate <name> again.");
            return true;
        }

        BuilderKit kit = builderKitService.loadKit(pending.kitName());
        if (kit == null) {
            pendingKitUpdates.remove(player.getUniqueId());
            ChatOutput.send(player, ChatColor.RED + "That kit no longer exists.");
            return true;
        }
        return applyKitUpdate(player, kit);
    }

    private boolean applyKitUpdate(Player player, BuilderKit kit) {
        int previousCount = kit.itemCount();
        if (!builderKitService.updateKit(player, kit.name())) {
            ChatOutput.send(player, ChatColor.RED + "Could not update kit.");
            return true;
        }

        pendingKitUpdates.remove(player.getUniqueId());
        kitShortcutRegistry.refresh();
        BuilderKit updated = builderKitService.loadKit(kit.name());
        int newCount = updated == null ? previousCount : updated.itemCount();
        ChatOutput.send(player, ChatColor.WHITE + "Updated " + formatKitScope(kit.scope()) + " "
                + ChatColor.WHITE + kit.name() + ChatColor.WHITE + " from your current " + scopeNoun(kit.scope()) + ".");
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Stored " + ChatColor.WHITE + newCount
                + ChatColor.DARK_GRAY + " items (" + formatSignedDelta(newCount - previousCount) + ChatColor.DARK_GRAY + ").");
        if (kit.builtIn()) {
            ChatOutput.send(player, ChatColor.AQUA + "This currently overrides Bayzyl's default version. Use /kit restoredefaults to revert it.");
        }
        return true;
    }

    private boolean handleKitNoteCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can edit kit metadata.");
            return true;
        }
        if (args.length < 3) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <name> <sentence...|clear>");
            return true;
        }
        BuilderKit kit = builderKitService.loadKit(args[1]);
        if (kit == null) {
            ChatOutput.send(player, ChatColor.RED + "Kit not found.");
            return true;
        }
        String note = joinArgs(args, 2);
        if (note.equalsIgnoreCase("clear")) {
            note = "";
        }
        if (!builderKitService.updateKitNote(kit.name(), note)) {
            ChatOutput.send(player, ChatColor.RED + "Could not update the kit note.");
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + "Updated note for " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleKitThemeCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can edit kit metadata.");
            return true;
        }
        if (args.length < 3) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <name> <theme...>");
            return true;
        }
        BuilderKit kit = builderKitService.loadKit(args[1]);
        if (kit == null) {
            ChatOutput.send(player, ChatColor.RED + "Kit not found.");
            return true;
        }
        String theme = joinArgs(args, 2);
        if (!builderKitService.updateKitTheme(kit.name(), theme)) {
            ChatOutput.send(player, ChatColor.RED + "Could not update the kit theme.");
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + "Updated theme for " + ChatColor.WHITE + kit.name()
                + ChatColor.WHITE + " to " + ChatColor.WHITE + theme + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleKitIconCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can edit kit metadata.");
            return true;
        }
        if (args.length != 3) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <name> <material|clear>");
            return true;
        }
        BuilderKit kit = builderKitService.loadKit(args[1]);
        if (kit == null) {
            ChatOutput.send(player, ChatColor.RED + "Kit not found.");
            return true;
        }
        Material material = null;
        if (!args[2].equalsIgnoreCase("clear")) {
            material = Material.matchMaterial(args[2]);
            if (material == null || !material.isItem()) {
                ChatOutput.send(player, ChatColor.RED + "Unknown icon material.");
                return true;
            }
        }
        if (!builderKitService.updateKitIcon(kit.name(), material)) {
            ChatOutput.send(player, ChatColor.RED + "Could not update the kit icon.");
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + "Updated icon for " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
        return true;
    }

    private boolean handleKitAliasCommand(Player player, String[] args, String usageBase) {
        if (!canManageServerKits(player, "bayzyl.kit.create")) {
            ChatOutput.send(player, ChatColor.RED + "Only server operators can manage kit aliases.");
            return true;
        }
        if (args.length < 2) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <list|add|remove> ...");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                if (args.length != 3) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " list <name>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[2]);
                if (kit == null) {
                    ChatOutput.send(player, ChatColor.RED + "Kit not found.");
                    return true;
                }
                sendMenuLines(player, KIT_ALIAS_LIST_MENU, "header", List.of(
                        "&a&lBZL",
                        "&aKit Aliases &8| &f{name}"
                ), ListMenuConfigService.tokens("name", kit.name()));
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
                ChatOutput.send(player, listMenuConfigService.format(
                        KIT_ALIAS_LIST_MENU,
                        "line",
                        "&fAliases &8| &f{aliases}",
                        ListMenuConfigService.tokens("name", kit.name(), "aliases", aliases)
                ));
                return true;
            }
            case "add" -> {
                if (args.length != 4) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " add <name> <alias>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[2]);
                if (kit == null) {
                    ChatOutput.send(player, ChatColor.RED + "Kit not found.");
                    return true;
                }
                if (!builderKitService.addAlias(kit.name(), args[3])) {
                    ChatOutput.send(player, ChatColor.RED + "Could not add alias. That label may be reserved or already claimed.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                ChatOutput.send(player, ChatColor.WHITE + "Added alias " + ChatColor.WHITE + builderKitService.normalizeName(args[3])
                        + ChatColor.WHITE + " to " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
                return true;
            }
            case "remove" -> {
                if (args.length != 4) {
                    ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " remove <name> <alias>");
                    return true;
                }
                BuilderKit kit = builderKitService.loadKit(args[2]);
                if (kit == null) {
                    ChatOutput.send(player, ChatColor.RED + "Kit not found.");
                    return true;
                }
                if (!builderKitService.removeAlias(kit.name(), args[3])) {
                    ChatOutput.send(player, ChatColor.RED + "Alias not found on that kit.");
                    return true;
                }
                kitShortcutRegistry.refresh();
                ChatOutput.send(player, ChatColor.WHITE + "Removed alias " + ChatColor.WHITE + builderKitService.normalizeName(args[3])
                        + ChatColor.WHITE + " from " + ChatColor.WHITE + kit.name() + ChatColor.WHITE + ".");
                return true;
            }
            default -> {
                ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase + " <list|add|remove> ...");
                return true;
            }
        }
    }

    private boolean handleKitShortcut(Player player, String kitName, String[] args, String usageBase) {
        if (args.length > 0) {
            ChatOutput.send(player, ChatColor.RED + "Usage: " + usageBase);
            return true;
        }
        return loadKitIntoPlayer(player, kitName);
    }

    private boolean loadKitIntoPlayer(Player player, String rawName) {
        BuilderKitLoadResult result = builderKitService.applyKit(player, rawName);
        if (result == null) {
            ChatOutput.send(player, ChatColor.RED + "Kit not found.");
            return true;
        }
        ChatOutput.send(player, ChatColor.WHITE + "Loaded " + formatKitScope(result.scope()) + " "
                + ChatColor.WHITE + result.name() + ChatColor.WHITE + ".");
        ChatOutput.send(player, ChatColor.DARK_GRAY + "Placed " + ChatColor.WHITE + result.placed()
                + ChatColor.DARK_GRAY + ", cleared " + ChatColor.WHITE + result.cleared()
                + ChatColor.DARK_GRAY + " slots.");
        if (result.scope() == BuilderKitScope.INVENTORY) {
            ChatOutput.send(player, ChatColor.DARK_GRAY + "Armor and offhand were left alone.");
        }
        return true;
    }

    private boolean handleKitList(CommandSender sender, String[] args, String usageBase) {
        List<KitListPage> pages = buildKitListPages();
        if (pages.isEmpty()) {
            sendHeader(sender);
            ChatOutput.send(sender, messageThemeService.applyAccent("&aKits &8| &fEmpty"));
            ChatOutput.send(sender, listMenuConfigService.format(
                    KIT_LIST_MENU,
                    "messages.empty",
                    "&fNo shared Bayzyl kits saved yet.",
                    Map.of()
            ));
            return true;
        }

        if (args.length > 1) {
            ChatOutput.send(sender, listMenuConfigService.format(
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
                ChatOutput.send(sender, listMenuConfigService.format(
                        KIT_LIST_MENU,
                        "messages.usage",
                        "&cUsage: {usage} [page]",
                        ListMenuConfigService.tokens("usage", usageBase)
                ));
                return true;
            }
        }

        if (requestedPage < 1 || requestedPage > pages.size()) {
            ChatOutput.send(sender, listMenuConfigService.format(
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
                List.of("&a&lBZL", "&aKits &8| &6Page {page}/{page_count} &8| &f{title}"),
                pageTokens
        )) {
            ChatOutput.send(sender, line);
        }
        String tip = listMenuConfigService.format(
                KIT_LIST_MENU,
                "messages.tip",
                "&bTip: /kit inspect <name> previews a kit. /kit restoredefaults resets Bayzyl defaults.",
                pageTokens
        );
        if (!tip.isBlank()) {
            ChatOutput.send(sender, tip);
        }
        for (BuilderKitSummary summary : page.kits()) {
            ChatOutput.send(sender, listMenuConfigService.format(
                    KIT_LIST_MENU,
                    "messages.row",
                    "&6{name} &8- &f{scope}&8, &f{item_count} items&8, &f{kind}&8, &f{theme} &8| &b{shortcut}",
                    kitListTokens(summary)
            ));
        }
        if (pages.size() > 1) {
            if (requestedPage < pages.size()) {
                ChatOutput.send(sender, listMenuConfigService.format(
                        KIT_LIST_MENU,
                        "messages.next",
                        "&bNext: &f{usage} {next_page}",
                        ListMenuConfigService.tokens(
                                "usage", usageBase,
                                "next_page", Integer.toString(requestedPage + 1)
                        )
                ));
            } else {
                ChatOutput.send(sender, listMenuConfigService.format(
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
            ChatOutput.send(sender, ChatColor.RED + "Players only.");
            return true;
        }
        VisualizationSettings settings = visualizationManager.getSettings(player.getUniqueId());
        settings.setEnabled(!settings.isEnabled());
        boolean enabled = settings.isEnabled();

        savePlayerRuntime(player);
        ChatOutput.send(sender, ChatColor.WHITE + "Selection visualization " + (enabled ? "enabled." : "disabled."));
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

    private void sendUnsortedHelpCommands(CommandSender sender) {
        sendHeader(sender);
        ChatOutput.rail(sender, messageThemeService.applyAccent("&a&lUnsorted &f| &6/bzlhelp unsorted"));
        ChatOutput.rail(sender, messageThemeService.applyAccent("&bCommands not folded into the topic-based help yet."));
        for (CommandSpec spec : CommandRegistry.getAllCommands()) {
            if (helpContentService.mentionsCommand(spec.name())) {
                continue;
            }
            String description = UNSORTED_DESCRIPTIONS.getOrDefault(spec.name(), spec.description());
            ChatOutput.rail(sender, messageThemeService.applyAccent("&6/" + spec.name() + " &f— " + description));
        }
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
            case "brush", "brushes" -> 14;
            default -> null;
        };
    }

    private void sendHeader(CommandSender sender) {
        ChatOutput.send(sender, messageThemeService.accentCode() + ChatColor.BOLD + "BZL");
    }

    private boolean shouldBypassConfirm(Player player, boolean explicitConfirm) {
        return explicitConfirm || adminModeService.isActive(player, bayzylAccess);
    }

    private WorkEstimate estimateSelectionWork(Selection selection) {
        if (selection == null || !selection.isComplete()) {
            return OperationLimits.checkMaterialized(0L);
        }
        return OperationLimits.estimateSelection(
                selection.getMinX(), selection.getMaxX(),
                selection.getMinY(), selection.getMaxY(),
                selection.getMinZ(), selection.getMaxZ());
    }

    private boolean rejectGenerationEstimate(CommandSender sender, WorkEstimate estimate,
                                             boolean confirmed, String confirmationMessage) {
        if (estimate.hardRejected()) {
            ChatOutput.send(sender, ChatColor.RED + estimate.reason());
            return true;
        }
        if (!estimate.permits(confirmed)) {
            ChatOutput.send(sender, ChatColor.RED + confirmationMessage);
            return true;
        }
        return false;
    }

    private void sendUsage(CommandSender sender, CommandSpec spec) {
        ChatOutput.send(sender, ChatColor.GOLD + spec.name());
        ChatOutput.send(sender, ChatColor.WHITE + spec.description());
        if (spec.name().equals("paste")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/paste [-aons] [rotation:<0|90|180|270>] [confirm:true]");
            return;
        }
        if (spec.name().equals("stack")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/stack <count> [direction] [-a] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/stack random <count> [spread:<n>|x:<n>|y:<n>|z:<n>] [-a] [confirm:true]");
            return;
        }
        if (spec.name().equals("rotate")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/rotate <0|90|180|270|left|right|back>");
            return;
        }
        if (spec.name().equals("selectionparticles")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl selectionparticles color <bayzyl|green|red|yellow|blue|purple|orange|#RRGGBB>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl selectionparticles <on|off>");
            return;
        }
        if (spec.name().equals("tool")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Terrain brushes shape terrain. Cleanup brushes are localized erasers."));
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl tool <smooth|raise|lower|flatten> <radius> [power] [bedrock:on|off]");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl cleanup brush <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> <radius> [power]");
            return;
        }
        if (spec.name().equals("cleanup")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Cleanup commands erase clutter from a selection; cleanup brushes are localized erasers."));
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl cleanup <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl cleanup brush <floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup> <radius> [power]");
            ChatOutput.send(sender, ChatColor.AQUA + "foliagecleanup erases plant clutter; lightcleanup erases torches/lanterns/end rods.");
            return;
        }
        if (spec.name().equals("palette")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Palette tools inspect and reshape the dominant block mix in your current selection."));
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl palette analyze");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl palette swap <from> <to> [confirm:true]");
            return;
        }
        if (spec.name().equals("nudge")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Use scroll-nudging to move a selected build a few blocks at a time."));
            ChatOutput.send(sender, ChatColor.AQUA + "/nudge status");
            ChatOutput.send(sender, ChatColor.AQUA + "/nudge invert <on|off>");
            ChatOutput.send(sender, ChatColor.AQUA + "/nudge step <amount>");
            ChatOutput.send(sender, ChatColor.AQUA + "/nudge vertical <jump|look|off>");
            ChatOutput.send(sender, ChatColor.AQUA + "/nudge reset");
            return;
        }
        if (spec.name().equals("env")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Adjust runtime Bayzyl settings like undo history and the menu accent color."));
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl env history <count>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl env accent <color|status|reset>");
            return;
        }
        if (spec.name().equals("ramalert")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Watch server JVM memory and warn staff before memory pressure gets dangerous."));
            ChatOutput.send(sender, ChatColor.AQUA + "/ramalert status");
            ChatOutput.send(sender, ChatColor.AQUA + "/ramalert help");
            ChatOutput.send(sender, ChatColor.AQUA + "/ramalert on threshold:<percent> interval:<seconds|minutes> cooldown:<seconds|minutes>");
            ChatOutput.send(sender, ChatColor.AQUA + "/ramalert off");
            return;
        }
        if (spec.name().equals("profile")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Save and restore Bayzyl config presets, toolbar presets, or combined workflow profiles."));
            ChatOutput.send(sender, ChatColor.AQUA + "/profile save <name> [type:config|toolbar|combined] [overwrite:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile update <name> [section:config|toolbar|all]");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile load <name> [section:config|toolbar|all]");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile inspect <name>");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile list");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile rename <from> <to> [overwrite:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile duplicate <from> <to> [overwrite:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/profile delete <name>");
            return;
        }
        if (spec.name().equals("kit")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Shared builder kits restore a saved hotbar or full main inventory for any player."));
            ChatOutput.send(sender, ChatColor.AQUA + "/kit list [page]");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit menu [page]");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit <name>  |  /kit load <name>  |  /bzl <kitname>");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit inspect <name>  |  /kit update <name>  |  /kit confirm");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit rename <from> <to> [overwrite:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit duplicate <from> <to> [overwrite:true]  |  /kit delete <name>");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit note <name> <sentence...>  |  /kit theme <name> <theme...>");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit icon <name> <material|clear>  |  /kit alias <list|add|remove> ...");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit restoredefaults  |  /kitlist [page]");
            return;
        }
        if (spec.name().equals("kitmake")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Capture your current hotbar or inventory as a shared server kit."));
            ChatOutput.send(sender, ChatColor.AQUA + "/kitmake <hotbar|inventory> <name> [overwrite:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl kitmake <hotbar|inventory> <name> [overwrite:true]");
            return;
        }
        if (spec.name().equals("kitupdate")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Update an existing shared kit from your current hotbar or inventory state."));
            ChatOutput.send(sender, ChatColor.AQUA + "/kitupdate <name>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl kitupdate <name>");
            ChatOutput.send(sender, ChatColor.AQUA + "/kitconfirm");
            return;
        }
        if (spec.name().equals("kitconfirm")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Confirm an overwrite-style kit update after Bayzyl warns about missing stored items."));
            ChatOutput.send(sender, ChatColor.AQUA + "/kitconfirm");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit confirm");
            return;
        }
        if (spec.name().equals("kitlist")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Browse the shared kit library in themed pages."));
            ChatOutput.send(sender, ChatColor.AQUA + "/kitlist [page]");
            ChatOutput.send(sender, ChatColor.AQUA + "/kit list [page]");
            return;
        }
        if (spec.name().equals("kithelp")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Open the Bayzyl kits help page or jump to another help topic."));
            ChatOutput.send(sender, ChatColor.AQUA + "/kithelp");
            ChatOutput.send(sender, ChatColor.AQUA + "/kithelp kits");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzlhelp kits");
            return;
        }
        if (spec.name().equals("tabmenu")) {
            ChatOutput.send(sender, messageThemeService.applyAccent(ChatColor.GREEN + "Toggle builder info modules shown beneath the player list."));
            ChatOutput.send(sender, ChatColor.AQUA + "/tabmenu status");
            ChatOutput.send(sender, ChatColor.AQUA + "/tabmenu all <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/tabmenu ram <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/tabmenu clipboard <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/tabmenu selection <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/tabmenu trail <on|off|status|count <1-" + recentEditTrailService.maxLimit() + ">>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl tabmenu all <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl tabmenu ram <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl tabmenu clipboard <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl tabmenu selection <on|off|status>");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl tabmenu trail <on|off|status|count <1-" + recentEditTrailService.maxLimit() + ">>");
            return;
        }
        if (spec.name().equals("flip")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/flip <x|y|z|left-right|front-back|up-down|left|right|forward|back>");
            return;
        }
        if (spec.name().equals("sphere") || spec.name().equals("hsphere")
                || spec.name().equals("dome") || spec.name().equals("hdome")
                || spec.name().equals("bowl") || spec.name().equals("hbowl")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/" + spec.name() + " <block|distribution> <radius|x,y,z> [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("cyl") || spec.name().equals("hcyl")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/" + spec.name() + " <block|distribution> <radius|x,z> [height] [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("pyramid") || spec.name().equals("hpyramid")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/" + spec.name() + " <block|distribution> <size> [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("generate")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/generate <block> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/g stone (0.75-sqrt(x^2+y^2))^2+z^2 < 0.25^2");
            return;
        }
        if (spec.name().equals("generatebiome")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/generatebiome <biome> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/genbiome cherry_grove (x*x+z*z) < 0.5");
            return;
        }
        if (spec.name().equals("biomeinfo")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/biomeinfo");
            return;
        }
        if (spec.name().equals("forestgen")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/forestgen [size] [type] [density] [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("pumpkins")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/pumpkins [size] [at:<player|target|selection-center>] [confirm:true]");
            return;
        }
        if (spec.name().equals("jail")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/jail <player>");
            ChatOutput.send(sender, ChatColor.GRAY + "Only works while Bayzyl admin mode is enabled.");
            return;
        }
        if (spec.name().equals("liberate")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/liberate <player>");
            ChatOutput.send(sender, ChatColor.GRAY + "Only works while Bayzyl admin mode is enabled.");
            return;
        }
        if (spec.name().equals("susu")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/susu");
            return;
        }
        if (spec.name().equals("artie")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/artie");
            return;
        }
        if (spec.name().equals("bubu")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/bubu");
            return;
        }
        if (spec.name().equals("walls")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/walls <block|distribution> [mask:<blocks>] [confirm:true]");
            return;
        }
        if (spec.name().equals("overlay")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/overlay <block> [mask:<blocks>] [confirm:true]");
            return;
        }
        if (spec.name().equals("naturalize")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/naturalize [depth:<n>] [bedrock:on|off] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/bzl naturalize [depth:<n>] [bedrock:on|off] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/brush naturalize <radius> [depth] [bedrock:on|off]");
            return;
        }
        if (spec.name().equals("schematic")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/schematic save <name> [origin:<min|center|player>] [entities:on|off] [biomes:on|off] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/schematic load <name> [preview:true] [rotation:<0|90|180|270>] [at:<player|target|origin>]");
            ChatOutput.send(sender, ChatColor.AQUA + "/schematic orient [rotation:<0|90|180|270>] [at:<player|target|origin>]");
            ChatOutput.send(sender, ChatColor.GREEN + "Saves/loads .schem files and previews placement footprint/orientation.");
            return;
        }
        if (spec.name().equals("brush")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/brush <sphere|hsphere|cyl|hcyl|pyramid|hpyramid|naturalize> <block|distribution> <args...> [mask:<blocks>] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/brush structure <structure_id> [at:<player|target|center|selection-center>] [confirm:true]");
            ChatOutput.send(sender, ChatColor.AQUA + "/brush <paint|spatter|replace|blend|surface|noise|restore|vegetation|decay> ...");
            ChatOutput.send(sender, ChatColor.AQUA + "/brush <clipboard|paint|erase|smooth|raise|lower|flatten|naturalize|floatingcleanup|foliagecleanup|liquidcleanup|snowcleanup|lightcleanup|none|info> ...");
            ChatOutput.send(sender, ChatColor.AQUA + "/brush <save|load|list|delete> ...");
            ChatOutput.send(sender, ChatColor.GREEN + "Binds the brush to your held item. Empty hand creates a Bayzyl brush item.");
            ChatOutput.send(sender, ChatColor.GRAY + "Shortcut: if you finish with a bare block name like 'air', it is treated as the mask.");
            return;
        }
        if (spec.name().equals("smooth")) {
            ChatOutput.send(sender, ChatColor.AQUA + "/smooth [iterations|iterations:<n>] [confirm:true]");
            return;
        }
        ChatOutput.send(sender, ChatColor.AQUA + spec.usage());
    }

    private void sendShapeResult(CommandSender sender, ShapeResult result, String label) {
        if (!result.success()) {
            ChatOutput.send(sender, ChatColor.RED + result.message());
            return;
        }
        ChatOutput.send(sender, ChatColor.DARK_GRAY + result.message());
        if (result.preview()) {
            ChatOutput.send(sender, ChatColor.WHITE + "Previewed " + label + ".");
            return;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Created " + label + " and changed " + result.changed() + " blocks.");
    }

    private void sendGeneratorResult(CommandSender sender, GeneratorResult result, String label) {
        if (!result.success()) {
            ChatOutput.send(sender, ChatColor.RED + result.message());
            return;
        }
        ChatOutput.send(sender, ChatColor.DARK_GRAY + result.message());
        if (result.preview()) {
            ChatOutput.send(sender, ChatColor.WHITE + "Previewed " + label + ".");
            return;
        }
        ChatOutput.send(sender, ChatColor.WHITE + "Completed " + label + " and changed " + result.changed() + " blocks.");
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

    private Location resolvePasteTarget(Player player, Clipboard clipboard, String at) {
        return switch (normalizePasteAt(at)) {
            case "origin" -> {
                if (clipboard == null || clipboard.getOrigin() == null || clipboard.getOrigin().getWorld() == null) {
                    ChatOutput.send(player, ChatColor.RED + "Clipboard origin unavailable.");
                    yield null;
                }
                yield clipboard.getOrigin();
            }
            case "target" -> {
                var result = player.rayTraceBlocks(120);
                if (result == null || result.getHitBlock() == null) {
                    ChatOutput.send(player, ChatColor.RED + "No target block in range.");
                    yield null;
                }
                var face = result.getHitBlockFace();
                yield face == null
                        ? result.getHitBlock().getLocation()
                        : result.getHitBlock().getRelative(face).getLocation();
            }
            default -> resolvePlayerPasteAnchor(player);
        };
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
        ChatOutput.send(sender, ChatColor.RED + "Syntax: " + ChatColor.GOLD + syntax);
    }

    private void savePlayerRuntime(Player player) {
        runtimePreferencesService.savePlayer(player, nightVisionService, autoUnstickService, ghostHandService, stackLookDirectionService, stackAutoMoveService, nudgeSettingsService, visualizationManager, tabMenuSettingsService, recentEditTrailService);
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
                "&a&lBZL",
                "&a{name} &8| &b{type}"
        ), baseTokens);
        if (profile.updatedAtEpochMillis() > 0L) {
            sendMenuLine(player, PROFILE_INSPECT_MENU, "updated",
                    "&fUpdated &8| &b{updated}",
                    baseTokens);
        }
        if (profile.config() != null) {
            BuilderProfileConfig config = profile.config();
            sendMenuLine(player, PROFILE_INSPECT_MENU, "features-title", "&bFeatures", ListMenuConfigService.tokens());
            sendMenuLine(player, PROFILE_INSPECT_MENU, "features-primary",
                    "&f  Viz &8| &6{viz}&8 | &fNightvision &8| &6{nightvision}",
                    ListMenuConfigService.tokens(
                            "viz", boolWord(config.selectionParticlesEnabled()),
                            "nightvision", boolWord(config.nightVisionEnabled())
                    ));
            sendMenuLine(player, PROFILE_INSPECT_MENU, "features-secondary",
                    "&f  Unstick &8| &6{unstick}&8 | &fGhosthand &8| &6{ghosthand}",
                    ListMenuConfigService.tokens(
                            "unstick", boolWord(config.autoUnstickEnabled()),
                            "ghosthand", boolWord(config.ghostHandEnabled())
                    ));
            sendMenuLine(player, PROFILE_INSPECT_MENU, "nudge-title", "&bNudge Settings", ListMenuConfigService.tokens());
            sendMenuLine(player, PROFILE_INSPECT_MENU, "nudge-line",
                    "&f  Step &8| &6{step}&8 | &fInvert &8| &6{invert}&8 | &fVertical &8| &6{vertical}",
                    ListMenuConfigService.tokens(
                            "step", Integer.toString(config.nudgeSettings().step()),
                            "invert", boolWord(config.nudgeSettings().inverted()),
                            "vertical", config.nudgeSettings().verticalMode().name().toLowerCase(Locale.ROOT)
                    ));
            sendMenuLine(player, PROFILE_INSPECT_MENU, "tabmenu-title", "&bTab Menu & Trail", ListMenuConfigService.tokens());
            sendMenuLine(player, PROFILE_INSPECT_MENU, "tabmenu-line",
                    "&f  {tabmenu}&8 | &fTrail limit &8| &6{trail_limit}",
                    ListMenuConfigService.tokens(
                            "tabmenu", summarizeTabMenuStates(config.tabMenuStates()),
                            "trail_limit", Integer.toString(config.trailLimit())
                    ));
        }
        if (profile.type().hasToolbar()) {
            sendMenuLine(player, PROFILE_INSPECT_MENU, "toolbar-title", "&bToolbar", ListMenuConfigService.tokens());
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
        ChatOutput.send(sender, listMenuConfigService.format(menuId, path, fallback, tokens));
    }

    private void sendMenuLines(CommandSender sender, String menuId, String path, List<String> fallback, Map<String, String> tokens) {
        for (String line : listMenuConfigService.formatList(menuId, path, fallback, tokens)) {
            ChatOutput.send(sender, line);
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
            parts.add(titleCase(module.key()) + ": " + (enabled ? ChatColor.GREEN + "on" + ChatColor.WHITE : ChatColor.RED + "off" + ChatColor.WHITE));
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

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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
                "&a&lBZL",
                "&aPalette Analysis &8| &f{size}"
        ), baseTokens);
        sendMenuLine(player, PALETTE_ANALYSIS_MENU, "summary",
                "&fFilled &8| &6{filled}&8 | &fAir &8| &6{air}&8 | &fUnique &8| &6{unique}",
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
                    "&6{material}&8 | &f{count}&8 | &f{percent}",
                    ListMenuConfigService.tokens(
                            "material", entry.displayName(),
                            "count", Integer.toString(entry.count()),
                            "percent", percent(entry.count(), analysis.filledBlocks())
                    )
            ));
        }
        if (topMaterials.isEmpty()) {
            sendMenuLine(player, PALETTE_ANALYSIS_MENU, "top-blocks.empty",
                    "&fTop blocks &8| &8selection is all air",
                    ListMenuConfigService.tokens());
        } else {
            String separator = listMenuConfigService.format(PALETTE_ANALYSIS_MENU, "top-blocks.separator", "&8 | ", ListMenuConfigService.tokens());
            sendMenuLine(player, PALETTE_ANALYSIS_MENU, "top-blocks.line",
                    "&fTop blocks &8| {items}",
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
                    "&b{family}&8 | &f{count}&8 | &f{percent}",
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
                    "&fFamilies &8| {items}",
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
                "&a&lBZL",
                "&a{name} &8| &b{scope}"
        ), tokens);
        sendMenuLine(player, KIT_INSPECT_MENU, "summary",
                "&fType &8| &b{kind}&8 | &fStored &8| &6{item_count} items",
                tokens);
        sendMenuLine(player, KIT_INSPECT_MENU, "updated",
                "&fUpdated &8| &b{updated}",
                tokens);
        sendMenuLine(player, KIT_INSPECT_MENU, "theme-author",
                "&fTheme &8| &6{theme}&8 | &fAuthor &8| &6{author}",
                tokens);
        String note = displayKitNote(kit);
        if (!note.isEmpty()) {
            sendMenuLine(player, KIT_INSPECT_MENU, "note",
                    "&bNote &8| &f{note}",
                    ListMenuConfigService.tokens("note", note));
        }
        sendMenuLine(player, KIT_INSPECT_MENU, "load-targets-title", "&bLoad Targets", ListMenuConfigService.tokens());
        sendMenuLine(player, KIT_INSPECT_MENU, "load-targets-line",
                "&f  {load_targets}",
                ListMenuConfigService.tokens("load_targets", formatKitLoadTargets(kit)));
        if (!kit.aliases().isEmpty()) {
            sendMenuLine(player, KIT_INSPECT_MENU, "aliases-title",
                    "&bAliases &8| &6{aliases}",
                    ListMenuConfigService.tokens("aliases", String.join(ChatColor.GRAY + ", " + ChatColor.GOLD, kit.aliases())));
            sendMenuLine(player, KIT_INSPECT_MENU, "aliases-loads-line",
                    "&f  Loads &8| {alias_loads}",
                    ListMenuConfigService.tokens("alias_loads", formatKitAliasLoads(kit)));
        }
        sendMenuLine(player, KIT_INSPECT_MENU, "contents-title", "&bContents", ListMenuConfigService.tokens());
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
        ChatOutput.send(player, ChatColor.WHITE + message);
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
        return player.hasPermission("bayzyl.admin") || player.hasPermission(permission);
    }

    private CommandCapability requiredCapability(String command, String[] args) {
        if (detailBrushShortcutRegistry.isShortcutRegistered(command)) {
            return commandAccessPolicy.requiredDetailShortcutCapability();
        }
        if (kitShortcutRegistry.isShortcutRegistered(command)) {
            return commandAccessPolicy.requiredKitShortcutCapability();
        }
        return commandAccessPolicy.requiredCapability(command, args);
    }

    private List<String> filterProspectiveSuggestions(CommandSender sender,
                                                      String command,
                                                      String[] args,
                                                      List<String> candidates) {
        return commandAccessPolicy.filterAllowedSuggestions(
                sender::hasPermission, command, args, candidates);
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
            suggestions.addAll(filterPrefix(SuggestionUtil.itemSuggestions(args[2]), args[2]));
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

    private List<String> suggestSchematicArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("save", "load", "orient", "orientation", "preview", "list"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("save")) {
            String current = args[args.length - 1];
            return filterPrefix(List.of("origin:min", "origin:center", "origin:player", "entities:on", "entities:off", "biomes:on", "biomes:off", "confirm:true"), current);
        }
        if (sub.equals("load")) {
            if (args.length == 2) {
                return filterPrefix(schematicService.listSchematics(null), args[1]);
            }
            return suggestSchematicOrientationOptions(args[args.length - 1]);
        }
        if (sub.equals("orient") || sub.equals("orientation") || sub.equals("preview")) {
            return suggestSchematicOrientationOptions(args[args.length - 1]);
        }
        if (sub.equals("list")) {
            String current = args[args.length - 1];
            String lower = current.toLowerCase(Locale.ROOT);
            if (lower.startsWith("filter:")) {
                String filter = current.substring("filter:".length());
                List<String> names = schematicService.listSchematics(filter);
                List<String> suggestions = new ArrayList<>();
                for (String name : names) {
                    suggestions.add("filter:" + name);
                }
                return suggestions;
            }
            if (lower.startsWith("page:")) {
                return filterPrefix(List.of("page:1", "page:2", "page:3", "page:4", "page:5"), current);
            }
            return filterPrefix(List.of("filter:", "page:"), current);
        }
        return Collections.emptyList();
    }

    private List<String> suggestSchematicOrientationOptions(String current) {
        String lower = current.toLowerCase(Locale.ROOT);
        if (lower.startsWith("rotation:") || lower.startsWith("rotate:")) {
            String key = lower.startsWith("rotate:") ? "rotate:" : "rotation:";
            return filterPrefix(List.of(key + "0", key + "90", key + "180", key + "270", key + "left", key + "right", key + "back"), current);
        }
        if (lower.startsWith("at:")) {
            return filterPrefix(List.of("at:player", "at:target", "at:origin"), current);
        }
        if (lower.startsWith("preview:")) {
            return filterPrefix(List.of("preview:true", "preview:false"), current);
        }
        if (lower.startsWith("select:")) {
            return filterPrefix(List.of("select:true", "select:false"), current);
        }
        return filterPrefix(List.of("rotation:", "at:player", "at:target", "at:origin", "preview:true", "select:true"), current);
    }

    private boolean isTopLevelSuggestionCommand(String cmd) {
        return switch (cmd) {
            case "set", "replace", "copy", "cut", "paste", "move", "stack", "rotate", "flip",
                    "sphere", "hsphere", "dome", "hdome", "bowl", "hbowl", "cyl", "hcyl", "pyramid", "hpyramid",
                    "generate", "generatebiome", "genfeature", "genstructure", "regen", "forestgen", "pumpkins", "clearhistory", "historyclear",
                    "walls", "overlay", "smooth", "naturalize", "undo", "redo", "oops", "expand", "contract",
                    "select", "tabmenu", "ramalert", "nudge", "nightvision", "ghosthand", "unstick", "align",
                    "jail", "liberate", "bubu", "susu", "artie", "lol", "mask", "gmask", "material", "size", "density", "none",
                    "memreset", "clearclipboard", "accent", "bzlaccent", "authority", "bzlauthority", "commandauthority",
                    "tool", "bzltool",
                    "floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup",
                    "clipboardinfo", "trailclear", "selcorners",
                    "selswap", "selsave", "selload", "selcenter", "pos1", "pos2", "measure", "ruler", "whereami", "surface",
                    "ascend", "descend", "biomeinfo", "thru", "resume", "particlevisualtoggle", "bzltoggle", "agitate",
                    "step", "wand", "eraser", "ceil", "centerme", "brushmenu", "bm",
                    "wetoggle" -> true;
            default -> false;
        };
    }

    private List<String> suggestTopLevelArgs(String cmd, String[] args) {
        return switch (cmd) {
            case "set" -> suggestSetArgs(args);
            case "replace" -> suggestReplaceArgs(args);
            case "copy", "cut" -> suggestMaskOnlyArgs(args);
            case "paste" -> suggestPasteArgs(args);
            case "move" -> suggestMoveArgs(args);
            case "stack" -> suggestStackArgs(args);
            case "rotate" -> suggestRotateArgs(args);
            case "flip" -> suggestFlipArgs(args);
            case "sphere", "hsphere", "dome", "hdome", "bowl", "hbowl", "cyl", "hcyl", "pyramid", "hpyramid" -> suggestShapeArgs(cmd, args);
            case "generate" -> suggestGenerateArgs(args);
            case "generatebiome" -> suggestGenerateBiomeArgs(args);
            case "genfeature" -> filterPrefix(VanillaContentRegistry.featureIds(), lastArg(args));
            case "genstructure" -> filterPrefix(VanillaContentRegistry.structureIds(), lastArg(args));
            case "regen" -> Collections.emptyList();
            case "forestgen" -> suggestForestGenArgs(args);
            case "pumpkins" -> suggestPumpkinsArgs(args);
            case "walls", "overlay" -> suggestWallsOverlayArgs(args);
            case "smooth" -> suggestSmoothArgs(args);
            case "naturalize" -> suggestNaturalizeArgs(args);
            case "pos1", "pos2" -> suggestPosArgs(args);
            case "undo", "redo", "oops" -> suggestStepsArgs(args);
            case "expand", "contract" -> suggestResizeArgs(args);
            case "select" -> suggestSelectArgs(args);
            case "jail" -> suggestJailArgs(args);
            case "liberate" -> suggestLiberateArgs(args);
            case "tabmenu" -> suggestTabMenuArgs(args);
            case "ramalert" -> suggestRamAlertArgs(args);
            case "memreset" -> args.length <= 1 ? filterPrefix(List.of("global"), lastArg(args)) : Collections.emptyList();
            case "clearclipboard" -> Collections.emptyList();
            case "accent", "bzlaccent" -> suggestAccentArgs(args);
            case "authority", "bzlauthority", "commandauthority" -> suggestCommandAuthorityArgs(args);
            case "tool", "bzltool" -> suggestToolArgs(args);
            case "floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup" ->
                    args.length <= 1 ? filterPrefix(List.of("confirm:true"), lastArg(args)) : Collections.emptyList();
            case "step" -> suggestStepArgs(args);
            case "nudge" -> suggestNudgeArgs(args);
            case "nightvision", "ghosthand" -> suggestToggleArgs(args);
            case "unstick" -> suggestUnstickArgs(args);
            case "align" -> suggestAlignArgs(args);
            case "mask" -> filterPrefix(List.of("none", "all", "solid", "air"), lastArg(args));
            case "gmask" -> args.length <= 1
                    ? filterPrefix(List.of("none", "status", "stone", "dirt", "grass_block", "air"), lastArg(args))
                    : SuggestionUtil.blockSuggestions(lastArg(args));
            case "material" -> SuggestionUtil.blockSuggestions(lastArg(args));
            case "size" -> filterPrefix(List.of("1", "3", "5", "8", "12", "16"), lastArg(args));
            case "density" -> filterPrefix(List.of("0.1", "0.2", "0.35", "0.5", "0.8", "1.0"), lastArg(args));
            case "bzltoggle" -> args.length <= 1
                    ? filterPrefix(List.of("admin", "ramalert", "authority"), lastArg(args))
                    : (args[0].equalsIgnoreCase("authority")
                    ? suggestCommandAuthorityArgs(stripFirstArg(args))
                    : filterPrefix(List.of("admin", "ramalert", "authority"), lastArg(args)));
            case "wetoggle" -> args.length <= 1
                    ? filterPrefix(List.of("on", "off", "toggle", "status"), lastArg(args))
                    : Collections.emptyList();
            default -> Collections.emptyList();
        };
    }

    private List<String> suggestBzlHelpArgs(String[] args) {
        if (args.length <= 1) {
            return filterPrefix(helpTopicSuggestions(), args.length == 0 ? "" : args[0]);
        }
        return filterPrefix(helpTopicSuggestions(), args[args.length - 1]);
    }

    private List<String> suggestPaletteArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("analyze", "swap"), args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("swap")) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("confirm:")) {
                return filterPrefix(List.of("confirm:true"), current);
            }
            if (args.length == 2) {
                return SuggestionUtil.blockSuggestions(current);
            }
            if (args.length == 3) {
                return SuggestionUtil.blockSuggestions(current);
            }
            return filterPrefix(List.of("confirm:true"), current);
        }
        return Collections.emptyList();
    }

    private List<String> suggestSetArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SuggestionUtil.blockSuggestions(args[0]);
        }
        String current = args[args.length - 1];
        if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            return SuggestionUtil.maskSuggestions(current);
        }
        return filterPrefix(List.of("mask:", "if:air", "if:solid", "if:any", "confirm:true"), current);
    }

    private List<String> suggestReplaceArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SuggestionUtil.blockSuggestions(args[0]);
        }
        if (args.length == 2) {
            return SuggestionUtil.blockSuggestions(args[1]);
        }
        String current = args[args.length - 1];
        if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            return SuggestionUtil.maskSuggestions(current);
        }
        return filterPrefix(List.of("mask:", "confirm:true", "at:target", "at:selection-center"), current);
    }

    private List<String> suggestMaskOnlyArgs(String[] args) {
        String current = lastArg(args);
        if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            return SuggestionUtil.maskSuggestions(current);
        }
        return filterPrefix(List.of("mask:", "confirm:true"), current);
    }

    private List<String> suggestPasteArgs(String[] args) {
        String current = lastArg(args);
        if (current.startsWith("-")) {
            return filterPrefix(List.of("-a", "-o", "-p", "-s", "-n"), current);
        }
        if (current.toLowerCase(Locale.ROOT).startsWith("rotation:")) {
            return filterPrefix(List.of("rotation:0", "rotation:90", "rotation:180", "rotation:270"), current);
        }
        return filterPrefix(List.of("-a", "-o", "-p", "-s", "-n", "rotation:", "confirm:true", "at:player", "at:origin", "at:target"), current);
    }

    private List<String> suggestMoveArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            if (args[0].isBlank() || !args[0].contains(":")) {
                return filterPrefix(List.of("1", "3", "5", "10", "20"), args[0]);
            }
        }
        String current = lastArg(args);
        return filterPrefix(List.of("north", "south", "east", "west", "up", "down", "forward", "back", "-a", "confirm:true"), current);
    }

    private List<String> suggestStackArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("random", "1", "2", "3", "5", "10"), args[0]);
        }
        String current = lastArg(args);
        if (args[0].equalsIgnoreCase("random")) {
            if (args.length == 2) {
                return filterPrefix(List.of("1", "2", "3", "5", "10"), current);
            }
            return filterPrefix(List.of("spread:", "x:", "y:", "z:", "-a", "confirm:true"), current);
        }
        return filterPrefix(List.of("north", "south", "east", "west", "up", "down", "forward", "back", "-a", "confirm:true"), current);
    }

    private List<String> suggestRotateArgs(String[] args) {
        return filterPrefix(List.of("0", "90", "180", "270", "left", "right", "back", "live"), lastArg(args));
    }

    private List<String> suggestFlipArgs(String[] args) {
        return filterPrefix(List.of("x", "y", "z", "left-right", "front-back", "up-down", "left", "right", "forward", "back"), lastArg(args));
    }

    private List<String> suggestShapeArgs(String cmd, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SuggestionUtil.blockSuggestions(args[0]);
        }
        if (args.length == 2 && !args[1].contains(":")) {
            return filterPrefix(List.of("3", "5", "8", "12", "16"), args[1]);
        }
        String current = lastArg(args);
        if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            return SuggestionUtil.maskSuggestions(current);
        }
        if (current.toLowerCase(Locale.ROOT).startsWith("at:")) {
            return filterPrefix(List.of("at:player", "at:target", "at:selection-center"), current);
        }
        List<String> options = new ArrayList<>(List.of("mask:", "at:player", "at:target", "at:selection-center", "confirm:true"));
        if (cmd.equals("cyl") || cmd.equals("hcyl")) {
            options.add(0, "height:");
        }
        if (cmd.equals("sphere") || cmd.equals("hsphere") || cmd.equals("dome") || cmd.equals("hdome") || cmd.equals("bowl") || cmd.equals("hbowl")) {
            options.add(0, "radius:");
        }
        if (cmd.equals("pyramid") || cmd.equals("hpyramid")) {
            options.add(0, "size:");
        }
        return filterPrefix(options, current);
    }

    private List<String> suggestGenerateArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SuggestionUtil.blockSuggestions(args[0]);
        }
        if (args.length == 2 && !args[1].contains(":")) {
            return Collections.emptyList();
        }
        return filterPrefix(List.of("mode:normalized", "mode:raw", "mode:center", "mode:origin", "hollow:true", "confirm:true"), lastArg(args));
    }

    private List<String> suggestGenerateBiomeArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(biomeSuggestions(), args[0]);
        }
        if (args.length == 2 && args[1].isBlank()) {
            return filterPrefix(List.of("sphere", "cyl", "pyramid", "dome", "bowl", "expr", "mode:normalized", "mode:raw", "mode:center", "mode:origin"), args[1]);
        }
        String current = lastArg(args);
        if (current.toLowerCase(Locale.ROOT).startsWith("mode:")) {
            return filterPrefix(List.of("mode:normalized", "mode:raw", "mode:center", "mode:origin"), current);
        }
        return filterPrefix(List.of("sphere", "cyl", "pyramid", "dome", "bowl", "expr", "mode:normalized", "mode:raw", "mode:center", "mode:origin", "hollow:true", "preview:true", "confirm:true"), current);
    }

    private List<String> suggestForestGenArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("5", "8", "10", "15", "20"), args[0]);
        }
        if (args.length == 2) {
            return filterPrefix(forestTreeTypes(), args[1]);
        }
        if (args.length == 3) {
            return filterPrefix(List.of("1", "2", "3", "5", "8"), args[2]);
        }
        return filterPrefix(List.of("at:player", "at:target", "at:selection-center", "confirm:true"), lastArg(args));
    }

    private List<String> suggestPumpkinsArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("5", "8", "10", "15", "20"), args[0]);
        }
        return filterPrefix(List.of("at:player", "at:target", "at:selection-center", "confirm:true"), lastArg(args));
    }

    private List<String> suggestWallsOverlayArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SuggestionUtil.blockSuggestions(args[0]);
        }
        String current = lastArg(args);
        if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            return SuggestionUtil.maskSuggestions(current);
        }
        return filterPrefix(List.of("mask:", "confirm:true"), current);
    }

    private List<String> suggestSmoothArgs(String[] args) {
        return filterPrefix(List.of("1", "2", "3", "5", "confirm:true", "iterations:1", "iterations:2", "iterations:3"), lastArg(args));
    }

    private List<String> suggestNaturalizeArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        String current = lastArg(args);
        if (current.toLowerCase(Locale.ROOT).startsWith("bedrock:")) {
            return filterPrefix(List.of("bedrock:on", "bedrock:off"), current);
        }
        return filterPrefix(List.of("depth:3", "depth:5", "depth:8", "bedrock:on", "bedrock:off", "confirm:true"), current);
    }

    private List<String> suggestStepsArgs(String[] args) {
        return filterPrefix(List.of("steps:1", "steps:2", "steps:3", "steps:5", "steps:10"), lastArg(args));
    }

    private List<String> suggestResizeArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("1", "2", "3", "5", "all"), args[0]);
        }
        if (args[0].equalsIgnoreCase("all")) {
            if (args.length == 2) {
                return filterPrefix(List.of("1", "2", "3", "5", "8", "10"), args[1]);
            }
            return filterPrefix(List.of("north", "south", "east", "west", "up", "down", "forward", "back"), lastArg(args));
        }
        String current = lastArg(args);
        return filterPrefix(List.of("north", "south", "east", "west", "up", "down", "forward", "back"), current);
    }

    private List<String> suggestSelectArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("cube", "viz"), args[0]);
        }
        if (args[0].equalsIgnoreCase("cube")) {
            if (args.length == 2) {
                if (args[1].contains(":")) {
                    return filterPrefix(List.of("at:player", "at:target"), args[1]);
                }
                return filterPrefix(List.of("3", "5", "7", "9", "11"), args[1]);
            }
            return filterPrefix(List.of("at:player", "at:target"), lastArg(args));
        }
        if (args[0].equalsIgnoreCase("viz")) {
            return filterPrefix(List.of("on", "off", "intensity:low", "intensity:medium", "intensity:high", "grid:on", "grid:off", "grid:auto", "consistency:5", "color:bayzyl"), lastArg(args));
        }
        return Collections.emptyList();
    }

    private List<String> suggestJailArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filterPrefix(names, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestLiberateArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> jailedNames = new ArrayList<>();
            for (JailSnapshot snapshot : activeJailSnapshots.values()) {
                Player online = Bukkit.getPlayer(snapshot.targetId());
                if (online != null && online.isOnline()) {
                    jailedNames.add(online.getName());
                }
            }
            return filterPrefix(jailedNames, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestSelectionParticlesArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("on", "off", "color"), args[0]);
        }
        if (args[0].equalsIgnoreCase("color")) {
            return filterPrefix(List.of("bayzyl", "green", "red", "yellow", "blue", "purple", "orange", "#RRGGBB"), lastArg(args));
        }
        return filterPrefix(List.of("on", "off", "color"), lastArg(args));
    }

    private List<String> suggestToolArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("smooth", "raise", "lower", "flatten"), args[0]);
        }
        if (args.length == 2) {
            return filterPrefix(List.of("1", "3", "5", "8", "12"), args[1]);
        }
        return filterPrefix(List.of("bedrock:on", "bedrock:off"), lastArg(args));
    }

    private List<String> suggestTabMenuArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("all", "ram", "clipboard", "selection", "trail", "status", "help"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("all")) {
            return filterPrefix(List.of("on", "off", "status"), lastArg(args));
        }
        if (sub.equals("trail")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("count")) {
                return filterPrefix(List.of("1", "2", "3", "5", "8", "10", "20"), lastArg(args));
            }
            return filterPrefix(List.of("on", "off", "status", "count"), lastArg(args));
        }
        if (TabMenuModule.fromKey(sub) != null) {
            return filterPrefix(List.of("on", "off", "status"), lastArg(args));
        }
        return Collections.emptyList();
    }

    private List<String> suggestRamAlertArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("on", "off", "status", "help"), args[0]);
        }
        return filterPrefix(List.of("threshold:", "interval:", "cooldown:"), lastArg(args));
    }

    private List<String> suggestStepArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("1", "2", "3", "4", "5", "8", "16"), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestPosArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("at:player", "at:target", "at:selection-center"), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestNudgeArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("status", "invert", "step", "vertical", "reset"), args[0]);
        }
        return filterPrefix(List.of("on", "off", "status", "1", "2", "3", "4", "5"), lastArg(args));
    }

    private List<String> suggestToggleArgs(String[] args) {
        return filterPrefix(List.of("on", "off", "toggle", "status"), lastArg(args));
    }

    private List<String> suggestCommandAuthorityArgs(String[] args) {
        if (args.length <= 1) {
            return filterPrefix(List.of("status", "claim", "giveup"), lastArg(args));
        }
        return Collections.emptyList();
    }

    private List<String> suggestAccentArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of(
                    "status", "reset", "default",
                    "aqua", "black", "blue", "dark_aqua", "dark_blue", "dark_gray", "dark_green",
                    "dark_purple", "dark_red", "gold", "gray", "green", "light_purple",
                    "red", "white", "yellow", "#"
            ), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> suggestEnvArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("accent", "memreset", "history", "nudge"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("accent")) {
            String[] inner = new String[args.length - 1];
            System.arraycopy(args, 1, inner, 0, args.length - 1);
            return suggestAccentArgs(inner);
        }
        if (sub.equals("memreset")) {
            if (args.length == 2) {
                return filterPrefix(List.of("global"), args[1]);
            }
            return Collections.emptyList();
        }
        if (sub.equals("history")) {
            if (args.length == 2) {
                return filterPrefix(List.of("10", "25", "50", "100"), args[1]);
            }
            return Collections.emptyList();
        }
        if (sub.equals("nudge")) {
            String[] inner = new String[args.length];
            inner[0] = "nudge";
            if (args.length > 1) {
                System.arraycopy(args, 1, inner, 1, args.length - 1);
            }
            return suggestNudgeArgs(inner);
        }
        return Collections.emptyList();
    }

    private List<String> suggestUnstickArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("auto", "status"), args[0]);
        }
        return filterPrefix(List.of("on", "off", "status"), lastArg(args));
    }

    private List<String> suggestAlignArgs(String[] args) {
        return filterPrefix(List.of("north", "south", "east", "west"), lastArg(args));
    }

    private List<String> helpTopicSuggestions() {
        return List.of(
                "overview", "selection", "selection-tools", "visuals", "qol", "navigation", "editing", "clipboard",
                "flags", "keywords", "shapes", "tools", "generation", "cleanup", "palette", "nudge", "profile",
                "profiles", "kit", "kits", "runtime", "tabmenu", "brush", "brushes", "brushmenu", "detailbrush",
                "db", "variants", "saved", "undo-last", "paste-safety", "structure-brushes", "authority", "unsorted"
        );
    }

    private List<String> biomeSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (Biome biome : Biome.values()) {
            suggestions.add(biome.name().toLowerCase(Locale.ROOT));
        }
        return suggestions;
    }

    private List<String> forestTreeTypes() {
        List<String> suggestions = new ArrayList<>();
        for (org.bukkit.TreeType type : org.bukkit.TreeType.values()) {
            suggestions.add(type.name().toLowerCase(Locale.ROOT));
        }
        return suggestions;
    }

    private String lastArg(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }

    private String[] stripFirstArg(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] remapped = new String[args.length - 1];
        System.arraycopy(args, 1, remapped, 0, args.length - 1);
        return remapped;
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
                    "structure",
                    "clipboard", "paint", "spatter", "replace", "blend", "surface", "noise", "restore", "vegetation", "decay",
                    "gen",
                    "erase", "smooth", "raise", "lower", "flatten",
                    "floatingcleanup", "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup", "none", "info"
            ), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("structure")) {
                return filterPrefix(VanillaContentRegistry.structureIds(), args[1]);
            }
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
            if (args[0].equalsIgnoreCase("gen")) {
                return filterPrefix(genTypeNames(), args[1]);
            }
            if (args[0].equalsIgnoreCase("noise")) {
                // /brush noise <token> — offer both gen types (smart-dispatch) and blocks.
                List<String> merged = new ArrayList<>(genTypeNames());
                merged.addAll(SuggestionUtil.suggest("set", new String[]{args[1]}));
                return filterPrefix(merged, args[1]);
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
        if (args.length >= 3 && (args[0].equalsIgnoreCase("gen")
                || (args[0].equalsIgnoreCase("noise")
                && com.bayzyl.gen.GenBrushType.parse(args[1]) != null))) {
            return suggestGenBrushArgs(args, 1);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("replace")) {
            return SuggestionUtil.suggest("set", new String[]{args[2]});
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("replace")) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                return SuggestionUtil.maskSuggestions(current);
            }
            if (current.toLowerCase(Locale.ROOT).startsWith("at:")) {
                return filterPrefix(List.of("at:target", "at:selection-center"), current);
            }
            if (current.equalsIgnoreCase("confirm:true")) {
                return filterPrefix(List.of("confirm:true"), current);
            }
            return SuggestionUtil.suggest("set", new String[]{current});
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("paint")) {
            String current = args[args.length - 1];
            if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
                return SuggestionUtil.maskSuggestions(current);
            }
            return filterPrefix(List.of("size:5", "size:8", "size:12", "density:0.2", "density:0.3", "density:0.5", "density:0.8", "mask:"), current);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("structure")) {
            return SuggestionUtil.suggest("genstructure", new String[]{args[args.length - 1]});
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

    private WorkEstimate estimateBrush(ShapeBrushSettings settings) {
        return switch (settings.type()) {
            case SPHERE, HSPHERE -> shapeService.estimateSphere(new SphereRequest(
                    settings.distribution(),
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
            ));
            case CYL, HCYL -> shapeService.estimateCylinder(new CylinderRequest(
                    settings.distribution(),
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
                    settings.distribution(),
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
                    settings.type().displayName() + " r:" + settings.radiusX() + (settings.distribution().isSingleMaterial() ? "" : " " + settings.distribution());
            case CYL, HCYL ->
                    settings.type().displayName() + " r:" + settings.radiusX() + " h:" + settings.height() + (settings.distribution().isSingleMaterial() ? "" : " " + settings.distribution());
            case PYRAMID, HPYRAMID ->
                    settings.type().displayName() + " size:" + settings.size() + (settings.distribution().isSingleMaterial() ? "" : " " + settings.distribution());
        };
    }

    private List<String> suggestBzlArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (CommandSpec spec : CommandRegistry.getBzlSubcommands()) {
                suggestions.add(spec.name());
            }
            suggestions.addAll(filterPrefix(builderKitService.listLoadLabels(), args[0]));
            return filterPrefix(mergeSuggestions(suggestions), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] remapped = stripFirstArg(args);
        if (sub.equals("help")) {
            return suggestBzlHelpArgs(remapped);
        }
        if (sub.equals("selectionparticles")) {
            return suggestSelectionParticlesArgs(remapped);
        }
        if (sub.equals("audit")) {
            return SuggestionUtil.redstoneAuditSuggestions(remapped);
        }
        if (sub.equals("tool")) {
            return suggestToolArgs(remapped);
        }
        if (sub.equals("env")) {
            return suggestEnvArgs(remapped);
        }
        if (sub.equals("nudge")) {
            return suggestNudgeArgs(remapped);
        }
        if (sub.equals("authority") || sub.equals("commandauthority")) {
            return suggestCommandAuthorityArgs(remapped);
        }
        if (sub.equals("stacklook")) {
            return suggestToggleArgs(remapped);
        }
        if (sub.equals("stackautomove")) {
            return suggestToggleArgs(remapped);
        }
        if (sub.equals("ramalert")) {
            return suggestRamAlertArgs(remapped);
        }
        if (sub.equals("tabmenu")) {
            return suggestTabMenuArgs(remapped);
        }
        if (sub.equals("profile")) {
            return suggestProfileArgs(remapped);
        }
        if (sub.equals("kit")) {
            return suggestKitArgs(remapped);
        }
        if (sub.equals("kitmake")) {
            return suggestKitMakeArgs(remapped);
        }
        if (sub.equals("kitupdate")) {
            return suggestKitUpdateArgs(remapped);
        }
        if (sub.equals("kitconfirm")) {
            return Collections.emptyList();
        }
        if (sub.equals("cleanup")) {
            return suggestCleanupArgs(remapped);
        }
        if (sub.equals("palette")) {
            return suggestPaletteArgs(remapped);
        }
        if (sub.equals("brush")) {
            return suggestBrushRootArgs(remapped);
        }
        if (sub.equals("naturalize")) {
            return suggestNaturalizeArgs(remapped);
        }
        if (sub.equals("unstick")) {
            return suggestUnstickArgs(remapped);
        }
        if (sub.equals("select")) {
            return suggestSelectArgs(remapped);
        }
        if (builderKitService.existsKit(args[0])) {
            return Collections.emptyList();
        }
        return filterPrefix(CommandRegistry.getBzlSubcommands().stream().map(CommandSpec::name).toList(), args[0]);
    }

    private List<String> mergeSuggestions(List<String> suggestions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(suggestions);
        return new ArrayList<>(merged);
    }

    private record KitListPage(String title, List<BuilderKitSummary> kits) {
    }

    private record PendingKitUpdate(String kitName, long createdAtEpochMillis) {
    }

    private record JailSnapshot(UUID targetId, String targetName, Location returnLocation, GameMode previousGameMode,
                                boolean wasOp, Location jailCenter, List<JailBlockSnapshot> blockSnapshots) {
    }

    private record JailBlockSnapshot(int x, int y, int z, BlockState state) {
    }

    private record KitUpdateCheck(int missingStoredSlots, int extraFilledSlots) {
    }

    private record SchematicOrientationOptions(int rotation, String at, boolean preview) {
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

    private boolean parseBoolean(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must be true or false.");
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> throw new IllegalArgumentException(label + " must be true or false.");
        };
    }

    private static List<String> genTypeNames() {
        List<String> out = new ArrayList<>();
        for (com.bayzyl.gen.GenBrushType type : com.bayzyl.gen.GenBrushType.values()) {
            out.add(type.commandName());
        }
        return out;
    }

    private static List<String> caveSubtypeNames() {
        List<String> out = new ArrayList<>();
        for (com.bayzyl.gen.CaveSubtype subtype : com.bayzyl.gen.CaveSubtype.values()) {
            out.add(subtype.commandName());
        }
        return out;
    }

    private List<String> suggestBrushGenRoot(String[] args) {
        if (args.length <= 1) {
            return filterPrefix(genTypeNames(), args.length == 0 ? "" : args[0]);
        }
        com.bayzyl.gen.GenBrushType type = com.bayzyl.gen.GenBrushType.parse(args[0]);
        if (type == null) {
            return Collections.emptyList();
        }
        // Re-use the same arg suggester used inside /brush gen; positional offset is 0
        // because the type was consumed at index 0 of THIS array.
        return suggestGenBrushArgs(args, 0);
    }

    /**
     * Autocomplete for the tail of a /brush gen <type> or /brushgen <type>
     * invocation. {@code typeArgIndex} is the position of the type token
     * in {@code args}; everything after it is parameter territory.
     */
    private List<String> suggestGenBrushArgs(String[] args, int typeArgIndex) {
        if (args.length <= typeArgIndex + 1) {
            return Collections.emptyList();
        }
        com.bayzyl.gen.GenBrushType type = com.bayzyl.gen.GenBrushType.parse(args[typeArgIndex]);
        if (type == null) {
            return Collections.emptyList();
        }
        int relIdx = args.length - 1 - typeArgIndex; // 1-based position within the type's tail.
        String current = args[args.length - 1];

        // Position 1 (relIdx == 1): cave subtype for /brush gen cave, or first option for others.
        if (relIdx == 1) {
            if (type == com.bayzyl.gen.GenBrushType.CAVE) {
                List<String> merged = new ArrayList<>(caveSubtypeNames());
                merged.addAll(perTypeOptions(type));
                return filterPrefix(merged, current);
            }
            return filterPrefix(perTypeOptions(type), current);
        }
        // Any further position: options + mask blocks if mask: prefix.
        if (current.toLowerCase(Locale.ROOT).startsWith("mask:")) {
            return SuggestionUtil.maskSuggestions(current);
        }
        return filterPrefix(perTypeOptions(type), current);
    }

    private static List<String> perTypeOptions(com.bayzyl.gen.GenBrushType type) {
        List<String> base = new ArrayList<>(List.of(
                "size:5", "size:8", "size:12", "size:16",
                "intensity:0.5", "intensity:0.85",
                "mask:", "adapt:on", "adapt:off",
                "seed:", "confirm:true"
        ));
        switch (type) {
            case RIDGE -> base.addAll(List.of("height:8", "height:16", "steepness:0.6",
                    "crest:0.75", "warp:1.2", "blend:0.35"));
            case PLATEAU -> base.addAll(List.of("height:6", "height:12", "flatness:0.65",
                    "edges:smooth", "edges:cliff", "roughness:0.18", "blend:0.25"));
            case VALLEY -> base.addAll(List.of("depth:6", "depth:12", "width:0.7",
                    "meander:0.5", "water:auto", "water:on", "water:off", "river:false"));
            case BASIN -> base.addAll(List.of("depth:5", "flatness:0.5", "water:auto",
                    "water:on", "water:off", "roughness:0.15"));
            case ERODE -> base.addAll(List.of("passes:1", "passes:2", "aggression:0.6",
                    "cracks:0.35", "debris:true", "debris:false"));
            case RAVINE -> base.addAll(List.of("length:24", "depth:18", "jaggedness:0.55",
                    "bridges:0.25", "width:1.0",
                    "direction:north", "direction:south", "direction:east", "direction:west"));
            case CAVE -> base.addAll(List.of("vertical:6", "density:0.55", "frequency:0.12",
                    "verticality:0.35"));
            case DUNES -> base.addAll(List.of("height:5", "wavelength:8", "wavelength:14",
                    "direction:north", "direction:south", "direction:east", "direction:west"));
            case MESA -> base.addAll(List.of("height:10", "banding:0.85", "terraces:3", "blend:0.18"));
            case PEAK -> base.addAll(List.of("height:18", "sharpness:0.7", "snowline:0.7", "blend:0.25"));
            case CLIFF -> base.addAll(List.of("height:12", "steepness:0.85",
                    "direction:north", "direction:south", "direction:east", "direction:west",
                    "blend:0.15"));
            case BOULDER -> base.addAll(List.of("count:6", "radius:3", "cluster:0.4", "block:stone"));
            case SCREE -> base.addAll(List.of("density:0.65", "block:cobblestone"));
        }
        return base;
    }
}
