package com.superniuma.arena;

/** 一个房间占用的地图副本：目标世界里的区域坐标 + 已平移的 Arena。 */
public final class RoomCopy {

    private final String roomId;
    private final String arenaName;
    private final String worldName;
    private final int slot;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Arena arena;

    public RoomCopy(String roomId, String arenaName, String worldName, int slot,
                    int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Arena arena) {
        this.roomId = roomId;
        this.arenaName = arenaName;
        this.worldName = worldName;
        this.slot = slot;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.arena = arena;
    }

    public String roomId() {
        return roomId;
    }

    public String arenaName() {
        return arenaName;
    }

    public String worldName() {
        return worldName;
    }

    public int slot() {
        return slot;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** 已平移到副本坐标的 Arena，房间直接使用它。 */
    public Arena arena() {
        return arena;
    }
}
