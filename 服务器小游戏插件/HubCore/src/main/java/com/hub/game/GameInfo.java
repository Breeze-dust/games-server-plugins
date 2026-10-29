package com.hub.game;

/**
 * 注册表中的一个游戏。
 *
 * @param id          唯一标识（配置游戏 = 显示名；自动发现的插件 = 插件名）
 * @param displayName tab 分区 / 菜单显示的正式名称
 * @param icon        菜单图标物品名（可选，默认 GRASS_BLOCK）
 * @param color       tab 分区标题颜色代码（0-9 a-f；0 表示未指定）
 */
public record GameInfo(String id, String displayName, String icon, char color) {

    public GameInfo {
        if (icon == null || icon.isEmpty()) {
            icon = "GRASS_BLOCK";
        }
        if (color == 0 || !isHexColor(color)) {
            color = 0;
        }
    }

    public boolean hasColor() {
        return color != 0;
    }

    private static boolean isHexColor(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
