package com.bridgepractice;

import com.hub.util.Chat;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class BridgePracticeListener implements Listener {

    private final BridgePracticePlugin plugin;
    private final BridgePracticeManager manager;

    public BridgePracticeListener(BridgePracticePlugin plugin, BridgePracticeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!manager.isPracticing(player)) {
            return;
        }
        if (manager.practiceItem(event.getItemInHand()) != null) {
            event.setCancelled(true);
            return;
        }
        manager.notePlacement(player, event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BridgeSession session = manager.session(player);
        if (session != null) {
            if (manager.untrackPlacement(session, event.getBlock())) {
                return;
            }
            if (manager.breakOtherPlayerBlock(session, event.getBlock())) {
                return;
            }
            event.setCancelled(true);
            Chat.send(player, "&c只能拆掉自己这次搭的方块。");
            return;
        }
        if (manager.isTrackedByAnySession(event.getBlock())) {
            event.setCancelled(true);
            Chat.send(player, "&c这是别人练习中的方块，不能拆。");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isPracticing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        BridgePracticeManager.PracticeItem item = manager.practiceItem(event.getItem());
        if (item == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!manager.isPracticing(player)) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                switch (item) {
                    case RESET -> {
                        manager.resetToStart(player);
                        Chat.send(player, "&a已回到出生点，本次搭的方块已清掉。");
                    }
                    case LEAVE -> manager.leave(player);
                    case ROOM -> manager.toggleJoin(player);
                    case ROOM_BREAK -> manager.toggleBreakOthers(player);
                }
            }
            default -> {
                // 忽略左键等其它动作
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && manager.isPracticing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && manager.isPracticing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.quit(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BridgeSession session = manager.session(player);
        if (session == null) {
            return;
        }
        Location spawn = session.course.spawnPoint();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (manager.isPracticing(player)) {
                manager.resetToStart(player);
            }
        });
    }
}
