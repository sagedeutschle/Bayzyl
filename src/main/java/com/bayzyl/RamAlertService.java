package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RamAlertService {
    private static final String RAMALERT_STATUS_MENU = "ramalert-status";
    private static final String RAMALERT_HELP_MENU = "ramalert-help";

    private final JavaPlugin plugin;
    private final ListMenuConfigService listMenuConfigService;
    private final MessageThemeService messageThemeService;
    private volatile RamAlertSettings settings = RamAlertSettings.defaults();
    private volatile BukkitTask task;
    private volatile long lastAlertAt = 0L;

    public RamAlertService(JavaPlugin plugin) {
        this(plugin, null, null);
    }

    public RamAlertService(JavaPlugin plugin, ListMenuConfigService listMenuConfigService, MessageThemeService messageThemeService) {
        this.plugin = plugin;
        this.listMenuConfigService = listMenuConfigService;
        this.messageThemeService = messageThemeService;
    }

    public RamAlertSettings getSettings() {
        return settings;
    }

    public RamSnapshot currentSnapshot() {
        return snapshot();
    }

    public void update(RamAlertSettings newSettings) {
        settings = newSettings;
        restartTask();
    }

    public void shutdown() {
        cancelTask();
    }

    private void restartTask() {
        cancelTask();
        if (!settings.enabled()) {
            return;
        }
        snapshot();
        long period = Math.max(20L, settings.intervalSeconds() * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkMemory, period, period);
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void checkMemory() {
        RamSnapshot snapshot = snapshot();
        if (snapshot.maxBytes() <= 0L) {
            return;
        }
        if (snapshot.usedPercent() < settings.thresholdPercent()) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = settings.cooldownSeconds() * 1000L;
        if (now - lastAlertAt < cooldownMillis) {
            return;
        }
        lastAlertAt = now;

        List<String> lines = alertLines(snapshot);
        for (String line : lines) {
            plugin.getLogger().warning(ChatColor.stripColor(line));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("bayzyl.admin") || player.hasPermission("bayzyl.runtime")) {
                for (String line : lines) {
                    ChatOutput.send(player, line);
                }
            }
        }
    }

    public List<String> helpLines() {
        List<String> fallback = List.of(
                accent(ChatColor.GREEN.toString() + ChatColor.BOLD + "BZL"),
                accent(ChatColor.GREEN + "RAM Alert " + ChatColor.DARK_GRAY + "|" + ChatColor.WHITE + " Help"),
                accent(ChatColor.GREEN + "Bayzyl RAM alert watches JVM memory pressure, not TPS or CPU."),
                ChatColor.GOLD + "It tracks total used JVM memory against the max JVM allocation and shows the largest memory pools.",
                accent(ChatColor.GREEN + "High values mean the server has less headroom before garbage collection pressure increases."),
                ChatColor.GOLD + "Frequent alerts usually mean heavy edits, too many loaded systems, or an undersized JVM allocation."
        );
        if (listMenuConfigService == null) {
            return fallback;
        }
        return listMenuConfigService.formatList(
                RAMALERT_HELP_MENU,
                "lines",
                fallback,
                ListMenuConfigService.tokens()
        );
    }

    public List<String> statusLines() {
        RamSnapshot snapshot = snapshot();
        List<String> fallback = List.of(
                accent(ChatColor.GREEN.toString() + ChatColor.BOLD + "BZL"),
                accent(ChatColor.GREEN + "RAM Alert " + ChatColor.DARK_GRAY + "|" + ChatColor.WHITE + " "
                        + (settings.enabled() ? ChatColor.GOLD + "on" : ChatColor.GOLD + "off")
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "threshold " + ChatColor.GOLD + settings.thresholdPercent() + "%"
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "interval " + ChatColor.GOLD + settings.intervalSeconds() + "s"
                        + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "cooldown " + ChatColor.GOLD + settings.cooldownSeconds() + "s"),
                accent(ChatColor.GREEN + "Current JVM use " + ChatColor.DARK_GRAY + "|" + ChatColor.WHITE + " " + ChatColor.GOLD + snapshot.usedPercent() + "%"
                        + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + formatMb(snapshot.usedBytes()) + "MB"
                        + ChatColor.DARK_GRAY + " / " + ChatColor.GOLD + formatMb(snapshot.maxBytes()) + "MB")
        );
        List<String> lines;
        if (listMenuConfigService == null) {
            lines = new ArrayList<>(fallback);
        } else {
            lines = new ArrayList<>(listMenuConfigService.formatList(
                    RAMALERT_STATUS_MENU,
                    "lines",
                    fallback,
                    ListMenuConfigService.tokens(
                            "state", settings.enabled()
                                    ? listMenuConfigService.format(RAMALERT_STATUS_MENU, "state.on", "&6on", ListMenuConfigService.tokens())
                                    : listMenuConfigService.format(RAMALERT_STATUS_MENU, "state.off", "&6off", ListMenuConfigService.tokens()),
                            "threshold", Integer.toString(settings.thresholdPercent()),
                            "interval", Integer.toString(settings.intervalSeconds()),
                            "cooldown", Integer.toString(settings.cooldownSeconds()),
                            "used_percent", Integer.toString(snapshot.usedPercent()),
                            "used_mb", formatMb(snapshot.usedBytes()),
                            "max_mb", formatMb(snapshot.maxBytes())
                    )
            ));
        }
        lines.addAll(formatTopConsumers(snapshot));
        return lines;
    }

    private List<String> alertLines(RamSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.RED + "[Bayzyl] RAM alert: "
                + ChatColor.GOLD + snapshot.usedPercent() + "%"
                + ChatColor.RED + " used ("
                + ChatColor.GOLD + formatMb(snapshot.usedBytes()) + "MB"
                + ChatColor.RED + " / "
                + ChatColor.GOLD + formatMb(snapshot.maxBytes()) + "MB"
                + ChatColor.RED + ").");
        lines.addAll(formatTopConsumers(snapshot));
        return lines;
    }

    private List<String> formatTopConsumers(RamSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        int configuredLimit = listMenuConfigService == null
                ? 5
                : listMenuConfigService.intValue(RAMALERT_STATUS_MENU, "consumer.limit", 5, 0, 20);
        int limit = Math.min(configuredLimit, snapshot.consumers().size());
        for (int i = 0; i < limit; i++) {
            RamConsumer consumer = snapshot.consumers().get(i);
            String fallback = ChatColor.GREEN + " - " + ChatColor.GOLD + consumer.name()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + formatMb(consumer.usedBytes()) + "MB"
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + consumer.percentOfMax() + "%"
                    + ChatColor.WHITE + " of max" + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + consumer.percentOfUsed() + "%"
                    + ChatColor.WHITE + " of current load";
            if (listMenuConfigService == null) {
                lines.add(accent(fallback));
                continue;
            }
            lines.add(listMenuConfigService.format(
                    RAMALERT_STATUS_MENU,
                    "consumer.row",
                    fallback,
                    ListMenuConfigService.tokens(
                            "name", consumer.name(),
                            "used_mb", formatMb(consumer.usedBytes()),
                            "percent_of_max", Integer.toString(consumer.percentOfMax()),
                            "percent_of_used", Integer.toString(consumer.percentOfUsed())
                    )
            ));
        }
        return lines;
    }

    private RamSnapshot snapshot() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        int usedPercent = max <= 0L ? 0 : (int) Math.min(100L, Math.round((used * 100.0D) / max));

        List<RamConsumer> consumers = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            long poolUsed = Math.max(0L, pool.getUsage() == null ? 0L : pool.getUsage().getUsed());
            long poolMax = pool.getUsage() == null ? -1L : pool.getUsage().getMax();
            if (poolMax <= 0L && pool.getUsage() != null) {
                poolMax = pool.getUsage().getCommitted();
            }
            if (poolUsed <= 0L) {
                continue;
            }
            consumers.add(toConsumer(pool.getName(), poolUsed, max, used));
        }
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            long poolUsed = Math.max(0L, pool.getMemoryUsed());
            if (poolUsed <= 0L) {
                continue;
            }
            consumers.add(toConsumer(pool.getName(), poolUsed, max, used));
        }
        consumers.sort(Comparator.comparingLong(RamConsumer::usedBytes).reversed());
        return new RamSnapshot(used, max, usedPercent, consumers);
    }

    private RamConsumer toConsumer(String name, long used, long totalMax, long totalUsed) {
        int ofMax = totalMax <= 0L ? 0 : (int) Math.min(100L, Math.round((used * 100.0D) / totalMax));
        int ofUsed = totalUsed <= 0L ? 0 : (int) Math.min(100L, Math.round((used * 100.0D) / totalUsed));
        return new RamConsumer(name, used, ofMax, ofUsed);
    }

    private String formatMb(long bytes) {
        return String.format(Locale.ROOT, "%.0f", bytes / 1024.0D / 1024.0D);
    }

    private String accent(String value) {
        return messageThemeService == null ? value : messageThemeService.applyAccent(value);
    }

    public record RamConsumer(String name, long usedBytes, int percentOfMax, int percentOfUsed) {
    }

    public record RamSnapshot(long usedBytes, long maxBytes, int usedPercent, List<RamConsumer> consumers) {
    }
}
