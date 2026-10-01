package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ChatOutput {
    private static final String DEFAULT_RAIL = ChatColor.GREEN.toString() + ChatColor.BOLD + "|" + ChatColor.RESET + ChatColor.WHITE;
    private static volatile Supplier<String> railPrefixSupplier = () -> DEFAULT_RAIL;
    private static final String CONTINUATION = "";
    private static final int DEFAULT_WRAP_WIDTH = 52;
    private static final Pattern AMPERSAND_RAIL = Pattern.compile("^(?:&[0-9A-FK-ORa-fk-or])*\\|\\s*");

    private ChatOutput() {
    }

    public static void setRailPrefixSupplier(Supplier<String> supplier) {
        railPrefixSupplier = supplier == null ? () -> DEFAULT_RAIL : supplier;
    }

    public static void rail(CommandSender sender, String message) {
        rail(sender, message, DEFAULT_WRAP_WIDTH);
    }

    public static void send(CommandSender sender, String message) {
        rail(sender, message);
    }

    public static void rail(CommandSender sender, String message, int wrapWidth) {
        if (sender == null) {
            return;
        }
        for (String line : wrappedLines(message, wrapWidth)) {
            sendPreparedRailLine(sender, line);
        }
    }

    public static List<String> wrappedLines(String message, int wrapWidth) {
        String normalized = stripExistingRail(message == null ? "" : message);
        String translated = ChatColor.WHITE + ChatColor.translateAlternateColorCodes('&', normalized);
        return wrap(translated, Math.max(20, wrapWidth));
    }

    public static void sendPreparedRailLine(CommandSender sender, String line) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(railPrefixSupplier.get() + (line == null ? "" : line));
    }

    /** One rail line that runs {@code command} when clicked and shows {@code hover} on mouse-over. */
    public static void clickableRail(CommandSender sender, String line, String command, String hover) {
        if (sender == null) {
            return;
        }
        String text = railPrefixSupplier.get() + ChatColor.translateAlternateColorCodes('&', line == null ? "" : line);
        sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(text)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text(hover == null ? command : hover))));
    }

    public static void railBlock(CommandSender sender, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (String line : lines) {
            rail(sender, line);
        }
    }

    public static void info(CommandSender sender, String message) {
        rail(sender, ChatColor.WHITE + message);
    }

    public static void success(CommandSender sender, String message) {
        rail(sender, ChatColor.GREEN + message);
    }

    public static void warn(CommandSender sender, String message) {
        rail(sender, ChatColor.YELLOW + message);
    }

    public static void error(CommandSender sender, String message) {
        rail(sender, ChatColor.RED + message);
    }

    private static String stripExistingRail(String message) {
        return AMPERSAND_RAIL.matcher(message).replaceFirst("");
    }

    private static List<String> wrap(String message, int wrapWidth) {
        List<String> result = new ArrayList<>();
        if (visibleLength(message) <= wrapWidth) {
            result.add(message);
            return result;
        }

        String[] words = message.split(" ");
        StringBuilder current = new StringBuilder();
        int currentVisible = 0;
        String activeColors = "";
        for (String word : words) {
            int wordVisible = visibleLength(word);
            int separator = currentVisible == 0 ? 0 : 1;
            if (currentVisible > 0 && currentVisible + separator + wordVisible > wrapWidth) {
                String line = current.toString();
                result.add(line);
                activeColors = ChatColor.getLastColors(line);
                current = new StringBuilder(activeColors).append(CONTINUATION).append(word);
                currentVisible = CONTINUATION.length() + wordVisible;
                continue;
            }
            if (currentVisible > 0 && shouldBreakBeforeWord(current, currentVisible, wordVisible, wrapWidth)) {
                String line = current.toString();
                result.add(line);
                activeColors = ChatColor.getLastColors(line);
                current = new StringBuilder(activeColors).append(CONTINUATION).append(word);
                currentVisible = CONTINUATION.length() + wordVisible;
                continue;
            }
            if (currentVisible > 0) {
                current.append(' ');
                currentVisible++;
            }
            current.append(word);
            currentVisible += wordVisible;
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static boolean shouldBreakBeforeWord(StringBuilder current, int currentVisible, int wordVisible, int wrapWidth) {
        if (wordVisible <= 2 || currentVisible < Math.max(28, wrapWidth - 14)) {
            return false;
        }
        String visible = stripColorCodes(current.toString()).trim();
        return visible.endsWith(".")
                || visible.endsWith(":")
                || visible.endsWith(";")
                || visible.endsWith("!")
                || visible.endsWith("?")
                || visible.endsWith("|")
                || visible.endsWith("—");
    }

    private static String stripColorCodes(String message) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < message.length()) {
                i++;
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private static int visibleLength(String message) {
        int length = 0;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < message.length()) {
                i++;
                continue;
            }
            length++;
        }
        return length;
    }
}
