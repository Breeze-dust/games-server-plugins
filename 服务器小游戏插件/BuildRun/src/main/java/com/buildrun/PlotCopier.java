package com.buildrun;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「复制场地 / 清回原样」的分帧任务：每 tick 只处理 budget 个方块，避免一次复制 8 块场地把服务器卡住。
 * <p>
 * 复制：把模板区域（src）原样写到每个目标区域（dests）；写之前记录目标区域原来的方块
 * （空气用位图、非空气用稀疏表，省内存），局终按记录还原。
 * 还原：目标区域每格 = 复制前记录的原方块；没记录过 = 当初"和模板一样、没被写过"，写回模板那一格即可
 * （模板区域永不被修改）。只还原真正复制过的格子，所以复制中途被 /br stop 也能干净退出。
 */
public final class PlotCopier {

    /** 进度 / 完成回调。 */
    public interface Callback {
        /** copiesDone = 已经复制完的地块数。 */
        void onProgress(int copiesDone, int totalCopies);

        void onDone();
    }

    private static final BlockData AIR = Material.AIR.createBlockData();

    private final Plugin plugin;
    private final World world;
    private final int[] src;
    private final List<int[]> dests = new ArrayList<>();
    private final List<Saved> saved = new ArrayList<>();
    private final int budget;
    private final Callback callback;

    private boolean restore;
    private boolean silent;
    private Runnable onRestoreDone;
    private int copyIdx;
    private int cursor;
    /** 已经处理过的方块总数（复制进度；还原时只回滚这些）。 */
    private long visited;
    /** 已完成地块的方块总数（用于把 visited 换算成当前地块内的下标）。 */
    private long visitedBase;
    private BukkitTask task;

    public PlotCopier(Plugin plugin, World world, int[] src, List<int[]> dests, int budget, Callback callback) {
        this.plugin = plugin;
        this.world = world;
        this.src = src;
        this.dests.addAll(dests);
        this.budget = Math.max(200, budget);
        this.callback = callback;
        for (int i = 0; i < dests.size(); i++) {
            saved.add(new Saved());
        }
    }

    public boolean isRunning() {
        return task != null;
    }

    public int totalCopies() {
        return dests.size();
    }

    /** 开始复制（分帧，主线程）。 */
    public void startCopy() {
        restore = false;
        silent = false;
        copyIdx = 0;
        cursor = 0;
        visited = 0L;
        visitedBase = 0L;
        schedule();
    }

    /** 开始把目标区域还原成复制前的样子。 */
    public void startRestore(Runnable onDone) {
        restore = true;
        silent = false;
        onRestoreDone = onDone;
        copyIdx = 0;
        cursor = 0;
        visitedBase = 0L;
        schedule();
    }

    /** 关服兜底：同步把所有已复制过的格子还原回去（不触发任何回调）。 */
    public void restoreSilently() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        restore = true;
        silent = true;
        onRestoreDone = null;
        copyIdx = 0;
        cursor = 0;
        visitedBase = 0L;
        int guard = 0;
        while (!tickBody() && guard++ < 10000) {
            // 同步跑完
        }
    }

    /** 取消（不再动任何方块）。 */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void schedule() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        if (tickBody()) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (silent) {
                return;
            }
            if (restore) {
                Runnable r = onRestoreDone;
                onRestoreDone = null;
                if (r != null) {
                    r.run();
                }
            } else if (callback != null) {
                callback.onDone();
            }
        }
    }

    /** 处理一批方块；返回是否全部完成。 */
    private boolean tickBody() {
        int processed = 0;
        while (processed < budget) {
            if (copyIdx >= dests.size()) {
                return true;
            }
            if (restore && visitedBase >= visited) {
                return true;
            }
            int[] dst = dests.get(copyIdx);
            int sx = dst[3] - dst[0] + 1;
            int sy = dst[4] - dst[1] + 1;
            int sz = dst[5] - dst[2] + 1;
            int volume = sx * sy * sz;
            if (cursor >= volume) {
                visitedBase += volume;
                copyIdx++;
                cursor = 0;
                if (!restore && callback != null) {
                    callback.onProgress(copyIdx, dests.size());
                }
                continue;
            }
            if (restore && visitedBase + cursor >= visited) {
                return true;
            }

            int local = cursor;
            int x = dst[0] + local % sx;
            int t = local / sx;
            int z = dst[2] + t % sz;
            int y = dst[1] + t / sz;

            int srcX = src[0] + (x - dst[0]);
            int srcZ = src[2] + (z - dst[2]);

            Block dstBlock = world.getBlockAt(x, y, z);
            Saved sv = saved.get(copyIdx);

            if (restore) {
                BlockData target;
                if (sv.air.get(local)) {
                    target = AIR;
                } else {
                    BlockData old = sv.blocks.get(local);
                    target = (old != null) ? old : world.getBlockAt(srcX, y, srcZ).getBlockData();
                }
                if (!dstBlock.getBlockData().equals(target)) {
                    dstBlock.setBlockData(target, false);
                }
            } else {
                BlockData from = world.getBlockAt(srcX, y, srcZ).getBlockData();
                BlockData current = dstBlock.getBlockData();
                if (!current.equals(from)) {
                    if (current.getMaterial().isAir()) {
                        sv.air.set(local);
                    } else {
                        sv.blocks.put(local, current);
                    }
                    dstBlock.setBlockData(from, false);
                }
            }
            cursor++;
            if (!restore) {
                visited++;
            }
            processed++;
        }
        return false;
    }

    /** 每块场地复制前的原方块：空气用位图，非空气用稀疏表。 */
    private static final class Saved {
        final BitSet air = new BitSet();
        final Map<Integer, BlockData> blocks = new HashMap<>();
    }
}
