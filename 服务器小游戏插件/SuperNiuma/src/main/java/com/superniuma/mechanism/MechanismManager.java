package com.superniuma.mechanism;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.game.GameRoom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.block.data.type.Speleothem;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 机关预览、放置、运行与还原。 */
public final class MechanismManager {

    private final SuperNiumaPlugin plugin;
    private final Map<UUID, ArrowShot> arrows = new HashMap<>();

    public MechanismManager(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    public record PlaceResult(boolean success, MechanismInstance instance, String error) {
        public static PlaceResult ok(MechanismInstance instance) {
            return new PlaceResult(true, instance, null);
        }

        public static PlaceResult fail(String error) {
            return new PlaceResult(false, null, error);
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
        List<Location> cells = cells(session.choice(), anchor, session.yawSteps(), session.pitchSteps());
        if (sameCells(cells, session.ghostCells())) {
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
            case "coin" -> spawnCoinGhost(session);
            case "bomb" -> {
                Location anchor = session.anchor();
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
        if ("ladder".equals(def.id())) {
            BlockFace supportFace = facingFor(def, yawSteps, pitchSteps).getOppositeFace();
            for (Location cell : cells) {
                if (!cell.getBlock().getRelative(supportFace).getType().isSolid()) {
                    return PlaceResult.fail("梯子需要贴在方块上。");
                }
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
                default -> {
                }
            }
        }
        tickArrows();
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
        for (Player player : room.activePlayers()) {
            BoundingBox box = player.getBoundingBox();
            for (PlacedBlock block : instance.blocks()) {
                Location loc = block.location();
                BoundingBox cube = new BoundingBox(
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        loc.getBlockX() + 1.0, loc.getBlockY() + 1.0, loc.getBlockZ() + 1.0);
                if (box.overlaps(cube)) {
                    room.onPlayerFailed(player, instance.owner(), "被尖刺扎到");
                    break;
                }
            }
        }
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
                double speed = Math.max(0.4, distance * 0.12);
                Vector horizontal = new Vector(direction.getX(), 0, direction.getZ())
                        .normalize().multiply(speed);
                player.setVelocity(new Vector(horizontal.getX(),
                        Math.max(velocity.getY(), 0.15), horizontal.getZ()));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
        }
    }

    private void tickMine(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        Location center = instance.anchor();
        BoundingBox trigger = new BoundingBox(
                center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                center.getBlockX() + 1.0, center.getBlockY() + 1.0, center.getBlockZ() + 1.0);
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
            center.getWorld().spawnParticle(Particle.EXPLOSION,
                    center.clone().add(0.5, 0.5, 0.5), 2);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            remove(room, instance);
            return;
        }
    }

    private void tickFragile(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
            return;
        }
        for (PlacedBlock block : instance.blocks()) {
            Location loc = block.location();
            if (instance.isBroken(loc)) {
                continue;
            }
            for (Player player : room.activePlayers()) {
                Location p = player.getLocation();
                if (p.getBlockX() == loc.getBlockX()
                        && p.getBlockZ() == loc.getBlockZ()
                        && p.getBlockY() == loc.getBlockY() + 1) {
                    instance.markBroken(loc);
                    loc.getBlock().setType(Material.AIR, false);
                    loc.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.8f, 1.0f);
                    break;
                }
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
                for (PlacedBlock block : instance.blocks()) {
                    block.location().getBlock().setBlockData(block.placed(), false);
                }
            } else if ("intermittent".equals(instance.def().id())) {
                instance.setHidden(false);
                instance.setCooldown(instance.def().intervalTicks());
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
        for (PlacedBlock block : instance.blocks()) {
            block.location().getBlock().setBlockData(block.previous(), false);
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
                || "ladder".equals(def.id());
    }

    private BlockFace facingFor(MechanismDef def, int yawSteps, int pitchSteps) {
        if ("stairs".equals(def.id()) || "ladder".equals(def.id())) {
            BlockFace face = BlockFace.SOUTH;
            for (int i = 0; i < (yawSteps & 3); i++) {
                face = rotateY(face);
            }
            return face;
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
        BlockFace face = BlockFace.SOUTH;
        for (int i = 0; i < (yawSteps & 3); i++) {
            face = rotateY(face);
        }
        if (pitch == 2) {
            face = face.getOppositeFace();
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
