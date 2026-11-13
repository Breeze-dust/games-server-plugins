package com.cloudwalk;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** config.yml 的只读快照。 */
public final class CloudWalkSettings {

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
    public int queueLength = 3;
    public boolean allow4JumpUp = false;
    public Material block = Material.SMOOTH_STONE;

    public int afkEndSeconds = 10;
    public boolean sound = true;
    public boolean particle = true;
    public boolean targetParticle = true;

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
        Material material = Material.matchMaterial(cfg.getString("path.block", "SMOOTH_STONE"));
        s.block = material == null ? Material.SMOOTH_STONE : material;

        s.afkEndSeconds = Math.max(0, cfg.getInt("run.afk-end-seconds", 0));
        s.sound = cfg.getBoolean("run.sound", true);
        s.particle = cfg.getBoolean("run.particle", true);
        s.targetParticle = cfg.getBoolean("run.target-particle", true);

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
