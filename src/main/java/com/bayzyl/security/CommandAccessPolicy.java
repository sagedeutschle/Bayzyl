package com.bayzyl.security;

import com.bayzyl.ToolType;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class CommandAccessPolicy {
    private static final Map<String, CommandCapability> ROOT_CAPABILITIES = buildRootCapabilities();
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("bayzyl", "bzl"),
            Map.entry("bayzylhelp", "bzlhelp"),
            Map.entry("bzyzylhelp", "bzlhelp"),
            Map.entry("bm", "brushmenu"),
            Map.entry("historyclear", "clearhistory"),
            Map.entry("genbrush", "brushgen"),
            Map.entry("mat", "material"),
            Map.entry("g", "generate"),
            Map.entry("gen", "generate"),
            Map.entry("genbiome", "generatebiome"),
            Map.entry("gbiome", "generatebiome"),
            Map.entry("schem", "schematic"),
            Map.entry("gh", "ghosthand"),
            Map.entry("pvt", "particlevisualtoggle"),
            Map.entry("db", "detailbrush"),
            Map.entry("bzlaccent", "accent"),
            Map.entry("bzlauthority", "authority"),
            Map.entry("commandauthority", "authority"),
            Map.entry("bzltool", "tool")
    );

    public CommandCapability requiredCapability(String command, String[] args) {
        String root = normalizeCommand(command);
        root = ALIASES.getOrDefault(root, root);
        String[] safeArgs = args == null ? new String[0] : args;

        if (root.equals("bzl")) {
            return requiredBzlCapability(safeArgs);
        }
        if (root.equals("brush")) {
            return requiredBrushCapability(safeArgs, 0);
        }
        if (root.equals("bzltoggle")) {
            if (safeArgs.length == 0) {
                return CommandCapability.USE;
            }
            return switch (safeArgs[0].toLowerCase(Locale.ROOT)) {
                case "admin" -> CommandCapability.ADMIN_MODE;
                case "ramalert", "authority" -> CommandCapability.RUNTIME;
                default -> CommandCapability.USE;
            };
        }
        return ROOT_CAPABILITIES.get(root);
    }

    public Map<String, CommandCapability> explicitRootCapabilities() {
        return ROOT_CAPABILITIES;
    }

    public CommandCapability requiredToolCapability(ToolType toolType) {
        if (toolType == null) {
            return CommandCapability.USE;
        }
        return switch (toolType) {
            case WAND -> CommandCapability.USE;
            case STRUCTURE_BRUSH -> CommandCapability.GENSTRUCTURE;
            case GEN_BRUSH -> CommandCapability.GENERATE;
            default -> CommandCapability.EDIT;
        };
    }

    public CommandCapability requiredDetailShortcutCapability() {
        return CommandCapability.EDIT;
    }

    public CommandCapability requiredKitShortcutCapability() {
        return CommandCapability.USE;
    }

    public CommandCapability requiredWandScrollCapability() {
        return CommandCapability.EDIT;
    }

    public List<String> filterAllowedSuggestions(Predicate<String> hasPermission,
                                                 String command,
                                                 String[] currentArgs,
                                                 List<String> candidates) {
        String[] args = currentArgs == null ? new String[0] : currentArgs;
        List<String> allowed = new ArrayList<>();
        BayzylAccess access = new BayzylAccess();
        for (String candidate : candidates) {
            String[] prospective;
            if (args.length == 0) {
                prospective = new String[]{candidate};
            } else {
                prospective = Arrays.copyOf(args, args.length);
                prospective[prospective.length - 1] = candidate;
            }
            if (access.allowed(hasPermission, requiredCapability(command, prospective))
                    && allowedNarrowPermission(hasPermission, requiredNarrowPermission(command, prospective))) {
                allowed.add(candidate);
            }
        }
        return List.copyOf(allowed);
    }

    public List<String> toggleSuggestions(String[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }
        List<String> candidates;
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            candidates = List.of("admin", "ramalert", "authority");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("authority")) {
            candidates = List.of("status", "claim", "giveup");
        } else {
            return List.of();
        }
        return candidates.stream().filter(candidate -> candidate.startsWith(prefix)).toList();
    }

    private String requiredNarrowPermission(String command, String[] args) {
        String root = normalizeCommand(command);
        root = ALIASES.getOrDefault(root, root);
        if (root.equals("bzl") && args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("kit")) {
                return args.length > 1 ? requiredKitPermission(args[1]) : null;
            }
            if (sub.equals("kitmake") || sub.equals("kitupdate") || sub.equals("kitconfirm")) {
                return "bayzyl.kit.create";
            }
            return null;
        }
        if (root.equals("kit")) {
            return args.length > 0 ? requiredKitPermission(args[0]) : null;
        }
        if (root.equals("kitmake") || root.equals("kitupdate") || root.equals("kitconfirm")) {
            return "bayzyl.kit.create";
        }
        return null;
    }

    private String requiredKitPermission(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "delete" -> "bayzyl.kit.delete";
            case "rename", "duplicate", "update", "confirm", "restoredefaults",
                    "note", "theme", "icon", "alias" -> "bayzyl.kit.create";
            default -> null;
        };
    }

    private boolean allowedNarrowPermission(Predicate<String> hasPermission, String permission) {
        return permission == null
                || hasPermission.test(BayzylAccess.ADMIN_PERMISSION)
                || hasPermission.test(permission);
    }

    private CommandCapability requiredBzlCapability(String[] args) {
        if (args.length == 0) {
            return CommandCapability.USE;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("brush")) {
            return requiredBrushCapability(args, 1);
        }
        return switch (sub) {
            case "env" -> CommandCapability.RUNTIME;
            case "ramalert", "authority", "commandauthority", "debug" -> CommandCapability.RUNTIME;
            case "naturalize", "cleanup", "palette", "tool" -> CommandCapability.EDIT;
            default -> CommandCapability.USE;
        };
    }

    private CommandCapability requiredBrushCapability(String[] args, int kindIndex) {
        if (args.length <= kindIndex) {
            return CommandCapability.EDIT;
        }
        return switch (args[kindIndex].toLowerCase(Locale.ROOT)) {
            case "structure" -> CommandCapability.GENSTRUCTURE;
            case "gen" -> CommandCapability.GENERATE;
            default -> CommandCapability.EDIT;
        };
    }

    private String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        int namespace = normalized.indexOf(':');
        return namespace >= 0 ? normalized.substring(namespace + 1) : normalized;
    }

    private static Map<String, CommandCapability> buildRootCapabilities() {
        Map<String, CommandCapability> capabilities = new LinkedHashMap<>();
        map(capabilities, CommandCapability.USE,
                "bzl", "bzlhelp", "kithelp", "kitlist", "kitupdate", "kitconfirm",
                "step", "nudge", "clearhistory", "biomeinfo", "measure",
                "ruler", "whereami", "surface", "ascend", "descend", "align", "ceil",
                "centerme", "ghosthand", "profile", "kit", "brushmenu", "kitmake",
                "clearclipboard", "clipboardinfo", "trailclear", "selcorners", "selswap",
                "selsave", "selload", "selcenter", "pos1", "pos2", "bubu", "lol",
                "select", "expand", "contract", "unstick", "thru", "tabmenu", "wand",
                "nightvision", "particlevisualtoggle", "resume", "redstoneaudit");
        map(capabilities, CommandCapability.EDIT,
                "set", "replace", "copy", "cut", "paste", "move", "stack", "rotate", "flip",
                "sphere", "hsphere", "dome", "hdome", "bowl", "hbowl", "cyl", "hcyl",
                "pyramid", "hpyramid", "brush", "mask", "gmask", "material", "size", "density",
                "none", "naturalize", "schematic", "walls", "overlay", "smooth", "palette",
                "eraser", "cleanup", "detailbrush", "agitate", "tool", "floatingcleanup",
                "foliagecleanup", "liquidcleanup", "snowcleanup", "lightcleanup",
                "undo", "redo", "oops", "susu", "artie");
        map(capabilities, CommandCapability.GENERATE,
                "brushgen", "generate", "generatebiome", "forestgen", "genfeature", "pumpkins");
        map(capabilities, CommandCapability.GENSTRUCTURE, "genstructure", "regen");
        map(capabilities, CommandCapability.ADMIN_MODE, "bzltoggle");
        map(capabilities, CommandCapability.JAIL, "jail", "liberate");
        map(capabilities, CommandCapability.WETOGGLE, "wetoggle");
        map(capabilities, CommandCapability.RUNTIME, "ramalert", "memreset", "authority", "accent");
        return Collections.unmodifiableMap(capabilities);
    }

    private static void map(Map<String, CommandCapability> target,
                            CommandCapability capability,
                            String... roots) {
        for (String root : roots) {
            target.put(root, capability);
        }
    }
}
