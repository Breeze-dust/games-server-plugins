package com.seatgrab;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** 抢位占座场地：矩形区域、等待点、出生点、地面高度、展示框扫描范围。 */
public final class SeatGrabArena {

    public static final int UNSET = Integer.MIN_VALUE;

    private final SeatGrabPlugin plugin;
    private String worldName = "world";
    private Location pos1;
    private Location pos2;
    private Location lobby;
    private Location podium;
    /** 讲台“正面线”的法线方向：x = 排向沿 X 轴推进（线沿 Z），z = 排向沿 Z 轴推进（线沿 X）。 */
    private String podiumAxis = "z";
    private final List<Location> spawns = new ArrayList<>();
    private int groundY = UNSET;
    private int scanHeight = 10;

    public SeatGrabArena(SeatGrabPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration cfg = plugin.getConfig();
        worldName = cfg.getString("arena.world", "world");
        pos1 = parse(cfg.getString("arena.pos1", ""));
        pos2 = parse(cfg.getString("arena.pos2", ""));
        lobby = parse(cfg.getString("arena.lobby", ""));
        podium = parse(cfg.getString("arena.podium", ""));
        podiumAxis = normalizeAxis(cfg.getString("arena.podium-axis", "z"));
        groundY = cfg.getInt("arena.ground-y", UNSET);
        if (groundY == -999) {
            groundY = UNSET;
        }
        scanHeight = Math.max(1, cfg.getInt("arena.scan-height", 10));
        spawns.clear();
        for (String raw : cfg.getStringList("arena.spawns")) {
            Location location = parse(raw);
            if (location != null) {
                spawns.add(location);
            }
        }
        if (pos1 != null && pos1.getWorld() != null) {
            worldName = pos1.getWorld().getName();
        }
    }

    public void save() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("arena.world", worldName);
        cfg.set("arena.pos1", format(pos1));
        cfg.set("arena.pos2", format(pos2));
        cfg.set("arena.lobby", format(lobby));
        cfg.set("arena.podium", format(podium));
        cfg.set("arena.podium-axis", podiumAxis);
        cfg.set("arena.ground-y", groundY);
        cfg.set("arena.scan-height", scanHeight);
        cfg.set("arena.spawns", formatAll(spawns));
        plugin.saveConfig();
    }

    public void setCorner(int index, Location location) {
        Location at = location.getBlock().getLocation();
        if (index == 1) {
            pos1 = at;
            groundY = at.getBlockY();
            worldName = at.getWorld().getName();
        } else {
            pos2 = at;
        }
    }

    public void setLobby(Location location) {
        lobby = location.clone();
        worldName = location.getWorld().getName();
    }

    public void setPodium(Location location) {
        podium = location.clone();
        podiumAxis = axisFromYaw(location.getYaw());
        worldName = location.getWorld().getName();
    }

    public void setPodium(Location location, String axis) {
        podium = location.clone();
        podiumAxis = normalizeAxis(axis);
        worldName = location.getWorld().getName();
    }

    /** 玩家面朝方向的主要水平轴：面向南北（±Z）→ z，面向东西（±X）→ x。 */
    public static String axisFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        double dx = -Math.sin(radians);
        double dz = Math.cos(radians);
        return Math.abs(dx) > Math.abs(dz) ? "x" : "z";
    }

    public static String normalizeAxis(String axis) {
        return axis != null && axis.equalsIgnoreCase("x") ? "x" : "z";
    }

    public String podiumAxis() {
        return podiumAxis;
    }

    public void setSpawn(Location location) {
        spawns.clear();
        addSpawn(location);
    }

    public void addSpawn(Location location) {
        Location loc = location.clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);
        spawns.add(loc);
        if (groundY == UNSET) {
            groundY = loc.getBlockY();
        }
    }

    public void clearSpawns() {
        spawns.clear();
    }

    public List<Location> spawns() {
        return new ArrayList<>(spawns);
    }

    public boolean regionConfigured() {
        return pos1 != null && pos2 != null
                && pos1.getWorld() != null && pos2.getWorld() != null
                && pos1.getWorld().getName().equals(pos2.getWorld().getName());
    }

    public boolean ready() {
        return regionConfigured() && lobby != null && podium != null && world() != null
                && lobby.getWorld() != null && lobby.getWorld().getName().equals(pos1.getWorld().getName())
                && podium.getWorld() != null && podium.getWorld().getName().equals(pos1.getWorld().getName());
    }

    public String missing() {
        StringBuilder out = new StringBuilder();
        if (!regionConfigured()) {
            out.append("pos1/pos2 ");
        }
        if (lobby == null) {
            out.append("等待点 ");
        }
        if (podium == null) {
            out.append("讲台 ");
        }
        if (world() == null) {
            out.append("世界未加载 ");
        }
        if (lobby != null && lobby.getWorld() != null && regionConfigured()
                && !lobby.getWorld().getName().equals(pos1.getWorld().getName())) {
            out.append("等待点不在同一世界 ");
        }
        if (podium != null && podium.getWorld() != null && regionConfigured()
                && !podium.getWorld().getName().equals(pos1.getWorld().getName())) {
            out.append("讲台不在同一世界 ");
        }
        return out.toString().trim();
    }

    public boolean contains(Location location) {
        if (!regionConfigured() || location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(pos1.getWorld().getName())) {
            return false;
        }
        return location.getBlockX() >= minX() && location.getBlockX() <= maxX()
                && location.getBlockZ() >= minZ() && location.getBlockZ() <= maxZ();
    }

    public int minX() {
        return Math.min(pos1.getBlockX(), pos2.getBlockX());
    }

    public int maxX() {
        return Math.max(pos1.getBlockX(), pos2.getBlockX());
    }

    public int minZ() {
        return Math.min(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public int maxZ() {
        return Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public int minY() {
        if (!regionConfigured()) {
            return 64;
        }
        return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    public int maxY() {
        if (!regionConfigured()) {
            return 64;
        }
        return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    /** 展示框扫描范围：XZ 取场地，Y 从两个角较低处向下 1 格，向上自动延伸 scan-height。 */
    public BoundingBox frameBox() {
        return new BoundingBox(minX(), minY() - 1.0, minZ(),
                maxX() + 1.0, Math.max(minY(), maxY()) + scanHeight + 1.0, maxZ() + 1.0);
    }

    /** 玩家站立高度（脚部 Y）。 */
    public int groundY() {
        if (groundY != UNSET) {
            return groundY;
        }
        if (!spawns.isEmpty()) {
            return spawns.get(0).getBlockY();
        }
        return regionConfigured() ? minY() : 64;
    }

    public World world() {
        if (regionConfigured()) {
            return pos1.getWorld();
        }
        return Bukkit.getWorld(worldName);
    }

    public String worldName() {
        World world = world();
        return world == null ? worldName : world.getName();
    }

    public Location lobby() {
        return lobby == null ? null : lobby.clone();
    }

    public Location podium() {
        return podium == null ? null : podium.clone();
    }

    public Location pos1() {
        return pos1 == null ? null : pos1.clone();
    }

    public Location pos2() {
        return pos2 == null ? null : pos2.clone();
    }

    public Location center() {
        if (!regionConfigured()) {
            return null;
        }
        return new Location(world(), (minX() + maxX() + 1) / 2.0, groundY(), (minZ() + maxZ() + 1) / 2.0);
    }

    public Location spectatorSpot() {
        if (lobby != null) {
            return lobby.clone();
        }
        Location center = center();
        return center == null ? null : center.clone().add(0, 8, 0);
    }

    /** 在场地内随机找一个能站人的位置（优先在地面附近找，找不到再向上）。 */
    public Location randomSpawn(Random random) {
        World world = world();
        if (world == null || !regionConfigured()) {
            return center();
        }
        int feet = groundY();
        int lowest = Math.max(world.getMinHeight() + 2, feet - 3);
        int highest = Math.min(world.getMaxHeight() - 2, feet + 8);
        for (int attempt = 0; attempt < 48; attempt++) {
            int x = minX() + random.nextInt(maxX() - minX() + 1);
            int z = minZ() + random.nextInt(maxZ() - minZ() + 1);
            for (int y = lowest; y <= highest; y++) {
                Block below = world.getBlockAt(x, y - 1, z);
                if (!below.getType().isSolid()) {
                    continue;
                }
                if (world.getBlockAt(x, y, z).getType().isAir()
                        && world.getBlockAt(x, y + 1, z).getType().isAir()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return center();
    }

    public String sizeText() {
        if (!regionConfigured()) {
            return "未设置";
        }
        return (maxX() - minX() + 1) + "x" + (maxZ() - minZ() + 1)
                + "（地面 Y " + groundY() + "，扫描到 Y " + (Math.max(minY(), maxY()) + scanHeight) + "）";
    }

    public static String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.2f,%.2f",
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private static List<String> formatAll(List<Location> locations) {
        List<String> out = new ArrayList<>();
        for (Location location : locations) {
            out.add(format(location));
        }
        return out;
    }

    public static Location parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
