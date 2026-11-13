package com.cloudwalk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 路径生成：从路径末端向前 / 左 / 右试探，水平 3~4 格、高度 -1/0/+1。
 * 候选先洗牌再逐个过滤，避免「随机重试」在小区域里空转。
 */
public final class PathGenerator {

    private final Random random = new Random();
    private final int[] distances;
    private final int[] heights;
    private final boolean allow4JumpUp;
    private final double turnChance;

    public PathGenerator(int[] distances, int[] heights, boolean allow4JumpUp, double turnChance) {
        this.distances = distances.clone();
        this.heights = heights.clone();
        this.allow4JumpUp = allow4JumpUp;
        this.turnChance = Math.max(0.0, Math.min(1.0, turnChance));
    }

    /**
     * @param tail     路径末端方块
     * @param heading  进入 tail 的方向（tail 的「前方」）
     * @param occupied 当前路径上已有方块（当前 + 队列）
     * @return 合法的新方块位置；没有可用位置时返回 null
     */
    public BlockPos next(BlockPos tail, Direction heading, Set<BlockPos> occupied, LaneBox box) {
        Direction[] directions = {heading, heading.left(), heading.right()};
        List<List<BlockPos>> valid = new ArrayList<>(3);
        for (Direction dir : directions) {
            List<BlockPos> list = new ArrayList<>(distances.length * heights.length);
            for (int distance : distances) {
                for (int dy : heights) {
                    if (!allow4JumpUp && distance == 4 && dy > 0) {
                        continue;
                    }
                    BlockPos pos = tail.offset(dir.dx * distance, dy, dir.dz * distance);
                    if (box.contains(pos) && !occupied.contains(pos)) {
                        list.add(pos);
                    }
                }
            }
            valid.add(list);
        }

        // 方向权重：直行 1-turnChance，左/右各 turnChance/2
        double[] weights = {1.0 - turnChance, turnChance / 2.0, turnChance / 2.0};
        double total = 0.0;
        for (int i = 0; i < 3; i++) {
            if (!valid.get(i).isEmpty()) {
                total += weights[i];
            }
        }

        int chosen = -1;
        if (total > 0.0) {
            double roll = random.nextDouble() * total;
            for (int i = 0; i < 3; i++) {
                if (valid.get(i).isEmpty()) {
                    continue;
                }
                roll -= weights[i];
                if (roll <= 0.0) {
                    chosen = i;
                    break;
                }
            }
        }
        if (chosen < 0) {
            // 所有可用方向权重都是 0（turn-chance=0 且直行被挡）：在可用方向里等概率兜底
            List<Integer> available = new ArrayList<>(3);
            for (int i = 0; i < 3; i++) {
                if (!valid.get(i).isEmpty()) {
                    available.add(i);
                }
            }
            if (available.isEmpty()) {
                return null;
            }
            chosen = available.get(random.nextInt(available.size()));
        }

        List<BlockPos> pool = valid.get(chosen);
        return pool.get(random.nextInt(pool.size()));
    }
}
