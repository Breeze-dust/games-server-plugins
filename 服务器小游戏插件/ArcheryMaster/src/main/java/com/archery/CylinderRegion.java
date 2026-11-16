package com.archery;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;

/** 靶子生成区域：一个圆柱体（轴心 + 半径 + 上下 Y）。 */
public final class CylinderRegion {

    private final World world;
    private final double centerX;
    private final double centerZ;
    private final double radius;
    private final double yMin;
    private final double yMax;

    public CylinderRegion(World world, double centerX, double centerZ,
                          double radius, double yMin, double yMax) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public World world() {
        return world;
    }

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    public double radius() {
        return radius;
    }

    public double yMin() {
        return yMin;
    }

    public double yMax() {
        return yMax;
    }

    /** 圆柱体内均匀取一个点；size 是靶子边长，用来把靶子完整留在半径内。 */
    public Location randomPoint(Random random, double size) {
        double usable = Math.max(0.1, radius - size / 2.0);
        double r = usable * Math.sqrt(random.nextDouble());
        double angle = random.nextDouble() * Math.PI * 2.0;
        double x = centerX + Math.cos(angle) * r;
        double z = centerZ + Math.sin(angle) * r;
        double y = yMin + random.nextDouble() * (yMax - yMin);
        return new Location(world, x, y, z);
    }

    @Override
    public String toString() {
        return world.getName() + " 中心(" + format(centerX) + ", " + format(centerZ) + ") 半径"
                + format(radius) + " Y " + format(yMin) + "~" + format(yMax);
    }

    private static String format(double value) {
        return String.format("%.1f", value);
    }
}
