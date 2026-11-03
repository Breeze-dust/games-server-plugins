package com.seatgrab;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;

/** 抢位占座事件：展示框交互、偷袋/投掷、击退笔、局内保护。 */
public final class SeatGrabListener implements Listener {

    private final SeatGrabPlugin plugin;
    private final SeatGrabManager manager;

    public SeatGrabListener(SeatGrabPlugin plugin, SeatGrabManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---------- 等待室道具 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWaitInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!manager.isWaiting(player)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (manager.isReturnItem(item)) {
            event.setCancelled(true);
            manager.leave(player, false);
            return;
        }
        if (manager.isStartItem(item)) {
            event.setCancelled(true);
            if (player.hasPermission("seatgrab.admin")) {
                manager.startGame(true);
            }
        }
    }

    // ---------- 展示框 / 玩家右键 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getRightClicked() instanceof ItemFrame frame) {
            if (!manager.isArenaFrame(frame)) {
                return;
            }
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                manager.handleFrameInteract(player, frame);
            }
            return;
        }
        if (event.getRightClicked() instanceof Player target && event.getHand() == EquipmentSlot.HAND) {
            if (manager.isPlaying(player) && manager.isPlaying(target)
                    && manager.isPen(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                manager.penHit(player, target);
            }
        }
    }

    /** 展示框还可能触发 at 事件，统一拦掉，避免旋转/取物品。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame frame && manager.isArenaFrame(frame)) {
            event.setCancelled(true);
        }
    }

    // ---------- 偷袋蓄力 ----------

    /** 手里拿着别人袋子时，右键空气/方块开始蓄力（不取消事件，让原版 bundle 的“按住”状态生效）。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!manager.isPlaying(player) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !manager.isBagItem(item)) {
            return;
        }
        if (manager.isOwnBag(item, player)) {
            return;
        }
        if (manager.isCarrying(player)) {
            manager.startCharge(player);
        }
    }

    @EventHandler
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        manager.finishCharge(event.getPlayer(), event.getTicksHeldFor());
    }

    // ---------- 击退笔：0 伤害 + 受击动画 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && manager.isArenaFrame(frame)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p : null;
        if (!manager.isInGame(victim) && (attacker == null || !manager.isInGame(attacker))) {
            return;
        }
        event.setCancelled(true);
        if (attacker != null) {
            manager.penHit(attacker, victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (event.getEntity() instanceof ItemFrame frame && manager.isArenaFrame(frame)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && manager.isInGame(player)) {
            event.setCancelled(true);
        }
    }

    // ---------- 局内保护 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame && manager.isArenaFrame(frame)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isInGame(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isPlaying(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isPlaying(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (manager.isCarrying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (manager.isCarrying(event.getPlayer()) || manager.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && manager.isInGame(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        manager.handlePickup(event);
    }

    // ---------- 死亡 / 重生 / 退出 ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isPlaying(player)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        manager.eliminate(player, "死亡");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInGame(player)) {
            return;
        }
        Location lobby = manager.arena().lobby();
        if (lobby != null) {
            event.setRespawnLocation(lobby);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && manager.isInGame(player)) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }
}
