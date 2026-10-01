package com.bayzyl.gen;

import com.bayzyl.HistoryService;
import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.generators.GenBrushGenerator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class GenBrushServiceSafetyTest {
    @Test
    void hardAndSoftSafetyRefusalsPrecedeEveryRuntimeDependency() {
        Fixture fixture = fixture();
        Player player = mock(Player.class);
        Location target = mock(Location.class);

        GenBrushService.ApplyResult hard = fixture.service.apply(
                player, target, settings(36, false), true);
        GenBrushService.ApplyResult soft = fixture.service.apply(
                player, target, settings(23, false), false);
        GenBrushSettings oversizedParameters = new GenBrushSettings(
                GenBrushType.ERODE, CaveSubtype.AUTO, 10,
                new GenBrushParameters(Map.of("passes", "41")),
                null, true, 99L);
        GenBrushService.ApplyResult oversized = fixture.service.apply(
                player, target, oversizedParameters, true);

        assertFalse(hard.success());
        assertFalse(soft.success());
        assertFalse(oversized.success());
        verifyNoInteractions(player, target, fixture.registry, fixture.history,
                fixture.plugin, fixture.sampler, fixture.backend, fixture.clock);
    }

    @Test
    void storedConfirmationAndEffectiveAdminConfirmationBypassOnlyTheSoftGate() {
        Fixture storedFixture = readyFixture();
        GenBrushService.ApplyResult stored = storedFixture.service.apply(
                storedFixture.player, storedFixture.target, settings(23, true));
        assertTrue(stored.success());

        Fixture adminFixture = readyFixture();
        GenBrushService.ApplyResult admin = adminFixture.service.apply(
                adminFixture.player, adminFixture.target, settings(23, false), true);
        assertTrue(admin.success());

        Fixture hardFixture = fixture();
        GenBrushService.ApplyResult hard = hardFixture.service.apply(
                mock(Player.class), mock(Location.class), settings(36, true), true);
        assertFalse(hard.success());
        verifyNoInteractions(hardFixture.registry, hardFixture.history,
                hardFixture.sampler, hardFixture.backend, hardFixture.clock);
    }

    @Test
    void safetyRefusalConsumesNeitherCooldownNorStampCounter() {
        Fixture afterRefusal = readyFixture();
        GenBrushService.ApplyResult rejected = afterRefusal.service.apply(
                afterRefusal.player, afterRefusal.target, settings(23, false));
        GenBrushService.ApplyResult accepted = afterRefusal.service.apply(
                afterRefusal.player, afterRefusal.target, settings(22, false));

        Fixture fresh = readyFixture();
        GenBrushService.ApplyResult freshAccepted = fresh.service.apply(
                fresh.player, fresh.target, settings(22, false));

        assertFalse(rejected.success());
        assertTrue(accepted.success());
        assertTrue(freshAccepted.success());
        assertEquals(fresh.capturedSeed.get(), afterRefusal.capturedSeed.get());
    }

    @Test
    void sharedSafetyEstimateReplacesGeneratorOwnedCompetingCaps() {
        Fixture fixture = readyFixture();

        assertTrue(fixture.service.apply(
                fixture.player, fixture.target, settings(22, false)).success());

        verify(fixture.generator, never()).estimateChanges(any());
    }

    private static Fixture readyFixture() {
        Fixture fixture = fixture();
        fixture.player = mock(Player.class);
        fixture.target = mock(Location.class);
        World world = mock(World.class);
        UUID playerId = new UUID(11L, 29L);
        when(fixture.player.getUniqueId()).thenReturn(playerId);
        when(fixture.target.getWorld()).thenReturn(world);
        when(fixture.registry.get(GenBrushType.RIDGE)).thenReturn(fixture.generator);
        when(fixture.sampler.sample(fixture.target, true)).thenReturn(fixture.environment);
        return fixture;
    }

    private static Fixture fixture() {
        GenBrushRegistry registry = mock(GenBrushRegistry.class);
        HistoryService history = mock(HistoryService.class);
        Plugin plugin = mock(Plugin.class);
        GenBrushService.EnvironmentSampler sampler = mock(GenBrushService.EnvironmentSampler.class);
        GenBrushService.GenerationBackend backend = mock(GenBrushService.GenerationBackend.class);
        LongSupplier clock = mock(LongSupplier.class);
        GenBrushGenerator generator = mock(GenBrushGenerator.class);
        EnvironmentSample environment = mock(EnvironmentSample.class);
        AtomicLong capturedSeed = new AtomicLong(Long.MIN_VALUE);
        when(clock.getAsLong()).thenReturn(1_000L);
        when(backend.generate(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            capturedSeed.set(invocation.getArgument(5));
            return List.of();
        });
        GenBrushService service = new GenBrushService(
                registry, history, plugin, sampler, clock, backend);
        return new Fixture(service, registry, history, plugin, sampler, backend,
                clock, generator, environment, capturedSeed);
    }

    private static GenBrushSettings settings(int radius, boolean confirmed) {
        return new GenBrushSettings(
                GenBrushType.RIDGE,
                CaveSubtype.AUTO,
                radius,
                new GenBrushParameters(Map.of("confirm", Boolean.toString(confirmed))),
                null,
                true,
                99L);
    }

    private static final class Fixture {
        private final GenBrushService service;
        private final GenBrushRegistry registry;
        private final HistoryService history;
        private final Plugin plugin;
        private final GenBrushService.EnvironmentSampler sampler;
        private final GenBrushService.GenerationBackend backend;
        private final LongSupplier clock;
        private final GenBrushGenerator generator;
        private final EnvironmentSample environment;
        private final AtomicLong capturedSeed;
        private Player player;
        private Location target;

        private Fixture(
                GenBrushService service,
                GenBrushRegistry registry,
                HistoryService history,
                Plugin plugin,
                GenBrushService.EnvironmentSampler sampler,
                GenBrushService.GenerationBackend backend,
                LongSupplier clock,
                GenBrushGenerator generator,
                EnvironmentSample environment,
                AtomicLong capturedSeed
        ) {
            this.service = service;
            this.registry = registry;
            this.history = history;
            this.plugin = plugin;
            this.sampler = sampler;
            this.backend = backend;
            this.clock = clock;
            this.generator = generator;
            this.environment = environment;
            this.capturedSeed = capturedSeed;
        }
    }
}
