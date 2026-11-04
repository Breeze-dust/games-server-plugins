package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 缩小药水：把玩家自己的 {@link Attribute#SCALE} 调小，模型和碰撞箱会一起缩小。
 * 再次右键、离开主城、进游戏、死亡、下线、关服都会恢复原大小。
 */
public final class ShrinkManager {

    private final LobbyToysPlugin plugin;
    /** 玩家 -> 使用前的基础缩放值，用于恢复。 */
    private final Map<UUID, Double> previousScale = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private BukkitTask task;

    public ShrinkManager(LobbyToysPlugin plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::guard, 20L, 20L);
    }

    public void toggle(Player player) {
        Settings settings = plugin.settings();
        if (!player.hasPermission("lobbytoys.use")) {
            Chat.actionBar(player, "&c你没有使用小玩具的权限");
            return;
        }
        if (!settings.isAllowedWorld(player.getWorld())) {
            Chat.actionBar(player, "&c缩小药水只能在主城使用");
            return;
        }
        if (plugin.isPlaying(player)) {
            Chat.actionBar(player, "&c游戏中不能使用小玩具");
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < settings.cooldownMillis) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        if (previousScale.containsKey(player.getUniqueId())) {
            stop(player.getUniqueId(), true);
        } else {
            start(player);
        }
    }

    public boolean isActive(UUID playerId) {
        return previousScale.containsKey(playerId);
    }

    public void stop(UUID playerId, boolean notify) {
        Double previous = previousScale.remove(playerId);
        if (previous == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        setScale(player, previous);
        if (notify) {
            Chat.actionBar(player, "&7已恢复原来的身形");
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Double> entry : previousScale.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                setScale(player, entry.getValue());
            }
        }
        previousScale.clear();
    }

    /**
     * 玩家重新进服时兜底：如果他上次在缩小状态下掉线/崩服，缩放会写进玩家存档，
     * 这里把"还停留在本插件缩小值"的玩家恢复成 1.0。
     */
    public void resetPersisted(Player player) {
        if (previousScale.containsKey(player.getUniqueId())) {
            return;
        }
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }
        double value = scale.getBaseValue();
        if (value < 1.0 && Math.abs(value - plugin.settings().shrinkScale) < 1.0E-6) {
            scale.setBaseValue(1.0);
        }
    }

    private void start(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            Chat.actionBar(player, "&c当前服务端不支持玩家缩放");
            return;
        }
        previousScale.put(player.getUniqueId(), scale.getBaseValue());
        scale.setBaseValue(plugin.settings().shrinkScale);
        Chat.actionBar(player, "&d已缩小 &7（再右键恢复）");
    }

    private static void setScale(Player player, double value) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(value);
        }
    }

    private void guard() {
        if (previousScale.isEmpty()) {
            return;
        }
        Settings settings = plugin.settings();
        Iterator<Map.Entry<UUID, Double>> iterator = previousScale.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Double> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()
                    || !settings.isAllowedWorld(player.getWorld())
                    || plugin.isPlaying(player)) {
                iterator.remove();
                if (player != null && player.isOnline()) {
                    setScale(player, entry.getValue());
                }
            }
        }
    }
}
