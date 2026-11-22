package com.bridgepractice;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BridgePracticePlugin extends JavaPlugin {

    private BridgePracticeManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new BridgePracticeManager(this);
        manager.load();

        CourseMenu menu = new CourseMenu(manager);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(new BridgePracticeListener(this, manager), this);

        BridgePracticeCommand command = new BridgePracticeCommand(this, manager, menu);
        PluginCommand bridge = getCommand("bridge");
        if (bridge != null) {
            bridge.setExecutor(command);
            bridge.setTabCompleter(command);
        }
        HubAPI.registerLobbyHandler(player -> manager.leave(player, false));

        getLogger().info("BridgePractice（搭路练习）已启用。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public BridgePracticeManager manager() {
        return manager;
    }
}
