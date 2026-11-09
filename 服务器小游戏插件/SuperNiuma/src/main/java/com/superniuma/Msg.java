package com.superniuma;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** 统一消息格式。 */
public final class Msg {

    public static final String PREFIX = "&8[&e超级牛马&8] &r";

    private Msg() {
    }

    public static String c(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static void send(CommandSender to, String text) {
        if (to != null) {
            to.sendMessage(c(PREFIX + text));
        }
    }

    public static void raw(CommandSender to, String text) {
        if (to != null) {
            to.sendMessage(c(text));
        }
    }

    /** 动作栏提示（不刷屏）。 */
    public static void actionBar(Player player, String text) {
        if (player == null) {
            return;
        }
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text == null ? "" : text));
    }
}
