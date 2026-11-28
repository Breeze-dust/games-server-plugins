package com.lobbytoys;

import com.hub.api.HubAPI;
import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主城「坐到别人头顶」：玩家空手右键另一个玩家就骑上去，被骑的人按 Shift 让人下来，
 * 空手长按右键把头顶上的一串人甩飞。
 * <p>
 * 座位是隐形 marker 盔甲架（和椅子同款）：玩家当载具时原版会再减去玩家自己的骑乘贴附点 0.6 格，
 * 直接骑玩家只能到对方脚上 1.2 格，碰撞箱会压住对方视线；改成座位后高度可调，逐刻跟随被坐玩家。
 * 只允许「玩家坐玩家」，NPC/分身/盔甲架都不行。
 * 进入非主城世界或进入大多数小游戏（骗子酒馆除外）时自动下来。
 */
public final class HeadSitManager {

    private static final long HOLD_GAP_MILLIS = 400L;
    /** 座位实体都带这个标签，方便启动时清掉上次崩溃留下的。 */
    private static final String SEAT_TAG = "lobbytoys-headseat";

    private static final class Seat {
        final ArmorStand stand;
        final UUID mountId;

        Seat(ArmorStand stand, UUID mountId) {
            this.stand = stand;
            this.mountId = mountId;
        }
    }

    private final LobbyToysPlugin plugin;
    /** 坐着的人 → 他的座位。 */
    private final Map<UUID, Seat> seatsByRider = new HashMap<>();
    /** 被坐的人 → 直接坐在他头顶的那个人。 */
    private final Map<UUID, UUID> riderByMount = new HashMap<>();
    /** 右键长按计时的起点与上一次右键事件时间。 */
    private final Map<UUID, Long> holdStart = new HashMap<>();
    private final Map<UUID, Long> holdLast = new HashMap<>();
    private int taskId = -1;

    public HeadSitManager(LobbyToysPlugin plugin) {
        this.plugin = plugin;
        cleanupLeftovers();
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (UUID riderId : new ArrayList<>(seatsByRider.keySet())) {
            dismissRider(riderId);
        }
        seatsByRider.clear();
        riderByMount.clear();
        holdStart.clear();
        holdLast.clear();
        cleanupLeftovers();
    }

    // ---------- 查询 ----------

    /** 玩家正骑在谁头上；没骑在玩家头上返回 null（坐在椅子/其他载具上也算 null）。 */
    public Player mountOf(Player player) {
        Player mount = validMount(player);
        return mount;
    }

    private Player validMount(Player player) {
        Seat seat = seatsByRider.get(player.getUniqueId());
        if (seat == null) {
            return null;
        }
        if (!seat.stand.isValid() || player.getVehicle() != seat.stand) {
            dismissRider(player.getUniqueId());
            return null;
        }
        Player mount = Bukkit.getPlayer(seat.mountId);
        if (mount == null || !mount.isOnline()) {
            dismissRider(player.getUniqueId());
            return null;
        }
        return mount;
    }

    /** 谁正坐在这个玩家头顶；没有返回 null。 */
    public Player directRiderOf(Player player) {
        UUID riderId = riderByMount.get(player.getUniqueId());
        if (riderId == null) {
            return null;
        }
        Player rider = Bukkit.getPlayer(riderId);
        Seat seat = seatsByRider.get(riderId);
        if (rider == null || seat == null || !seat.stand.isValid() || rider.getVehicle() != seat.stand) {
            riderByMount.remove(player.getUniqueId());
            dismissRider(riderId);
            return null;
        }
        return rider;
    }

    /** 这个玩家头顶上的一串人，从最下面那个人开始往上排。 */
    private List<Player> stackAbove(Player base) {
        List<Player> out = new ArrayList<>();
        Player current = base;
        while (out.size() < 32) {
            Player next = directRiderOf(current);
            if (next == null || out.contains(next)) {
                break;
            }
            out.add(next);
            current = next;
        }
        return out;
    }

    /** 这个玩家当前能不能参与头顶玩法（主城 + 不在会清场的游戏里）。 */
    public boolean canUse(Player player) {
        if (!plugin.settings().headSitEnabled || player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.settings().isAllowedWorld(player.getWorld())) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        String game = HubAPI.gameOf(player.getUniqueId());
        return game == null || plugin.settings().headSitKeepGames.contains(game);
    }

    // ---------- 坐上去 / 下来 ----------

    /** 右键玩家时调用：坐上对方头顶。返回 false 表示这次不该拦右键。 */
    public boolean sit(Player rider, Player mount) {
        if (rider == null || mount == null || rider.equals(mount)) {
            return false;
        }
        if (!canUse(rider) || !canUse(mount)) {
            return false;
        }
        if (rider.getWorld() != mount.getWorld()) {
            return false;
        }
        double max = plugin.settings().headSitMaxDistance;
        if (rider.getLocation().distanceSquared(mount.getLocation()) > max * max) {
            return false;
        }
        if (rider.isInsideVehicle() || rider.isSneaking()) {
            return false;
        }
        if (directRiderOf(mount) != null) {
            return false;
        }
        if (isInStack(mount, rider)) {
            return false;
        }
        Location seatLocation = seatLocation(mount);
        ArmorStand stand = seatLocation.getWorld().spawn(seatLocation, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setCanPickupItems(false);
            as.setCollidable(false);
            as.addScoreboardTag(SEAT_TAG);
        });
        if (!stand.addPassenger(rider)) {
            stand.remove();
            return false;
        }
        seatsByRider.put(rider.getUniqueId(), new Seat(stand, mount.getUniqueId()));
        riderByMount.put(mount.getUniqueId(), rider.getUniqueId());
        if (!plugin.settings().headSitMessageSit.isEmpty()) {
            Chat.actionBar(rider, plugin.settings().headSitMessageSit.replace("%mount%", mount.getName()));
        }
        if (!plugin.settings().headSitMessageSat.isEmpty()) {
            Chat.actionBar(mount, plugin.settings().headSitMessageSat.replace("%rider%", rider.getName()));
        }
        return true;
    }

    /** 座位位置：被坐玩家头顶再往上 head-sit.height 格（默认 0.7）。 */
    private Location seatLocation(Player mount) {
        Location loc = mount.getLocation();
        loc.add(0, mount.getHeight() + plugin.settings().headSitHeight, 0);
        loc.setYaw(mount.getLocation().getYaw());
        loc.setPitch(0f);
        return loc;
    }

    /**
     * 从 base 开始往上找，如果 candidate 已经在里面，说明再坐上去会成环。
     * （A 骑 B 时 B 不能再骑 A。）
     */
    private boolean isInStack(Player base, Player candidate) {
        Player current = base;
        for (int depth = 0; depth < 32 && current != null; depth++) {
            if (current.equals(candidate)) {
                return true;
            }
            current = mountOf(current);
        }
        return false;
    }

    /** 把坐在 base 头顶上的一串人全部叫下来（不飞出去）。 */
    public void dropAbove(Player base) {
        if (base == null) {
            return;
        }
        UUID riderId = riderByMount.get(base.getUniqueId());
        if (riderId != null) {
            dismissRider(riderId);
        }
    }

    /** 让 rider 自己（以及它头顶上的一串人）从载具上下来。 */
    public void dropSelf(Player rider) {
        if (rider == null) {
            return;
        }
        dismissRider(rider.getUniqueId());
    }

    /** 让 rider 以及叠在他头顶上的人全部下车；座位实体一并清掉。 */
    private void dismissRider(UUID riderId) {
        Seat seat = seatsByRider.remove(riderId);
        UUID above = riderByMount.remove(riderId);
        if (above != null) {
            dismissRider(above);
        }
        if (seat == null) {
            return;
        }
        riderByMount.remove(seat.mountId);
        Player rider = Bukkit.getPlayer(riderId);
        if (rider != null && rider.getVehicle() == seat.stand) {
            rider.leaveVehicle();
        }
        if (seat.stand.isValid()) {
            seat.stand.remove();
        }
    }

    /** 被骑的人空手长按右键：把头顶上的一串人一起甩出去。 */
    public boolean throwAbove(Player mount) {
        List<Player> chain = stackAbove(mount);
        if (chain.isEmpty()) {
            return false;
        }
        Location origin = mount.getLocation();
        Vector direction = origin.getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1e-6) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        double power = plugin.settings().headSitThrowPower;
        double up = plugin.settings().headSitThrowUp;

        UUID direct = riderByMount.get(mount.getUniqueId());
        if (direct != null) {
            dismissRider(direct);
        }
        for (Player player : chain) {
            player.setFallDistance(0f);
            player.setVelocity(direction.clone().multiply(power).add(new Vector(0, up, 0)));
            if (!plugin.settings().headSitMessageThrown.isEmpty()) {
                Chat.actionBar(player, plugin.settings().headSitMessageThrown);
            }
        }
        mount.getWorld().playSound(origin, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.9f);
        return true;
    }

    // ---------- 长按右键 ----------

    /** 空手右键方块时调用一次；返回 true 表示这一下触发了甩飞。 */
    public boolean handleHoldRightClick(Player player) {
        UUID id = player.getUniqueId();
        if (!canUse(player) || directRiderOf(player) == null) {
            resetHold(player);
            return false;
        }
        long now = System.currentTimeMillis();
        Long start = holdStart.get(id);
        Long last = holdLast.get(id);
        if (start == null || last == null || now - last > HOLD_GAP_MILLIS) {
            start = now;
        }
        holdStart.put(id, start);
        holdLast.put(id, now);
        if (now - start < plugin.settings().headSitHoldMillis) {
            return false;
        }
        resetHold(player);
        return throwAbove(player);
    }

    public void resetHold(Player player) {
        holdStart.remove(player.getUniqueId());
        holdLast.remove(player.getUniqueId());
    }

    // ---------- 自动下来 ----------

    private void tick() {
        if (!plugin.settings().headSitEnabled) {
            return;
        }
        for (UUID riderId : new ArrayList<>(seatsByRider.keySet())) {
            Player rider = Bukkit.getPlayer(riderId);
            if (rider == null || !rider.isOnline()) {
                dismissRider(riderId);
                continue;
            }
            Player mount = validMount(rider);
            if (mount == null) {
                continue;
            }
            // 自己骑在别人头上：任何一方进了会清场的游戏 / 离开主城都下来
            if (!canUse(rider) || !canUse(mount)) {
                dismissRider(riderId);
                continue;
            }
            Seat seat = seatsByRider.get(riderId);
            if (seat == null) {
                continue;
            }
            follow(seat.stand, mount);
        }
    }

    private void follow(ArmorStand stand, Player mount) {
        Location loc = seatLocation(mount);
        Location current = stand.getLocation();
        if (current.getWorld() != loc.getWorld()
                || current.distanceSquared(loc) > 1e-4
                || Math.abs(current.getYaw() - loc.getYaw()) > 1.0f) {
            stand.teleport(loc);
        }
    }

    /** 被外部从座位上弄下来（Shift 原生下车、座位被删）时清理。 */
    public void handleDismount(Player rider, Entity dismounted) {
        Seat seat = seatsByRider.get(rider.getUniqueId());
        if (seat != null && seat.stand.equals(dismounted)) {
            dismissRider(rider.getUniqueId());
        }
    }

    /** 换世界后调用：离开主城世界就把自己身上和身上的都放下来。 */
    public void handleWorldChange(Player player) {
        if (!plugin.settings().isAllowedWorld(player.getWorld())) {
            dropAbove(player);
            dropSelf(player);
        }
    }

    private void cleanupLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getScoreboardTags().contains(SEAT_TAG)) {
                    stand.remove();
                }
            }
        }
    }
}
