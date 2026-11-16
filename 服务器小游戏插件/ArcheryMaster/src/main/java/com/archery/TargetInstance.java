package com.archery;

import org.bukkit.util.BoundingBox;

import java.util.UUID;

/** 场上的一个方块靶子。x/y/z 是方块中心，size 是边长。 */
public final class TargetInstance {

    public final UUID displayId;
    /** 所属区域，决定基础分和移动边界。 */
    public final TargetRegion region;
    public double x;
    public double y;
    public double z;
    /** 每刻位移（格/刻），固定靶是 0。 */
    public double vx;
    public double vz;
    /** true = 缩小方块。 */
    public final boolean small;
    /** 移动速度（格/秒），固定靶是 0。 */
    public final double speed;
    public final boolean fast;
    public final long spawnedAt;
    public final double size;
    public final int baseScore;

    public TargetInstance(UUID displayId, TargetRegion region, double x, double y, double z,
                          double vx, double vz, boolean small, double speed, boolean fast,
                          long spawnedAt, double size, int baseScore) {
        this.displayId = displayId;
        this.region = region;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vz = vz;
        this.small = small;
        this.speed = speed;
        this.fast = fast;
        this.spawnedAt = spawnedAt;
        this.size = size;
        this.baseScore = baseScore;
    }

    public boolean moving() {
        return speed > 0.0;
    }

    public BoundingBox box() {
        double half = size / 2.0;
        return new BoundingBox(x - half, y - half, z - half, x + half, y + half, z + half);
    }

    /** 区域基础分 + 移动/快速/缩小三项独立加成。 */
    public int score(ArcherySettings settings) {
        int total = baseScore;
        if (moving()) {
            total += settings.bonusMoving;
        }
        if (fast) {
            total += settings.bonusFast;
        }
        if (small) {
            total += settings.bonusSmall;
        }
        return total;
    }

    /** 给玩家看的得分构成，例如「基础 3 + 移动 1 + 缩小 2」。 */
    public String scoreBreakdown(ArcherySettings settings) {
        StringBuilder sb = new StringBuilder("基础 ").append(baseScore);
        if (moving()) {
            sb.append(" + 移动 ").append(settings.bonusMoving);
        }
        if (fast) {
            sb.append(" + 快速 ").append(settings.bonusFast);
        }
        if (small) {
            sb.append(" + 缩小 ").append(settings.bonusSmall);
        }
        return sb.toString();
    }

    public String typeName() {
        String motion = !moving() ? "固定" : (fast ? "快速移动" : "移动");
        return (small ? "缩小" : "正常") + motion + "靶";
    }
}
