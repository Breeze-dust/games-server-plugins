package com.example.joinquit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class JoinQuitBroadcastPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    private boolean joinEnabled;
    private boolean quitEnabled;
    private String joinFormat;
    private String quitFormat;
    private Object titleHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        if (getServer().getPluginManager().getPlugin("PlayerTitles") != null) {
            try {
                titleHook = new TitleHook();
            } catch (LinkageError | RuntimeException exception) {
                getLogger().warning("无法连接到 PlayerTitles 头衔插件: " + exception.getMessage());
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        joinEnabled = getConfig().getBoolean("join.enabled", true);
        quitEnabled = getConfig().getBoolean("quit.enabled", true);
        joinFormat = getConfig().getString("join.message", "&a[+] &f%player% 加入了服务器 &8| &7在线: &f%online%");
        quitFormat = getConfig().getString("quit.message", "&c[-] &f%player% 离开了服务器 &8| &7在线: &f%online%");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!joinEnabled) {
            return;
        }
        event.joinMessage(render(joinFormat, event.getPlayer(), Bukkit.getOnlinePlayers().size()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (!quitEnabled) {
            return;
        }
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
        event.quitMessage(render(quitFormat, event.getPlayer(), online));
    }

    private Component render(String format, Player player, int online) {
        String effective = format;
        if (!effective.contains("%title%")) {
            int playerIndex = effective.indexOf("%player%");
            effective = playerIndex >= 0
                    ? effective.substring(0, playerIndex) + "%title%" + effective.substring(playerIndex)
                    : "%title% " + effective;
        }

        String text = effective
                .replace("%player%", player.getName())
                .replace("%online%", Integer.toString(online));

        Component title = null;
        if (titleHook != null) {
            title = ((TitleHook) titleHook).titleFor(player);
        }

        int index = text.indexOf("%title%");
        if (index < 0) {
            return SERIALIZER.deserialize(text);
        }

        String before = text.substring(0, index);
        String after = text.substring(index + "%title%".length());
        Component result = SERIALIZER.deserialize(before);
        if (title != null) {
            result = result.append(title);
            if (!after.isEmpty() && !after.startsWith(" ")) {
                result = result.append(Component.text(" "));
            }
        }
        return result.append(SERIALIZER.deserialize(after));
    }
}
