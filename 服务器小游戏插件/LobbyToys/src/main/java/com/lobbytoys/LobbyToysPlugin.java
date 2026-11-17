package com.lobbytoys;

import com.lobbytoys.scratch.ScratchListener;
import com.lobbytoys.scratch.ScratchManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyToysPlugin extends JavaPlugin {

    private Settings settings;
    private ToyRegistry registry;
    private CloneManager clones;
    private ShrinkManager shrinks;
    private ScratchManager scratch;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        registry = new ToyRegistry(this, getConfig());

        ToyItems items = new ToyItems(this, registry);
        clones = new CloneManager(this);
        shrinks = new ShrinkManager(this);
        ToyListener listener = new ToyListener(this, clones, shrinks, items);
        getServer().getPluginManager().registerEvents(listener, this);
        ToyMenu menu = new ToyMenu(this, items, clones, shrinks);
        getServer().getPluginManager().registerEvents(menu, this);

        scratch = new ScratchManager(this);
        getServer().getPluginManager().registerEvents(new ScratchListener(scratch), this);
        scratch.start();

        ToyCommand command = new ToyCommand(this, clones, items, menu, scratch);
        PluginCommand pluginCommand = getCommand("lobbytoys");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        Bukkit.getScheduler().runTaskTimer(this, listener::refillAll, 100L, 100L);

        getLogger().info("主城小玩具（LobbyToys）v" + getPluginMeta().getVersion() + " 已启用。");
        if (settings.hideFromOwner) {
            getLogger().info("hide-from-owner=true：召唤者本人看不到自己的分身（F5 第三人称也看不到）；想看到请改成 false。");
        }
    }

    @Override
    public void onDisable() {
        if (clones != null) {
            clones.shutdown();
        }
        if (shrinks != null) {
            shrinks.shutdown();
        }
        if (scratch != null) {
            scratch.shutdown();
        }
    }

    public Settings settings() {
        return settings;
    }

    /**
     * 玩家当前是否在小游戏里（HubCore 上报的游戏状态，跨世界/同世界区域都算）。
     * 云间漫步虽然也上报状态（tab 显示），但它是主城活动：隐藏头衔需要在跑酷中使用缩小药水，
     * 所以这里不把它当成“游戏中”，小玩具照常可用。
     */
    public boolean isPlaying(org.bukkit.entity.Player player) {
        String game = com.hub.api.HubAPI.gameOf(player.getUniqueId());
        if (game == null) {
            return false;
        }
        return !"云中漫步".equals(game) && !"CloudWalk".equalsIgnoreCase(game);
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
        registry.reload(getConfig());
        if (clones != null) {
            clones.refreshVisibility();
        }
        if (scratch != null) {
            scratch.reload();
        }
    }
}
