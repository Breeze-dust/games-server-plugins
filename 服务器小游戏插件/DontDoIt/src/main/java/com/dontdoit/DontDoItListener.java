package com.dontdoit;

import com.dontdoit.menu.SettingsMenu;
import com.dontdoit.menu.TeamSelectMenu;
import com.hub.util.Chat;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** 事件入口：等待室道具 + 词条判定。 */
public final class DontDoItListener implements Listener {

    private final DontDoItPlugin plugin;
    private final DontDoItManager manager;
    private final java.util.Map<UUID, Long> ceilingWarnAt = new java.util.HashMap<>();

    public DontDoItListener(DontDoItPlugin plugin, DontDoItManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---------- 等待室道具 ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Game game = manager.gameOf(player);
        if (game == null || game.state() != Game.State.WAITING) {
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (manager.isTeamMenuItem(item)) {
            event.setCancelled(true);
            TeamSelectMenu.open(plugin, manager, game, player);
            return;
        }
        if (manager.isStartPaper(item)) {
            event.setCancelled(true);
            if (!player.isOp() && !player.hasPermission("ddi.admin")) {
                Chat.send(player, "&c只有管理员可以开始游戏。");
                return;
            }
            game.autoAssignTeams();
            if (!game.start(player)) {
                Chat.send(player, "&c开局失败，检查队伍人数与场地石层。");
            }
            return;
        }
        if (manager.isLeaveBed(item)) {
            event.setCancelled(true);
            manager.leave(player, false);
            Chat.send(player, "&7你退出了不要做挑战。");
        }
    }

    @EventHandler
    public void onBookSign(PlayerEditBookEvent event) {
        if (event.isSigning()) {
            Player player = event.getPlayer();
            Game game = manager.gameOf(player);
            if (game == null || game.state() != Game.State.WAITING) {
                return;
            }
            if (!manager.customCriteriaEnabled()) {
                return;
            }
            String text = "";
            for (String page : event.getNewBookMeta().getPages()) {
                String plain = Chat.toLegacy(Chat.color(page)).replace("§", "");
                if (!plain.isBlank()) {
                    text = plain;
                    break;
                }
            }
            String error = manager.setCustomCriteria(player, text);
            if (error != null) {
                Chat.send(player, "&c" + error);
            }
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (TeamSelectMenu.handleClick(manager, player, event)) {
            return;
        }
        manager.settingsMenu().handleClick(player, event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.leave(event.getPlayer(), true);
    }

    // ---------- 词条事件 ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getItem().getItemStack().getType() == Material.DIAMOND) {
            Game game = manager.gameOf(player);
            if (game != null && game.state() == Game.State.RUNNING) {
                game.onDiamond(player);
            }
        }
        managerEvent(player, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            managerEvent(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            managerEvent(player, event);
        } else if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player player) {
            managerEvent(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDeath(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent death) {
            Game game = manager.gameOf(death.getEntity());
            if (game != null) {
                game.onDeath(death.getEntity());
            }
            managerEvent(death.getEntity(), event);
        } else if (event.getEntity().getKiller() != null) {
            managerEvent(event.getEntity().getKiller(), event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            managerEvent(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteractCriteria(PlayerInteractEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLevel(PlayerLevelChangeEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onHotbar(PlayerItemHeldEvent event) {
        managerEvent(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClickCriteria(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            managerEvent(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onRespawn(PlayerRespawnEvent event) {
        Game game = manager.gameOf(event.getPlayer());
        if (game != null) {
            game.onRespawn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Game game = manager.gameOf(event.getPlayer());
        if (game == null || game.state() != Game.State.RUNNING) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        game.onMove(event.getPlayer(), event.getFrom(), event.getTo());
        game.onMoveState(event.getPlayer());
    }

    /** 对局中禁止普通玩家用创造等模式例外；也防止把等待道具丢出去。 */
    @EventHandler
    public void onDropProtected(PlayerDropItemEvent event) {
        Game game = manager.gameOf(event.getPlayer());
        if (game == null) {
            return;
        }
        ItemStack item = event.getItemDrop().getItemStack();
        if (manager.isStartPaper(item) || manager.isLeaveBed(item) || manager.isTeamMenuItem(item)) {
            event.setCancelled(true);
        }
    }

    /**
     * 放行本方场地的方块破坏：GuessTheBuild 在 NORMAL 优先级对任何非 OP、不在 GTB 局内的玩家
     * 无条件取消破坏（不分世界），DigToBottom/BuildRun 也是用同样的“取消取消”处理。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreakAllow(BlockBreakEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        Game game = manager.gameOf(event.getPlayer());
        if (game == null || game.state() != Game.State.RUNNING) {
            return;
        }
        if (game.arena().contains(event.getBlock().getLocation())) {
            event.setCancelled(false);
        }
    }

    /**
     * 同理放行本方场地的放置（等待室的放置拦截在 WAITING 生效，不冲突）。
     * 超过地图基线高度上限的放置直接拒绝，否则那些方块脱离还原范围、会一直留着。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlaceAllow(BlockPlaceEvent event) {
        Game game = manager.gameOf(event.getPlayer());
        if (game == null || game.state() != Game.State.RUNNING) {
            return;
        }
        if (!game.isInsideArena(event.getBlock().getLocation())) {
            return;
        }
        if (event.getBlock().getY() > game.restoreCeilingY()) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            Long last = ceilingWarnAt.get(player.getUniqueId());
            if (last == null || now - last > 3000) {
                ceilingWarnAt.put(player.getUniqueId(), now);
                Chat.send(player, "&c超出可还原高度，不能在这里放方块。");
            }
            return;
        }
        if (event.isCancelled()) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onPlaceProtected(BlockPlaceEvent event) {
        Game game = manager.gameOf(event.getPlayer());
        if (game == null || game.state() != Game.State.WAITING) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onGameMode(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        Game game = manager.gameOf(event.getPlayer());
        if (game != null && game.state() == Game.State.RUNNING
                && event.getNewGameMode() == GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    private void managerEvent(Player player, org.bukkit.event.Event event) {
        Game game = manager.gameOf(player);
        if (game == null || game.state() != Game.State.RUNNING) {
            return;
        }
        game.handleEvent(player, event);
    }
}
