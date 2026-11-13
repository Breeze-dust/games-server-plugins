package com.cloudwalk;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 云中漫步：主城随时加入的无尽跑酷。 */
public class CloudWalkPlugin extends JavaPlugin {

    private CloudWalkManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new CloudWalkManager(this);
        manager.load();

        CloudWalkCommand command = new CloudWalkCommand(this);
        PluginCommand pluginCommand = getCommand("cloudwalk");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new CloudWalkListener(this), this);
        HubAPI.registerLobbyHandler(manager::exitForLobby);

        getLogger().info("CloudWalk 云中漫步已启用，/cloudwalk help 查看帮助。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.onDisable();
        }
    }

    public CloudWalkManager getManager() {
        return manager;
    }
}
