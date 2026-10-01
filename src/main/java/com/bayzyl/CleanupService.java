package com.bayzyl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.bayzyl.safety.BrushSafety;

public final class CleanupService {
    private static final int DEFAULT_COMMAND_STRENGTH = 2;
    private static final List<BlockFace> CARDINAL_FACES = List.of(
            BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    );

    public List<BlockChange> cleanupSelection(Player player, Selection selection, TerrainBrushType type) {
        return cleanupSelection(player, selection, type, DEFAULT_COMMAND_STRENGTH);
    }

    public List<BlockChange> cleanupSelection(Player player, Selection selection, TerrainBrushType type, int strength) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, org.bukkit.ChatColor.RED + "Selection is incomplete.");
            return List.of();
        }
        if (type == null || !type.isCleanupMode()) {
            return List.of();
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return List.of();
        }
        return collectChanges(
                world,
                selection.getMinX(),
                selection.getMaxX(),
                selection.getMinY(),
                selection.getMaxY(),
                selection.getMinZ(),
                selection.getMaxZ(),
                type,
                Math.max(1, strength),
                null,
                null
        );
    }

    public List<BlockChange> cleanupBrush(Player player, Location center, TerrainBrushSettings settings) {
        if (settings == null || settings.type() == null || !settings.type().isCleanupMode()
                || !BrushSafety.isValidTerrain(settings)) {
            return List.of();
        }
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        World world = center.getWorld();
        int radius = settings.radius();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        return collectChanges(
                world,
                centerX - radius,
                centerX + radius,
                Math.max(world.getMinHeight(), centerY - radius),
                Math.min(world.getMaxHeight() - 1, centerY + radius),
                centerZ - radius,
                centerZ + radius,
                settings.type(),
                settings.power(),
                center,
                radius
        );
    }

    private List<BlockChange> collectChanges(World world,
                                             int minX,
                                             int maxX,
                                             int minY,
                                             int maxY,
                                             int minZ,
                                             int maxZ,
                                             TerrainBrushType type,
                                             int strength,
                                             Location sphereCenter,
                                             Integer sphereRadius) {
        List<TargetBlock> targets = new ArrayList<>();
        int radiusSquared = sphereRadius == null ? 0 : sphereRadius * sphereRadius;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (sphereCenter != null) {
                        int dx = x - sphereCenter.getBlockX();
                        int dy = y - sphereCenter.getBlockY();
                        int dz = z - sphereCenter.getBlockZ();
                        if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSquared) {
                            continue;
                        }
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (shouldRemove(block, type, strength)) {
                        targets.add(new TargetBlock(block.getLocation(), block.getBlockData().clone()));
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return List.of();
        }

        List<BlockChange> changes = new ArrayList<>(targets.size());
        for (TargetBlock target : targets) {
            Block block = world.getBlockAt(target.location().getBlockX(), target.location().getBlockY(), target.location().getBlockZ());
            BlockData before = target.before();
            block.setType(Material.AIR, false);
            BlockData after = block.getBlockData().clone();
            if (!before.matches(after)) {
                changes.add(new BlockChange(target.location(), before, after));
            }
        }
        return changes;
    }

    private boolean shouldRemove(Block block, TerrainBrushType type, int strength) {
        Material material = block.getType();
        if (material.isAir()) {
            return false;
        }
        return switch (type) {
            case CLEANUP_FLOATING -> shouldRemoveFloating(block, material, strength);
            case CLEANUP_FOLIAGE -> shouldRemoveFoliage(block, material, strength);
            case CLEANUP_LIQUIDS -> isLiquidCleanupTarget(material);
            case CLEANUP_SNOW -> isSnowCleanupTarget(material);
            case CLEANUP_LIGHTSPAM -> isLightCleanupTarget(material, strength);
            default -> false;
        };
    }

    private boolean shouldRemoveFloating(Block block, Material material, int strength) {
        if (!isFloatingCandidate(material)) {
            return false;
        }
        int supportCount = countNeighborSupport(block);
        boolean supportedBelow = isStableSupport(block.getRelative(BlockFace.DOWN).getType());
        if (isLeafOrWood(material)) {
            return supportCount <= Math.max(1, strength);
        }
        if (isLightCleanupTarget(material, strength)) {
            return supportCount <= Math.max(1, strength);
        }
        return !supportedBelow && supportCount <= Math.max(1, strength);
    }

    private boolean shouldRemoveFoliage(Block block, Material material, int strength) {
        if (isFoliageClutter(material)) {
            return true;
        }
        if (strength >= 3 && Tag.LEAVES.isTagged(material) && countNeighborSupport(block) <= 1) {
            return true;
        }
        return strength >= 4 && isLeafOrWood(material) && countNeighborSupport(block) <= 1;
    }

    private int countNeighborSupport(Block block) {
        int count = 0;
        for (BlockFace face : CARDINAL_FACES) {
            Material neighbor = block.getRelative(face).getType();
            if (isStableSupport(neighbor) || isLeafOrWood(neighbor)) {
                count++;
            }
        }
        return count;
    }

    private boolean isStableSupport(Material material) {
        return material != null && !material.isAir() && material != Material.WATER && material != Material.LAVA;
    }

    private boolean isFloatingCandidate(Material material) {
        return isFoliageClutter(material)
                || isLeafOrWood(material)
                || isSnowCleanupTarget(material)
                || isLightCleanupTarget(material, 1);
    }

    private boolean isFoliageClutter(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.FLOWERS.isTagged(material) || Tag.SAPLINGS.isTagged(material)) {
            return true;
        }
        String name = material.name();
        return name.equals("SHORT_GRASS")
                || name.equals("TALL_GRASS")
                || name.equals("FERN")
                || name.equals("LARGE_FERN")
                || name.equals("DEAD_BUSH")
                || name.equals("BROWN_MUSHROOM")
                || name.equals("RED_MUSHROOM")
                || name.equals("VINE")
                || name.equals("GLOW_LICHEN")
                || name.equals("HANGING_ROOTS")
                || name.equals("PINK_PETALS")
                || name.equals("WILDFLOWERS")
                || name.equals("LEAF_LITTER")
                || name.equals("WEEPING_VINES")
                || name.equals("WEEPING_VINES_PLANT")
                || name.equals("TWISTING_VINES")
                || name.equals("TWISTING_VINES_PLANT")
                || name.equals("CAVE_VINES")
                || name.equals("CAVE_VINES_PLANT");
    }

    private boolean isLeafOrWood(Material material) {
        if (material == null) {
            return false;
        }
        if (Tag.LEAVES.isTagged(material) || Tag.LOGS.isTagged(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_WOOD")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_STEM")
                || name.equals("MANGROVE_ROOTS")
                || name.equals("MUDDY_MANGROVE_ROOTS")
                || name.equals("BAMBOO_BLOCK")
                || name.equals("STRIPPED_BAMBOO_BLOCK");
    }

    private boolean isLiquidCleanupTarget(Material material) {
        if (material == null) {
            return false;
        }
        if (material == Material.WATER || material == Material.LAVA) {
            return true;
        }
        String name = material.name();
        return name.equals("BUBBLE_COLUMN")
                || name.equals("KELP")
                || name.equals("KELP_PLANT")
                || name.equals("SEAGRASS")
                || name.equals("TALL_SEAGRASS");
    }

    private boolean isSnowCleanupTarget(Material material) {
        return material == Material.SNOW || material == Material.POWDER_SNOW;
    }

    private boolean isLightCleanupTarget(Material material, int strength) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if (name.contains("TORCH") || name.contains("LANTERN") || name.equals("END_ROD")
                || name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE")) {
            return true;
        }
        return strength >= 3 && (name.equals("SEA_PICKLE") || name.contains("FROGLIGHT") || name.equals("SHROOMLIGHT"));
    }

    private record TargetBlock(Location location, BlockData before) {
    }
}
