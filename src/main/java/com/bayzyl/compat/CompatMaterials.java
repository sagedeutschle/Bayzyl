package com.bayzyl.compat;

import org.bukkit.Material;

import java.util.function.Function;

/**
 * Resolves materials Mojang renamed between Minecraft versions by name at runtime, so no compile-time reference to a
 * constant that is missing on some supported server (a missing enum constant throws NoSuchFieldError on first use).
 * Paper 1.21 has {@code CHAIN} only; Paper 1.21.11 and 26.x have {@code IRON_CHAIN} only.
 */
public final class CompatMaterials {
    private static final String MODERN_CHAIN = "IRON_CHAIN";
    private static final String LEGACY_CHAIN = "CHAIN";
    // IRON_BARS exists on every supported version and is the closest always-present iron lattice block.
    private static final Material CHAIN_FALLBACK = Material.IRON_BARS;

    private static final class ChainHolder {
        private static final Material CHAIN = resolveChain(Material::getMaterial);
    }

    private CompatMaterials() {
    }

    /** The chain block on the running server; never null (falls back to iron bars if neither name exists). */
    public static Material chain() {
        return ChainHolder.CHAIN;
    }

    /** Looks up the modern name first, then the legacy name, then the fallback. {@code lookup} may return null. */
    static Material resolveChain(Function<String, Material> lookup) {
        Material modern = lookup.apply(MODERN_CHAIN);
        if (modern != null) {
            return modern;
        }
        Material legacy = lookup.apply(LEGACY_CHAIN);
        return legacy != null ? legacy : CHAIN_FALLBACK;
    }
}
