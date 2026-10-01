package com.bayzyl;

import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlockMask {
    private final String raw;
    private volatile Resolved resolved;
    private final BlockMask andMask;

    private record Resolved(List<Tag<Material>> tags, List<Material> materials) {
    }

    public BlockMask(String raw, List<Tag<Material>> tags, List<Material> materials) {
        this(raw, tags, materials, null);
    }

    private BlockMask(String raw, List<Tag<Material>> tags, List<Material> materials, BlockMask andMask) {
        this.raw = raw;
        this.resolved = tags == null ? null : new Resolved(tags, materials);
        this.andMask = andMask;
    }

    /** A mask restored from its text whose tags and materials resolve on first use, not at restore time. */
    static BlockMask deferred(String raw) {
        return new BlockMask(raw == null ? "" : raw.trim(), null, null, null);
    }

    private Resolved resolved() {
        Resolved current = resolved;
        if (current == null) {
            BlockMask parsed = parse(raw);
            current = new Resolved(parsed.tags(), parsed.materials());
            resolved = current;
        }
        return current;
    }

    private List<Tag<Material>> tags() {
        return resolved().tags();
    }

    private List<Material> materials() {
        return resolved().materials();
    }

    public boolean matches(Material material) {
        if (material == null) {
            return false;
        }
        if (andMask != null && !andMask.matches(material)) {
            return false;
        }
        Resolved current = resolved();
        for (Material value : current.materials()) {
            if (value == material) {
                return true;
            }
        }
        for (Tag<Material> tag : current.tags()) {
            if (tag.isTagged(material)) {
                return true;
            }
        }
        return current.tags().isEmpty() && current.materials().isEmpty();
    }

    public boolean isAny() {
        return (raw == null || raw.isBlank())
                && tags().isEmpty()
                && materials().isEmpty()
                && andMask == null;
    }

    public static BlockMask and(BlockMask base, BlockMask additional) {
        if (additional == null || additional.isAny()) {
            return base;
        }
        if (base == null || base.isAny()) {
            return additional;
        }
        BlockMask nestedAnd = base.andMask == null ? additional : BlockMask.and(base.andMask, additional);
        return new BlockMask(
                base.raw + " & " + additional.summary(),
                base.tags(),
                base.materials(),
                nestedAnd
        );
    }

    public String getRaw() {
        return raw;
    }

    /** True for a mask built by {@link #and}, whose raw text no longer re-parses to the same mask. */
    boolean isCombined() {
        return andMask != null;
    }

    public String summary() {
        if (raw == null || raw.isBlank()) {
            return "any";
        }
        return raw;
    }

    public static BlockMask parse(String input) {
        if (input == null || input.isBlank()) {
            return new BlockMask("", List.of(), List.of());
        }

        String[] parts = input.split(",");
        List<Tag<Material>> tags = new ArrayList<>();
        List<Material> materials = new ArrayList<>();

        for (String raw : parts) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }

            if (token.startsWith("##")) {
                String tagName = token.substring(2);
                if (tagName.startsWith("*")) {
                    tagName = tagName.substring(1);
                }
                Tag<Material> tag = resolveTag(tagName);
                if (tag != null) {
                    tags.add(tag);
                }
                continue;
            }

            Material material = EditUtil.parseBlock(token);
            if (material != null) {
                materials.add(material);
            }
        }

        return new BlockMask(input.trim(), tags, materials);
    }

    public static boolean isResolvable(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String[] parts = input.split(",");
        for (String raw : parts) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("##")) {
                String tagName = token.substring(2);
                if (tagName.startsWith("*")) {
                    tagName = tagName.substring(1);
                }
                if (resolveTag(tagName) != null) {
                    return true;
                }
                continue;
            }
            Material material = EditUtil.parseBlock(token);
            if (material != null) {
                return true;
            }
        }
        return false;
    }

    private static Tag<Material> resolveTag(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name);
        if (key == null) {
            return null;
        }
        return Bukkit.getTag("blocks", key, Material.class);
    }
}
