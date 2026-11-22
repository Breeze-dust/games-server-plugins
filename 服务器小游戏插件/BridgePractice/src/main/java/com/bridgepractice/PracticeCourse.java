package com.bridgepractice;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** 一个练习房间：起点、终点（点或一条线）、虚空线、菜单图标。 */
public final class PracticeCourse {

    private final String name;
    private Location start;
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

    /** 起点和终点都设好了，并且在同一张已加载的世界里。 */
    public boolean ready() {
        return start != null && endA != null && endB != null
                && start.getWorld() != null && endA.getWorld() != null && endB.getWorld() != null
                && start.getWorld().equals(endA.getWorld())
                && endA.getWorld().equals(endB.getWorld());
    }

    public boolean hasStart() {
        return start != null;
    }

    public boolean hasEnd() {
        return endA != null && endB != null;
    }

    public Location start() {
        return start == null ? null : start.clone();
    }

    public Location endA() {
        return endA == null ? null : endA.clone();
    }

    public Location endB() {
        return endB == null ? null : endB.clone();
    }

    /** 终点线的中点。 */
    public Location endCenter() {
        if (endA == null || endB == null) {
            return null;
        }
        return new Location(endA.getWorld(),
                (endA.getX() + endB.getX()) / 2.0,
                (endA.getY() + endB.getY()) / 2.0,
                (endA.getZ() + endB.getZ()) / 2.0);
    }

    /** 从起点看过去、终点线上最近的点（用来朝向和清理走廊）。 */
    public Location facingTarget() {
        if (start != null) {
            Nearest nearest = nearest(start);
            if (nearest != null && nearest.distance > 0.01) {
                return new Location(start.getWorld(), nearest.x, nearest.y, nearest.z);
            }
        }
        return endCenter();
    }

    public void setStart(Location location) {
        this.start = location.clone();
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
        int base = start == null ? 0 : start.getBlockY();
        if (endA != null) {
            base = Math.min(base, endA.getBlockY());
        }
        if (endB != null) {
            base = Math.min(base, endB.getBlockY());
        }
        return base - Math.max(1, depth);
    }

    /** 到终点线（线段）的水平最近距离；终点没设置时返回 -1。 */
    public double horizontalDistanceToEnd(Location location) {
        Nearest nearest = nearest(location);
        return nearest == null ? -1 : nearest.distance;
    }

    /** 起点到终点线的水平距离（房间长度）；没设置好时返回 0。 */
    public double horizontalLength() {
        if (start == null) {
            return 0;
        }
        Nearest nearest = nearest(start);
        return nearest == null ? 0 : nearest.distance;
    }

    /** 玩家是否已经进入终点线判定范围（水平最近距离 + 垂直高度）。 */
    public boolean isAtEnd(Location location, double radius) {
        Nearest nearest = nearest(location);
        if (nearest == null) {
            return false;
        }
        double r = Math.max(0.5, radius);
        return nearest.distance <= r && Math.abs(location.getY() - nearest.y) <= r + 1.0;
    }

    /** 位置到终点线段的最近点；世界不同或没设置终点时返回 null。 */
    private Nearest nearest(Location location) {
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

    // ---------- 读写 courses.yml ----------

    public void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        start = parse(section.getString("start", null));
        endA = parse(section.getString("end-1", null));
        endB = parse(section.getString("end-2", null));
        if (endA == null && endB == null) {
            // 旧版本只有一个 end 点
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
        section.set("world", start != null && start.getWorld() != null
                ? start.getWorld().getName()
                : (endA != null && endA.getWorld() != null ? endA.getWorld().getName() : null));
        section.set("start", format(start));
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
