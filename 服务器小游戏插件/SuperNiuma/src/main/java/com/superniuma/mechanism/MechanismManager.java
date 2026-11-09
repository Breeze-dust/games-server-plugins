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
import java.util.Locale;
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

    public void updatePreview(PlacementSession session, Arena arena) {
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
        Material material = ghostMaterial(session.choice().def());
        for (Location cell : cells) {
            BlockDisplay display = cell.getWorld().spawn(
                    cell.clone().add(0.5, 0.0, 0.5), BlockDisplay.class, d -> {
                        d.setBlock(material.createBlockData());
                        d.setPersistent(false);
                        d.setGravity(false);
                        d.setInvulnerable(true);
                        d.setSilent(true);
                        d.setInterpolationDuration(2);
                    });
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(session.owner())) {
                    other.hideEntity(plugin, display);
                }
            }
            session.ghosts().add(display);
        }
    }

    public void clearPreview(PlacementSession session) {
        clearGhosts(session);
        session.setAnchor(null);
    }

    private void clearGhosts(PlacementSession session) {
        for (BlockDisplay display : session.ghosts()) {
            if (display.isValid()) {
                display.remove();
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
            Msg.actionBar(session.player(), "&e水平旋转 90°（" + (session.yawSteps() * 90) + "°）");
        } else {
            session.setPitchSteps(session.pitchSteps() + 2);
            Msg.actionBar(session.player(), "&e竖直翻转 180°（"
                    + (session.pitchSteps() == 2 ? "下" : "上") + "）");
        }
        session.setGhostCells(List.of());
    }

    public void rotateRight(PlacementSession session) {
        if (lockIfNeeded(session)) {
            return;
        }
        RotationRule rule = session.choice().def().rotation();
        int step = rule == RotationRule.VERTICAL ? 2 : 1;
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
        Vector direction = horizontalFacing(session.player());
        if (!forward) {
            direction.multiply(-1);
        }
        Location next = anchor.clone().add(direction.getX(), 0, direction.getZ());
        session.setAnchor(next.getBlock().getLocation());
        session.setGhostCells(List.of());
        Msg.actionBar(session.player(), forward ? "&e向前移动 1 格" : "&e向后移动 1 格");
    }

    // ---------- 放置 ----------

    public PlaceResult place(GameRoom room, PlacementSession session) {
        MechanismChoice choice = session.choice();
        MechanismDef def = choice.def();
        Location anchor = session.anchor();
        if (anchor == null) {
            anchor = computeAnchor(session);
            if (anchor == null) {
                return PlaceResult.fail("还没有可放置的位置。");
            }
            session.setAnchor(anchor);
            session.setLocked(true);
        }
        Arena arena = room.arena();

        if ("bomb".equals(def.id())) {
            if (!arena.inBuildBounds(anchor)) {
                return PlaceResult.fail("炸弹只能放在可建造范围内。");
            }
            bomb(room, anchor, def.bombRadius());
            return PlaceResult.ok(null);
        }

        List<Location> cells = cells(choice, anchor, session.yawSteps(), session.pitchSteps());
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

        MechanismInstance instance = new MechanismInstance(def, choice.variant(), session.owner(),
                anchor, session.yawSteps(), session.pitchSteps(), facingFor(session, def));

        switch (def.id()) {
            case "spike" -> placeSpike(room, instance, cells);
            case "dispenser" -> placeDispenser(instance, cells.get(0));
            case "coin" -> placeCoin(instance);
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

    private void placeSpike(GameRoom room, MechanismInstance instance, List<Location> cells) {
        setBlock(instance, cells.get(0), Material.DRIPSTONE_BLOCK.createBlockData());
        BlockData tipData = Material.POINTED_DRIPSTONE.createBlockData();
        if (tipData instanceof PointedDripstone dripstone) {
            dripstone.setVerticalDirection((instance.pitchSteps() & 2) == 2 ? BlockFace.DOWN : BlockFace.UP);
            dripstone.setThickness(Speleothem.Thickness.TIP);
        }
        setBlock(instance, cells.get(1), tipData);
    }

    private void placeDispenser(MechanismInstance instance, Location cell) {
        BlockData data = Material.DISPENSER.createBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(instance.facing());
        }
        setBlock(instance, cell, data);
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
        instance.blocks().add(new PlacedBlock(location.clone(), block.getBlockData().clone()));
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
        Location center = instance.anchor().clone().add(0.5, 0.5, 0.5);
        for (Player player : room.activePlayers()) {
            if (player.getLocation().distanceSquared(center) <= 1.5) {
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

    private void tickDispenser(GameRoom room, MechanismInstance instance) {
        if (!room.isRunning()) {
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
        Location spawn = instance.anchor().clone().add(0.5, 0.5, 0.5).add(direction.clone().multiply(0.6));
        Arrow arrow = spawn.getWorld().spawnArrow(spawn, direction,
                (float) instance.def().arrowSpeed(), 0.0f);
        arrow.setDamage(0.0);
        arrow.setPersistent(false);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrows.put(arrow.getUniqueId(), new ArrowShot(arrow.getUniqueId(), instance.id(), room,
                instance.owner(), spawn.clone(), instance.def().arrowRange()));
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
                out.add(anchor.clone());
                out.add(rotated(anchor, 0, 1, 0, yawSteps, pitchSteps));
            }
            case "platform" -> {
                int sizeX = choice.variant().sizeX();
                int sizeZ = choice.variant().sizeZ();
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        out.add(rotated(anchor, x - sizeX / 2, 0, z - sizeZ / 2, yawSteps, pitchSteps));
                    }
                }
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

    /** 先把相对偏移绕 Y 轴水平旋转，再绕 X 轴竖直翻转。 */
    private Location rotated(Location anchor, int dx, int dy, int dz, int yawSteps, int pitchSteps) {
        int x = dx;
        int y = dy;
        int z = dz;
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
        switch (pitchSteps & 3) {
            case 1 -> {
                int ny = -z;
                int nz = y;
                y = ny;
                z = nz;
            }
            case 2 -> {
                y = -y;
                z = -z;
            }
            case 3 -> {
                int ny = z;
                int nz = -y;
                y = ny;
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

    private Material ghostMaterial(MechanismDef def) {
        return switch (def.id().toLowerCase(Locale.ROOT)) {
            case "spike" -> Material.RED_STAINED_GLASS;
            case "platform" -> Material.WHITE_STAINED_GLASS;
            case "dispenser" -> Material.GRAY_STAINED_GLASS;
            case "coin" -> Material.YELLOW_STAINED_GLASS;
            case "bomb" -> Material.RED_STAINED_GLASS;
            default -> Material.WHITE_STAINED_GLASS;
        };
    }

    private BlockFace facingFor(PlacementSession session, MechanismDef def) {
        if (!"dispenser".equals(def.id())) {
            return BlockFace.SOUTH;
        }
        int pitch = session.pitchSteps() & 3;
        if (pitch == 1) {
            return BlockFace.UP;
        }
        if (pitch == 3) {
            return BlockFace.DOWN;
        }
        Player player = session.player();
        Vector direction = player.getEyeLocation().getDirection();
        BlockFace face;
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            face = direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            face = direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        for (int i = 0; i < session.yawSteps(); i++) {
            face = rotateY(face);
        }
        if (pitch == 2) {
            face = face.getOppositeFace();
        }
        return face;
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
