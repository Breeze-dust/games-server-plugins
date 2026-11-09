package com.superniuma;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

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
}
