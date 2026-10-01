package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TabInfoPanelService {
    private static final int UPDATE_TICKS = 40;
    private static final int MAX_HISTORY = 180;
    private static final int PLAYERS_PER_COLUMN = 20;
    private static final int MAX_VANILLA_COLUMNS = 3;
    private static final int MIN_GRAPH_WIDTH = 5;
    private static final int MAX_GRAPH_WIDTH = 10;
    private static final int GRAPH_SPLIT_PERCENT = 50;
    private static final String BLOCKS = " ▁▂▃▄▅▆▇█";
    private static final int FIXED_PANEL_WIDTH = 10;
    private static final boolean EXPERIMENTAL_LAYOUT = true;

    // Footer layout controls. Adjust these values to move metrics or graph around.
    private static final int DIVIDER_ROW = 0;
    private static final int CURRENT_COLUMN = 0;
    private static final int PEAK_COLUMN = 0;
    private static final int USAGE_COLUMN = 0;
    private static final int CLIPBOARD_HEADER_COLUMN = 0;
    private static final int CLIPBOARD_DETAIL_COLUMN = 0;
    private static final int CLIPBOARD_RENDER_COLUMN = 0;
    private static final int SELECTION_COLUMN = 0;
    private static final int TRAIL_COLUMN = 0;
    private static final int GRAPH_COLUMN = 0;
    private static final int DIVIDER_EXTRA_WIDTH = 0;
    private static final int MIN_PANEL_CONTENT_WIDTH = 10;
    private static final int CLIPBOARD_RENDER_MIN_WIDTH = 4;
    private static final int CLIPBOARD_RENDER_MAX_WIDTH = 12;
    private static final int CLIPBOARD_LABEL_WIDTH = 2;
    private static final int SELECTION_RENDER_MAX_WIDTH = 12;
    private static final int SELECTION_LABEL_WIDTH = 2;
    private static final int TRAIL_MAX_ROWS = 4;
    private static final long SELECTION_EXACT_BLOCK_SCAN_LIMIT = 75_000L;
    private static final int SELECTION_SOLID_SAMPLE_LIMIT = 4096;
    private static final int SELECTION_PREVIEW_SAMPLE_LIMIT = 128;
    private static final ModuleStyle RAM_STYLE = new ModuleStyle(ChatColor.GOLD);
    private static final ModuleStyle CLIPBOARD_STYLE = new ModuleStyle(ChatColor.AQUA);
    private static final ModuleStyle SELECTION_STYLE = new ModuleStyle(ChatColor.GREEN);
    private static final ModuleStyle TRAIL_STYLE = new ModuleStyle(ChatColor.BLUE);

    private final JavaPlugin plugin;
    private final RamAlertService ramAlertService;
    private final TabMenuSettingsService tabMenuSettingsService;
    private final DecoyPlayerCountService decoyPlayerCountService;
    private final ClipboardManager clipboardManager;
    private final SelectionManager selectionManager;
    private final RecentEditTrailService recentEditTrailService;
    private final Map<UUID, PlayerPanelCache> panelCache = new ConcurrentHashMap<>();
    private final List<Integer> ramHistory = new ArrayList<>();
    private BukkitTask task;

    public TabInfoPanelService(JavaPlugin plugin,
                               RamAlertService ramAlertService,
                               TabMenuSettingsService tabMenuSettingsService,
                               DecoyPlayerCountService decoyPlayerCountService,
                               ClipboardManager clipboardManager,
                               SelectionManager selectionManager,
                               RecentEditTrailService recentEditTrailService) {
        this.plugin = plugin;
        this.ramAlertService = ramAlertService;
        this.tabMenuSettingsService = tabMenuSettingsService;
        this.decoyPlayerCountService = decoyPlayerCountService;
        this.clipboardManager = clipboardManager;
        this.selectionManager = selectionManager;
        this.recentEditTrailService = recentEditTrailService;
    }

    public void start() {
        stop();
        refreshAll();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, UPDATE_TICKS, UPDATE_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        panelCache.clear();
        clearAll();
    }

    public void refreshPlayer(Player player) {
        RamAlertService.RamSnapshot snapshot = ramAlertService.currentSnapshot();
        recordSample(snapshot.usedPercent());
        player.setPlayerListFooter(renderFooter(player, snapshot));
    }

    public void refreshAllNow() {
        refreshAll();
    }

    public void clearPlayer(UUID playerId) {
        if (playerId != null) {
            panelCache.remove(playerId);
        }
    }

    private ClipboardSummary cachedClipboardSummary(Player player) {
        UUID playerId = player.getUniqueId();
        long revision = clipboardManager.revision(playerId);
        PlayerPanelCache cache = panelCache.computeIfAbsent(playerId, ignored -> new PlayerPanelCache());
        if (cache.clipboardRevision == revision && cache.clipboardSummary != null) {
            return cache.clipboardSummary;
        }
        ClipboardSummary summary = buildClipboardSummary(player);
        cache.clipboardRevision = revision;
        cache.clipboardSummary = summary;
        cache.clipboardPanel = null;
        cache.clipboardPanelScale = Integer.MIN_VALUE;
        return summary;
    }

    private ClipboardPanel cachedClipboardPanel(Player player, int scale, ClipboardSummary summary) {
        UUID playerId = player.getUniqueId();
        long revision = clipboardManager.revision(playerId);
        PlayerPanelCache cache = panelCache.computeIfAbsent(playerId, ignored -> new PlayerPanelCache());
        if (cache.clipboardRevision == revision && cache.clipboardPanel != null && cache.clipboardPanelScale == scale) {
            return cache.clipboardPanel;
        }
        int desiredWidth = CLIPBOARD_RENDER_COLUMN + CLIPBOARD_LABEL_WIDTH + desiredClipboardRenderWidth(summary, scale);
        ClipboardPanel panel = renderClipboardPanel(summary, desiredWidth, scale);
        cache.clipboardRevision = revision;
        cache.clipboardSummary = summary;
        cache.clipboardPanel = panel;
        cache.clipboardPanelScale = scale;
        return panel;
    }

    private SelectionPanel cachedSelectionPanel(Player player, int scale) {
        UUID playerId = player.getUniqueId();
        long revision = selectionManager.revision(playerId);
        long globalRevision = selectionManager.revision();
        PlayerPanelCache cache = panelCache.computeIfAbsent(playerId, ignored -> new PlayerPanelCache());
        if (cache.selectionRevision == revision && cache.selectionGlobalRevision == globalRevision && cache.selectionPanel != null) {
            return cache.selectionPanel;
        }
        SelectionPanel panel = buildSelectionPanel(player, scale);
        cache.selectionRevision = revision;
        cache.selectionGlobalRevision = globalRevision;
        cache.selectionPanel = panel;
        return panel;
    }

    private TrailPanel cachedTrailPanel(Player player, int scale) {
        UUID playerId = player.getUniqueId();
        long revision = recentEditTrailService.revision(playerId);
        int limit = recentEditTrailService.limit(playerId);
        PlayerPanelCache cache = panelCache.computeIfAbsent(playerId, ignored -> new PlayerPanelCache());
        if (cache.trailRevision == revision && cache.trailLimit == limit && cache.trailScale == scale && cache.trailPanel != null) {
            return cache.trailPanel;
        }
        TrailPanel panel = buildTrailPanel(player, scale);
        cache.trailRevision = revision;
        cache.trailLimit = limit;
        cache.trailScale = scale;
        cache.trailPanel = panel;
        return panel;
    }

    private void refreshAll() {
        RamAlertService.RamSnapshot snapshot = ramAlertService.currentSnapshot();
        recordSample(snapshot.usedPercent());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListFooter(renderFooter(player, snapshot));
        }
    }

    private void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListFooter("");
        }
    }

    private void recordSample(int usedPercent) {
        ramHistory.add(Math.max(0, Math.min(100, usedPercent)));
        if (ramHistory.size() > MAX_HISTORY) {
            ramHistory.removeFirst();
        }
    }

    private String renderFooter(Player player, RamAlertService.RamSnapshot snapshot) {
        if (EXPERIMENTAL_LAYOUT) {
            return renderFooterExperimental(player, snapshot);
        }

        return renderFooterBaseline(player, snapshot);
    }

    private String renderFooterBaseline(Player player, RamAlertService.RamSnapshot snapshot) {
        if (!tabMenuSettingsService.hasAnyEnabled(player.getUniqueId())) {
            return "";
        }

        RamPanelMetrics metrics = buildRamMetrics(snapshot);
        int scale = playerListColumnScale();
        ClipboardSummary clipboardSummary = cachedClipboardSummary(player);
        SelectionPanel selectionPanel = cachedSelectionPanel(player, scale);
        TrailPanel trailPanel = cachedTrailPanel(player, scale);
        FooterLayout layout = buildLayout(player, metrics, clipboardSummary, selectionPanel, trailPanel, scale);
        ClipboardPanel clipboardPanel = layout.showClipboard()
                ? cachedClipboardPanel(player, scale, clipboardSummary)
                : ClipboardPanel.empty();
        GraphRows graph = layout.showRam()
                ? renderGraph(layout.graphWidth(), metrics.mediumStart(), metrics.threshold())
                : null;
        int totalRows = 1 + (layout.showRam() ? 5 : 0) + (layout.showClipboard() ? clipboardPanel.rowCount() : 0)
                + (layout.showSelection() ? selectionPanel.rowCount() : 0)
                + (layout.showTrail() ? trailPanel.rowCount() : 0);
        TextCanvas canvas = new TextCanvas(layout.totalWidth(), totalRows);

        canvas.placeText(DIVIDER_ROW, 0,
                ChatColor.DARK_GRAY + repeat('─', Math.max(MIN_PANEL_CONTENT_WIDTH, layout.totalWidth() + DIVIDER_EXTRA_WIDTH)));
        int row = 1;
        if (layout.showRam()) {
            canvas.placeText(row++, CURRENT_COLUMN, metrics.current());
            canvas.placeText(row++, PEAK_COLUMN, metrics.peak());
            canvas.placeText(row++, USAGE_COLUMN, metrics.usage());
            canvas.placeText(row++, GRAPH_COLUMN, graph.topRow());
            canvas.placeText(row++, GRAPH_COLUMN, graph.bottomRow());
        }
        if (layout.showClipboard()) {
            canvas.placeText(row++, CLIPBOARD_HEADER_COLUMN, clipboardPanel.header());
            canvas.placeText(row++, CLIPBOARD_DETAIL_COLUMN, clipboardPanel.detail());
            for (String renderRow : clipboardPanel.renderRows()) {
                canvas.placeText(row++, CLIPBOARD_RENDER_COLUMN, renderRow);
            }
            if (clipboardPanel.footer() != null && !clipboardPanel.footer().isEmpty()) {
                canvas.placeText(row, CLIPBOARD_DETAIL_COLUMN, clipboardPanel.footer());
                row++;
            }
        }
        if (layout.showSelection()) {
            for (String selectionRow : selectionPanel.rows()) {
                canvas.placeText(row++, SELECTION_COLUMN, selectionRow);
            }
        }
        if (layout.showTrail()) {
            for (String trailRow : trailPanel.rows()) {
                canvas.placeText(row++, TRAIL_COLUMN, trailRow);
            }
        }

        return canvas.render();
    }

    private String renderFooterExperimental(Player player, RamAlertService.RamSnapshot snapshot) {
        if (!tabMenuSettingsService.hasAnyEnabled(player.getUniqueId())) {
            return "";
        }

        RamPanelMetrics metrics = buildRamMetrics(snapshot);
        int scale = playerListColumnScale();
        ClipboardSummary clipboardSummary = cachedClipboardSummary(player);
        SelectionPanel selectionPanel = cachedSelectionPanel(player, scale);
        TrailPanel trailPanel = cachedTrailPanel(player, scale);
        FooterLayout layout = buildLayout(player, metrics, clipboardSummary, selectionPanel, trailPanel, scale);
        ClipboardPanel clipboardPanel = layout.showClipboard()
                ? cachedClipboardPanel(player, scale, clipboardSummary)
                : ClipboardPanel.empty();
        GraphRows graph = layout.showRam()
                ? renderGraph(layout.graphWidth(), metrics.mediumStart(), metrics.threshold())
                : null;

        List<String> rows = new ArrayList<>();
        rows.add(subtleDivider(layout.totalWidth()));

        if (layout.showRam()) {
            rows.addAll(List.of(metrics.current(), metrics.peak(), metrics.usage(), graph.topRow(), graph.bottomRow()));
        }
        if (layout.showClipboard()) {
            if (!rows.isEmpty()) {
                rows.add(subtleDivider(layout.totalWidth()));
            }
            rows.add(clipboardPanel.header());
            rows.add(clipboardPanel.detail());
            rows.addAll(clipboardPanel.renderRows());
            if (clipboardPanel.footer() != null && !clipboardPanel.footer().isEmpty()) {
                rows.add(clipboardPanel.footer());
            }
        }
        if (layout.showSelection()) {
            if (!rows.isEmpty()) {
                rows.add(subtleDivider(layout.totalWidth()));
            }
            rows.addAll(selectionPanel.rows());
        }
        if (layout.showTrail()) {
            if (!rows.isEmpty()) {
                rows.add(subtleDivider(layout.totalWidth()));
            }
            rows.addAll(trailPanel.rows());
        }

        return String.join("\n", rows);
    }

    private RamPanelMetrics buildRamMetrics(RamAlertService.RamSnapshot snapshot) {
        int threshold = ramAlertService.getSettings().thresholdPercent();
        int mediumStart = mediumStart(threshold);
        int peak = ramHistory.stream().mapToInt(Integer::intValue).max().orElse(snapshot.usedPercent());
        String current = RAM_STYLE.accent() + "Usage: " + colorFor(snapshot.usedPercent(), mediumStart, threshold) + snapshot.usedPercent() + "%";
        String peakLine = RAM_STYLE.accent() + "Peak: " + colorFor(peak, mediumStart, threshold) + peak + "%";
        String usageLine = ChatColor.WHITE + formatMemory(snapshot.usedBytes()) + ChatColor.DARK_GRAY + "/"
                + ChatColor.WHITE + formatMemory(snapshot.maxBytes());
        return new RamPanelMetrics(
                current,
                peakLine,
                usageLine,
                visibleLength(current),
                visibleLength(peakLine),
                visibleLength(usageLine),
                mediumStart,
                threshold
        );
    }

    private ClipboardSummary buildClipboardSummary(Player player) {
        Clipboard clipboard = clipboardManager.get(player.getUniqueId());
        if (clipboard == null) {
            String header = CLIPBOARD_STYLE.accent() + "Clipboard";
            String detail = ChatColor.WHITE + "Empty";
            return new ClipboardSummary(
                    null,
                    header,
                    detail,
                    visibleLength(header),
                    visibleLength(detail)
            );
        }
        int sizeX = clipboard.getSizeX();
        int sizeY = clipboard.getSizeY();
        int sizeZ = clipboard.getSizeZ();
        long blockCount = (long) sizeX * sizeY * sizeZ;
        String header = CLIPBOARD_STYLE.accent() + "Clipboard";
        String originState = clipboard.getOrigin() == null ? "origin unset" : "origin set";
        String detail = ChatColor.WHITE.toString() + sizeX + "x" + sizeY + "x" + sizeZ
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + formatBlockCount(blockCount) + " blocks"
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + originState;
        return new ClipboardSummary(
                clipboard,
                header,
                detail,
                visibleLength(header),
                visibleLength(detail)
        );
    }

    private ClipboardPanel renderClipboardPanel(ClipboardSummary summary, int totalWidth, int scale) {
        if (summary.clipboard() == null) {
            return new ClipboardPanel(summary.header(), summary.detail(), List.of(), "");
        }

        Clipboard clipboard = summary.clipboard();
        int renderWidth = Math.max(CLIPBOARD_RENDER_MIN_WIDTH,
                Math.min(totalWidth - CLIPBOARD_RENDER_COLUMN - CLIPBOARD_LABEL_WIDTH, desiredClipboardRenderWidth(summary, scale)));
        int renderHeightLimit = 3 + (scale * 2);
        int renderColumns = Math.max(1, Math.min(renderWidth, clipboard.getSizeX()));
        int renderRows = Math.max(1, Math.min(renderHeightLimit, clipboard.getSizeZ()));

        List<String> rows = new ArrayList<>(renderRows);
        for (int renderZ = 0; renderZ < renderRows; renderZ++) {
            int zStart = sampleStart(renderZ, renderRows, clipboard.getSizeZ());
            int zEnd = sampleEnd(renderZ, renderRows, clipboard.getSizeZ());
            StringBuilder line = new StringBuilder(totalWidth * 2);
            line.append(clipboardDepthLabel(zStart, zEnd));
            for (int renderX = 0; renderX < renderColumns; renderX++) {
                int xStart = sampleStart(renderX, renderColumns, clipboard.getSizeX());
                int xEnd = sampleEnd(renderX, renderColumns, clipboard.getSizeX());
                line.append(renderSample(clipboard, xStart, xEnd, zStart, zEnd));
            }
            rows.add(line.toString());
        }

        int xScale = sampleSpan(clipboard.getSizeX(), renderColumns);
        int zScale = sampleSpan(clipboard.getSizeZ(), renderRows);
        String footer = ChatColor.DARK_GRAY + "sample " + ChatColor.WHITE + xScale + "x" + zScale + ChatColor.DARK_GRAY + " px";
        return new ClipboardPanel(summary.header(), summary.detail(), rows, footer);
    }

    private SelectionPanel buildSelectionPanel(Player player, int scale) {
        if (EXPERIMENTAL_LAYOUT) {
            return buildSelectionPanelExperimental(player);
        }
        return buildSelectionPanelBaseline(player);
    }

    private SelectionPanel buildSelectionPanelBaseline(Player player) {
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || (selection.getPos1() == null && selection.getPos2() == null)) {
            String text = ChatColor.WHITE + "Selection: " + ChatColor.DARK_GRAY + "empty";
            return new SelectionPanel(List.of(text), visibleLength(text));
        }
        if (!selection.isComplete()) {
            String text = ChatColor.WHITE + "Selection: " + ChatColor.YELLOW + "incomplete"
                    + ChatColor.DARK_GRAY + " | "
                    + ChatColor.GOLD + missingSelectionCorner(selection);
            return new SelectionPanel(List.of(text), visibleLength(text));
        }
        long sizeX = (long) selection.getMaxX() - selection.getMinX() + 1;
        long sizeY = (long) selection.getMaxY() - selection.getMinY() + 1;
        long sizeZ = (long) selection.getMaxZ() - selection.getMinZ() + 1;
        long totalBlocks = sizeX * sizeY * sizeZ;
        BlockCountEstimate nonAirBlocks = countNonAirBlocks(selection, totalBlocks);
        SelectionConflict conflict = findSelectionConflict(player.getUniqueId(), selection);
        List<String> rows = List.of(
                ChatColor.WHITE + "Selection: " + ChatColor.WHITE + sizeX + "x" + sizeY + "x" + sizeZ,
                renderSelectionBlockLine(totalBlocks, nonAirBlocks),
                renderSelectionConflictLine(conflict)
        );
        return new SelectionPanel(rows, rows.stream().mapToInt(this::visibleLength).max().orElse(0));
    }

    private SelectionPanel buildSelectionPanelExperimental(Player player) {
        Selection selection = selectionManager.get(player.getUniqueId());
        if (selection == null || (selection.getPos1() == null && selection.getPos2() == null)) {
            List<String> rows = List.of(
                    SELECTION_STYLE.accent() + "Selection",
                    ChatColor.WHITE + "  " + ChatColor.DARK_GRAY + "empty"
            );
            return new SelectionPanel(rows, rows.stream().mapToInt(this::visibleLength).max().orElse(0));
        }
        if (!selection.isComplete()) {
            List<String> rows = List.of(
                    SELECTION_STYLE.accent() + "Selection",
                    ChatColor.WHITE + "  " + ChatColor.YELLOW + "incomplete"
                            + ChatColor.DARK_GRAY + " | "
                            + ChatColor.GOLD + missingSelectionCorner(selection)
            );
            return new SelectionPanel(rows, rows.stream().mapToInt(this::visibleLength).max().orElse(0));
        }
        long sizeX = (long) selection.getMaxX() - selection.getMinX() + 1;
        long sizeY = (long) selection.getMaxY() - selection.getMinY() + 1;
        long sizeZ = (long) selection.getMaxZ() - selection.getMinZ() + 1;
        long totalBlocks = sizeX * sizeY * sizeZ;
        BlockCountEstimate nonAirBlocks = countNonAirBlocks(selection, totalBlocks);
        SelectionConflict conflict = findSelectionConflict(player.getUniqueId(), selection);
        List<String> rows = new ArrayList<>();
        rows.add(SELECTION_STYLE.accent() + "Selection");
        rows.add(ChatColor.WHITE + "  Size: " + ChatColor.WHITE + sizeX + "x" + sizeY + "x" + sizeZ);
        rows.add(renderSelectionBlockLine(totalBlocks, nonAirBlocks));
        rows.add(renderSelectionConflictLine(conflict));
        return new SelectionPanel(rows, rows.stream().mapToInt(this::visibleLength).max().orElse(0));
    }

    private TrailPanel buildTrailPanel(Player player, int scale) {
        if (EXPERIMENTAL_LAYOUT) {
            return buildTrailPanelExperimental(player, scale);
        }
        return buildTrailPanelBaseline(player, scale);
    }

    private TrailPanel buildTrailPanelBaseline(Player player, int scale) {
        List<String> recent = recentEditTrailService.recent(player.getUniqueId());
        if (recent.isEmpty()) {
            return new TrailPanel(
                    List.of(
                            ChatColor.WHITE + "Recent trail",
                            ChatColor.WHITE + "  " + ChatColor.DARK_GRAY + "none"
                    ),
                    visibleLength("Recent trail")
            );
        }

        int maxRows = Math.max(1, Math.min(TRAIL_MAX_ROWS + Math.max(0, scale - 1), recent.size()));
        List<String> rows = new ArrayList<>(maxRows + 2);
        rows.add(ChatColor.WHITE + "Recent trail");
        for (int i = 0; i < maxRows; i++) {
            String prefix = "  " + (i + 1) + ". ";
            String line = ChatColor.WHITE + prefix + ChatColor.WHITE + compactTrailEntry(recent.get(i));
            rows.add(line);
        }
        if (recent.size() > maxRows) {
            int hidden = recent.size() - maxRows;
            rows.add(ChatColor.DARK_GRAY + " +" + hidden + " more");
        }
        int width = rows.stream()
                .mapToInt(this::visibleLength)
                .max()
                .orElse(0);
        return new TrailPanel(rows, width);
    }

    private TrailPanel buildTrailPanelExperimental(Player player, int scale) {
        List<String> recent = recentEditTrailService.recent(player.getUniqueId());
        if (recent.isEmpty()) {
            List<String> rows = List.of(
                    TRAIL_STYLE.accent() + "Recent trail",
                    ChatColor.WHITE + "  " + ChatColor.DARK_GRAY + "none"
            );
            return new TrailPanel(rows, rows.stream().mapToInt(this::visibleLength).max().orElse(0));
        }

        int maxRows = Math.max(1, Math.min(TRAIL_MAX_ROWS + Math.max(0, scale - 1), recent.size()));
        List<String> rows = new ArrayList<>(maxRows + 2);
        rows.add(TRAIL_STYLE.accent() + "Recent trail");
        for (int i = 0; i < maxRows; i++) {
            rows.add(ChatColor.WHITE + "  " + ChatColor.WHITE + compactTrailEntry(recent.get(i)));
        }
        if (recent.size() > maxRows) {
            rows.add(ChatColor.DARK_GRAY + "  +" + (recent.size() - maxRows) + " more");
        }
        return new TrailPanel(rows, rows.stream().mapToInt(this::visibleLength).max().orElse(0));
    }

    private FooterLayout buildLayout(Player player, RamPanelMetrics metrics, ClipboardSummary clipboardSummary, SelectionPanel selectionPanel, TrailPanel trailPanel, int scale) {
        boolean showRam = tabMenuSettingsService.isEnabled(player.getUniqueId(), TabMenuModule.RAM);
        boolean showClipboard = tabMenuSettingsService.isEnabled(player.getUniqueId(), TabMenuModule.CLIPBOARD);
        boolean showSelection = tabMenuSettingsService.isEnabled(player.getUniqueId(), TabMenuModule.SELECTION);
        boolean showTrail = tabMenuSettingsService.isEnabled(player.getUniqueId(), TabMenuModule.TRAIL);
        int contentWidth = 0;
        if (showRam) {
            contentWidth = Math.max(
                CURRENT_COLUMN + metrics.currentWidth(),
                    Math.max(
                            PEAK_COLUMN + metrics.peakWidth(),
                            USAGE_COLUMN + metrics.usageWidth()
                    )
            );
        }
        if (showClipboard) {
            contentWidth = Math.max(
                    contentWidth,
                    Math.max(
                            CLIPBOARD_HEADER_COLUMN + clipboardSummary.headerWidth(),
                            Math.max(
                                    CLIPBOARD_DETAIL_COLUMN + clipboardSummary.detailWidth(),
                                    CLIPBOARD_RENDER_COLUMN + CLIPBOARD_LABEL_WIDTH + desiredClipboardRenderWidth(clipboardSummary, scale)
                            )
                    )
            );
        }
        if (showSelection) {
            contentWidth = Math.max(contentWidth, SELECTION_COLUMN + selectionPanel.width());
        }
        if (showTrail) {
            contentWidth = Math.max(contentWidth, TRAIL_COLUMN + trailPanel.width());
        }
        int scaledFixedWidth = FIXED_PANEL_WIDTH * scale;
        int scaledMinWidth = MIN_PANEL_CONTENT_WIDTH * scale;
        int scaledMinGraphWidth = MIN_GRAPH_WIDTH * scale;
        int scaledMaxGraphWidth = MAX_GRAPH_WIDTH * scale;
        int totalWidth = Math.max(scaledFixedWidth, Math.max(scaledMinWidth, contentWidth));
        int graphWidth = showRam
                ? Math.max(scaledMinGraphWidth, Math.min(scaledMaxGraphWidth, totalWidth - GRAPH_COLUMN))
                : 0;
        return new FooterLayout(totalWidth, graphWidth, showRam, showClipboard, showSelection, showTrail);
    }

    private GraphRows renderGraph(int width, int mediumStart, int threshold) {
        StringBuilder top = new StringBuilder(width * 3);
        StringBuilder bottom = new StringBuilder(width * 3);
        List<Integer> values = rollingWindow(width);
        int padding = Math.max(0, width - values.size());
        for (int i = 0; i < padding; i++) {
            top.append(ChatColor.DARK_GRAY).append('·');
            bottom.append(ChatColor.DARK_GRAY).append('·');
        }
        for (int value : values) {
            ChatColor color = colorFor(value, mediumStart, threshold);
            top.append(color).append(blockFor(upperBandLevel(value)));
            bottom.append(color).append(blockFor(lowerBandLevel(value)));
        }
        return new GraphRows(top.toString(), bottom.toString());
    }

    private List<Integer> rollingWindow(int width) {
        if (ramHistory.isEmpty()) {
            return List.of();
        }
        int size = ramHistory.size();
        if (size <= width) {
            return List.copyOf(ramHistory);
        }
        return List.copyOf(ramHistory.subList(size - width, size));
    }

    private int playerListColumnScale() {
        int onlinePlayers = Math.max(1, decoyPlayerCountService.effectiveOnlinePlayers());
        return Math.max(1, Math.min(MAX_VANILLA_COLUMNS, (onlinePlayers + PLAYERS_PER_COLUMN - 1) / PLAYERS_PER_COLUMN));
    }

    private int mediumStart(int threshold) {
        return Math.max(65, threshold - 20);
    }

    private int visibleLength(String text) {
        String stripped = ChatColor.stripColor(text);
        return stripped == null ? 0 : stripped.length();
    }

    private String repeat(char value, int count) {
        return String.valueOf(value).repeat(Math.max(0, count));
    }

    private String subtleDivider(int width) {
        return ChatColor.DARK_GRAY + repeat('·', Math.max(MIN_PANEL_CONTENT_WIDTH, width));
    }

    private String formatMemory(long bytes) {
        if (bytes <= 0L) {
            return "0MB";
        }
        double megabytes = bytes / 1024.0D / 1024.0D;
        if (megabytes >= 1024.0D) {
            return String.format(Locale.ROOT, "%.1fGB", megabytes / 1024.0D);
        }
        return String.format(Locale.ROOT, "%.0fMB", megabytes);
    }

    private String formatBlockCount(long blocks) {
        if (blocks >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", blocks / 1_000_000.0D);
        }
        if (blocks >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", blocks / 1_000.0D);
        }
        return Long.toString(blocks);
    }

    private String renderSample(Clipboard clipboard, int xStart, int xEnd, int zStart, int zEnd) {
        int highestFilledY = highestClipboardY(clipboard, xStart, xEnd, zStart, zEnd);
        if (highestFilledY < 0) {
            return "" + ChatColor.DARK_GRAY + '░';
        }
        int yValue = highestFilledY + 1;
        return "" + elevationColor(yValue, 1, clipboard.getSizeY()) + '█';
    }

    private int sampleStart(int index, int buckets, int sourceSize) {
        return Math.min(sourceSize - 1, Math.max(0, (int) Math.floor((index * sourceSize) / (double) buckets)));
    }

    private int sampleEnd(int index, int buckets, int sourceSize) {
        int end = (int) Math.floor(((index + 1) * sourceSize) / (double) buckets) - 1;
        return Math.max(sampleStart(index, buckets, sourceSize), Math.min(sourceSize - 1, end));
    }

    private int sampleSpan(int sourceSize, int buckets) {
        return Math.max(1, (int) Math.ceil(sourceSize / (double) Math.max(1, buckets)));
    }

    private int desiredClipboardRenderWidth(ClipboardSummary summary, int scale) {
        if (summary.clipboard() == null) {
            return CLIPBOARD_RENDER_MIN_WIDTH;
        }
        int maxWidth = CLIPBOARD_RENDER_MAX_WIDTH * Math.max(1, scale);
        return Math.max(CLIPBOARD_RENDER_MIN_WIDTH, Math.min(summary.clipboard().getSizeX(), maxWidth));
    }

    private String clipboardDepthLabel(int zStart, int zEnd) {
        int lowest = Math.max(1, zStart + 1);
        int highest = Math.max(lowest, zEnd + 1);
        if (lowest == highest) {
            return ChatColor.DARK_GRAY + String.format(Locale.ROOT, "%2d", lowest);
        }
        return ChatColor.DARK_GRAY + Integer.toString(lowest % 10) + Integer.toString(highest % 10);
    }

    private String missingSelectionCorner(Selection selection) {
        if (selection.getPos1() == null && selection.getPos2() == null) {
            return "pos1 pos2";
        }
        if (selection.getPos1() == null) {
            return "missing pos1";
        }
        if (selection.getPos2() == null) {
            return "missing pos2";
        }
        return "world mismatch";
    }

    private String compactTrailEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return "";
        }
        String normalized = entry.trim().replaceAll("\\s+", " ");
        int split = normalized.indexOf(' ');
        String verb = split < 0 ? normalized : normalized.substring(0, split);
        String args = split < 0 ? "" : normalized.substring(split + 1).trim();
        String compact = switch (verb) {
            case "rotate" -> args.startsWith("live ")
                    ? "rot live " + args.substring(5)
                    : "rot " + args;
            case "replace" -> args.isEmpty() ? "repl" : "repl " + args;
            default -> normalized;
        };
        if (compact.length() > 24) {
            return compact.substring(0, 21).trim() + "...";
        }
        return compact;
    }

    private String renderSelectionConflictLine(SelectionConflict conflict) {
        if (conflict == null) {
            return ChatColor.WHITE + "  Overlap: " + ChatColor.GREEN + "none";
        }
        return ChatColor.WHITE + "  Overlap: " + ChatColor.RED + conflict.playerName();
    }

    private List<String> renderSelectionPreview(Selection selection, int scale) {
        int sourceX = (int) ((long) selection.getMaxX() - selection.getMinX() + 1);
        int sourceZ = (int) ((long) selection.getMaxZ() - selection.getMinZ() + 1);
        int renderColumns = Math.max(1, Math.min(sourceX, SELECTION_RENDER_MAX_WIDTH * Math.max(1, scale)));
        int renderRows = Math.max(1, Math.min(sourceZ, 3 + (scale * 2)));
        List<String> rows = new ArrayList<>(renderRows + 1);
        World world = selection.getPos1().getWorld();
        int minY = selection.getMinY();
        int maxY = selection.getMaxY();
        for (int renderZ = 0; renderZ < renderRows; renderZ++) {
            int zStart = selection.getMinZ() + sampleStart(renderZ, renderRows, sourceZ);
            int zEnd = selection.getMinZ() + sampleEnd(renderZ, renderRows, sourceZ);
            StringBuilder line = new StringBuilder();
            line.append(selectionDepthLabel(zStart - selection.getMinZ(), zEnd - selection.getMinZ()));
            for (int renderX = 0; renderX < renderColumns; renderX++) {
                int xStart = selection.getMinX() + sampleStart(renderX, renderColumns, sourceX);
                int xEnd = selection.getMinX() + sampleEnd(renderX, renderColumns, sourceX);
                line.append(renderSelectionSample(world, xStart, xEnd, zStart, zEnd, minY, maxY));
            }
            rows.add(line.toString());
        }
        int xScale = sampleSpan(sourceX, renderColumns);
        int zScale = sampleSpan(sourceZ, renderRows);
        rows.add(ChatColor.DARK_GRAY + "X " + ChatColor.WHITE + xScale + "x" + zScale + "/px");
        return rows;
    }

    private String renderSelectionSample(World world, int xStart, int xEnd, int zStart, int zEnd, int minY, int maxY) {
        int highestY = highestWorldY(world, xStart, xEnd, zStart, zEnd, minY, maxY);
        if (highestY < 0) {
            return "" + ChatColor.DARK_GRAY + '░';
        }
        return "" + elevationColor(highestY, minY, maxY) + '█';
    }

    private int highestClipboardY(Clipboard clipboard, int xStart, int xEnd, int zStart, int zEnd) {
        int highestFilledY = -1;
        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = zStart; z <= zEnd; z++) {
                for (int x = xStart; x <= xEnd; x++) {
                    var data = clipboard.get(x, y, z);
                    if (data == null || data.getMaterial().isAir()) {
                        continue;
                    }
                    highestFilledY = Math.max(highestFilledY, y);
                }
            }
        }
        return highestFilledY;
    }

    private int highestWorldY(World world, int xStart, int xEnd, int zStart, int zEnd, int minY, int maxY) {
        long sampleVolume = (long) (xEnd - xStart + 1) * (maxY - minY + 1) * (zEnd - zStart + 1);
        if (sampleVolume > SELECTION_PREVIEW_SAMPLE_LIMIT) {
            return sampledHighestWorldY(world, xStart, xEnd, zStart, zEnd, minY, maxY, sampleVolume);
        }
        int highestFilledY = -1;
        for (int y = minY; y <= maxY; y++) {
            for (int z = zStart; z <= zEnd; z++) {
                for (int x = xStart; x <= xEnd; x++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        highestFilledY = Math.max(highestFilledY, y);
                    }
                }
            }
        }
        return highestFilledY;
    }

    private int sampledHighestWorldY(World world, int xStart, int xEnd, int zStart, int zEnd, int minY, int maxY, long volume) {
        int sizeX = xEnd - xStart + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = zEnd - zStart + 1;
        long stride = Math.max(1L, (long) Math.ceil(volume / (double) SELECTION_PREVIEW_SAMPLE_LIMIT));
        int highestFilledY = -1;
        for (long index = 0L; index < volume; index += stride) {
            int x = (int) (index % sizeX);
            long yz = index / sizeX;
            int z = (int) (yz % sizeZ);
            int y = (int) (yz / sizeZ);
            if (!world.getBlockAt(xStart + x, minY + y, zStart + z).getType().isAir()) {
                highestFilledY = Math.max(highestFilledY, minY + y);
            }
        }
        return highestFilledY;
    }

    private String selectionDepthLabel(int zStart, int zEnd) {
        int lowest = Math.max(1, zStart + 1);
        int highest = Math.max(lowest, zEnd + 1);
        if (lowest == highest) {
            return ChatColor.DARK_GRAY + String.format(Locale.ROOT, "%2d", lowest);
        }
        return ChatColor.DARK_GRAY + Integer.toString(lowest % 10) + Integer.toString(highest % 10);
    }

    private SelectionConflict findSelectionConflict(UUID playerId, Selection selection) {
        List<SelectionConflict> conflicts = new ArrayList<>();
        for (Map.Entry<UUID, Selection> entry : selectionManager.snapshot().entrySet()) {
            if (entry.getKey().equals(playerId)) {
                continue;
            }
            Selection other = entry.getValue();
            if (other == null || !other.isComplete() || !selectionsOverlap(selection, other)) {
                continue;
            }
            Player otherPlayer = Bukkit.getPlayer(entry.getKey());
            String name = otherPlayer != null ? otherPlayer.getName() : "other";
            conflicts.add(new SelectionConflict(name));
        }
        return conflicts.stream()
                .sorted(Comparator.comparing(SelectionConflict::playerName, String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .orElse(null);
    }

    private BlockCountEstimate countNonAirBlocks(Selection selection, long totalBlocks) {
        if (!selection.isComplete() || selection.getPos1() == null || selection.getPos1().getWorld() == null) {
            return new BlockCountEstimate(0L, false);
        }
        World world = selection.getPos1().getWorld();
        if (totalBlocks > SELECTION_EXACT_BLOCK_SCAN_LIMIT) {
            return sampledNonAirBlocks(selection, world, totalBlocks);
        }
        long count = 0L;
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        count++;
                    }
                }
            }
        }
        return new BlockCountEstimate(count, false);
    }

    private BlockCountEstimate sampledNonAirBlocks(Selection selection, World world, long totalBlocks) {
        int sizeX = selection.getMaxX() - selection.getMinX() + 1;
        int sizeY = selection.getMaxY() - selection.getMinY() + 1;
        int sizeZ = selection.getMaxZ() - selection.getMinZ() + 1;
        long stride = Math.max(1L, (long) Math.ceil(totalBlocks / (double) SELECTION_SOLID_SAMPLE_LIMIT));
        long sampled = 0L;
        long nonAir = 0L;
        for (long index = 0L; index < totalBlocks; index += stride) {
            int x = (int) (index % sizeX);
            long yz = index / sizeX;
            int z = (int) (yz % sizeZ);
            int y = (int) (yz / sizeZ);
            if (!world.getBlockAt(selection.getMinX() + x, selection.getMinY() + y, selection.getMinZ() + z).getType().isAir()) {
                nonAir++;
            }
            sampled++;
        }
        long estimate = sampled == 0L ? 0L : Math.round(nonAir * (totalBlocks / (double) sampled));
        return new BlockCountEstimate(Math.max(0L, Math.min(totalBlocks, estimate)), true);
    }

    private String renderSelectionBlockLine(long totalBlocks, BlockCountEstimate nonAirBlocks) {
        long airBlocks = Math.max(0L, totalBlocks - nonAirBlocks.count());
        String prefix = nonAirBlocks.approximate() ? "~" : "";
        return ChatColor.WHITE + "  Solid: " + ChatColor.WHITE + prefix + formatBlockCount(nonAirBlocks.count())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.WHITE + "Air: " + ChatColor.WHITE + prefix + formatBlockCount(airBlocks);
    }

    private int countEntities(Selection selection) {
        if (!selection.isComplete() || selection.getPos1() == null || selection.getPos1().getWorld() == null) {
            return 0;
        }
        World world = selection.getPos1().getWorld();
        int count = 0;
        for (var entity : world.getEntities()) {
            if (selection.contains(entity.getLocation())) {
                count++;
            }
        }
        return count;
    }

    private boolean selectionsOverlap(Selection a, Selection b) {
        if (!a.isComplete() || !b.isComplete()) {
            return false;
        }
        if (a.getPos1() == null || b.getPos1() == null || a.getPos1().getWorld() == null || b.getPos1().getWorld() == null) {
            return false;
        }
        if (!a.getPos1().getWorld().equals(b.getPos1().getWorld())) {
            return false;
        }
        return a.getMinX() <= b.getMaxX() && a.getMaxX() >= b.getMinX()
                && a.getMinY() <= b.getMaxY() && a.getMaxY() >= b.getMinY()
                && a.getMinZ() <= b.getMaxZ() && a.getMaxZ() >= b.getMinZ();
    }

    private ChatColor elevationColor(int yValue, int minHeight, int maxHeight) {
        double normalized = (yValue - minHeight) / (double) Math.max(1, maxHeight - minHeight);
        normalized = Math.max(0.0D, Math.min(1.0D, normalized));
        if (normalized >= 0.75D) {
            return ChatColor.GOLD;
        }
        if (normalized >= 0.5D) {
            return ChatColor.YELLOW;
        }
        if (normalized >= 0.25D) {
            return ChatColor.GREEN;
        }
        return ChatColor.BLUE;
    }

    private ChatColor colorFor(int percent, int mediumStart, int threshold) {
        if (percent >= threshold) {
            return ChatColor.RED;
        }
        if (percent >= mediumStart) {
            return ChatColor.YELLOW;
        }
        return ChatColor.GREEN;
    }

    private int upperBandLevel(int percent) {
        if (percent <= GRAPH_SPLIT_PERCENT) {
            return 0;
        }
        return scaleBand(percent - GRAPH_SPLIT_PERCENT, 100 - GRAPH_SPLIT_PERCENT);
    }

    private int lowerBandLevel(int percent) {
        return scaleBand(Math.min(percent, GRAPH_SPLIT_PERCENT), GRAPH_SPLIT_PERCENT);
    }

    private int scaleBand(int value, int rangeMax) {
        int clamped = Math.max(0, Math.min(rangeMax, value));
        return (int) Math.round((clamped / (double) rangeMax) * (BLOCKS.length() - 1));
    }

    private char blockFor(int level) {
        int clamped = Math.max(0, Math.min(BLOCKS.length() - 1, level));
        return BLOCKS.charAt(clamped);
    }

    private record GraphRows(String topRow, String bottomRow) {
    }

    private record RamPanelMetrics(String current,
                                   String peak,
                                   String usage,
                                   int currentWidth,
                                   int peakWidth,
                                   int usageWidth,
                                   int mediumStart,
                                   int threshold) {
    }

    private record ClipboardSummary(Clipboard clipboard, String header, String detail, int headerWidth, int detailWidth) {
    }

    private record ClipboardPanel(String header, String detail, List<String> renderRows, String footer) {
        private static ClipboardPanel empty() {
            return new ClipboardPanel("", "", List.of(), "");
        }

        private int rowCount() {
            return 2 + renderRows.size() + (footer == null || footer.isEmpty() ? 0 : 1);
        }
    }

    private record SelectionPanel(List<String> rows, int width) {
        private int rowCount() {
            return rows.size();
        }
    }

    private record TrailPanel(List<String> rows, int width) {
        private int rowCount() {
            return rows.size();
        }
    }

    private record SelectionConflict(String playerName) {
    }

    private record BlockCountEstimate(long count, boolean approximate) {
    }

    private record FooterLayout(int totalWidth, int graphWidth, boolean showRam, boolean showClipboard, boolean showSelection, boolean showTrail) {
    }

    private record ModuleStyle(ChatColor accent) {
    }

    private static final class PlayerPanelCache {
        private long clipboardRevision = -1L;
        private int clipboardPanelScale = Integer.MIN_VALUE;
        private ClipboardSummary clipboardSummary;
        private ClipboardPanel clipboardPanel;
        private long selectionRevision = -1L;
        private long selectionGlobalRevision = -1L;
        private SelectionPanel selectionPanel;
        private long trailRevision = -1L;
        private int trailLimit = -1;
        private int trailScale = Integer.MIN_VALUE;
        private TrailPanel trailPanel;
    }

    private static final class TextCanvas {
        private final int width;
        private final List<List<PlacedText>> rows;

        private TextCanvas(int width, int height) {
            this.width = Math.max(1, width);
            this.rows = new ArrayList<>(height);
            for (int i = 0; i < height; i++) {
                rows.add(new ArrayList<>());
            }
        }

        private void placeText(int row, int column, String text) {
            if (row < 0 || row >= rows.size() || text == null || text.isEmpty()) {
                return;
            }
            rows.get(row).add(new PlacedText(Math.max(0, column), text));
        }

        private String render() {
            List<String> rendered = new ArrayList<>(rows.size());
            for (List<PlacedText> row : rows) {
                rendered.add(renderRow(row));
            }
            return String.join("\n", rendered);
        }

        private String renderRow(List<PlacedText> entries) {
            if (entries.isEmpty()) {
                return "";
            }
            entries.sort(Comparator.comparingInt(PlacedText::column));
            StringBuilder line = new StringBuilder(width * 2);
            int cursor = 0;
            for (PlacedText entry : entries) {
                int start = Math.max(cursor, entry.column());
                if (start > cursor) {
                    line.append(" ".repeat(start - cursor));
                    cursor = start;
                }
                line.append(entry.text());
                cursor += visibleLength(entry.text());
            }
            return line.toString();
        }

        private int visibleLength(String text) {
            String stripped = ChatColor.stripColor(text);
            return stripped == null ? 0 : stripped.length();
        }
    }

    private record PlacedText(int column, String text) {
    }
}
