package com.archery;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;

/** config.yml 的只读快照。 */
public final class ArcherySettings {

    public String regionWorld = "";
    public double centerX;
    public double centerZ;
    public double radius = 10.0;
    public double yMin;
    public double yMax = 10.0;

    public String startWorld = "";
    public double startX;
    public double startY;
    public double startZ;
    public float startYaw;
    public float startPitch;

    public int gameSeconds = 60;

    public int maxTargets = 8;
    public int respawnDelayTicks = 10;
    public int targetLifetimeSeconds = 0;
    public double smallChance = 0.35;
    public double movingChance = 0.50;
    public double minSpeed = 1.5;
    public double maxSpeed = 6.0;
    public double fastSpeed = 4.5;

    /** 三种靶子的动物：成年 = 正常大小，幼年 = 缩小靶。 */
    public EntityType fixedEntity = EntityType.COW;
    public EntityType movingEntity = EntityType.PIG;
    public EntityType fastEntity = EntityType.SHEEP;

    public int scoreFixedNormal = 1;
    public int scoreFixedSmall = 3;
    public int scoreMovingNormal = 3;
    public int scoreMovingSmall = 5;
    public int scoreFastNormal = 5;
    public int scoreFastSmall = 10;

    public boolean sound = true;
    public boolean particle = true;
    public int topSize = 10;

    public static ArcherySettings load(FileConfiguration cfg) {
        ArcherySettings s = new ArcherySettings();

        ConfigurationSection region = cfg.getConfigurationSection("region");
        if (region != null) {
            s.regionWorld = region.getString("world", "");
            s.centerX = region.getDouble("center-x", 0.0);
            s.centerZ = region.getDouble("center-z", 0.0);
            s.radius = clamp(region.getDouble("radius", 10.0), 1.0, 256.0);
            s.yMin = region.getDouble("y-min", 0.0);
            s.yMax = region.getDouble("y-max", 10.0);
            if (s.yMax < s.yMin) {
                double swap = s.yMin;
                s.yMin = s.yMax;
                s.yMax = swap;
            }
            if (s.yMax - s.yMin < 0.5) {
                s.yMax = s.yMin + 0.5;
            }
        }

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

        s.maxTargets = (int) clamp(cfg.getInt("targets.max", 8), 1, 64);
        s.respawnDelayTicks = (int) clamp(cfg.getInt("targets.respawn-delay-ticks", 10), 0, 200);
        s.targetLifetimeSeconds = (int) clamp(cfg.getInt("targets.lifetime-seconds", 0), 0, 3600);
        s.smallChance = clamp(cfg.getDouble("targets.small-chance", 0.35), 0.0, 1.0);
        s.movingChance = clamp(cfg.getDouble("targets.moving-chance", 0.50), 0.0, 1.0);
        s.minSpeed = Math.max(0.0, cfg.getDouble("targets.min-speed", 1.5));
        s.maxSpeed = Math.max(s.minSpeed, cfg.getDouble("targets.max-speed", 6.0));
        s.fastSpeed = Math.max(0.0, cfg.getDouble("targets.fast-speed", 4.5));

        s.fixedEntity = animal(cfg.getString("targets.entities.fixed"), EntityType.COW);
        s.movingEntity = animal(cfg.getString("targets.entities.moving"), EntityType.PIG);
        s.fastEntity = animal(cfg.getString("targets.entities.fast"), EntityType.SHEEP);

        s.scoreFixedNormal = score(cfg, "targets.scores.fixed-normal", 1);
        s.scoreFixedSmall = score(cfg, "targets.scores.fixed-small", 3);
        s.scoreMovingNormal = score(cfg, "targets.scores.moving-normal", 3);
        s.scoreMovingSmall = score(cfg, "targets.scores.moving-small", 5);
        s.scoreFastNormal = score(cfg, "targets.scores.fast-normal", 5);
        s.scoreFastSmall = score(cfg, "targets.scores.fast-small", 10);

        s.sound = cfg.getBoolean("effects.sound", true);
        s.particle = cfg.getBoolean("effects.particle", true);
        s.topSize = (int) clamp(cfg.getInt("top-size", 10), 1, 50);
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

    private static int score(FileConfiguration cfg, String path, int fallback) {
        return (int) clamp(cfg.getInt(path, fallback), 0, 1000);
    }

    /** 只接受能切换成年/幼年的动物，写错或写成僵尸之类就退回默认值。 */
    private static EntityType animal(String name, EntityType fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
        Class<?> clazz = type.getEntityClass();
        if (clazz == null || !Animals.class.isAssignableFrom(clazz)) {
            return fallback;
        }
        return type;
    }
}
