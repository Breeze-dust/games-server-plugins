package com.archery;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;

/**
 * 一个长方体靶子生成区域，带自己的基础分。
 * 坐标是连续坐标：编辑模式下选中的方块整格算进区域（max 加 1）。
 */
public final class TargetRegion {

    private final World world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final int baseScore;

    public TargetRegion(World world, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ, int baseScore) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.baseScore = baseScore;
    }

    public static TargetRegion fromCorners(World world, Location a, Location b, int baseScore) {
        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX()) + 1.0;
        double maxY = Math.max(a.getY(), b.getY()) + 1.0;
        double maxZ = Math.max(a.getZ(), b.getZ()) + 1.0;
        return new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ, baseScore);
    }

    public World world() {
        return world;
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double minZ() {
        return minZ;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public double maxZ() {
        return maxZ;
    }

    public int baseScore() {
        return baseScore;
    }

    /** 区域里均匀取一个点，size 是靶子边长，尽量把整个靶子留在区域内。 */
    public Location randomPoint(Random random, double size) {
        double half = size / 2.0;
        double x = between(minX + half, maxX - half, random);
        double y = between(minY + half, maxY - half, random);
        double z = between(minZ + half, maxZ - half, random);
        return new Location(world, x, y, z);
    }

    private static double between(double min, double max, Random random) {
        if (max <= min) {
            return (min + max) / 2.0;
        }
        return min + random.nextDouble() * (max - min);
    }

    public String describe() {
        return world.getName() + " (" + fmt(minX) + ", " + fmt(minY) + ", " + fmt(minZ)
                + ") ~ (" + fmt(maxX) + ", " + fmt(maxY) + ", " + fmt(maxZ)
                + ") 基础分 " + baseScore;
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }
}
