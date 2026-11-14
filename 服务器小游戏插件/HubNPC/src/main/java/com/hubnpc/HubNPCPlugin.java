package com.hubnpc;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** HubNPC：主城可生成、可设皮肤、右键执行指令的 NPC。 */
public final class HubNPCPlugin extends JavaPlugin {

    private NpcManager manager;
    private SkinService skins;

    @Override
    public void onEnable() {
        try {
            skins = new SkinService(this);
        } catch (Throwable t) {
            skins = null;
            getLogger().warning("未加载 SkinsRestorer 皮肤接口：" + t.getMessage());
        }

        manager = new NpcManager(this);
        manager.load();

        NpcCommand command = new NpcCommand(this, manager);
        PluginCommand pluginCommand = getCommand("npc");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new NpcListener(manager), this);
        manager.reapplyLoaded();

        getLogger().info("HubNPC 已启用，共 " + manager.size() + " 个 NPC"
                + (skins != null && skins.available() ? "，已连接 SkinsRestorer。" : "。"));
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
        }
    }

    SkinService skins() {
        return skins;
    }
}
