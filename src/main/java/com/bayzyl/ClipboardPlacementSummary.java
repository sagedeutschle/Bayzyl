package com.bayzyl;

import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public final class ClipboardPlacementSummary {
    private ClipboardPlacementSummary() {
    }

    public static List<String> describe(Clipboard clipboard, Location anchor, int rotation) {
        if (clipboard == null || anchor == null || anchor.getWorld() == null) {
            return List.of(ChatColor.RED + "No clipboard placement can be described.");
        }
        Selection bounds = EditUtil.getPasteSelection(clipboard, anchor, rotation);
        if (bounds == null) {
            return List.of(ChatColor.RED + "Could not resolve schematic placement bounds.");
        }

        int rot = normalizeRotation(rotation);
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "Schematic orientation " + ChatColor.GRAY + "(rotation:" + rot + ")");
        lines.add(ChatColor.GRAY + "Anchor/origin lands at " + ChatColor.WHITE + block(anchor)
                + ChatColor.GRAY + ". Paste footprint is measured from that anchor.");
        lines.add(ChatColor.GRAY + "Size " + ChatColor.WHITE + clipboard.getSizeX() + "x" + clipboard.getSizeY() + "x" + clipboard.getSizeZ()
                + ChatColor.GRAY + " blocks; bounds " + ChatColor.WHITE + block(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ())
                + ChatColor.GRAY + " -> " + ChatColor.WHITE + block(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()) + ChatColor.GRAY + ".");
        lines.add(ChatColor.GRAY + "From anchor: X " + ChatColor.WHITE + axisRange(bounds.getMinX() - anchor.getBlockX(), bounds.getMaxX() - anchor.getBlockX(), "west", "east")
                + ChatColor.GRAY + ", Y " + ChatColor.WHITE + verticalRange(bounds.getMinY() - anchor.getBlockY(), bounds.getMaxY() - anchor.getBlockY())
                + ChatColor.GRAY + ", Z " + ChatColor.WHITE + axisRange(bounds.getMinZ() - anchor.getBlockZ(), bounds.getMaxZ() - anchor.getBlockZ(), "north", "south") + ChatColor.GRAY + ".");
        lines.add(ChatColor.GRAY + "Center is about " + ChatColor.WHITE + center(bounds)
                + ChatColor.GRAY + " (" + relativeCenter(bounds, anchor) + ChatColor.GRAY + ").");
        lines.add(ChatColor.GRAY + "Bottom corners: "
                + ChatColor.WHITE + "NW " + block(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ())
                + ChatColor.GRAY + " | " + ChatColor.WHITE + "NE " + block(bounds.getMaxX(), bounds.getMinY(), bounds.getMinZ())
                + ChatColor.GRAY + " | " + ChatColor.WHITE + "SW " + block(bounds.getMinX(), bounds.getMinY(), bounds.getMaxZ())
                + ChatColor.GRAY + " | " + ChatColor.WHITE + "SE " + block(bounds.getMaxX(), bounds.getMinY(), bounds.getMaxZ()));
        return lines;
    }

    private static int normalizeRotation(int rotation) {
        return ((rotation % 360) + 360) % 360;
    }

    private static String block(Location location) {
        return block(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static String block(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static String center(Selection bounds) {
        double x = (bounds.getMinX() + bounds.getMaxX()) / 2.0D;
        double y = (bounds.getMinY() + bounds.getMaxY()) / 2.0D;
        double z = (bounds.getMinZ() + bounds.getMaxZ()) / 2.0D;
        return oneDecimal(x) + "," + oneDecimal(y) + "," + oneDecimal(z);
    }

    private static String relativeCenter(Selection bounds, Location anchor) {
        double x = (bounds.getMinX() + bounds.getMaxX()) / 2.0D - anchor.getBlockX();
        double y = (bounds.getMinY() + bounds.getMaxY()) / 2.0D - anchor.getBlockY();
        double z = (bounds.getMinZ() + bounds.getMaxZ()) / 2.0D - anchor.getBlockZ();
        return relativeAxis(x, "west", "east") + ", "
                + relativeVertical(y) + ", "
                + relativeAxis(z, "north", "south");
    }

    private static String axisRange(int minOffset, int maxOffset, String negativeName, String positiveName) {
        return relativeAxis(minOffset, negativeName, positiveName) + " to " + relativeAxis(maxOffset, negativeName, positiveName);
    }

    private static String verticalRange(int minOffset, int maxOffset) {
        return relativeVertical(minOffset) + " to " + relativeVertical(maxOffset);
    }

    private static String relativeAxis(double offset, String negativeName, String positiveName) {
        if (offset < 0) {
            return oneDecimal(Math.abs(offset)) + " " + negativeName;
        }
        if (offset > 0) {
            return oneDecimal(offset) + " " + positiveName;
        }
        return "anchor";
    }

    private static String relativeVertical(double offset) {
        if (offset < 0) {
            return oneDecimal(Math.abs(offset)) + " down";
        }
        if (offset > 0) {
            return oneDecimal(offset) + " up";
        }
        return "anchor";
    }

    private static String oneDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
