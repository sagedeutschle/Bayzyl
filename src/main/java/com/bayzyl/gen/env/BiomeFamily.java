package com.bayzyl.gen.env;

/**
 * Coarse buckets the gen brushes care about. We don't want every
 * generator to maintain a switch over ~70 vanilla biomes; this enum lets
 * a generator say "is this a desert-ish thing" without trying to
 * enumerate the seven actual desert/badlands biomes.
 */
public enum BiomeFamily {
    GRASSLAND,
    FOREST,
    JUNGLE,
    TAIGA,
    SAVANNA,
    DESERT,
    BADLANDS,
    MESA,
    SWAMP,
    MUSHROOM,
    SNOWY,
    OCEAN,
    BEACH,
    RIVER,
    MOUNTAIN,
    CHERRY,
    LUSH,
    DRIPSTONE,
    DEEP_DARK,
    NETHER,
    END,
    VOID,
    UNKNOWN
}
