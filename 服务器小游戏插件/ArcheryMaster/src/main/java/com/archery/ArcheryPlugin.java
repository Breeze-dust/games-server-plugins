package com.archery;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 箭术大师：主城随时加入的限时射箭小游戏。 */
public class ArcheryPlugin extends JavaPlugin {

    private ArcheryManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new ArcheryManager(this);
        manager.load();

        ArcheryCommand command = new ArcheryCommand(this);
        PluginCommand pluginCommand = getCommand("archery");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new ArcheryListener(this), this);
        HubAPI.registerLobbyHandler(manager::exitForLobby);

        getLogger().info("ArcheryMaster 箭术大师已启用，/archery help 查看帮助。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.onDisable();
        }
    }

    public ArcheryManager getManager() {
        return manager;
    }
}
