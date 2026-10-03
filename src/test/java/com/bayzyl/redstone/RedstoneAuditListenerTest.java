package com.bayzyl.redstone;

import com.bayzyl.redstone.RedstoneAuditSnapshotFactory.Capture;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedstoneAuditListenerTest {
    @Test
    void quittingDropsThatPlayersResultsAndMarkers() {
        List<UUID> cleared = new ArrayList<>();
        RedstoneAuditCommand command = new RedstoneAuditCommand(
                new RedstoneAuditService(List.of(snapshot -> List.of(new AuditFinding(new AuditPosition(1, 1, 1),
                        AuditConfidence.HIGH, AuditSeverity.BREAKS, "t", "Finding", "p", "s")))),
                player -> Capture.of(RedstoneAuditSnapshot.builder(new AuditPosition(0, 0, 0), new AuditPosition(3, 3, 3)).build()),
                new RedstoneAuditCommand.MarkerSink() {
                    @Override
                    public void show(Player player, List<AuditFinding> findings, java.util.UUID worldId) {
                    }

                    @Override
                    public void clear(UUID playerId) {
                        cleared.add(playerId);
                    }
                });
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        command.handle(player, new String[0]);

        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        new RedstoneAuditListener(command).onQuit(quit);

        assertEquals(List.of(id), cleared);
        command.handle(player, new String[]{"show", "1"});
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(player, atLeast(1)).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().stream().anyMatch(text -> text.contains("first")), messages.getAllValues().toString());
    }
}
