package com.superniuma.listener;

import com.superniuma.SuperNiumaPlugin;
import com.superniuma.game.GameRoom;
import com.superniuma.mechanism.PlacementSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 对局内的玩家交互与机关运行事件。 */
public final class GameListener implements Listener {

    private final SuperNiumaPlugin plugin;

    public GameListener(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameRoom room = plugin.rooms().roomOf(player);
        if (room == null) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (room.phase() == GameRoom.Phase.WAITING) {
            handleWaitItem(event, player, room);
            return;
        }
        if (room.phase() != GameRoom.Phase.PLACING) {
            return;
        }
        PlacementSession session = room.session(player);
        if (session == null || session.confirmed()) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            room.rotateLeft(player);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            room.rotateRight(player);
        }
    }

    /** 等待阶段：右键「开始游戏」/「退出房间」。 */
    private void handleWaitItem(PlayerInteractEvent event, Player player, GameRoom room) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (room.isStartItem(item)) {
            event.setCancelled(true);
            room.startGame(player);
        } else if (room.isExitItem(item)) {
            event.setCancelled(true);
            plugin.rooms().leave(player, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        GameRoom room = plugin.rooms().roomOf(event.getPlayer());
        if (room != null && room.phase() == GameRoom.Phase.PLACING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        GameRoom room = plugin.rooms().roomOf(player);
        if (room == null || room.phase() != GameRoom.Phase.PLACING) {
            return;
        }
        PlacementSession session = room.session(player);
        if (session == null || session.confirmed()) {
            return;
        }
        int delta = event.getPreviousSlot() - event.getNewSlot();
        if (delta == 0) {
            return;
        }
        // 取消槽位变化，把滚轮当成"向前 / 向后"一步。
        event.setCancelled(true);
        room.scroll(player, delta > 0);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameRoom room = plugin.rooms().roomOf(player);
        if (room == null) {
            return;
        }
        if (room.phase() == GameRoom.Phase.WAITING) {
            ItemStack item = event.getItemDrop().getItemStack();
            if (room.isStartItem(item) || room.isExitItem(item)) {
                event.setCancelled(true);
            }
            return;
        }
        if (room.phase() != GameRoom.Phase.PLACING) {
            return;
        }
        event.setCancelled(true);
        room.handleDrop(player);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        GameRoom room = plugin.rooms().roomOf(event.getPlayer());
        if (room == null || room.phase() != GameRoom.Phase.RUNNING) {
            return;
        }
        room.handleMove(event.getPlayer());
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        plugin.mechanismManager().handleArrowHit(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.rooms().leave(event.getPlayer(), false);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.rooms().roomOf(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.rooms().roomOf(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.rooms().roomOf(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (plugin.rooms().roomOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.rooms().roomOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }
}
