package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ChairListener implements Listener {

    private final ChairManager chairs;

    public ChairListener(ChairManager chairs) {
        this.chairs = chairs;
    }

    /** 椅子设置工具右键方块 = 登记 / 取消；玩家空手右键登记的方块 = 坐下 / 起身。 */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();

        if (chairs.isTool(event.getItem())) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            toggleChair(player, event.getClickedBlock());
            return;
        }

        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir()) {
            return;
        }
        if (chairs.isSitting(player)) {
            chairs.stand(player);
            return;
        }
        if (player.isSneaking()) {
            return;
        }
        if (chairs.sit(player, event.getClickedBlock())) {
            // 登记过的方块可能带界面（箱子、按钮等），坐下时不要再触发原版用途
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    private void toggleChair(Player player, Block block) {
        if (!player.isOp() && !player.hasPermission("lobbytoys.admin")) {
            Chat.actionBar(player, "&c只有管理员能登记椅子。");
            return;
        }
        if (!chairs.registry().isAllowedWorld(block.getWorld())) {
            Chat.actionBar(player, "&c只能在主城世界里登记椅子。");
            return;
        }
        boolean added = chairs.registry().toggle(block, player.getLocation().getYaw());
        if (added) {
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            Chat.actionBar(player, "&a已把这把方块登记为椅子（共 " + chairs.registry().size() + " 把）");
        } else {
            chairs.evict(block);
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            Chat.actionBar(player, "&7已取消这把椅子（还剩 " + chairs.registry().size() + " 把）");
        }
    }

    /** 客户端按 Shift 会让原版直接下车：跟着把座位实体收掉。 */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            chairs.dismiss(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            Player player = event.getPlayer();
            if (chairs.isSitting(player)) {
                chairs.stand(player);
            }
        }
    }

    /** 被 /hub、传送门等挪走时自动起来（座位不动，玩家被传开）。 */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ChairManager.SeatInfo seat = chairs.seatOf(player);
        if (seat == null) {
            return;
        }
        if (!chairs.isSitting(player)
                || player.getWorld() != seat.standLocation().getWorld()
                || player.getLocation().distanceSquared(seat.standLocation()) > 4.0) {
            chairs.dismiss(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chairs.dismiss(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        chairs.dismiss(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        chairs.dismiss(event.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            chairs.dismiss(player.getUniqueId());
        }
    }

    /** 椅子被拆了：取消登记，并让上面的人站起来。 */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (chairs.registry().remove(event.getBlock())) {
            chairs.evict(event.getBlock());
        }
    }
}
