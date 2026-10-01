package com.bayzyl;

import org.bukkit.ChatColor;

import java.util.Locale;

public final class MessageThemeService {
    private static final String DEFAULT_ACCENT = "green";

    private volatile String accent = DEFAULT_ACCENT;

    public MessageThemeService() {
        ChatOutput.setRailPrefixSupplier(this::railPrefix);
    }

    public String accentValue() {
        return accent;
    }

    public void resetAccent() {
        accent = DEFAULT_ACCENT;
    }

    public boolean setAccent(String value) {
        String resolved = normalizeAccent(value);
        if (resolved == null) {
            return false;
        }
        accent = resolved;
        return true;
    }

    public String accentCode() {
        return resolveAccentCode(accent);
    }

    public String railPrefix() {
        return accentCode() + ChatColor.BOLD + "|" + ChatColor.RESET + ChatColor.WHITE;
    }

    public String applyAccent(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String resolved = accentCode();
        return value
                .replace("&a", resolved)
                .replace("&A", resolved)
                .replace(ChatColor.GREEN.toString(), resolved);
    }

    private String normalizeAccent(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ACCENT;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#") && normalized.length() == 7 && isHexColor(normalized.substring(1))) {
            return normalized;
        }

        try {
            ChatColor.valueOf(normalized.toUpperCase(Locale.ROOT));
            return normalized;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveAccentCode(String value) {
        if (value == null || value.isBlank()) {
            return ChatColor.GREEN.toString();
        }

        if (value.startsWith("#")) {
            try {
                return net.md_5.bungee.api.ChatColor.of(value).toString();
            } catch (IllegalArgumentException ex) {
                return ChatColor.GREEN.toString();
            }
        }

        try {
            return ChatColor.valueOf(value.toUpperCase(Locale.ROOT)).toString();
        } catch (IllegalArgumentException ex) {
            return ChatColor.GREEN.toString();
        }
    }

    private boolean isHexColor(String value) {
        if (value.length() != 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            if (!digit && !lower) {
                return false;
            }
        }
        return true;
    }
}
