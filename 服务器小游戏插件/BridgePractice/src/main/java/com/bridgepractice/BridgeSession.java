package com.bridgepractice;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 一次练习会话：玩家、场地、本次搭过的方块、计时状态。 */
final class BridgeSession {

    final UUID playerId;
    final PracticeCourse course;
    final PlayerState savedState;
    final int fallY;
    final Set<Pos> placedBlocks = new LinkedHashSet<>();

    long startedAt;
    long lastHud;
    boolean pending;
    int runBlocks;
    double maxDisplacement;

    BridgeSession(UUID playerId, PracticeCourse course, PlayerState savedState, int fallY) {
        this.playerId = playerId;
        this.course = course;
        this.savedState = savedState;
        this.fallY = fallY;
    }

    boolean started() {
        return startedAt > 0;
    }

    long elapsed(long now) {
        return startedAt <= 0 ? 0 : Math.max(0, now - startedAt);
    }
}

/** 方块坐标（跨世界也能安全清理）。 */
record Pos(String world, int x, int y, int z) {

    static Pos of(Block block) {
        return new Pos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    static Pos of(Location location) {
        World world = location.getWorld();
        return new Pos(world == null ? "" : world.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    Block block() {
        World loaded = org.bukkit.Bukkit.getWorld(world);
        return loaded == null ? null : loaded.getBlockAt(x, y, z);
    }
}
