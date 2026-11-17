package com.archery;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** config.yml 的只读快照。 */
public final class ArcherySettings {

    public String startWorld = "";
    public double startX;
    public double startY;
    public double startZ;
    public float startYaw;
    public float startPitch;

    public int gameSeconds = 60;
    public int bonusMoving = 1;
    public int bonusFast = 2;
    public int bonusSmall = 2;
    public boolean sound = true;
    public boolean particle = true;
    public int topSize = 10;
    public String defaultDifficultyId = "normal";
    public final List<Difficulty> difficulties = new ArrayList<>();

    /** 一个难度共用的靶子生成参数（可在难度自己的 targets 段覆盖）。 */
    public static final class TargetGen {
        public final int safetyMax;
        public final int respawnDelayTicks;
        public final int lifetimeSeconds;
        public final double minSpeed;
        public final double maxSpeed;
        public final double fastSpeed;
        public final double smallScale;
        public final Material fixedBlock;
        public final Material movingBlock;
        public final Material fastBlock;
        public final Color smallGlowColor;

        public TargetGen(int safetyMax, int respawnDelayTicks, int lifetimeSeconds,
                         double minSpeed, double maxSpeed, double fastSpeed, double smallScale,
                         Material fixedBlock, Material movingBlock, Material fastBlock,
                         Color smallGlowColor) {
            this.safetyMax = safetyMax;
            this.respawnDelayTicks = respawnDelayTicks;
            this.lifetimeSeconds = lifetimeSeconds;
            this.minSpeed = minSpeed;
            this.maxSpeed = maxSpeed;
            this.fastSpeed = fastSpeed;
            this.smallScale = smallScale;
            this.fixedBlock = fixedBlock;
            this.movingBlock = movingBlock;
            this.fastBlock = fastBlock;
            this.smallGlowColor = smallGlowColor;
        }
    }

    /** 一个难度：显示名、颜色、是否开放、生成参数、独立区域列表。 */
    public static final class Difficulty {
        public final String id;
        public final String name;
        public final String color;
        public final boolean enabled;
        public final TargetGen gen;
        public final List<TargetRegion> regions;
        public final int order;

        public Difficulty(String id, String name, String color, boolean enabled,
                          TargetGen gen, List<TargetRegion> regions, int order) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.enabled = enabled;
            this.gen = gen;
            this.regions = regions;
            this.order = order;
        }
    }

    public static ArcherySettings load(FileConfiguration cfg) {
        ArcherySettings s = new ArcherySettings();

        ConfigurationSection start = cfg.getConfigurationSection("start");
        if (start != null) {
            s.startWorld = start.getString("world", "");
            s.startX = start.getDouble("x", 0.0);
            s.startY = start.getDouble("y", 0.0);
            s.startZ = start.getDouble("z", 0.0);
            s.startYaw = (float) start.getDouble("yaw", 0.0);
            s.startPitch = (float) start.getDouble("pitch", 0.0);
        }

        s.gameSeconds = (int) clamp(cfg.getInt("game.time-seconds", 60), 10, 3600);
        s.bonusMoving = (int) clamp(cfg.getInt("targets.bonuses.moving", 1), 0, 1000);
        s.bonusFast = (int) clamp(cfg.getInt("targets.bonuses.fast", 2), 0, 1000);
        s.bonusSmall = (int) clamp(cfg.getInt("targets.bonuses.small", 2), 0, 1000);
        s.sound = cfg.getBoolean("effects.sound", true);
        s.particle = cfg.getBoolean("effects.particle", true);
        s.topSize = (int) clamp(cfg.getInt("top-size", 10), 1, 50);
        s.defaultDifficultyId = cfg.getString("leaderboard.default-difficulty", "normal");

        TargetGen global = readGen(cfg.getConfigurationSection("targets"), defaultGen());
        ConfigurationSection diffRoot = cfg.getConfigurationSection("difficulties");
        s.difficulties.add(readDifficulty("normal", diffRoot, global, cfg));
        s.difficulties.add(readDifficulty("hard", diffRoot, global, cfg));
        s.difficulties.add(readDifficulty("evil", diffRoot, global, cfg));
        return s;
    }

    public Difficulty difficulty(String id) {
        if (id == null) {
            return null;
        }
        for (Difficulty difficulty : difficulties) {
            if (difficulty.id.equalsIgnoreCase(id) || difficulty.name.equals(id)) {
                return difficulty;
            }
        }
        return null;
    }

    public Difficulty defaultDifficulty() {
        Difficulty difficulty = difficulty(defaultDifficultyId);
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

    public Location start() {
        if (startWorld == null || startWorld.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(startWorld);
        if (world == null) {
            return null;
        }
        return new Location(world, startX, startY, startZ, startYaw, startPitch);
    }

    private static Difficulty readDifficulty(String id, ConfigurationSection root,
                                             TargetGen global, FileConfiguration cfg) {
        ConfigurationSection sec = root == null ? null : root.getConfigurationSection(id);
        String name = sec == null ? defaultName(id) : sec.getString("name", defaultName(id));
        String color = sec == null ? defaultColor(id) : sec.getString("color", defaultColor(id));
        boolean enabled = sec == null || sec.getBoolean("enabled", true);
        TargetGen gen = readGen(sec == null ? null : sec.getConfigurationSection("targets"), global);
        List<TargetRegion> regions = readRegions(sec == null ? List.of() : sec.getMapList("regions"));
        // 兼容旧版：顶层 regions 迁移到 normal 难度。
        if (id.equals("normal") && regions.isEmpty()) {
            regions = readRegions(cfg.getMapList("regions"));
        }
        int order = switch (id) {
            case "normal" -> 0;
            case "hard" -> 1;
            default -> 2;
        };
        return new Difficulty(id, name, color, enabled, gen, regions, order);
    }

    private static String defaultName(String id) {
        return switch (id) {
            case "normal" -> "正常";
            case "hard" -> "困难";
            default -> "恶意";
        };
    }

    private static String defaultColor(String id) {
        return switch (id) {
            case "normal" -> "&a";
            case "hard" -> "&e";
            default -> "&c";
        };
    }

    private static TargetGen defaultGen() {
        return new TargetGen(256, 10, 0, 1.5, 6.0, 4.5, 0.5,
                Material.TARGET, Material.REDSTONE_BLOCK, Material.GOLD_BLOCK, Color.RED);
    }

    private static TargetGen readGen(ConfigurationSection sec, TargetGen fallback) {
        if (sec == null) {
            return fallback;
        }
        int safetyMax = (int) clamp(sec.getInt("safety-max", fallback.safetyMax), 1, 1024);
        int respawn = (int) clamp(sec.getInt("respawn-delay-ticks", fallback.respawnDelayTicks), 0, 200);
        int lifetime = (int) clamp(sec.getInt("lifetime-seconds", fallback.lifetimeSeconds), 0, 3600);
        double minSpeed = Math.max(0.0, sec.getDouble("min-speed", fallback.minSpeed));
        double maxSpeed = Math.max(minSpeed, sec.getDouble("max-speed", fallback.maxSpeed));
        double fastSpeed = Math.max(0.0, sec.getDouble("fast-speed", fallback.fastSpeed));
        double smallScale = clamp(sec.getDouble("small-scale", fallback.smallScale), 0.2, 0.9);
        Material fixed = block(sec.getString("blocks.fixed"), fallback.fixedBlock);
        Material moving = block(sec.getString("blocks.moving"), fallback.movingBlock);
        Material fast = block(sec.getString("blocks.fast"), fallback.fastBlock);
        Color glow = glowColor(sec.getString("small-glow-color"), fallback.smallGlowColor);
        return new TargetGen(safetyMax, respawn, lifetime, minSpeed, maxSpeed, fastSpeed,
                smallScale, fixed, moving, fast, glow);
    }

    private static List<TargetRegion> readRegions(List<Map<?, ?>> rawList) {
        List<TargetRegion> out = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            Object worldName = raw.get("world");
            if (worldName == null) {
                continue;
            }
            World world = Bukkit.getWorld(String.valueOf(worldName));
            if (world == null) {
                continue;
            }
            double minX = number(raw.get("min-x"), 0.0);
            double minY = number(raw.get("min-y"), 0.0);
            double minZ = number(raw.get("min-z"), 0.0);
            double maxX = number(raw.get("max-x"), 0.0);
            double maxY = number(raw.get("max-y"), 0.0);
            double maxZ = number(raw.get("max-z"), 0.0);
            if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
                continue;
            }
            int base = (int) clamp(number(raw.get("base-score"), 1.0), 1, 100000);
            int count = (int) clamp(number(raw.get("count"), TargetRegion.DEFAULT_COUNT), 0, 64);
            Map<?, ?> weightsRaw = raw.get("weights") instanceof Map<?, ?> map ? map : Map.of();
            Map<?, ?> guaranteedRaw = raw.get("guaranteed") instanceof Map<?, ?> map ? map : Map.of();
            int[] weights = new int[RegionMetric.values().length];
            int[] minimums = new int[RegionMetric.values().length];
            for (RegionMetric metric : RegionMetric.values()) {
                weights[metric.ordinal()] = (int) clamp(
                        intValue(weightsRaw.get(metric.key), defaultWeight(metric)), 0, 100000);
                minimums[metric.ordinal()] = (int) clamp(
                        intValue(guaranteedRaw.get(metric.key), 0), 0, count);
            }
            TargetAxis axis = TargetAxis.fromKey(string(raw.get("axis"), "x"));
            out.add(new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ, base,
                    count, weights, minimums, axis));
        }
        return out;
    }

    private static int defaultWeight(RegionMetric metric) {
        return switch (metric) {
            case FIXED -> TargetRegion.DEFAULT_WEIGHT_FIXED;
            case MOVING -> TargetRegion.DEFAULT_WEIGHT_MOVING;
            case FAST -> TargetRegion.DEFAULT_WEIGHT_FAST;
            case NORMAL -> TargetRegion.DEFAULT_WEIGHT_NORMAL;
            case SMALL -> TargetRegion.DEFAULT_WEIGHT_SMALL;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static Material block(String name, Material fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material != null && material.isBlock() ? material : fallback;
    }

    private static Color glowColor(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "BLACK" -> Color.BLACK;
            case "BLUE" -> Color.BLUE;
            case "AQUA", "CYAN" -> Color.AQUA;
            case "GREEN" -> Color.GREEN;
            case "LIME" -> Color.LIME;
            case "GRAY", "GREY" -> Color.GRAY;
            case "PURPLE" -> Color.PURPLE;
            case "PINK" -> Color.FUCHSIA;
            case "ORANGE" -> Color.ORANGE;
            case "WHITE" -> Color.WHITE;
            case "YELLOW" -> Color.YELLOW;
            case "RED" -> Color.RED;
            default -> fallback;
        };
    }
}
