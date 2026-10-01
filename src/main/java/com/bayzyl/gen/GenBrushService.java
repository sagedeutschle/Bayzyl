package com.bayzyl.gen;

import com.bayzyl.BlockChange;
import com.bayzyl.HistoryService;
import com.bayzyl.gen.env.EnvironmentProbe;
import com.bayzyl.gen.env.EnvironmentSample;
import com.bayzyl.gen.generators.GenBrushGenerator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Orchestrator for parametric gen brush clicks. Owns the click counter,
 * dispatch to the generator, history record, and a small cooldown so
 * holding right-click doesn't fire dozens of overlapping stamps per
 * second.
 */
public final class GenBrushService {
    private static final long CLICK_COOLDOWN_MS = 250L;
    /** @deprecated Use {@link com.bayzyl.safety.OperationLimits#CONFIRMATION_THRESHOLD}. */
    @Deprecated
    public static final long CONFIRM_VOLUME = com.bayzyl.safety.OperationLimits.CONFIRMATION_THRESHOLD;
    /** @deprecated Use {@link com.bayzyl.safety.OperationLimits#GEN_BRUSH_HARD_MAX}. */
    @Deprecated
    public static final long HARD_REFUSE_VOLUME = com.bayzyl.safety.OperationLimits.GEN_BRUSH_HARD_MAX;

    private final GenBrushRegistry registry;
    private final HistoryService historyService;
    private final Plugin plugin;
    private final EnvironmentSampler environmentSampler;
    private final LongSupplier clock;
    private final GenerationBackend generationBackend;
    private final AtomicLong stampCounter = new AtomicLong(0);
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public GenBrushService(GenBrushRegistry registry, HistoryService historyService, Plugin plugin) {
        this(registry, historyService, plugin,
                GenBrushService::sampleEnvironment,
                System::currentTimeMillis,
                GenBrushService::generate);
    }

    GenBrushService(
            GenBrushRegistry registry,
            HistoryService historyService,
            Plugin plugin,
            EnvironmentSampler environmentSampler,
            LongSupplier clock,
            GenerationBackend generationBackend
    ) {
        this.registry = registry;
        this.historyService = historyService;
        this.plugin = plugin;
        this.environmentSampler = environmentSampler;
        this.clock = clock;
        this.generationBackend = generationBackend;
    }

    public GenBrushRegistry registry() {
        return registry;
    }

    public ApplyResult apply(Player player, Location target, GenBrushSettings settings) {
        return apply(player, target, settings, GenBrushSafety.storedConfirmation(settings));
    }

    public ApplyResult apply(
            Player player,
            Location target,
            GenBrushSettings settings,
            boolean effectiveConfirmation
    ) {
        com.bayzyl.safety.WorkEstimate estimate = GenBrushSafety.assess(settings);
        if (!estimate.permits(effectiveConfirmation)) {
            return ApplyResult.failure(estimate.reason()
                    + (estimate.confirmationRequired() ? " Rebind with confirm:true." : ""));
        }
        if (player == null) {
            return ApplyResult.failure("No player available.");
        }
        if (target == null || target.getWorld() == null) {
            return ApplyResult.failure("No valid target.");
        }

        GenBrushGenerator generator = registry.get(settings.type());
        if (generator == null) {
            return ApplyResult.failure("No generator for type " + settings.type().commandName());
        }

        long now = clock.getAsLong();
        UUID playerId = player.getUniqueId();
        Long last = lastClick.get(playerId);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return ApplyResult.cooldownResult();
        }
        lastClick.put(playerId, now);

        EnvironmentSample env;
        List<BlockChange> committed;
        try {
            env = environmentSampler.sample(target, settings.adaptToEnvironment());
            long seed = mixSeed(settings.seed(), stampCounter.incrementAndGet(),
                    playerId.getMostSignificantBits());
            committed = generationBackend.generate(
                    player, target, settings, env, generator, seed);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Gen brush " + settings.type().commandName()
                    + " threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return ApplyResult.failure("Generator failed: " + ex.getMessage());
        }
        if (committed == null) {
            committed = List.of();
        }
        if (!committed.isEmpty()) {
            historyService.record(playerId, committed);
        }
        return ApplyResult.success(committed.size(), env);
    }

    private static EnvironmentSample sampleEnvironment(Location target, boolean adapt) {
        return adapt ? EnvironmentProbe.probe(target) : EnvironmentProbe.probe(target, 4, 4);
    }

    private static List<BlockChange> generate(
            Player player,
            Location target,
            GenBrushSettings settings,
            EnvironmentSample environment,
            GenBrushGenerator generator,
            long seed
    ) {
        World world = target.getWorld();
        GenChangeCollector changes = new GenChangeCollector(world, false);
        GenBrushGenerator.GenBrushContext context = new GenBrushGenerator.GenBrushContext(
                player, target, settings, environment, changes, seed);
        generator.generate(context);
        return changes.commit();
    }

    private static long mixSeed(long seed, long counter, long playerBits) {
        long h = seed;
        h ^= counter * 0x9E3779B97F4A7C15L;
        h ^= playerBits * 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }

    public record ApplyResult(boolean success, boolean cooldown, String message,
                              int changedBlocks, EnvironmentSample env) {
        public static ApplyResult success(int changed, EnvironmentSample env) {
            return new ApplyResult(true, false, "", changed, env);
        }

        public static ApplyResult failure(String message) {
            return new ApplyResult(false, false, message, 0, null);
        }

        public static ApplyResult cooldownResult() {
            return new ApplyResult(false, true, "cooldown", 0, null);
        }
    }

    @FunctionalInterface
    public interface EnvironmentSampler {
        EnvironmentSample sample(Location target, boolean adaptToEnvironment);
    }

    @FunctionalInterface
    public interface GenerationBackend {
        List<BlockChange> generate(
                Player player,
                Location target,
                GenBrushSettings settings,
                EnvironmentSample environment,
                GenBrushGenerator generator,
                long seed);
    }
}
