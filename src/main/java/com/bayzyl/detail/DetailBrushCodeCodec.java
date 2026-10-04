package com.bayzyl.detail;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DetailBrushCodeCodec {
    private static final int VERSION = 2;
    private static final int MIN_SUPPORTED_VERSION = 1;
    private static final int CODE_TEXT_MAX = 8_192;
    private final DetailBrushSafety safety;

    public DetailBrushCodeCodec(DetailBrushSafety safety) {
        if (safety == null) {
            throw new IllegalArgumentException("Detail brush safety is required.");
        }
        this.safety = safety;
    }

    public String encode(DetailBrushSettings settings) {
        settings = safety.requireValid(settings);
        DetailBrushPresetRegistry registry = safety.registry();
        DetailBrushPreset preset = registry.get(settings.presetId());
        if (preset == null) {
            throw new IllegalArgumentException("Unknown detail brush preset: " + settings.presetId());
        }
        DetailBrushParameters params = settings.parameters().mergeDefaults(preset.parameterSpecs());
        StringBuilder builder = new StringBuilder();
        builder.append(codeLetter(preset.id())).append(VERSION).append(modeLetter(settings.mode())).append('(');
        boolean first = true;
        for (DetailBrushParameterSpec spec : preset.parameterSpecs()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(encodeValue(params.get(spec.name(), spec.defaultValue())));
        }
        builder.append(')');
        return builder.toString();
    }

    public DetailBrushSettings decode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Usage: /db code <code>");
        }
        if (rawCode.length() > CODE_TEXT_MAX) {
            throw new IllegalArgumentException("Detail brush code is too long.");
        }
        String code = rawCode.trim();
        int open = code.indexOf('(');
        int close = code.lastIndexOf(')');
        if (open < 3 || close <= open || close != code.length() - 1) {
            throw new IllegalArgumentException("Invalid detail brush code. Expected format like F1S(...).");
        }

        char presetLetter = Character.toUpperCase(code.charAt(0));
        String versionText = code.substring(1, open - 1);
        char modeLetter = Character.toUpperCase(code.charAt(open - 1));
        int version;
        try {
            version = Integer.parseInt(versionText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid detail brush code version.");
        }
        if (version < MIN_SUPPORTED_VERSION || version > VERSION) {
            throw new IllegalArgumentException("Unsupported detail brush code version: " + version + ".");
        }

        DetailBrushPreset preset = safety.registry().get(presetId(presetLetter));
        if (preset == null) {
            throw new IllegalArgumentException("Unknown detail brush code type: " + presetLetter + ".");
        }
        DetailBrushMode mode = decodeMode(modeLetter);
        List<DetailBrushParameterSpec> specs = preset.parameterSpecs();
        String payload = code.substring(open + 1, close);
        if (payload.length() > DetailBrushSafety.PARAMETER_TEXT_MAX) {
            throw new IllegalArgumentException("Detail brush code payload is too long.");
        }
        int valueCount = payload.isEmpty() ? 0 : 1;
        for (int i = 0; i < payload.length(); i++) {
            if (payload.charAt(i) == ',' && ++valueCount > specs.size()) {
                throw new IllegalArgumentException("Wrong value count for " + preset.id() + " code. Expected "
                        + specs.size() + " values.");
            }
        }
        String[] values = payload.isEmpty() ? new String[0] : payload.split(",", -1);
        // Codes made before a parameter was added are shorter than the current spec list. Cloud's opacity was
        // inserted mid-list, so those codes are matched to the earlier layout by name; every other short code
        // only lacks trailing values, which fall back to the spec defaults.
        List<String> legacyLayout = legacyLayout(preset.id(), values.length, specs);
        if (legacyLayout != null) {
            String[] aligned = new String[specs.size()];
            for (int i = 0; i < specs.size(); i++) {
                int at = legacyLayout.indexOf(specs.get(i).name());
                aligned[i] = at >= 0 ? values[at] : encodeValue(specs.get(i).defaultValue());
            }
            values = aligned;
        } else if (values.length != specs.size()) {
            if (version < VERSION && values.length < specs.size()) {
                String[] padded = new String[specs.size()];
                for (int i = 0; i < specs.size(); i++) {
                    padded[i] = i < values.length ? values[i] : encodeValue(specs.get(i).defaultValue());
                }
                values = padded;
            } else {
                throw new IllegalArgumentException("Wrong value count for " + preset.id() + " code. Expected "
                        + specs.size() + " values.");
            }
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            DetailBrushParameterSpec spec = specs.get(i);
            String value = decodeValue(values[i]);
            params.put(spec.name(), safety.normalizeValue(preset, spec, value));
        }
        return safety.requireValid(new DetailBrushSettings(
                preset.id(), new DetailBrushParameters(params), mode));
    }

    /**
     * Parameter order of share codes minted before newer parameters were added, keyed by preset id. Only the
     * layouts that differ from "current specs minus trailing values" need listing; the lightning color
     * parameter was appended, so its older six-value codes are accepted at any code version.
     */
    private List<String> legacyLayout(String presetId, int valueCount, List<DetailBrushParameterSpec> specs) {
        if (valueCount >= specs.size()) {
            return null;
        }
        List<String> layout = switch (presetId.toLowerCase(Locale.ROOT)) {
            case "cloud" -> List.of("volume", "puffiness", "density", "flatness", "tint");
            case "lightning" -> List.of("length", "jaggedness", "branches", "branch_length", "glow", "direction");
            default -> null;
        };
        return layout != null && layout.size() == valueCount ? layout : null;
    }

    private String encodeValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String decodeValue(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private char codeLetter(String presetId) {
        return switch (presetId.toLowerCase(Locale.ROOT)) {
            case "flame" -> 'F';
            case "cloud" -> 'C';
            case "lightning" -> 'L';
            case "vine" -> 'V';
            case "bark" -> 'B';
            case "hearts" -> 'H';
            case "rainbow" -> 'R';
            default -> throw new IllegalArgumentException("No code letter for detail brush preset: " + presetId);
        };
    }

    private String presetId(char letter) {
        return switch (Character.toUpperCase(letter)) {
            case 'F' -> "flame";
            case 'C' -> "cloud";
            case 'L' -> "lightning";
            case 'V' -> "vine";
            case 'B' -> "bark";
            case 'H' -> "hearts";
            case 'R' -> "rainbow";
            default -> "";
        };
    }

    private char modeLetter(DetailBrushMode mode) {
        return mode == DetailBrushMode.STROKE ? 'T' : 'S';
    }

    private DetailBrushMode decodeMode(char letter) {
        return switch (letter) {
            case 'S' -> DetailBrushMode.STAMP;
            case 'T' -> DetailBrushMode.STROKE;
            default -> throw new IllegalArgumentException("Unknown detail brush mode code: " + letter + ".");
        };
    }
}
