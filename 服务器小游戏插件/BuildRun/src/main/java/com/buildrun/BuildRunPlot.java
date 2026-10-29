package com.buildrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * 某个玩家在本局里的那块场地（模板的一次复制体）。
 * <p>
 * 建造阶段由建造者用道具设置起点 / 终点；两条都设好才算"可跑"。
 * 起点 = 跑者落地的脚部坐标（点到方块的上表面中央），终点 = 点到方块的坐标（判定用方块中心）。
 * 设点时把点到的那块方块刷成标志色：起点 = 绿色羊毛、终点 = 粉红色羊毛；原材质记下来，换点/重置时还原。
 */
public final class BuildRunPlot {

    /** 起点标志方块颜色。 */
    public static final Material START_MARK = Material.GREEN_WOOL;
    /** 终点标志方块颜色。 */
    public static final Material END_MARK = Material.PINK_WOOL;

    private final UUID builder;
    /** 复制体序号（1 = 紧贴模板的第一块）。 */
    private final int index;
    /** 复制体范围 {minX,minY,minZ,maxX,maxY,maxZ}。 */
    private final int[] bounds;
    private final World world;
    private final Location spawn;
    private Location start;
    private Location end;
    /** 起点标志方块的位置 + 刷羊毛之前的材质（换点/重置时还原）。 */
    private Location startMark;
    private Material startMarkOld;
    /** 终点标志方块的位置 + 刷羊毛之前的材质。 */
    private Location endMark;
    private Material endMarkOld;

    public BuildRunPlot(UUID builder, int index, int[] bounds, World world, Location spawn) {
        this.builder = builder;
        this.index = index;
        this.bounds = bounds;
        this.world = world;
        this.spawn = spawn;
    }

    public UUID getBuilder() {
        return builder;
    }

    public int getIndex() {
        return index;
    }

    public int[] getBounds() {
        return bounds;
    }

    public World getWorld() {
        return world;
    }

    public Location getSpawn() {
        return spawn;
    }

    public Location getStart() {
        return start;
    }

    public void setStart(Location start) {
        this.start = start;
    }

    public Location getEnd() {
        return end;
    }

    public void setEnd(Location end) {
        this.end = end;
    }

    /**
     * 把起点方块刷成绿色羊毛，并记下原材质（同一块再设一次不会丢原材质）。
     */
    public void markStart(Block block) {
        if (block == null) {
            return;
        }
        if (!sameBlock(block.getLocation(), startMark)) {
            clearStartMark();
            startMark = block.getLocation();
            startMarkOld = block.getType();
        }
        block.setType(START_MARK, false);
    }

    /** 把终点方块刷成粉红色羊毛，并记下原材质。 */
    public void markEnd(Block block) {
        if (block == null) {
            return;
        }
        if (!sameBlock(block.getLocation(), endMark)) {
            clearEndMark();
            endMark = block.getLocation();
            endMarkOld = block.getType();
        }
        block.setType(END_MARK, false);
    }

    /** 起点标志方块还原成原来的材质（如果它还是绿色羊毛的话）。 */
    public void clearStartMark() {
        restore(startMark, startMarkOld, START_MARK);
        startMark = null;
        startMarkOld = null;
    }

    /** 终点标志方块还原成原来的材质。 */
    public void clearEndMark() {
        restore(endMark, endMarkOld, END_MARK);
        endMark = null;
        endMarkOld = null;
    }

    /** 起点 + 终点标志都还原（重置/换场时用）。 */
    public void clearMarks() {
        clearStartMark();
        clearEndMark();
    }

    /** 这个方块是不是本场地的起点/终点标志（绿色羊毛 / 粉红色羊毛）。 */
    public boolean isMarkBlock(Block block) {
        if (block == null) {
            return false;
        }
        Location b = block.getLocation();
        return sameBlock(b, startMark) || sameBlock(b, endMark);
    }

    private static void restore(Location mark, Material old, Material expect) {
        if (mark == null || mark.getWorld() == null) {
            return;
        }
        Block block = mark.getBlock();
        if (block.getType() != expect) {
            return;
        }
        block.setType(old == null || old.isAir() ? Material.AIR : old, false);
    }

    // ------------------------------------------------------------------
    // 屏障：场地里的屏障既不许拆、也不许放（用户口径：自己也不能放屏障）
    // ------------------------------------------------------------------

    /** 两个 Location 是否指向同一块方块（只比世界 + 方块坐标）。 */
    private static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** 起点 + 终点都设了才算可跑。 */
    public boolean isReady() {
        return start != null && end != null;
    }

    /** 这个方块坐标是否在自己场地的范围内（含边界）。 */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return false;
        }
        return containsBlock(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean containsBlock(int x, int y, int z) {
        return x >= bounds[0] && x <= bounds[3]
                && y >= bounds[1] && y <= bounds[4]
                && z >= bounds[2] && z <= bounds[5];
    }

    /** 场地地板那一层的 Y（= 复制体最低点，也是判失败那条线的底面）。 */
    public int getFloorY() {
        return bounds[1];
    }

    /**
     * 这块方块是不是场地地板那一层（只认最低这一层）。
     * <p>
     * 往下的方块本来就在场地范围外（{@link #contains} 为 false），所以"拆穿地板"只可能从这一层下手。
     */
    public boolean isFloor(Location loc) {
        return loc != null && contains(loc) && loc.getBlockY() == bounds[1];
    }

    /** 设置起点时用的"脚部位置"：方块上表面中央，朝向沿用玩家当前朝向。 */
    public static Location feetOn(org.bukkit.block.Block block, Location playerLoc) {
        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        if (playerLoc != null) {
            loc.setYaw(playerLoc.getYaw());
            loc.setPitch(playerLoc.getPitch());
        }
        return loc;
    }

    /**
     * 跑者是否已经"碰到地板 / 跑出场地"（判失败）。
     *
     * @param tolerance 地板容差：脚部 Y 低于「场地最低点 + 1 + tolerance」即判失败
     */
    public boolean isOut(Location loc, int tolerance) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return true;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (y < bounds[1] + 1 + tolerance) {
            return true;
        }
        return x < bounds[0] || x > bounds[3] || z < bounds[2] || z > bounds[5];
    }

    /**
     * 跑者是不是正踩在终点方块（粉红色羊毛）上面：脚部方块 = 终点方块正上方那一格。
     * <p>
     * 「站满 1 秒才算通过」由 {@link BuildRunManager} 计时，这里只判位置。
     */
    public boolean isStandingOnEnd(Location loc) {
        if (loc == null || end == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
            return false;
        }
        return loc.getBlockX() == end.getBlockX()
                && loc.getBlockZ() == end.getBlockZ()
                && loc.getBlockY() == end.getBlockY() + 1;
    }

    /** 终点坐标是否为"安全高度"（不在地板线以下）。 */
    public boolean isSafeHeight(Location loc, int tolerance) {
        return loc != null && loc.getBlockY() >= bounds[1] + 1 + tolerance;
    }
}
