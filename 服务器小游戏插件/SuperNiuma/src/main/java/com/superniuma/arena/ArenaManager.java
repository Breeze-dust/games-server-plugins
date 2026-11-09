package com.superniuma.arena;

import com.superniuma.SuperNiumaPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 地图的读写（arenas.yml）。 */
public final class ArenaManager {

    private static final String FILE = "arenas.yml";

    private final SuperNiumaPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                continue;
            }
            Arena arena = new Arena(name, sec.getString("world"));
            arena.setSpawn(sec.getLocation("spawn"));
            arena.setStart(sec.getLocation("start"));
            Location b1 = sec.getLocation("build.pos1");
            Location b2 = sec.getLocation("build.pos2");
            if (b1 != null && b2 != null) {
                arena.setBuildCorner(b1, b2);
            }
            Location f1 = sec.getLocation("finish.pos1");
            Location f2 = sec.getLocation("finish.pos2");
            if (f1 != null && f2 != null) {
                arena.setFinishCorner(f1, f2);
            }
            arena.setVoidY(sec.getDouble("void-y", 0.0));
            arenas.put(name, arena);
        }
        plugin.getLogger().info("已加载 " + arenas.size() + " 张超级牛马地图。");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("arenas");
        for (Arena arena : arenas.values()) {
            ConfigurationSection sec = root.createSection(arena.getName());
            sec.set("world", arena.getWorldName());
            sec.set("spawn", arena.getSpawn());
            sec.set("start", arena.getStart());
            sec.set("build.pos1", arena.getBuildMin());
            sec.set("build.pos2", arena.getBuildMax());
            sec.set("finish.pos1", arena.getFinishMin());
            sec.set("finish.pos2", arena.getFinishMax());
            sec.set("void-y", arena.getVoidY());
        }
        try {
            File file = new File(plugin.getDataFolder(), FILE);
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录。");
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 " + FILE + " 失败：" + ex.getMessage());
        }
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name);
    }

    public Arena create(String name, World world) {
        Arena arena = new Arena(name, world.getName());
        arenas.put(name, arena);
        save();
        return arena;
    }

    public boolean delete(String name) {
        boolean removed = arenas.remove(name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public List<Arena> all() {
        return new ArrayList<>(arenas.values());
    }
}
