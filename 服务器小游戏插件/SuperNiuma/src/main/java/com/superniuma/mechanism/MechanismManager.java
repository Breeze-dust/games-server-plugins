package com.superniuma.mechanism;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.game.GameRoom;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.block.data.type.Speleothem;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 机关预览、放置、运行与还原。 */
public final class MechanismManager {

    private final SuperNiumaPlugin plugin;
    private final Map<UUID, ArrowShot> arrows = new HashMap<>();
    private final Map<UUID, String> portalInside = new HashMap<>();
    private final Set<UUID> blinded = new HashSet<>();
    private final Set<UUID> boosted = new HashSet<>();
    private final Map<UUID, Float> trafficWalkSpeed = new HashMap<>();
    private final Map<UUID, Integer> launched = new HashMap<>();
    private final Random random = new Random();

    public MechanismManager(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    public record PlaceResult(boolean success, MechanismInstance instance, String error,
                              boolean stageAdvanced) {
        public static PlaceResult ok(MechanismInstance instance) {
            return new PlaceResult(true, instance, null, false);
        }

        public static PlaceResult advance() {
            return new PlaceResult(true, null, null, true);
        }

        public static PlaceResult fail(String error) {
            return new PlaceResult(false, null, error, false);
        }
    }

    private record ArrowShot(UUID arrowId, String instanceId, GameRoom room, UUID owner,
                             Location start, int range) {
    }

    // ---------- 预览 ----------

    public void updatePreview(GameRoom room, PlacementSession session) {
        if (session.confirmed()) {
            return;
        }
        // 位置由第一次调整操作锁定；锁定前不生成、也不跟随玩家。
        Location anchor = session.anchor();
        if (anchor == null) {
            return;
        }
        anchor = clampPortalAnchor(session, anchor);
        session.setAnchor(anchor);
        List<Location> cells = cells(session.choice(), anchor, session.yawSteps(), session.pitchSteps());
        // 朝向也会改变外观（传送门/发射器/活塞等），格子没变时同样需要重建预览。
        boolean sameShape = sameCells(cells, session.ghostCells())
                && session.rendered()
                && session.renderedYawSteps() == (session.yawSteps() & 3)
                && session.renderedPitchSteps() == (session.pitchSteps() & 3);
        if (sameShape) {
            return;
        }
        clearGhosts(session);
        session.setGhostCells(cells);
        session.setRendered(anchor, session.yawSteps(), session.pitchSteps());
        spawnGhosts(room, session, cells);
    }

    /** 预览使用机关真实方块/实体，不再用染色玻璃占位。 */
    private void spawnGhosts(GameRoom room, PlacementSession session, List<Location> cells) {
        MechanismChoice choice = session.choice();
        MechanismDef def = choice.def();
        BlockFace facing = facingFor(def, session.yawSteps(), session.pitchSteps());
        Location anchor = session.anchor();
        switch (def.id()) {
            case "platform" -> {
                BlockData wool = room.woolFor(session.owner()).createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, wool);
                }
            }
            case "spike" -> {
                int bases = choice.variant().sizeX() * choice.variant().sizeZ();
                BlockData base = Material.DRIPSTONE_BLOCK.createBlockData();
                BlockData tip = Material.POINTED_DRIPSTONE.createBlockData();
                if (tip instanceof PointedDripstone dripstone) {
                    dripstone.setVerticalDirection((session.pitchSteps() & 2) == 2
                            ? BlockFace.DOWN : BlockFace.UP);
                    dripstone.setThickness(Speleothem.Thickness.TIP);
                }
                for (int i = 0; i < cells.size(); i++) {
                    spawnBlockGhost(session, cells.get(i), i < bases ? base : tip);
                }
            }
            case "dispenser" -> {
                BlockData data = Material.DISPENSER.createBlockData();
                if (data instanceof Directional directional) {
                    directional.setFacing(facing);
                }
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "piston" -> {
                BlockData data = Material.PISTON.createBlockData();
                if (data instanceof Directional directional) {
                    directional.setFacing(facing);
                }
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "spring" -> {
                BlockData iron = Material.IRON_BLOCK.createBlockData();
                BlockData glass = Material.BLUE_STAINED_GLASS.createBlockData();
                int height = (int) Math.floor(def.height());
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, iron);
                    for (int dy = 1; dy <= height; dy++) {
                        spawnBlockGhost(session, cell.clone().add(0, dy, 0), glass);
                    }
                }
            }
            case "stairs" -> {
                BlockData data = Material.OAK_STAIRS.createBlockData();
                if (data instanceof Directional directional) {
                    directional.setFacing(facing);
                }
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "mine" -> {
                BlockData data = Material.RED_CARPET.createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "ladder" -> {
                BlockData data = Material.LADDER.createBlockData();
                if (data instanceof Directional directional) {
                    directional.setFacing(facing);
                }
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "fragile" -> {
                BlockData data = Material.CRACKED_STONE_BRICKS.createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "intermittent" -> {
                BlockData data = Material.LODESTONE.createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "ice" -> {
                BlockData data = Material.ICE.createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "blind" -> {
                BlockData data = Material.BLACK_WOOL.createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "speed" -> {
                BlockData data = Material.BLUE_WOOL.createBlockData();
                for (Location cell : cells) {
                    spawnBlockGhost(session, cell, data);
                }
            }
            case "portal" -> {
                Location first = session.portalFirstAnchor();
                if (first != null && session.portalSecondStage()) {
                    spawnPortalGhost(session, first, session.portalFirstFacing());
                    spawnPortalGhost(session, anchor, facing);
                } else {
                    spawnPortalGhost(session, anchor, facing);
                }
            }
            case "traffic" -> spawnTrafficGhosts(session, anchor, facing);
            case "coin" -> spawnCoinGhost(session);
            case "bomb" -> {
                BlockData glass = Material.RED_STAINED_GLASS.createBlockData();
                for (Location cell : cells) {
                    if (sameBlock(cell, anchor)) {
                        continue;
                    }
                    spawnBlockGhost(session, cell, glass);
                }
                spawnBlockGhost(session, anchor, Material.TNT.createBlockData());
            }
            default -> {
            }
        }
    }

    private void spawnBlockGhost(PlacementSession session, Location location, BlockData data) {
        BlockDisplay display = location.getWorld().spawn(location.clone(), BlockDisplay.class, d -> {
            d.setBlock(data.clone());
            d.setPersistent(false);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setInterpolationDuration(2);
        });
        hideFromOthers(session, display);
        session.ghosts().add(display);
    }

    private void spawnCoinGhost(PlacementSession session) {
        Location center = session.anchor().clone().add(0.5, 0.15, 0.5);
        float scale = (float) session.choice().def().scale();
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(Material.GOLD_BLOCK));
            entity.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
        });
        hideFromOthers(session, display);
        session.ghosts().add(display);
    }

    private void hideFromOthers(PlacementSession session, Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(session.owner())) {
                other.hideEntity(plugin, entity);
            }
        }
    }

    private Location clampPortalAnchor(PlacementSession session, Location anchor) {
        if (!session.portalSecondStage()) {
            return anchor;
        }
        Location first = session.portalFirstAnchor();
        if (first == null) {
            return anchor;
        }
        int range = Math.max(1, session.choice().def().paramInt("range", 7));
        int half = (range - 1) / 2;
        Location out = anchor.clone();
        out.setX(Math.min(first.getBlockX() + half,
                Math.max(first.getBlockX() - half, anchor.getBlockX())));
        out.setY(Math.min(first.getBlockY() + half,
                Math.max(first.getBlockY() - half, anchor.getBlockY())));
        out.setZ(Math.min(first.getBlockZ() + half,
                Math.max(first.getBlockZ() - half, anchor.getBlockZ())));
        return out;
    }

    private void spawnPortalGhost(PlacementSession session, Location anchor, BlockFace facing) {
        BlockData data = Material.NETHER_PORTAL.createBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(axisFor(facing));
        }
        spawnBlockGhost(session, anchor, data);
        spawnBlockGhost(session, anchor.clone().add(0, 1, 0), data);
        BlockData glass = Material.BLUE_STAINED_GLASS.createBlockData();
        Location front = anchor.clone().add(facing.getModX(), 0, facing.getModZ());
        spawnBlockGhost(session, front, glass);
        spawnBlockGhost(session, front.clone().add(0, 1, 0), glass);
    }

    private Axis axisFor(BlockFace facing) {
        return facing == BlockFace.EAST || facing == BlockFace.WEST ? Axis.Z : Axis.X;
    }

    private void spawnTrafficGhosts(PlacementSession session, Location anchor, BlockFace facing) {
        spawnBlockGhost(session, anchor, Material.OAK_FENCE.createBlockData());
        spawnBlockGhost(session, anchor.clone().add(0, 1, 0), Material.WHITE_WOOL.createBlockData());
        BlockData glass = Material.BLUE_STAINED_GLASS.createBlockData();
        for (Location loc : trafficOutline(anchor, facing, session.choice().def())) {
            spawnBlockGhost(session, loc, glass);
        }
        // 物品展示框将来所在的一面，用绿色玻璃标出来，方便确认朝向。
        spawnBlockGhost(session, anchor.clone().add(0, 1, 0)
                .add(facing.getModX(), 0, facing.getModZ()), Material.GREEN_STAINED_GLASS.createBlockData());
    }

    private int[] trafficBounds(Location anchor, BlockFace facing, MechanismDef def) {
        int width = Math.max(1, def.paramInt("range-width", 7));
        int depth = Math.max(1, def.paramInt("range-depth", 3));
        int height = Math.max(1, def.paramInt("range-height", 7));
        int halfWidth = (width - 1) / 2;
        int halfHeight = (height - 1) / 2;
        int wx = anchor.getBlockX();
        int wy = anchor.getBlockY() + 1;
        int wz = anchor.getBlockZ();
        int fx = facing.getModX();
        int fz = facing.getModZ();
        int rx = -fz;
        int rz = fx;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int d = 1; d <= depth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                for (int h = -halfHeight; h <= halfHeight; h++) {
                    int x = wx + fx * d + rx * w;
                    int y = wy + h;
                    int z = wz + fz * d + rz * w;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private List<Location> trafficOutline(Location anchor, BlockFace facing, MechanismDef def) {
        int[] b = trafficBounds(anchor, facing, def);
        List<Location> out = new ArrayList<>();
        for (int x = b[0]; x <= b[3]; x++) {
            for (int y = b[1]; y <= b[4]; y++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    int extremes = 0;
                    if (x == b[0] || x == b[3]) {
                        extremes++;
                    }
                    if (y == b[1] || y == b[4]) {
                        extremes++;
                    }
                    if (z == b[2] || z == b[5]) {
                        extremes++;
                    }
                    if (extremes >= 2) {
                        out.add(new Location(anchor.getWorld(), x, y, z));
                    }
                }
            }
        }
        return out;
    }

    private boolean inTrafficArea(MechanismInstance instance, Location loc) {
        int[] b = trafficBounds(instance.anchor(), instance.facing(), instance.def());
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
    }

    public void clearPreview(PlacementSession session) {
        clearGhosts(session);
        session.setAnchor(null);
    }

    private void clearGhosts(PlacementSession session) {
        for (Entity entity : session.ghosts()) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        session.ghosts().clear();
        session.setGhostCells(List.of());
    }

    private Location computeAnchor(PlacementSession session) {
        Player player = session.player();
        if (!player.isOnline()) {
            return null;
        }
        Vector direction = horizontalFacing(player);
        Location target = player.getLocation().clone().add(direction.multiply(session.distance()));
        return target.getBlock().getLocation();
    }

    private Vector horizontalFacing(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            BlockFace facing = player.getFacing();
            direction = new Vector(facing.getModX(), 0, facing.getModZ());
        }
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 0, 1);
        }
        return direction.normalize();
    }

    /** 第一次调整操作：把机关锁定在玩家面前，并消耗这次操作。 */
    private boolean lockIfNeeded(PlacementSession session) {
        if (session.locked()) {
            return false;
        }
        Location anchor = computeAnchor(session);
        if (anchor == null) {
            return true;
        }
        session.setAnchor(anchor);
        session.setLocked(true);
        if (usesBaseYaw(session.choice().def())) {
            int base = baseYawSteps(session.player().getFacing());
            session.setBaseYawSteps(base);
            session.setYawSteps(base);
        }
        session.setGhostCells(List.of());
        Msg.actionBar(session.player(), "&a机关位置已锁定；继续用左键/右键/滚轮调整");
        return true;
    }

    public void rotateLeft(PlacementSession session) {
        if (lockIfNeeded(session)) {
            return;
        }
        RotationRule rule = session.choice().def().rotation();
        if (rule.supportsHorizontal()) {
            session.setYawSteps(session.yawSteps() + 1);
            Msg.actionBar(session.player(), "&e水平旋转 90°（" + shownYaw(session) + "°）");
        } else if (rule.supportsVertical()) {
            session.setPitchSteps(session.pitchSteps() + 2);
            Msg.actionBar(session.player(), "&e竖直翻转 180°（"
                    + (session.pitchSteps() == 2 ? "下" : "上") + "）");
        } else {
            Msg.actionBar(session.player(), "&c该机关无法旋转");
            return;
        }
        session.setGhostCells(List.of());
    }

    public void rotateRight(PlacementSession session) {
        if (lockIfNeeded(session)) {
            return;
        }
        RotationRule rule = session.choice().def().rotation();
        if (!rule.supportsVertical()) {
            Msg.actionBar(session.player(), "&c该机关无法竖直翻转");
            return;
        }
        int step = rule.verticalStepIsHalfTurn() ? 2 : 1;
        session.setPitchSteps(session.pitchSteps() + step);
        Msg.actionBar(session.player(), "&e竖直翻转 " + (step * 90) + "°（当前 "
                + (session.pitchSteps() * 90) + "°）");
        session.setGhostCells(List.of());
    }

    public void scroll(PlacementSession session, boolean forward) {
        if (lockIfNeeded(session)) {
            return;
        }
        Location anchor = session.anchor();
        if (anchor == null) {
            return;
        }
        Vector direction = session.player().getEyeLocation().getDirection();
        int stepX = 0;
        int stepY = 0;
        int stepZ = 0;
        double ax = Math.abs(direction.getX());
        double ay = Math.abs(direction.getY());
        double az = Math.abs(direction.getZ());
        if (ay >= ax && ay >= az) {
            stepY = direction.getY() >= 0 ? 1 : -1;
        } else if (ax >= az) {
            stepX = direction.getX() >= 0 ? 1 : -1;
        } else {
            stepZ = direction.getZ() >= 0 ? 1 : -1;
        }
        if (!forward) {
            stepX = -stepX;
            stepY = -stepY;
            stepZ = -stepZ;
        }
        session.setAnchor(anchor.clone().add(stepX, stepY, stepZ));
        session.setGhostCells(List.of());
        String message;
        if (stepY > 0) {
            message = "&e向上移动 1 格";
        } else if (stepY < 0) {
            message = "&e向下移动 1 格";
        } else {
            message = forward ? "&e向前移动 1 格" : "&e向后移动 1 格";
        }
        Msg.actionBar(session.player(), message);
    }

    public void moveTo(PlacementSession session, Location anchor) {
        if (anchor == null) {
            return;
        }
        session.setAnchor(anchor.clone());
        if (!session.locked()) {
            if (usesBaseYaw(session.choice().def())) {
                int base = baseYawSteps(session.player().getFacing());
                session.setBaseYawSteps(base);
                session.setYawSteps(base);
            }
            session.setLocked(true);
        }
        session.setGhostCells(List.of());
    }

    // ---------- 放置 ----------

    public PlaceResult place(GameRoom room, PlacementSession session) {
        MechanismChoice choice = session.choice();
        MechanismDef def = choice.def();
        Location anchor = session.anchor();
        int yawSteps = session.yawSteps();
        int pitchSteps = session.pitchSteps();
        // 以最后一次真正渲染出来的预览为准，避免预览没刷新就确认导致"放下时自己转了一圈"。
        if (session.rendered()) {
            Location rendered = session.renderedAnchor();
            if (rendered != null) {
                anchor = rendered;
            }
            yawSteps = session.renderedYawSteps();
            pitchSteps = session.renderedPitchSteps();
        }
        if (anchor == null) {
            anchor = computeAnchor(session);
            if (anchor == null) {
                return PlaceResult.fail("还没有可放置的位置。");
            }
            session.setAnchor(anchor);
            session.setLocked(true);
            if (usesBaseYaw(def)) {
                int base = baseYawSteps(session.player().getFacing());
                session.setBaseYawSteps(base);
                session.setYawSteps(base);
                yawSteps = base;
            }
        }
        Arena arena = room.arena();

        if ("portal".equals(def.id())) {
            return placePortal(room, session, def, arena, anchor, yawSteps, pitchSteps);
        }

        if ("bomb".equals(def.id())) {
            if (!arena.inBuildBounds(anchor)) {
                return PlaceResult.fail("炸弹只能放在可建造范围内。");
            }
            bomb(room, anchor, def.bombRadius());
            return PlaceResult.ok(null);
        }

        List<Location> cells = cells(choice, anchor, yawSteps, pitchSteps);
        for (Location cell : cells) {
            if (!arena.inBuildBounds(cell)) {
                return PlaceResult.fail("机关超出了可建造范围。");
            }
            if (!cell.getBlock().isEmpty()) {
                return PlaceResult.fail("目标位置被方块挡住了。");
            }
            if (room.occupiedByMechanism(cell)) {
                return PlaceResult.fail("这里已经有别的机关了。");
            }
        }

        if ("mine".equals(def.id())
                && !anchor.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) {
            return PlaceResult.fail("地雷需要放在方块上。");
        }
        if ("traffic".equals(def.id())) {
            BlockFace face = facingFor(def, yawSteps, pitchSteps);
            if (!cells.get(1).getBlock().getRelative(face).isEmpty()) {
                return PlaceResult.fail("物品展示框的位置被方块挡住了。");
            }
        }

        MechanismInstance instance = new MechanismInstance(def, choice.variant(), session.owner(),
                anchor, yawSteps, pitchSteps,
                facingFor(def, yawSteps, pitchSteps));

        switch (def.id()) {
            case "spike" -> placeSpike(instance, choice, anchor, yawSteps, pitchSteps);
            case "dispenser" -> placeDispenser(instance, cells);
            case "piston" -> placeDirectional(instance, cells, Material.PISTON, instance.facing());
            case "coin" -> placeCoin(instance);
            case "spring" -> placeMaterial(instance, cells, Material.IRON_BLOCK.createBlockData());
            case "stairs" -> placeDirectional(instance, cells, Material.OAK_STAIRS, instance.facing());
            case "mine" -> placeMaterial(instance, cells, Material.RED_CARPET.createBlockData());
            case "ladder" -> placeDirectional(instance, cells, Material.LADDER, instance.facing());
            case "fragile" -> placeMaterial(instance, cells,
                    Material.CRACKED_STONE_BRICKS.createBlockData());
            case "intermittent" -> placeMaterial(instance, cells, Material.LODESTONE.createBlockData());
            case "ice" -> placeMaterial(instance, cells, Material.ICE.createBlockData());
            case "blind" -> placeMaterial(instance, cells, Material.BLACK_WOOL.createBlockData());
            case "speed" -> placeMaterial(instance, cells, Material.BLUE_WOOL.createBlockData());
            case "traffic" -> placeTraffic(instance, cells, facingFor(def, yawSteps, pitchSteps));
            default -> placePlatform(room, instance, cells);
        }
        room.addMechanism(instance);
        room.applyMechanismCollision(instance);
        return PlaceResult.ok(instance);
    }

    private void placePlatform(GameRoom room, MechanismInstance instance, List<Location> cells) {
        Material wool = room.woolFor(instance.owner());
        BlockData data = wool.createBlockData();
        for (Location cell : cells) {
            setBlock(instance, cell, data);
        }
    }

    private void placeMaterial(MechanismInstance instance, List<Location> cells, BlockData data) {
        for (Location cell : cells) {
            setBlock(instance, cell, data);
        }
    }

    private void placeDirectional(MechanismInstance instance, List<Location> cells,
                                  Material material, BlockFace facing) {
        BlockData data = material.createBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(facing);
        }
        for (Location cell : cells) {
            setBlock(instance, cell, data);
        }
    }

    private PlaceResult placePortal(GameRoom room, PlacementSession session, MechanismDef def,
                                    Arena arena, Location anchor, int yawSteps, int pitchSteps) {
        BlockFace facing = facingFor(def, yawSteps, pitchSteps);
        if (!session.portalSecondStage()) {
            String error = validateCells(room, arena, portalCells(anchor));
            if (error != null) {
                return PlaceResult.fail(error);
            }
            session.setPortalFirstAnchor(anchor);
            session.setPortalFirstFacing(facing);
            session.setPortalSecondStage(true);
            session.setGhostCells(List.of());
            return PlaceResult.advance();
        }
        Location firstAnchor = session.portalFirstAnchor();
        BlockFace firstFacing = session.portalFirstFacing();
        if (firstAnchor == null || firstFacing == null) {
            return PlaceResult.fail("第一个传送门数据丢失，请重新放置。");
        }
        Location secondAnchor = clampPortalAnchor(session, anchor);
        BlockFace secondFacing = facingFor(def, yawSteps, pitchSteps);
        List<Location> firstCells = portalCells(firstAnchor);
        List<Location> secondCells = portalCells(secondAnchor);
        // 两扇门重叠会把同一格记录两次；还原时第二次还原会把这格写成“原来的传送门”，留下残块。
        for (Location cell : secondCells) {
            for (Location first : firstCells) {
                if (sameBlock(cell, first)) {
                    return PlaceResult.fail("第二扇门和第一扇重叠了，请往外挪一格。");
                }
            }
        }
        List<Location> cells = new ArrayList<>(firstCells);
        cells.addAll(secondCells);
        String error = validateCells(room, arena, cells);
        if (error != null) {
            return PlaceResult.fail(error);
        }
        MechanismInstance instance = new MechanismInstance(def, session.choice().variant(),
                session.owner(), firstAnchor, yawSteps, pitchSteps, firstFacing);
        instance.setSecondAnchor(secondAnchor);
        instance.setSecondFacing(secondFacing);
        placePortalBlocks(instance, firstAnchor, firstFacing);
        placePortalBlocks(instance, secondAnchor, secondFacing);
        room.addMechanism(instance);
        room.applyMechanismCollision(instance);
        return PlaceResult.ok(instance);
    }

    private List<Location> portalCells(Location anchor) {
        List<Location> out = new ArrayList<>();
        out.add(anchor.clone());
        out.add(anchor.clone().add(0, 1, 0));
        return out;
    }

    private String validateCells(GameRoom room, Arena arena, List<Location> cells) {
        for (Location cell : cells) {
            if (!arena.inBuildBounds(cell)) {
                return "机关超出了可建造范围。";
            }
            if (!cell.getBlock().isEmpty()) {
                return "目标位置被方块挡住了。";
            }
            if (room.occupiedByMechanism(cell)) {
                return "这里已经有别的机关了。";
            }
        }
        return null;
    }

    private void placePortalBlocks(MechanismInstance instance, Location anchor, BlockFace facing) {
        BlockData data = Material.NETHER_PORTAL.createBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(axisFor(facing));
        }
        setBlock(instance, anchor, data);
        setBlock(instance, anchor.clone().add(0, 1, 0), data);
    }

    private void placeTraffic(MechanismInstance instance, List<Location> cells, BlockFace facing) {
        setBlock(instance, cells.get(0), Material.OAK_FENCE.createBlockData());
        setBlock(instance, cells.get(1), Material.WHITE_WOOL.createBlockData());
        Location frameBlock = cells.get(1).getBlock().getRelative(facing).getLocation();
        Location spawn = frameBlock.clone().add(0.5, 0.5, 0.5);
        ItemFrame frame = spawn.getWorld().spawn(spawn, ItemFrame.class, entity -> {
            entity.setFacingDirection(facing, true);
            entity.setItem(new ItemStack(Material.GREEN_WOOL));
            entity.setFixed(true);
            entity.setInvulnerable(true);
        });
        instance.entities().add(frame);
        instance.setLightState(0);
        instance.setLightTicks(greenTicks(instance.def()));
    }

    private int greenTicks(MechanismDef def) {
        return randomSeconds(def, "green-min-seconds", "green-max-seconds", 3, 8);
    }

    private int redTicks(MechanismDef def) {
        return randomSeconds(def, "red-min-seconds", "red-max-seconds", 3, 8);
    }

    private int randomSeconds(MechanismDef def, String minKey, String maxKey, int defMin, int defMax) {
        int min = Math.max(1, def.paramInt(minKey, defMin));
        int max = Math.max(min, def.paramInt(maxKey, defMax));
        return (min + random.nextInt(max - min + 1)) * 20;
    }

    private void updateLightFrame(MechanismInstance instance) {
        Material material = switch (instance.lightState()) {
            case 1 -> Material.YELLOW_WOOL;
            case 2 -> Material.RED_WOOL;
            default -> Material.GREEN_WOOL;
        };
        for (Entity entity : instance.entities()) {
            if (entity instanceof ItemFrame frame) {
                frame.setItem(new ItemStack(material));
            }
        }
    }

    private void tickPortal(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        Location first = instance.anchor();
        Location second = instance.secondAnchor();
        if (second == null) {
            return;
        }
        for (Player player : room.activePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean inFirst = overlapsPortal(player, first);
            boolean inSecond = overlapsPortal(player, second);
            if (!inFirst && !inSecond) {
                if (instance.id().equals(portalInside.get(uuid))) {
                    portalInside.remove(uuid);
                }
                continue;
            }
            // 玩家还没离开传送门范围时不再触发，避免两个门之间来回弹。
            if (portalInside.containsKey(uuid)) {
                continue;
            }
            Location target = inFirst ? second : first;
            BlockFace exit = inFirst ? instance.secondFacing() : instance.facing();
            portalInside.put(uuid, instance.id());
            Vector velocity = player.getVelocity();
            // 出口放在门框正前方半格，避免镜头停留在传送门方块里（原版会扭曲画面）。
            Location dest = target.clone().add(
                    0.5 + exit.getModX() * 0.55, 0.0, 0.5 + exit.getModZ() * 0.55);
            dest.setYaw(yawFor(exit));
            dest.setPitch(0.0f);
            player.teleport(dest);
            player.setVelocity(velocity);
        }
    }

    private boolean overlapsPortal(Player player, Location anchor) {
        BoundingBox box = player.getBoundingBox();
        for (Location cell : portalCells(anchor)) {
            BoundingBox cube = new BoundingBox(
                    cell.getBlockX(), cell.getBlockY(), cell.getBlockZ(),
                    cell.getBlockX() + 1.0, cell.getBlockY() + 1.0, cell.getBlockZ() + 1.0);
            if (box.overlaps(cube)) {
                return true;
            }
        }
        return false;
    }

    private float yawFor(BlockFace facing) {
        return switch (facing) {
            case EAST -> -90.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            default -> 0.0f;
        };
    }

    private void tickBlind(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        for (Player player : room.activePlayers()) {
            if (standingOn(player, instance)) {
                blinded.add(player.getUniqueId());
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, 60, 0, true, false, false));
            } else if (blinded.contains(player.getUniqueId())
                    && !onAny(room, player, "blind")) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                blinded.remove(player.getUniqueId());
            }
        }
    }

    private void tickSpeed(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        int amplifier = instance.def().paramInt("amplifier", 1);
        for (Player player : room.activePlayers()) {
            if (standingOn(player, instance)) {
                boosted.add(player.getUniqueId());
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 60, amplifier, true, false, false));
            } else if (boosted.contains(player.getUniqueId())
                    && player.isOnGround()
                    && !onAny(room, player, "speed")) {
                player.removePotionEffect(PotionEffectType.SPEED);
                boosted.remove(player.getUniqueId());
            }
        }
    }

    private boolean onAny(GameRoom room, Player player, String id) {
        for (MechanismInstance other : room.mechanisms()) {
            if (id.equals(other.def().id()) && standingOn(player, other)) {
                return true;
            }
        }
        return false;
    }

    private boolean standingOn(Player player, MechanismInstance instance) {
        Location loc = player.getLocation();
        for (PlacedBlock block : instance.blocks()) {
            Location base = block.location();
            if (loc.getBlockX() == base.getBlockX()
                    && loc.getBlockZ() == base.getBlockZ()
                    && loc.getBlockY() == base.getBlockY() + 1) {
                return true;
            }
        }
        return false;
    }

    private void tickTraffic(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        instance.setLightTicks(instance.lightTicks() - 1);
        if (instance.lightTicks() <= 0) {
            int next = (instance.lightState() + 1) % 3;
            instance.setLightState(next);
            instance.setLightTicks(next == 1
                    ? Math.max(1, instance.def().paramInt("yellow-seconds", 3)) * 20
                    : (next == 2 ? redTicks(instance.def()) : greenTicks(instance.def())));
            updateLightFrame(instance);
        }
        if (instance.lightTicks() % 10 == 0) {
            for (Location point : trafficOutline(instance.anchor(), instance.facing(), instance.def())) {
                point.getWorld().spawnParticle(Particle.END_ROD,
                        point.clone().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
            }
        }
    }

    /** 弹簧弹起/活塞击飞后的短暂窗口，红灯不再锁住位移。 */
    public boolean isLaunched(Player player) {
        Integer tick = launched.get(player.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick <= 40;
    }

    private void markLaunched(Player player) {
        launched.put(player.getUniqueId(), Bukkit.getCurrentTick());
    }

    /** 红灯区域内禁止使用方向键/跳跃（只对正在跑的玩家生效）。 */
    public boolean isTrafficRed(GameRoom room, Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return false;
        }
        for (MechanismInstance instance : room.mechanisms()) {
            if (!"traffic".equals(instance.def().id()) || instance.lightState() != 2) {
                continue;
            }
            if (inTrafficArea(instance, player.getLocation())) {
                return true;
            }
        }
        return false;
    }

    public void clearPlayerEffects(Player player) {
        if (blinded.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        if (boosted.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
        portalInside.remove(player.getUniqueId());
        launched.remove(player.getUniqueId());
        Float speed = trafficWalkSpeed.remove(player.getUniqueId());
        if (speed != null) {
            player.setWalkSpeed(speed);
        }
    }

    private void placeSpike(MechanismInstance instance, MechanismChoice choice,
                            Location anchor, int yawSteps, int pitchSteps) {
        int sizeX = choice.variant().sizeX();
        int sizeZ = choice.variant().sizeZ();
        BlockData base = Material.DRIPSTONE_BLOCK.createBlockData();
        BlockData tipData = Material.POINTED_DRIPSTONE.createBlockData();
        if (tipData instanceof PointedDripstone dripstone) {
            dripstone.setVerticalDirection((pitchSteps & 2) == 2 ? BlockFace.DOWN : BlockFace.UP);
            dripstone.setThickness(Speleothem.Thickness.TIP);
        }
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int dx = x - sizeX / 2;
                int dz = z - sizeZ / 2;
                setBlock(instance, rotated(anchor, dx, 0, dz, yawSteps, pitchSteps), base);
                setBlock(instance, rotated(anchor, dx, 1, dz, yawSteps, pitchSteps), tipData);
            }
        }
    }

    private void placeDispenser(MechanismInstance instance, List<Location> cells) {
        BlockData data = Material.DISPENSER.createBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(instance.facing());
        }
        for (Location cell : cells) {
            setBlock(instance, cell, data);
        }
    }

    private void placeCoin(MechanismInstance instance) {
        spawnCoinEntity(instance);
    }

    private void spawnCoinEntity(MechanismInstance instance) {
        MechanismDef def = instance.def();
        Location center = instance.anchor().clone().add(0.5, 0.15, 0.5);
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(Material.GOLD_BLOCK));
            float scale = (float) def.scale();
            entity.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
        });
        instance.entities().add(display);
    }

    /** 回合开始时把上一回合被吃掉的金币重新显示出来。 */
    public void respawnCoins(GameRoom room) {
        for (MechanismInstance instance : room.mechanisms()) {
            if (instance.removed() || !"coin".equals(instance.def().id())) {
                continue;
            }
            boolean live = false;
            for (Entity entity : instance.entities()) {
                if (entity.isValid()) {
                    live = true;
                    break;
                }
            }
            if (!live) {
                instance.entities().clear();
                spawnCoinEntity(instance);
            }
        }
    }

    private void setBlock(MechanismInstance instance, Location location, BlockData data) {
        Block block = location.getBlock();
        instance.blocks().add(new PlacedBlock(location.clone(),
                block.getBlockData().clone(), data.clone()));
        block.setBlockData(data, false);
    }

    // ---------- 运行 ----------

    public void tick(GameRoom room) {
        for (MechanismInstance instance : new ArrayList<>(room.mechanisms())) {
            if (instance.removed()) {
                continue;
            }
            switch (instance.def().id()) {
                case "coin" -> tickCoin(room, instance);
                case "spike" -> tickSpike(room, instance);
                case "dispenser" -> tickDispenser(room, instance);
                case "piston" -> tickPiston(room, instance);
                case "spring" -> tickSpring(room, instance);
                case "mine" -> tickMine(room, instance);
                case "fragile" -> tickFragile(room, instance);
                case "intermittent" -> tickIntermittent(room, instance);
                case "portal" -> tickPortal(room, instance);
                case "blind" -> tickBlind(room, instance);
                case "speed" -> tickSpeed(room, instance);
                case "traffic" -> tickTraffic(room, instance);
                default -> {
                }
            }
        }
        syncTrafficLocks(room);
        tickArrows();
    }

    /**
     * 红灯不冻结玩家，只是拿走方向键和跳跃键：步行速度归零，原有速度和重力照常作用。
     * 只有还在跑的玩家会被锁定，旁观者不受影响。
     */
    private void syncTrafficLocks(GameRoom room) {
        Set<UUID> active = new HashSet<>();
        for (Player player : room.activePlayers()) {
            active.add(player.getUniqueId());
        }
        for (Player player : room.onlinePlayers()) {
            UUID id = player.getUniqueId();
            boolean red = active.contains(id) && isTrafficRed(room, player);
            if (red && !trafficWalkSpeed.containsKey(id)) {
                trafficWalkSpeed.put(id, player.getWalkSpeed());
                player.setWalkSpeed(0f);
            } else if (!red) {
                Float speed = trafficWalkSpeed.remove(id);
                if (speed != null) {
                    player.setWalkSpeed(speed);
                }
            }
        }
    }

    /** 回合结束等场景下恢复所有被红灯锁住的玩家的步行速度。 */
    public void releaseTrafficLocks() {
        for (Map.Entry<UUID, Float> entry : trafficWalkSpeed.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.setWalkSpeed(entry.getValue());
            }
        }
        trafficWalkSpeed.clear();
    }

    private void tickCoin(GameRoom room, MechanismInstance instance) {
        if (instance.entities().isEmpty()) {
            return;
        }
        Location center = instance.anchor().clone().add(0.5, 0.15, 0.5);
        // 用玩家碰撞箱与金币包围盒相交判定，而不是只看脚底位置。
        BoundingBox coinBox = new BoundingBox(
                center.getX() - 0.4, center.getY() - 0.4, center.getZ() - 0.4,
                center.getX() + 0.4, center.getY() + 0.4, center.getZ() + 0.4);
        for (Player player : room.activePlayers()) {
            if (player.getBoundingBox().overlaps(coinBox)) {
                for (Entity entity : instance.entities()) {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                }
                instance.entities().clear();
                room.onCoinTouched(player, instance);
                // 保留机关实例，下回合 respawnCoins() 会重新生成实体。
                return;
            }
        }
    }

    private void tickSpike(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        // 尖刺只有尖端窄窄一截，判定盒按 hit-inset 水平收缩，避免擦到边就失败。
        double inset = hitInset(instance, 0.3);
        for (Player player : room.activePlayers()) {
            BoundingBox box = player.getBoundingBox();
            for (PlacedBlock block : instance.blocks()) {
                Location loc = block.location();
                BoundingBox cube = new BoundingBox(
                        loc.getBlockX() + inset, loc.getBlockY(), loc.getBlockZ() + inset,
                        loc.getBlockX() + 1.0 - inset, loc.getBlockY() + 1.0,
                        loc.getBlockZ() + 1.0 - inset);
                if (box.overlaps(cube)) {
                    room.onPlayerFailed(player, instance.owner(), "被尖刺扎到");
                    break;
                }
            }
        }
    }

    private double hitInset(MechanismInstance instance, double fallback) {
        return Math.max(0.0, Math.min(0.45, instance.def().param("hit-inset", fallback)));
    }

    private void tickSpring(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        for (Player player : room.activePlayers()) {
            Vector velocity = player.getVelocity();
            if (velocity.getY() > 0.05) {
                continue;
            }
            Location location = player.getLocation();
            for (PlacedBlock block : instance.blocks()) {
                Location base = block.location();
                double top = base.getBlockY() + 1.0;
                if (location.getBlockX() == base.getBlockX()
                        && location.getBlockZ() == base.getBlockZ()
                        && Math.abs(location.getY() - top) < 0.35) {
                    double launch = Math.sqrt(2.0 * 0.08 * Math.max(0.0, instance.def().height()));
                    player.setVelocity(new Vector(velocity.getX(),
                            Math.max(launch, velocity.getY()), velocity.getZ()));
                    markLaunched(player);
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_SLIME_JUMP, 1.0f, 1.2f);
                    break;
                }
            }
        }
    }

    private void tickPiston(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        BlockFace face = instance.facing();
        Location front = instance.anchor().clone()
                .add(face.getModX(), face.getModY(), face.getModZ());
        BoundingBox zone = new BoundingBox(
                front.getBlockX(), front.getBlockY(), front.getBlockZ(),
                front.getBlockX() + 1.0, front.getBlockY() + 1.0, front.getBlockZ() + 1.0);
        Vector direction = face.getDirection();
        double distance = instance.def().distance();
        for (Player player : room.activePlayers()) {
            if (!player.getBoundingBox().overlaps(zone)) {
                continue;
            }
            Vector velocity = player.getVelocity();
            if (velocity.dot(direction) > 0.25) {
                continue; // 已经在朝这个方向移动，避免每 tick 重复加速
            }
            if (Math.abs(direction.getY()) > 0.5) {
                double speed = Math.sqrt(2.0 * 0.08 * Math.max(0.5, distance));
                double y = direction.getY() > 0 ? speed : -speed;
                player.setVelocity(new Vector(velocity.getX(), y, velocity.getZ()));
            } else {
                // 水平击飞：distance 约等于想弹出去的格数，地面摩擦会吃掉大部分速度。
                double speed = Math.max(0.5, distance * 0.25);
                Vector horizontal = new Vector(direction.getX(), 0, direction.getZ())
                        .normalize().multiply(speed);
                player.setVelocity(new Vector(horizontal.getX(),
                        Math.max(velocity.getY(), 0.2), horizontal.getZ()));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
            markLaunched(player);
        }
    }

    private void tickMine(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning() || instance.triggered()) {
            return;
        }
        Location center = instance.anchor();
        // 地毯很薄：判定盒水平收缩 + 只算地毯下半层，站在旁边或从上面跳过去都不会触发。
        double inset = hitInset(instance, 0.2);
        BoundingBox trigger = new BoundingBox(
                center.getBlockX() + inset, center.getBlockY(), center.getBlockZ() + inset,
                center.getBlockX() + 1.0 - inset, center.getBlockY() + 0.5,
                center.getBlockZ() + 1.0 - inset);
        for (Player player : room.activePlayers()) {
            if (!player.getBoundingBox().overlaps(trigger)) {
                continue;
            }
            int radius = Math.max(1, instance.def().bombRadius());
            for (Player victim : new ArrayList<>(room.activePlayers())) {
                Location loc = victim.getLocation();
                if (Math.abs(loc.getBlockX() - center.getBlockX()) <= radius
                        && Math.abs(loc.getBlockY() - center.getBlockY()) <= radius
                        && Math.abs(loc.getBlockZ() - center.getBlockZ()) <= radius) {
                    room.onPlayerFailed(victim, instance.owner(), "踩到地雷");
                }
            }
            instance.setTriggered(true);
            for (PlacedBlock block : instance.blocks()) {
                block.location().getBlock().setType(Material.AIR, false);
            }
            center.getWorld().spawnParticle(Particle.EXPLOSION,
                    center.clone().add(0.5, 0.5, 0.5), 2);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            return;
        }
    }

    private void tickFragile(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        int delayTicks = Math.max(1, (int) Math.round(
                instance.def().param("break-delay-seconds", 1.0) * 20.0));
        for (PlacedBlock block : instance.blocks()) {
            Location loc = block.location();
            if (instance.isBroken(loc)) {
                continue;
            }
            int pending = instance.fragileTimer(loc);
            if (pending <= 0) {
                boolean standing = false;
                for (Player player : room.activePlayers()) {
                    Location p = player.getLocation();
                    if (p.getBlockX() == loc.getBlockX()
                            && p.getBlockZ() == loc.getBlockZ()
                            && p.getBlockY() == loc.getBlockY() + 1) {
                        standing = true;
                        break;
                    }
                }
                if (!standing) {
                    continue;
                }
                pending = delayTicks;
            }
            pending--;
            if (pending <= 0) {
                instance.clearFragileTimer(loc);
                instance.markBroken(loc);
                loc.getBlock().setType(Material.AIR, false);
                loc.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.8f, 1.0f);
            } else {
                instance.setFragileTimer(loc, pending);
            }
        }
    }

    private void tickIntermittent(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        instance.setCooldown(instance.cooldown() - 1);
        if (instance.cooldown() > 0) {
            return;
        }
        instance.setCooldown(instance.def().intervalTicks());
        boolean hide = !instance.hidden();
        instance.setHidden(hide);
        for (PlacedBlock block : instance.blocks()) {
            if (hide) {
                block.location().getBlock().setType(Material.AIR, false);
            } else {
                block.location().getBlock().setBlockData(block.placed(), false);
            }
        }
    }

    /** 回合开始时恢复易碎方块、重置间歇方块的状态。 */
    public void resetRound(GameRoom room) {
        for (MechanismInstance instance : room.mechanisms()) {
            if (instance.removed()) {
                continue;
            }
            if ("fragile".equals(instance.def().id())) {
                instance.clearBroken();
                instance.clearFragileTimers();
                for (PlacedBlock block : instance.blocks()) {
                    block.location().getBlock().setBlockData(block.placed(), false);
                }
            } else if ("intermittent".equals(instance.def().id())) {
                instance.setHidden(false);
                instance.setCooldown(instance.def().intervalTicks());
                for (PlacedBlock block : instance.blocks()) {
                    block.location().getBlock().setBlockData(block.placed(), false);
                }
            } else if ("traffic".equals(instance.def().id())) {
                instance.setLightState(0);
                instance.setLightTicks(greenTicks(instance.def()));
                updateLightFrame(instance);
            } else if ("mine".equals(instance.def().id())) {
                instance.setTriggered(false);
                for (PlacedBlock block : instance.blocks()) {
                    block.location().getBlock().setBlockData(block.placed(), false);
                }
            }
        }
    }

    private void tickDispenser(GameRoom room, MechanismInstance instance) {        if (!room.isRunning()) {
            return;
        }
        instance.setCooldown(instance.cooldown() - 1);
        if (instance.cooldown() > 0) {
            return;
        }
        instance.setCooldown(instance.def().intervalTicks());
        shoot(room, instance);
    }

    private void shoot(GameRoom room, MechanismInstance instance) {
        Vector direction = instance.facing().getDirection();
        for (PlacedBlock block : instance.blocks()) {
            Location center = block.location().clone().add(0.5, 0.5, 0.5);
            Location spawn = center.clone().add(direction.clone().multiply(0.6));
            Arrow arrow = spawn.getWorld().spawnArrow(spawn, direction,
                    (float) instance.def().arrowSpeed(), 0.0f);
            arrow.setDamage(0.0);
            arrow.setPersistent(false);
            arrow.setGravity(false);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrows.put(arrow.getUniqueId(), new ArrowShot(arrow.getUniqueId(), instance.id(), room,
                    instance.owner(), spawn.clone(), instance.def().arrowRange()));
        }
    }

    private void tickArrows() {
        Iterator<Map.Entry<UUID, ArrowShot>> iterator = arrows.entrySet().iterator();
        while (iterator.hasNext()) {
            ArrowShot shot = iterator.next().getValue();
            Entity entity = Bukkit.getEntity(shot.arrowId());
            if (entity == null || !entity.isValid()) {
                iterator.remove();
                continue;
            }
            double max = (double) shot.range() * shot.range();
            if (entity.getLocation().distanceSquared(shot.start()) > max) {
                entity.remove();
                iterator.remove();
            }
        }
    }

    public void handleArrowHit(ProjectileHitEvent event) {
        ArrowShot shot = arrows.remove(event.getEntity().getUniqueId());
        if (shot == null) {
            return;
        }
        if (event.getHitEntity() instanceof Player player) {
            shot.room().onPlayerFailed(player, shot.owner(), "被箭射中");
        }
    }

    public void bomb(GameRoom room, Location anchor, int radius) {
        for (MechanismInstance instance : new ArrayList<>(room.mechanisms())) {
            if (instance.intersectsCube(anchor, radius)) {
                remove(room, instance);
            }
        }
        anchor.getWorld().spawnParticle(Particle.EXPLOSION, anchor.clone().add(0.5, 0.5, 0.5), 3);
        anchor.getWorld().playSound(anchor, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        room.broadcast("&c炸弹炸掉了周围的机关！");
    }

    // ---------- 还原 ----------

    public void remove(GameRoom room, MechanismInstance instance) {
        if (instance.removed()) {
            return;
        }
        instance.setRemoved(true);
        // 同一格可能被记录多次（例如重复放置），还原时只认第一次记录的原始方块。
        Set<String> restored = new HashSet<>();
        for (PlacedBlock block : instance.blocks()) {
            Location loc = block.location();
            String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":"
                    + loc.getBlockY() + ":" + loc.getBlockZ();
            if (!restored.add(key)) {
                continue;
            }
            loc.getBlock().setBlockData(block.previous(), false);
        }
        instance.blocks().clear();
        for (Entity entity : instance.entities()) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        instance.entities().clear();
        arrows.entrySet().removeIf(entry -> {
            if (entry.getValue().instanceId().equals(instance.id())) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity != null) {
                    entity.remove();
                }
                return true;
            }
            return false;
        });
        room.removeMechanism(instance);
    }

    public void removeAll(GameRoom room) {
        for (MechanismInstance instance : new ArrayList<>(room.mechanisms())) {
            remove(room, instance);
        }
    }

    public void shutdown() {
        for (ArrowShot shot : arrows.values()) {
            Entity entity = Bukkit.getEntity(shot.arrowId());
            if (entity != null) {
                entity.remove();
            }
        }
        arrows.clear();
    }

    // ---------- cells ----------

    public List<Location> cells(MechanismChoice choice, Location anchor, int yawSteps, int pitchSteps) {
        MechanismDef def = choice.def();
        List<Location> out = new ArrayList<>();
        switch (def.id()) {
            case "spike" -> {
                int sizeX = choice.variant().sizeX();
                int sizeZ = choice.variant().sizeZ();
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        out.add(rotated(anchor, x - sizeX / 2, 0, z - sizeZ / 2,
                                yawSteps, pitchSteps));
                    }
                }
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        out.add(rotated(anchor, x - sizeX / 2, 1, z - sizeZ / 2,
                                yawSteps, pitchSteps));
                    }
                }
            }
            case "dispenser" -> {
                int width = choice.variant().sizeX();
                int height = choice.variant().sizeY();
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        out.add(rotated(anchor, x - width / 2, y, 0, yawSteps, pitchSteps));
                    }
                }
            }
            case "platform", "spring", "fragile", "ice" -> planarCells(choice, anchor, yawSteps, pitchSteps, out);
            case "stairs" -> {
                int height = choice.variant().sizeY();
                for (int i = 0; i < height; i++) {
                    out.add(rotated(anchor, 0, i, i, yawSteps, pitchSteps));
                }
            }
            case "ladder" -> {
                int height = choice.variant().sizeY();
                for (int i = 0; i < height; i++) {
                    out.add(rotated(anchor, 0, i, 0, yawSteps, pitchSteps));
                }
            }
            case "mine" -> out.add(anchor.clone());
            case "intermittent" -> planarCells(choice, anchor, yawSteps, pitchSteps, out);
            case "blind" -> planarCells(choice, anchor, yawSteps, pitchSteps, out);
            case "speed" -> planarCells(choice, anchor, yawSteps, pitchSteps, out);
            case "portal" -> {
                out.add(anchor.clone());
                out.add(anchor.clone().add(0, 1, 0));
            }
            case "traffic" -> {
                out.add(anchor.clone());
                out.add(anchor.clone().add(0, 1, 0));
            }
            case "bomb" -> {
                int radius = def.bombRadius();
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            out.add(anchor.clone().add(x, y, z));
                        }
                    }
                }
            }
            default -> out.add(anchor.clone());
        }
        return out;
    }

    private void planarCells(MechanismChoice choice, Location anchor, int yawSteps,
                             int pitchSteps, List<Location> out) {
        int sizeX = choice.variant().sizeX();
        int sizeZ = choice.variant().sizeZ();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                out.add(rotated(anchor, x - sizeX / 2, 0, z - sizeZ / 2,
                        yawSteps, pitchSteps));
            }
        }
    }

    /** 先把相对偏移绕 X 轴竖直翻转（机关局部），再绕世界 Y 轴水平旋转。 */
    private Location rotated(Location anchor, int dx, int dy, int dz, int yawSteps, int pitchSteps) {
        int x = dx;
        int y = dy;
        int z = dz;
        switch (pitchSteps & 3) {
            case 1 -> {
                // 局部 +Z 转到 +Y（朝上）
                int ny = z;
                int nz = -y;
                y = ny;
                z = nz;
            }
            case 2 -> {
                y = -y;
                z = -z;
            }
            case 3 -> {
                // 局部 +Z 转到 -Y（朝下）
                int ny = -z;
                int nz = y;
                y = ny;
                z = nz;
            }
            default -> {
            }
        }
        switch (yawSteps & 3) {
            case 1 -> {
                int nx = -z;
                int nz = x;
                x = nx;
                z = nz;
            }
            case 2 -> {
                x = -x;
                z = -z;
            }
            case 3 -> {
                int nx = z;
                int nz = -x;
                x = nx;
                z = nz;
            }
            default -> {
            }
        }
        return anchor.clone().add(x, y, z);
    }

    private boolean sameCells(List<Location> a, List<Location> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Location x = a.get(i);
            Location y = b.get(i);
            if (x.getWorld() == null || !x.getWorld().equals(y.getWorld())
                    || x.getBlockX() != y.getBlockX()
                    || x.getBlockY() != y.getBlockY()
                    || x.getBlockZ() != y.getBlockZ()) {
                return false;
            }
        }
        return true;
    }

    private boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** 锁定位置时是否以玩家朝向作为基准 yaw。 */
    private boolean usesBaseYaw(MechanismDef def) {
        return "dispenser".equals(def.id())
                || "piston".equals(def.id())
                || "stairs".equals(def.id())
                || "ladder".equals(def.id())
                || "portal".equals(def.id())
                || "traffic".equals(def.id());
    }

    private BlockFace facingFor(MechanismDef def, int yawSteps, int pitchSteps) {
        if ("stairs".equals(def.id()) || "ladder".equals(def.id())
                || "portal".equals(def.id()) || "traffic".equals(def.id())) {
            return yawFacing(yawSteps);
        }
        if (!"dispenser".equals(def.id()) && !"piston".equals(def.id())) {
            return BlockFace.SOUTH;
        }
        int pitch = pitchSteps & 3;
        if (pitch == 1) {
            return BlockFace.UP;
        }
        if (pitch == 3) {
            return BlockFace.DOWN;
        }
        BlockFace face = yawFacing(yawSteps);
        if (pitch == 2) {
            face = face.getOppositeFace();
        }
        return face;
    }

    private BlockFace yawFacing(int yawSteps) {
        BlockFace face = BlockFace.SOUTH;
        for (int i = 0; i < (yawSteps & 3); i++) {
            face = rotateY(face);
        }
        return face;
    }

    /** 玩家朝向对应的基准 yaw：南 0、西 1、北 2、东 3。 */
    private int baseYawSteps(BlockFace face) {
        return switch (face) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** 动作栏显示的是相对基准朝向的角度，而不是加上基准后的绝对值。 */
    private int shownYaw(PlacementSession session) {
        return ((session.yawSteps() - session.baseYawSteps()) & 3) * 90;
    }

    private BlockFace rotateY(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }
}
