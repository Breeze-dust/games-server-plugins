package com.hub.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * 公共消息工具：所有小游戏插件共用一份实现（原来 6 个插件各抄一份）。
 * <p>
 * 颜色代码统一用 {@code &}；{@code §} 也会被接受（自动换算）。
 * 需要给自己消息加前缀的插件（如建筑猜猜乐），保留一个很薄的本地包装类调用这里。
 */
public final class Chat {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Chat() {
    }

    /** 把 & 或 § 颜色代码转成 Component。 */
    public static Component color(String text) {
        return LEGACY.deserialize(text == null ? "" : text.replace('§', '&'));
    }

    /** 物品名/提示统一用正体：Minecraft 默认把物品显示名渲染成斜体。 */
    public static Component noItalic(Component component) {
        return component == null ? null : component.decoration(TextDecoration.ITALIC, false);
    }

    /** 发消息给玩家/控制台。 */
    public static void send(CommandSender target, String msg) {
        if (target == null) {
            return;
        }
        target.sendMessage(color(msg));
    }

    /** 广播给全服。 */
    public static void broadcast(String msg) {
        Bukkit.broadcast(color(msg));
    }

    /** 发消息给玩家，并给消息里出现的在线玩家名加上头衔（没装头衔插件时与 send 一致）。 */
    public static void sendTitled(Player player, String msg) {
        if (player != null) {
            player.sendMessage(Titles.decorate(color(msg)));
        }
    }

    /** 广播给全服，并给消息里出现的在线玩家名加上头衔（没装头衔插件时与 broadcast 一致）。 */
    public static void broadcastTitled(String msg) {
        Bukkit.broadcast(Titles.decorate(color(msg)));
    }

    /** 屏幕中央大字幕（默认停留 3 秒）。 */
    public static void title(Player player, String title, String subtitle) {
        title(player, title, subtitle, 3);
    }

    /** 屏幕中央大字幕，staySeconds 为停留秒数。 */
    public static void title(Player player, String title, String subtitle, int staySeconds) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(
                color(title),
                color(subtitle),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(staySeconds), Duration.ofMillis(500))));
    }

    /** 物品栏上方的动作栏提示。 */
    public static void actionBar(Player player, String msg) {
        if (player != null) {
            player.sendActionBar(color(msg));
        }
    }

    /** Component 转回 § 代码文本（用于告示牌行）。 */
    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
