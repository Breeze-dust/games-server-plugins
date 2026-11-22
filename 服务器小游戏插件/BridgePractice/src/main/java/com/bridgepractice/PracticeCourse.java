package com.bridgepractice;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** 一个练习场地：起点、终点、虚空线、菜单图标。 */
public final class PracticeCourse {

    private final String name;
    private Location start;
    private Location end;
    private double endRadius;
    private int fallY;
    private Material icon = Material.WHITE_WOOL;

    public PracticeCourse(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** 起点终点都设好了，并且在同一张已加载的世界里。 */
    public boolean ready() {
        return start != null && end != null && start.getWorld() != null && end.getWorld() != null
                && start.getWorld().equals(end.getWorld());
    }

    public boolean hasStart() {
        return start != null;
    }

    public boolean hasEnd() {
        return end != null;
    }

    public Location start() {
        return start == null ? null : start.clone();
    }

    public Location end() {
        return end == null ? null : end.clone();
    }

    public void setStart(Location location) {
        this.start = location.clone();
    }

    public void setEnd(Location location) {
        this.end = location.clone();
    }

    public Material icon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon == null ? Material.WHITE_WOOL : icon;
    }

    /** 场地自己配了 end-radius 就用它，否则用配置里的默认值。 */
    public double endRadius(double fallback) {
        return endRadius > 0 ? endRadius : fallback;
    }

    /** 场地自己配了 fall-y（>0）就用它，否则按起点/终点较低者减指定深度计算。 */
    public int fallY(int depth) {
        if (fallY > 0) {
            return fallY;
        }
        int base = start == null ? 0 : start.getBlockY();
        if (end != null) {
            base = Math.min(base, end.getBlockY());
        }
        return base - Math.max(1, depth);
    }

    /** 到终点的水平距离；终点没设置时返回 -1。 */
    public double horizontalDistanceToEnd(Location location) {
        if (end == null || location.getWorld() == null || !location.getWorld().equals(end.getWorld())) {
            return -1;
        }
        double dx = location.getX() - end.getX();
        double dz = location.getZ() - end.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 起点到终点的水平距离（房间长度）；没设置好时返回 0。 */
    public double horizontalLength() {
        if (start == null || end == null || start.getWorld() == null || end.getWorld() == null
                || !start.getWorld().equals(end.getWorld())) {
            return 0;
        }
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 玩家是否已经站进终点判定范围（水平、垂直分别判断）。 */
    public boolean isAtEnd(Location location, double radius) {
        if (end == null || location.getWorld() == null || !location.getWorld().equals(end.getWorld())) {
            return false;
        }
        double r = Math.max(0.5, radius);
        return horizontalDistanceToEnd(location) <= r
                && Math.abs(location.getY() - end.getY()) <= r + 1.0;
    }

    // ---------- 读写 courses.yml ----------

    public void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        start = parse(section.getString("start", null));
        end = parse(section.getString("end", null));
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
                : (end != null && end.getWorld() != null ? end.getWorld().getName() : null));
        section.set("start", format(start));
        section.set("end", format(end));
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
