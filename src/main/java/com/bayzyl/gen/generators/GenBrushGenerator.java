package com.bayzyl.gen.generators;

import com.bayzyl.BlockMask;
import com.bayzyl.gen.GenBrushParameters;
import com.bayzyl.gen.GenBrushSettings;
import com.bayzyl.gen.GenChangeCollector;
import com.bayzyl.gen.env.EnvironmentSample;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Contract every gen-brush generator implements. Generators receive a
 * deterministic seed mixed from player + click counter, a probed
 * environment sample, and a staged change collector. They write into
 * the collector and return — the service does the history record.
 */
public interface GenBrushGenerator {
    String id();

    /**
     * Mutate {@code collector} with the blocks this generator wants to
     * place. The collector is already wired to the right world and
     * honors bedrock protection.
     */
    void generate(GenBrushContext ctx);

    /**
     * @deprecated Safety estimates are centralized in
     * {@link com.bayzyl.gen.GenBrushSafety}.
     */
    @Deprecated
    default long estimateChanges(GenBrushSettings settings) {
        return com.bayzyl.gen.GenBrushSafety.assess(settings).workUnits();
    }

    /** Bundle of inputs each generator needs. Keeps the {@code generate} signature short. */
    final class GenBrushContext {
        public final Player player;
        public final Location target;
        public final GenBrushSettings settings;
        public final GenBrushParameters params;
        public final EnvironmentSample env;
        public final GenChangeCollector changes;
        public final long seed;
        public final BlockMask mask;

        public GenBrushContext(Player player, Location target, GenBrushSettings settings,
                               EnvironmentSample env, GenChangeCollector changes, long seed) {
            this.player = player;
            this.target = target;
            this.settings = settings;
            this.params = settings.parameters();
            this.env = env;
            this.changes = changes;
            this.seed = seed;
            this.mask = settings.mask();
        }

        public boolean maskAllows(org.bukkit.Material material) {
            if (mask == null) {
                return true;
            }
            return mask.matches(material);
        }
    }
}
