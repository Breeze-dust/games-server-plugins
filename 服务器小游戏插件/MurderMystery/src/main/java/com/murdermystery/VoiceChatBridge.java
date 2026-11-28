package com.murdermystery;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * 语音集成的最小接口：没装 Simple Voice Chat 时用空实现。
 *
 * <p>VoiceChatHook 直接引用 voicechat-api 的类（内部 Hook implements VoicechatPlugin），
 * 服务端没装 SVC 插件时 JVM 一加载这个类就抛 {@code NoClassDefFoundError: de/maxhenkel/voicechat/api/VoicechatPlugin}，
 * 把整个 MurderMystery 拖到 enable 失败。所以这里只暴露接口，真正实现按需反射加载；
 * 装没装 SVC 都不影响游戏本身。
 */
interface VoiceChatBridge {

    /**
     * 语音配置（{@code config.yml} 的 {@code voice-chat} 段），/mm reload 时整体重读。
     *
     * @param enabled           {@code voice-chat.enabled}：false 时完全不碰语音
     * @param blockGroups       {@code voice-chat.block-groups}：局内是否禁止建组 / 入组
     * @param deadGroup         {@code voice-chat.dead-group.enabled}：淘汰者是否自动进「死人组」
     * @param deadGroupIsolated {@code voice-chat.dead-group.isolated}：死人组是否只听组内（说不到活人）
     */
    record Settings(boolean enabled, boolean blockGroups, boolean deadGroup, boolean deadGroupIsolated) {
    }

    /** 读配置并接入 SVC（没装时什么都不做）。 */
    void load(Settings settings);

    /** 进局：先踢出现有语音组，再标记为局内。 */
    void enter(UUID id);

    /** 正式开局：等待阶段结束，放开的主城公共组重新禁掉，并把玩家踢出公共组。 */
    void play(UUID id);

    /** 离开对局 / 退出服务器 / 结算：解除局内标记，回到可以自由建组的状态。 */
    void exit(UUID id);

    /** 被淘汰：保留局内标记，并拉进「死人组」（跨全图恒定音量的死者频道）。 */
    void eliminated(UUID id);

    /** 关服 / 插件禁用时断开引用。 */
    void shutdown();

    /** 有 SVC 时装真实实现，没有时装空实现；任何加载失败都退回空实现，不影响插件启用。 */
    static VoiceChatBridge create(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().info("未安装 Simple Voice Chat，跳过语音集成（装上后重启服务器即自动生效）。");
            return new Noop();
        }
        try {
            return (VoiceChatBridge) Class.forName("com.murdermystery.VoiceChatHook")
                    .getDeclaredConstructor(Plugin.class)
                    .newInstance(plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Simple Voice Chat 集成加载失败，按未安装处理：" + t);
            return new Noop();
        }
    }

    /** 没装 SVC 时的空实现。 */
    final class Noop implements VoiceChatBridge {
        @Override
        public void load(Settings settings) {
        }

        @Override
        public void enter(UUID id) {
        }

        @Override
        public void play(UUID id) {
        }

        @Override
        public void exit(UUID id) {
        }

        @Override
        public void eliminated(UUID id) {
        }

        @Override
        public void shutdown() {
        }
    }
}
