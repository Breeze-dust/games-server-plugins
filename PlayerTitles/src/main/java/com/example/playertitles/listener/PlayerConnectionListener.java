package com.example.playertitles.listener;

import com.example.playertitles.PlayerTitlesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerConnectionListener implements Listener {

    private final PlayerTitlesPlugin plugin;

    public PlayerConnectionListener(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getTitleManager().applyNametag(event.getPlayer());
        refreshInLobby(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        refreshInLobby(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        refreshInLobby(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTitleManager().removeFromTeams(event.getPlayer());
        plugin.getChatInput().cancel(event.getPlayer());
    }

    /**
     * 回到主城后静默重刷一次头顶头衔。
     * 小游戏可能占用主计分板队伍（烫手山芋/雪塔/单挑/密室/足球），离开游戏时会把头衔队伍顶掉。
     */
    private void refreshInLobby(Player player) {
        String lobby = plugin.getConfig().getString("nametag.lobby-world", "world");
        for (long delay : new long[]{20L, 60L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getWorld().getName().equals(lobby)) {
                    plugin.getTitleManager().applyNametag(player);
                }
            }, delay);
        }
    }
}
