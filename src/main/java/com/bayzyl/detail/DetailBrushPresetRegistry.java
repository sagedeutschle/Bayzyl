package com.bayzyl.detail;

import com.bayzyl.detail.presets.BarkPreset;
import com.bayzyl.detail.presets.CloudPreset;
import com.bayzyl.detail.presets.FlamePreset;
import com.bayzyl.detail.presets.HeartsPreset;
import com.bayzyl.detail.presets.LightningPreset;
import com.bayzyl.detail.presets.RainbowPreset;
import com.bayzyl.detail.presets.VinePreset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DetailBrushPresetRegistry {
    private final Map<String, DetailBrushPreset> presets = new LinkedHashMap<>();
    private final Map<String, DetailBrushVariant> builtInVariants = new LinkedHashMap<>();

    public DetailBrushPresetRegistry() {
        DetailBrushPreset flame = new FlamePreset();
        register(flame);
        registerFlameVariants(flame);

        DetailBrushPreset cloud = new CloudPreset();
        register(cloud);
        registerCloudVariants(cloud);

        DetailBrushPreset lightning = new LightningPreset();
        register(lightning);
        registerLightningVariants(lightning);

        DetailBrushPreset vine = new VinePreset();
        register(vine);
        registerVineVariants(vine);

        DetailBrushPreset bark = new BarkPreset();
        register(bark);
        registerBarkVariants(bark);

        DetailBrushPreset hearts = new HeartsPreset();
        register(hearts);
        registerHeartsVariants(hearts);

        DetailBrushPreset rainbow = new RainbowPreset();
        register(rainbow);
        registerRainbowVariants(rainbow);
    }

    private void registerHeartsVariants(DetailBrushPreset hearts) {
        registerVariant(hearts, "valentine", "Valentine Heart",
                "Classic single heart in red+pink, modest sparkle.",
                params("size", "5", "count", "1", "spread", "0",
                        "density", "0.95", "sparkle_density", "0.35",
                        "tone", "mixed", "rotation", "face"));
        registerVariant(hearts, "lovestorm", "Lovestorm",
                "Cluster of magenta hearts with heavy sparkle.",
                params("size", "4", "count", "4", "spread", "5",
                        "density", "0.9", "sparkle_density", "0.85",
                        "tone", "magenta", "rotation", "face"));
        registerVariant(hearts, "sparkle-pop", "Sparkle Pop",
                "Small fast hearts with heavy sparkle for accents.",
                params("size", "3", "count", "3", "spread", "3",
                        "density", "0.95", "sparkle_density", "1.0",
                        "tone", "mixed", "rotation", "face"));
        registerVariant(hearts, "single-pink", "Single Pink",
                "One clean pink heart, no sparkles.",
                params("size", "5", "count", "1", "spread", "0",
                        "density", "0.95", "sparkle_density", "0.0",
                        "tone", "pink", "rotation", "face"));
        registerVariant(hearts, "crush", "Crush",
                "Large red+pink statement heart with light sparkle.",
                params("size", "8", "count", "1", "spread", "0",
                        "density", "0.95", "sparkle_density", "0.25",
                        "tone", "mixed", "rotation", "face"));
    }

    private void registerRainbowVariants(DetailBrushPreset rainbow) {
        registerVariant(rainbow, "classic-rainbow", "Classic Rainbow",
                "Seven-band concrete rainbow with cloud puffs at each end.",
                params("length", "24", "arc_height", "10", "bands", "7",
                        "density", "0.95", "sparkle", "0.0", "cloud_size", "4",
                        "palette", "classic", "heading", "auto"));
        registerVariant(rainbow, "double-rainbow", "Double Rainbow",
                "Wider arc, taller peak, bigger end clouds.",
                params("length", "48", "arc_height", "18", "bands", "7",
                        "density", "0.95", "sparkle", "0.15", "cloud_size", "6",
                        "palette", "classic", "heading", "auto"));
        registerVariant(rainbow, "pastel-arc", "Pastel Arc",
                "Soft wool pastels with light clouds — friendlier look.",
                params("length", "20", "arc_height", "8", "bands", "7",
                        "density", "0.92", "sparkle", "0.0", "cloud_size", "3",
                        "palette", "pastel", "heading", "auto"));
        registerVariant(rainbow, "sunset-rainbow", "Sunset Rainbow",
                "Warm-shifted bands for evening skies.",
                params("length", "28", "arc_height", "12", "bands", "7",
                        "density", "0.95", "sparkle", "0.05", "cloud_size", "4",
                        "palette", "sunset", "heading", "auto"));
        registerVariant(rainbow, "nimbus", "Nimbus",
                "Short arc with oversized cumulus cloud anchors.",
                params("length", "14", "arc_height", "6", "bands", "5",
                        "density", "0.85", "sparkle", "0.0", "cloud_size", "8",
                        "palette", "classic", "heading", "auto"));
    }

    private void registerLightningVariants(DetailBrushPreset lightning) {
        registerVariant(lightning, "thunderbolt", "Thunderbolt", "Classic jagged downward strike with two branches.",
                params("length", "24", "jaggedness", "0.62", "branches", "3", "branch_length", "0.5", "glow", "0.82", "direction", "down", "color", "blue"));
        registerVariant(lightning, "forked-bolt", "Forked Bolt", "Heavy fork shape with multiple branches.",
                params("length", "20", "jaggedness", "0.75", "branches", "6", "branch_length", "0.62", "glow", "0.72", "direction", "down", "color", "purple"));
        registerVariant(lightning, "spark", "Spark", "Tiny crackle for detail work and accents.",
                params("length", "7", "jaggedness", "0.85", "branches", "2", "branch_length", "0.45", "glow", "0.9", "direction", "down", "color", "white"));
        registerVariant(lightning, "groundstrike", "Ground Strike", "Long straight bolt for cinematic strikes.",
                params("length", "36", "jaggedness", "0.36", "branches", "2", "branch_length", "0.42", "glow", "0.82", "direction", "down", "color", "yellow"));
    }

    private void registerVineVariants(DetailBrushPreset vine) {
        registerVariant(vine, "jungle-vine", "Jungle Vine", "Long, wandering jungle drape with leaves.",
                params("length", "10", "droop", "0.6", "leaf_density", "0.45", "gap", "0.1", "species", "jungle"));
        registerVariant(vine, "ivy", "Ivy", "Short oak ivy strand for ruins and stone walls.",
                params("length", "5", "droop", "0.3", "leaf_density", "0.4", "gap", "0.15", "species", "oak"));
        registerVariant(vine, "azalea-drape", "Azalea Drape", "Flowering drape for lush biome details.",
                params("length", "7", "droop", "0.45", "leaf_density", "0.55", "gap", "0.08", "species", "flowering_azalea"));
        registerVariant(vine, "mangrove-root", "Mangrove Root", "Sparse mangrove drop with leaf clumps.",
                params("length", "12", "droop", "0.35", "leaf_density", "0.3", "gap", "0.2", "species", "mangrove_root"));
    }

    private void registerBarkVariants(DetailBrushPreset bark) {
        registerVariant(bark, "oak-bark", "Oak Bark Detailing", "Mixed oak log/wood/stripped grain for trunks.",
                params("radius", "3", "height", "5", "grain", "0.55", "knots", "0.2", "coverage", "0.7", "species", "oak"));
        registerVariant(bark, "spruce-bark", "Spruce Bark Detailing", "Vertical spruce grain with occasional knots.",
                params("radius", "3", "height", "6", "grain", "0.7", "knots", "0.18", "coverage", "0.75", "species", "spruce"));
        registerVariant(bark, "birch-bark", "Birch Bark Detailing", "Subtle birch grain with light stripped accents.",
                params("radius", "3", "height", "5", "grain", "0.4", "knots", "0.12", "coverage", "0.65", "species", "birch"));
        registerVariant(bark, "cherry-bark", "Cherry Bark Detailing", "Cherry trunk grain for ornamental builds.",
                params("radius", "3", "height", "5", "grain", "0.5", "knots", "0.15", "coverage", "0.7", "species", "cherry"));
    }

    private void registerCloudVariants(DetailBrushPreset cloud) {
        registerVariant(cloud, "puffy-cloud", "Puffy Cloud", "Round white cumulus shape for skyboxes and float islands.",
                params("volume", "6", "puffiness", "0.55", "density", "0.78", "flatness", "0.3", "opacity", "0.78", "tint", "white"));
        registerVariant(cloud, "thunderhead", "Thunderhead", "Heavy storm cloud with grey gradient.",
                params("volume", "12", "puffiness", "0.7", "density", "0.85", "flatness", "0.25", "opacity", "0.82", "tint", "storm"));
        registerVariant(cloud, "wispy", "Wispy Cloud", "Thin streak of cloud, sparse and stretched flat.",
                params("volume", "10", "puffiness", "0.85", "density", "0.45", "flatness", "0.7", "opacity", "0.55", "tint", "white"));
        registerVariant(cloud, "sunset-cloud", "Sunset Cloud", "Pink and orange cloud for warm-light skies.",
                params("volume", "8", "puffiness", "0.6", "density", "0.7", "flatness", "0.45", "opacity", "0.72", "tint", "sunset"));
    }

    private void register(DetailBrushPreset preset) {
        presets.put(preset.id().toLowerCase(Locale.ROOT), preset);
    }

    public DetailBrushPreset get(String id) {
        if (id == null) {
            return null;
        }
        return presets.get(id.toLowerCase(Locale.ROOT));
    }

    public List<DetailBrushPreset> all() {
        return List.copyOf(presets.values());
    }

    public List<String> ids() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(presets.keySet()));
    }

    public DetailBrushVariant builtInVariant(String name) {
        if (name == null) {
            return null;
        }
        return builtInVariants.get(name.toLowerCase(Locale.ROOT));
    }

    public DetailBrushVariant builtInVariant(String presetId, String name) {
        DetailBrushVariant variant = builtInVariant(name);
        if (variant == null || presetId == null || !variant.presetId().equalsIgnoreCase(presetId)) {
            return null;
        }
        return variant;
    }

    public List<DetailBrushVariant> builtInVariants(String presetId) {
        List<DetailBrushVariant> matches = new java.util.ArrayList<>();
        for (DetailBrushVariant variant : builtInVariants.values()) {
            if (presetId == null || variant.presetId().equalsIgnoreCase(presetId)) {
                matches.add(variant);
            }
        }
        return List.copyOf(matches);
    }

    public List<String> builtInVariantNames(String presetId) {
        List<String> names = new java.util.ArrayList<>();
        for (DetailBrushVariant variant : builtInVariants(presetId)) {
            names.add(variant.name());
        }
        return Collections.unmodifiableList(names);
    }

    public List<String> builtInVariantNames() {
        return builtInVariantNames(null);
    }

    private void registerFlameVariants(DetailBrushPreset flame) {
        registerVariant(flame, "campfire", "Campfire Flame", "Small, dense, warm utility flame.",
                params("heat", "0.68", "height", "6", "width", "2", "flicker", "0.35", "lean_x", "0.0", "lean_z", "0.0", "density", "0.92"));
        registerVariant(flame, "bonfire", "Bonfire Flame", "Tall, readable fire column for build focal points.",
                params("heat", "0.74", "height", "15", "width", "4", "flicker", "0.5", "lean_x", "0.0", "lean_z", "0.0", "density", "0.9"));
        registerVariant(flame, "wildfire", "Wildfire Flame", "Wide, windy, uneven flame for burning trees and ruins.",
                params("heat", "0.55", "height", "12", "width", "6", "flicker", "0.88", "lean_x", "0.45", "lean_z", "-0.25", "density", "0.78"));
        registerVariant(flame, "torch-flame", "Torch Flame", "Tiny vertical flame for lamps, braziers, and detail work.",
                params("heat", "0.82", "height", "3", "width", "1", "flicker", "0.2", "lean_x", "0.0", "lean_z", "0.0", "density", "1.0"));
        registerVariant(flame, "ember-smoke", "Ember Smoke", "Sparse, low-heat ember/smoke shape using darker edge material.",
                params("heat", "0.12", "height", "10", "width", "4", "flicker", "0.65", "lean_x", "0.15", "lean_z", "0.0", "density", "0.42"));
        registerVariant(flame, "white-hot", "White-Hot Flame", "Bright core-heavy flame with a cleaner silhouette.",
                params("heat", "0.96", "height", "9", "width", "3", "flicker", "0.22", "lean_x", "0.0", "lean_z", "0.0", "density", "0.95"));
    }

    private void registerVariant(DetailBrushPreset preset, String name, String displayName, String description, DetailBrushParameters parameters) {
        String normalized = name.toLowerCase(Locale.ROOT);
        DetailBrushSettings settings = new DetailBrushSettings(preset.id(), parameters.mergeDefaults(preset.parameterSpecs()), DetailBrushMode.STAMP);
        builtInVariants.put(normalized, new DetailBrushVariant(normalized, preset.id(), displayName, description, settings));
    }

    private DetailBrushParameters params(String... values) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            params.put(values[i], values[i + 1]);
        }
        return new DetailBrushParameters(params);
    }
}
