package com.bombardmentzone;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 轰炸区主插件。 */
public final class BombardmentZonePlugin extends JavaPlugin {

    private BombardmentZoneManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new BombardmentZoneManager(this);
        manager.load();

        BombardmentZoneCommand command = new BombardmentZoneCommand(this, manager);
        PluginCommand bombard = getCommand("bombard");
        if (bombard != null) {
            bombard.setExecutor(command);
            bombard.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new BombardmentZoneListener(this, manager), this);
        HubAPI.registerLobbyHandler(player -> manager.leave(player, true));

        getLogger().info("轰炸区（BombardmentZone）已启用。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public BombardmentZoneManager manager() {
        return manager;
    }
}
