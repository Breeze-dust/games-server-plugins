package com.archery;

import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;

import java.util.UUID;

/** 场上的一个动物靶子。x/y/z 是碰撞箱中心，width/height 取自动物实体。 */
public final class TargetInstance {

    public final UUID entityId;
    public final EntityType type;
    public double x;
    public double y;
    public double z;
    /** 每刻位移（格/刻），固定靶是 0。 */
    public double vx;
    public double vz;
    /** true = 幼年形态（缩小靶）。 */
    public final boolean small;
    /** 移动速度（格/秒），固定靶是 0。 */
    public final double speed;
    public final boolean fast;
    public final long spawnedAt;
    /** 每 tick 从实体刷新，幼年形态的碰撞箱可能生成后一拍才生效。 */
    public double width;
    public double height;

    public TargetInstance(UUID entityId, EntityType type, double x, double y, double z,
                          double vx, double vz, boolean small, double speed, boolean fast,
                          long spawnedAt, double width, double height) {
        this.entityId = entityId;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vz = vz;
        this.small = small;
        this.speed = speed;
        this.fast = fast;
        this.spawnedAt = spawnedAt;
        this.width = width;
        this.height = height;
    }

    public boolean moving() {
        return speed > 0.0;
    }

    /** 实体脚底位置（Bukkit 的实体坐标是脚底，碰撞箱中心要再往上半个身高）。 */
    public double feetY() {
        return y - height / 2.0;
    }

    public BoundingBox box() {
        double half = width / 2.0;
        return new BoundingBox(x - half, y - height / 2.0, z - half,
                x + half, y + height / 2.0, z + half);
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
        String animal = switch (type) {
            case COW -> "牛";
            case PIG -> "猪";
            case SHEEP -> "羊";
            case CHICKEN -> "鸡";
            case RABBIT -> "兔";
            default -> type.name();
        };
        String age = small ? "幼年" : "成年";
        String motion = !moving() ? "固定" : (fast ? "快速移动" : "移动");
        return age + animal + "·" + motion + "靶";
    }
}
