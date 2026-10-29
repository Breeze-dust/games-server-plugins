package com.football;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 足球本体：一个 BlockDisplay 展示 + 自写的方块碰撞物理（服务端每 2 tick 推进一次）。
 * 球不使用原版实体物理（展示实体不能被"踢"），速度/重力/反弹全部自己算：
 * 撞墙按命中面法线反射（乘 bounce），落地反弹 + 滚动摩擦，低于 rest-speed 视为静止。
 * 视觉用 BlockDisplay（整个方块模型居中在实体位置）：ItemDisplay + NONE 变换实测在客户端
 * 只渲染阴影不渲染物品模型，方块模型不走物品变换那条渲染路径。
 */
public class FootballBall {

    private final FootballPlugin plugin;
    private BlockDisplay display;         // 视觉（方块模型居中于实体位置，带发光 + 全亮度便于看清）
    private Vector velocity = new Vector(0, 0, 0);
    private double scale;
    private double radius;                // 碰撞半径 = 视觉半宽（scale/2），必须与显示实体的方块半边长一致

    public FootballBall(FootballPlugin plugin) {
        this.plugin = plugin;
    }

    /** 在开球点生成/重生球（旧实体先清掉）。material 必须是方块材质。 */
    public void spawn(Location at, Material material, double scale) {
        remove();
        this.scale = scale;
        // 碰撞箱必须等于视觉方块半边长（scale/2），否则球会陷进地板/嵌进墙里（0.4 系数时代真事）
        this.radius = Math.max(0.1, scale / 2.0);
        World w = at.getWorld();
        display = w.spawn(at.clone().add(0, radius + 0.05, 0), BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setBillboard(Display.Billboard.FIXED);
            d.setGlowing(true);
            d.setViewRange(1.5f);
            d.setShadowRadius(0.4f);
            d.setBrightness(new Display.Brightness(15, 15)); // 全亮度：背光/夜里也是纯白
            d.setPersistent(false); // 不落盘：关服/崩溃后由开赛流程重新生成
            // 方块模型居中在实体位置（scale = 方块边长）；旋转时平移必须跟着转，见 centeredTransformation
            d.setTransformation(centeredTransformation(new Quaternionf()));
        });
        velocity = new Vector(0, 0, 0);
    }

    public void remove() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        display = null;
        velocity = new Vector(0, 0, 0);
    }

    public boolean isAlive() {
        return display != null && display.isValid();
    }

    /** 球心位置。 */
    public Location location() {
        return display == null ? null : display.getLocation();
    }

    /** 球与某点（玩家脚部+1 格高的躯干中心）的水平距离。 */
    public double distanceTo(Location loc) {
        Location l = location();
        if (l == null || l.getWorld() == null || loc == null
                || !l.getWorld().equals(loc.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = l.getX() - loc.getX();
        double dy = l.getY() - loc.getY();
        double dz = l.getZ() - loc.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean isMoving() {
        return velocity.lengthSquared() > 0.0001;
    }

    /** 冻结在原地（开球阶段：球钉在开球点，踢到才解冻）。 */
    public void freeze(Location at) {
        if (display == null) {
            return;
        }
        if (at != null && at.getWorld() != null) {
            display.teleport(at.clone().add(0, supportHeight() + 0.05, 0));
        }
        velocity = new Vector(0, 0, 0);
    }

    /** 设置速度（踢球/开球/带球）。 */
    public void setVelocity(Vector v) {
        velocity = v.clone();
    }

    public Vector velocity() {
        return velocity.clone();
    }

    /** 物理推进一步（period=两次调用的 tick 数）。 */
    public void step(int period, double gravity, double drag, double bounce, double groundFriction, double restSpeed) {
        if (display == null || !display.isValid()) {
            return;
        }
        Location loc = display.getLocation();
        World w = loc.getWorld();

        // 重力（只在下一帧会撞地时贴地，避免持续穿地 + 抖动）
        velocity.setY(velocity.getY() - gravity * period);

        // 滚动旋转先于碰撞更新：贴地/贴墙距离要用旋转后方块的最低角，碰撞箱才能跟着可见方块走。
        // 只有水平速度有意义时才转：静止时轴向量是零向量，JOML rotateAxis 会把四元数写成全 NaN
        // （实测 joml 1.10.8），NaN 发给客户端后模型永久不渲染、只剩阴影。
        double hSpeed = Math.hypot(velocity.getX(), velocity.getZ());
        if (hSpeed > 0.05) {
            Quaternionf rot = display.getTransformation().getLeftRotation();
            float angle = (float) Math.toRadians(hSpeed * 22.0 * period);
            Vector dir = new Vector(velocity.getZ(), 0, -velocity.getX()).normalize();
            rot.rotateAxis(angle, new org.joml.Vector3f((float) dir.getX(), 0f, (float) dir.getZ()));
            display.setTransformation(centeredTransformation(rot));
        }

        // 旋转后 8 个角相对球心的偏移：方块是刚体，滚动时最低点在 scale/2 ~ 0.87*scale 之间变化，
        // 碰撞贴合距离取最深角，否则方块角会扎进地面/墙里（表现为"球滚到地下去"）
        Vector3f[] corners = rotatedCorners();
        double support = extentAlong(corners, new Vector(0, -1, 0));
        if (!Double.isFinite(support) || support < 0.05) {
            support = radius; // 四元数异常时的保底，绝不把球心算成 NaN
        }

        Location next = loc.clone().add(velocity.clone().multiply(period));
        // 竖直：向下时用射线找地面
        if (velocity.getY() <= 0) {
            Vector down = new Vector(0, -1, 0);
            RayTraceResult hit = w.rayTraceBlocks(loc, down, Math.max(0.2, -velocity.getY() * period + support),
                    org.bukkit.FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitPosition() != null) {
                // 落地：反弹（低速则贴地）
                double fallSpeed = -velocity.getY();
                if (fallSpeed > restSpeed * 2) {
                    velocity.setY(fallSpeed * bounce);
                } else {
                    velocity.setY(0);
                }
                // 滚动摩擦
                double fr = Math.pow(groundFriction, period);
                velocity.setX(velocity.getX() * fr);
                velocity.setZ(velocity.getZ() * fr);
                next = hit.getHitPosition().toLocation(w);
                next.setY(hit.getHitPosition().getY() + support + 0.02);
            }
        }

        // 水平撞墙：按射线命中面法线反射（把速度分量沿法线翻转 x bounce）；
        // 射线长度与贴墙距离都取方块沿该方向最深的角，视觉方块正好贴住墙面
        Vector horizontal = new Vector(velocity.getX(), 0, velocity.getZ());
        if (horizontal.lengthSquared() > 0.0001) {
            Vector dir = horizontal.clone().normalize();
            double extRay = extentAlong(corners, dir);
            RayTraceResult wall = w.rayTraceBlocks(loc, dir, horizontal.length() * period + extRay,
                    org.bukkit.FluidCollisionMode.NEVER, true);
            if (wall != null && wall.getHitBlockFace() != null) {
                Vector n = wall.getHitBlockFace().getDirection().clone();
                double extN = extentAlong(corners, n.clone().multiply(-1));
                double dot = velocity.getX() * n.getX() + velocity.getZ() * n.getZ();
                if (dot < 0) {
                    velocity.setX((velocity.getX() - 2 * dot * n.getX()) * bounce);
                    velocity.setZ((velocity.getZ() - 2 * dot * n.getZ()) * bounce);
                    // 声音反馈
                    w.playSound(wall.getHitPosition().toLocation(w),
                            org.bukkit.Sound.BLOCK_STONE_STEP, 0.6f, 1.4f);
                }
                next = wall.getHitPosition().toLocation(w).add(n.clone().multiply(extN));
                next.setY(loc.getY());
            }
        }

        // 静止收敛
        if (velocity.lengthSquared() < restSpeed * restSpeed) {
            velocity.setX(0);
            velocity.setZ(0);
        }

        display.teleport(next);
        // 视觉插值：让客户端在两次 teleport 之间平滑移动
        display.setTeleportDuration(2);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
    }

    /**
     * 生成"方块绕自身中心旋转、中心钉在实体位置"的变换。
     * 展示变换顺序是"先转模型、后平移"（渲染位 = 平移 + 旋转·缩放·模型坐标），固定平移 -s/2³
     * 只在零旋转时居中：一旦滚动，方块绕模型原点角（渲染上的最低角）翻转，随角度累积整块甩到
     * 实体位置斜下方沉进地里（表现为旋转时只露出一个角，2026-10-17 修）。
     * 平移取 (-s/2)³ 经同一四元数旋转后的向量后，渲染角相对实体位置 = rot·(±s/2)³，
     * 与 rotatedCorners() 的碰撞模型严格一致。
     */
    private Transformation centeredTransformation(Quaternionf rot) {
        float s = (float) scale;
        Vector3f offset = rot.transform(new Vector3f(-s / 2f, -s / 2f, -s / 2f));
        return new Transformation(offset, rot, new Vector3f(s, s, s), new Quaternionf());
    }

    /** 旋转后 8 个方块角相对球心的偏移（= 可见方块的碰撞外形）。 */
    private Vector3f[] rotatedCorners() {
        Quaternionf rot = display.getTransformation().getLeftRotation();
        float half = (float) (scale / 2.0);
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = rot.transform(new Vector3f(
                    (i & 1) == 0 ? -half : half,
                    (i & 2) == 0 ? -half : half,
                    (i & 4) == 0 ? -half : half));
        }
        return corners;
    }

    /** 方块沿单位向量 v 方向离球心最远的距离（碰撞贴合距离，恒 >= scale/2）。 */
    private static double extentAlong(Vector3f[] corners, Vector v) {
        double m = 0;
        for (Vector3f c : corners) {
            m = Math.max(m, c.x * v.getX() + c.y * v.getY() + c.z * v.getZ());
        }
        return m;
    }

    /** 当前姿态下球心到方块最低点的距离（贴地碰撞高度，随滚动在 scale/2 ~ 0.87*scale 间变化）。 */
    public double supportHeight() {
        if (display == null || !display.isValid()) {
            return radius;
        }
        double e = extentAlong(rotatedCorners(), new Vector(0, -1, 0));
        return (Double.isFinite(e) && e >= radius * 0.99) ? e : radius;
    }

    /** 出生点上方微调（开球冻结时球心贴地）。 */
    public void nudgeToGround() {
        if (display == null || !display.isValid()) {
            return;
        }
        Location loc = display.getLocation();
        RayTraceResult hit = loc.getWorld().rayTraceBlocks(loc, new Vector(0, -1, 0), Math.max(radius * 2, supportHeight() * 2),
                org.bukkit.FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            display.teleport(hit.getHitPosition().toLocation(loc.getWorld()).add(0, supportHeight() + 0.02, 0));
        }
    }

    public double getScale() {
        return scale;
    }

    public double getRadius() {
        return radius;
    }
}
