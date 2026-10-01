package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PaletteService {
    public AnalysisResult analyzeSelection(Player player, Selection selection) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return null;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return null;
        }

        Map<Material, Integer> materialCounts = new HashMap<>();
        Map<String, Integer> familyCounts = new HashMap<>();
        long totalBlocks = selection.getVolume();
        long filledBlocks = 0L;

        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (material.isAir()) {
                        continue;
                    }
                    filledBlocks++;
                    materialCounts.merge(material, 1, Integer::sum);
                    familyCounts.merge(resolveFamily(material), 1, Integer::sum);
                }
            }
        }

        List<MaterialCount> materials = materialCounts.entrySet().stream()
                .sorted(Map.Entry.<Material, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().name()))
                .map(entry -> new MaterialCount(entry.getKey(), entry.getValue()))
                .toList();
        List<FamilyCount> families = familyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new FamilyCount(entry.getKey(), entry.getValue()))
                .toList();

        return new AnalysisResult(totalBlocks, filledBlocks, materialCounts.size(), materials, families);
    }

    public List<BlockChange> swapSelection(Player player, Selection selection, Material from, Material to) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return List.of();
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return List.of();
        }
        if (from == null || to == null || !from.isBlock() || !to.isBlock()) {
            ChatOutput.send(player, ChatColor.RED + "Palette swap needs valid block materials.");
            return List.of();
        }
        if (from == to) {
            ChatOutput.send(player, ChatColor.RED + "Palette swap source and target must be different.");
            return List.of();
        }

        List<BlockChange> changes = new ArrayList<>();
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != from) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    block.setType(to, false);
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }
        return changes;
    }

    private String resolveFamily(Material material) {
        String name = material.name();
        if (Tag.LOGS.isTagged(material) || Tag.LEAVES.isTagged(material)
                || name.endsWith("_PLANKS") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) {
            return "wood";
        }
        if (name.contains("STONE") || name.contains("DEEPSLATE") || name.contains("BLACKSTONE")
                || name.contains("TUFF") || name.contains("BASALT") || name.contains("DIORITE")
                || name.contains("ANDESITE") || name.contains("GRANITE") || name.contains("CALCITE")
                || name.contains("DRIPSTONE") || name.contains("PRISMARINE") || name.contains("SANDSTONE")
                || name.contains("BRICK")) {
            return "masonry";
        }
        if (name.contains("GLASS")) {
            return "glass";
        }
        if (name.contains("COPPER") || name.contains("IRON") || name.contains("GOLD") || name.contains("CHAIN")) {
            return "metal";
        }
        if (name.contains("DIRT") || name.contains("GRASS") || name.contains("MUD")
                || name.contains("PODZOL") || name.contains("MYCELIUM") || name.contains("SAND")
                || name.contains("GRAVEL") || name.contains("CLAY")) {
            return "earth";
        }
        if (Tag.FLOWERS.isTagged(material) || name.contains("MUSHROOM") || name.contains("VINE")
                || name.contains("LICHEN") || name.contains("LEAF")) {
            return "foliage";
        }
        if (name.contains("TORCH") || name.contains("LANTERN") || name.contains("LIGHT")
                || name.equals("END_ROD") || name.equals("GLOWSTONE")) {
            return "lighting";
        }
        if (name.contains("NETHER") || name.contains("CRIMSON") || name.contains("WARPED") || name.contains("SOUL")) {
            return "nether";
        }
        return "other";
    }

    public record AnalysisResult(long totalBlocks,
                                 long filledBlocks,
                                 int uniqueMaterialCount,
                                 List<MaterialCount> materials,
                                 List<FamilyCount> families) {
    }

    public record MaterialCount(Material material, int count) {
        public String displayName() {
            return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    public record FamilyCount(String family, int count) {
    }
}
