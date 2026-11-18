package com.liarbar;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 骗子酒馆主插件。 */
public class LiarBarPlugin extends JavaPlugin {

    private LiarBarManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new LiarBarManager(this);
        manager.load();

        LiarBarCommand command = new LiarBarCommand(this);
        PluginCommand pc = getCommand("liarbar");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        } else {
            getLogger().warning("plugin.yml 里没有 liarbar 命令，/liarbar 不可用。");
        }
        getServer().getPluginManager().registerEvents(new LiarBarListener(this), this);

        HubAPI.registerGame(getName(), LiarBarManager.GAME, "BELL", "4");
        HubAPI.registerLobbyHandler(player -> manager.leave(player, true));

        // 兜底：每隔 1 秒重新发送手牌伪装，防止客户端在收牌/换手时看到真实牌面。
        getServer().getScheduler().runTaskTimer(this, manager::refreshDisguises, 20L, 20L);
        // 每 tick 补发一次伪装：切换手持物品时原版会先同步真实道具，下一 tick 立刻盖回去。
        getServer().getScheduler().runTaskTimer(this, manager::tickDisguises, 1L, 1L);
        // 准备状态变化不会回调到插件，这里定期检查每桌是否满足开局条件（含 2~3 人全员准备）。
        getServer().getScheduler().runTaskTimer(this, manager::tickTables, 10L, 10L);

        getLogger().info("LiarBar 已启用（/liarbar，别名 /lb /liars），已加载 "
                + manager.tables().size() + " 张桌子。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public LiarBarManager getManager() {
        return manager;
    }
}
