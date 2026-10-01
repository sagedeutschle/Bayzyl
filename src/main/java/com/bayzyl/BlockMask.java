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
    private final List<Tag<Material>> tags;
    private final List<Material> materials;
    private final BlockMask andMask;

    public BlockMask(String raw, List<Tag<Material>> tags, List<Material> materials) {
        this(raw, tags, materials, null);
    }

    private BlockMask(String raw, List<Tag<Material>> tags, List<Material> materials, BlockMask andMask) {
        this.raw = raw;
        this.tags = tags;
        this.materials = materials;
        this.andMask = andMask;
    }

    public boolean matches(Material material) {
        if (material == null) {
            return false;
        }
        if (andMask != null && !andMask.matches(material)) {
            return false;
        }
        for (Material value : materials) {
            if (value == material) {
                return true;
            }
        }
        for (Tag<Material> tag : tags) {
            if (tag.isTagged(material)) {
                return true;
            }
        }
        return tags.isEmpty() && materials.isEmpty();
    }

    public boolean isAny() {
        return (raw == null || raw.isBlank())
                && tags.isEmpty()
                && materials.isEmpty()
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
                base.tags,
                base.materials,
                nestedAnd
        );
    }

    public String getRaw() {
        return raw;
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
