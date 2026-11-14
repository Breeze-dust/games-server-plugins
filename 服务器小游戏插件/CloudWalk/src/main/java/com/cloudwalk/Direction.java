package com.cloudwalk;

/** 水平四向。dx/dz 是世界坐标轴上的单位位移。 */
public enum Direction {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    public final int dx;
    public final int dz;

    Direction(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    /** 面朝该方向时的左手边。 */
    public Direction left() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
        };
    }

    /** 面朝该方向时的右手边。 */
    public Direction right() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    /** 该方向对应的原版 yaw（0=南 90=西 180=北 270=东）。 */
    public float yaw() {
        return switch (this) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
        };
    }

    /** 玩家水平朝向（yaw）→ 最近的四个方向。原版 yaw：0=南 90=西 180=北 270=东。 */
    public static Direction fromYaw(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        int quarter = Math.round(normalized / 90f) % 4;
        return switch (quarter) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }

    /** 相邻两格之间的位移方向；非正南北东西时返回 null。 */
    public static Direction between(BlockPos from, BlockPos to) {
        int sx = Integer.signum(to.x() - from.x());
        int sz = Integer.signum(to.z() - from.z());
        for (Direction d : values()) {
            if (d.dx == sx && d.dz == sz && (sx != 0 || sz != 0)) {
                return d;
            }
        }
        return null;
    }
}
