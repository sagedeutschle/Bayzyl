package com.bayzyl;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class ClipboardCommandParser {
    private ClipboardCommandParser() {
    }

    public static ClipboardPasteRequest parsePaste(String[] args) {
        int rotation = 0;
        boolean ignoreAir = false;
        String at = "player";
        boolean selectAfterPaste = false;
        boolean previewOnly = false;
        boolean confirm = false;

        for (String raw : args) {
            String token = raw.toLowerCase(Locale.ROOT);
            if (token.startsWith("-") && token.length() > 1) {
                for (int i = 1; i < token.length(); i++) {
                    switch (token.charAt(i)) {
                        case 'a' -> ignoreAir = true;
                        case 'o' -> at = "origin";
                        case 'p' -> at = "player";
                        case 's' -> selectAfterPaste = true;
                        case 'n' -> previewOnly = true;
                        default -> {
                        }
                    }
                }
                continue;
            }
            if (!token.contains(":")) {
                continue;
            }
            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "rotation", "rotate" -> {
                    try {
                        rotation = Integer.parseInt(DirectionUtil.normalizeRotationKeyword(value));
                    } catch (NumberFormatException ignored) {
                    }
                }
                case "at" -> {
                    if (value.equals("origin") || value.equals("original")) {
                        at = "origin";
                    } else if (value.equals("target") || value.equals("look")) {
                        at = "target";
                    } else {
                        at = "player";
                    }
                }
                case "confirm" -> confirm = value.equals("true");
                case "air", "ignoreair" -> ignoreAir = isTrue(value);
                case "select" -> selectAfterPaste = isTrue(value);
                case "preview" -> previewOnly = isTrue(value);
                default -> {
                }
            }
        }

        return new ClipboardPasteRequest(rotation, ignoreAir, at, selectAfterPaste, previewOnly, confirm);
    }

    public static ClipboardRotateRequest parseRotate(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Syntax: /rotate <0|90|180|270|left|right|back> [live]");
        }

        try {
            int rotation = Integer.parseInt(DirectionUtil.normalizeRotationKeyword(args[0]));
            int normalized = ((rotation % 360) + 360) % 360;
            if (normalized % 90 != 0) {
                throw new IllegalArgumentException("Only 90-degree rotations are supported right now.");
            }
            boolean live = false;
            for (int i = 1; i < args.length; i++) {
                String token = args[i].toLowerCase(Locale.ROOT);
                if (token.equals("live") || token.equals("mode:live")) {
                    live = true;
                }
            }
            return new ClipboardRotateRequest(normalized, live);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Rotation must be a number or direction keyword.");
        }
    }

    public static ClipboardFlipRequest parseFlip(Player player, String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Syntax: /flip <x|y|z|left-right|front-back|up-down>");
        }

        String axis = DirectionUtil.normalizeFlipAxis(player, args[0]);
        if (axis == null) {
            throw new IllegalArgumentException("Unknown flip axis.");
        }
        return new ClipboardFlipRequest(axis);
    }

    public static ClipboardStackRequest parseStack(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Syntax: /stack <count> [<direction>] [-a] [confirm:true] or /stack random <count> [spread:<n>|x:<n>|y:<n>|z:<n>] [-a] [confirm:true]");
        }

        boolean random = args[0].equalsIgnoreCase("random");
        int startIndex = random ? 1 : 0;
        if (args.length <= startIndex) {
            throw new IllegalArgumentException("Count must be a number.");
        }

        int count;
        try {
            count = Integer.parseInt(args[startIndex]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Count must be a number.");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0.");
        }

        String direction = null;
        boolean ignoreAir = false;
        boolean confirm = false;
        int spreadX = 1;
        int spreadY = 0;
        int spreadZ = 1;

        for (int i = startIndex + 1; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (token.startsWith("-") && token.length() > 1) {
                for (int flagIndex = 1; flagIndex < token.length(); flagIndex++) {
                    if (token.charAt(flagIndex) == 'a') {
                        ignoreAir = true;
                    }
                }
                continue;
            }
            if (token.contains(":")) {
                String[] parts = token.split(":", 2);
                String key = parts[0];
                String value = parts.length > 1 ? parts[1] : "";
                switch (key) {
                    case "confirm" -> confirm = value.equals("true");
                    case "direction", "dir" -> direction = value;
                    case "air", "ignoreair" -> ignoreAir = isTrue(value);
                    case "spread", "range" -> {
                        int spread = parseNonNegative(value, "Spread");
                        spreadX = spread;
                        spreadY = spread;
                        spreadZ = spread;
                    }
                    case "x" -> spreadX = parseNonNegative(value, "X spread");
                    case "y" -> spreadY = parseNonNegative(value, "Y spread");
                    case "z" -> spreadZ = parseNonNegative(value, "Z spread");
                    default -> {
                    }
                }
                continue;
            }
            if (!random) {
                direction = token;
            }
        }

        return new ClipboardStackRequest(random, count, direction, ignoreAir, confirm, spreadX, spreadY, spreadZ);
    }

    public static ClipboardMoveRequest parseMove(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Syntax: /move <distance> [<direction>] [-a] [confirm:true]");
        }

        int distance;
        try {
            distance = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Distance must be a number.");
        }
        if (distance <= 0) {
            throw new IllegalArgumentException("Distance must be greater than 0.");
        }

        String direction = "forward";
        boolean ignoreAir = false;
        boolean confirm = false;

        for (int i = 1; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (token.startsWith("-") && token.length() > 1) {
                for (int flagIndex = 1; flagIndex < token.length(); flagIndex++) {
                    if (token.charAt(flagIndex) == 'a') {
                        ignoreAir = true;
                    }
                }
                continue;
            }
            if (token.contains(":")) {
                String[] parts = token.split(":", 2);
                String key = parts[0];
                String value = parts.length > 1 ? parts[1] : "";
                switch (key) {
                    case "confirm" -> confirm = value.equals("true");
                    case "direction", "dir" -> direction = value;
                    case "air", "ignoreair" -> ignoreAir = isTrue(value);
                    default -> {
                    }
                }
                continue;
            }
            direction = token;
        }

        return new ClipboardMoveRequest(distance, direction, ignoreAir, confirm);
    }

    private static boolean isTrue(String value) {
        return value.equals("true") || value.equals("on") || value.equals("yes");
    }

    private static int parseNonNegative(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(label + " must be 0 or greater.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }
}
