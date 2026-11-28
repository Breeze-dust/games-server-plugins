package com.hub.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Simple Voice Chat 实现。只在 voicechat 插件已启用时才会被加载。 */
final class SvcBackend implements VoiceChatBackend {

    /** 自建的组：非持久组要等第一个玩家加入时才会登记到服务器，所以本地留一份对象。 */
    private final Map<UUID, Group> groups = new HashMap<>();
    /**
     * SVC 在 onEnable 里只登记插件，真正的 initialize() 要等服务端启动后的下一个 tick。
     * 这里用 volatile 等它回调，MiniGameLib 不会因为拿不到 API 就整个关掉语音。
     */
    private volatile VoicechatServerApi api;

    private SvcBackend() {
    }

    static SvcBackend install() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            throw new IllegalStateException("voicechat 插件没有注册 BukkitVoicechatService");
        }
        SvcBackend backend = new SvcBackend();
        service.registerPlugin(new VoicechatPlugin() {
            @Override
            public String getPluginId() {
                return "minigamelib";
            }

            @Override
            public void initialize(VoicechatApi api) {
                if (api instanceof VoicechatServerApi server) {
                    backend.api = server;
                }
            }
        });
        return backend;
    }

    @Override
    public boolean ready() {
        return api != null;
    }

    @Override
    public UUID createIsolatedGroup(String name) {
        VoicechatServerApi server = api;
        if (server == null) {
            return null;
        }
        Group group = server.groupBuilder()
                .setName(name)
                .setType(Group.Type.ISOLATED)
                .setPersistent(false)
                .build();
        groups.put(group.getId(), group);
        return group.getId();
    }

    @Override
    public UUID createPersistentGroup(UUID groupId, String name) {
        VoicechatServerApi server = api;
        if (server == null) {
            return null;
        }
        Group group = findGroup(server, groupId);
        if (group == null) {
            group = server.groupBuilder()
                    .setId(groupId)
                    .setName(name)
                    .setType(Group.Type.NORMAL)
                    .setPersistent(true)
                    .build();
        }
        groups.put(groupId, group);
        return groupId;
    }

    @Override
    public boolean groupExists(UUID groupId) {
        VoicechatServerApi server = api;
        return server != null && groupId != null && findGroup(server, groupId) != null;
    }

    /**
     * 按 ID 找组。
     * <p>
     * 不能用 {@code api.getGroup()}：voicechat-bukkit-2.6.25 在组不存在时也会返回
     * 内部 delegate 为 null 的 GroupImpl，紧接着的 getId() 会直接 NPE。组表里的都是真组。
     */
    private static Group findGroup(VoicechatServerApi server, UUID groupId) {
        try {
            for (Group group : server.getGroups()) {
                if (group != null && groupId.equals(group.getId())) {
                    return group;
                }
            }
        } catch (RuntimeException ignored) {
            // 老版本组表异常时按“没有这个组”处理，下一 tick 会重试
        }
        return null;
    }

    @Override
    public boolean inGroup(UUID playerId, UUID groupId) {
        VoicechatServerApi server = api;
        if (server == null || groupId == null) {
            return false;
        }
        VoicechatConnection connection = server.getConnectionOf(playerId);
        if (connection == null) {
            return false;
        }
        Group current = connection.getGroup();
        return current != null && groupId.equals(current.getId());
    }

    @Override
    public void removeGroup(UUID groupId) {
        VoicechatServerApi server = api;
        Group group = groups.remove(groupId);
        if (server != null && group != null) {
            server.removeGroup(group.getId());
        }
    }

    @Override
    public boolean apply(UUID playerId, UUID groupId) {
        VoicechatServerApi server = api;
        if (server == null) {
            return false;
        }
        VoicechatConnection connection = server.getConnectionOf(playerId);
        // 刚进服时客户端还没完成语音握手：返回 false，让调用方过一会儿再试
        if (connection == null || !connection.isInstalled()) {
            return false;
        }
        if (groupId == null) {
            if (connection.isInGroup()) {
                connection.setGroup(null);
            }
            return true;
        }
        Group group = groups.get(groupId);
        if (group == null) {
            return true;
        }
        Group current = connection.getGroup();
        if (current != null && current.getId().equals(groupId)) {
            return true;
        }
        connection.setGroup(group);
        return true;
    }
}
