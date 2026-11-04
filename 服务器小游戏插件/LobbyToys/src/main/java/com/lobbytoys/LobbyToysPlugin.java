package com.lobbytoys;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyToysPlugin extends JavaPlugin {

    private Settings settings;
    private ToyRegistry registry;
    private CloneManager clones;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        registry = new ToyRegistry(getConfig());

        ToyItems items = new ToyItems(this, registry);
        clones = new CloneManager(this);
        ToyListener listener = new ToyListener(this, clones, items);
        getServer().getPluginManager().registerEvents(listener, this);
        ToyMenu menu = new ToyMenu(this, items);
        getServer().getPluginManager().registerEvents(menu, this);

        ToyCommand command = new ToyCommand(this, clones, items, menu);
        PluginCommand pluginCommand = getCommand("lobbytoys");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        Bukkit.getScheduler().runTaskTimer(this, listener::refillAll, 100L, 100L);

        getLogger().info("主城小玩具（LobbyToys）v" + getPluginMeta().getVersion() + " 已启用。");
    }

    @Override
    public void onDisable() {
        if (clones != null) {
            clones.shutdown();
        }
    }

    public Settings settings() {
        return settings;
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
        registry.reload(getConfig());
    }
}
