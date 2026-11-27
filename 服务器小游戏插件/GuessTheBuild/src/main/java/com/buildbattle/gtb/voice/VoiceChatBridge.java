package com.buildbattle.gtb.voice;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 语音频道桥接（Simple Voice Chat 为可选依赖）。
 *
 * <p>服务器没装 SVC 时 {@link #create(Plugin)} 返回 {@link #NOOP}，本插件其余功能完全不受影响。
 * 真正调用 SVC API 的代码全部在 {@link VoiceChatHook} 里，只有确认 SVC 存在后才会被类加载，
 * 避免 NoClassDefFoundError 把整个插件带崩。</p>
 */
public interface VoiceChatBridge {

    /** 读取配置并（重新）注册到 SVC。 */
    void load(Settings settings);

    /** 玩家进入语音频道（开局对全员调用，中途加入者单独调用）。 */
    void enter(Player p);

    /** 玩家离开语音频道（退出/被踢/断线）。 */
    void exit(Player p);

    /** 解散频道：所有人退出并删除该频道。 */
    void disband();

    /** config.yml 的 voice 段。 */
    record Settings(boolean enabled, boolean lock, boolean isolated, String channelName) {
    }

    VoiceChatBridge NOOP = new VoiceChatBridge() {
        @Override
        public void load(Settings settings) {
        }

        @Override
        public void enter(Player p) {
        }

        @Override
        public void exit(Player p) {
        }

        @Override
        public void disband() {
        }
    };

    /** 从 config.yml 读取 voice 段（缺省时全部走默认值，老配置无需手改）。 */
    static Settings readSettings(Plugin plugin) {
        var cfg = plugin.getConfig();
        String name = cfg.getString("voice.channel-name", "建筑猜猜乐");
        if (name == null || name.isBlank()) {
            name = "建筑猜猜乐";
        }
        return new Settings(
                cfg.getBoolean("voice.enabled", true),
                cfg.getBoolean("voice.lock", true),
                cfg.getBoolean("voice.isolated", true),
                name);
    }

    /** 装了 SVC 就返回真实实现，否则返回空实现（只打一条日志）。 */
    static VoiceChatBridge create(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().info("未安装 Simple Voice Chat，跳过语音频道集成（装上后重启服务器即自动生效）。");
            return NOOP;
        }
        try {
            Class<?> hook = Class.forName("com.buildbattle.gtb.voice.VoiceChatHook");
            return (VoiceChatBridge) hook.getConstructor(Plugin.class).newInstance(plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Simple Voice Chat 语音频道初始化失败，已跳过：" + t);
            return NOOP;
        }
    }
}
