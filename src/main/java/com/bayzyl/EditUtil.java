package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class EditUtil {
    public static final long CONFIRM_VOLUME = 200_000L;

    private static final Set<UUID> ENTITY_DEBUG_PLAYERS = ConcurrentHashMap.newKeySet();

    private EditUtil() {
    }

    public static void setEntityDebug(UUID playerId, boolean enabled) {
        if (enabled) {
            ENTITY_DEBUG_PLAYERS.add(playerId);
        } else {
            ENTITY_DEBUG_PLAYERS.remove(playerId);
        }
    }

    public static boolean isEntityDebug(UUID playerId) {
        return ENTITY_DEBUG_PLAYERS.contains(playerId);
    }

    private static void entityDebug(Player player, String message) {
        if (player == null) {
            return;
        }
        if (ENTITY_DEBUG_PLAYERS.contains(player.getUniqueId())) {
            ChatOutput.send(player, ChatColor.YELLOW + "[entity] " + ChatColor.WHITE + message);
        }
    }

    private static String entitySummary(Entity entity) {
        Location l = entity.getLocation();
        String detail = "";
        if (entity instanceof Painting painting) {
            detail = " " + painting.getArt().name();
        }
        return entity.getType().name() + detail + " @ " + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", l.getX(), l.getY(), l.getZ());
    }

    public static Material parseBlock(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String token = input.toLowerCase(Locale.ROOT).trim();
        if (token.equals("0")) {
            return Material.AIR;
        }
        if (token.startsWith("minecraft:")) {
            token = token.substring("minecraft:".length());
            if (token.equals("0")) {
                return Material.AIR;
            }
        }
        Material material = Material.matchMaterial(token);
        if (material == null) {
            return null;
        }
        if (material == Material.AIR) {
            return material;
        }
        if (!material.isBlock()) {
            return null;
        }
        return material;
    }

    public static boolean requiresConfirm(Selection selection, boolean confirm) {
        return selection != null && selection.isComplete() && selection.getVolume() > CONFIRM_VOLUME && !confirm;
    }

    public static List<BlockChange> setBlocks(Player player,
                                              Selection selection,
                                              BlockDistribution distribution,
                                              BlockMask mask,
                                              String ifMode) {
        List<BlockChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return changes;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return changes;
        }
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!mask.matches(block.getType())) {
                        continue;
                    }
                    if (!passesIf(block.getType(), ifMode)) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    Material toMaterial = distribution.isSingleMaterial() ? distribution.getSoleMaterial() : distribution.pickRandom();
                    block.setType(toMaterial, false);
                    BlockData after = block.getBlockData().clone();
                    changes.add(new BlockChange(block.getLocation(), before, after));
                }
            }
        }
        return changes;
    }

    public static List<BlockChange> replaceBlocks(Player player,
                                                  Selection selection,
                                                  BlockMask from,
                                                  BlockDistribution toDistribution,
                                                  BlockMask mask) {
        List<BlockChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return changes;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return changes;
        }
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!from.matches(block.getType())) {
                        continue;
                    }
                    if (!mask.matches(block.getType())) {
                        continue;
                    }
                    Material pickedMaterial = toDistribution.pickRandom();
                    BlockData before = block.getBlockData().clone();
                    block.setType(pickedMaterial, false);
                    BlockData after = block.getBlockData().clone();
                    changes.add(new BlockChange(block.getLocation(), before, after));
                }
            }
        }
        return changes;
    }

    public static Clipboard copySelection(Player player, Selection selection, BlockMask mask) {
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return null;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return null;
        }
        int sizeX = selection.getMaxX() - selection.getMinX() + 1;
        int sizeY = selection.getMaxY() - selection.getMinY() + 1;
        int sizeZ = selection.getMaxZ() - selection.getMinZ() + 1;
        BlockData[] data = new BlockData[sizeX * sizeY * sizeZ];
        BlockState[] states = new BlockState[sizeX * sizeY * sizeZ];

        int idx = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    Block block = world.getBlockAt(selection.getMinX() + x, selection.getMinY() + y, selection.getMinZ() + z);
                    if (mask.matches(block.getType())) {
                        data[idx] = block.getBlockData().clone();
                        BlockState state = block.getState();
                        states[idx] = state instanceof TileState ? state.copy() : null;
                    } else {
                        data[idx] = Material.AIR.createBlockData();
                        states[idx] = null;
                    }
                    idx++;
                }
            }
        }

        Location origin = player.getLocation().getBlock().getLocation();
        int minOffsetX = selection.getMinX() - origin.getBlockX();
        int minOffsetY = selection.getMinY() - origin.getBlockY();
        int minOffsetZ = selection.getMinZ() - origin.getBlockZ();
        List<ClipboardEntity> entities = copyEntities(world, selection, origin);
        return new Clipboard(sizeX, sizeY, sizeZ, data, states, entities, origin, minOffsetX, minOffsetY, minOffsetZ);
    }

    public static List<BlockChange> cutSelection(Player player, Selection selection, BlockMask mask) {
        List<BlockChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return changes;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return changes;
        }
        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!mask.matches(block.getType())) {
                        continue;
                    }
                    BlockData before = block.getBlockData().clone();
                    block.setType(Material.AIR, false);
                    BlockData after = block.getBlockData().clone();
                    changes.add(new BlockChange(block.getLocation(), before, after));
                }
            }
        }
        return changes;
    }

    public static List<BlockChange> makeWalls(Player player, Selection selection, BlockDistribution distribution, BlockMask mask) {
        List<BlockChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return changes;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return changes;
        }

        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                    boolean wall = x == selection.getMinX() || x == selection.getMaxX()
                            || z == selection.getMinZ() || z == selection.getMaxZ();
                    if (!wall) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (!mask.matches(block.getType())) {
                        continue;
                    }
                    Material pickedMaterial = distribution.pickRandom();
                    BlockData placedData = pickedMaterial.createBlockData();
                    BlockData before = block.getBlockData().clone();
                    if (before.matches(placedData)) {
                        continue;
                    }
                    block.setBlockData(placedData, false);
                    changes.add(new BlockChange(block.getLocation(), before, block.getBlockData().clone()));
                }
            }
        }
        return changes;
    }

    public static List<BlockChange> overlaySelection(Player player, Selection selection, Material material, BlockMask mask) {
        List<BlockChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return changes;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return changes;
        }


        for (int x = selection.getMinX(); x <= selection.getMaxX(); x++) {
            for (int z = selection.getMinZ(); z <= selection.getMaxZ(); z++) {
                for (int y = selection.getMaxY() - 1; y >= selection.getMinY(); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    if (!mask.matches(block.getType())) {
                        break;
                    }
                    Block above = world.getBlockAt(x, y + 1, z);
                    if (!above.getType().isAir()) {
                        break;
                    }
                    BlockData placedData = material.createBlockData();
                    BlockData before = above.getBlockData().clone();
                    if (before.matches(placedData)) {
                        break;
                    }
                    above.setBlockData(placedData, false);
                    changes.add(new BlockChange(above.getLocation(), before, above.getBlockData().clone()));
                    break;
                }
            }
        }
        return changes;
    }

    public static List<BlockChange> smoothSelection(Player player, Selection selection, int iterations) {
        List<BlockChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete()) {
            ChatOutput.send(player, ChatColor.RED + "Selection is incomplete.");
            return changes;
        }
        if (iterations <= 0) {
            return changes;
        }
        World world = selection.getPos1().getWorld();
        if (world == null) {
            return changes;
        }
        if (isSelectionUnderwater(world, selection)) {
            ChatOutput.send(player, ChatColor.RED + "Smooth is not supported in underwater selections.");
            return changes;
        }

        int width = selection.getMaxX() - selection.getMinX() + 1;
        int depth = selection.getMaxZ() - selection.getMinZ() + 1;
        int[][] heights = new int[width][depth];
        Material[][] topMaterials = new Material[width][depth];
        captureHeightMap(world, selection, heights, topMaterials);
        for (int i = 0; i < iterations; i++) {
            heights = smoothHeights(heights);
        }

        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depth; localZ++) {
                int x = selection.getMinX() + localX;
                int z = selection.getMinZ() + localZ;
                int targetY = clamp(heights[localX][localZ], selection.getMinY(), selection.getMaxY());
                Material topMaterial = topMaterials[localX][localZ];
                if (topMaterial == null) {
                    Block blockAtTarget = world.getBlockAt(x, targetY, z);
                    topMaterial = blockAtTarget.getType().isAir() ? Material.DIRT : blockAtTarget.getType();
                }

                for (int y = selection.getMinY(); y <= selection.getMaxY(); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockData before = block.getBlockData().clone();
                    if (y < targetY) {
                        if (block.getType().isAir()) {
                            block.setType(Material.DIRT, false);
                        }
                    } else if (y == targetY) {
                        if (block.getType() != topMaterial) {
                            block.setType(topMaterial, false);
                        }
                    } else {
                        if (!block.getType().isAir()) {
                            block.setType(Material.AIR, false);
                        }
                    }
                    BlockData after = block.getBlockData().clone();
                    if (!before.matches(after)) {
                        changes.add(new BlockChange(block.getLocation(), before, after));
                    }
                }
            }
        }

        return changes;
    }

    public static List<EntityChange> cutEntities(Player player, Selection selection) {
        List<EntityChange> changes = new ArrayList<>();
        if (selection == null || !selection.isComplete() || selection.getPos1().getWorld() == null) {
            return changes;
        }
        Location origin = player.getLocation().getBlock().getLocation();
        List<Entity> collected = collectSelectionEntities(selection);
        entityDebug(player, "cut: collected " + collected.size() + " entities from selection");
        for (Entity entity : collected) {
            entityDebug(player, "cut: " + entitySummary(entity));
            ClipboardEntity clipboardEntity = ClipboardEntity.from(entity, origin);
            changes.add(EntityChange.deleted(clipboardEntity, origin));
            if (entity instanceof org.bukkit.entity.ItemFrame frame) {
                frame.setItemDropChance(0.0f);
            }
            entity.remove();
        }
        return changes;
    }

    public static List<Entity> collectSelectionEntities(Selection selection) {
        if (selection == null || !selection.isComplete() || selection.getPos1() == null || selection.getPos1().getWorld() == null) {
            return List.of();
        }
        return collectEntities(selection.getPos1().getWorld(), null, 0, selection);
    }

    public static List<Entity> collectNearbyEntities(Location center, int radius, Selection selection) {
        if (center == null || center.getWorld() == null || radius <= 0) {
            return List.of();
        }
        return collectEntities(center.getWorld(), center, radius, selection);
    }

public static List<BlockChange> pasteClipboard(Player player,
                                                    Clipboard clipboard,
                                                    Location target,
                                                    int rotation,
                                                    boolean ignoreAir) {
        List<BlockChange> changes = new ArrayList<>();
        if (clipboard == null) {
            ChatOutput.send(player, ChatColor.RED + "Clipboard is empty.");
            return changes;
        }
        if (target == null || target.getWorld() == null) {
            return changes;
        }
        
        World world = target.getWorld();
        
        int sizeX = clipboard.getSizeX();
        int sizeY = clipboard.getSizeY();
        int sizeZ = clipboard.getSizeZ();
        
        // CRITICAL: Validate clipboard size to prevent memory explosion
        long totalBlocks = (long) sizeX * (long) sizeY * (long) sizeZ;
        if (totalBlocks > 10_000_000L) { // 10 million block limit
            ChatOutput.send(player, ChatColor.RED + "Clipboard too large (" + totalBlocks + 
                           " blocks). Maximum is 10,000,000 blocks.");
            return changes;
        }
        
        int rot = normalizeRotation(rotation);
        
        // Pre-calculate bounds and validate
        int minWorldY = world.getMinHeight();
        int maxWorldY = world.getMaxHeight();
        
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockData originalData = clipboard.get(x, y, z);
                    if (originalData == null) {
                        continue;
                    }
                    if (ignoreAir && originalData.getMaterial().isAir()) {
                        continue;
                    }
                    
                    int vectorX = clipboard.getMinOffsetX() + x;
                    int vectorZ = clipboard.getMinOffsetZ() + z;
                    int[] rotated = rotateY(vectorX, vectorZ, rot);
                    
                    int worldX = target.getBlockX() + rotated[0];
                    int worldYCoord = target.getBlockY() + clipboard.getMinOffsetY() + y;
                    int worldZ = target.getBlockZ() + rotated[1];
                    
                    // CRITICAL: Check world bounds
                    if (worldYCoord < minWorldY || worldYCoord >= maxWorldY) {
                        // Skip blocks outside world vertical bounds
                        continue;
                    }
                    
                    // CRITICAL: Check if chunk is loaded
                    if (!world.isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                        // Either load chunk or skip - loading is safer but slower
                        // For now, skip unloaded chunks
                        continue;
                    }
                    
                    try {
                        Block block = world.getBlockAt(worldX, worldYCoord, worldZ);
                        if (block == null) {
                            continue; // Should not happen with loaded chunk, but safety first
                        }
                        
                        BlockData before = block.getBlockData().clone();
                        BlockData placedData = ClipboardTransforms.applyRotation(originalData.clone(), rot);
                        
                        // CRITICAL: Validate block data before setting
                        if (placedData == null || placedData.getMaterial() == null) {
                            continue;
                        }
                        
                        block.setBlockData(placedData, false);
                        
                        BlockState state = clipboard.getState(x, y, z);
                        if (state != null) {
                            try {
                                BlockState placed = state.copy(block.getLocation());
                                placed.setBlockData(placedData.clone());
                                placed.update(true, false);
                            } catch (Exception e) {
                                // Log but continue - tile entity failure shouldn't crash entire paste
                                if (player != null) {
                                    player.sendMessage(ChatColor.YELLOW + 
                                        "Note: Failed to restore tile entity at " + 
                                        worldX + "," + worldYCoord + "," + worldZ);
                                }
                            }
                        }
                        
                        BlockData after = block.getBlockData().clone();
                        changes.add(new BlockChange(block.getLocation(), before, after));
                        
                    } catch (Exception e) {
                        // Catch any per-block exceptions and continue
                        if (player != null) {
                            player.sendMessage(ChatColor.RED + 
                                "Failed to paste block at " + worldX + "," + worldYCoord + "," + worldZ + 
                                ": " + e.getClass().getSimpleName());
                        }
                        // Continue with next block instead of crashing
                    }
                }
            }
        }
        
        return changes;
    }

    public static List<EntityChange> pasteEntities(Clipboard clipboard, Location target, int rotation) {
        return pasteEntities(null, clipboard, target, rotation);
    }

    public static List<EntityChange> pasteEntities(Player player, Clipboard clipboard, Location target, int rotation) {
        List<EntityChange> changes = new ArrayList<>();
        if (clipboard == null || target == null || target.getWorld() == null) {
            return changes;
        }

        entityDebug(player, "paste: clipboard has " + clipboard.getEntities().size() + " entities");
        for (ClipboardEntity entity : clipboard.getEntities()) {
            ClipboardEntity transformed = entity.rotateY(rotation);
            try {
                Entity placed = transformed.spawn(target.getWorld(), target);
                if (placed != null) {
                    entityDebug(player, "paste: spawned " + entitySummary(placed));
                } else {
                    entityDebug(player, ChatColor.RED + "paste: spawn returned null");
                }
                changes.add(EntityChange.created(transformed, target, placed));
            } catch (Exception ex) {
                entityDebug(player, ChatColor.RED + "paste: spawn FAILED - " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        return changes;
    }

    public static Clipboard reanchorClipboard(Clipboard clipboard, Location newOrigin) {
        if (clipboard == null || clipboard.getOrigin() == null || newOrigin == null) {
            return clipboard;
        }
        int dx = clipboard.getOrigin().getBlockX() - newOrigin.getBlockX();
        int dy = clipboard.getOrigin().getBlockY() - newOrigin.getBlockY();
        int dz = clipboard.getOrigin().getBlockZ() - newOrigin.getBlockZ();
        return new Clipboard(
                clipboard.getSizeX(),
                clipboard.getSizeY(),
                clipboard.getSizeZ(),
                copyDataArray(clipboard),
                copyStateArray(clipboard),
                clipboard.getEntities().stream().map(entity -> entity.translate(dx, dy, dz)).collect(Collectors.toList()),
                newOrigin.clone(),
                clipboard.getMinOffsetX() + dx,
                clipboard.getMinOffsetY() + dy,
                clipboard.getMinOffsetZ() + dz
        );
    }

    public static Selection getPasteSelection(Clipboard clipboard, Location target, int rotation) {
        if (clipboard == null || target == null || target.getWorld() == null) {
            return null;
        }

        int rot = normalizeRotation(rotation);
        int minX = Integer.MAX_VALUE;
        int minY = target.getBlockY() + clipboard.getMinOffsetY();
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = minY + clipboard.getSizeY() - 1;
        int maxZ = Integer.MIN_VALUE;

        int startX = clipboard.getMinOffsetX();
        int endX = clipboard.getMinOffsetX() + clipboard.getSizeX() - 1;
        int startZ = clipboard.getMinOffsetZ();
        int endZ = clipboard.getMinOffsetZ() + clipboard.getSizeZ() - 1;

        int[][] corners = new int[][]{
                {startX, startZ},
                {startX, endZ},
                {endX, startZ},
                {endX, endZ}
        };

        for (int[] corner : corners) {
            int[] rotated = rotateY(corner[0], corner[1], rot);
            int worldX = target.getBlockX() + rotated[0];
            int worldZ = target.getBlockZ() + rotated[1];
            minX = Math.min(minX, worldX);
            maxX = Math.max(maxX, worldX);
            minZ = Math.min(minZ, worldZ);
            maxZ = Math.max(maxZ, worldZ);
        }

        Location pos1 = new Location(target.getWorld(), minX, minY, minZ);
        Location pos2 = new Location(target.getWorld(), maxX, maxY, maxZ);
        return new Selection(pos1, pos2, SelectionType.CUBOID);
    }

    private static boolean passesIf(Material material, String ifMode) {
        if (ifMode == null || ifMode.isBlank() || ifMode.equals("any")) {
            return true;
        }
        if (ifMode.equals("air")) {
            return material.isAir();
        }
        if (ifMode.equals("solid")) {
            return material.isSolid();
        }
        return true;
    }

    private static int normalizeRotation(int rotation) {
        return ((rotation % 360) + 360) % 360;
    }

    public static int[] rotateY(int x, int z, int rotation) {
        if (rotation == 90) {
            return new int[]{-z, x};
        }
        if (rotation == 180) {
            return new int[]{-x, -z};
        }
        if (rotation == 270) {
            return new int[]{z, -x};
        }
        return new int[]{x, z};
    }

    private static List<ClipboardEntity> copyEntities(World world, Selection selection, Location origin) {
        List<ClipboardEntity> entities = new ArrayList<>();
        for (Entity entity : collectSelectionEntities(selection)) {
            entities.add(ClipboardEntity.from(entity, origin));
        }
        return entities;
    }

    private static List<Entity> collectEntities(World world, Location center, int radius, Selection selection) {
        List<Entity> entities = new ArrayList<>();
        if (world == null) {
            return entities;
        }

        double radiusSq = radius <= 0 ? Double.MAX_VALUE : (double) radius * radius;
        Iterable<Entity> candidates = center == null
                ? world.getEntities()
                : world.getNearbyEntities(center, radius, radius, radius);
        for (Entity entity : candidates) {
            if (entity instanceof LivingEntity && !(entity instanceof org.bukkit.entity.ArmorStand)) {
                continue;
            }
            if (entity instanceof Player) {
                continue;
            }
            if (center != null && entity.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            if (selection != null && selection.isComplete()) {
                if (entity instanceof Hanging) {
                    if (!supportInsideSelection(entity, selection)) {
                        continue;
                    }
                } else {
                    if (!intersectsSelection(entity.getLocation(), selection)) {
                        continue;
                    }
                }
            }
            entities.add(entity);
        }
        return entities;
    }

    private static boolean supportInsideSelection(Entity entity, Selection selection) {
        if (!(entity instanceof Hanging hanging)) {
            return true;
        }
        Block entityBlock = hanging.getLocation().getBlock();
        if (selection.contains(entityBlock.getLocation())) {
            return true;
        }
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP,
                org.bukkit.block.BlockFace.DOWN
        }) {
            Block neighbor = entityBlock.getRelative(face);
            if (selection.contains(neighbor.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private static BlockData[] copyDataArray(Clipboard clipboard) {
        BlockData[] copy = new BlockData[clipboard.getSizeX() * clipboard.getSizeY() * clipboard.getSizeZ()];
        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = 0; z < clipboard.getSizeZ(); z++) {
                for (int x = 0; x < clipboard.getSizeX(); x++) {
                    int idx = (y * clipboard.getSizeZ() + z) * clipboard.getSizeX() + x;
                    BlockData data = clipboard.get(x, y, z);
                    copy[idx] = data == null ? null : data.clone();
                }
            }
        }
        return copy;
    }

    private static BlockState[] copyStateArray(Clipboard clipboard) {
        BlockState[] copy = new BlockState[clipboard.getSizeX() * clipboard.getSizeY() * clipboard.getSizeZ()];
        for (int y = 0; y < clipboard.getSizeY(); y++) {
            for (int z = 0; z < clipboard.getSizeZ(); z++) {
                for (int x = 0; x < clipboard.getSizeX(); x++) {
                    int idx = (y * clipboard.getSizeZ() + z) * clipboard.getSizeX() + x;
                    BlockState state = clipboard.getState(x, y, z);
                    copy[idx] = state == null ? null : state.copy();
                }
            }
        }
        return copy;
    }

    private static boolean intersectsSelection(Location location, Selection selection) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(selection.getPos1().getWorld())) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= selection.getMinX() - 1 && x <= selection.getMaxX() + 1
                && y >= selection.getMinY() - 1 && y <= selection.getMaxY() + 1
                && z >= selection.getMinZ() - 1 && z <= selection.getMaxZ() + 1;
    }

    private static void captureHeightMap(World world, Selection selection, int[][] heights, Material[][] topMaterials) {
        for (int localX = 0; localX < heights.length; localX++) {
            for (int localZ = 0; localZ < heights[0].length; localZ++) {
                int x = selection.getMinX() + localX;
                int z = selection.getMinZ() + localZ;
                heights[localX][localZ] = selection.getMinY();
                topMaterials[localX][localZ] = null;
                for (int y = selection.getMaxY(); y >= selection.getMinY(); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    heights[localX][localZ] = y;
                    topMaterials[localX][localZ] = block.getType();
                    break;
                }
            }
        }
    }

    private static int[][] smoothHeights(int[][] heights) {
        int width = heights.length;
        int depth = heights[0].length;
        int[][] out = new int[width][depth];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int total = 0;
                int count = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nz < 0 || nx >= width || nz >= depth) {
                            continue;
                        }
                        total += heights[nx][nz];
                        count++;
                    }
                }
                out[x][z] = Math.round((float) total / Math.max(1, count));
            }
        }
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isSelectionUnderwater(World world, Selection selection) {
        int minX = selection.getMinX();
        int maxX = selection.getMaxX();
        int minZ = selection.getMinZ();
        int maxZ = selection.getMaxZ();
        int maxY = selection.getMaxY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, maxY, z).isLiquid()) {
                    return true;
                }
            }
        }
        return false;
    }
}
