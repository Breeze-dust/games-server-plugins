package com.liarbar;

import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import com.hub.util.Top3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 房间、桌子、玩家进出的总调度。 */
public final class LiarBarManager {

    public static final String GAME = "骗子酒馆";

    private final LiarBarPlugin plugin;
    private final LiarBarSettings settings = new LiarBarSettings();
    private final LiarBarRoom room;
    private final LiarBarItems items;
    private final Map<String, LiarBarTable> tables = new LinkedHashMap<>();
    private final Map<UUID, LiarBarTable> seating = new HashMap<>();
    private final Set<UUID> inRoom = new HashSet<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Set<UUID> forceDismounts = new HashSet<>();

    private YamlConfiguration stats;
    private int loadRetries;

    public LiarBarManager(LiarBarPlugin plugin) {
        this.plugin = plugin;
        this.room = new LiarBarRoom(plugin);
        this.items = new LiarBarItems(plugin);
    }

    // ---------- 配置加载 ----------

    public void load() {
        plugin.reloadConfig();
        settings.load(plugin.getConfig());
        room.load(plugin.getConfig());
        rebuildTables();
        if (room.unresolved() && loadRetries++ < 30) {
            Bukkit.getScheduler().runTaskLater(plugin, this::load, 200L);
        } else if (room.unresolved()) {
            plugin.getLogger().warning("骗子酒馆：配置里的世界还没有加载，房间/椅子暂时不可用。");
        }
    }

    private void rebuildTables() {
        for (LiarBarTable table : tables.values()) {
            table.shutdown();
        }
        tables.clear();
        for (Map.Entry<String, List<Location>> entry : room.tables().entrySet()) {
            tables.put(entry.getKey(),
                    new LiarBarTable(plugin, this, settings, entry.getKey(), entry.getValue()));
        }
        reportAll();
    }

    public boolean reload() {
        for (LiarBarTable table : tables.values()) {
            if (table.getState() != LiarBarTable.State.WAITING) {
                return false;
            }
            HubAPI.removeArena(GAME, table.getName());
        }
        loadRetries = 0;
        load();
        return true;
    }

    private void reportAll() {
        for (LiarBarTable table : tables.values()) {
            reportArena(table);
        }
    }

    public void reportArena(LiarBarTable table) {
        HubAPI.setArena(GAME, table.getName(), table.hubState(),
                table.occupiedCount(), table.hubExtra());
    }

    public LiarBarSettings settings() {
        return settings;
    }

    public LiarBarItems items() {
        return items;
    }

    public LiarBarRoom room() {
        return room;
    }

    public Map<String, LiarBarTable> tables() {
        return tables;
    }

    // ---------- 进出房间 ----------

    public boolean inRoom(Player player) {
        return inRoom.contains(player.getUniqueId());
    }

    public LiarBarTable tableOf(Player player) {
        return seating.get(player.getUniqueId());
    }

    public LiarBarTable tableAt(Block block) {
        if (block == null) {
            return null;
        }
        for (LiarBarTable table : tables.values()) {
            if (table.hasChair(block)) {
                return table;
            }
        }
        return null;
    }

    public void enter(Player player, String tableName) {
        if (!room.configured()) {
            Chat.send(player, "&c骗子酒馆还没配置好：OP 需要先设置房间出生点并登记至少一张桌子。");
            return;
        }
        UUID id = player.getUniqueId();
        String currentGame = HubAPI.gameOf(id);
        if (currentGame != null && !currentGame.equals(GAME)) {
            Chat.send(player, "&c你正在「" + currentGame + "」里，先回主城再来骗子酒馆。");
            return;
        }
        if (inRoom.contains(id)) {
            if (tableName != null && !tableName.isEmpty()) {
                sitNamed(player, tableName);
            } else {
                Chat.send(player, "&7你已经在酒馆里了：右键台阶椅子入座。");
            }
            return;
        }
        snapshots.put(id, Snapshot.capture(player));
        inRoom.add(id);
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.teleport(room.spawn());
        giveLeaveItem(player);
        player.setFireTicks(0);
        player.updateInventory();

        HubAPI.setGame(id, GAME);
        HubAPI.announceJoin(plugin.getName(), player.getName());
        Chat.send(player, "&e欢迎来到骗子酒馆：&f找一张桌子，右键台阶椅子坐下就是准备。");
        Chat.send(player, "&7坐满 " + settings.playersPerTable + " 人自动开局；想走就右键手里的床或 &e/liarbar leave&7。");
        Chat.actionBar(player, "&e右键台阶椅子入座");
        if (tableName != null && !tableName.isEmpty()) {
            sitNamed(player, tableName);
        }
    }

    private void sitNamed(Player player, String tableName) {
        LiarBarTable table = tables.get(tableName);
        if (table == null) {
            Chat.send(player, "&c没有叫「" + tableName + "」的桌子。");
            return;
        }
        sitOrStand(player, table);
    }

    public void leave(Player player, boolean silent) {
        UUID id = player.getUniqueId();
        LiarBarTable table = seating.get(id);
        boolean inside = inRoom.contains(id) || table != null;
        if (!inside) {
            if (!silent) {
                Chat.send(player, "&7你不在骗子酒馆里。");
            }
            return;
        }
        if (table != null) {
            table.removePlayer(player);
        }
        inRoom.remove(id);
        Snapshot snapshot = snapshots.remove(id);
        if (snapshot != null) {
            snapshot.restore(player);
        }
        HubAPI.clearGame(id);
        if (!silent) {
            HubAPI.teleportToLobby(player);
            Chat.send(player, "&e已离开骗子酒馆。");
        }
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        LiarBarTable table = seating.get(id);
        if (table != null) {
            table.removePlayer(player);
        }
        inRoom.remove(id);
        Snapshot snapshot = snapshots.remove(id);
        if (snapshot != null) {
            snapshot.restore(player);
        }
    }

    public void sitOrStand(Player player, LiarBarTable table) {
        LiarBarTable current = seating.get(player.getUniqueId());
        if (current == table) {
            table.standUp(player);
            return;
        }
        if (current != null) {
            Chat.actionBar(player, "&c你已经坐在「" + current.getName() + "」了，先右键起身。");
            return;
        }
        if (table.sit(player)) {
            Chat.send(player, "&a你坐到了「" + table.getName() + "」的椅子上。");
        }
    }

    public void bind(Player player, LiarBarTable table) {
        seating.put(player.getUniqueId(), table);
    }

    public void unbind(UUID id) {
        seating.remove(id);
    }

    public void markForceDismount(UUID id) {
        forceDismounts.add(id);
    }

    public void clearForceDismount(UUID id) {
        forceDismounts.remove(id);
    }

    public boolean isForceDismount(UUID id) {
        return forceDismounts.contains(id);
    }

    public void giveLeaveItem(Player player) {
        player.getInventory().setItem(8, items.leave());
    }

    // ---------- 管理员建场 ----------

    /** 站在/看着台阶时把它登记为某张桌子的一把椅子。 */
    public boolean addChair(Player player, String tableName) {
        Block block = blockUnder(player);
        if (block == null) {
            Chat.send(player, "&c请站在台阶方块上（或看着 5 格内的台阶）再执行。");
            return false;
        }
        if (room.isChair(block)) {
            Chat.send(player, "&c这个台阶已经是某张桌子的椅子了。");
            return false;
        }
        LiarBarTable existing = tables.get(tableName);
        if (existing != null) {
            if (existing.getState() != LiarBarTable.State.WAITING) {
                Chat.send(player, "&c「" + tableName + "」正在对局中，不能改椅子。");
                return false;
            }
            if (existing.occupiedCount() > 0) {
                Chat.send(player, "&c「" + tableName + "」已经有人入座了，等人起身再登记椅子。");
                return false;
            }
        }
        if (room.seatsOf(tableName).size() >= settings.playersPerTable) {
            Chat.send(player, "&c「" + tableName + "」已经有 " + settings.playersPerTable + " 把椅子了。");
            return false;
        }
        room.addChair(tableName, block.getLocation());
        room.save();
        List<Location> seats = new ArrayList<>(room.seatsOf(tableName));
        if (existing != null) {
            existing.setSeats(seats);
            reportArena(existing);
        } else {
            LiarBarTable table = new LiarBarTable(plugin, this, settings, tableName, seats);
            tables.put(tableName, table);
            reportArena(table);
        }
        Chat.send(player, "&a已把台阶 &7(" + block.getX() + ", " + block.getY() + ", " + block.getZ()
                + ")&a 登记为「&e" + tableName + "&a」的第 " + seats.size() + " 把椅子。");
        if (seats.size() < settings.playersPerTable) {
            Chat.send(player, "&7还差 &e" + (settings.playersPerTable - seats.size()) + " &7把椅子。");
        } else {
            Chat.send(player, "&6这张桌子配好了，" + settings.playersPerTable + " 人坐下就会自动开局。");
        }
        return true;
    }

    public boolean deleteTable(String tableName) {
        LiarBarTable table = tables.remove(tableName);
        if (table == null && !room.tables().containsKey(tableName)) {
            return false;
        }
        if (table != null) {
            table.forceStop();
            HubAPI.removeArena(GAME, tableName);
        }
        room.removeTable(tableName);
        room.save();
        return true;
    }

    private Block blockUnder(Player player) {
        Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (Tag.STAIRS.isTagged(below.getType())) {
            return below;
        }
        Block target = player.getTargetBlockExact(5);
        if (target != null && Tag.STAIRS.isTagged(target.getType())) {
            return target;
        }
        return null;
    }

    public void setSpawn(Player player) {
        room.setSpawn(player.getLocation());
        room.save();
    }

    public void setCorner(Player player, int index) {
        if (index == 1) {
            room.setPos1(player.getLocation());
        } else {
            room.setPos2(player.getLocation());
        }
        room.save();
    }

    // ---------- 战绩 ----------

    public void recordWin(UUID winner) {
        try {
            if (stats == null) {
                stats = PlayerStats.load("liarbar", new File(plugin.getDataFolder(), "data.yml"));
            }
            Top3.award(stats, List.of(winner), List.of(), List.of());
            PlayerStats.save("liarbar", stats);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存骗子酒馆战绩失败：" + ex.getMessage());
        }
    }

    // ---------- 关闭 ----------

    public void shutdown() {
        for (LiarBarTable table : tables.values()) {
            table.shutdown();
        }
        for (Map.Entry<UUID, Snapshot> entry : snapshots.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                entry.getValue().restore(p);
            }
        }
        snapshots.clear();
        inRoom.clear();
        seating.clear();
    }

    /** 进房间时保存的原背包/状态，离开或下线时还原。 */
    private static final class Snapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final int level;
        private final float exp;
        private final double health;
        private final int food;
        private final float saturation;
        private final GameMode mode;

        private Snapshot(Player p) {
            PlayerInventory inv = p.getInventory();
            contents = inv.getStorageContents().clone();
            armor = inv.getArmorContents().clone();
            offhand = inv.getItemInOffHand();
            level = p.getLevel();
            exp = p.getExp();
            health = p.getHealth();
            food = p.getFoodLevel();
            saturation = p.getSaturation();
            mode = p.getGameMode();
        }

        static Snapshot capture(Player p) {
            return new Snapshot(p);
        }

        void restore(Player p) {
            PlayerInventory inv = p.getInventory();
            inv.setStorageContents(contents);
            inv.setArmorContents(armor);
            inv.setItemInOffHand(offhand);
            p.setLevel(level);
            p.setExp(exp);
            p.setFoodLevel(food);
            p.setSaturation(saturation);
            p.setHealth(Math.max(0.5, Math.min(health, p.getMaxHealth())));
            p.setGameMode(mode);
            p.updateInventory();
        }
    }

    // 供 HubCore 菜单用的提示
}
