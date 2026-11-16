package com.hub;

import java.util.List;

/**
 * tab 头部名单的四列排版：每行最多 4 个名字，按 Minecraft 默认字体的像素宽度补空格对齐。
 * 名字是 ASCII 玩家名，宽度表足够准；换行后的续行前面补上与行前缀等宽的缩进。
 */
final class TabColumns {

    /** 列间距（像素）。 */
    private static final int GAP = 8;
    private static final int MIN_CELL = 40;
    private static final int MAX_CELL = 96;
    private static final int MAX_COLUMNS = 4;

    /** Minecraft 默认字体每个 ASCII 字符的宽度（含 1px 字间距）。 */
    private static final int[] ASCII_WIDTH = new int[128];

    static {
        for (int i = 0; i < ASCII_WIDTH.length; i++) {
            ASCII_WIDTH[i] = 6;
        }
        int[] narrow = {'!', ':', ';', ',', '.', 'i', '|', '\'', '`'};
        for (int c : narrow) {
            ASCII_WIDTH[c] = 2;
        }
        ASCII_WIDTH[' '] = 4;
        ASCII_WIDTH['"'] = 5;
        ASCII_WIDTH['('] = 5;
        ASCII_WIDTH[')'] = 5;
        ASCII_WIDTH['*'] = 5;
        ASCII_WIDTH['<'] = 5;
        ASCII_WIDTH['>'] = 5;
        ASCII_WIDTH['['] = 4;
        ASCII_WIDTH[']'] = 4;
        ASCII_WIDTH['{'] = 5;
        ASCII_WIDTH['}'] = 5;
        ASCII_WIDTH['I'] = 4;
        ASCII_WIDTH['l'] = 3;
        ASCII_WIDTH['t'] = 4;
        ASCII_WIDTH['f'] = 5;
        ASCII_WIDTH['k'] = 5;
        ASCII_WIDTH['@'] = 7;
        ASCII_WIDTH['~'] = 7;
    }

    private TabColumns() {
    }

    /**
     * 把名字排成四列文本。
     *
     * @param names  带 {@code &} 颜色代码的名字
     * @param prefix 第一行行首的前缀（如 {@code &e[等待中] }），续行按它的宽度缩进
     */
    static String layout(List<String> names, String prefix) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        int widest = 0;
        for (String name : names) {
            widest = Math.max(widest, width(name));
        }
        int cell = clamp(roundUp4(widest + GAP), MIN_CELL, MAX_CELL);
        String indent = spaces((width(prefix) + 3) / 4);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i % MAX_COLUMNS == 0) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(i == 0 ? prefix : indent);
            }
            String name = names.get(i);
            out.append(name);
            boolean rowEnd = i % MAX_COLUMNS == MAX_COLUMNS - 1 || i == names.size() - 1;
            if (!rowEnd) {
                out.append(spaces(Math.max(1, (cell - width(name) + 3) / 4)));
            }
        }
        return out.toString();
    }

    /** 文本显示宽度（像素），跳过 {@code &}/{@code §} 颜色代码。 */
    static int width(String legacy) {
        int total = 0;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < legacy.length()) {
                i++;
                continue;
            }
            if (c < 128) {
                total += ASCII_WIDTH[c];
            } else {
                total += 9;
            }
        }
        return total;
    }

    /**
     * 把整块头部的每一行右补空格到同一宽度：客户端逐行居中，
     * 只有等宽的行叠在一起才能让四列真正对齐。
     */
    static String justify(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, width(line));
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            String line = lines.get(i);
            out.append(line).append(spaces((widest - width(line) + 3) / 4));
        }
        return out.toString();
    }

    private static String spaces(int count) {
        return count <= 0 ? "" : " ".repeat(count);
    }

    private static int roundUp4(int value) {
        return (value + 3) / 4 * 4;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
