package com.superniuma;

import com.superniuma.arena.ArenaManager;
import com.superniuma.command.SnCommand;
import com.superniuma.game.RoomManager;
import com.superniuma.listener.GameListener;
import com.superniuma.mechanism.MechanismManager;
import com.superniuma.mechanism.MechanismRegistry;
import com.superniuma.menu.MenuListener;
import com.hub.util.StartCountdown;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** 超级牛马主插件。 */
public final class SuperNiumaPlugin extends JavaPlugin {

    private ArenaManager arenas;
    private MechanismRegistry mechanisms;
    private MechanismManager mechanismManager;
    private RoomManager rooms;
    private NamespacedKey itemKey;
    private StartCountdown startCountdown;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "mechanisms.yml").exists()) {
            saveResource("mechanisms.yml", false);
        }

        itemKey = new NamespacedKey(this, "sn_item");

        arenas = new ArenaManager(this);
        arenas.load();

        mechanisms = new MechanismRegistry(this);
        mechanisms.load();

        mechanismManager = new MechanismManager(this);
        rooms = new RoomManager(this);
        startCountdown = new StartCountdown(this);

        PluginCommand command = getCommand("sn");
        if (command != null) {
            SnCommand executor = new SnCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        com.hub.api.HubAPI.registerLobbyHandler(player -> rooms.leave(player, true));

        getLogger().info("超级牛马已启用（机关 " + mechanisms.enabled().size() + " 种）。");
    }

    @Override
    public void onDisable() {
        if (startCountdown != null) {
            startCountdown.cancelAll();
        }
        if (rooms != null) {
            rooms.shutdown();
        }
        if (mechanismManager != null) {
            mechanismManager.shutdown();
        }
        if (arenas != null) {
            arenas.save();
        }
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public MechanismRegistry mechanisms() {
        return mechanisms;
    }

    public MechanismManager mechanismManager() {
        return mechanismManager;
    }

    public RoomManager rooms() {
        return rooms;
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }

    public StartCountdown startCountdown() {
        return startCountdown;
    }
}
