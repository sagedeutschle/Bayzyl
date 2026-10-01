package com.bayzyl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandRegistrationParityTest {
    @Test
    void pluginDescriptorMatchesRuntimeCommandRegistry() throws IOException {
        Set<String> pluginCommands = readPluginCommands();
        Set<String> runtimeCommands = new LinkedHashSet<>();
        for (CommandSpec spec : CommandRegistry.getAllCommands()) {
            runtimeCommands.add(spec.name());
        }

        assertEquals(runtimeCommands, pluginCommands, "plugin.yml command keys must match CommandRegistry.getAllCommands()");
    }

    @Test
    void descriptorUsageAndAliasesMatchTheRegistryAndAccessPolicy() throws IOException {
        List<String> descriptor = Files.readAllLines(Path.of("src", "main", "resources", "plugin.yml"), StandardCharsets.UTF_8);
        assertEquals(List.of(), DescriptorParity.problems(descriptor, CommandRegistry.getAllCommands(),
                new com.bayzyl.security.CommandAccessPolicy().aliases()));
    }

    @Test
    void parityCatchesAMissingRootAMismatchedUsageAndAnUndeclaredAlias() {
        List<String> descriptor = List.of(
                "name: Bayzyl",
                "commands:",
                "  copy:",
                "    description: Copy",
                "    usage: /copy [mask]",
                "  db:",
                "    usage: /db",
                "    aliases: [dbx]",
                "permissions:");
        List<CommandSpec> registry = List.of(
                new CommandSpec("copy", "Copy", "/copy [options]"),
                new CommandSpec("paste", "Paste", "/paste"));
        List<String> problems = DescriptorParity.problems(descriptor, registry, java.util.Map.of("pst", "paste"));

        assertTrue(problems.contains("missing root: paste"), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.startsWith("usage differs for copy")), problems.toString());
        assertTrue(problems.contains("undeclared in registry: db"), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.startsWith("alias dbx is declared for db")), problems.toString());
        assertTrue(problems.contains("alias pst -> paste is not declared in plugin.yml"), problems.toString());
    }

    @Test
    void redstoneAuditIsRegisteredAsARootAndUnderBzl() throws IOException {
        CommandSpec root = CommandRegistry.getTopLevel().stream()
                .filter(spec -> spec.name().equals("redstoneaudit")).findFirst().orElseThrow();
        assertEquals("/redstoneaudit [clear|show <n>|page <n>]", root.usage());
        CommandSpec bzl = CommandRegistry.getBzlSubcommands().stream()
                .filter(spec -> spec.name().equals("audit")).findFirst().orElseThrow();
        assertEquals("/bzl audit [clear|show <n>|page <n>]", bzl.usage());

        List<String> descriptor = Files.readAllLines(Path.of("src", "main", "resources", "plugin.yml"), StandardCharsets.UTF_8);
        int index = descriptor.indexOf("  redstoneaudit:");
        assertTrue(index > 0, "plugin.yml must declare redstoneaudit");
        assertTrue(descriptor.subList(index, Math.min(descriptor.size(), index + 4))
                .contains("    usage: " + root.usage()), "plugin.yml usage must match the registry");
    }

    private static Set<String> readPluginCommands() throws IOException {
        Path pluginYaml = Path.of("src", "main", "resources", "plugin.yml");
        Set<String> commands = new LinkedHashSet<>();
        boolean inCommandsBlock = false;
        for (String line : Files.readAllLines(pluginYaml, StandardCharsets.UTF_8)) {
            if (line.equals("commands:")) {
                inCommandsBlock = true;
                continue;
            }
            if (inCommandsBlock && !line.startsWith("  ")) {
                break;
            }
            if (inCommandsBlock && line.matches("  [a-z0-9]+:")) {
                commands.add(line.substring(2, line.length() - 1));
            }
        }
        return commands;
    }
}
