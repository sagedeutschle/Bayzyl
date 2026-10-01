package com.bayzyl.generation;

import com.bayzyl.EditUtil;
import com.bayzyl.ShapeAnchorMode;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;

import java.util.Locale;

public final class GeneratorCommandParser {
    private GeneratorCommandParser() {
    }

    public static GenerateShapeRequest parseGenerate(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Syntax: /generate <block> <expression> [mode:normalized|raw|center|origin] [hollow:true] [confirm:true]");
        }

        Material material = EditUtil.parseBlock(args[0]);
        if (material == null) {
            throw new IllegalArgumentException("Unknown block: " + args[0]);
        }

        GeneratorOptions options = new GeneratorOptions();
        String expression = parseTail(args, 1, options);
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Expression is required.");
        }
        return new GenerateShapeRequest(material, expression, options.coordinateMode, options.hollow, options.confirm);
    }

    public static GenerateBiomeRequest parseGenerateBiome(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Syntax: /generatebiome <biome> [sphere|cyl|pyramid|dome|bowl|expr <expression>] [options]");
        }

        Biome biome = parseBiome(args[0]);
        GeneratorOptions options = new GeneratorOptions();
        BiomeGeneratorMode mode = BiomeGeneratorMode.FULL;
        String expression = "";
        int tailStart = 1;

        if (args.length >= 2 && !looksLikeOption(args[1])) {
            try {
                mode = BiomeGeneratorMode.parse(args[1]);
                tailStart = 2;
            } catch (IllegalArgumentException ignored) {
                mode = BiomeGeneratorMode.EXPRESSION;
                tailStart = 1;
            }
        }

        if (mode == BiomeGeneratorMode.EXPRESSION) {
            expression = parseTail(args, tailStart, options);
            if (expression.isBlank()) {
                throw new IllegalArgumentException("Expression is required.");
            }
        } else {
            parseShapeTail(args, tailStart, options);
        }
        if (mode == BiomeGeneratorMode.CYLINDER && options.height == null) {
            options.height = 1;
        }

        return new GenerateBiomeRequest(
                biome,
                mode,
                expression,
                options.coordinateMode,
                options.hollow,
                options.confirm,
                options.preview,
                options.radius,
                options.radiusX,
                options.radiusY,
                options.radiusZ,
                options.height,
                options.size
        );
    }

    public static ForestGenRequest parseForest(String[] args) {
        int size = 5;
        TreeType type = TreeType.TREE;
        double density = 5.0;
        ShapeAnchorMode anchorMode = ShapeAnchorMode.PLAYER;
        boolean confirm = false;

        int positionalIndex = 0;
        for (String arg : args) {
            if (arg.contains(":")) {
                String[] parts = arg.split(":", 2);
                String key = parts[0].toLowerCase(Locale.ROOT);
                String value = parts.length > 1 ? parts[1] : "";
                ShapeAnchorMode parsedAnchor = parseAnchorOption(key, value);
                if (parsedAnchor != null) {
                    anchorMode = parsedAnchor;
                    continue;
                }
                switch (key) {
                    case "confirm" -> confirm = parseBoolean(value, "confirm");
                    default -> throw new IllegalArgumentException("Unknown option: " + key);
                }
                continue;
            }

            switch (positionalIndex++) {
                case 0 -> size = parsePositiveInt(arg, "Size");
                case 1 -> type = parseTreeType(arg);
                case 2 -> density = parseDensity(arg);
                default -> throw new IllegalArgumentException("Syntax: /forestgen [size] [type] [density] [at:<player|target|selection-center>] [confirm:true]");
            }
        }

        WorkEstimate estimate = OperationLimits.estimateForest(size, density);
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
        return new ForestGenRequest(size, type, density, anchorMode, confirm);
    }

    public static PumpkinPatchRequest parsePumpkins(String[] args) {
        int size = 5;
        ShapeAnchorMode anchorMode = ShapeAnchorMode.PLAYER;
        boolean confirm = false;
        boolean sawSize = false;

        for (String arg : args) {
            if (arg.contains(":")) {
                String[] parts = arg.split(":", 2);
                String key = parts[0].toLowerCase(Locale.ROOT);
                String value = parts.length > 1 ? parts[1] : "";
                ShapeAnchorMode parsedAnchor = parseAnchorOption(key, value);
                if (parsedAnchor != null) {
                    anchorMode = parsedAnchor;
                    continue;
                }
                switch (key) {
                    case "confirm" -> confirm = parseBoolean(value, "confirm");
                    default -> throw new IllegalArgumentException("Unknown option: " + key);
                }
                continue;
            }

            if (sawSize) {
                throw new IllegalArgumentException("Syntax: /pumpkins [size] [at:<player|target|selection-center>] [confirm:true]");
            }
            size = parsePositiveInt(arg, "Size");
            sawSize = true;
        }

        WorkEstimate estimate = OperationLimits.estimatePumpkins(size);
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
        return new PumpkinPatchRequest(size, anchorMode, confirm);
    }

    public static FeatureGenRequest parseFeatureGen(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Syntax: /genfeature <feature_id> [at:<player|target|selection-center>] [confirm:true]");
        }

        String featureId = args[0];
        ShapeAnchorMode anchorMode = ShapeAnchorMode.PLAYER; // Default anchor
        boolean confirm = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.contains(":")) {
                String[] parts = arg.split(":", 2);
                String key = parts[0].toLowerCase(Locale.ROOT);
                String value = parts.length > 1 ? parts[1] : "";
                ShapeAnchorMode parsedAnchor = parseAnchorOption(key, value);
                if (parsedAnchor != null) {
                    anchorMode = parsedAnchor;
                    continue;
                }
                switch (key) {
                    case "confirm" -> confirm = parseBoolean(value, "confirm");
                    default -> throw new IllegalArgumentException("Unknown option: " + key);
                }
            } else {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
        }
        return new FeatureGenRequest(featureId, anchorMode, confirm);
    }

    public static StructureGenRequest parseStructureGen(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Syntax: /genstructure <structure_id> [at:<player|target|selection-center>] [confirm:true]");
        }
        
        String structureId = args[0];
        ShapeAnchorMode anchorMode = ShapeAnchorMode.TARGET; // Default anchor
        boolean confirm = false;
        
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.contains(":")) {
                String[] parts = arg.split(":", 2);
                String key = parts[0].toLowerCase(Locale.ROOT);
                String value = parts.length > 1 ? parts[1] : "";
                ShapeAnchorMode parsedAnchor = parseAnchorOption(key, value);
                if (parsedAnchor != null) {
                    anchorMode = parsedAnchor;
                    continue;
                }
                switch (key) {
                    case "confirm" -> confirm = parseBoolean(value, "confirm");
                    default -> throw new IllegalArgumentException("Unknown option: " + key);
                }
            } else {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
        }
        
        return new StructureGenRequest(structureId, anchorMode, confirm);
    }


    private static String parseTail(String[] args, int startIndex, GeneratorOptions options) {
        StringBuilder expression = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i];
            if (looksLikeOption(token)) {
                String[] parts = token.split(":", 2);
                String key = parts[0].toLowerCase(Locale.ROOT);
                String value = parts.length > 1 ? parts[1] : "";
                switch (key) {
                    case "mode" -> options.coordinateMode = CoordinateMode.parse(value);
                    case "hollow" -> options.hollow = parseBoolean(value, "hollow");
                    case "confirm" -> options.confirm = parseBoolean(value, "confirm");
                    case "preview" -> options.preview = parseBoolean(value, "preview");
                    case "r", "radius" -> options.radius = parsePositiveInt(value, "Radius");
                    case "rx", "radiusx" -> options.radiusX = parsePositiveInt(value, "Radius X");
                    case "ry", "radiusy" -> options.radiusY = parsePositiveInt(value, "Radius Y");
                    case "rz", "radiusz" -> options.radiusZ = parsePositiveInt(value, "Radius Z");
                    case "w", "width" -> options.radiusX = parsePositiveInt(value, "Width");
                    case "l", "length" -> options.radiusZ = parsePositiveInt(value, "Length");
                    case "h", "height" -> options.height = parsePositiveInt(value, "Height");
                    case "size" -> options.size = parsePositiveInt(value, "Size");
                    default -> {
                        if (expression.length() > 0) {
                            expression.append(' ');
                        }
                        expression.append(token);
                    }
                }
            } else {
                if (expression.length() > 0) {
                    expression.append(' ');
                }
                expression.append(token);
            }
        }
        return expression.toString().trim();
    }

    private static void parseShapeTail(String[] args, int startIndex, GeneratorOptions options) {
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i];
            if (!looksLikeOption(token)) {
                throw new IllegalArgumentException("Unexpected token: " + token);
            }
            String[] parts = token.split(":", 2);
            String key = parts[0].toLowerCase(Locale.ROOT);
            String value = parts.length > 1 ? parts[1] : "";
            switch (key) {
                case "mode" -> options.coordinateMode = CoordinateMode.parse(value);
                case "hollow" -> options.hollow = parseBoolean(value, "hollow");
                case "confirm" -> options.confirm = parseBoolean(value, "confirm");
                case "preview" -> options.preview = parseBoolean(value, "preview");
                case "r", "radius" -> options.radius = parsePositiveInt(value, "Radius");
                case "rx", "radiusx" -> options.radiusX = parsePositiveInt(value, "Radius X");
                case "ry", "radiusy" -> options.radiusY = parsePositiveInt(value, "Radius Y");
                case "rz", "radiusz" -> options.radiusZ = parsePositiveInt(value, "Radius Z");
                case "w", "width" -> options.radiusX = parsePositiveInt(value, "Width");
                case "l", "length" -> options.radiusZ = parsePositiveInt(value, "Length");
                case "h", "height" -> options.height = parsePositiveInt(value, "Height");
                case "size" -> options.size = parsePositiveInt(value, "Size");
                default -> throw new IllegalArgumentException("Unknown option: " + key);
            }
        }
    }

    private static boolean looksLikeOption(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.startsWith("mode:") || lower.startsWith("hollow:") || lower.startsWith("confirm:")
                || lower.startsWith("preview:") || lower.startsWith("r:") || lower.startsWith("radius:")
                || lower.startsWith("rx:") || lower.startsWith("radiusx:") || lower.startsWith("ry:")
                || lower.startsWith("radiusy:") || lower.startsWith("rz:") || lower.startsWith("radiusz:")
                || lower.startsWith("w:") || lower.startsWith("width:") || lower.startsWith("l:")
                || lower.startsWith("length:") || lower.startsWith("h:") || lower.startsWith("height:")
                || lower.startsWith("size:");
    }

    private static TreeType parseTreeType(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "OAK", "TREE" -> TreeType.TREE;
            case "BIG_OAK", "BIGTREE", "BIG_TREE" -> TreeType.BIG_TREE;
            case "BIRCH" -> TreeType.BIRCH;
            case "REDWOOD", "SPRUCE" -> TreeType.REDWOOD;
            case "TALL_REDWOOD", "TALL_SPRUCE" -> TreeType.TALL_REDWOOD;
            case "JUNGLE" -> TreeType.JUNGLE;
            case "SMALL_JUNGLE", "JUNGLE_BUSH" -> TreeType.SMALL_JUNGLE;
            case "COCOA_TREE", "JUNGLE_TREE" -> TreeType.COCOA_TREE;
            case "ACACIA" -> TreeType.ACACIA;
            case "DARK_OAK" -> TreeType.DARK_OAK;
            case "RED_MUSHROOM" -> TreeType.RED_MUSHROOM;
            case "BROWN_MUSHROOM" -> TreeType.BROWN_MUSHROOM;
            case "MEGA_REDWOOD", "MEGA_SPRUCE" -> TreeType.MEGA_REDWOOD;
            case "CHORUS_PLANT", "CHORUS" -> TreeType.CHORUS_PLANT;
            case "CRIMSON_FUNGUS", "CRIMSON" -> TreeType.CRIMSON_FUNGUS;
            case "WARPED_FUNGUS", "WARPED" -> TreeType.WARPED_FUNGUS;
            case "AZALEA" -> TreeType.AZALEA;
            case "MANGROVE" -> TreeType.MANGROVE;
            case "TALL_MANGROVE" -> TreeType.TALL_MANGROVE;
            case "CHERRY" -> TreeType.CHERRY;
            default -> throw new IllegalArgumentException("Unknown tree type: " + value);
        };
    }

    private static Biome parseBiome(String value) {
        try {
            return Biome.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown biome: " + value);
        }
    }

    private static ShapeAnchorMode parseAnchor(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "player", "feet", "here" -> ShapeAnchorMode.PLAYER;
            case "eyes", "eye" -> ShapeAnchorMode.EYES;
            case "target", "look" -> ShapeAnchorMode.TARGET;
            case "selection", "selection-center", "sel", "center" -> ShapeAnchorMode.SELECTION_CENTER;
            default -> throw new IllegalArgumentException("Unknown anchor: " + value);
        };
    }

    private static ShapeAnchorMode parseAnchorOption(String key, String value) {
        return switch (key) {
            case "at" -> parseAnchor(value);
            case "player", "feet", "here" -> value.isBlank() ? ShapeAnchorMode.PLAYER : null;
            case "eyes", "eye" -> value.isBlank() ? ShapeAnchorMode.EYES : null;
            case "target", "look" -> value.isBlank() ? ShapeAnchorMode.TARGET : null;
            case "selection", "selection-center", "sel", "center" -> value.isBlank() ? ShapeAnchorMode.SELECTION_CENTER : null;
            default -> null;
        };
    }

    private static boolean parseBoolean(String input, String label) {
        return switch (input.toLowerCase(Locale.ROOT)) {
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

    private static double parseDensity(String input) {
        try {
            double density = Double.parseDouble(input);
            if (density < 0.0 || density > 100.0) {
                throw new IllegalArgumentException("Density must be between 0 and 100.");
            }
            return density;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Density must be a number.");
        }
    }

    private static final class GeneratorOptions {
        private CoordinateMode coordinateMode = CoordinateMode.NORMALIZED;
        private boolean hollow;
        private boolean confirm;
        private boolean preview;
        private Integer radius;
        private Integer radiusX;
        private Integer radiusY;
        private Integer radiusZ;
        private Integer height;
        private Integer size;
    }
}
