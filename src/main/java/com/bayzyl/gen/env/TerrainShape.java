package com.bayzyl.gen.env;

/**
 * Categorical "what shape is this terrain in" hint. Drives whether a
 * brush should erode aggressively (CLEAN flat slab needs none) or
 * lightly (random vanilla already has noise).
 */
public enum TerrainShape {
    SUPERFLAT,   // Single-level slab. No vertical variation.
    FLAT,        // <2 block delta across probe radius. Plains-ish.
    ROLLING,     // 2-6 block delta. Hilly.
    HILLY,       // 6-12 block delta.
    MOUNTAIN,    // 12-30 block delta.
    PEAK,        // >30 block delta. Already a peak.
    UNDERWATER,  // Probe surface is below sea level.
    VOID         // No surface found (the End void, custom void worlds).
}
