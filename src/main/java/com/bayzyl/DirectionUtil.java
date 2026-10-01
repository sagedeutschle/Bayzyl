package com.bayzyl;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class DirectionUtil {
    private DirectionUtil() {
    }

    public static int[] resolve(Player player, String direction) {
        String value = direction == null || direction.isBlank() ? "forward" : direction.toLowerCase(Locale.ROOT);
        switch (value) {
            case "north":
                return new int[]{0, 0, -1};
            case "south":
                return new int[]{0, 0, 1};
            case "east":
                return new int[]{1, 0, 0};
            case "west":
                return new int[]{-1, 0, 0};
            case "northeast":
            case "north-east":
            case "ne":
                return new int[]{1, 0, -1};
            case "northwest":
            case "north-west":
            case "nw":
                return new int[]{-1, 0, -1};
            case "southeast":
            case "south-east":
            case "se":
                return new int[]{1, 0, 1};
            case "southwest":
            case "south-west":
            case "sw":
                return new int[]{-1, 0, 1};
            case "up":
                return new int[]{0, 1, 0};
            case "down":
                return new int[]{0, -1, 0};
            case "back":
                return invert(resolveLookDirection(player));
            case "left":
                return invert(resolveRelative(player, "right"));
            case "right":
                return resolveRelative(player, "right");
            case "me":
            case "forward":
            default:
                return resolveLookDirection(player);
        }
    }

    public static int[] resolveStackDefault(Player player, boolean diagonalEnabled) {
        if (!diagonalEnabled) {
            return resolveLookDirection(player);
        }

        var direction = player.getEyeLocation().getDirection();
        double absX = Math.abs(direction.getX());
        double absY = Math.abs(direction.getY());
        double absZ = Math.abs(direction.getZ());

        if (absY >= absX && absY >= absZ) {
            return resolveLookDirection(player);
        }

        float yaw = player.getLocation().getYaw();
        int facing = Math.floorMod(Math.round((yaw + 22.5f) / 45.0f), 8);
        return switch (facing) {
            case 0 -> new int[]{0, 0, 1};
            case 1 -> new int[]{-1, 0, 1};
            case 2 -> new int[]{-1, 0, 0};
            case 3 -> new int[]{-1, 0, -1};
            case 4 -> new int[]{0, 0, -1};
            case 5 -> new int[]{1, 0, -1};
            case 6 -> new int[]{1, 0, 0};
            default -> new int[]{1, 0, 1};
        };
    }

    public static String normalizeRotationKeyword(String input) {
        if (input == null) {
            return null;
        }
        String value = input.toLowerCase(Locale.ROOT);
        switch (value) {
            case "right":
            case "cw":
                return "90";
            case "back":
            case "around":
                return "180";
            case "left":
            case "ccw":
                return "270";
            default:
                return input;
        }
    }

    public static String normalizeFlipAxis(Player player, String input) {
        if (input == null) {
            return null;
        }
        String value = input.toLowerCase(Locale.ROOT);
        switch (value) {
            case "x", "left-right", "lr", "east-west", "ew":
                return "x";
            case "y", "up-down", "ud", "vertical":
                return "y";
            case "z", "front-back", "fb", "north-south", "ns":
                return "z";
            case "left", "right":
                return isFacingNorthSouth(player) ? "x" : "z";
            case "forward", "back":
                return isFacingNorthSouth(player) ? "z" : "x";
            case "up", "down":
                return "y";
            default:
                return null;
        }
    }

    public static int[] resolveLookDirection(Player player) {
        var direction = player.getEyeLocation().getDirection();
        double absX = Math.abs(direction.getX());
        double absY = Math.abs(direction.getY());
        double absZ = Math.abs(direction.getZ());

        if (absY >= absX && absY >= absZ) {
            return new int[]{0, direction.getY() >= 0 ? 1 : -1, 0};
        }
        if (absX >= absZ) {
            return new int[]{direction.getX() >= 0 ? 1 : -1, 0, 0};
        }
        return new int[]{0, 0, direction.getZ() >= 0 ? 1 : -1};
    }

    private static int[] resolveRelative(Player player, String mode) {
        float yaw = player.getLocation().getYaw();
        int facing = Math.floorMod(Math.round(yaw / 90.0f), 4);

        if (mode.equals("forward")) {
            switch (facing) {
                case 0:
                    return new int[]{0, 0, 1};
                case 1:
                    return new int[]{-1, 0, 0};
                case 2:
                    return new int[]{0, 0, -1};
                default:
                    return new int[]{1, 0, 0};
            }
        }

        switch (facing) {
            case 0:
                return new int[]{-1, 0, 0};
            case 1:
                return new int[]{0, 0, -1};
            case 2:
                return new int[]{1, 0, 0};
            default:
                return new int[]{0, 0, 1};
        }
    }

    private static int[] invert(int[] vector) {
        return new int[]{-vector[0], -vector[1], -vector[2]};
    }

    private static boolean isFacingNorthSouth(Player player) {
        int[] forward = resolve(player, "forward");
        return forward[2] != 0;
    }
}
