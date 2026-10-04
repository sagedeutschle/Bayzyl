package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class MovementAssistService {
    private static final int TARGET_RANGE = 120;
    // Passable-but-harmful blocks the safe-stand search must never put a player inside of.
    private static final Set<Material> HAZARDOUS_BODY = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.COBWEB);
    private static final Set<Material> HAZARDOUS_FLOOR = EnumSet.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    public boolean unstick(Player player) {
        if (!canUseUnstick(player)) {
            return false;
        }
        Location candidate = findNearestSafeLocation(player.getLocation(), 5, 8, 5, true);
        if (candidate == null) {
            return false;
        }
        return player.teleport(candidate);
    }

    public boolean shouldUnstick(Player player) {
        if (!canUseUnstick(player)) {
            return false;
        }
        return intersectsSolid(player);
    }

    public boolean thru(Player player) {
        RayTraceResult result = player.rayTraceBlocks(TARGET_RANGE);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) {
            return false;
        }

        Block hit = result.getHitBlock();
        BlockFace face = result.getHitBlockFace();
        Block beyond = hit.getRelative(face);

        Location direct = standLocation(beyond);
        if (direct != null) {
            return player.teleport(direct);
        }

        for (int steps = 2; steps <= 8; steps++) {
            Block cursor = hit;
            for (int i = 0; i < steps; i++) {
                cursor = cursor.getRelative(face);
            }
            Location candidate = findNearestSafeLocation(cursor.getLocation(), 2, 3, 2, false);
            if (candidate != null) {
                return player.teleport(candidate);
            }
        }
        return false;
    }

    public double rulerDistance(Player player) {
        RayTraceResult result = player.rayTraceBlocks(TARGET_RANGE);
        if (result == null || result.getHitBlock() == null) {
            return -1.0D;
        }

        Location eye = player.getEyeLocation();
        Location target = result.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        return eye.distance(target);
    }

    public boolean surface(Player player) {
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) {
            return false;
        }

        int x = current.getBlockX();
        int z = current.getBlockZ();
        int topY = world.getHighestBlockYAt(x, z);
        for (int y = topY + 2; y >= world.getMinHeight(); y--) {
            Location candidate = new Location(world, x + 0.5, y, z + 0.5, current.getYaw(), current.getPitch());
            if (isSafeStandLocation(candidate)) {
                return player.teleport(candidate);
            }
        }
        return false;
    }

    public boolean ascend(Player player) {
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) {
            return false;
        }
        int x = current.getBlockX();
        int z = current.getBlockZ();
        int startY = current.getBlockY() + 1;
        for (int y = startY; y < world.getMaxHeight(); y++) {
            Location candidate = new Location(world, x + 0.5, y, z + 0.5, current.getYaw(), current.getPitch());
            if (isSafeStandLocation(candidate)) {
                return player.teleport(candidate);
            }
        }
        return false;
    }

    public boolean descend(Player player) {
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) {
            return false;
        }
        int x = current.getBlockX();
        int z = current.getBlockZ();
        for (int y = current.getBlockY() - 1; y >= world.getMinHeight(); y--) {
            Location candidate = new Location(world, x + 0.5, y, z + 0.5, current.getYaw(), current.getPitch());
            if (isSafeStandLocation(candidate)) {
                return player.teleport(candidate);
            }
        }
        return false;
    }

    public void align(Player player, String direction) {
        Location location = player.getLocation().clone();
        location.setYaw(resolveYaw(location.getYaw(), direction));
        location.setPitch(0.0f);
        player.teleport(location);
    }

    public int ceilingClearance(Player player) {
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) {
            return -1;
        }
        int x = current.getBlockX();
        int z = current.getBlockZ();
        int feetY = current.getBlockY();
        for (int y = feetY + 1; y < world.getMaxHeight(); y++) {
            if (!world.getBlockAt(x, y, z).isPassable()) {
                return y - feetY - 1;
            }
        }
        return -1;
    }

    public String whereAmI(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return ChatColor.RED + "World unavailable.";
        }
        Biome biome = world.getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return ChatColor.GREEN.toString() + ChatColor.BOLD + "XYZ" + ChatColor.RESET + ChatColor.WHITE + ": " + ChatColor.AQUA + String.format(java.util.Locale.ROOT, "%.2f, %.2f, %.2f", location.getX(), location.getY(), location.getZ())
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Block " + ChatColor.GOLD + String.format("%d, %d, %d", location.getBlockX(), location.getBlockY(), location.getBlockZ())
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Chunk " + ChatColor.GOLD + String.format("%d, %d", location.getChunk().getX(), location.getChunk().getZ())
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Biome " + ChatColor.AQUA + biome.name().toLowerCase(java.util.Locale.ROOT)
                + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Facing " + ChatColor.GOLD + cardinal(location.getYaw());
    }

    public boolean centerMe(Player player) {
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) {
            return false;
        }
        Location centered = new Location(world,
                current.getBlockX() + 0.5,
                current.getY(),
                current.getBlockZ() + 0.5,
                current.getYaw(),
                current.getPitch());
        return player.teleport(centered);
    }

    public boolean stepForward(Player player, int blocks) {
        if (blocks < 1) {
            return false;
        }
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) {
            return false;
        }

        Vector direction = current.getDirection().clone();
        direction.setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) {
            direction = new Vector(-Math.sin(Math.toRadians(current.getYaw())), 0.0, Math.cos(Math.toRadians(current.getYaw())));
        }
        direction.normalize().multiply(blocks);

        Location target = current.clone().add(direction);
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());

        Location safe = findNearestSafeLocation(target, 1, 2, 1, false);
        if (safe != null) {
            return player.teleport(safe);
        }
        // Nothing to stand on (a flying builder stepping through open air) is fine, but never step into a wall or hazard.
        if (!isOpenAir(target)) {
            return false;
        }
        return player.teleport(target);
    }

    private Location findNearestSafeLocation(Location origin, int horizontalRadius, int upRange, int downRange, boolean excludeOriginBlock) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }

        List<Location> candidates = new ArrayList<>();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        for (int y = baseY + upRange; y >= baseY - downRange; y--) {
            for (int x = baseX - horizontalRadius; x <= baseX + horizontalRadius; x++) {
                for (int z = baseZ - horizontalRadius; z <= baseZ + horizontalRadius; z++) {
                    if (excludeOriginBlock && x == baseX && y == baseY && z == baseZ) {
                        continue;
                    }
                    Location location = new Location(world, x + 0.5, y, z + 0.5, origin.getYaw(), origin.getPitch());
                    if (isSafeStandLocation(location)) {
                        candidates.add(location);
                    }
                }
            }
        }

        candidates.sort(Comparator
                .<Location>comparingDouble(loc -> loc.distanceSquared(origin))
                .thenComparingInt((Location loc) -> Math.abs(loc.getBlockY() - baseY))
                .thenComparingDouble(loc -> planarDistanceSquared(loc, origin)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private double planarDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean isSafeStandLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);
        return isPassable(feet) && isPassable(head) && below.getType().isSolid()
                && !isHazardousStand(feet.getType(), head.getType(), below.getType());
    }

    static boolean isHazardousStand(Material feet, Material head, Material floor) {
        return HAZARDOUS_BODY.contains(feet) || HAZARDOUS_BODY.contains(head) || HAZARDOUS_FLOOR.contains(floor);
    }

    private boolean isOpenAir(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Block head = feet.getRelative(BlockFace.UP);
        return isPassable(feet) && isPassable(head)
                && !isHazardousStand(feet.getType(), head.getType(), Material.AIR);
    }

    private boolean intersectsSolid(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        BoundingBox box = player.getBoundingBox().expand(-0.02);
        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX());
        int maxY = (int) Math.floor(box.getMaxY());
        int maxZ = (int) Math.floor(box.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isPassable(block)) {
                        continue;
                    }
                    if (block.getBoundingBox().overlaps(box)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPassable(Block block) {
        return block.isPassable() || !block.getType().isSolid();
    }

    private Location standLocation(Block block) {
        if (!isPassable(block)) {
            return null;
        }
        if (!isPassable(block.getRelative(BlockFace.UP))) {
            return null;
        }
        if (!block.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return null;
        }
        return block.getLocation().add(0.5, 0.0, 0.5);
    }

    private float resolveYaw(float currentYaw, String direction) {
        if (direction != null) {
            return switch (direction.toLowerCase(java.util.Locale.ROOT)) {
                case "south" -> 0.0f;
                case "sw" -> 45.0f;
                case "west" -> 90.0f;
                case "nw" -> 135.0f;
                case "north" -> 180.0f;
                case "ne" -> 225.0f;
                case "east" -> 270.0f;
                case "se" -> 315.0f;
                default -> nearestYaw(currentYaw);
            };
        }
        return nearestYaw(currentYaw);
    }

    private float nearestYaw(float yaw) {
        float normalized = ((yaw % 360.0f) + 360.0f) % 360.0f;
        int quadrant = Math.round(normalized / 90.0f) % 4;
        return switch (quadrant) {
            case 0 -> 0.0f;
            case 1 -> 90.0f;
            case 2 -> 180.0f;
            default -> 270.0f;
        };
    }

    private String cardinal(float yaw) {
        float normalized = ((yaw % 360.0f) + 360.0f) % 360.0f;
        if (normalized < 45.0f || normalized >= 315.0f) {
            return "south";
        }
        if (normalized < 135.0f) {
            return "west";
        }
        if (normalized < 225.0f) {
            return "north";
        }
        return "east";
    }

    private boolean canUseUnstick(Player player) {
        return player.getGameMode() != GameMode.SPECTATOR;
    }
}
