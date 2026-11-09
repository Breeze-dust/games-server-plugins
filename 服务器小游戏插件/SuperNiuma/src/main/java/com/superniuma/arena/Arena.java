package com.superniuma.arena;

import org.bukkit.Location;
import org.bukkit.World;

/** 一张地图的固定数据：出生点、起点、终点、可建造范围。 */
public final class Arena {

    private final String name;
    private String worldName;
    private Location spawn;
    private Location start;
    private Location buildMin;
    private Location buildMax;
    private Location finishMin;
    private Location finishMax;
    private double voidY;

    public Arena(String name, String worldName) {
        this.name = name;
        this.worldName = worldName;
        this.voidY = 0.0;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public World getWorld() {
        return worldName == null ? null : org.bukkit.Bukkit.getWorld(worldName);
    }

    public boolean isReady() {
        return getWorld() != null && spawn != null && start != null
                && buildMin != null && buildMax != null
                && finishMin != null && finishMax != null;
    }

    public Location getSpawn() {
        return spawn == null ? null : spawn.clone();
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn == null ? null : spawn.clone();
    }

    public Location getStart() {
        return start == null ? null : start.clone();
    }

    public void setStart(Location start) {
        this.start = start == null ? null : start.clone();
    }

    public Location getBuildMin() {
        return buildMin == null ? null : buildMin.clone();
    }

    public Location getBuildMax() {
        return buildMax == null ? null : buildMax.clone();
    }

    public void setBuildCorner(Location a, Location b) {
        this.buildMin = min(a, b);
        this.buildMax = max(a, b);
    }

    public Location getFinishMin() {
        return finishMin == null ? null : finishMin.clone();
    }

    public Location getFinishMax() {
        return finishMax == null ? null : finishMax.clone();
    }

    public void setFinishCorner(Location a, Location b) {
        this.finishMin = min(a, b);
        this.finishMax = max(a, b);
    }

    public double getVoidY() {
        return voidY;
    }

    public void setVoidY(double voidY) {
        this.voidY = voidY;
    }

    /** 方块坐标是否在可建造范围内。 */
    public boolean inBuildBounds(Location loc) {
        if (buildMin == null || buildMax == null || loc == null) {
            return false;
        }
        return loc.getBlockX() >= buildMin.getBlockX() && loc.getBlockX() <= buildMax.getBlockX()
                && loc.getBlockY() >= buildMin.getBlockY() && loc.getBlockY() <= buildMax.getBlockY()
                && loc.getBlockZ() >= buildMin.getBlockZ() && loc.getBlockZ() <= buildMax.getBlockZ();
    }

    /** 玩家是否进入终点区域。 */
    public boolean inFinish(Location loc) {
        if (finishMin == null || finishMax == null || loc == null) {
            return false;
        }
        return loc.getX() >= finishMin.getX() && loc.getX() <= finishMax.getX() + 1.0
                && loc.getY() >= finishMin.getY() && loc.getY() <= finishMax.getY() + 1.0
                && loc.getZ() >= finishMin.getZ() && loc.getZ() <= finishMax.getZ() + 1.0;
    }

    private static Location min(Location a, Location b) {
        Location out = a.clone();
        out.setX(Math.min(a.getX(), b.getX()));
        out.setY(Math.min(a.getY(), b.getY()));
        out.setZ(Math.min(a.getZ(), b.getZ()));
        return out;
    }

    private static Location max(Location a, Location b) {
        Location out = a.clone();
        out.setX(Math.max(a.getX(), b.getX()));
        out.setY(Math.max(a.getY(), b.getY()));
        out.setZ(Math.max(a.getZ(), b.getZ()));
        return out;
    }
}
