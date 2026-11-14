package com.cloudwalk;

import org.bukkit.entity.BlockDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一名玩家的一局跑酷。path 顺序固定为 [当前方块, 队列1, 队列2, ...]。 */
public final class ParkourSession {

    public final UUID playerId;
    public final String playerName;
    public final int lane;
    public final LaneBox box;
    public final CloudWalkSettings.Difficulty difficulty;
    public final List<BlockPos> path = new ArrayList<>();

    /** 进入路径末端方块的方向，下一块按它来算前 / 左 / 右。 */
    public Direction heading;
    public int score;
    public boolean finished;

    /** 标记「下一个目标方块」的发光方块展示实体。 */
    public BlockDisplay glow;

    public ParkourSession(UUID playerId, String playerName, int lane, LaneBox box,
                          CloudWalkSettings.Difficulty difficulty) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.lane = lane;
        this.box = box;
        this.difficulty = difficulty;
    }

    public BlockPos current() {
        return path.get(0);
    }
}
