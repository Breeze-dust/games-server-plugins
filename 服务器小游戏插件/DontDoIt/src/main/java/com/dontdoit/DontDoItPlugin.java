package com.dontdoit;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 不要做挑战（DontDoIt）主插件。 */
public class DontDoItPlugin extends JavaPlugin {

    private DontDoItManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new DontDoItManager(this);
        manager.load();

        DontDoItCommand command = new DontDoItCommand(this, manager);
        PluginCommand cmd = getCommand("dontdoit");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new DontDoItListener(this, manager), this);

        // 主城回城 / /hub 时把玩家踢出本游戏（silent=true：不在场中时不刷提示）
        HubAPI.registerLobbyHandler(p -> manager.leave(p, true));
        // 兜底登记（HubCore 正常会按 plugin.yml 的 minigame 段自动扫描）
        HubAPI.registerGame(getName(), DontDoItManager.GAME, "NAME_TAG", "d");

        getLogger().info("不要做挑战（DontDoIt）已启用，场地 " + manager.arenas().size()
                + " 个，词条 " + manager.library().all().size() + " 条。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public DontDoItManager getManager() {
        return manager;
    }
}
