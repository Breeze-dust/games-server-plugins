package com.example.playertitles.util;

import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", "&0"),
            Map.entry("dark_blue", "&1"),
            Map.entry("dark_green", "&2"),
            Map.entry("dark_aqua", "&3"),
            Map.entry("dark_red", "&4"),
            Map.entry("dark_purple", "&5"),
            Map.entry("gold", "&6"),
            Map.entry("gray", "&7"),
            Map.entry("grey", "&7"),
            Map.entry("dark_gray", "&8"),
            Map.entry("dark_grey", "&8"),
            Map.entry("blue", "&9"),
            Map.entry("green", "&a"),
            Map.entry("aqua", "&b"),
            Map.entry("red", "&c"),
            Map.entry("light_purple", "&d"),
            Map.entry("pink", "&d"),
            Map.entry("yellow", "&e"),
            Map.entry("white", "&f")
    );

    private static final String COLORS = "0123456789abcdef";

    private Text() {
    }

    public static Component parse(String input) {
        return LEGACY.deserialize(input == null ? "" : input);
    }

    public static String normalizeColor(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("&") || value.startsWith("§")) {
            if (value.length() >= 2 && COLORS.indexOf(Character.toLowerCase(value.charAt(1))) >= 0) {
                return "&" + Character.toLowerCase(value.charAt(1));
            }
            return null;
        }
        if (value.startsWith("#")) {
            return value.matches("#[0-9a-fA-F]{6}") ? value.toUpperCase(Locale.ROOT) : null;
        }
        if (value.matches("[0-9a-fA-F]{6}")) {
            return "#" + value.toUpperCase(Locale.ROOT);
        }
        return NAMED_COLORS.get(value.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }
}
