package com.lobbytoys;

import org.bukkit.entity.Player;
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

    /** 空手右键可坐的方块 → 坐下；坐着时空手再右键 → 起身（骗子酒馆同款操作）。 */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir()) {
            return;
        }
        Player player = event.getPlayer();
        if (chairs.isSitting(player)) {
            chairs.stand(player);
            return;
        }
        if (player.isSneaking()) {
            return;
        }
        chairs.sit(player, event.getClickedBlock());
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

    /** 椅子被拆了：上面的人站起来。 */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        for (Player player : event.getBlock().getWorld().getPlayers()) {
            ChairManager.SeatInfo seat = chairs.seatOf(player);
            if (seat != null && seat.block().equals(event.getBlock())) {
                chairs.stand(player);
            }
        }
    }
}
