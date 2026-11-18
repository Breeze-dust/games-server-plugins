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

        // 酒与空酒瓶：只要在酒馆区域里，任何时候都能喝/扔
        if (right && manager.items().isLiquor(event.getItem())) {
            event.setCancelled(true);
            if (debounce(player)) {
                manager.drinkLiquor(player);
            }
            return;
        }
        if (right && manager.items().isBottle(event.getItem())) {
            event.setCancelled(true);
            if (debounce(player)) {
                manager.throwBottle(player);
            }
            return;
        }

        if (manager.isBuilder(player)) {
            return; // 建场模式：不拦右键，交给原版处理
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
            if (manager.items().isRevolver(event.getItem())) {
                table.fireRevolver(player.getUniqueId());
            } else if (manager.items().isBell(event.getItem())) {
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
                    manager.sitOrStand(player, chairTable);
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
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.tableOf(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.inRoom(event.getPlayer()) && !manager.isBuilder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (manager.inRoom(event.getPlayer()) && !manager.isBuilder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
