package com.bayzyl;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum TabMenuModule {
    RAM("ram", false),
    CLIPBOARD("clipboard", false),
    SELECTION("selection", false),
    TRAIL("trail", false);

    private final String key;
    private final boolean defaultEnabled;

    TabMenuModule(String key, boolean defaultEnabled) {
        this.key = key;
        this.defaultEnabled = defaultEnabled;
    }

    public String key() {
        return key;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public static TabMenuModule fromKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(module -> module.key.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public static List<String> keys() {
        return Arrays.stream(values())
                .map(TabMenuModule::key)
                .toList();
    }
}
