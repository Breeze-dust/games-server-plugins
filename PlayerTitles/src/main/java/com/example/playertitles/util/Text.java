package com.example.playertitles.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

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

    /** 渲染称号名称：优先识别 {起色>…>止色} 渐变语法，否则按颜色 + 文本解析。 */
    public static Component titleName(String color, String raw) {
        Gradient gradient = parseGradient(raw);
        if (gradient != null) {
            return gradient.render();
        }
        return parse((color == null ? "" : color) + (raw == null ? "" : raw));
    }

    /** 称号名称的纯文本（去掉渐变语法和颜色代码），用于预填和“名称没变”判断。 */
    public static String titlePlain(String raw) {
        Gradient gradient = parseGradient(raw);
        if (gradient != null) {
            return gradient.text().trim();
        }
        return PlainTextComponentSerializer.plainText().serialize(parse(raw)).trim();
    }

    public static boolean looksLikeGradient(String raw) {
        return raw != null && raw.trim().startsWith("{");
    }

    /** 解析 {RRGGBB>RRGGBB[>…]}文字；不是合法渐变时返回 null。 */
    public static Gradient parseGradient(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (!value.startsWith("{")) {
            return null;
        }
        int close = value.indexOf('}');
        if (close <= 1 || close == value.length() - 1) {
            return null;
        }
        String[] parts = value.substring(1, close).split(">");
        if (parts.length < 2) {
            return null;
        }
        List<Integer> colors = new ArrayList<>();
        for (String part : parts) {
            int rgb = parseHex(part.trim());
            if (rgb < 0) {
                return null;
            }
            colors.add(rgb);
        }
        return new Gradient(value.substring(close + 1), List.copyOf(colors));
    }

    private static int parseHex(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        return hex.matches("[0-9a-fA-F]{6}") ? Integer.parseInt(hex, 16) : -1;
    }

    /** 已解析的渐变：文字 + 一组颜色，按字符逐个插值。 */
    public record Gradient(String text, List<Integer> colors) {

        public Component render() {
            int[] codePoints = text.codePoints().toArray();
            if (codePoints.length == 0) {
                return Component.empty();
            }
            Component result = Component.empty();
            for (int i = 0; i < codePoints.length; i++) {
                double t = codePoints.length == 1 ? 0.0 : (double) i / (codePoints.length - 1);
                double scaled = t * (colors.size() - 1);
                int index = Math.min((int) Math.floor(scaled), colors.size() - 2);
                int rgb = lerp(colors.get(index), colors.get(index + 1), scaled - index);
                result = result.append(Component.text(
                        new String(Character.toChars(codePoints[i])),
                        TextColor.color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)));
            }
            return result;
        }

        private static int lerp(int from, int to, double t) {
            int fromRed = (from >> 16) & 0xFF;
            int fromGreen = (from >> 8) & 0xFF;
            int fromBlue = from & 0xFF;
            int toRed = (to >> 16) & 0xFF;
            int toGreen = (to >> 8) & 0xFF;
            int toBlue = to & 0xFF;
            int red = (int) Math.round(fromRed + (toRed - fromRed) * t);
            int green = (int) Math.round(fromGreen + (toGreen - fromGreen) * t);
            int blue = (int) Math.round(fromBlue + (toBlue - fromBlue) * t);
            return (red << 16) | (green << 8) | blue;
        }
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
