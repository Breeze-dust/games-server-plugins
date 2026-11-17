package com.archery;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;

/**
 * 一个长方体靶子生成区域，带自己的基础分、靶子总数、类型权重和保底数量。
 * 坐标是连续坐标：编辑模式下选中的方块整格算进区域（max 加 1）。
 */
public final class TargetRegion {

    /** 新区域默认参数。 */
    public static final int DEFAULT_COUNT = 4;
    public static final int DEFAULT_WEIGHT_FIXED = 5;
    public static final int DEFAULT_WEIGHT_MOVING = 3;
    public static final int DEFAULT_WEIGHT_FAST = 2;
    public static final int DEFAULT_MINIMUM = 0;

    private final World world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final int baseScore;
    /** 这个区域同时存在的靶子总数。 */
    private final int count;
    private final int weightFixed;
    private final int weightMoving;
    private final int weightFast;
    private final int minFixed;
    private final int minMoving;
    private final int minFast;

    public TargetRegion(World world, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ, int baseScore,
                        int count, int weightFixed, int weightMoving, int weightFast,
                        int minFixed, int minMoving, int minFast) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.baseScore = baseScore;
        this.count = count;
        this.weightFixed = weightFixed;
        this.weightMoving = weightMoving;
        this.weightFast = weightFast;
        this.minFixed = minFixed;
        this.minMoving = minMoving;
        this.minFast = minFast;
    }

    public static TargetRegion fromCorners(World world, Location a, Location b, int baseScore) {
        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX()) + 1.0;
        double maxY = Math.max(a.getY(), b.getY()) + 1.0;
        double maxZ = Math.max(a.getZ(), b.getZ()) + 1.0;
        return new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ, baseScore,
                DEFAULT_COUNT, DEFAULT_WEIGHT_FIXED, DEFAULT_WEIGHT_MOVING, DEFAULT_WEIGHT_FAST,
                DEFAULT_MINIMUM, DEFAULT_MINIMUM, DEFAULT_MINIMUM);
    }

    /** 复制区域并替换部分参数；传 -1 表示保持原值。 */
    public TargetRegion with(int newBaseScore, int newCount,
                             int newWeightFixed, int newWeightMoving, int newWeightFast,
                             int newMinFixed, int newMinMoving, int newMinFast) {
        return new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ,
                newBaseScore < 0 ? baseScore : newBaseScore,
                newCount < 0 ? count : newCount,
                newWeightFixed < 0 ? weightFixed : newWeightFixed,
                newWeightMoving < 0 ? weightMoving : newWeightMoving,
                newWeightFast < 0 ? weightFast : newWeightFast,
                newMinFixed < 0 ? minFixed : newMinFixed,
                newMinMoving < 0 ? minMoving : newMinMoving,
                newMinFast < 0 ? minFast : newMinFast);
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

    public int count() {
        return count;
    }

    public int weight(TargetType type) {
        return switch (type) {
            case FIXED -> weightFixed;
            case MOVING -> weightMoving;
            case FAST -> weightFast;
        };
    }

    public int minimum(TargetType type) {
        return switch (type) {
            case FIXED -> minFixed;
            case MOVING -> minMoving;
            case FAST -> minFast;
        };
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

    /** 一行展示靶子数量、权重和保底。 */
    public String settingsLine() {
        return "靶子 " + count + " 个 · 权重 固定" + weightFixed + "/移动" + weightMoving
                + "/快速" + weightFast + " · 保底 固定" + minFixed + "/移动" + minMoving
                + "/快速" + minFast;
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }
}
