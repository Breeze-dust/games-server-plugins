package com.liarbar;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 事件入口：进入区域、入座、出牌/质疑/开枪、房间保护、离场。 */
public class LiarBarListener implements Listener {

    private final LiarBarPlugin plugin;
    private final LiarBarManager manager;

    /** 同 tick 去重：对着方块右键时 Paper 会先发方块事件、再发物品事件。 */
    private final Map<UUID, Integer> lastClickTick = new HashMap<>();

    public LiarBarListener(LiarBarPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    // ---------- 区域识别 ----------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getBlockX() == event.getFrom().getBlockX()
                && event.getTo().getBlockY() == event.getFrom().getBlockY()
                && event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }
        manager.checkRegion(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> manager.checkRegion(event.getPlayer()));
    }

    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        java.util.UUID thrower = manager.takeThrownBottle(event.getEntity().getUniqueId());
        if (thrower == null) {
            return;
        }
        if (event.getHitEntity() instanceof Player target) {
            manager.bottleHit(thrower, target);
        } else {
            event.getEntity().getWorld().playSound(
                    event.getEntity().getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        }
    }

    /** 喝完酒：取消原版消耗与效果，由插件手动结算（消耗、空瓶、反胃）。 */
    @EventHandler
    public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!manager.items().isLiquor(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (!manager.inRoom(player)) {
            Chat.actionBar(player, "&7只有在骗子酒馆里才能喝这种酒。");
            return;
        }
        manager.finishDrinking(player, event.getHand());
    }

    @EventHandler
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        if (manager.inRoom(event.getPlayer())) {
            manager.refreshDisguise(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> manager.checkRegion(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClickTick.remove(event.getPlayer().getUniqueId());
        manager.onQuit(event.getPlayer());
    }

    // ---------- 交互 ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.inRoom(player)) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) {
            return;
        }

        if (manager.isBuilder(player)) {
            return; // 建场模式：不拦右键，交给原版处理
        }

        // 酒：右键空气放行，让原版播放「喝」的动作；右键场景方块则拦下，避免开箱子/按按钮
        if (right && manager.items().isLiquor(event.getItem())) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                return;
            }
            if (manager.tableAt(clicked) != null || manager.tableAtButton(clicked) != null) {
                // 椅子 / 开火按钮照常处理，继续往下走
            } else {
                event.setCancelled(true);
                Chat.actionBar(player, "&7拿着酒时不能操作场景方块；抬头右键空气可以喝。");
                return;
            }
        }
        if (right && manager.items().isBottle(event.getItem())) {
            event.setCancelled(true);
            if (debounce(player)) {
                manager.throwBottle(player);
            }
            return;
        }

        // 发射器上的开火按钮：对局中也要先处理，不能落到后面的出牌逻辑里
        if (right && action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            LiarBarTable buttonTable = manager.tableAtButton(clicked);
            if (buttonTable != null) {
                // 不取消事件：让原版播放按钮按下的动画；只在轮到开枪时生效
                if (debounce(player)) {
                    buttonTable.pressButton(player, clicked);
                }
                return;
            }
        }

        LiarBarTable table = manager.tableOf(player);
        if (table != null && table.getState() == LiarBarTable.State.PLAYING) {
            event.setCancelled(true);
            if (!debounce(player)) {
                return;
            }
            if (left) {
                LiarBarCards.Card card = manager.items().cardOf(event.getItem());
                if (card != null) {
                    table.toggleSelect(player, player.getInventory().getHeldItemSlot());
                }
                return;
            }
            if (manager.items().isBell(event.getItem())) {
                table.challenge(player);
            } else {
                table.playSelected(player);
            }
            return;
        }

        if (right && action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            LiarBarTable chairTable = manager.tableAt(block);
            if (chairTable != null) {
                event.setCancelled(true);
                if (debounce(player)) {
                    manager.sitOrStand(player, chairTable, block);
                }
                return;
            }
        }

        // 房间内其它方块一律不响应（避免打开箱子、按按钮等）
        if (right && event.getClickedBlock() != null) {
            event.setCancelled(true);
        }
    }

    private boolean debounce(Player player) {
        int tick = Bukkit.getCurrentTick();
        Integer last = lastClickTick.put(player.getUniqueId(), tick);
        return last == null || last != tick;
    }

    /** 自己按下潜行下坐骑：等待/倒计时阶段视为起身；对局中拦下来。 */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        LiarBarTable table = manager.tableOf(player);
        if (table == null || manager.isForceDismount(player.getUniqueId())) {
            return;
        }
        if (table.blocksStanding()) {
            if (event.isCancellable()) {
                event.setCancelled(true);
            }
            Chat.actionBar(player, "&c对局中不能起身，想离开请用 &e/liarbar leave&c。");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> table.onVoluntaryDismount(player));
    }

    // ---------- 房间保护 ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.room().inRoom(event.getBlock().getLocation())
                && !manager.isBuilder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.room().inRoom(event.getBlock().getLocation())
                && !manager.isBuilder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 房间里的画/物品展示框不会被酒瓶或玩家打掉（建场模式除外）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakEvent event) {
        if (!manager.room().inRoom(event.getEntity().getLocation())) {
            return;
        }
        if (event instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player
                && manager.isBuilder(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !manager.inRoom(player)) {
            return;
        }
        if (manager.consumeDamageAllowed(player.getUniqueId())) {
            return; // 岩浆灼烧：放行，让其他人看到受击变红
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && manager.inRoom(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.tableOf(player) != null) {
            // 只锁对局道具（手牌/目标牌/质疑钟/左轮/准备道具），酒、空瓶、菜单道具可以在快捷栏和背包里自由移动。
            ItemStack hotbar = event.getHotbarButton() >= 0
                    ? player.getInventory().getItem(event.getHotbarButton()) : null;
            if (lockedItem(event.getCurrentItem())
                    || lockedItem(event.getCursor())
                    || lockedItem(hotbar)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.tableOf(player) != null) {
            for (ItemStack item : event.getNewItems().values()) {
                if (lockedItem(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean lockedItem(ItemStack item) {
        return manager.items().isGameItem(item)
                || com.hub.util.ReadyItems.isReadyItem(item);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.inRoom(event.getPlayer()) && !manager.isBuilder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!manager.inRoom(player)) {
            return;
        }
        if (lockedItem(event.getMainHandItem()) || lockedItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
