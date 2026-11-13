package com.cloudwalk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一名玩家的一局跑酷。path 顺序固定为 [当前方块, 队列1, 队列2, ...]。 */
public final class ParkourSession {

    public final UUID playerId;
    public final String playerName;
    public final int lane;
    public final LaneBox box;
    public final List<BlockPos> path = new ArrayList<>();

    /** 进入路径末端方块的方向，下一块按它来算前 / 左 / 右。 */
    public Direction heading;
    public int score;
    public long lastProgressAt;
    public boolean finished;

    public ParkourSession(UUID playerId, String playerName, int lane, LaneBox box) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.lane = lane;
        this.box = box;
    }

    public BlockPos current() {
        return path.get(0);
    }
}
