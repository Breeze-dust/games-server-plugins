package com.dontdoit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** 一个场地：中心、边长、等待点、观战点；开局时用于定位石层与生成矿物。 */
public final class DontDoItArena {

    private final String name;
    private String worldName;
    private String centerText;
    private int areaSize;
    private String lobbyText;
    private String spectatorText;

    // 运行时
    private int stoneY = Integer.MIN_VALUE;
    private double savedBorderSize = -1;
    private Location savedBorderCenter;

    public DontDoItArena(String name) {
        this.name = name;
        this.worldName = "world";
        this.centerText = "0,64,0";
        this.areaSize = 64;
        this.lobbyText = "0,70,0,0,0";
        this.spectatorText = "0,90,0,0,0";
    }

    public String getName() {
        return name;
    }

    public void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        worldName = section.getString("world", worldName);
        centerText = section.getString("center", centerText);
        areaSize = section.getInt("area-size", areaSize);
        lobbyText = section.getString("lobby", lobbyText);
        spectatorText = section.getString("spectator", spectatorText);
    }

    public void save(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("center", centerText);
        section.set("area-size", areaSize);
        section.set("lobby", lobbyText);
        section.set("spectator", spectatorText);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getAreaSize() {
        return areaSize;
    }

    public void setAreaSize(int areaSize) {
        this.areaSize = Math.max(9, areaSize);
    }

    /** 实际边长：比配置值大的最小奇数。 */
    public int side() {
        return areaSize % 2 == 0 ? areaSize + 1 : areaSize;
    }

    public int radius() {
        return side() / 2;
    }

    public Location center() {
        Location loc = parse(centerText);
        if (loc == null) {
            World w = world();
            return w == null ? null : new Location(w, 0, 64, 0);
        }
        World w = world();
        if (w != null) {
            loc.setWorld(w);
        }
        return loc;
    }

    public void setCenter(Location loc) {
        this.worldName = loc.getWorld().getName();
        this.centerText = fmt(loc);
    }

    public Location lobby() {
        Location loc = parse(lobbyText);
        if (loc == null) {
            return null;
        }
        World w = world();
        if (w != null) {
            loc.setWorld(w);
        }
        return loc;
    }

    public void setLobby(Location loc) {
        this.lobbyText = fmtWithAngles(loc);
    }

    public Location spectator() {
        Location loc = parse(spectatorText);
        World w = world();
        if (loc == null) {
            Location lobby = lobby();
            return lobby == null ? null : lobby.clone().add(0, 20, 0);
        }
        if (w != null) {
            loc.setWorld(w);
        }
        return loc;
    }

    public void setSpectator(Location loc) {
        this.spectatorText = fmtWithAngles(loc);
    }

    public int getStoneY() {
        return stoneY;
    }

    public void setStoneY(int stoneY) {
        this.stoneY = stoneY;
    }

    public double getSavedBorderSize() {
        return savedBorderSize;
    }

    public void saveBorder(double size, Location center) {
        if (savedBorderSize < 0) {
            savedBorderSize = size;
            savedBorderCenter = center;
        }
    }

    public Location getSavedBorderCenter() {
        return savedBorderCenter;
    }

    public void clearBorder() {
        savedBorderSize = -1;
        savedBorderCenter = null;
    }

    public boolean contains(Location loc) {
        Location center = center();
        if (center == null || loc == null || loc.getWorld() == null || !loc.getWorld().equals(center.getWorld())) {
            return false;
        }
        int r = radius();
        return Math.abs(loc.getBlockX() - center.getBlockX()) <= r
                && Math.abs(loc.getBlockZ() - center.getBlockZ()) <= r;
    }

    private Location parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            float yaw = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 0;
            float pitch = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0;
            Location loc = new Location(null, x, y, z, yaw, pitch);
            loc.setWorld(world());
            return loc;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(Location loc) {
        return String.format("%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    private static String fmtWithAngles(Location loc) {
        return String.format("%.1f,%.1f,%.1f,%.1f,%.1f",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }
}
