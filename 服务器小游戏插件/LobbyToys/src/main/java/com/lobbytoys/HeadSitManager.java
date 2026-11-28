package com.lobbytoys;

import com.hub.api.HubAPI;
import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主城「坐到别人头顶」：玩家右键另一个玩家就骑上去，被骑的人按 Shift 让人下来，
 * 空手长按右键把头顶上的一串人甩飞。
 * <p>
 * 骑乘直接用原版乘客关系（玩家当载具）：乘客位置由服务端按载具高度自动算在头顶，
 * 跟随时不用逐刻传送，叠人（头顶上的人再被坐）也天然成立。
 * 只允许「玩家坐玩家」，NPC/分身/盔甲架都不行。
 * 进入非主城世界或进入大多数小游戏（骗子酒馆除外）时自动下来。
 */
public final class HeadSitManager {

    private static final long HOLD_GAP_MILLIS = 400L;

    private final LobbyToysPlugin plugin;
    /** 右键长按计时的起点与上一次右键事件时间。 */
    private final Map<UUID, Long> holdStart = new HashMap<>();
    private final Map<UUID, Long> holdLast = new HashMap<>();
    private int taskId = -1;

    public HeadSitManager(LobbyToysPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        holdStart.clear();
        holdLast.clear();
    }

    // ---------- 查询 ----------

    /** 玩家正骑在谁头上；没骑在玩家头上返回 null（坐在椅子/其他载具上也算 null）。 */
    public Player mountOf(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof Player mount ? mount : null;
    }

    /** 谁正坐在这个玩家头顶；没有返回 null。 */
    public Player directRiderOf(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Player rider) {
                return rider;
            }
        }
        return null;
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
        if (!mount.addPassenger(rider)) {
            return false;
        }
        if (!plugin.settings().headSitMessageSit.isEmpty()) {
            Chat.actionBar(rider, plugin.settings().headSitMessageSit.replace("%mount%", mount.getName()));
        }
        if (!plugin.settings().headSitMessageSat.isEmpty()) {
            Chat.actionBar(mount, plugin.settings().headSitMessageSat.replace("%rider%", rider.getName()));
        }
        rider.playSound(rider.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
        return true;
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
            Entity vehicle = current.getVehicle();
            current = vehicle instanceof Player mount ? mount : null;
        }
        return false;
    }

    /** 把坐在 base 头顶上的一串人全部叫下来（不飞出去）。 */
    public void dropAbove(Player base) {
        if (base == null) {
            return;
        }
        dismissChain(stackAbove(base));
    }

    /** 让 rider 自己（以及它头顶上的一串人）从载具上下来。 */
    public void dropSelf(Player rider) {
        if (rider == null) {
            return;
        }
        List<Player> chain = new ArrayList<>();
        chain.add(rider);
        chain.addAll(stackAbove(rider));
        dismissChain(chain);
    }

    /** 从上往下依次下车，避免下面的人下车时把上面的人一起带走。 */
    private void dismissChain(List<Player> chain) {
        for (int i = chain.size() - 1; i >= 0; i--) {
            Player player = chain.get(i);
            if (player != null && player.getVehicle() != null) {
                player.leaveVehicle();
            }
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

        dismissChain(chain);
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

    /** 空手右键空气时调用一次；返回 true 表示这一下触发了甩飞。 */
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            Player mount = mountOf(player);
            if (mount != null) {
                // 自己正骑在别人头上：任何一方进了会清场的游戏 / 离开主城都下来
                if (!canUse(player) || !canUse(mount)) {
                    dropSelf(player);
                    continue;
                }
            }
            if (directRiderOf(player) != null && !canUse(player)) {
                dropAbove(player);
            }
        }
    }

    /** 换世界后调用：离开主城世界就把自己身上和身上的都放下来。 */
    public void handleWorldChange(Player player) {
        if (!plugin.settings().isAllowedWorld(player.getWorld())) {
            dropAbove(player);
            dropSelf(player);
        }
    }
}
