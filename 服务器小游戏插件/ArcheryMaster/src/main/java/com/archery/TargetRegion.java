package com.archery;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;

/**
 * 一个长方体靶子生成区域，带自己的基础分、靶子总数、类型/大小权重和保底数量、移动轴。
 * 坐标是连续坐标：编辑模式下选中的方块整格算进区域（max 加 1）。
 */
public final class TargetRegion {

    public static final int DEFAULT_COUNT = 4;
    public static final int DEFAULT_WEIGHT_FIXED = 5;
    public static final int DEFAULT_WEIGHT_MOVING = 3;
    public static final int DEFAULT_WEIGHT_FAST = 2;
    public static final int DEFAULT_WEIGHT_NORMAL = 65;
    public static final int DEFAULT_WEIGHT_SMALL = 35;
    public static final int DEFAULT_MINIMUM = 0;

    private final World world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final int baseScore;
    private final int count;
    /** 下标 = {@link RegionMetric#ordinal()}。 */
    private final int[] weights;
    private final int[] minimums;
    private final TargetAxis axis;

    public TargetRegion(World world, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ, int baseScore,
                        int count, int[] weights, int[] minimums, TargetAxis axis) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.baseScore = baseScore;
        this.count = count;
        this.weights = defaultWeights(weights);
        this.minimums = defaultMinimums(minimums);
        this.axis = axis == null ? TargetAxis.X : axis;
    }

    public static TargetRegion fromCorners(World world, Location a, Location b, int baseScore) {
        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX()) + 1.0;
        double maxY = Math.max(a.getY(), b.getY()) + 1.0;
        double maxZ = Math.max(a.getZ(), b.getZ()) + 1.0;
        return new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ, baseScore,
                DEFAULT_COUNT, defaultWeights(null), defaultMinimums(null), TargetAxis.X);
    }

    private static int[] defaultWeights(int[] source) {
        int[] values = new int[RegionMetric.values().length];
        values[RegionMetric.FIXED.ordinal()] = DEFAULT_WEIGHT_FIXED;
        values[RegionMetric.MOVING.ordinal()] = DEFAULT_WEIGHT_MOVING;
        values[RegionMetric.FAST.ordinal()] = DEFAULT_WEIGHT_FAST;
        values[RegionMetric.NORMAL.ordinal()] = DEFAULT_WEIGHT_NORMAL;
        values[RegionMetric.SMALL.ordinal()] = DEFAULT_WEIGHT_SMALL;
        if (source != null) {
            for (RegionMetric metric : RegionMetric.values()) {
                int index = metric.ordinal();
                if (index < source.length && source[index] >= 0) {
                    values[index] = source[index];
                }
            }
        }
        return values;
    }

    private static int[] defaultMinimums(int[] source) {
        int[] values = new int[RegionMetric.values().length];
        if (source != null) {
            for (RegionMetric metric : RegionMetric.values()) {
                int index = metric.ordinal();
                if (index < source.length) {
                    values[index] = Math.max(0, source[index]);
                }
            }
        }
        return values;
    }

    /** 复制区域并替换部分参数；baseScore/count 传 -1 表示保持原值。 */
    public TargetRegion with(int newBaseScore, int newCount, int[] newWeights, int[] newMinimums) {
        return new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ,
                newBaseScore < 0 ? baseScore : newBaseScore,
                newCount < 0 ? count : newCount,
                newWeights == null ? weights : newWeights,
                newMinimums == null ? minimums : newMinimums,
                axis);
    }

    public TargetRegion withAxis(TargetAxis newAxis) {
        return new TargetRegion(world, minX, minY, minZ, maxX, maxY, maxZ, baseScore,
                count, weights.clone(), minimums.clone(), newAxis == null ? axis : newAxis);
    }

    /** 改单项权重（其他项保持）。 */
    public TargetRegion withWeight(RegionMetric metric, int value) {
        int[] next = weights.clone();
        next[metric.ordinal()] = Math.max(0, value);
        return with(-1, -1, next, null);
    }

    /** 改单项保底（其他项保持）。 */
    public TargetRegion withMinimum(RegionMetric metric, int value) {
        int[] next = minimums.clone();
        next[metric.ordinal()] = Math.max(0, value);
        return with(-1, -1, null, next);
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

    public TargetAxis axis() {
        return axis;
    }

    public int weight(RegionMetric metric) {
        return weights[metric.ordinal()];
    }

    public int minimum(RegionMetric metric) {
        return minimums[metric.ordinal()];
    }

    public int minimumSum() {
        int total = 0;
        for (int value : minimums) {
            total += value;
        }
        return total;
    }

    public int[] weightsCopy() {
        return weights.clone();
    }

    public int[] minimumsCopy() {
        return minimums.clone();
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

    /** 一行展示靶子数量、权重、保底和移动轴。 */
    public String settingsLine() {
        return "靶子 " + count + " 个 · 权重 固定" + weight(RegionMetric.FIXED)
                + "/移动" + weight(RegionMetric.MOVING) + "/快速" + weight(RegionMetric.FAST)
                + "/正常" + weight(RegionMetric.NORMAL) + "/缩小" + weight(RegionMetric.SMALL)
                + " · 保底 固定" + minimum(RegionMetric.FIXED)
                + "/移动" + minimum(RegionMetric.MOVING) + "/快速" + minimum(RegionMetric.FAST)
                + "/缩小" + minimum(RegionMetric.SMALL) + " · 移动轴 " + axis.label;
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }
}
