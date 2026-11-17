package com.archery;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** 把射箭之外的各种出口都收束成「结束本局」，并处理编辑模式和聊天栏分数输入。 */
public class ArcheryListener implements Listener {

    private final ArcheryPlugin plugin;

    public ArcheryListener(ArcheryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        ArcheryManager manager = plugin.getManager();
        manager.stop(event.getPlayer(), "下线", true, true);
        manager.exitEdit(event.getPlayer(), true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getManager().hideTargetsFrom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        ArcheryManager manager = plugin.getManager();
        Player player = event.getPlayer();
        if (manager.isPlaying(player.getUniqueId())
                && (manager.isBow(event.getItemDrop().getItemStack())
                || manager.isArrow(event.getItemDrop().getItemStack()))) {
            event.setCancelled(true);
        } else if (manager.isEditing(player.getUniqueId())
                && manager.isEditItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // UNKNOWN 是服务端自己的位置校正，不是玩家或插件传送
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        plugin.getManager().stop(event.getPlayer(), "传送离开", true, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getManager().stop(event.getEntity(), "死亡", true, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE
                || event.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getManager().stop(event.getPlayer(), "切换游戏模式", true, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying() || !plugin.getManager().isPlaying(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getManager().isPlaying(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        plugin.getManager().stop(player, "尝试滑翔", true, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getManager().isPlaying(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** 局内射出的箭不能伤害别的玩家。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && plugin.getManager().ownsArrow(arrow)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Arrow arrow
                && arrow.getShooter() instanceof Player player) {
            plugin.getManager().onArrowLaunched(player, arrow);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            plugin.getManager().onArrowHit(arrow);
        }
    }

    /** 编辑模式下在聊天栏输入基础分 / 区域参数；其他聊天不拦截。 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ArcheryManager manager = plugin.getManager();
        UUID id = player.getUniqueId();
        if (!manager.isAwaitingScore(id) && !manager.isAwaitingParams(id)) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!manager.handleScoreInput(player, text)) {
                manager.handleParamsInput(player, text);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ArcheryManager manager = plugin.getManager();
        ItemStack item = event.getItem();

        if (manager.isEditing(player.getUniqueId()) && manager.isEditItem(item)) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.OFF_HAND) {
                return;
            }
            manager.handleEditInteract(player, item, event.getAction(), event.getClickedBlock());
            return;
        }

        if (!manager.isPlaying(player.getUniqueId())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (item != null && (item.getType() == Material.ENDER_PEARL
                || item.getType() == Material.FIREWORK_ROCKET)) {
            event.setCancelled(true);
            manager.stop(player, "使用了违禁物品", true, true);
        }
    }
}
