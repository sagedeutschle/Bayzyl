package com.bayzyl.security;

import com.bayzyl.CommandRegistry;
import com.bayzyl.ToolType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandAccessPolicyTest {
    private final CommandAccessPolicy policy = new CommandAccessPolicy();
    private final BayzylAccess access = new BayzylAccess();

    @Test
    void everyRegisteredRootHasAnExplicitCapability() {
        Set<String> registered = new LinkedHashSet<>();
        CommandRegistry.getAllCommands().forEach(spec -> registered.add(spec.name()));

        assertEquals(110, registered.size(), "security review must cover the complete command surface");
        assertEquals(registered, policy.explicitRootCapabilities().keySet());
        registered.forEach(root -> assertNotNull(policy.requiredCapability(root, new String[0]), root));
    }

    @Test
    void aliasesAndNamespacedInvocationsShareTheRootCapability() throws IOException {
        for (Map.Entry<String, String> alias : readAliases().entrySet()) {
            assertEquals(
                    policy.requiredCapability(alias.getValue(), new String[0]),
                    policy.requiredCapability(alias.getKey(), new String[0]),
                    alias.getKey()
            );
        }
        assertEquals(
                policy.requiredCapability("set", new String[0]),
                policy.requiredCapability("bayzyl:set", new String[0])
        );
    }

    @Test
    void redstoneAuditIsReadOnlyAndNeedsOnlyUse() {
        for (String[] args : new String[][]{{}, {"clear"}, {"show", "1"}, {"page", "2"}}) {
            assertEquals(CommandCapability.USE, policy.requiredCapability("redstoneaudit", args));
            String[] bzlArgs = new String[args.length + 1];
            bzlArgs[0] = "audit";
            System.arraycopy(args, 0, bzlArgs, 1, args.length);
            assertEquals(CommandCapability.USE, policy.requiredCapability("bzl", bzlArgs));
        }
    }

    @Test
    void bzlRoutesAndDynamicBrushKindsResolveToNarrowCapabilities() {
        assertEquals(CommandCapability.EDIT,
                policy.requiredCapability("bzl", new String[]{"brush", "paint", "stone"}));
        assertEquals(CommandCapability.GENERATE,
                policy.requiredCapability("bzl", new String[]{"brush", "gen", "ridge"}));
        assertEquals(CommandCapability.GENSTRUCTURE,
                policy.requiredCapability("bzl", new String[]{"brush", "structure", "minecraft:village_plains"}));
        assertEquals(CommandCapability.USE,
                policy.requiredCapability("bzl", new String[]{"help", "unknown-topic"}));
        for (var subcommand : CommandRegistry.getBzlSubcommands()) {
            if (policy.explicitRootCapabilities().containsKey(subcommand.name())) {
                assertEquals(
                        policy.requiredCapability(subcommand.name(), new String[0]),
                        policy.requiredCapability("bzl", new String[]{subcommand.name()}),
                        subcommand.name()
                );
            }
        }
    }

    @Test
    void adminPermissionOverridesWhileMutationPermissionsDefaultToDenied() {
        Predicate<String> unprivileged = permission -> permission.equals(CommandCapability.USE.permission());
        Predicate<String> administrator = permission -> permission.equals(BayzylAccess.ADMIN_PERMISSION);

        assertTrue(access.allowed(unprivileged, CommandCapability.USE));
        for (CommandCapability capability : CommandCapability.values()) {
            if (capability != CommandCapability.USE) {
                assertFalse(access.allowed(unprivileged, capability), capability.name());
            }
            assertTrue(access.allowed(administrator, capability), capability.name());
        }
    }

    @Test
    void runtimeGrantPassesRuntimeRoutesWithoutGrantingAdminMode() {
        Predicate<String> runtimeOnly = permission -> permission.equals(CommandCapability.RUNTIME.permission());

        assertTrue(access.allowed(runtimeOnly, policy.requiredCapability("ramalert", new String[]{"status"})));
        assertTrue(access.allowed(runtimeOnly,
                policy.requiredCapability("bzl", new String[]{"authority", "status"})));
        assertFalse(access.allowed(runtimeOnly,
                policy.requiredCapability("bzltoggle", new String[]{"admin"})));
    }

    @Test
    void mutatingHistoryAndEntityCommandsRequireEdit() {
        for (String command : List.of("undo", "redo", "oops", "susu", "artie")) {
            assertEquals(CommandCapability.EDIT,
                    policy.requiredCapability(command, new String[0]), command);
        }
    }

    @Test
    void environmentNamespaceIsRuntimeButPublicNudgeRemainsUse() {
        assertEquals(CommandCapability.RUNTIME,
                policy.requiredCapability("bzl", new String[]{"env"}));
        assertEquals(CommandCapability.RUNTIME,
                policy.requiredCapability("bzl", new String[]{"env", "history"}));
        assertEquals(CommandCapability.RUNTIME,
                policy.requiredCapability("bzl", new String[]{"env", "nudge"}));
        assertEquals(CommandCapability.USE,
                policy.requiredCapability("bzl", new String[]{"nudge"}));
    }

    @Test
    void unmappedRootsFailClosedExceptForUniversalAdminOverride() {
        Predicate<String> useOnly = permission -> permission.equals(CommandCapability.USE.permission());
        Predicate<String> administrator = permission -> permission.equals(BayzylAccess.ADMIN_PERMISSION);

        CommandCapability unknown = policy.requiredCapability("future-unmapped-root", new String[0]);
        assertFalse(access.allowed(useOnly, unknown));
        assertTrue(access.allowed(administrator, unknown));
    }

    @Test
    void prospectiveSuggestionsAreFilteredByExecutableCapability() {
        Predicate<String> useOnly = permission -> permission.equals(CommandCapability.USE.permission());
        Predicate<String> editOnly = permission -> permission.equals(CommandCapability.EDIT.permission());
        Predicate<String> runtimeOnly = permission -> permission.equals(CommandCapability.RUNTIME.permission());
        Predicate<String> structureOnly = permission -> permission.equals(CommandCapability.GENSTRUCTURE.permission());

        assertEquals(List.of("help", "nudge"), policy.filterAllowedSuggestions(
                useOnly, "bzl", new String[]{""}, List.of("help", "env", "nudge", "brush")));
        assertEquals(List.of("env", "authority"), policy.filterAllowedSuggestions(
                runtimeOnly, "bzl", new String[]{""}, List.of("env", "authority", "nudge", "brush")));
        assertEquals(List.of("paint"), policy.filterAllowedSuggestions(
                editOnly, "bzl", new String[]{"brush", ""}, List.of("paint", "gen", "structure")));
        assertEquals(List.of("structure"), policy.filterAllowedSuggestions(
                structureOnly, "brush", new String[]{""}, List.of("paint", "gen", "structure")));
        assertEquals(List.of("ramalert", "authority"), policy.filterAllowedSuggestions(
                runtimeOnly, "bzltoggle", new String[]{""}, List.of("admin", "ramalert", "authority")));
    }

    @Test
    void sneakingWandScrollRequiresEdit() {
        assertEquals(CommandCapability.EDIT, policy.requiredWandScrollCapability());
    }

    @Test
    void kitSuggestionsHonorExistingNarrowPermissionsAcrossAllRoutes() {
        Predicate<String> useOnly = permissions(CommandCapability.USE.permission());
        Predicate<String> creator = permissions(CommandCapability.USE.permission(), "bayzyl.kit.create");
        Predicate<String> deleter = permissions(CommandCapability.USE.permission(), "bayzyl.kit.delete");
        Predicate<String> admin = permissions(BayzylAccess.ADMIN_PERMISSION);
        List<String> kitActions = List.of(
                "list", "menu", "inspect", "load", "delete", "rename", "duplicate", "update",
                "confirm", "restoredefaults", "note", "theme", "icon", "alias");

        assertEquals(List.of("list", "menu", "inspect", "load"),
                policy.filterAllowedSuggestions(useOnly, "kit", new String[]{""}, kitActions));
        assertEquals(List.of("list", "menu", "inspect", "load", "delete"),
                policy.filterAllowedSuggestions(deleter, "kit", new String[]{""}, kitActions));
        assertEquals(List.of("list", "menu", "inspect", "load", "rename", "duplicate", "update",
                        "confirm", "restoredefaults", "note", "theme", "icon", "alias"),
                policy.filterAllowedSuggestions(creator, "kit", new String[]{""}, kitActions));
        assertEquals(kitActions,
                policy.filterAllowedSuggestions(admin, "kit", new String[]{""}, kitActions));

        assertEquals(List.of(), policy.filterAllowedSuggestions(
                useOnly, "kitmake", new String[]{""}, List.of("hotbar", "inventory")));
        assertEquals(List.of("hotbar", "inventory"), policy.filterAllowedSuggestions(
                creator, "kitmake", new String[]{""}, List.of("hotbar", "inventory")));
        assertEquals(List.of(), policy.filterAllowedSuggestions(
                useOnly, "kitupdate", new String[]{""}, List.of("autumn")));
        assertEquals(List.of("autumn"), policy.filterAllowedSuggestions(
                creator, "kitupdate", new String[]{""}, List.of("autumn")));

        List<String> bzlFirstTokens = List.of("kit", "kitmake", "kitupdate", "kitconfirm", "autumn");
        assertEquals(List.of("kit", "autumn"), policy.filterAllowedSuggestions(
                useOnly, "bzl", new String[]{""}, bzlFirstTokens));
        assertEquals(bzlFirstTokens, policy.filterAllowedSuggestions(
                creator, "bzl", new String[]{""}, bzlFirstTokens));
        assertEquals(List.of(), policy.filterAllowedSuggestions(
                useOnly, "bzl", new String[]{"kit", "delete", ""}, List.of("autumn")));
        assertEquals(List.of("autumn"), policy.filterAllowedSuggestions(
                deleter, "bzl", new String[]{"kit", "delete", ""}, List.of("autumn")));
    }

    @Test
    void nestedAuthorityToggleSuggestionsSurviveProspectiveFiltering() {
        Predicate<String> runtime = permissions(CommandCapability.RUNTIME.permission());

        assertEquals(List.of("status", "claim", "giveup"),
                policy.toggleSuggestions(new String[]{"authority", ""}));
        assertEquals(List.of("claim"),
                policy.toggleSuggestions(new String[]{"authority", "cl"}));
        assertEquals(List.of("status", "claim", "giveup"),
                policy.filterAllowedSuggestions(runtime, "bzltoggle", new String[]{"authority", ""},
                        policy.toggleSuggestions(new String[]{"authority", ""})));
    }

    private static Predicate<String> permissions(String... granted) {
        Set<String> permissions = Set.of(granted);
        return permissions::contains;
    }

    @Test
    void toolAndDynamicShortcutActionsUseNarrowCapabilities() {
        assertEquals(CommandCapability.USE, policy.requiredToolCapability(ToolType.WAND));
        assertEquals(CommandCapability.GENSTRUCTURE, policy.requiredToolCapability(ToolType.STRUCTURE_BRUSH));
        assertEquals(CommandCapability.GENERATE, policy.requiredToolCapability(ToolType.GEN_BRUSH));
        for (ToolType toolType : ToolType.values()) {
            if (toolType != ToolType.WAND
                    && toolType != ToolType.STRUCTURE_BRUSH
                    && toolType != ToolType.GEN_BRUSH) {
                assertEquals(CommandCapability.EDIT, policy.requiredToolCapability(toolType), toolType.name());
            }
        }
        assertEquals(CommandCapability.EDIT, policy.requiredDetailShortcutCapability());
        assertEquals(CommandCapability.USE, policy.requiredKitShortcutCapability());
    }

    @Test
    void descriptorDeclaresCapabilityPermissionsWithSafeDefaults() throws IOException {
        Map<String, String> defaults = readPermissionDefaults();
        assertEquals("true", defaults.get(CommandCapability.USE.permission()));
        for (CommandCapability capability : CommandCapability.values()) {
            if (capability != CommandCapability.USE) {
                assertEquals("op", defaults.get(capability.permission()), capability.permission());
            }
        }
        assertEquals("op", defaults.get(BayzylAccess.ADMIN_PERMISSION));
    }

    private static Map<String, String> readAliases() throws IOException {
        Path pluginYaml = Path.of("src", "main", "resources", "plugin.yml");
        List<String> lines = Files.readAllLines(pluginYaml, StandardCharsets.UTF_8);
        Map<String, String> aliases = new LinkedHashMap<>();
        boolean inCommands = false;
        String currentRoot = null;
        for (String line : lines) {
            if (line.equals("commands:")) {
                inCommands = true;
                continue;
            }
            if (inCommands && !line.startsWith("  ")) {
                break;
            }
            if (line.matches("  [a-z0-9]+:")) {
                currentRoot = line.substring(2, line.length() - 1);
                continue;
            }
            if (currentRoot != null && line.startsWith("    aliases: [")) {
                String values = line.substring(line.indexOf('[') + 1, line.lastIndexOf(']'));
                for (String alias : values.split(",")) {
                    aliases.put(alias.trim(), currentRoot);
                }
            }
        }
        return aliases;
    }

    private static Map<String, String> readPermissionDefaults() throws IOException {
        Path pluginYaml = Path.of("src", "main", "resources", "plugin.yml");
        Map<String, String> defaults = new LinkedHashMap<>();
        boolean inPermissions = false;
        String currentPermission = null;
        for (String line : Files.readAllLines(pluginYaml, StandardCharsets.UTF_8)) {
            if (line.equals("permissions:")) {
                inPermissions = true;
                continue;
            }
            if (!inPermissions) {
                continue;
            }
            if (line.matches("  [a-z0-9.]+:")) {
                currentPermission = line.substring(2, line.length() - 1);
            } else if (currentPermission != null && line.startsWith("    default: ")) {
                defaults.put(currentPermission, line.substring("    default: ".length()).trim());
            }
        }
        return defaults;
    }
}
