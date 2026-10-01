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
