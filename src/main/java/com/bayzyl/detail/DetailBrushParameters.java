package com.bayzyl.detail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DetailBrushParameters {
    private final Map<String, String> values;

    public DetailBrushParameters(Map<String, String> values) {
        this.values = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                this.values.put(entry.getKey().toLowerCase(Locale.ROOT), trimBoundarySpaces(entry.getValue()));
            }
        }
    }

    private static String trimBoundarySpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }

    public static DetailBrushParameters empty() {
        return new DetailBrushParameters(Collections.emptyMap());
    }

    public Map<String, String> raw() {
        return Collections.unmodifiableMap(values);
    }

    public String get(String name, String fallback) {
        String v = values.get(name.toLowerCase(Locale.ROOT));
        return v == null ? fallback : v;
    }

    public double getFloat(String name, double fallback) {
        String v = values.get(name.toLowerCase(Locale.ROOT));
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public int getInt(String name, int fallback) {
        String v = values.get(name.toLowerCase(Locale.ROOT));
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            try {
                return (int) Math.round(Double.parseDouble(v.trim()));
            } catch (NumberFormatException nested) {
                return fallback;
            }
        }
    }

    public DetailBrushParameters with(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(values);
        if (value == null) {
            next.remove(name.toLowerCase(Locale.ROOT));
        } else {
            next.put(name.toLowerCase(Locale.ROOT), value);
        }
        return new DetailBrushParameters(next);
    }

    public DetailBrushParameters mergeDefaults(java.util.List<DetailBrushParameterSpec> specs) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (DetailBrushParameterSpec spec : specs) {
            merged.put(spec.name().toLowerCase(Locale.ROOT), spec.defaultValue());
        }
        merged.putAll(values);
        return new DetailBrushParameters(merged);
    }

    public String serialize() {
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue().replace(';', ',').replace('=', '_'));
        }
        return builder.toString();
    }

    public static DetailBrushParameters parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : raw.split(";")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx <= 0 || idx == pair.length() - 1) {
                continue;
            }
            map.put(pair.substring(0, idx).trim().toLowerCase(Locale.ROOT), pair.substring(idx + 1).trim());
        }
        return new DetailBrushParameters(map);
    }
}
