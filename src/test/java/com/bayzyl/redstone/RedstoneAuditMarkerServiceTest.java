package com.bayzyl.redstone;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedstoneAuditMarkerServiceTest {
    private record Spawn(Player player, AuditPosition position) {
    }

    private final List<Spawn> spawns = new ArrayList<>();
    private final FakeTicker ticker = new FakeTicker();
    private final RedstoneAuditMarkerService markers = new RedstoneAuditMarkerService(ticker,
            (player, position, confidence) -> spawns.add(new Spawn(player, position)));

    @Test
    void markersAreDrawnOnlyForTheRequestingPlayer() {
        Player alice = player();
        Player bob = player();
        markers.show(alice, List.of(finding(1, 2, 3)));

        ticker.tick();

        assertEquals(List.of(new Spawn(alice, new AuditPosition(1, 2, 3))), spawns);
    }

    @Test
    void clearingRemovesOnlyTheCallersMarkers() {
        Player alice = player();
        Player bob = player();
        markers.show(alice, List.of(finding(1, 0, 0)));
        markers.show(bob, List.of(finding(2, 0, 0)));

        markers.clear(alice.getUniqueId());
        ticker.tick();

        assertEquals(List.of(new Spawn(bob, new AuditPosition(2, 0, 0))), spawns);
    }

    @Test
    void showingAgainReplacesThePreviousHighlight() {
        Player alice = player();
        markers.show(alice, List.of(finding(1, 0, 0), finding(2, 0, 0)));
        markers.show(alice, List.of(finding(2, 0, 0)));

        ticker.tick();

        assertEquals(List.of(new Spawn(alice, new AuditPosition(2, 0, 0))), spawns);
    }

    @Test
    void markersExpireAndTheTickerStopsWhenNothingIsLeft() {
        Player alice = player();
        markers.show(alice, List.of(finding(1, 0, 0)));
        assertTrue(ticker.running());

        for (long elapsed = 0; elapsed <= RedstoneAuditMarkerService.LIFETIME_TICKS; elapsed += RedstoneAuditMarkerService.PERIOD_TICKS) {
            ticker.tick();
        }
        spawns.clear();
        ticker.tick();

        assertTrue(spawns.isEmpty());
        assertFalse(ticker.running());
    }

    @Test
    void offlinePlayersLoseTheirMarkers() {
        Player alice = player();
        markers.show(alice, List.of(finding(1, 0, 0)));
        when(alice.isOnline()).thenReturn(false);

        ticker.tick();

        assertTrue(spawns.isEmpty());
        assertFalse(ticker.running());
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static AuditFinding finding(int x, int y, int z) {
        return new AuditFinding(new AuditPosition(x, y, z), AuditConfidence.HIGH, AuditSeverity.BREAKS, "t", "t", "p", "s");
    }

    private static final class FakeTicker implements RedstoneAuditMarkerService.Ticker {
        private Runnable task;

        @Override
        public Runnable start(Runnable tick, long periodTicks) {
            task = tick;
            return () -> task = null;
        }

        void tick() {
            if (task != null) {
                task.run();
            }
        }

        boolean running() {
            return task != null;
        }
    }
}
