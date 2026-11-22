package com.hub.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Simple Voice Chat 实现。只在 voicechat 插件已启用时才会被加载。 */
final class SvcBackend implements VoiceChatBackend {

    private final VoicechatServerApi api;
    /** 自建的组：非持久组要等第一个玩家加入时才会登记到服务器，所以本地留一份对象。 */
    private final Map<UUID, Group> groups = new HashMap<>();

    private SvcBackend(VoicechatServerApi api) {
        this.api = api;
    }

    static VoiceChatBackend install() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            throw new IllegalStateException("voicechat 插件没有注册 BukkitVoicechatService");
        }
        VoicechatServerApi[] holder = new VoicechatServerApi[1];
        service.registerPlugin(new VoicechatPlugin() {
            @Override
            public String getPluginId() {
                return "minigamelib";
            }

            @Override
            public void initialize(VoicechatApi api) {
                if (api instanceof VoicechatServerApi server) {
                    holder[0] = server;
                }
            }
        });
        if (holder[0] == null) {
            // SVC 在启用后的下一个 tick 才初始化已注册的插件；这里拿不到说明版本不兼容
            throw new IllegalStateException("voicechat 插件没有回调 initialize()");
        }
        return new SvcBackend(holder[0]);
    }

    @Override
    public UUID createIsolatedGroup(String name) {
        Group group = api.groupBuilder()
                .setName(name)
                .setType(Group.Type.ISOLATED)
                .setPersistent(false)
                .build();
        groups.put(group.getId(), group);
        return group.getId();
    }

    @Override
    public void removeGroup(UUID groupId) {
        Group group = groups.remove(groupId);
        if (group != null) {
            api.removeGroup(group.getId());
        }
    }

    @Override
    public void apply(UUID playerId, UUID groupId) {
        VoicechatConnection connection = api.getConnectionOf(playerId);
        // 没装客户端模组时 isInstalled() 为 false：不建组、也不报错，到点了下次对账再试
        if (connection == null || !connection.isInstalled()) {
            return;
        }
        if (groupId == null) {
            if (connection.isInGroup()) {
                connection.setGroup(null);
            }
            return;
        }
        Group group = groups.get(groupId);
        if (group == null) {
            return;
        }
        Group current = connection.getGroup();
        if (current != null && current.getId().equals(groupId)) {
            return;
        }
        connection.setGroup(group);
    }
}
