package com.superniuma.listener;

import com.superniuma.SuperNiumaPlugin;
import com.superniuma.game.GameRoom;
import com.superniuma.mechanism.PlacementSession;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
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

    /** 摆放阶段中键（创造模式拾取方块）：把机关移动到准星位置。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();
        GameRoom room = plugin.rooms().roomOf(player);
        if (room == null || room.phase() != GameRoom.Phase.PLACING) {
            return;
        }
        event.setCancelled(true);
        PlacementSession session = room.session(player);
        if (session != null && !session.confirmed()) {
            room.moveToCrosshair(player, event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickEntity(PlayerPickEntityEvent event) {
        GameRoom room = plugin.rooms().roomOf(event.getPlayer());
        if (room != null && room.phase() == GameRoom.Phase.PLACING) {
            event.setCancelled(true);
        }
    }

    /** 创造模式下拦截背包打开，避免玩家从创造物品栏拿东西。 */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            GameRoom room = plugin.rooms().roomOf(player);
            if (room != null && room.phase() == GameRoom.Phase.PLACING) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.rooms().roomOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (plugin.rooms().roomOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {        Player player = event.getPlayer();
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
        if (room == null) {
            return;
        }
        if (room.phase() == GameRoom.Phase.STARTING
                && event.getPlayer().getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            // 跑酷倒计时期间锁住位置，只允许转头。
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                        to.getYaw(), to.getPitch()));
            }
            return;
        }
        if (room.phase() == GameRoom.Phase.RUNNING
                && plugin.mechanismManager().isTrafficRed(room, event.getPlayer())) {
            // 红灯只屏蔽方向键和跳跃：步行速度已由机关置零，这里只拦住起跳，
            // 原有速度、下落和击飞照常生效。
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                double y = to.getY();
                boolean onGround = from.getBlock().getRelative(BlockFace.DOWN).getType().isSolid();
                if (onGround && y > from.getY() + 1.0E-4) {
                    y = from.getY();
                }
                event.setTo(new Location(from.getWorld(), to.getX(), y, to.getZ(),
                        to.getYaw(), to.getPitch()));
            }
            return;
        }
        if (room.phase() != GameRoom.Phase.RUNNING) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        // 机关传送门是真实的地狱门方块，禁止原版把玩家送去下界；跨门由机关逻辑处理。
        if (plugin.rooms().roomOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
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
