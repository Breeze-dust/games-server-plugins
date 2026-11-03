package com.redlight;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 一二三木头人主插件。 */
public final class RedLightPlugin extends JavaPlugin {

    private RedLightManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new RedLightManager(this);
        manager.load();

        RedLightCommand command = new RedLightCommand(this, manager);
        PluginCommand redlight = getCommand("redlight");
        if (redlight != null) {
            redlight.setExecutor(command);
            redlight.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new RedLightListener(this, manager), this);
        HubAPI.registerLobbyHandler(player -> manager.leave(player, true));

        getLogger().info("一二三木头人（RedLight）已启用。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public RedLightManager manager() {
        return manager;
    }
}
