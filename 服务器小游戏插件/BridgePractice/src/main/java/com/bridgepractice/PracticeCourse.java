package com.bridgepractice;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** 一个练习房间：起点线、终点线（都可退化成点）、出生点、虚空线、菜单图标。 */
public final class PracticeCourse {

    private final String name;
    private Location startA;
    private Location startB;
    private Location spawn;
    private Location endA;
    private Location endB;
    private double endRadius;
    private int fallY;
    private Material icon = Material.WHITE_WOOL;

    public PracticeCourse(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** 起点线和终点线都设好了，并且在同一张已加载的世界里。 */
    public boolean ready() {
        return startA != null && startB != null && endA != null && endB != null
                && startA.getWorld() != null && startB.getWorld() != null
                && endA.getWorld() != null && endB.getWorld() != null
                && startA.getWorld().equals(startB.getWorld())
                && startA.getWorld().equals(endA.getWorld())
                && endA.getWorld().equals(endB.getWorld());
    }

    public boolean hasStart() {
        return startA != null && startB != null;
    }

    public boolean hasEnd() {
        return endA != null && endB != null;
    }

    /** 起点线的中点。 */
    public Location start() {
        return midpoint(startA, startB);
    }

    /** 房间出生点；没单独设置时返回起点线中点。 */
    public Location spawnPoint() {
        if (spawn != null) {
            return spawn.clone();
        }
        return start();
    }

    public boolean hasSpawn() {
        return spawn != null;
    }

    public Location endA() {
        return endA == null ? null : endA.clone();
    }

    public Location endB() {
        return endB == null ? null : endB.clone();
    }

    /** 终点线的中点。 */
    public Location endCenter() {
        return midpoint(endA, endB);
    }

    /** 从起点看过去、终点线上最近的点（用来朝向和清理走廊）。 */
    public Location facingTarget() {
        Location from = start();
        if (from != null) {
            Nearest nearest = nearestOnEnd(from);
            if (nearest != null && nearest.distance > 0.01) {
                return new Location(from.getWorld(), nearest.x, nearest.y, nearest.z);
            }
        }
        return endCenter();
    }

    public void setStartPoint(Location location) {
        this.startA = location.clone();
        this.startB = location.clone();
    }

    public void setStartLinePoint(int index, Location location) {
        if (index == 1) {
            this.startA = location.clone();
            if (startB == null) {
                startB = location.clone();
            }
        } else {
            this.startB = location.clone();
            if (startA == null) {
                startA = location.clone();
            }
        }
    }

    public void setSpawn(Location location) {
        this.spawn = location == null ? null : location.clone();
    }

    /** 不区分 1/2 时设成一个点（两个端点重合）。 */
    public void setEndPoint(Location location) {
        this.endA = location.clone();
        this.endB = location.clone();
    }

    /** 设置终点线的第 1 或第 2 个端点。 */
    public void setEndLinePoint(int index, Location location) {
        if (index == 1) {
            this.endA = location.clone();
            if (endB == null) {
                endB = location.clone();
            }
        } else {
            this.endB = location.clone();
            if (endA == null) {
                endA = location.clone();
            }
        }
    }

    public Material icon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon == null ? Material.WHITE_WOOL : icon;
    }

    /** 房间自己配了 end-radius 就用它，否则用配置里的默认值。 */
    public double endRadius(double fallback) {
        return endRadius > 0 ? endRadius : fallback;
    }

    /** 房间自己配了 fall-y（>0）就用它，否则按起点/终点较低者减指定深度计算。 */
    public int fallY(int depth) {
        if (fallY > 0) {
            return fallY;
        }
        int base = startA == null ? 0 : startA.getBlockY();
        for (Location point : new Location[]{startB, endA, endB}) {
            if (point != null) {
                base = Math.min(base, point.getBlockY());
            }
        }
        return base - Math.max(1, depth);
    }

    /** 到终点线的水平最近距离；终点没设置时返回 -1。 */
    public double horizontalDistanceToEnd(Location location) {
        Nearest nearest = nearestOnEnd(location);
        return nearest == null ? -1 : nearest.distance;
    }

    /** 到起点线的水平最近距离；起点没设置时返回 -1。 */
    public double distanceToStart(Location location) {
        if (startA == null || startB == null || location == null || location.getWorld() == null
                || !location.getWorld().equals(startA.getWorld())) {
            return -1;
        }
        return segmentDistance(location.getX(), location.getZ(),
                startA.getX(), startA.getZ(), startB.getX(), startB.getZ());
    }

    /** 起点线到终点线的水平距离（房间长度）；没设置好时返回 0。 */
    public double horizontalLength() {
        if (!hasStart() || !hasEnd() || startA.getWorld() == null
                || !startA.getWorld().equals(endA.getWorld())) {
            return 0;
        }
        double[] d = {
                segmentDistance(startA.getX(), startA.getZ(), endA.getX(), endA.getZ(), endB.getX(), endB.getZ()),
                segmentDistance(startB.getX(), startB.getZ(), endA.getX(), endA.getZ(), endB.getX(), endB.getZ()),
                segmentDistance(endA.getX(), endA.getZ(), startA.getX(), startA.getZ(), startB.getX(), startB.getZ()),
                segmentDistance(endB.getX(), endB.getZ(), startA.getX(), startA.getZ(), startB.getX(), startB.getZ())
        };
        double min = d[0];
        for (double value : d) {
            min = Math.min(min, value);
        }
        return min;
    }

    /** 玩家是否已经进入终点线判定范围（水平最近距离 + 垂直高度）。 */
    public boolean isAtEnd(Location location, double radius) {
        Nearest nearest = nearestOnEnd(location);
        if (nearest == null) {
            return false;
        }
        double r = Math.max(0.5, radius);
        return nearest.distance <= r && Math.abs(location.getY() - nearest.y) <= r + 1.0;
    }

    /** 位置到终点线段的最近点；世界不同或没设置终点时返回 null。 */
    private Nearest nearestOnEnd(Location location) {
        if (endA == null || endB == null || location == null || location.getWorld() == null
                || !location.getWorld().equals(endA.getWorld())) {
            return null;
        }
        double ax = endA.getX();
        double az = endA.getZ();
        double dx = endB.getX() - ax;
        double dz = endB.getZ() - az;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq < 1.0E-9
                ? 0.0
                : ((location.getX() - ax) * dx + (location.getZ() - az) * dz) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double x = ax + dx * t;
        double z = az + dz * t;
        double y = endA.getY() + (endB.getY() - endA.getY()) * t;
        double ex = location.getX() - x;
        double ez = location.getZ() - z;
        return new Nearest(x, y, z, Math.sqrt(ex * ex + ez * ez));
    }

    private record Nearest(double x, double y, double z, double distance) {
    }

    private static Location midpoint(Location a, Location b) {
        if (a == null || b == null) {
            return null;
        }
        return new Location(a.getWorld(),
                (a.getX() + b.getX()) / 2.0,
                (a.getY() + b.getY()) / 2.0,
                (a.getZ() + b.getZ()) / 2.0);
    }

    /** 点到水平线段的距离。 */
    private static double segmentDistance(double px, double pz,
                                          double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq < 1.0E-9 ? 0.0 : ((px - ax) * dx + (pz - az) * dz) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double ex = px - (ax + dx * t);
        double ez = pz - (az + dz * t);
        return Math.sqrt(ex * ex + ez * ez);
    }

    // ---------- 读写 courses.yml ----------

    public void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        startA = parse(section.getString("start-1", null));
        startB = parse(section.getString("start-2", null));
        if (startA == null && startB == null) {
            Location legacy = parse(section.getString("start", null));
            startA = legacy;
            startB = legacy;
        }
        spawn = parse(section.getString("spawn", null));
        endA = parse(section.getString("end-1", null));
        endB = parse(section.getString("end-2", null));
        if (endA == null && endB == null) {
            Location legacy = parse(section.getString("end", null));
            endA = legacy;
            endB = legacy;
        }
        endRadius = section.getDouble("end-radius", 0.0);
        fallY = section.getInt("fall-y", 0);
        Material parsed = section.getString("icon", null) == null
                ? null : Material.matchMaterial(section.getString("icon"));
        icon = parsed == null ? Material.WHITE_WOOL : parsed;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("world", startA != null && startA.getWorld() != null
                ? startA.getWorld().getName()
                : (endA != null && endA.getWorld() != null ? endA.getWorld().getName() : null));
        section.set("start-1", format(startA));
        section.set("start-2", format(startB));
        section.set("start", null);
        section.set("spawn", format(spawn));
        section.set("end-1", format(endA));
        section.set("end-2", format(endB));
        section.set("end", null);
        section.set("end-radius", endRadius > 0 ? endRadius : null);
        section.set("fall-y", fallY > 0 ? fallY : null);
        section.set("icon", icon.name());
    }

    // ---------- 位置字符串 ----------

    public static String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.2f,%.2f",
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
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

    /** 让玩家从起点出发时朝向终点。 */
    public static void faceEnd(Location from, Location end) {
        if (from == null || end == null || from.getWorld() == null || end.getWorld() == null
                || !from.getWorld().equals(end.getWorld())) {
            return;
        }
        org.bukkit.util.Vector direction = end.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }
        Location view = from.clone().setDirection(direction);
        from.setYaw(view.getYaw());
        from.setPitch(0f);
    }
}
