package com.dontdoit.criteria;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** 每个对局玩家的时间型状态（移动/疾跑/潜行/视角/死亡等），供 move_time 等词条使用。 */
public final class PlayerState {

    public long lastMove;
    public long lastRotate;
    public long lastJump;
    public long lastSprint;
    public long lastSneak;
    public long lastSwim;
    public long lastGlide;
    public long lastFall;
    public long lastOnAir;

    public long moveStart;
    public long rotateStart;
    public long sprintStart;
    public long sneakStart;
    public long swimStart;
    public long glideStart;
    public long fallStart;
    public long onAirStart;

    public Location lastLocation;
    public float lastYaw;
    public float lastPitch;

    public long deathTick = -1;
    public long lastDeathTick = -1;
    public long bornTick;

    public PlayerState(Player player, long now) {
        this.lastLocation = player.getLocation().clone();
        this.lastYaw = player.getLocation().getYaw();
        this.lastPitch = player.getLocation().getPitch();
        this.bornTick = now;
        // 从“现在”开始计时，否则“10 秒不移动”这类词条第一次永远不会触发
        this.lastMove = now;
        this.lastRotate = now;
        this.lastJump = now;
        this.lastSprint = now;
        this.lastSneak = now;
        this.lastSwim = now;
        this.lastGlide = now;
        this.lastFall = now;
        this.lastOnAir = now;
        if (!player.isOnGround()) {
            this.onAirStart = now;
            this.lastOnAir = now;
        }
        if (player.isSprinting()) {
            this.sprintStart = now;
        }
        if (player.isSneaking()) {
            this.sneakStart = now;
        }
    }

    /** 每次判定任务（约每 5 tick）刷新一次。 */
    public void tick(Player player, long now) {
        Location loc = player.getLocation();
        boolean moved = lastLocation == null
                || lastLocation.getWorld() != loc.getWorld()
                || lastLocation.distanceSquared(loc) > 0.0004;
        if (moved) {
            lastMove = now;
            if (moveStart == 0) {
                moveStart = now;
            }
        } else {
            moveStart = 0;
        }

        boolean rotated = Math.abs(loc.getYaw() - lastYaw) > 0.5f
                || Math.abs(loc.getPitch() - lastPitch) > 0.5f;
        if (rotated) {
            lastRotate = now;
            if (rotateStart == 0) {
                rotateStart = now;
            }
        } else {
            rotateStart = 0;
        }

        lastSprint = track(player.isSprinting(), lastSprint, now, s -> sprintStart = s, sprintStart);
        lastSneak = track(player.isSneaking(), lastSneak, now, s -> sneakStart = s, sneakStart);
        lastSwim = track(player.isSwimming(), lastSwim, now, s -> swimStart = s, swimStart);
        lastGlide = track(player.isGliding(), lastGlide, now, s -> glideStart = s, glideStart);
        lastFall = track(player.getFallDistance() > 0.0f, lastFall, now, s -> fallStart = s, fallStart);
        lastOnAir = track(!player.isOnGround(), lastOnAir, now, s -> onAirStart = s, onAirStart);

        lastLocation = loc.clone();
        lastYaw = loc.getYaw();
        lastPitch = loc.getPitch();
    }

    private static long track(boolean active, long lastActive, long now,
                              java.util.function.LongConsumer setStart, long currentStart) {
        if (active) {
            if (currentStart == 0) {
                setStart.accept(now);
            }
            return now;
        }
        setStart.accept(0);
        return lastActive;
    }

    public void onJump(long now) {
        lastJump = now;
    }

    public void onDeath(long now) {
        deathTick = now;
        lastDeathTick = now;
    }

    public void onRespawn() {
        deathTick = -1;
    }

    /** 持续时长（tick）：正在持续返回 now-start，否则 0。 */
    public static long continuous(long start, long now) {
        return start <= 0 ? 0 : Math.max(0, now - start);
    }

    /** 未持续时长（tick）：now - lastActive。 */
    public static long idle(long lastActive, long now) {
        return lastActive <= 0 ? 0 : Math.max(0, now - lastActive);
    }
}
