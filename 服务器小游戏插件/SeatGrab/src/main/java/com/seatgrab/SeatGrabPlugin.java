package com.seatgrab;

import com.hub.api.HubAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 抢位占座主插件。 */
public final class SeatGrabPlugin extends JavaPlugin {

    private SeatGrabManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new SeatGrabManager(this);
        manager.load();

        SeatGrabCommand command = new SeatGrabCommand(this, manager);
        PluginCommand seatgrab = getCommand("seatgrab");
        if (seatgrab != null) {
            seatgrab.setExecutor(command);
            seatgrab.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new SeatGrabListener(this, manager), this);
        HubAPI.registerLobbyHandler(player -> manager.leave(player, true));

        getLogger().info("抢位占座（SeatGrab）已启用。");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public SeatGrabManager manager() {
        return manager;
    }
}
