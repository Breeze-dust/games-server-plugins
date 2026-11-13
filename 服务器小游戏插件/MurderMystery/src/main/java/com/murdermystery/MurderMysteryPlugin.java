package com.murdermystery;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 密室杀手主插件。 */
public class MurderMysteryPlugin extends JavaPlugin {

    private MurderMysteryManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new MurderMysteryManager(this);
        manager.load();

        MurderMysteryCommand cmd = new MurderMysteryCommand(this);
        PluginCommand mm = getCommand("murdermystery");
        if (mm != null) {
            mm.setExecutor(cmd);
            mm.setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(new MurderMysteryListener(this), this);
        // 玩家执行 /hub 或右键回主城道具时，HubCore 回调这里把玩家踢出游戏（防递归）；
        // silent=true：不在场中时别报"你不在任何场中"（进服/hub 复原会广播给所有游戏插件，否则一次刷三行）
        com.hub.api.HubAPI.registerLobbyHandler(p -> manager.leave(p, true));

        // 先把头衔定义建好，管理/头衔菜单里不用等第一个玩家达成才出现（没装头衔插件时静默跳过）。
        // 依赖 plugin.yml 的 softdepend: PlayerTitles 保证它先启用；没有该依赖时会因尚未注册而无效。
        com.hub.api.GameTitles.ensure(MurderMysteryManager.TITLE_NO_KNIFE,
                MurderMysteryManager.TITLE_NO_KNIFE_NAME,
                MurderMysteryManager.TITLE_NO_KNIFE_COLOR,
                MurderMysteryManager.TITLE_NO_KNIFE_DESC);

        getLogger().info("MurderMystery 已启用。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.cancelAllTasksAndGlow();
            manager.clearAllCorpses();
            manager.restoreAllWorldPvp(); // 对局中关服时把临时开启的 PvP 还原
            manager.saveAll();
        }
    }

    public MurderMysteryManager getManager() {
        return manager;
    }
}
