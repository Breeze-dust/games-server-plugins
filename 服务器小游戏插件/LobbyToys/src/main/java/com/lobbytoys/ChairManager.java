package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 可坐的方块：右键楼梯/半砖 → 生成一个隐形座位实体（marker 盔甲架）并把玩家放上去。
 * 玩家按 Shift、受伤、下线、换世界、座位被拆时自动起身；座位实体不写盘，关服后不会残留。
 */
public final class ChairManager {

    /** 座位实体都带这个标签，方便关服/启动时清掉上次崩溃留下的。 */
    private static final String SEAT_TAG = "lobbytoys-seat";

    private static final class Seat {
        final ArmorStand stand;
        final Block block;

        Seat(ArmorStand stand, Block block) {
            this.stand = stand;
            this.block = block;
        }
    }

    private final LobbyToysPlugin plugin;
    private final Map<UUID, Seat> seats = new HashMap<>();

    public ChairManager(LobbyToysPlugin plugin) {
        this.plugin = plugin;
        cleanupLeftovers();
    }

    public boolean isSittable(Material material) {
        return plugin.settings().chairBlocks.contains(material);
    }

    /** 玩家是否正坐在椅子上（座位被别的插件删掉时顺便清掉记录）。 */
    public boolean isSitting(Player player) {
        Seat seat = seats.get(player.getUniqueId());
        if (seat == null) {
            return false;
        }
        if (!seat.stand.isValid() || !seat.stand.getPassengers().contains(player)) {
            seats.remove(player.getUniqueId());
            if (seat.stand.isValid()) {
                seat.stand.remove();
            }
            return false;
        }
        return true;
    }

    public SeatInfo seatOf(Player player) {
        Seat seat = seats.get(player.getUniqueId());
        return seat == null ? null : new SeatInfo(seat.stand.getLocation(), seat.block);
    }

    public record SeatInfo(Location standLocation, Block block) {
    }

    /** 坐上去。返回 false 表示这次交互不该被当成“坐下”。 */
    public boolean sit(Player player, Block block) {
        if (!plugin.settings().chairsEnabled || !isSittable(block.getType())) {
            return false;
        }
        if (!plugin.settings().isAllowedWorld(player.getWorld()) || plugin.isPlaying(player)) {
            return false;
        }
        if (player.isInsideVehicle() || isSitting(player) || player.isSneaking()) {
            return false;
        }
        Location loc = block.getLocation().add(0.5,
                plugin.settings().chairSeatHeight + plugin.settings().chairSeatOffset, 0.5);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(0.0f);
        ArmorStand stand = block.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
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
        if (!stand.addPassenger(player)) {
            stand.remove();
            return false;
        }
        seats.put(player.getUniqueId(), new Seat(stand, block));
        if (!plugin.settings().chairMessage.isEmpty()) {
            Chat.actionBar(player, plugin.settings().chairMessage);
        }
        return true;
    }

    /** 起身：把玩家放回座位方块上方（座位实体可能埋在方块里，直接下车会卡住）。 */
    public void stand(Player player) {
        Seat seat = seats.remove(player.getUniqueId());
        if (seat == null) {
            return;
        }
        Location out = seat.block.getLocation().add(0.5, 1.0, 0.5);
        out.setYaw(player.getLocation().getYaw());
        out.setPitch(player.getLocation().getPitch());
        if (seat.stand.getPassengers().contains(player)) {
            seat.stand.eject();
        }
        seat.stand.remove();
        player.teleport(out);
    }

    /** 玩家只是离开座位（下线/换世界/死亡），不用再传送他。 */
    public void dismiss(UUID playerId) {
        Seat seat = seats.remove(playerId);
        if (seat == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && seat.stand.getPassengers().contains(player)) {
            seat.stand.eject();
        }
        seat.stand.remove();
    }

    /** 关服 / 关掉 chairs 时把所有座位收掉。 */
    public void shutdown() {
        for (UUID id : new HashMap<>(seats).keySet()) {
            dismiss(id);
        }
        cleanupLeftovers();
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
