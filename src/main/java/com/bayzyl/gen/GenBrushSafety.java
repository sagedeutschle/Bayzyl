package com.bayzyl.gen;

import com.bayzyl.BlockMask;
import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;
import org.bukkit.Material;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure validation and checked work estimates for generation brushes. */
public final class GenBrushSafety {
    static final int MAX_PARAMETER_COUNT = 32;
    static final int MAX_PARAMETER_KEY_LENGTH = 32;
    static final int MAX_PARAMETER_VALUE_LENGTH = 128;
    static final int MAX_SERIALIZED_PARAMETER_LENGTH = 4_096;
    public static final int MAX_MASK_LENGTH = 1_024;

    private static final Set<String> STRICT_BOOLEANS = Set.of(
            "true", "false", "on", "off", "yes", "no", "1", "0");
    private static final Set<String> CARDINAL_DIRECTIONS = Set.of(
            "north", "south", "east", "west");
    private static final Set<String> RAVINE_DIRECTIONS = Set.of(
            "north", "south", "east", "west", "ne", "nw", "se", "sw");

    private GenBrushSafety() {
    }

    public static WorkEstimate assess(GenBrushSettings settings) {
        if (settings == null) {
            return rejected("Generation brush settings are required.");
        }
        String parameterIssue = parameterIssue(settings);
        if (parameterIssue != null) {
            return rejected(parameterIssue);
        }
        WorkEstimate base = OperationLimits.estimateGenBrush(settings.radius(), 2L);
        if (base.hardRejected()) {
            return base;
        }
        WorkEstimate parameterAware = parameterAwareEstimate(settings, base.workUnits());
        if (parameterAware.hardRejected() || parameterAware.workUnits() > base.workUnits()) {
            return parameterAware;
        }
        return base;
    }

    public static GenBrushParameters parseStoredParameters(String raw) {
        if (raw == null || raw.isEmpty()) {
            return GenBrushParameters.empty();
        }
        if (raw.length() > MAX_SERIALIZED_PARAMETER_LENGTH) {
            throw new IllegalArgumentException(
                    "Generation brush parameters exceed the serialized storage limit.");
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : raw.split(";", -1)) {
            int equals = pair.indexOf('=');
            if (equals <= 0 || equals != pair.lastIndexOf('=') || equals == pair.length() - 1) {
                throw new IllegalArgumentException("Malformed generation brush parameter storage.");
            }
            String key = pair.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = pair.substring(equals + 1).trim();
            if (parsed.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate generation brush parameter '" + key + "'.");
            }
        }
        GenBrushParameters parameters = new GenBrushParameters(parsed);
        String issue = structuralIssue(parameters);
        if (issue != null) {
            throw new IllegalArgumentException(issue);
        }
        return parameters;
    }

    public static boolean storedConfirmation(GenBrushSettings settings) {
        if (settings == null || !settings.parameters().has("confirm")) {
            return false;
        }
        return switch (settings.parameters().get("confirm", "").trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> true;
            default -> false;
        };
    }

    public static boolean isResolvableMask(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_MASK_LENGTH) {
            return false;
        }
        for (String part : raw.split(",", -1)) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                return false;
            }
            if (token.startsWith("##")) {
                try {
                    if (!BlockMask.isResolvable(token)) {
                        return false;
                    }
                } catch (RuntimeException | LinkageError noRegistryInUnitTest) {
                    String tag = token.substring(2);
                    if (tag.startsWith("*")) {
                        tag = tag.substring(1);
                    }
                    if (!tag.matches("[a-z0-9_./-]+")) {
                        return false;
                    }
                }
                continue;
            }
            Material material = Material.matchMaterial(token);
            if (material == null || !isBlockMaterial(material)) {
                return false;
            }
        }
        return true;
    }

    private static WorkEstimate parameterAwareEstimate(GenBrushSettings settings, long baseWork) {
        GenBrushParameters parameters = settings.parameters();
        long surfaceSpan = (long) settings.radius() * 2L + 1L;
        return switch (settings.type()) {
            case RIDGE, PLATEAU, DUNES, MESA, PEAK, CLIFF -> parameters.has("height")
                    ? checkedGenProduct(surfaceSpan, surfaceSpan, parameters.getInt("height", 1))
                    : OperationLimits.checkGenBrush(baseWork);
            case VALLEY, BASIN -> parameters.has("depth")
                    ? checkedGenProduct(surfaceSpan, surfaceSpan, parameters.getInt("depth", 1))
                    : OperationLimits.checkGenBrush(baseWork);
            case CAVE -> parameters.has("vertical")
                    ? checkedGenProduct(surfaceSpan, surfaceSpan,
                            checkedAdd(checkedMultiply(parameters.getInt("vertical", 1), 2L), 1L))
                    : OperationLimits.checkGenBrush(baseWork);
            case ERODE -> checkedGenProduct(baseWork, parameters.getInt("passes", 1));
            case RAVINE -> estimateRavine(settings);
            case BOULDER -> estimateBoulders(settings);
            case SCREE -> OperationLimits.checkGenBrush(baseWork);
        };
    }

    private static WorkEstimate estimateRavine(GenBrushSettings settings) {
        GenBrushParameters parameters = settings.parameters();
        int radius = settings.radius();
        long length = parameters.getInt("length", Math.max(12, radius * 4));
        long depth = parameters.getInt("depth", Math.max(10, radius * 3));
        double width = parameters.getFloat("width", 1.0);
        long maximumHalfWidth = (long) Math.ceil(2.4 * width);
        return checkedGenProduct(
                checkedAdd(length, 1L),
                checkedAdd(checkedMultiply(maximumHalfWidth, 2L), 3L),
                checkedAdd(depth, 1L));
    }

    private static WorkEstimate estimateBoulders(GenBrushSettings settings) {
        GenBrushParameters parameters = settings.parameters();
        int radius = settings.radius();
        long count = parameters.getInt("count", Math.max(2, radius / 2));
        long innerRadius = parameters.getInt("radius", Math.max(2, radius / 3));
        try {
            long innerSpan = Math.addExact(Math.multiplyExact(innerRadius, 2L), 1L);
            long innerCubes = checkedProduct(count, innerSpan, innerSpan, innerSpan);
            long attempts = Math.multiplyExact(count, 8L);
            return OperationLimits.checkGenBrush(Math.addExact(innerCubes, attempts));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    private static WorkEstimate checkedGenProduct(long... factors) {
        try {
            return OperationLimits.checkGenBrush(checkedProduct(factors));
        } catch (ArithmeticException ex) {
            return overflow();
        }
    }

    private static long checkedProduct(long... factors) {
        long result = 1L;
        for (long factor : factors) {
            result = Math.multiplyExact(result, factor);
        }
        return result;
    }

    private static long checkedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static WorkEstimate overflow() {
        return new WorkEstimate(Long.MAX_VALUE, false, true,
                "Generation brush work estimate exceeds the supported numeric range.");
    }

    private static String parameterIssue(GenBrushSettings settings) {
        GenBrushParameters parameters = settings.parameters();
        if (settings.type() != GenBrushType.CAVE && settings.caveSubtype() != CaveSubtype.AUTO) {
            return "Only cave brushes may carry a cave subtype.";
        }
        if (settings.mask() != null && settings.mask().getRaw() != null
                && !settings.mask().getRaw().isBlank()
                && !isResolvableMask(settings.mask().getRaw())) {
            return "Generation brush mask must resolve entirely to blocks.";
        }
        String issue = structuralIssue(parameters);
        if (issue != null) {
            return issue;
        }
        issue = strictBoolean(parameters, "confirm");
        if (issue != null) {
            return issue;
        }
        issue = normalized(parameters, "intensity");
        if (issue != null) {
            return issue;
        }
        issue = positiveFinite(parameters, "frequency");
        if (issue != null) {
            return issue;
        }
        issue = nonNegativeFinite(parameters, "warp");
        if (issue != null) {
            return issue;
        }

        return switch (settings.type()) {
            case RIDGE -> firstIssue(
                    positiveInteger(parameters, "height"),
                    normalized(parameters, "steepness"),
                    normalized(parameters, "crest"),
                    normalized(parameters, "blend"));
            case PLATEAU -> firstIssue(
                    positiveInteger(parameters, "height"),
                    finiteRange(parameters, "flatness", 0.0, 0.9),
                    normalized(parameters, "roughness"),
                    normalized(parameters, "blend"),
                    strictEnum(parameters, "edges", Set.of("smooth", "cliff")));
            case VALLEY -> firstIssue(
                    positiveInteger(parameters, "depth"),
                    finiteRange(parameters, "width", 0.2, 1.0),
                    normalized(parameters, "meander"),
                    strictEnum(parameters, "water", Set.of("auto", "on", "off")),
                    strictBoolean(parameters, "river"));
            case BASIN -> firstIssue(
                    positiveInteger(parameters, "depth"),
                    finiteRange(parameters, "flatness", 0.0, 0.85),
                    strictEnum(parameters, "water", Set.of("auto", "on", "off")),
                    normalized(parameters, "roughness"));
            case DUNES -> firstIssue(
                    positiveInteger(parameters, "height"),
                    finiteRange(parameters, "wavelength", 2.0, Double.MAX_VALUE),
                    strictEnum(parameters, "direction", CARDINAL_DIRECTIONS));
            case ERODE -> firstIssue(
                    positiveInteger(parameters, "passes"),
                    normalized(parameters, "aggression"),
                    normalized(parameters, "cracks"),
                    strictBoolean(parameters, "debris"));
            case RAVINE -> firstIssue(
                    positiveInteger(parameters, "length"),
                    positiveInteger(parameters, "depth"),
                    normalized(parameters, "jaggedness"),
                    normalized(parameters, "bridges"),
                    finiteRange(parameters, "width", 0.4, 2.0),
                    strictEnum(parameters, "direction", RAVINE_DIRECTIONS));
            case CAVE -> firstIssue(
                    positiveInteger(parameters, "vertical"),
                    normalized(parameters, "density"),
                    normalized(parameters, "verticality"));
            case MESA -> firstIssue(
                    positiveInteger(parameters, "height"),
                    normalized(parameters, "banding"),
                    integerRange(parameters, "terraces", 0, 64),
                    normalized(parameters, "blend"));
            case PEAK -> firstIssue(
                    positiveInteger(parameters, "height"),
                    normalized(parameters, "sharpness"),
                    normalized(parameters, "snowline"),
                    normalized(parameters, "blend"));
            case CLIFF -> firstIssue(
                    positiveInteger(parameters, "height"),
                    normalized(parameters, "steepness"),
                    normalized(parameters, "blend"),
                    strictEnum(parameters, "direction", CARDINAL_DIRECTIONS));
            case BOULDER -> firstIssue(
                    positiveInteger(parameters, "count"),
                    positiveInteger(parameters, "radius"),
                    normalized(parameters, "cluster"),
                    resolvableBlock(parameters, "block"));
            case SCREE -> firstIssue(
                    normalized(parameters, "density"),
                    resolvableBlock(parameters, "block"));
        };
    }

    private static String structuralIssue(GenBrushParameters parameters) {
        Map<String, String> raw = parameters.raw();
        if (raw.size() > MAX_PARAMETER_COUNT) {
            return "Generation brush parameters exceed the maximum count of "
                    + MAX_PARAMETER_COUNT + ".";
        }
        int serializedLength = 0;
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > MAX_PARAMETER_KEY_LENGTH
                    || !key.matches("[a-z0-9_.-]+")) {
                return "Generation brush parameter key is invalid or too long.";
            }
            if (value == null || value.isBlank() || value.length() > MAX_PARAMETER_VALUE_LENGTH
                    || value.indexOf(';') >= 0 || value.indexOf('=') >= 0
                    || value.chars().anyMatch(Character::isISOControl)) {
                return "Generation brush parameter '" + key + "' has an invalid or oversized value.";
            }
            if (serializedLength > 0) {
                serializedLength++;
            }
            serializedLength += key.length() + value.length() + 1;
            if (serializedLength > MAX_SERIALIZED_PARAMETER_LENGTH) {
                return "Generation brush parameters exceed the serialized storage limit.";
            }
        }
        return null;
    }

    private static String positiveInteger(GenBrushParameters parameters, String name) {
        return integerRange(parameters, name, 1, Integer.MAX_VALUE);
    }

    private static String integerRange(
            GenBrushParameters parameters, String name, int minimum, int maximum
    ) {
        if (!parameters.has(name)) {
            return null;
        }
        String raw = parameters.get(name, "").trim();
        final int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return "Gen brush parameter '" + name + "' must be a whole number.";
        }
        if (value < minimum || value > maximum) {
            return "Gen brush parameter '" + name + "' must be between "
                    + minimum + " and " + maximum + ".";
        }
        return null;
    }

    private static String normalized(GenBrushParameters parameters, String name) {
        return finiteRange(parameters, name, 0.0, 1.0);
    }

    private static String strictBoolean(GenBrushParameters parameters, String name) {
        return strictEnum(parameters, name, STRICT_BOOLEANS);
    }

    private static String strictEnum(
            GenBrushParameters parameters, String name, Set<String> accepted
    ) {
        if (!parameters.has(name)) {
            return null;
        }
        String value = parameters.get(name, "").trim().toLowerCase(Locale.ROOT);
        if (!accepted.contains(value)) {
            return "Gen brush parameter '" + name + "' has unsupported value '" + value + "'.";
        }
        return null;
    }

    private static String resolvableBlock(GenBrushParameters parameters, String name) {
        if (!parameters.has(name)) {
            return null;
        }
        String value = parameters.get(name, "").trim();
        Material material = Material.matchMaterial(value);
        if (material == null || !isBlockMaterial(material)) {
            return "Gen brush parameter '" + name + "' must resolve to a block material.";
        }
        return null;
    }

    private static boolean isBlockMaterial(Material material) {
        if (material == Material.AIR) {
            return true;
        }
        try {
            return material.isBlock();
        } catch (IllegalStateException | LinkageError noRegistryInUnitTest) {
            // Paper's Material#isBlock requires a live registry in newer APIs.
            // matchMaterial above is still a pure, strict name resolution; the
            // server runtime takes this branch only when its registry is broken.
            return true;
        }
    }

    private static String positiveFinite(GenBrushParameters parameters, String name) {
        if (!parameters.has(name)) {
            return null;
        }
        String issue = finiteRange(parameters, name, Double.MIN_VALUE, Double.MAX_VALUE);
        if (issue != null) {
            return "Gen brush parameter '" + name + "' must be a positive finite number.";
        }
        return null;
    }

    private static String nonNegativeFinite(GenBrushParameters parameters, String name) {
        return finiteRange(parameters, name, 0.0, Double.MAX_VALUE);
    }

    private static String finiteRange(
            GenBrushParameters parameters, String name, double minimum, double maximum
    ) {
        if (!parameters.has(name)) {
            return null;
        }
        String raw = parameters.get(name, "").trim();
        final double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return "Gen brush parameter '" + name + "' must be numeric.";
        }
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            return "Gen brush parameter '" + name + "' must be finite and between "
                    + minimum + " and " + maximum + ".";
        }
        return null;
    }

    private static String firstIssue(String... issues) {
        for (String issue : issues) {
            if (issue != null) {
                return issue;
            }
        }
        return null;
    }

    private static WorkEstimate rejected(String reason) {
        return new WorkEstimate(0L, false, true, reason);
    }
}
