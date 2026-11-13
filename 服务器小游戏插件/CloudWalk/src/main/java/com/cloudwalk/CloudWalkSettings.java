package com.cloudwalk;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** config.yml 的只读快照。 */
public final class CloudWalkSettings {

    /** 默认方块池：16 色羊毛 + 16 色带釉陶瓦。 */
    public static final List<Material> DEFAULT_PALETTE = List.of(
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL,
            Material.WHITE_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA,
            Material.MAGENTA_GLAZED_TERRACOTTA, Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
            Material.YELLOW_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
            Material.PINK_GLAZED_TERRACOTTA, Material.GRAY_GLAZED_TERRACOTTA,
            Material.LIGHT_GRAY_GLAZED_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA,
            Material.PURPLE_GLAZED_TERRACOTTA, Material.BLUE_GLAZED_TERRACOTTA,
            Material.BROWN_GLAZED_TERRACOTTA, Material.GREEN_GLAZED_TERRACOTTA,
            Material.RED_GLAZED_TERRACOTTA, Material.BLACK_GLAZED_TERRACOTTA);

    public String regionWorld = "";
    public int x1;
    public int y1;
    public int z1;
    public int x2;
    public int y2;
    public int z2;

    public String startWorld = "";
    public int startX;
    public int startY;
    public int startZ;

    public Location lobby;

    public String axis = "x";
    public int laneGap = 3;
    public int heightStep = 2;
    public int maxLanes = 12;

    public int[] distances = {3, 4};
    public int[] heights = {-2, -1, 0, 1};
    public int queueLength = 3;
    public boolean allow4JumpUp = false;
    public List<Material> palette = new ArrayList<>(DEFAULT_PALETTE);

    public boolean sound = true;
    public boolean particle = true;

    public int topSize = 10;

    public static CloudWalkSettings load(FileConfiguration cfg) {
        CloudWalkSettings s = new CloudWalkSettings();

        ConfigurationSection region = cfg.getConfigurationSection("region");
        if (region != null) {
            s.regionWorld = region.getString("world", "");
            s.x1 = region.getInt("x1");
            s.y1 = region.getInt("y1");
            s.z1 = region.getInt("z1");
            s.x2 = region.getInt("x2");
            s.y2 = region.getInt("y2");
            s.z2 = region.getInt("z2");
        }

        ConfigurationSection start = cfg.getConfigurationSection("start");
        if (start != null) {
            s.startWorld = start.getString("world", "");
            s.startX = start.getInt("x");
            s.startY = start.getInt("y");
            s.startZ = start.getInt("z");
        }

        s.lobby = readLocation(cfg.getConfigurationSection("lobby"));

        s.axis = "z".equalsIgnoreCase(cfg.getString("lane.axis", "x")) ? "z" : "x";
        s.laneGap = Math.max(1, cfg.getInt("lane.gap", 3));
        s.heightStep = Math.max(0, cfg.getInt("lane.height-step", 2));
        s.maxLanes = Math.max(1, Math.min(64, cfg.getInt("lane.max", 12)));

        List<Integer> dists = cfg.getIntegerList("path.distances");
        if (dists != null && !dists.isEmpty()) {
            s.distances = dists.stream()
                    .filter(d -> d != null && d >= 1 && d <= 8)
                    .mapToInt(Integer::intValue)
                    .toArray();
        }
        if (s.distances.length == 0) {
            s.distances = new int[]{3, 4};
        }

        s.queueLength = Math.max(1, Math.min(6, cfg.getInt("path.queue-length", 3)));
        s.allow4JumpUp = cfg.getBoolean("path.allow4-jump-up", false);

        List<Integer> heights = cfg.getIntegerList("path.heights");
        if (heights != null && !heights.isEmpty()) {
            s.heights = heights.stream()
                    .filter(h -> h != null && h >= -8 && h <= 8)
                    .mapToInt(Integer::intValue)
                    .toArray();
        }
        if (s.heights.length == 0) {
            s.heights = new int[]{-2, -1, 0, 1};
        }

        List<String> blockNames = cfg.getStringList("path.blocks");
        List<Material> palette = new ArrayList<>();
        for (String name : blockNames) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock() && !palette.contains(material)) {
                palette.add(material);
            }
        }
        if (palette.isEmpty()) {
            palette.addAll(DEFAULT_PALETTE);
        }
        s.palette = palette;

        s.sound = cfg.getBoolean("run.sound", true);
        s.particle = cfg.getBoolean("run.particle", true);

        s.topSize = Math.max(1, Math.min(50, cfg.getInt("top-size", 10)));
        return s;
    }

    static Location readLocation(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String worldName = sec.getString("world", "");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }
}
