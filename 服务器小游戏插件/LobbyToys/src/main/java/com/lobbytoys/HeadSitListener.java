package com.lobbytoys;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 头顶骑乘的交互：
 * <ul>
 *   <li>右键玩家 → 坐到对方头顶（只认 Player，NPC/分身不行）；</li>
 *   <li>被骑的人按 Shift → 头顶的人全部下来；</li>
 *   <li>被骑的人空手长按右键 → 头顶上的一串人一起被甩飞；</li>
 *   <li>下线 / 死亡 / 离开主城世界 → 自动下来。</li>
 * </ul>
 */
public final class HeadSitListener implements Listener {

    private final HeadSitManager headSit;

    public HeadSitListener(HeadSitManager headSit) {
        this.headSit = headSit;
    }

    /** 空手右键另一个玩家：坐上去。只处理 Player，其他实体（NPC、分身、盔甲架）一概不理。 */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player mount)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!mainHand.getType().isAir()) {
            return;
        }
        if (headSit.sit(player, mount)) {
            // 拦住原版/其他插件对这个玩家的右键效果
            event.setCancelled(true);
            headSit.resetHold(player);
        }
    }

    /**
     * 空手按住右键 = 可能是在蓄力，攒够了就把头顶的人甩出去。
     * <p>
     * 注意原版限制：空手右键「空气」客户端根本不发包（SPIGOT-604），事件只有对着方块按住才有，
     * 所以这里同时收 RIGHT_CLICK_BLOCK 与 RIGHT_CLICK_AIR（后者在装了物品/基岩端等情况下才会来）。
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir()) {
            headSit.resetHold(player);
            return;
        }
        headSit.handleHoldRightClick(player);
    }

    /** 被骑的人蹲下 → 让头顶的人下来；骑在别人头上的人蹲下 → 自己也下来。 */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (headSit.directRiderOf(player) != null) {
            headSit.dropAbove(player);
        }
        if (headSit.mountOf(player) != null) {
            headSit.dropSelf(player);
        }
    }

    /** 玩家从座位上下来（Shift 原生下车 / 座位被删）时把座位实体收掉。 */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            headSit.handleDismount(player, event.getDismounted());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        headSit.resetHold(player);
        headSit.dropAbove(player);
        headSit.dropSelf(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        headSit.dropAbove(player);
        headSit.dropSelf(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        headSit.handleWorldChange(event.getPlayer());
    }
}
