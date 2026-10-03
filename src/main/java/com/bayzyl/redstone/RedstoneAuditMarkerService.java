package com.bayzyl.redstone;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary, per-player highlights for audit results. Only the requesting player sees them (particles are sent to
 * that player alone), they expire after {@link #LIFETIME_TICKS}, and nothing is placed in the world. Main thread.
 */
public final class RedstoneAuditMarkerService implements RedstoneAuditCommand.MarkerSink {
    public static final long LIFETIME_TICKS = 20L * 30L;
    public static final long PERIOD_TICKS = 10L;
    private static final int MAX_MARKERS = 24;

    /** Starts a repeating task; the returned runnable cancels it. */
    @FunctionalInterface
    public interface Ticker {
        Runnable start(Runnable tick, long periodTicks);
    }

    /** Draws one marker for one player. */
    @FunctionalInterface
    public interface ParticleSink {
        void spawn(Player player, AuditPosition position, AuditConfidence confidence);
    }

    private record Markers(Player player, List<AuditFinding> findings, UUID worldId, long expiresAt) {
    }

    private final Ticker ticker;
    private final ParticleSink particles;
    private final Map<UUID, Markers> active = new LinkedHashMap<>();
    private Runnable cancel;
    private long now;

    /** Red/orange/yellow dust at the block centre, sent to the one player only. */
    public static ParticleSink dustParticles() {
        return (player, position, confidence) -> {
            org.bukkit.Color colour = switch (confidence) {
                case HIGH -> org.bukkit.Color.RED;
                case MEDIUM -> org.bukkit.Color.ORANGE;
                case LOW -> org.bukkit.Color.YELLOW;
            };
            player.spawnParticle(org.bukkit.Particle.DUST, position.x() + 0.5, position.y() + 0.5, position.z() + 0.5,
                    8, 0.25, 0.25, 0.25, 0, new org.bukkit.Particle.DustOptions(colour, 1.3f));
        };
    }

    public RedstoneAuditMarkerService(Ticker ticker, ParticleSink particles) {
        this.ticker = ticker;
        this.particles = particles;
    }

    @Override
    public void show(Player player, List<AuditFinding> findings, UUID worldId) {
        List<AuditFinding> shown = findings.size() > MAX_MARKERS ? findings.subList(0, MAX_MARKERS) : findings;
        active.put(player.getUniqueId(), new Markers(player, List.copyOf(shown), worldId, now + LIFETIME_TICKS));
        if (cancel == null) {
            cancel = ticker.start(this::tick, PERIOD_TICKS);
        }
    }

    @Override
    public void clear(UUID playerId) {
        active.remove(playerId);
        stopIfIdle();
    }

    /** Remove every marker (plugin disable). */
    public void clearAll() {
        active.clear();
        stopIfIdle();
    }

    private void tick() {
        now += PERIOD_TICKS;
        Iterator<Markers> iterator = active.values().iterator();
        while (iterator.hasNext()) {
            Markers markers = iterator.next();
            if (now > markers.expiresAt() || !markers.player().isOnline()) {
                iterator.remove();
                continue;
            }
            if (markers.worldId() != null && !inWorld(markers.player(), markers.worldId())) {
                continue;
            }
            for (AuditFinding finding : markers.findings()) {
                particles.spawn(markers.player(), finding.position(), finding.confidence());
            }
        }
        stopIfIdle();
    }

    /** Markers sit at the audited coordinates, so they are only drawn while the player is in that world. */
    private static boolean inWorld(Player player, UUID worldId) {
        World world = player.getWorld();
        return world != null && worldId.equals(world.getUID());
    }

    private void stopIfIdle() {
        if (active.isEmpty() && cancel != null) {
            cancel.run();
            cancel = null;
        }
    }
}
