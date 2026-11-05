package com.murdermystery;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 读取 {@code plugins/MurderMystery/mechanisms.yml}（机关参数，手写、插件不改写）
 * 与 {@code mechanisms-data.yml}（已登记的拉杆 / 传送点，由 /mm teleporter 命令写入）。
 */
public final class MurderMysteryMechanisms {

    private static final String CONFIG_FILE = "mechanisms.yml";
    private static final String DATA_FILE = "mechanisms-data.yml";
    /** 传送器类型 id（以后新增拉杆机关就在 leverTypes 里加自己的类型）。 */
    public static final String TELEPORTER = "teleporter";

    private final MurderMysteryPlugin plugin;
    /** 机关类型 -> 该类型的拉杆坐标；同一张图可以同时放多种拉杆机关。 */
    private final Map<String, List<Location>> leverTypes = new LinkedHashMap<>();
    private final List<Location> teleporterPoints = new ArrayList<>();

    private boolean teleporterEnabled = true;
    private int teleporterCostGold = 5;
    private double teleporterCooldownSeconds = 5.0;
    private int teleporterMinDistance = 3;
    private String teleporterMessage = "&d传送器嗡的一声，你被传到了别处……";
    private String teleporterNoTargetMessage = "&c传送器还没有其他可用的传送点。";
    private String teleporterSound = "ENTITY_ENDERMAN_TELEPORT";

    public MurderMysteryMechanisms(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
        leverTypes.put(TELEPORTER, new ArrayList<>());
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(configFile);
        teleporterEnabled = yml.getBoolean("teleporter.enabled", true);
        teleporterCostGold = Math.max(0, yml.getInt("teleporter.cost-gold", 5));
        teleporterCooldownSeconds = Math.max(0.0, yml.getDouble("teleporter.cooldown-seconds", 5.0));
        teleporterMinDistance = Math.max(0, yml.getInt("teleporter.min-distance", 3));
        teleporterMessage = yml.getString("teleporter.message", teleporterMessage);
        teleporterNoTargetMessage = yml.getString("teleporter.no-target-message", teleporterNoTargetMessage);
        teleporterSound = yml.getString("teleporter.sound", teleporterSound);
        loadData();
    }

    private void loadData() {
        for (List<Location> list : leverTypes.values()) {
            list.clear();
        }
        teleporterPoints.clear();
        File dataFile = new File(plugin.getDataFolder(), DATA_FILE);
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection leverRoot = yml.getConfigurationSection("levers");
        if (leverRoot != null) {
            for (String type : leverRoot.getKeys(false)) {
                List<Location> list = leverTypes.computeIfAbsent(type.toLowerCase(Locale.ROOT),
                        k -> new ArrayList<>());
                list.addAll(readLocations(yml, "levers." + type));
            }
        }
        teleporterPoints.addAll(readLocations(yml, "teleporter.points"));
    }

    private List<Location> readLocations(YamlConfiguration yml, String path) {
        List<Location> out = new ArrayList<>();
        List<?> list = yml.getList(path);
        if (list == null) {
            return out;
        }
        for (Object o : list) {
            if (o instanceof Location loc) {
                out.add(loc);
            }
        }
        return out;
    }

    /** 登记/注销拉杆或传送点后立刻落盘（只写数据文件，不碰手写的 mechanisms.yml）。 */
    public void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection leverRoot = yml.createSection("levers");
        for (Map.Entry<String, List<Location>> entry : leverTypes.entrySet()) {
            leverRoot.set(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        yml.set("teleporter.points", new ArrayList<>(teleporterPoints));
        try {
            yml.save(new File(plugin.getDataFolder(), DATA_FILE));
        } catch (IOException ex) {
            plugin.getLogger().warning("保存机关数据失败：" + ex.getMessage());
        }
    }

    public boolean teleporterEnabled() {
        return teleporterEnabled;
    }

    /** 每次使用传送器扣除的金锭数量（0 = 免费）。 */
    public int teleporterCostGold() {
        return teleporterCostGold;
    }

    public double teleporterCooldownSeconds() {
        return teleporterCooldownSeconds;
    }

    public int teleporterMinDistance() {
        return teleporterMinDistance;
    }

    public String teleporterMessage() {
        return teleporterMessage;
    }

    public String teleporterNoTargetMessage() {
        return teleporterNoTargetMessage;
    }

    public String teleporterSound() {
        return teleporterSound;
    }

    /** 只要传送器类型的拉杆（/mm teleporter list 用）。 */
    public List<Location> teleporterLevers() {
        return new ArrayList<>(leverTypes.getOrDefault(TELEPORTER, List.of()));
    }

    public List<Location> teleporterPoints() {
        return new ArrayList<>(teleporterPoints);
    }

    /** 这个拉杆属于哪种机关（teleporter / 以后新增的类型）；没登记返回 null。 */
    public String leverTypeAt(Location loc) {
        for (Map.Entry<String, List<Location>> entry : leverTypes.entrySet()) {
            for (Location lever : entry.getValue()) {
                if (sameBlock(lever, loc)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public boolean isLeverOfType(String type, Location loc) {
        return type != null && type.equalsIgnoreCase(leverTypeAt(loc));
    }

    /** 登记一根拉杆，并记录它属于哪种机关（不同类型可以共存在同一张图里）。 */
    public boolean addLever(String type, Location loc) {
        if (type == null || type.isBlank()) {
            return false;
        }
        String key = type.toLowerCase(Locale.ROOT);
        if (leverTypeAt(loc) != null) {
            return false; // 一根拉杆只能绑定一种机关
        }
        leverTypes.computeIfAbsent(key, k -> new ArrayList<>()).add(loc);
        saveData();
        return true;
    }

    /** 注销拉杆（不管它属于哪种机关）。 */
    public boolean removeLever(Location loc) {
        for (List<Location> list : leverTypes.values()) {
            for (int i = 0; i < list.size(); i++) {
                if (sameBlock(list.get(i), loc)) {
                    list.remove(i);
                    saveData();
                    return true;
                }
            }
        }
        return false;
    }

    public void addPoint(Location loc) {
        teleporterPoints.add(loc);
        saveData();
    }

    /** 按 1 开始的序号删除传送点；越界返回 false。 */
    public boolean removePoint(int indexOneBased) {
        int i = indexOneBased - 1;
        if (i < 0 || i >= teleporterPoints.size()) {
            return false;
        }
        teleporterPoints.remove(i);
        saveData();
        return true;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
