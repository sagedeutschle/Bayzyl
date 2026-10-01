package com.bayzyl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import org.bukkit.Material;

public final class SuggestionUtil {

    private SuggestionUtil() {}

    private static final List<String> PALETTE_COMMANDS = List.of("analyze", "swap");

    public static List<String> maskSuggestions(String token) {
        if (token == null) return Collections.emptyList();
        String t = token.toLowerCase(Locale.ROOT);
        List<String> opts = List.of("none", "all", "solid", "air");
        List<String> out = new ArrayList<>();
        for (String s : opts) {
            if (s.startsWith(t)) out.add(s);
        }
        return out;
    }

    public static List<String> suggest(String command, String[] args) {
        if (command == null) return Collections.emptyList();
        switch (command.toLowerCase(Locale.ROOT)) {
            case "palette":
                return PALETTE_COMMANDS;
            case "set":
                return blockSuggestions(args.length == 0 ? "" : args[0]);
            case "genstructure":
                return filterPrefix(com.bayzyl.generation.VanillaContentRegistry.structureIds(), args.length == 0 ? "" : args[0]);
            case "regen":
                return Collections.emptyList();
            case "genfeature":
                return filterPrefix(com.bayzyl.generation.VanillaContentRegistry.featureIds(), args.length == 0 ? "" : args[0]);
            case "memreset":
                return filterPrefix(List.of("global"), args.length == 0 ? "" : args[args.length - 1]);
            case "clearclipboard":
                return Collections.emptyList();
            case "redstoneaudit":
                return redstoneAuditSuggestions(args);
            default:
                return Collections.emptyList();
        }
    }

    private static final List<String> REDSTONE_AUDIT_ACTIONS = List.of("clear", "page", "show");

    /** Completions for {@code /redstoneaudit} and {@code /bzl audit} (arguments after the command or "audit"). */
    public static List<String> redstoneAuditSuggestions(String[] args) {
        if (args == null || args.length != 1) {
            return Collections.emptyList();
        }
        return filterPrefix(REDSTONE_AUDIT_ACTIONS, args[0]);
    }

    public static List<String> blockSuggestions(String prefix) {
        if (prefix == null) return Collections.emptyList();
        String p = prefix.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Material material : Material.values()) {
            if (!material.isBlock() && material != Material.AIR) {
                continue;
            }
            String plain = material.name().toLowerCase(Locale.ROOT);
            if (plain.startsWith(p)) {
                out.add(plain);
            }
            String namespaced = "minecraft:" + plain;
            if (namespaced.startsWith(p)) {
                out.add(namespaced);
            }
        }
        ArrayList<String> results = new ArrayList<>(out);
        results.sort(String.CASE_INSENSITIVE_ORDER);
        return results;
    }

    public static List<String> itemSuggestions(String prefix) {
        if (prefix == null) return Collections.emptyList();
        String p = prefix.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Material material : Material.values()) {
            if (!material.isItem() || material == Material.AIR) {
                continue;
            }
            String plain = material.name().toLowerCase(Locale.ROOT);
            if (plain.startsWith(p)) {
                out.add(plain);
            }
            String namespaced = "minecraft:" + plain;
            if (namespaced.startsWith(p)) {
                out.add(namespaced);
            }
        }
        ArrayList<String> results = new ArrayList<>(out);
        results.sort(String.CASE_INSENSITIVE_ORDER);
        return results;
    }

    private static List<String> filterPrefix(List<String> source, String prefix) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (prefix == null || prefix.isEmpty()) {
            return source;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
