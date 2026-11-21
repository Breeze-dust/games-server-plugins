package com.lobbytoys;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 登记过的椅子（方块坐标 + 坐下时的朝向），存 {@code plugins/LobbyToys/chairs.yml}。
 * 只有被登记过的方块才允许坐，不再按材质自动识别。
 */
public final class ChairRegistry {

    private final LobbyToysPlugin plugin;
    private final File file;
    /** 坐标键 "world;x;y;z" → 坐下后的朝向（度）。 */
    private final Map<String, Float> chairs = new LinkedHashMap<>();

    public ChairRegistry(LobbyToysPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chairs.yml");
        load();
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    public boolean isChair(Block block) {
        return block != null && chairs.containsKey(key(block));
    }

    /** 只允许在配置的 worlds（主城）里登记。 */
    public boolean isAllowedWorld(org.bukkit.World world) {
        return plugin.settings().isAllowedWorld(world);
    }

    /** 登记时管理员面朝的方向；没登记返回 null。 */
    public Float yawOf(Block block) {
        return block == null ? null : chairs.get(key(block));
    }

    public int size() {
        return chairs.size();
    }

    /** 右键登记 / 取消登记，返回 true = 新增，false = 移除。 */
    public boolean toggle(Block block, float yaw) {
        String key = key(block);
        boolean added;
        if (chairs.remove(key) != null) {
            added = false;
        } else {
            chairs.put(key, normalizeYaw(yaw));
            added = true;
        }
        save();
        return added;
    }

    public boolean remove(Block block) {
        if (block == null || chairs.remove(key(block)) == null) {
            return false;
        }
        save();
        return true;
    }

    private void load() {
        chairs.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String entry : config.getStringList("chairs")) {
            String[] parts = entry.split(";");
            if (parts.length != 5) {
                continue;
            }
            try {
                Integer.parseInt(parts[1]);
                Integer.parseInt(parts[2]);
                Integer.parseInt(parts[3]);
                chairs.put(parts[0] + ";" + parts[1] + ";" + parts[2] + ";" + parts[3],
                        normalizeYaw(Float.parseFloat(parts[4])));
            } catch (NumberFormatException ignored) {
            }
        }
        plugin.getLogger().info("已读取 " + chairs.size() + " 把登记的椅子。");
    }

    private void save() {
        List<String> entries = new ArrayList<>(chairs.size());
        for (Map.Entry<String, Float> e : chairs.entrySet()) {
            entries.add(e.getKey() + ";" + String.format(Locale.ROOT, "%.2f", e.getValue()));
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("chairs", entries);
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录，椅子登记只保存在内存里。");
                return;
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 chairs.yml 失败：" + ex.getMessage());
        }
    }

    /** 统一成 -180..180。 */
    public static float normalizeYaw(float yaw) {
        float result = yaw % 360.0f;
        if (result >= 180.0f) {
            result -= 360.0f;
        }
        if (result < -180.0f) {
            result += 360.0f;
        }
        return result;
    }
}
