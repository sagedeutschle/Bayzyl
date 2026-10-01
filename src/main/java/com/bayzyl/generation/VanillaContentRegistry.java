package com.bayzyl.generation;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.generator.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lookup layer for vanilla structures and features used by genstructure/genfeature.
 *
 * Structures are loaded from {@link Registry#STRUCTURE} when available, with a fallback
 * to a curated list. Features use a curated list because Bukkit does not expose a stable
 * registry of configured features across server platforms.
 *
 * For each entry we hold:
 *   - id (namespaced key, e.g. minecraft:village_plains)
 *   - description (short human-readable hint)
 *   - footprintRadius (horizontal radius in blocks used to size pre/post snapshots and confirm gating)
 *   - footprintHeight (vertical radius)
 */
public final class VanillaContentRegistry {

    public static final int DEFAULT_STRUCTURE_RADIUS = 64;
    public static final int DEFAULT_STRUCTURE_HEIGHT = 48;
    public static final int DEFAULT_FEATURE_RADIUS = 8;
    public static final int DEFAULT_FEATURE_HEIGHT = 8;

    private static final Map<String, Entry> STRUCTURE_META = buildStructureMeta();
    private static final Map<String, Entry> FEATURE_META = buildFeatureMeta();

    private static volatile List<String> cachedStructureIds;
    private static volatile List<String> cachedFeatureIds;

    private VanillaContentRegistry() {
    }

    public static List<String> structureIds() {
        List<String> cached = cachedStructureIds;
        if (cached != null) {
            return cached;
        }
        cached = loadStructureIds();
        cachedStructureIds = cached;
        return cached;
    }

    public static List<String> featureIds() {
        List<String> cached = cachedFeatureIds;
        if (cached != null) {
            return cached;
        }
        cached = new ArrayList<>(FEATURE_META.keySet());
        Collections.sort(cached);
        cached = Collections.unmodifiableList(cached);
        cachedFeatureIds = cached;
        return cached;
    }

    public static Entry structureMeta(String id) {
        Entry e = STRUCTURE_META.get(normalize(id));
        return e != null ? e : Entry.unknownStructure(id);
    }

    public static Entry featureMeta(String id) {
        Entry e = FEATURE_META.get(normalize(id));
        return e != null ? e : Entry.unknownFeature(id);
    }

    private static List<String> loadStructureIds() {
        List<String> ids = new ArrayList<>();
        try {
            Registry<Structure> registry = Bukkit.getRegistry(Structure.class);
            if (registry != null) {
                for (Structure structure : registry) {
                    NamespacedKey key = structure.getKey();
                    if (key != null) {
                        ids.add(key.toString());
                    }
                }
            }
        } catch (Throwable ignored) {
            // Registry not available; fall back to curated keys.
        }
        if (ids.isEmpty()) {
            ids.addAll(STRUCTURE_META.keySet());
        }
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    private static String normalize(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private static Map<String, Entry> buildStructureMeta() {
        Map<String, Entry> map = new LinkedHashMap<>();
        // radius/height tuned to typical bounding boxes; large outliers (mineshaft, stronghold) get bigger windows.
        put(map, "minecraft:village_plains",       "Plains village",                   80, 32);
        put(map, "minecraft:village_desert",       "Desert village",                   80, 32);
        put(map, "minecraft:village_savanna",      "Savanna village",                  80, 32);
        put(map, "minecraft:village_snowy",        "Snowy village",                    80, 32);
        put(map, "minecraft:village_taiga",        "Taiga village",                    80, 32);
        put(map, "minecraft:pillager_outpost",     "Pillager outpost tower",           32, 32);
        put(map, "minecraft:ruined_portal",        "Surface ruined portal",            16, 16);
        put(map, "minecraft:ruined_portal_desert", "Desert ruined portal",             16, 16);
        put(map, "minecraft:ruined_portal_jungle", "Jungle ruined portal",             16, 16);
        put(map, "minecraft:ruined_portal_swamp",  "Swamp ruined portal",              16, 16);
        put(map, "minecraft:ruined_portal_mountain","Mountain ruined portal",          16, 16);
        put(map, "minecraft:ruined_portal_ocean",  "Ocean ruined portal",              16, 16);
        put(map, "minecraft:ruined_portal_nether", "Nether ruined portal",             16, 16);
        put(map, "minecraft:shipwreck",            "Surface shipwreck",                24, 16);
        put(map, "minecraft:shipwreck_beached",    "Beached shipwreck",                24, 16);
        put(map, "minecraft:ocean_ruin_cold",      "Cold ocean ruin",                  24, 12);
        put(map, "minecraft:ocean_ruin_warm",      "Warm ocean ruin",                  24, 12);
        put(map, "minecraft:mineshaft",            "Abandoned mineshaft (large area)", 96, 48);
        put(map, "minecraft:mineshaft_mesa",       "Badlands mineshaft (large area)",  96, 48);
        put(map, "minecraft:stronghold",           "Stronghold (large area)",         128, 32);
        put(map, "minecraft:monument",             "Ocean monument",                   58, 24);
        put(map, "minecraft:mansion",              "Woodland mansion",                 80, 32);
        put(map, "minecraft:igloo",                "Igloo",                            12, 12);
        put(map, "minecraft:swamp_hut",            "Swamp witch hut",                  10, 12);
        put(map, "minecraft:desert_pyramid",       "Desert pyramid",                   24, 16);
        put(map, "minecraft:jungle_pyramid",       "Jungle temple",                    16, 16);
        put(map, "minecraft:igloo",                "Igloo",                            12, 12);
        put(map, "minecraft:buried_treasure",      "Buried treasure chest",             4,  4);
        put(map, "minecraft:nether_fossil",        "Nether fossil",                    16, 12);
        put(map, "minecraft:fortress",             "Nether fortress (large area)",     96, 48);
        put(map, "minecraft:bastion_remnant",      "Bastion remnant",                  64, 32);
        put(map, "minecraft:end_city",             "End city",                         64, 64);
        put(map, "minecraft:ancient_city",         "Deep dark ancient city",          112, 32);
        put(map, "minecraft:trail_ruins",          "Trail ruins",                      32, 12);
        put(map, "minecraft:trial_chambers",       "Trial chambers",                   80, 48);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Entry> buildFeatureMeta() {
        Map<String, Entry> map = new LinkedHashMap<>();
        put(map, "minecraft:tree",                 "Generic tree",                      6, 16);
        put(map, "minecraft:flower",               "Single flower patch",               2,  2);
        put(map, "minecraft:ore",                  "Ore vein",                          4,  4);
        put(map, "minecraft:fossil_skull",         "Fossil skull",                      8,  6);
        put(map, "minecraft:desert_well",          "Desert well",                       4,  6);
        put(map, "minecraft:geode",                "Amethyst geode",                   12, 12);
        put(map, "minecraft:monster_room",         "Dungeon room",                      8,  8);
        put(map, "minecraft:iceberg",              "Iceberg",                          24, 32);
        put(map, "minecraft:lake",                 "Surface water lake",               12,  6);
        put(map, "minecraft:end_island",           "End void island",                  16, 12);
        return Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, Entry> map, String id, String desc, int radius, int height) {
        map.putIfAbsent(id, new Entry(id, desc, radius, height));
    }

    public static final class Entry {
        private final String id;
        private final String description;
        private final int footprintRadius;
        private final int footprintHeight;

        public Entry(String id, String description, int footprintRadius, int footprintHeight) {
            this.id = id;
            this.description = description;
            this.footprintRadius = footprintRadius;
            this.footprintHeight = footprintHeight;
        }

        public String id() { return id; }
        public String description() { return description; }
        public int footprintRadius() { return footprintRadius; }
        public int footprintHeight() { return footprintHeight; }

        public long estimatedVolume() {
            long span = (footprintRadius * 2L) + 1L;
            long height = (footprintHeight * 2L) + 1L;
            return span * span * height;
        }

        static Entry unknownStructure(String id) {
            return new Entry(id, "vanilla structure", DEFAULT_STRUCTURE_RADIUS, DEFAULT_STRUCTURE_HEIGHT);
        }

        static Entry unknownFeature(String id) {
            return new Entry(id, "vanilla feature", DEFAULT_FEATURE_RADIUS, DEFAULT_FEATURE_HEIGHT);
        }
    }
}
