package com.hub;

import com.hub.data.PlayerStats;
import com.hub.util.SetupMode;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MiniGameLib —— 小游戏公共库。
 * <p>
 * 本身不注册命令、不碰游戏逻辑，只向 6 个小游戏插件提供公共能力：
 * <ul>
 *   <li>{@link com.hub.api.HubAPI}：与主城插件（HubCore）对接的静态 API 与数据契约；</li>
 *   <li>{@link com.hub.util.Chat}：颜色/标题/动作栏消息工具；</li>
 *   <li>{@link com.hub.util.SetupMode}：OP 建场模式（备份背包 + 带标记的配置工具）；</li>
 *   <li>{@link com.hub.util.WaitCountdown}：等待室自动开局倒计时。</li>
 * </ul>
 * 加载顺序：本插件 ← HubCore ← 各小游戏插件（靠 plugin.yml 的 depend 保证）。
 */
public class MiniGameLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MiniGameLib 已启用（公共库：HubAPI / Chat / SetupMode / WaitCountdown / PlayerStats）。");
        // 唯一的玩家记录文件（各小游戏共用一个）：启动日志就是“线上有没有带对文件”的证据
        getLogger().info("玩家记录 " + PlayerStats.file().getPath() + " -> " + PlayerStats.summary());
    }

    @Override
    public void onDisable() {
        // 关服兜底：把还停在建场模式里的 OP 背包还原回去
        SetupMode.exitAllRegistered();
    }
}
