package com.hub.voice;

import com.hub.api.ArcadeBridge;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * 全程还有一个常驻的「主城公共组」：SVC 就绪后建一次，组名 {@link #HUB_GROUP_NAME}，
 * 持久化（人走光也不会被回收），所以客户端组列表里始终能看到。主城入口和各游戏等待室
 * 调 {@link #joinHub(UUID)} 加入，真正开局时 {@link com.hub.api.HubAPI#setPlaying} 调
 * {@link #leaveHub(UUID)} 移出。公共组不参与每秒对账，玩家可以自己退出。
 * <p>
 * 要注意 SVC 要等服务端启动后的下一个 tick 才回调 initialize()：注册插件时拿不到 API
 * 是正常现象，必须等就绪后再建组/拉人，否则语音会一直停留在原版「按距离衰减」模式。
 */
public final class VoiceChat {

    /** 主城常驻公共组的名字（显示在 SVC 客户端的组列表里）。 */
    public static final String HUB_GROUP_NAME = "大厅公共频道";

    /** 固定组 ID：MiniGameLib 重载后还能认出同一个组，不会建出同名的第二个。 */
    private static final UUID HUB_GROUP_ID = UUID.fromString("3f7c9d21-8b4e-4a6f-9c1d-2e5b7a8c9d10");

    private static VoiceChatBackend backend;
    private static Plugin owner;
    private static BukkitTask enforceTask;
    private static boolean announced;
    /** 建组失败只提示一次，避免定时任务刷屏。 */
    private static boolean hubWarned;
    private static UUID hubGroupId;
    private static final Map<UUID, UUID> desired = new HashMap<>();
    /** SVC 还没就绪时先记下要进公共组的人，就绪后补一次。 */
    private static final Set<UUID> pendingHub = new HashSet<>();

    private VoiceChat() {
    }

    public static void init(Plugin plugin) {
        owner = plugin;
        hubWarned = false;
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

    /** 主城公共组的 ID；还没建好（SVC 未就绪）时为 null。 */
    public static UUID hubGroupId() {
        return hubGroupId;
    }

    public static UUID createIsolatedGroup(String name) {
        return available() ? backend.createIsolatedGroup(name) : null;
    }

    /**
     * 把玩家加入主城公共组。
     * <p>
     * 只在“进入主城 / 进入游戏等待室”这类入口调用一次，不参与每秒对账，
     * 所以玩家自己退组后不会被反复拉回来。已经在队伍组里的玩家不抢。
     */
    public static void joinHub(UUID player) {
        if (player == null || ArcadeBridge.isInRound(player) || desired.containsKey(player)) {
            return;
        }
        if (!available()) {
            if (backend != null) {
                pendingHub.add(player);
            }
            return;
        }
        ensureHubGroup();
        if (hubGroupId == null) {
            pendingHub.add(player);
            return;
        }
        backend.apply(player, hubGroupId);
    }

    /** 把玩家移出主城公共组；如果他在队伍组里则不动。 */
    public static void leaveHub(UUID player) {
        if (player == null) {
            return;
        }
        pendingHub.remove(player);
        if (!available() || hubGroupId == null || desired.containsKey(player)) {
            return;
        }
        if (backend.inGroup(player, hubGroupId)) {
            backend.apply(player, null);
        }
    }

    /** 把玩家放进组；groupId 为 null 等同于 {@link #leave(UUID)}。 */
    public static void join(UUID player, UUID groupId) {
        if (groupId == null) {
            leave(player);
            return;
        }
        pendingHub.remove(player);
        desired.put(player, groupId);
        if (available()) {
            backend.apply(player, groupId);
        }
    }

    public static void leave(UUID player) {
        pendingHub.remove(player);
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
            if (hubGroupId != null) {
                backend.removeGroup(hubGroupId);
            }
        }
        pendingHub.clear();
        hubGroupId = null;
        hubWarned = false;
        desired.clear();
        backend = null;
        announced = false;
        owner = null;
    }

    /** 等 SVC 就绪后建常驻组并开始每秒对账；就绪之前什么都不做。 */
    private static void tick() {
        if (!available()) {
            return;
        }
        ensureHubGroup();
        if (!announced) {
            announced = true;
            if (owner != null) {
                owner.getLogger().info("已接入 Simple Voice Chat，队伍语音与主城公共组可用。");
            }
        }
        flushPendingHub();
        enforce();
    }

    /** 公共组是常驻的：SVC 就绪后建一次；SVC 被重载导致组失效时补建。 */
    private static void ensureHubGroup() {
        if (!available()) {
            hubGroupId = null;
            return;
        }
        try {
            if (hubGroupId != null && backend.groupExists(hubGroupId)) {
                return;
            }
            hubGroupId = backend.createPersistentGroup(HUB_GROUP_ID, HUB_GROUP_NAME);
        } catch (RuntimeException ex) {
            // 出错时不要让定时任务每秒钟刷一次堆栈；只提示一次，下一 tick 继续重试
            hubGroupId = null;
            if (owner != null && !hubWarned) {
                hubWarned = true;
                owner.getLogger().log(Level.WARNING, "创建主城公共语音组失败，稍后自动重试。", ex);
            }
        }
    }

    /** SVC 就绪前收到的入组请求，在这里补发一次。 */
    private static void flushPendingHub() {
        if (pendingHub.isEmpty() || hubGroupId == null) {
            return;
        }
        for (UUID id : new ArrayList<>(pendingHub)) {
            pendingHub.remove(id);
            if (Bukkit.getPlayer(id) == null || desired.containsKey(id) || ArcadeBridge.isInRound(id)) {
                continue;
            }
            backend.apply(id, hubGroupId);
        }
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
