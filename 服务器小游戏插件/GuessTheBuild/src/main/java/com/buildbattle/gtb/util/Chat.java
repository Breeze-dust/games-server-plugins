package com.buildbattle.gtb.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * 消息与标题工具：本插件是唯一带前缀的小游戏，所以保留这个很薄的本地包装类。
 * <p>
 * 颜色解析、消息发送、大字幕等实现全部委托给公共库 {@link com.hub.util.Chat}（MiniGameLib），
 * 这里只负责拼接 &6[建筑猜猜乐] 前缀并保持插件原有的方法名不变
 * （{@code send}/{@code broadcast} 带前缀，{@code sendRaw}/{@code broadcastRaw} 不带前缀）。
 */
public final class Chat {

    /** 前缀（& 颜色代码形式；序列化交给公共库）。 */
    private static final String PREFIX_TEXT = "&6[建筑猜猜乐] &r";

    /** 前缀 Component（保持旧接口：部分代码直接使用 Chat.PREFIX）。 */
    public static final Component PREFIX = com.hub.util.Chat.color(PREFIX_TEXT);

    private Chat() {
    }

    /** 将 & 或 § 颜色代码转换为 Adventure Component（委托公共库）。 */
    public static Component color(String text) {
        return com.hub.util.Chat.color(text);
    }

    /** 物品名统一正体（委托公共库）。 */
    public static Component noItalic(Component component) {
        return com.hub.util.Chat.noItalic(component);
    }

    /** 带插件前缀发消息给玩家。 */
    public static void send(Player player, String msg) {
        com.hub.util.Chat.send(player, PREFIX_TEXT + msg);
    }

    /** 不带前缀发消息给玩家。 */
    public static void sendRaw(Player player, String msg) {
        com.hub.util.Chat.send(player, msg);
    }

    /** 带插件前缀广播给全服（玩家名自动带头衔）。 */
    public static void broadcast(String msg) {
        com.hub.util.Chat.broadcastTitled(PREFIX_TEXT + msg);
    }

    /** 不带前缀广播给全服（玩家名自动带头衔）。 */
    public static void broadcastRaw(String msg) {
        com.hub.util.Chat.broadcastTitled(msg);
    }

    /** 屏幕中央大字幕（默认停留 3 秒）。 */
    public static void title(Player player, String title, String subtitle) {
        com.hub.util.Chat.title(player, title, subtitle);
    }

    /** 大字幕（屏幕中央），staySeconds 为停留秒数。 */
    public static void title(Player player, String title, String subtitle, int staySeconds) {
        com.hub.util.Chat.title(player, title, subtitle, staySeconds);
    }
}
