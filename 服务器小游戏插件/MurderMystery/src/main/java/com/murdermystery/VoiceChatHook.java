package com.murdermystery;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import com.hub.voice.VoiceChat;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat（SVC）可选集成：活人用「真实世界麦」，淘汰者进「死人组」。
 *
 * <p>不改 SVC 源码，只用它的官方 Bukkit API（{@code voicechat-api}，provided 依赖）。
 * 软依赖：没装 SVC、或玩家没装 SVC 客户端时，这里全部空转，游戏照常运行。
 *
 * <p>为什么需要这个类：SVC 默认就是距离麦（玩家不在任何语音组时按
 * {@code max_voice_distance} / {@code whisper_distance} 衰减），但**语音组是无方向、恒定音量的**
 * （官方文档：组语音与 static 频道一致）。玩家自己建个组就能跨全场通话，等于开挂。所以：
 * <ol>
 *   <li>进局 / 离场 / 结算时，把玩家踢出现有语音组（{@code setGroup(null)}）；</li>
 *   <li>局内玩家尝试建组 / 入组时取消事件（{@code voice-chat.block-groups}）。</li>
 * </ol>
 *
 * <p>「死人频道」由本类自建：被淘汰时把死者 {@code setGroup(死人组)}，这个组是隐藏的持久组，
 * 组语音恒定额定音量、不受距离限制，所以死者隔着半张图也能商量；组类型默认 {@code ISOLATED}
 * （{@code voice-chat.dead-group.isolated}），死者只听组内、说不到活人，活人也听不见死者。
 *
 * <p><b>为什么还要给死人组留豁免：</b>SVC 的 {@code setGroup(已存在的组)} 走的是
 * {@code ServerGroupManager#joinGroup}，那里会派发 {@link JoinGroupEvent}——正是上面用来禁止
 * 局内入组的同一个事件。不豁免的话，{@code block-groups: true} 时死者会被自己的规则挡在死人组外面。
 *
 * <p><b>为什么还要拦 LeaveGroupEvent：</b>死者自己退出死人组后会掉回「旁观者距离麦」，
 * 而服务端 {@code spectator_interaction=false}（默认值）并不能阻止旁观者的声音被附近的活人听见，
 * 相当于让死者给活人报点。所以死人组是单向门：只能靠 {@code exit()}（离场 / 结算）放出来。
 */
final class VoiceChatHook implements VoiceChatBridge {

    /** 在本服 SVC 插件列表里唯一标识我们，不能和别的插件重名。 */
    private static final String PLUGIN_ID = "murdermystery";

    /**
     * 「死人组」的固定 ID：/mm reload 之后还认得同一个组，不会每局多出一堆同名组。
     * 组本身不落盘（SVC 的 persistent 只保证不被自动回收），重启服务器后按需重建即可。
     */
    private static final UUID DEAD_GROUP_ID = UUID.fromString("8a6f0f4c-4b2a-4f8d-9d3e-2c5b1a7e6f10");
    /** 组名会显示在 SVC 客户端里；必须满足 SVC 的 GROUP_REGEX（无控制字符、不超过 24 字符）。 */
    private static final String DEAD_GROUP_NAME = "死者频道";

    private final Plugin plugin;
    /**
     * 局内玩家（含已淘汰的旁观者）。进局加入，离开 / 退出 / 结算移除。
     * SVC 的事件在它自己的线程上跑，这里必须用并发集合。
     */
    private final Set<UUID> inGame = ConcurrentHashMap.newKeySet();
    /** 还在等待室、尚未正式开局的玩家：这段时间放行主城公共语音组。 */
    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    /** 已淘汰、被拉进「死人组」的玩家；SVC 的事件线程也要读，同样必须并发。 */
    private final Set<UUID> dead = ConcurrentHashMap.newKeySet();
    private volatile VoicechatServerApi api;
    /** 是否已经把 {@link Hook} 注册进 SVC：SVC 不接受同 ID 重复注册，/mm reload 只能改开关不能重注册。 */
    private volatile boolean registered;
    /** {@code voice-chat.block-groups} 的当前值，/mm reload 后热更新（Hook 每次事件读它）。 */
    private volatile boolean blockGroups = true;
    /** {@code voice-chat.dead-group.enabled} 的当前值。 */
    private volatile boolean deadGroup = true;
    /** {@code voice-chat.dead-group.isolated} 的当前值；只在建组时生效（组已存在就沿用它原来的类型）。 */
    private volatile boolean deadGroupIsolated = true;

    VoiceChatHook(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 读配置并接入 SVC。 */
    public void load(Settings settings) {
        blockGroups = settings.enabled() && settings.blockGroups();
        deadGroupIsolated = settings.deadGroupIsolated();
        boolean wantDeadGroup = settings.enabled() && settings.deadGroup();
        if (!wantDeadGroup) {
            // 先把死者标记清掉再退组：否则 release() 里的 setGroup(null) 会被「死者不许退组」拦下
            releaseAllDead();
        }
        deadGroup = wantDeadGroup;
        if (!settings.enabled()) {
            api = null;
            inGame.clear();
            return;
        }
        if (registered) {
            return; // /mm reload：已经接入过，只更新开关（SVC 不接受同 ID 重复注册）
        }
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().info("未安装 Simple Voice Chat，跳过语音集成（装上后重启服务器即自动生效）。");
            return;
        }
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().warning("检测到 voicechat 插件，但取不到 BukkitVoicechatService（版本太旧？），已跳过语音集成。");
            return;
        }
        service.registerPlugin(new Hook());
        registered = true;
    }

    /** 进局：先踢出现有语音组，再标记为局内（等待阶段放行主城公共组）。 */
    public void enter(UUID id) {
        waiting.add(id);
        inGame.add(id);
        dead.remove(id); // 重新进局：先摘掉死者标记，再做「活人的规矩」
        release(id);
    }

    /** 正式开局：等待结束，公共组放行失效，并把玩家从公共组踢回距离麦。 */
    public void play(UUID id) {
        waiting.remove(id);
        inGame.add(id);
        dead.remove(id);
        release(id);
    }

    /** 离开对局 / 退出服务器 / 结算：取消局内标记、摘掉死者标记，并放回自由状态（可以自己建组）。 */
    public void exit(UUID id) {
        waiting.remove(id);
        inGame.remove(id);
        dead.remove(id); // 必须先摘标记：否则下面的 setGroup(null) 会被「死者不许退组」拦掉
        release(id);
    }

    /**
     * 被淘汰：**保留**局内标记（不能让死者建组给活人报点），并拉进「死人组」——
     * 组语音是恒定音量、不受距离限制的，死者之间可以隔着整张图说话。
     *
     * <p>{@code voice-chat.dead-group.enabled: false} 时退回旧行为：只踢出语音组，
     * 靠 SVC 原生的旁观者语音范围。
     */
    public void eliminated(UUID id) {
        if (!deadGroup) {
            release(id);
            return;
        }
        dead.add(id);
        putIntoDeadGroup(id);
    }

    /** 关服 / 插件禁用时断开引用，避免 SVC 还在回调我们。 */
    public void shutdown() {
        api = null;
        inGame.clear();
        waiting.clear();
        dead.clear();
    }

    /** 把玩家踢出当前语音组：null 即「退出当前组」。 */
    private void release(UUID id) {
        VoicechatServerApi a = api;
        if (a == null || id == null) {
            return;
        }
        try {
            VoicechatConnection c = a.getConnectionOf(id);
            if (c != null && c.isInGroup()) {
                c.setGroup(null);
            }
        } catch (IllegalStateException ignored) {
            // SVC 规定不能在「会修改该玩家自身状态」的事件里调 setGroup；
            // 真遇到时它自己会处理，不该让异常冒到服务器主线程把事件打崩。
        }
    }

    /** 把所有人从死人组里放出来（/mm reload 关掉死者组时用）。 */
    private void releaseAllDead() {
        if (dead.isEmpty()) {
            return;
        }
        Set<UUID> ids = Set.copyOf(dead);
        dead.clear();
        ids.forEach(this::release);
    }

    /**
     * 把死者拉进「死人组」。
     *
     * <p>坑：{@code setGroup(已在组表里的组)} 在 SVC 里走 {@code ServerGroupManager#joinGroup}，
     * 那里会派发 {@link JoinGroupEvent}，也就是 {@code block-groups} 用来禁止局内入组的同一个事件——
     * 所以 {@link Hook#onJoinGroup} 里必须给「死人组 + 死者」留一条豁免，否则进不去。
     */
    private void putIntoDeadGroup(UUID id) {
        VoicechatServerApi a = api;
        if (a == null) {
            return;
        }
        try {
            VoicechatConnection c = a.getConnectionOf(id);
            if (c == null) {
                return;
            }
            Group group = deadGroupOf(a);
            if (group != null) {
                c.setGroup(group); // 已经在同一个组里时 SVC 自己会直接 return
            }
        } catch (IllegalStateException ignored) {
            // 同 release()：SVC 不允许在会修改该玩家状态的事件里调 setGroup
        }
    }

    /** 取「死人组」：组表里没有就按当前配置建一个（persistent → 人走光也不会被 SVC 回收）。 */
    private Group deadGroupOf(VoicechatServerApi a) {
        Group existing = findGroup(a);
        if (existing != null) {
            return existing;
        }
        return a.groupBuilder()
                .setId(DEAD_GROUP_ID)
                .setName(DEAD_GROUP_NAME)
                .setPersistent(true) // 不算「持久化到磁盘」，只表示不随人走光被清掉
                .setHidden(true)     // 不出现在客户端的组列表里，活人不会误入
                .setType(deadGroupIsolated ? Group.Type.ISOLATED : Group.Type.NORMAL)
                .build();
    }

    /**
     * 按固定 ID 在组表里找死人组。
     * <p>
     * 不能用 {@code api.getGroup()}：voicechat-bukkit-2.6.25 在组不存在时也会返回
     * 内部 delegate 为 null 的 GroupImpl，setGroup 时直接 NPE。
     */
    private static Group findGroup(VoicechatServerApi a) {
        try {
            for (Group group : a.getGroups()) {
                if (group != null && DEAD_GROUP_ID.equals(group.getId())) {
                    return group;
                }
            }
        } catch (RuntimeException ignored) {
            // 老版本组表异常时当作没有，交给本次重建
        }
        return null;
    }

    private boolean isInGame(VoicechatConnection c) {
        return c != null && c.getPlayer() != null && inGame.contains(c.getPlayer().getUuid());
    }

    private boolean isDead(VoicechatConnection c) {
        return c != null && c.getPlayer() != null && dead.contains(c.getPlayer().getUuid());
    }

    private boolean isWaiting(VoicechatConnection c) {
        return c != null && c.getPlayer() != null && waiting.contains(c.getPlayer().getUuid());
    }

    /** 主城公共语音组（MiniGameLib 建的常驻组）。 */
    private static boolean isHubGroup(Group g) {
        UUID hub = VoiceChat.hubGroupId();
        return g != null && hub != null && hub.equals(g.getId());
    }

    private static boolean isDeadGroup(Group g) {
        return g != null && DEAD_GROUP_ID.equals(g.getId());
    }

    /**
     * 注册进 SVC 的插件对象。SVC 在服务端启动完成后调用 {@link #initialize}，把服务端 API 交给我们；
     * 事件只能在 {@link #registerEvents} 里注册。
     */
    private final class Hook implements VoicechatPlugin {

        @Override
        public String getPluginId() {
            return PLUGIN_ID;
        }

        @Override
        public void initialize(VoicechatApi voicechatApi) {
            if (!(voicechatApi instanceof VoicechatServerApi server)) {
                plugin.getLogger().warning("语音服务不是服务端实现，跳过语音集成。");
                return;
            }
            api = server;
            plugin.getLogger().info("已接入 Simple Voice Chat：局内玩家自动退出语音组，语音按距离衰减"
                    + (blockGroups ? "；局内禁止建组 / 入组" : "")
                    + (deadGroup ? "；淘汰者进「" + DEAD_GROUP_NAME + "」跨全图通话" : "") + "。");
        }

        @Override
        public void registerEvents(EventRegistration registration) {
            registration.registerEvent(JoinGroupEvent.class, this::onJoinGroup);
            registration.registerEvent(CreateGroupEvent.class, this::onCreateGroup);
            registration.registerEvent(LeaveGroupEvent.class, this::onLeaveGroup);
        }

        /**
         * 玩家想加入某个组。
         *
         * <p>局内一律取消（组语音不受距离限制，等于全场通话）；但 MM 自己把死者塞进「死人组」时
         * 走的也是这条路径，必须放行，否则 {@code block-groups: true} 时死者进不了自己的频道。
         */
        private void onJoinGroup(JoinGroupEvent e) {
            if (!blockGroups) {
                return;
            }
            if (isDeadGroup(e.getGroup()) && isDead(e.getConnection())) {
                return; // 死人组豁免
            }
            if (isHubGroup(e.getGroup()) && isWaiting(e.getConnection())) {
                return; // 等待阶段：主城公共组放行；开局 play() 后不再放行
            }
            if (isInGame(e.getConnection())) {
                e.cancel();
            }
        }

        /** 玩家想自己建组：同理取消；但 MM 自己建「死人组」时要放行。 */
        private void onCreateGroup(CreateGroupEvent e) {
            if (!blockGroups) {
                return;
            }
            if (isDeadGroup(e.getGroup()) && (e.getConnection() == null || isDead(e.getConnection()))) {
                return; // 死人组豁免（服务端懒建组时 connection 为 null）
            }
            if (isInGame(e.getConnection())) {
                e.cancel();
            }
        }

        /**
         * 玩家想退出当前组：死者不许退出「死人组」。
         *
         * <p>退出后 SVC 会把他放回旁观者距离麦，而 {@code spectator_interaction=false} 挡不住
         * 「旁观者的声音被附近活人听见」，等于给活人报点。所以只能靠 {@code exit()}（离场 / 结算）放出来——
         * 那条路径里我们先把 UUID 从 {@code dead} 里摘掉，这里就不会再拦。
         */
        private void onLeaveGroup(LeaveGroupEvent e) {
            if (deadGroup && isDeadGroup(e.getGroup()) && isDead(e.getConnection())) {
                e.cancel();
            }
        }
    }
}
