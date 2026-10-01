package com.bayzyl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares plugin.yml's commands block with the runtime registry and the access policy's alias map. */
final class DescriptorParity {
    private static final Pattern ROOT = Pattern.compile("^  ([a-z0-9]+):$");
    private static final Pattern FIELD = Pattern.compile("^    (usage|aliases): (.*)$");

    record Declared(String usage, List<String> aliases) {
    }

    private DescriptorParity() {
    }

    static Map<String, Declared> parse(List<String> descriptor) {
        Map<String, Declared> commands = new LinkedHashMap<>();
        boolean inCommands = false;
        String current = null;
        for (String line : descriptor) {
            if (line.equals("commands:")) {
                inCommands = true;
                continue;
            }
            if (inCommands && !line.isEmpty() && !line.startsWith(" ")) {
                break;
            }
            if (!inCommands) {
                continue;
            }
            Matcher root = ROOT.matcher(line);
            if (root.matches()) {
                current = root.group(1);
                commands.put(current, new Declared(null, List.of()));
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.matches() && current != null) {
                Declared declared = commands.get(current);
                if (field.group(1).equals("usage")) {
                    commands.put(current, new Declared(field.group(2), declared.aliases()));
                } else {
                    String list = field.group(2).replace("[", "").replace("]", "");
                    List<String> aliases = new ArrayList<>();
                    for (String alias : list.split(",")) {
                        if (!alias.isBlank()) {
                            aliases.add(alias.trim());
                        }
                    }
                    commands.put(current, new Declared(declared.usage(), List.copyOf(aliases)));
                }
            }
        }
        return commands;
    }

    /** Every disagreement between the descriptor, the registry, and the alias map; empty when they agree. */
    static List<String> problems(List<String> descriptor, List<CommandSpec> registry, Map<String, String> policyAliases) {
        Map<String, Declared> declared = parse(descriptor);
        List<String> problems = new ArrayList<>();
        Map<String, String> registryUsage = new LinkedHashMap<>();
        for (CommandSpec spec : registry) {
            registryUsage.put(spec.name(), spec.usage());
        }
        for (Map.Entry<String, String> entry : registryUsage.entrySet()) {
            Declared command = declared.get(entry.getKey());
            if (command == null) {
                problems.add("missing root: " + entry.getKey());
            } else if (!entry.getValue().equals(command.usage())) {
                problems.add("usage differs for " + entry.getKey() + ": registry '" + entry.getValue()
                        + "' vs plugin.yml '" + command.usage() + "'");
            }
        }
        for (String name : declared.keySet()) {
            if (!registryUsage.containsKey(name)) {
                problems.add("undeclared in registry: " + name);
            }
        }
        Map<String, String> descriptorAliases = new LinkedHashMap<>();
        declared.forEach((root, command) -> command.aliases().forEach(alias -> descriptorAliases.put(alias, root)));
        descriptorAliases.forEach((alias, root) -> {
            if (!root.equals(policyAliases.get(alias))) {
                problems.add("alias " + alias + " is declared for " + root + " but the access policy maps it to "
                        + policyAliases.get(alias));
            }
        });
        policyAliases.forEach((alias, root) -> {
            if (!descriptorAliases.containsKey(alias)) {
                problems.add("alias " + alias + " -> " + root + " is not declared in plugin.yml");
            }
        });
        return problems;
    }
}
