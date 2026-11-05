package com.murdermystery;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 {@code plugins/MurderMystery/rooms.yml}：每个场地的金锭刷新逻辑。
 * 没写的项沿用 config.yml 的全局默认值。
 */
public final class MurderMysteryRooms {

    private static final String FILE_NAME = "rooms.yml";

    private final MurderMysteryPlugin plugin;
    private final Map<String, ConfigurationSection> rooms = new LinkedHashMap<>();

    public MurderMysteryRooms(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        rooms.clear();
        ConfigurationSection root = yml.getConfigurationSection("rooms");
        if (root == null) {
            return;
        }
        for (String arena : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(arena);
            if (sec != null) {
                rooms.put(arena, sec);
            }
        }
        plugin.getLogger().info("密室杀手房间配置：已加载 " + rooms.size() + " 个场地的独立金锭设置。");
    }

    private ConfigurationSection section(String arena) {
        return arena == null ? null : rooms.get(arena);
    }

    public boolean spawnEnabled(String arena, boolean def) {
        ConfigurationSection sec = section(arena);
        return sec == null ? def : sec.getBoolean("gold-spawn-enabled", def);
    }

    public int spawnInterval(String arena, int def) {
        ConfigurationSection sec = section(arena);
        return Math.max(1, sec == null ? def : sec.getInt("gold-spawn-interval", def));
    }

    public int spawnAmount(String arena, int def) {
        ConfigurationSection sec = section(arena);
        return Math.max(1, sec == null ? def : sec.getInt("gold-spawn-amount", def));
    }

    public int goldPerDeath(String arena, int def) {
        ConfigurationSection sec = section(arena);
        return Math.max(1, sec == null ? def : sec.getInt("gold-per-death", def));
    }

    public List<String> roomNames() {
        return new ArrayList<>(rooms.keySet());
    }
}
