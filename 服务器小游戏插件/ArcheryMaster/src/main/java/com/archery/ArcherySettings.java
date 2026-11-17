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

    /** 全场上限只作安全保护；实际数量由每个区域的 count 决定。 */
    public int safetyMax = 256;
    public int respawnDelayTicks = 10;
    public int targetLifetimeSeconds = 0;
    public double smallChance = 0.35;
    public double minSpeed = 1.5;
    public double maxSpeed = 6.0;
    public double fastSpeed = 4.5;
    public double smallScale = 0.5;

    public Material fixedBlock = Material.TARGET;
    public Material movingBlock = Material.REDSTONE_BLOCK;
    public Material fastBlock = Material.GOLD_BLOCK;
    public Color smallGlowColor = Color.RED;

    /** 三项独立加成：移动 +1、快速 +2、缩小 +2，可叠加。 */
    public int bonusMoving = 1;
    public int bonusFast = 2;
    public int bonusSmall = 2;

    public boolean sound = true;
    public boolean particle = true;
    public int topSize = 10;

    /** 编辑模式划出的长方体区域，每个区域带自己的基础分。 */
    public List<TargetRegion> regions = new ArrayList<>();

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

        s.safetyMax = (int) clamp(cfg.getInt("targets.safety-max", 256), 1, 1024);
        s.respawnDelayTicks = (int) clamp(cfg.getInt("targets.respawn-delay-ticks", 10), 0, 200);
        s.targetLifetimeSeconds = (int) clamp(cfg.getInt("targets.lifetime-seconds", 0), 0, 3600);
        s.smallChance = clamp(cfg.getDouble("targets.small-chance", 0.35), 0.0, 1.0);
        s.minSpeed = Math.max(0.0, cfg.getDouble("targets.min-speed", 1.5));
        s.maxSpeed = Math.max(s.minSpeed, cfg.getDouble("targets.max-speed", 6.0));
        s.fastSpeed = Math.max(0.0, cfg.getDouble("targets.fast-speed", 4.5));
        s.smallScale = clamp(cfg.getDouble("targets.small-scale", 0.5), 0.2, 0.9);

        s.fixedBlock = block(cfg.getString("targets.blocks.fixed"), Material.TARGET);
        s.movingBlock = block(cfg.getString("targets.blocks.moving"), Material.REDSTONE_BLOCK);
        s.fastBlock = block(cfg.getString("targets.blocks.fast"), Material.GOLD_BLOCK);
        s.smallGlowColor = glowColor(cfg.getString("targets.small-glow-color"));

        s.bonusMoving = (int) clamp(cfg.getInt("targets.bonuses.moving", 1), 0, 1000);
        s.bonusFast = (int) clamp(cfg.getInt("targets.bonuses.fast", 2), 0, 1000);
        s.bonusSmall = (int) clamp(cfg.getInt("targets.bonuses.small", 2), 0, 1000);

        s.sound = cfg.getBoolean("effects.sound", true);
        s.particle = cfg.getBoolean("effects.particle", true);
        s.topSize = (int) clamp(cfg.getInt("top-size", 10), 1, 50);

        for (Map<?, ?> raw : cfg.getMapList("regions")) {
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
            Map<?, ?> weights = raw.get("weights") instanceof Map<?, ?> map ? map : Map.of();
            Map<?, ?> guaranteed = raw.get("guaranteed") instanceof Map<?, ?> map ? map : Map.of();
            int weightFixed = (int) clamp(number(weights.get("fixed"),
                    TargetRegion.DEFAULT_WEIGHT_FIXED), 0, 100000);
            int weightMoving = (int) clamp(number(weights.get("moving"),
                    TargetRegion.DEFAULT_WEIGHT_MOVING), 0, 100000);
            int weightFast = (int) clamp(number(weights.get("fast"),
                    TargetRegion.DEFAULT_WEIGHT_FAST), 0, 100000);
            int minFixed = (int) clamp(number(guaranteed.get("fixed"), 0), 0, count);
            int minMoving = (int) clamp(number(guaranteed.get("moving"), 0), 0, count);
            int minFast = (int) clamp(number(guaranteed.get("fast"), 0), 0, count);
            TargetAxis axis = TargetAxis.fromKey(string(raw.get("axis"), "x"));
            s.regions.add(new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ,
                    base, count, weightFixed, weightMoving, weightFast,
                    minFixed, minMoving, minFast, axis));
        }
        return s;
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
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

    /** 支持 #RRGGBB 或常见颜色名；写错退回红色。 */
    private static Color glowColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return Color.RED;
        }
        String value = raw.trim();
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return Color.RED;
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
            default -> Color.RED;
        };
    }
}
