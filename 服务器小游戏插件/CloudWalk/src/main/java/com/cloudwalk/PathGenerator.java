package com.cloudwalk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 路径生成：从路径末端向前 / 左 / 右试探，水平 3~4（可选 5）格、高度 -2~+1。
 *
 * <p>两类「特殊跳」：三远一高（水平 4 + 上升）和四格跳（水平 5）。
 * 它们都受 `allowSpecial` 控制（开局前几块、以及不能连续出现时传 false）。
 */
public final class PathGenerator {

    private static final int FAR_DISTANCE = 5;

    private final Random random = new Random();
    private final int[] distances;
    private final int[] heights;
    private final boolean allow4JumpUp;
    private final boolean allow4Far;
    private final double turnChance;
    private final double farChance;

    public PathGenerator(int[] distances, int[] heights, boolean allow4JumpUp, boolean allow4Far,
                         double turnChance, double farChance) {
        this.distances = distances.clone();
        this.heights = heights.clone();
        this.allow4JumpUp = allow4JumpUp;
        this.allow4Far = allow4Far;
        this.turnChance = Math.max(0.0, Math.min(1.0, turnChance));
        this.farChance = Math.max(0.0, Math.min(1.0, farChance));
    }

    /**
     * @param tail         路径末端方块
     * @param heading      进入 tail 的方向（tail 的「前方」）
     * @param occupied     当前路径上已有方块（当前 + 队列）
     * @param allowSpecial 是否允许生成特殊跳（三远一高 / 四格跳）
     * @return 合法的新方块位置；没有可用位置时返回 null
     */
    public BlockPos next(BlockPos tail, Direction heading, Set<BlockPos> occupied, LaneBox box,
                         boolean allowSpecial) {
        // 先按概率决定这一跳要不要尝试四格跳；没有可用位置就退回普通候选
        if (allowSpecial && allow4Far && farChance > 0 && random.nextDouble() < farChance) {
            BlockPos far = pick(tail, heading, occupied, box, allowSpecial, true);
            if (far != null) {
                return far;
            }
        }

        BlockPos chosen = pick(tail, heading, occupied, box, allowSpecial, false);
        if (chosen == null && !allowSpecial) {
            // 除了特殊跳已经没有别的落点：宁可破一次例，也不要让玩家卡死
            chosen = pick(tail, heading, occupied, box, true, false);
            if (chosen == null) {
                chosen = pick(tail, heading, occupied, box, true, true);
            }
        }
        return chosen;
    }

    private BlockPos pick(BlockPos tail, Direction heading, Set<BlockPos> occupied, LaneBox box,
                          boolean allowSpecial, boolean farOnly) {
        Direction[] directions = {heading, heading.left(), heading.right()};
        List<List<BlockPos>> valid = new ArrayList<>(3);
        for (int i = 0; i < directions.length; i++) {
            Direction dir = directions[i];
            boolean straight = i == 0;
            List<BlockPos> list = new ArrayList<>(distances.length * heights.length);
            for (int distance : distances) {
                boolean far = distance >= FAR_DISTANCE;
                if (far != farOnly) {
                    continue;
                }
                if (far && !straight) {
                    // 四格跳只走直行，拐弯方向不出现
                    continue;
                }
                for (int dy : heights) {
                    if (far && dy > 0) {
                        // 四格跳只出平跳 / 下降，不做「四远一高」
                        continue;
                    }
                    if (!allow4JumpUp && distance == 4 && dy > 0) {
                        continue;
                    }
                    if (!allowSpecial && distance == 4 && dy > 0) {
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
