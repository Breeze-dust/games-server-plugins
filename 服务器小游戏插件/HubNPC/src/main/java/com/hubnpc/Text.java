package com.hubnpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Pattern;

/**
 * NPC 名字与消息的颜色解析。
 * <p>
 * 同时支持三种写法：
 * <ul>
 *   <li>传统颜色代码：{@code &6}、{@code &l}、{@code &r}</li>
 *   <li>单色十六进制：{@code &#FFAA00} 或 {#FFAA00}</li>
 *   <li>渐变：{@code <gradient:#FF0000:#00AAFF>文字</gradient>}
 *       或简写 {@code {#FF0000:#00AAFF}文字{/}}</li>
 * </ul>
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.builder().strict(false).build();

    private static final Pattern GRADIENT_BRACE =
            Pattern.compile("\\{#([0-9a-fA-F]{6}):([0-9a-fA-F]{6})}");
    private static final Pattern HEX_BRACE =
            Pattern.compile("\\{#([0-9a-fA-F]{6})}");
    private static final Pattern HEX_AMP =
            Pattern.compile("&#([0-9a-fA-F]{6})");

    private Text() {
    }

    /** 解析带颜色/渐变的文本。 */
    public static Component parse(String raw) {
        return MINI.deserialize(toMini(raw));
    }

    /** 把 & 代码与简写渐变翻译成 MiniMessage 标签。 */
    public static String toMini(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('§', '&');
        s = GRADIENT_BRACE.matcher(s).replaceAll("<gradient:#$1:#$2>");
        s = s.replace("{/}", "</gradient>");
        s = HEX_BRACE.matcher(s).replaceAll("<color:#$1>");
        s = HEX_AMP.matcher(s).replaceAll("<color:#$1>");

        StringBuilder out = new StringBuilder(s.length() + 24);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                String tag = switch (Character.toLowerCase(s.charAt(i + 1))) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> null;
                };
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** 把 MiniMessage/颜色标签去掉，得到纯文本（列表、日志用）。 */
    public static String strip(String raw) {
        return MINI.stripTags(toMini(raw));
    }
}
