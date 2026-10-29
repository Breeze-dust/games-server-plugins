package com.colorparty;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 地板形状绘制：把地板划成尽量方正的网格色块，每格 = 底色方块 + 叠加一个随机形状
 * （圆环 / 方框 / 十字 / 叉叉 / 菱形 / 实心圆），形状用候选池里的另一种颜色画。
 * 每种候选颜色至少占一格底色，指定颜色在场上表现为"几块区域"（自己的格子 + 其他格子里的同色形状）。
 * 所有 y 层共用同一张 (x,z) 图案；塌陷只认颜色，与图案无关。
 */
final class ColorPartyFloor {

    private ColorPartyFloor() {
    }

    /** 形状种类数：0=圆环 1=方框 2=十字 3=叉叉 4=菱形 5=实心圆。 */
    private static final int SHAPES = 6;

    /** 把地板刷成形状拼布。palette = 候选池的混凝土材质，target = 本回合指定色（场地太小时优先保留）。 */
    static void paint(World w, int[] b, List<Material> palette, Material target) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int minX = b[0];
        int minZ = b[2];
        int width = b[3] - minX + 1;
        int depth = b[5] - minZ + 1;
        int k = palette.size();
        // 目标每格面积 ≈ 地板面积 / 颜色数 → 格子尽量方正；格数不够 k 就往宽的那边加
        double cell = Math.sqrt((double) width * depth / Math.max(1, k));
        int cols = Math.max(1, Math.min(width, (int) Math.round((double) width / cell)));
        int rows = Math.max(1, Math.min(depth, (int) Math.round((double) depth / cell)));
        while (cols * rows < k && (cols < width || rows < depth)) {
            if ((double) width / cols >= (double) depth / rows && cols < width) {
                cols++;
            } else {
                rows++;
            }
        }
        int cells = cols * rows;
        // 场地太小铺不满 k 种：保留指定色，其余随机裁到格子数，保证指定色一定在场上
        List<Material> mats = palette;
        if (cells < k) {
            mats = new ArrayList<>(palette);
            Collections.shuffle(mats, rnd);
            mats.remove(target);
            while (mats.size() > cells - 1) {
                mats.remove(mats.size() - 1);
            }
            mats.add(target);
        }
        // 每格底色：每种颜色先各占一格，剩余格子随机补，再整体洗牌
        List<Material> cellColors = new ArrayList<>(mats);
        while (cellColors.size() < cells) {
            cellColors.add(mats.get(rnd.nextInt(mats.size())));
        }
        Collections.shuffle(cellColors, rnd);
        for (int y = b[1]; y <= b[4]; y++) {
            for (int ri = 0; ri < rows; ri++) {
                int z0 = minZ + depth * ri / rows;
                int z1 = minZ + depth * (ri + 1) / rows - 1;
                for (int ci = 0; ci < cols; ci++) {
                    int x0 = minX + width * ci / cols;
                    int x1 = minX + width * (ci + 1) / cols - 1;
                    Material bg = cellColors.get(ri * cols + ci);
                    stamp(w, y, x0, z0, x1, z1, bg, pickOverlay(mats, bg, rnd), rnd.nextInt(SHAPES));
                }
            }
        }
    }

    /** 叠加色：从候选池里挑一个不等于底色的颜色（池只有一种颜色时退化为同色 = 纯色格）。 */
    private static Material pickOverlay(List<Material> mats, Material bg, ThreadLocalRandom rnd) {
        if (mats.size() <= 1) {
            return bg;
        }
        Material m;
        do {
            m = mats.get(rnd.nextInt(mats.size()));
        } while (m == bg);
        return m;
    }

    /** 单格绘制：形状区域刷叠加色，其余刷底色（物理更新关闭，防止连锁刷屏）。 */
    private static void stamp(World w, int y, int x0, int z0, int x1, int z1, Material bg, Material ov, int shape) {
        int cw = x1 - x0 + 1;
        int cd = z1 - z0 + 1;
        if (Math.min(cw, cd) < 3) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    w.getBlockAt(x, y, z).setType(bg, false);
                }
            }
            return;
        }
        double cx = (cw - 1) / 2.0;
        double cz = (cd - 1) / 2.0;
        double rOut = Math.min(cw, cd) / 2.0;
        // 大格子（短边 ≥ 9）用 3 格宽的十字 / 叉叉，小格子用 1~2 格宽
        double bandT = Math.min(cw, cd) >= 9 ? 1.0 : 0.0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dx = x - x0 - cx;
                double dz = z - z0 - cz;
                boolean over;
                switch (shape) {
                    case 0 -> { // 圆环
                        double dist = Math.hypot(dx, dz);
                        over = dist <= rOut && dist >= rOut * 0.55;
                    }
                    case 1 -> { // 方框
                        int lx = x - x0;
                        int lz = z - z0;
                        over = lx >= 1 && lx <= cw - 2 && lz >= 1 && lz <= cd - 2
                                && (lx == 1 || lx == cw - 2 || lz == 1 || lz == cd - 2);
                    }
                    case 2 -> over = Math.abs(dx) <= bandT + 0.5 || Math.abs(dz) <= bandT + 0.5; // 十字
                    case 3 -> over = Math.abs(Math.abs(dx) - Math.abs(dz)) <= bandT + 0.5; // 叉叉
                    case 4 -> over = Math.abs(dx) + Math.abs(dz) <= rOut * 0.9; // 菱形
                    default -> over = Math.hypot(dx, dz) <= rOut * 0.62; // 实心圆
                }
                w.getBlockAt(x, y, z).setType(over ? ov : bg, false);
            }
        }
    }
}
