package com.archery;

import org.bukkit.util.BoundingBox;

import java.util.UUID;

/** 场上的一个靶子。位置是靶子中心，size 是边长（正常 1.0，缩小按配置）。 */
public final class TargetInstance {

    public final UUID displayId;
    public double x;
    public double y;
    public double z;
    /** 每刻位移（格/刻），固定靶是 0。 */
    public double vx;
    public double vz;
    public final boolean small;
    /** 移动速度（格/秒），固定靶是 0。 */
    public final double speed;
    public final boolean fast;
    public final long spawnedAt;

    public TargetInstance(UUID displayId, double x, double y, double z,
                          double vx, double vz, boolean small, double speed,
                          boolean fast, long spawnedAt) {
        this.displayId = displayId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vz = vz;
        this.small = small;
        this.speed = speed;
        this.fast = fast;
        this.spawnedAt = spawnedAt;
    }

    public boolean moving() {
        return speed > 0.0;
    }

    public double size(ArcherySettings settings) {
        return small ? settings.smallScale : 1.0;
    }

    public BoundingBox box(ArcherySettings settings) {
        double half = size(settings) / 2.0;
        return new BoundingBox(x - half, y - half, z - half, x + half, y + half, z + half);
    }

    public int score(ArcherySettings settings) {
        if (moving()) {
            if (fast) {
                return small ? settings.scoreFastSmall : settings.scoreFastNormal;
            }
            return small ? settings.scoreMovingSmall : settings.scoreMovingNormal;
        }
        return small ? settings.scoreFixedSmall : settings.scoreFixedNormal;
    }

    public String typeName() {
        if (!moving()) {
            return small ? "缩小固定靶" : "固定靶";
        }
        if (fast) {
            return small ? "缩小快速移动靶" : "快速移动靶";
        }
        return small ? "缩小移动靶" : "移动靶";
    }
}
