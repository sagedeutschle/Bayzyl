package com.bayzyl.redstone;

import com.bayzyl.ChatOutput;
import com.bayzyl.SuggestionUtil;
import com.bayzyl.redstone.RedstoneAuditSnapshotFactory.Capture;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /redstoneaudit [clear | show <n> | page <n>]} and {@code /bzl audit ...}: parses, runs the read-only audit
 * on the caller's selection, and renders ranked, clickable results. Never changes a block.
 */
public final class RedstoneAuditCommand {
    public static final int PAGE_SIZE = 8;
    private static final String ROOT = "/redstoneaudit";

    /** Captures the caller's selection, or explains why it can't. */
    @FunctionalInterface
    public interface SelectionCapture {
        Capture capture(Player player);
    }

    /** Where temporary, per-player highlights go. */
    public interface MarkerSink {
        MarkerSink NONE = new MarkerSink() {
            @Override
            public void show(Player player, List<AuditFinding> findings) {
            }

            @Override
            public void clear(UUID playerId) {
            }
        };

        void show(Player player, List<AuditFinding> findings);

        void clear(UUID playerId);
    }

    private final RedstoneAuditService service;
    private final SelectionCapture capture;
    private final MarkerSink markers;
    private final Map<UUID, List<AuditFinding>> lastFindings = new ConcurrentHashMap<>();

    public RedstoneAuditCommand(RedstoneAuditService service, SelectionCapture capture, MarkerSink markers) {
        this.service = service;
        this.capture = capture;
        this.markers = markers;
    }

    /** {@code args} are the arguments after {@code /redstoneaudit} (or after {@code /bzl audit}). */
    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatOutput.send(sender, "&cPlayers only.");
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "" -> run(player);
            case "clear" -> {
                markers.clear(player.getUniqueId());
                ChatOutput.send(player, "&7Cleared your redstone audit markers.");
            }
            case "show" -> show(player, args);
            case "page" -> page(player, args);
            default -> ChatOutput.send(player, "&7Usage: &f" + ROOT + " &7[clear | show <n> | page <n>]");
        }
        return true;
    }

    public List<String> suggest(String[] args) {
        return SuggestionUtil.redstoneAuditSuggestions(args);
    }

    /** Forget a player's results (on quit). */
    public void forget(UUID playerId) {
        lastFindings.remove(playerId);
        markers.clear(playerId);
    }

    private void run(Player player) {
        Capture captured = capture.capture(player);
        if (captured.refusal() != null) {
            ChatOutput.send(player, "&c" + captured.refusal());
            return;
        }
        List<AuditFinding> findings = service.audit(captured.snapshot());
        lastFindings.put(player.getUniqueId(), findings);
        if (findings.isEmpty()) {
            markers.clear(player.getUniqueId());
            ChatOutput.send(player, "&aNo likely wiring faults found in this selection.");
            ChatOutput.send(player, "&7The audit checks static wiring only; it does not simulate timing.");
            return;
        }
        markers.show(player, findings);
        render(player, findings, 1);
    }

    private void render(Player player, List<AuditFinding> findings, int page) {
        int pages = pages(findings);
        ChatOutput.send(player, "&6Redstone audit: &f" + findings.size() + "&6 likely fault(s), most likely first"
                + (pages > 1 ? " &7(page " + page + "/" + pages + ")" : ""));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(findings.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            AuditFinding finding = findings.get(index);
            int number = index + 1;
            ChatOutput.clickableRail(player,
                    "&f#" + number + " " + colour(finding.confidence()) + finding.confidence().name() + " &7— &f"
                            + finding.title() + " &7(" + finding.position() + ")",
                    ROOT + " show " + number,
                    finding.pattern() + "\nLikely fix: " + finding.suggestion() + "\nClick to highlight it.");
        }
        if (page < pages) {
            ChatOutput.send(player, "&7More: &f" + ROOT + " page " + (page + 1));
        }
        ChatOutput.send(player, "&7Click a result to highlight it; &f" + ROOT + " clear&7 removes markers.");
    }

    private void show(Player player, String[] args) {
        List<AuditFinding> findings = lastFindings.get(player.getUniqueId());
        if (findings == null || findings.isEmpty()) {
            ChatOutput.send(player, "&cRun " + ROOT + " on a selected machine first.");
            return;
        }
        Integer number = number(args);
        if (number == null || number < 1 || number > findings.size()) {
            ChatOutput.send(player, "&cPick a result between 1 and " + findings.size() + ".");
            return;
        }
        AuditFinding finding = findings.get(number - 1);
        markers.show(player, List.of(finding));
        ChatOutput.send(player, "&f#" + number + " " + colour(finding.confidence()) + finding.confidence().name()
                + " &7— &f" + finding.title());
        ChatOutput.send(player, "&7" + finding.position());
        ChatOutput.send(player, "&7" + finding.pattern());
        ChatOutput.send(player, "&7Likely fix: &f" + finding.suggestion());
    }

    private void page(Player player, String[] args) {
        List<AuditFinding> findings = lastFindings.get(player.getUniqueId());
        if (findings == null || findings.isEmpty()) {
            ChatOutput.send(player, "&cRun " + ROOT + " on a selected machine first.");
            return;
        }
        Integer page = number(args);
        if (page == null || page < 1 || page > pages(findings)) {
            ChatOutput.send(player, "&cPick a page between 1 and " + pages(findings) + ".");
            return;
        }
        render(player, findings, page);
    }

    private static int pages(List<AuditFinding> findings) {
        return Math.max(1, (findings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static Integer number(String[] args) {
        if (args.length < 2) {
            return null;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String colour(AuditConfidence confidence) {
        return switch (confidence) {
            case HIGH -> "&c";
            case MEDIUM -> "&6";
            case LOW -> "&e";
        };
    }
}
