package com.lobbytoys;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class ToyListener implements Listener {

    private final LobbyToysPlugin plugin;
    private final CloneManager clones;
    private final ToyItems items;

    public ToyListener(LobbyToysPlugin plugin, CloneManager clones, ToyItems items) {
        this.plugin = plugin;
        this.clones = clones;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ToyType type = items.typeOf(event.getItem());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        switch (type) {
            case CLONE -> clones.toggle(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (items.isToy(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveLater(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.settings().isAllowedWorld(player.getWorld())) {
            giveLater(player);
        } else {
            clones.stop(player.getUniqueId(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clones.stop(event.getPlayer().getUniqueId(), false);
    }

    /** 每 5 秒兜底：主城里的玩家没有玩具就补一个（/hub 清背包后也能自动拿回来）。 */
    public void refillAll() {
        if (!plugin.settings().giveOnJoin) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.settings().isAllowedWorld(player.getWorld())) {
                continue;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            ensureToy(player);
        }
    }

    private void giveLater(Player player) {
        if (!plugin.settings().giveOnJoin) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.settings().isAllowedWorld(player.getWorld())) {
                ensureToy(player);
            }
        }, 30L);
    }

    private void ensureToy(Player player) {
        if (items.hasAny(player)) {
            // 旧版本留下的道具 / 配置改过外观：原地换成当前的
            items.refreshStale(player);
            return;
        }
        ItemStack toy = items.create(player, items.selected(player));
        items.give(player.getInventory(), toy, plugin.settings().itemSlot);
    }
}
