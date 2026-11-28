package com.buildbattle.gtb.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.Event;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat 真实实现：一局游戏 = 一个语音频道。
 *
 * <ul>
 *   <li>开局：创建（或复用）一个隐藏、不持久、隔离的频道，把全员拉进去 —— 组内语音不受距离限制；</li>
 *   <li>中途加入：立即进频道；退出/断线：离开频道；</li>
 *   <li>结束：所有人退出并删除频道（不会残留到下一局）；</li>
 *   <li>锁死（voice.lock）：游戏内取消玩家自行退组/建组/进别组的事件。</li>
 * </ul>
 *
 * <p>只在确认服务器装了 SVC 之后由 {@link VoiceChatBridge#create(Plugin)} 反射加载，
 * 所以没装 SVC 的服务器不会因为缺少 SVC 类而报 NoClassDefFoundError。</p>
 */
public final class VoiceChatHook implements VoiceChatBridge, VoicechatPlugin {

    /** 本插件的语音 ID（SVC 用来区分各插件的注册）。 */
    private static final String PLUGIN_ID = "guessthebuild";

    /** 频道固定 ID：每局复用同一个频道，避免玩家列表里堆一堆历史频道。 */
    private static final UUID GROUP_ID = UUID.fromString("7c1f5d2e-9a44-4b3e-8f60-1d2a6b9c0f35");

    private final Plugin plugin;

    /** 本局应该在频道里的玩家。 */
    private final Set<UUID> inGame = ConcurrentHashMap.newKeySet();

    private volatile VoicechatServerApi api;
    private volatile Group group;
    private volatile Settings settings = new Settings(false, true, true, "建筑猜猜乐");
    private volatile boolean registered;
    private volatile boolean disbanding;
    private BukkitTask keepTask;

    public VoiceChatHook(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---------- VoicechatPlugin ----------

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        if (voicechatApi instanceof VoicechatServerApi serverApi) {
            this.api = serverApi;
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(CreateGroupEvent.class, this::onCreateGroup);
        registration.registerEvent(JoinGroupEvent.class, this::onJoinGroup);
        registration.registerEvent(LeaveGroupEvent.class, this::onLeaveGroup);
    }

    // ---------- VoiceChatBridge ----------

    @Override
    public void load(Settings newSettings) {
        this.settings = newSettings == null ? new Settings(false, true, true, "建筑猜猜乐") : newSettings;
        if (!this.settings.enabled()) {
            disband();
            return;
        }
        register();
        if (!inGame.isEmpty()) {
            startKeepTask();
        }
    }

    @Override
    public void enter(Player p) {
        if (p == null) {
            return;
        }
        inGame.add(p.getUniqueId());
        assign(p.getUniqueId());
        startKeepTask();
    }

    @Override
    public void exit(Player p) {
        if (p == null) {
            return;
        }
        // 先摘掉标记再退组：否则会撞上自己注册的 LeaveGroupEvent 拦截，导致退不出去
        inGame.remove(p.getUniqueId());
        release(p.getUniqueId());
    }

    @Override
    public void disband() {
        disbanding = true;
        try {
            VoicechatServerApi a = this.api;
            List<UUID> ids = new ArrayList<>(inGame);
            inGame.clear();
            stopKeepTask();
            if (a == null) {
                group = null;
                return;
            }
            for (UUID id : ids) {
                release(id);
            }
            try {
                if (findGroup(a) != null) {
                    a.removeGroup(GROUP_ID);
                }
            } catch (RuntimeException ignored) {
                // 频道已被 SVC 自己删掉时忽略
            }
            group = null;
        } finally {
            disbanding = false;
        }
    }

    // ---------- 内部 ----------

    private void register() {
        if (registered) {
            return;
        }
        Object loaded = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (!(loaded instanceof BukkitVoicechatService service)) {
            plugin.getLogger().warning("未找到 Simple Voice Chat 服务，语音频道暂不可用。");
            return;
        }
        service.registerPlugin(this);
        registered = true;
        plugin.getLogger().info("已接入 Simple Voice Chat：游戏开始后全员进入频道「"
                + settings.channelName() + "」（锁死=" + settings.lock() + "）。");
    }

    /** 把一个玩家放进本局频道；连接还没建立时静默失败，由 keepTask 反复补。 */
    private void assign(UUID id) {
        VoicechatServerApi a = this.api;
        if (a == null || !settings.enabled()) {
            return;
        }
        VoicechatConnection connection = a.getConnectionOf(id);
        if (connection == null) {
            return;
        }
        try {
            Group current = connection.getGroup();
            if (current != null && GROUP_ID.equals(current.getId())) {
                return;
            }
            Group target = ensureGroup(a);
            if (target != null) {
                connection.setGroup(target);
            }
        } catch (IllegalStateException ignored) {
            // SVC 不允许在部分回调里立刻改组：交给 keepTask 下一拍重试
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("把玩家加入语音频道失败：" + ex);
        }
    }

    private Group ensureGroup(VoicechatServerApi a) {
        Group g = this.group;
        if (g != null && findGroup(a) != null) {
            return g;
        }
        g = findGroup(a);
        if (g == null) {
            Group.Builder builder = a.groupBuilder()
                    .setId(GROUP_ID)
                    .setName(settings.channelName())
                    .setPersistent(false)
                    .setHidden(true)
                    .setType(settings.isolated() ? Group.Type.ISOLATED : Group.Type.NORMAL);
            try {
                g = builder.build();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("创建语音频道失败：" + ex);
                return null;
            }
        }
        this.group = g;
        return g;
    }

    private void release(UUID id) {
        VoicechatServerApi a = this.api;
        if (a == null) {
            return;
        }
        VoicechatConnection connection = a.getConnectionOf(id);
        if (connection == null) {
            return;
        }
        try {
            if (connection.isInGroup()) {
                connection.setGroup(null);
            }
        } catch (RuntimeException ignored) {
            // 玩家正在断线时 SVC 可能拒绝改组：忽略
        }
    }

    private synchronized void startKeepTask() {
        if (keepTask != null || api == null || !settings.enabled()) {
            return;
        }
        // 每 2 秒兜底一次：语音连接晚到的玩家、被 SVC 踢出频道的玩家都能自动补回
        keepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::keepAlive, 20L, 40L);
    }

    private synchronized void stopKeepTask() {
        if (keepTask != null) {
            keepTask.cancel();
            keepTask = null;
        }
    }

    private void keepAlive() {
        if (!settings.enabled() || inGame.isEmpty()) {
            stopKeepTask();
            return;
        }
        for (UUID id : new ArrayList<>(inGame)) {
            if (Bukkit.getPlayer(id) == null) {
                exitById(id);
                continue;
            }
            assign(id);
        }
        if (api != null && group != null && findGroup(api) == null) {
            group = null;
        }
    }

    /**
     * 按固定 ID 在组表里找本局频道。
     * <p>
     * 不能用 {@code api.getGroup()}：voicechat-bukkit-2.6.25 在组不存在时也会返回
     * 内部 delegate 为 null 的 GroupImpl，setGroup 时直接 NPE。
     */
    private static Group findGroup(VoicechatServerApi a) {
        try {
            for (Group group : a.getGroups()) {
                if (group != null && GROUP_ID.equals(group.getId())) {
                    return group;
                }
            }
        } catch (RuntimeException ignored) {
            // 老版本组表异常时当作没有，交给调用方重建
        }
        return null;
    }

    private void exitById(UUID id) {
        inGame.remove(id);
        release(id);
    }

    // ---------- 锁死频道 ----------

    private void onCreateGroup(CreateGroupEvent event) {
        if (blocked(event.getConnection())) {
            deny(event);
        }
    }

    private void onJoinGroup(JoinGroupEvent event) {
        if (!blocked(event.getConnection())) {
            return;
        }
        // 加入的就是本局频道（包括我们自己拉人进来）→ 放行
        if (isOurGroup(event.getGroup())) {
            return;
        }
        deny(event);
    }

    private void onLeaveGroup(LeaveGroupEvent event) {
        if (!blocked(event.getConnection())) {
            return;
        }
        if (!isOurGroup(event.getGroup())) {
            return;
        }
        deny(event);
    }

    /** 该连接对应的玩家是否正处于"游戏内且锁死频道"状态。 */
    private boolean blocked(VoicechatConnection connection) {
        if (disbanding || !settings.lock() || !settings.enabled()) {
            return false;
        }
        UUID id = playerId(connection);
        return id != null && inGame.contains(id);
    }

    private static UUID playerId(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) {
            return null;
        }
        return connection.getPlayer().getUuid();
    }

    private static boolean isOurGroup(Group g) {
        return g != null && GROUP_ID.equals(g.getId());
    }

    private static void deny(Event event) {
        if (event.isCancellable()) {
            event.cancel();
        }
    }
}
