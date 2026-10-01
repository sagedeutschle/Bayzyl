package com.bayzyl.gen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Typed accessor over the loose {@code option:value} pairs a player can
 * stack on a gen brush invocation. Mirrors the shape of
 * {@code DetailBrushParameters} so the rest of the system can treat the
 * two interchangeably when persisting or serializing.
 */
public final class GenBrushParameters {
    private final Map<String, String> values;

    public GenBrushParameters(Map<String, String> values) {
        this.values = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                this.values.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
    }

    public static GenBrushParameters empty() {
        return new GenBrushParameters(Collections.emptyMap());
    }

    public Map<String, String> raw() {
        return Collections.unmodifiableMap(values);
    }

    public boolean has(String name) {
        return values.containsKey(name.toLowerCase(Locale.ROOT));
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

    public long getLong(String name, long fallback) {
        String v = values.get(name.toLowerCase(Locale.ROOT));
        if (v == null) {
            return fallback;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean getBool(String name, boolean fallback) {
        String v = values.get(name.toLowerCase(Locale.ROOT));
        if (v == null) {
            return fallback;
        }
        String t = v.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> fallback;
        };
    }

    public GenBrushParameters with(String name, String value) {
        Map<String, String> next = new LinkedHashMap<>(values);
        if (value == null) {
            next.remove(name.toLowerCase(Locale.ROOT));
        } else {
            next.put(name.toLowerCase(Locale.ROOT), value);
        }
        return new GenBrushParameters(next);
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
            builder.append(entry.getKey()).append('=')
                    .append(entry.getValue().replace(';', ',').replace('=', '_'));
        }
        return builder.toString();
    }

    public static GenBrushParameters parse(String raw) {
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
            map.put(pair.substring(0, idx).trim().toLowerCase(Locale.ROOT),
                    pair.substring(idx + 1).trim());
        }
        return new GenBrushParameters(map);
    }
}
