package com.hubnpc;

import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** 右键执行指令、保护 NPC 不被攻击、区块加载时恢复设置。 */
final class NpcListener implements Listener {

    private final NpcManager manager;

    NpcListener(NpcManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin)) {
            return;
        }
        if (!manager.isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        manager.handleClick(event.getPlayer(), event.getRightClicked());
    }

    /**
     * 右键实体时服务端先发 At 事件；取消它就不会再发普通的 Interact 事件，
     * 所以这里必须同时执行指令。重复触发由 manager 的点击冷却去重。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin)) {
            return;
        }
        if (!manager.isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        manager.handleClick(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mannequin && manager.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Mannequin && manager.isNpc(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        manager.onEntitiesLoaded(event.getEntities());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearCooldowns(event.getPlayer().getUniqueId());
    }
}
