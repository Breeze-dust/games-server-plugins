package com.lobbytoys.scratch;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 把玩家的左键长按、误触和离开事件接给 {@link ScratchManager}。 */
public final class ScratchListener implements Listener {

    private final ScratchManager scratch;

    public ScratchListener(ScratchManager scratch) {
        this.scratch = scratch;
    }

    /** 按住左键时客户端会持续发挥臂包，这是「长按」在服务端唯一可靠的表现。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            scratch.onSwing(event.getPlayer());
        }
    }

    /** 固定住的展示框本来就不该受伤；万一客户端发了攻击包，这里兜住。 */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (scratch.isCardFrame(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** 防止活塞/爆炸/玩家拆掉屏幕。 */
    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (scratch.isCardFrame(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** 右键不参与刮卡，取消掉就不会旋转或取走地图。 */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (scratch.isCardFrame(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scratch.close(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scratch.close(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scratch.hideFrom(event.getPlayer());
    }
}
