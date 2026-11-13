package com.cloudwalk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 路径生成：从路径末端向前 / 左 / 右试探，水平 3~4 格、高度 -1/0/+1。
 * 候选先洗牌再逐个过滤，避免「随机重试」在小区域里空转。
 */
public final class PathGenerator {

    private static final int[] HEIGHT_STEPS = {-1, 0, 1};

    private final Random random = new Random();
    private final int[] distances;
    private final boolean allow4JumpUp;

    public PathGenerator(int[] distances, boolean allow4JumpUp) {
        this.distances = distances.clone();
        this.allow4JumpUp = allow4JumpUp;
    }

    /**
     * @param tail     路径末端方块
     * @param heading  进入 tail 的方向（tail 的「前方」）
     * @param occupied 当前路径上已有方块（当前 + 队列）
     * @return 合法的新方块位置；没有可用位置时返回 null
     */
    public BlockPos next(BlockPos tail, Direction heading, Set<BlockPos> occupied, LaneBox box) {
        List<BlockPos> candidates = new ArrayList<>(18);
        for (Direction dir : new Direction[]{heading, heading.left(), heading.right()}) {
            for (int distance : distances) {
                for (int dy : HEIGHT_STEPS) {
                    if (!allow4JumpUp && distance == 4 && dy == 1) {
                        continue;
                    }
                    candidates.add(tail.offset(dir.dx * distance, dy, dir.dz * distance));
                }
            }
        }
        Collections.shuffle(candidates, random);
        for (BlockPos pos : candidates) {
            if (box.contains(pos) && !occupied.contains(pos)) {
                return pos;
            }
        }
        return null;
    }
}
