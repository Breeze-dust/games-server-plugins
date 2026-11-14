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

    /** 一个难度：名字、是否开放、生成参数、对应头衔。 */
    public static final class Difficulty {
        public final String id;
        public final String name;
        public final String color;
        public final boolean enabled;
        public final boolean allow4JumpUp;
        /** 是否加入距离 5（空 4 格）；原版跳不到，留给跳跃提升。 */
        public final boolean allow4Far;
        public final int[] distances;
        public final String titleId;
        public final String titleName;
        public final String titleColor;
        public final String titleDesc;
        /** 0=简单 1=中等 2=困难；越大越难。 */
        public final int order;

        public Difficulty(String id, String name, String color, boolean enabled, boolean allow4JumpUp,
                          boolean allow4Far, int[] distances, String titleId, String titleName,
                          String titleColor, String titleDesc, int order) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.enabled = enabled;
            this.allow4JumpUp = allow4JumpUp;
            this.allow4Far = allow4Far;
            this.distances = distances;
            this.titleId = titleId;
            this.titleName = titleName;
            this.titleColor = titleColor;
            this.titleDesc = titleDesc;
            this.order = order;
        }
    }

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

    public int[] heights = {-2, -1, 0, 1};
    public int queueLength = 3;
    public double turnChance = 0.25;
    /** 开局前这么多块不允许出现「水平4 + 上升1」。 */
    public int specialFirstBlocks = 3;
    public List<Material> palette = new ArrayList<>(DEFAULT_PALETTE);

    public boolean sound = true;
    public boolean particle = true;

    public int topSize = 10;
    public List<Difficulty> difficulties = new ArrayList<>();
    public String defaultDifficulty = "medium";

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

        int[] baseDistances = {3, 4};
        List<Integer> dists = cfg.getIntegerList("path.distances");
        if (dists != null && !dists.isEmpty()) {
            int[] parsed = dists.stream()
                    .filter(d -> d != null && d >= 1 && d <= 8)
                    .mapToInt(Integer::intValue)
                    .toArray();
            if (parsed.length > 0) {
                baseDistances = parsed;
            }
        }

        s.queueLength = Math.max(1, Math.min(6, cfg.getInt("path.queue-length", 3)));
        s.turnChance = Math.max(0.0, Math.min(1.0, cfg.getDouble("path.turn-chance", 0.25)));
        s.specialFirstBlocks = Math.max(0, Math.min(20, cfg.getInt("path.special-first-blocks", 3)));

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

        ConfigurationSection diffSec = cfg.getConfigurationSection("difficulties");
        s.difficulties.add(readDifficulty("easy", diffSec == null ? null : diffSec.getConfigurationSection("easy"), baseDistances));
        s.difficulties.add(readDifficulty("medium", diffSec == null ? null : diffSec.getConfigurationSection("medium"), baseDistances));
        s.difficulties.add(readDifficulty("hard", diffSec == null ? null : diffSec.getConfigurationSection("hard"), baseDistances));
        s.defaultDifficulty = cfg.getString("leaderboard.default-difficulty", "medium");

        s.sound = cfg.getBoolean("run.sound", true);
        s.particle = cfg.getBoolean("run.particle", true);

        s.topSize = Math.max(1, Math.min(50, cfg.getInt("top-size", 10)));
        return s;
    }

    public Difficulty difficulty(String id) {
        for (Difficulty difficulty : difficulties) {
            if (difficulty.id.equalsIgnoreCase(id)) {
                return difficulty;
            }
        }
        return null;
    }

    public Difficulty defaultDifficulty() {
        Difficulty difficulty = difficulty(defaultDifficulty);
        if (difficulty != null) {
            return difficulty;
        }
        for (Difficulty candidate : difficulties) {
            if (candidate.enabled) {
                return candidate;
            }
        }
        return difficulties.get(0);
    }

    private static Difficulty readDifficulty(String id, ConfigurationSection sec, int[] baseDistances) {
        Difficulty def = defaultDifficulty(id, baseDistances);
        if (sec == null) {
            return def;
        }
        int[] distances = def.distances;
        List<Integer> parsed = sec.getIntegerList("distances");
        if (parsed != null && !parsed.isEmpty()) {
            int[] values = parsed.stream()
                    .filter(d -> d != null && d >= 1 && d <= 8)
                    .mapToInt(Integer::intValue)
                    .toArray();
            if (values.length > 0) {
                distances = values;
            }
        }
        boolean allow4Far = sec.getBoolean("allow-4-far", def.allow4Far);
        if (allow4Far) {
            boolean present = false;
            for (int distance : distances) {
                if (distance == 5) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                int[] extended = new int[distances.length + 1];
                System.arraycopy(distances, 0, extended, 0, distances.length);
                extended[distances.length] = 5;
                distances = extended;
            }
        }
        return new Difficulty(
                id,
                sec.getString("name", def.name),
                sec.getString("color", def.color),
                sec.getBoolean("enabled", def.enabled),
                sec.getBoolean("allow4-jump-up", def.allow4JumpUp),
                allow4Far,
                distances,
                sec.getString("title-id", def.titleId),
                sec.getString("title-name", def.titleName),
                sec.getString("title-color", def.titleColor),
                sec.getString("title-desc", def.titleDesc),
                def.order);
    }

    private static Difficulty defaultDifficulty(String id, int[] baseDistances) {
        return switch (id) {
            case "easy" -> new Difficulty("easy", "简单", "&a", true, false, false, baseDistances,
                    "cloudwalk_easy_bridge", "漫步新手", "&a", "云中漫步：简单榜前十", 0);
            case "medium" -> new Difficulty("medium", "中等", "&e", true, true, false, baseDistances,
                    "cloudwalk_super_bridge", "超级搭路", "&b", "云中漫步：中等榜前十", 1);
            default -> new Difficulty("hard", "困难", "&c", false, true, true, new int[]{3, 4},
                    "cloudwalk_hard_bridge", "云端宗师", "&c", "云中漫步：困难榜前十", 2);
        };
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
