package com.bayzyl.detail;

import com.bayzyl.safety.OperationLimits;
import com.bayzyl.safety.WorkEstimate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registry-aware schema validation and checked loop envelopes for detail brushes. */
public final class DetailBrushSafety {
    public static final int PRESET_ID_MAX = 64;
    public static final int PARAMETER_TEXT_MAX = 4_096;
    public static final int PARAMETER_COUNT_MAX = 32;
    public static final int PARAMETER_KEY_MAX = 64;
    public static final int PARAMETER_VALUE_MAX = 256;

    private final DetailBrushPresetRegistry registry;

    public DetailBrushSafety(DetailBrushPresetRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Detail brush registry is required.");
        }
        this.registry = registry;
    }

    public DetailBrushPresetRegistry registry() {
        return registry;
    }

    public WorkEstimate assess(DetailBrushSettings settings) {
        String invalidReason = validateSchema(settings);
        if (invalidReason != null) {
            return rejected(invalidReason);
        }
        DetailBrushSettings canonical = canonicalizeValidated(settings);
        DetailBrushPreset preset = registry.get(canonical.presetId());
        DetailBrushParameters parameters = canonical.parameters().mergeDefaults(preset.parameterSpecs());
        try {
            return OperationLimits.checkMaterialized(estimateEnvelope(preset.id(), parameters));
        } catch (ArithmeticException ex) {
            return new WorkEstimate(Long.MAX_VALUE, false, true,
                    "Detail brush work estimate overflowed.");
        }
    }

    public DetailBrushSettings requireValid(DetailBrushSettings settings) {
        WorkEstimate estimate = assess(settings);
        if (estimate.hardRejected()) {
            throw new IllegalArgumentException(estimate.reason());
        }
        return canonicalizeValidated(settings);
    }

    public DetailBrushSettings withParameter(DetailBrushSettings settings, String rawName, String rawValue) {
        settings = requireValid(settings);
        DetailBrushPreset preset = registry.get(settings.presetId());
        DetailBrushParameterSpec spec = findSpec(preset, rawName);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown parameter " + rawName + " for preset " + preset.id() + ".");
        }
        String normalized = normalizeValue(preset, spec, rawValue);
        DetailBrushSettings updated = settings.withParameters(
                settings.parameters().with(spec.name(), normalized));
        return requireValid(updated);
    }

    public String normalizeValue(DetailBrushPreset preset, DetailBrushParameterSpec spec, String rawValue) {
        if (preset == null || spec == null) {
            throw new IllegalArgumentException("Detail brush parameter specification is required.");
        }
        if (rawValue == null || rawValue.length() > PARAMETER_VALUE_MAX
                || hasUnsafeStoredCharacter(rawValue)) {
            throw new IllegalArgumentException(spec.name() + " has an invalid value length.");
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(spec.name() + " has an invalid value length.");
        }
        return switch (spec.type()) {
            case FLOAT -> normalizeFloat(spec, value);
            case INT -> normalizeInteger(spec, value);
            case STRING -> normalizeString(preset, spec, value);
        };
    }

    public DetailBrushParameters parseStoredParameters(String raw) {
        if (raw == null || raw.isEmpty()) {
            return DetailBrushParameters.empty();
        }
        if (raw.length() > PARAMETER_TEXT_MAX) {
            throw new IllegalArgumentException("Stored detail brush parameters are too long.");
        }
        int entryCount = 1;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ';' && ++entryCount > PARAMETER_COUNT_MAX) {
                throw new IllegalArgumentException("Stored detail brush has too many parameters.");
            }
        }
        String[] pairs = raw.split(";", -1);
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : pairs) {
            if (pair.isBlank()) {
                throw new IllegalArgumentException("Stored detail brush contains an empty parameter.");
            }
            int separator = pair.indexOf('=');
            if (separator <= 0 || separator != pair.lastIndexOf('=') || separator == pair.length() - 1) {
                throw new IllegalArgumentException("Stored detail brush contains a malformed parameter.");
            }
            String rawKey = pair.substring(0, separator);
            String rawValue = pair.substring(separator + 1);
            if (hasUnsafeStoredCharacter(rawKey) || hasUnsafeStoredCharacter(rawValue)) {
                throw new IllegalArgumentException("Stored detail brush parameter exceeds its bounds.");
            }
            String key = rawKey.trim().toLowerCase(Locale.ROOT);
            String value = rawValue.trim();
            if (key.isEmpty() || key.length() > PARAMETER_KEY_MAX
                    || value.isEmpty() || value.length() > PARAMETER_VALUE_MAX
                    || hasUnsafeStoredCharacter(key) || hasUnsafeStoredCharacter(value)) {
                throw new IllegalArgumentException("Stored detail brush parameter exceeds its bounds.");
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Stored detail brush contains duplicate parameter " + key + ".");
            }
        }
        return new DetailBrushParameters(values);
    }

    private String validateSchema(DetailBrushSettings settings) {
        if (settings == null || settings.presetId() == null
                || settings.presetId().isBlank() || settings.presetId().length() > PRESET_ID_MAX) {
            return "Detail brush preset id is invalid.";
        }
        if (settings.mode() == null) {
            return "Detail brush mode is invalid.";
        }
        DetailBrushPreset preset = registry.get(settings.presetId());
        if (preset == null) {
            return "Unknown detail brush preset: " + settings.presetId();
        }
        if (settings.parameters() == null) {
            return "Detail brush parameters are missing.";
        }
        Map<String, String> raw = settings.parameters().raw();
        if (raw.size() > PARAMETER_COUNT_MAX) {
            return "Detail brush has too many parameters.";
        }
        Map<String, DetailBrushParameterSpec> specs = new LinkedHashMap<>();
        for (DetailBrushParameterSpec spec : preset.parameterSpecs()) {
            if (spec == null || spec.name() == null || spec.name().isBlank()) {
                return "Detail brush preset schema is invalid.";
            }
            specs.put(spec.name().toLowerCase(Locale.ROOT), spec);
        }
        long serializedLength = 0L;
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > PARAMETER_KEY_MAX
                    || value == null || value.length() > PARAMETER_VALUE_MAX
                    || hasUnsafeStoredCharacter(key) || hasUnsafeStoredCharacter(value)) {
                return "Detail brush parameter exceeds its bounds.";
            }
            serializedLength += key.length() + 1L + value.length() + (serializedLength == 0L ? 0L : 1L);
            if (serializedLength > PARAMETER_TEXT_MAX) {
                return "Detail brush parameters are too long.";
            }
        }
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            DetailBrushParameterSpec spec = specs.get(key.toLowerCase(Locale.ROOT));
            if (spec == null) {
                return "Unknown detail brush parameter: " + key;
            }
            try {
                normalizeValue(preset, spec, value);
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
        }
        for (DetailBrushParameterSpec spec : preset.parameterSpecs()) {
            try {
                normalizeValue(preset, spec, spec.defaultValue());
            } catch (IllegalArgumentException ex) {
                return "Detail brush preset default is invalid: " + spec.name();
            }
        }
        return null;
    }

    private String normalizeFloat(DetailBrushParameterSpec spec, String value) {
        final double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(spec.name() + " must be a number.");
        }
        if (!Double.isFinite(parsed) || parsed < spec.min() || parsed > spec.max()) {
            throw new IllegalArgumentException(spec.name() + " must be finite and between "
                    + spec.min() + " and " + spec.max() + ".");
        }
        return Double.toString(parsed);
    }

    private String normalizeInteger(DetailBrushParameterSpec spec, String value) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(spec.name() + " must be an integer.");
        }
        if (parsed < spec.min() || parsed > spec.max()) {
            throw new IllegalArgumentException(spec.name() + " must be between "
                    + (int) spec.min() + " and " + (int) spec.max() + ".");
        }
        return Integer.toString(parsed);
    }

    private String normalizeString(DetailBrushPreset preset, DetailBrushParameterSpec spec, String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        List<String> allowed = allowedValues(preset.id(), spec.name());
        if (allowed.isEmpty() || !allowed.contains(normalized)) {
            throw new IllegalArgumentException(spec.name() + " has an unknown value: " + value + ".");
        }
        return normalized;
    }

    public List<String> allowedValues(String presetId, String parameter) {
        if (presetId == null || parameter == null) {
            return List.of();
        }
        String key = presetId.toLowerCase(Locale.ROOT) + ":" + parameter.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "cloud:tint" -> List.of("white", "gray", "sunset", "storm");
            case "lightning:direction" -> List.of("down", "up", "north", "south", "east", "west");
            case "lightning:color" -> List.of("blue", "white", "purple", "yellow", "red");
            case "vine:species" -> List.of(
                    "oak", "birch", "spruce", "jungle", "dark_oak", "darkoak", "azalea",
                    "flowering_azalea", "flowering", "mangrove", "mangrove_root", "mangrove_roots",
                    "root", "roots", "cherry");
            case "bark:species" -> List.of(
                    "oak", "birch", "spruce", "jungle", "dark_oak", "darkoak", "acacia",
                    "mangrove", "cherry", "crimson", "warped");
            case "hearts:tone" -> List.of("pink", "red", "magenta", "mixed");
            case "hearts:rotation" -> List.of("face", "up", "down", "north", "south", "east", "west");
            case "rainbow:palette" -> List.of("classic", "pastel", "sunset");
            case "rainbow:heading" -> List.of("auto", "north", "south", "east", "west");
            default -> List.of();
        };
    }

    private DetailBrushParameterSpec findSpec(DetailBrushPreset preset, String rawName) {
        if (preset == null || rawName == null) {
            return null;
        }
        for (DetailBrushParameterSpec spec : preset.parameterSpecs()) {
            if (spec.name().equalsIgnoreCase(rawName)) {
                return spec;
            }
        }
        return null;
    }

    private DetailBrushSettings canonicalizeValidated(DetailBrushSettings settings) {
        DetailBrushPreset preset = registry.get(settings.presetId());
        Map<String, String> canonical = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : settings.parameters().raw().entrySet()) {
            DetailBrushParameterSpec spec = findSpec(preset, entry.getKey());
            canonical.put(spec.name().toLowerCase(Locale.ROOT),
                    normalizeValue(preset, spec, entry.getValue()));
        }
        return new DetailBrushSettings(
                preset.id(), new DetailBrushParameters(canonical), settings.mode());
    }

    private boolean hasUnsafeStoredCharacter(String value) {
        if (value.indexOf(';') >= 0 || value.indexOf('=') >= 0) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private long estimateEnvelope(String presetId, DetailBrushParameters parameters) {
        return switch (presetId.toLowerCase(Locale.ROOT)) {
            case "cloud" -> estimateCloud(parameters);
            case "flame" -> estimateFlame(parameters);
            case "bark" -> estimateBark(parameters);
            case "lightning" -> estimateLightning(parameters);
            case "vine" -> estimateVine(parameters);
            case "hearts" -> estimateHearts(parameters);
            case "rainbow" -> estimateRainbow(parameters);
            default -> throw new ArithmeticException("Unknown detail brush envelope.");
        };
    }

    private long estimateCloud(DetailBrushParameters p) {
        long volume = p.getInt("volume", 6);
        double flatness = p.getFloat("flatness", 0.35);
        long horizontalSpan = Math.addExact(Math.multiplyExact(2L, volume), 3L);
        long verticalReach = (long) Math.ceil(volume / (1.0 + flatness * 1.5));
        long verticalSpan = Math.addExact(Math.multiplyExact(2L, verticalReach), 3L);
        return product(horizontalSpan, horizontalSpan, verticalSpan);
    }

    private long estimateFlame(DetailBrushParameters p) {
        long heightSpan = Math.addExact((long) p.getInt("height", 8), 1L);
        long width = p.getInt("width", 3);
        long leanX = (long) Math.ceil(Math.abs(p.getFloat("lean_x", 0.0) * width));
        long leanZ = (long) Math.ceil(Math.abs(p.getFloat("lean_z", 0.0) * width));
        long spanX = Math.addExact(Math.multiplyExact(2L, Math.addExact(width, leanX)), 3L);
        long spanZ = Math.addExact(Math.multiplyExact(2L, Math.addExact(width, leanZ)), 3L);
        return product(heightSpan, spanX, spanZ);
    }

    private long estimateBark(DetailBrushParameters p) {
        long horizontal = Math.addExact(Math.multiplyExact(2L, p.getInt("radius", 3)), 1L);
        long vertical = Math.addExact(Math.multiplyExact(2L, p.getInt("height", 4)), 1L);
        return product(horizontal, horizontal, vertical);
    }

    private long estimateLightning(DetailBrushParameters p) {
        long length = p.getInt("length", 14);
        long branches = p.getInt("branches", 2);
        long branchLength = Math.max(2L, Math.round(length * p.getFloat("branch_length", 0.45)));
        return Math.multiplyExact(2L,
                Math.addExact(length, Math.multiplyExact(branches, branchLength)));
    }

    private long estimateVine(DetailBrushParameters p) {
        long length = p.getInt("length", 6);
        String species = p.get("species", "oak").toLowerCase(Locale.ROOT);
        boolean root = Set.of("mangrove_root", "mangrove_roots", "root", "roots").contains(species);
        if (!root) {
            return Math.multiplyExact(3L, length);
        }
        return Math.addExact(length,
                Math.multiplyExact(10L, Math.max(0L, length - 2L)));
    }

    private long estimateHearts(DetailBrushParameters p) {
        int size = p.getInt("size", 4);
        long count = p.getInt("count", 1);
        int renderedSize = count == 1L ? size : Math.min(12, size + 1);
        long planeSpan = Math.addExact(Math.multiplyExact(2L, renderedSize), 1L);
        int depth = Math.max(1, renderedSize / 3);
        long depthSpan = (long) ((depth + 1) / 2) - (-depth / 2) + 1L;
        long heartLoops = Math.multiplyExact(count, product(planeSpan, planeSpan, depthSpan));
        long sparkles = Math.round(p.getFloat("sparkle_density", 0.4) * 8.0 * count);
        return Math.addExact(count, Math.addExact(heartLoops, sparkles));
    }

    private long estimateRainbow(DetailBrushParameters p) {
        long length = p.getInt("length", 24);
        long bands = p.getInt("bands", 7);
        long steps = Math.max(8L, Math.multiplyExact(2L, length));
        long arc = Math.multiplyExact(2L, Math.multiplyExact(steps, bands));
        long radius = p.getInt("cloud_size", 4);
        if (radius == 0L) {
            return arc;
        }
        long horizontalSpan = Math.addExact(Math.multiplyExact(2L, radius), 3L);
        long verticalSpan = Math.addExact(Math.multiplyExact(2L, radius / 2L), 2L);
        long clouds = Math.multiplyExact(2L, product(horizontalSpan, horizontalSpan, verticalSpan));
        return Math.addExact(arc, clouds);
    }

    private long product(long... values) {
        long result = 1L;
        for (long value : values) {
            result = Math.multiplyExact(result, value);
        }
        return result;
    }

    private WorkEstimate rejected(String reason) {
        return new WorkEstimate(0L, false, true, reason);
    }
}
