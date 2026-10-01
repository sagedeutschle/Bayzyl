package com.bayzyl.security;

import com.bayzyl.AdminModeService;
import com.bayzyl.AutoUnstickService;
import com.bayzyl.BuilderProfileLoadSection;
import com.bayzyl.BuilderProfileService;
import com.bayzyl.GhostHandService;
import com.bayzyl.MessageThemeService;
import com.bayzyl.NightVisionService;
import com.bayzyl.NudgeSettingsService;
import com.bayzyl.RecentEditTrailService;
import com.bayzyl.RuntimePreferencesService;
import com.bayzyl.StackAutoMoveService;
import com.bayzyl.StackLookDirectionService;
import com.bayzyl.TabInfoPanelService;
import com.bayzyl.TabMenuSettingsService;
import com.bayzyl.ToolManager;
import com.bayzyl.VisualizationManager;
import com.bayzyl.VisualizationSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AdminModeYamlPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimeLegacyAdminFlagCannotActivateSessionWhenPreferencesLoad() throws IOException {
        UUID playerId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("runtime-preferences.yml"), """
                players:
                  %s:
                    admin_mode: true
                    night_vision: false
                """.formatted(playerId));
        JavaPlugin plugin = pluginAt(tempDir);
        Player player = player(playerId);
        VisualizationManager visualization = mock(VisualizationManager.class);
        when(visualization.getSettings(playerId)).thenReturn(new VisualizationSettings());
        RecentEditTrailService trail = mock(RecentEditTrailService.class);
        when(trail.defaultLimit()).thenReturn(5);
        AdminModeService admin = new AdminModeService();
        admin.setEnabled(playerId, true);
        admin.beginSession(playerId);

        new RuntimePreferencesService(plugin).loadPlayer(
                player,
                mock(NightVisionService.class),
                mock(AutoUnstickService.class),
                mock(GhostHandService.class),
                mock(StackLookDirectionService.class),
                mock(StackAutoMoveService.class),
                mock(NudgeSettingsService.class),
                visualization,
                mock(TabMenuSettingsService.class),
                trail
        );

        assertFalse(admin.isEnabled(playerId));
    }

    @Test
    void currentAndLegacyProfileAdminFlagsCannotActivateSessionWhenApplied() throws IOException {
        Files.writeString(tempDir.resolve("profiles.yml"), """
                profiles:
                  current:
                    version: 1
                    type: config
                    config:
                      admin_mode: true
                      selection_particles:
                        enabled: true
                  legacy:
                    adminModeEnabled: true
                    selectionParticlesEnabled: true
                """);
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        JavaPlugin plugin = pluginAt(tempDir);
        VisualizationManager visualization = mock(VisualizationManager.class);
        when(visualization.getSettings(playerId)).thenReturn(new VisualizationSettings());
        MessageThemeService theme = mock(MessageThemeService.class);
        when(theme.accentValue()).thenReturn("gold");
        RecentEditTrailService trail = mock(RecentEditTrailService.class);
        when(trail.defaultLimit()).thenReturn(5);
        BuilderProfileService profiles = new BuilderProfileService(
                plugin, visualization, mock(NightVisionService.class), mock(AutoUnstickService.class),
                mock(GhostHandService.class), mock(StackLookDirectionService.class),
                mock(NudgeSettingsService.class), mock(TabMenuSettingsService.class), trail,
                mock(TabInfoPanelService.class), theme, mock(ToolManager.class)
        );
        AdminModeService admin = new AdminModeService();
        admin.setEnabled(playerId, true);
        admin.beginSession(playerId);

        assertNotNull(profiles.applyProfile(player, "current", BuilderProfileLoadSection.CONFIG));
        assertFalse(admin.isEnabled(playerId));
        assertNotNull(profiles.applyProfile(player, "legacy", BuilderProfileLoadSection.CONFIG));
        assertFalse(admin.isEnabled(playerId));
    }

    private static JavaPlugin pluginAt(Path directory) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        return plugin;
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }
}
