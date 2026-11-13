package com.cloudwalk;

import org.bukkit.Location;
import org.bukkit.World;

/** 一条通道的可生成区域（闭区间方块坐标）。 */
public record LaneBox(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {

    public boolean contains(BlockPos pos) {
        return pos.x() >= minX && pos.x() <= maxX
                && pos.y() >= minY && pos.y() <= maxY
                && pos.z() >= minZ && pos.z() <= maxZ;
    }

    /** 只比 XZ，并留 2 格余量：玩家站在区域最边上的方块、身体有一部分伸出去时不该判出界。 */
    public boolean containsXZ(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return false;
        }
        return loc.getX() >= minX - 2.0 && loc.getX() < maxX + 3.0
                && loc.getZ() >= minZ - 2.0 && loc.getZ() < maxZ + 3.0;
    }

    public LaneBox translate(int dx, int dy, int dz) {
        return new LaneBox(world, minX + dx, maxX + dx, minY + dy, maxY + dy, minZ + dz, maxZ + dz);
    }
}
