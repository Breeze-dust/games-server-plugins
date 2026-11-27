package com.hub.voice;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 队伍语音（Simple Voice Chat）接入层，供各小游戏调用。
 * <p>
 * 用法：开局给每队 {@link #createIsolatedGroup(String)} 建一个隔离组，
 * 分队后用 {@link #join(UUID, UUID)} 把玩家塞进去，淘汰/离开用 {@link #leave(UUID)}，
 * 结算用 {@link #deleteGroup(UUID)} 删组。<b>安装 SVC 前所有调用都是空操作</b>，
 * 小游戏不需要判断服务器有没有装。
 * <p>
 * SVC 的组是客户端可自行退出的，所以这里每秒对账一次，把被踢出组的玩家拉回来。
 * <p>
 * 要注意 SVC 要等服务端启动后的下一个 tick 才回调 initialize()：注册插件时拿不到 API
 * 是正常现象，必须等就绪后再建组/拉人，否则语音会一直停留在原版「按距离衰减」模式。
 */
public final class VoiceChat {

    private static VoiceChatBackend backend;
    private static Plugin owner;
    private static BukkitTask enforceTask;
    private static boolean announced;
    private static final Map<UUID, UUID> desired = new HashMap<>();

    private VoiceChat() {
    }

    public static void init(Plugin plugin) {
        owner = plugin;
        Plugin svc = Bukkit.getPluginManager().getPlugin("voicechat");
        if (svc == null || !svc.isEnabled()) {
            plugin.getLogger().info("未安装 Simple Voice Chat，队伍语音功能关闭（不影响游戏）。");
            return;
        }
        try {
            backend = SvcBackend.install();
            enforceTask = Bukkit.getScheduler().runTaskTimer(plugin, VoiceChat::tick, 20L, 20L);
        } catch (Throwable t) {
            backend = null;
            plugin.getLogger().log(Level.WARNING, "接入 Simple Voice Chat 失败，队伍语音功能关闭。", t);
        }
    }

    public static boolean available() {
        return backend != null && backend.ready();
    }

    public static UUID createIsolatedGroup(String name) {
        return available() ? backend.createIsolatedGroup(name) : null;
    }

    /** 把玩家放进组；groupId 为 null 等同于 {@link #leave(UUID)}。 */
    public static void join(UUID player, UUID groupId) {
        if (groupId == null) {
            leave(player);
            return;
        }
        desired.put(player, groupId);
        if (available()) {
            backend.apply(player, groupId);
        }
    }

    public static void leave(UUID player) {
        desired.remove(player);
        if (available()) {
            backend.apply(player, null);
        }
    }

    public static void deleteGroup(UUID groupId) {
        if (groupId == null) {
            return;
        }
        desired.values().removeIf(groupId::equals);
        if (available()) {
            backend.removeGroup(groupId);
        }
    }

    public static void shutdown() {
        if (enforceTask != null) {
            enforceTask.cancel();
            enforceTask = null;
        }
        if (available()) {
            for (UUID player : new HashMap<>(desired).keySet()) {
                backend.apply(player, null);
            }
        }
        desired.clear();
        backend = null;
        announced = false;
        owner = null;
    }

    /** 等 SVC 就绪后开始每秒对账；就绪之前什么都不做。 */
    private static void tick() {
        if (!available()) {
            return;
        }
        if (!announced) {
            announced = true;
            if (owner != null) {
                owner.getLogger().info("已接入 Simple Voice Chat，队伍语音功能可用。");
            }
        }
        enforce();
    }

    private static void enforce() {
        if (!available()) {
            return;
        }
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(desired).entrySet()) {
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                desired.remove(entry.getKey());
                continue;
            }
            backend.apply(entry.getKey(), entry.getValue());
        }
    }
}
