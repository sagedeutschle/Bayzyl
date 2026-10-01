package com.bayzyl;

import java.util.Arrays;
import java.util.Locale;

public final class ShapeCommandParser {
    private ShapeCommandParser() {
    }

    public static SphereRequest parseSphere(String verb, String[] args, boolean hollow) {
        return parseSphere(verb, args, hollow, ShapeAnchorMode.PLAYER);
    }

    private static SphereRequest parseSphere(String verb, String[] args, boolean hollow, ShapeAnchorMode defaultAnchorMode) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <BLOCK|distribution> <radius|x,y,z> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }

        BlockDistribution distribution = BlockDistribution.parse(args[0]);

        ParsedShapeArgs parsed = parseArgs(args, 1, defaultAnchorMode);
        if (parsed.positionals().isEmpty()) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <BLOCK|distribution> <radius|x,y,z> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }
        int[] radii = parseRadii(parsed.positionals().get(0), 3);
        ShapeOptions options = parsed.options();

        return new SphereRequest(
                distribution,
                radii[0],
                radii[1],
                radii[2],
                hollow,
                options.anchorMode(),
                options.thickness(),
                options.evenCenter(),
                options.preview(),
                options.confirm(),
                options.mask()
        );
    }

    public static CylinderRequest parseCylinder(String verb, String[] args, boolean hollow) {
        return parseCylinder(verb, args, hollow, ShapeAnchorMode.PLAYER);
    }

    private static CylinderRequest parseCylinder(String verb, String[] args, boolean hollow, ShapeAnchorMode defaultAnchorMode) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <BLOCK|distribution> <radius|x,z> [height] [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }

        BlockDistribution distribution = BlockDistribution.parse(args[0]);

        ParsedShapeArgs parsed = parseArgs(args, 1, defaultAnchorMode);
        if (parsed.positionals().isEmpty()) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <BLOCK|distribution> <radius|x,z> [height] [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }
        int[] radii = parseRadii(parsed.positionals().get(0), 2);
        int height = parsed.positionals().size() >= 2 ? parsePositiveInt(parsed.positionals().get(1), "Height") : 1;
        ShapeOptions options = parsed.options();

        return new CylinderRequest(
                distribution,
                radii[0],
                radii[1],
                height,
                hollow,
                options.anchorMode(),
                options.thickness(),
                options.evenCenter(),
                options.preview(),
                options.confirm(),
                options.mask()
        );
    }

    public static PyramidRequest parsePyramid(String verb, String[] args, boolean hollow) {
        return parsePyramid(verb, args, hollow, ShapeAnchorMode.PLAYER);
    }

    private static PyramidRequest parsePyramid(String verb, String[] args, boolean hollow, ShapeAnchorMode defaultAnchorMode) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <BLOCK|distribution> <size> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }

        BlockDistribution distribution = BlockDistribution.parse(args[0]);

        ParsedShapeArgs parsed = parseArgs(args, 1, defaultAnchorMode);
        if (parsed.positionals().isEmpty()) {
            throw new IllegalArgumentException("Syntax: /" + verb + " <BLOCK|distribution> <size> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }
        int size = parsePositiveInt(parsed.positionals().get(0), "Size");
        ShapeOptions options = parsed.options();

        return new PyramidRequest(
                distribution,
                size,
                hollow,
                options.anchorMode(),
                options.preview(),
                options.confirm(),
                options.mask()
        );
    }

    public static ShapeBrushSettings parseBrush(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("Syntax: /brush <sphere|hsphere|cyl|hcyl|pyramid|hpyramid> <BLOCK|distribution> <args...> [mask:<blocks>] [at:<player|target|selection-center>] [confirm:true]");
        }

        ShapeBrushType type = ShapeBrushType.parse(args[0]);
        if (type == null) {
            throw new IllegalArgumentException("Unknown brush shape: " + args[0]);
        }

        String[] shapeArgs = normalizeBrushArgs(type, Arrays.copyOfRange(args, 1, args.length));
        return switch (type) {
            case SPHERE -> fromSphere(type, parseSphere(type.commandName(), shapeArgs, false, ShapeAnchorMode.TARGET));
            case HSPHERE -> fromSphere(type, parseSphere(type.commandName(), shapeArgs, true, ShapeAnchorMode.TARGET));
            case CYL -> fromCylinder(type, parseCylinder(type.commandName(), shapeArgs, false, ShapeAnchorMode.TARGET));
            case HCYL -> fromCylinder(type, parseCylinder(type.commandName(), shapeArgs, true, ShapeAnchorMode.TARGET));
            case PYRAMID -> fromPyramid(type, parsePyramid(type.commandName(), shapeArgs, false, ShapeAnchorMode.TARGET));
            case HPYRAMID -> fromPyramid(type, parsePyramid(type.commandName(), shapeArgs, true, ShapeAnchorMode.TARGET));
        };
    }

    private static String[] normalizeBrushArgs(ShapeBrushType type, String[] shapeArgs) {
        int requiredPositionals = switch (type) {
            case SPHERE, HSPHERE -> 2;
            case CYL, HCYL -> 3;
            case PYRAMID, HPYRAMID -> 2;
        };
        if (shapeArgs.length <= requiredPositionals) {
            return shapeArgs;
        }
        String last = shapeArgs[shapeArgs.length - 1];
        if (last.contains(":")) {
            return shapeArgs;
        }
        if (!BlockMask.isResolvable(last)) {
            return shapeArgs;
        }
        if (shapeArgs.length == requiredPositionals + 1) {
            String[] trimmed = Arrays.copyOf(shapeArgs, shapeArgs.length - 1);
            return appendMask(trimmed, last);
        }
        return shapeArgs;
    }

    private static String[] appendMask(String[] shapeArgs, String maskToken) {
        String[] withMask = Arrays.copyOf(shapeArgs, shapeArgs.length + 1);
        withMask[shapeArgs.length] = "mask:" + maskToken;
        return withMask;
    }

    private static ShapeBrushSettings fromSphere(ShapeBrushType type, SphereRequest request) {
        return new ShapeBrushSettings(
                type,
                request.distribution(),
                request.radiusX(),
                request.radiusY(),
                request.radiusZ(),
                0,
                0,
                request.anchorMode(),
                request.mask(),
                request.confirm()
        );
    }

    private static ShapeBrushSettings fromCylinder(ShapeBrushType type, CylinderRequest request) {
        return new ShapeBrushSettings(
                type,
                request.distribution(),
                request.radiusX(),
                0,
                request.radiusZ(),
                request.height(),
                0,
                request.anchorMode(),
                request.mask(),
                request.confirm()
        );
    }

    private static ShapeBrushSettings fromPyramid(ShapeBrushType type, PyramidRequest request) {
        return new ShapeBrushSettings(
                type,
                request.distribution(),
                0,
                0,
                0,
                0,
                request.size(),
                request.anchorMode(),
                request.mask(),
                request.confirm()
        );
    }

    private static ParsedShapeArgs parseArgs(String[] args, int startIndex) {
        return parseArgs(args, startIndex, ShapeAnchorMode.PLAYER);
    }

    private static ParsedShapeArgs parseArgs(String[] args, int startIndex, ShapeAnchorMode defaultAnchorMode) {
        ShapeAnchorMode anchorMode = defaultAnchorMode;
        int thickness = 1;
        boolean evenCenter = false;
        boolean preview = false;
        boolean confirm = false;
        BlockMask mask = BlockMask.parse(null);
        java.util.List<String> positionals = new java.util.ArrayList<>();

        for (int i = startIndex; i < args.length; i++) {
            String token = args[i].toLowerCase(Locale.ROOT);
            if (token.equals("confirm:true")) {
                confirm = true;
                continue;
            }
            if (!token.contains(":")) {
                positionals.add(args[i]);
                continue;
            }

            String[] parts = token.split(":", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "confirm":
                    confirm = parseBoolean(value, "confirm");
                    break;
                case "preview":
                    preview = parseBoolean(value, "preview");
                    break;
                case "mask":
                    mask = BlockMask.parse(value);
                    break;
                case "at":
                    anchorMode = parseAnchor(value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + key);
            }
        }

        return new ParsedShapeArgs(positionals, new ShapeOptions(anchorMode, thickness, evenCenter, preview, confirm, mask));
    }

    private static ShapeAnchorMode parseAnchor(String value) {
        return switch (value) {
            case "player", "feet", "here" -> ShapeAnchorMode.PLAYER;
            case "eyes", "eye" -> ShapeAnchorMode.EYES;
            case "target", "look" -> ShapeAnchorMode.TARGET;
            case "selection", "selection-center", "sel", "center" -> ShapeAnchorMode.SELECTION_CENTER;
            default -> throw new IllegalArgumentException("Unknown anchor: " + value);
        };
    }

    private static int[] parseRadii(String input, int dimensions) {
        String normalized = input.toLowerCase(Locale.ROOT).replace('x', ',');
        String[] parts = normalized.split(",");
        if (parts.length == 0 || parts.length > dimensions) {
            throw new IllegalArgumentException("Invalid radius format.");
        }

        int[] values = new int[dimensions];
        int first = parsePositiveInt(parts[0], "Radius");
        for (int i = 0; i < dimensions; i++) {
            values[i] = first;
        }
        for (int i = 1; i < parts.length; i++) {
            values[i] = parsePositiveInt(parts[i], "Radius");
        }
        return values;
    }

    private static boolean parseBoolean(String input, String label) {
        return switch (input) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> throw new IllegalArgumentException(label + " must be true or false.");
        };
    }

    private static int parsePositiveInt(String input, String label) {
        try {
            int value = Integer.parseInt(input);
            if (value <= 0) {
                throw new IllegalArgumentException(label + " must be greater than 0.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private record ShapeOptions(
            ShapeAnchorMode anchorMode,
            int thickness,
            boolean evenCenter,
            boolean preview,
            boolean confirm,
            BlockMask mask
    ) {
    }

    private record ParsedShapeArgs(
            java.util.List<String> positionals,
            ShapeOptions options
    ) {
    }
}
