package com.cloudwalk;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

/** 把跑酷之外的所有出口（下线、传送、死亡、飞行、珍珠……）都收束成「结束本局」。 */
public class CloudWalkListener implements Listener {

    private final CloudWalkPlugin plugin;

    public CloudWalkListener(CloudWalkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getManager().stop(event.getPlayer(), "下线", true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // UNKNOWN 是服务端自己做的位置校正（延迟/移动校验回拉），不是玩家或插件传送；
        // 把它当成「传送离开」会让人明明落在方块上却突然结束。
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        plugin.getManager().stop(event.getPlayer(), "传送离开", true, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getManager().stop(event.getEntity(), "死亡", true, false);
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
        // 只取消飞行：主城如果有双击空格起飞之类的机制，不应因此直接结束本局。
        // 真正飞起来（isFlying）由 2 tick 循环兜底结束。
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)
                || !plugin.getManager().isPlaying(player.getUniqueId())) {
            return;
        }
        if (projectile instanceof EnderPearl || projectile instanceof Firework) {
            event.setCancelled(true);
            plugin.getManager().stop(player, "使用了违禁物品", true, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getManager().isPlaying(event.getPlayer().getUniqueId())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && (item.getType() == Material.ENDER_PEARL
                || item.getType() == Material.FIREWORK_ROCKET)) {
            event.setCancelled(true);
            plugin.getManager().stop(event.getPlayer(), "使用了违禁物品", true, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getManager().isPathBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
