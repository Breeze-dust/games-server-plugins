package com.superniuma.menu;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.game.GameMode;
import com.superniuma.game.GameRoom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 创建房间：模式、目标分数、最高回合。 */
public final class CreateRoomMenu {

    private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

    private CreateRoomMenu() {
    }

    public static final class Draft {
        private final Arena arena;
        private GameMode mode;
        private int teamCount;
        private int winScore;
        private int maxRounds;

        private Draft(Arena arena, GameMode mode, int teamCount, int winScore, int maxRounds) {
            this.arena = arena;
            this.mode = mode;
            this.teamCount = teamCount;
            this.winScore = winScore;
            this.maxRounds = maxRounds;
        }
    }

    public static void open(SuperNiumaPlugin plugin, Player player, Arena arena) {
        int teams = plugin.getConfig().getInt("room.default-team-count", 2);
        int win = plugin.getConfig().getInt("room.default-win-score", 10);
        int rounds = plugin.getConfig().getInt("room.default-max-rounds", 8);
        DRAFTS.put(player.getUniqueId(), new Draft(arena, GameMode.SOLO, teams, win, rounds));
        render(plugin, player);
    }

    public static void render(SuperNiumaPlugin plugin, Player player) {
        Draft draft = DRAFTS.get(player.getUniqueId());
        if (draft == null) {
            RoomMenu.open(plugin, player);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CREATE);
        Inventory inventory = Bukkit.createInventory(holder, 27, Msg.c("&e创建房间 - " + draft.arena.getName()));
        holder.setInventory(inventory);

        inventory.setItem(10, RoomMenu.item(Material.PAPER, "&e模式：&f" + modeText(draft), Arrays.asList(
                Msg.c("&7点击切换：个人 → 团队 2 队 → 3 队 → 4 队"))));
        holder.action(10, "mode");

        inventory.setItem(12, RoomMenu.item(Material.REDSTONE, "&c目标分数 -1", List.of()));
        holder.action(12, "win:-");
        inventory.setItem(13, RoomMenu.item(Material.GOLD_INGOT,
                "&e目标分数：&6" + draft.winScore, List.of(Msg.c("&7达到该分数即获胜"))));
        inventory.setItem(14, RoomMenu.item(Material.GLOWSTONE_DUST, "&a目标分数 +1", List.of()));
        holder.action(14, "win:+");

        inventory.setItem(15, RoomMenu.item(Material.REDSTONE_BLOCK, "&c最高回合 -1", List.of()));
        holder.action(15, "rounds:-");
        inventory.setItem(16, RoomMenu.item(Material.CLOCK,
                "&e最高回合：&6" + draft.maxRounds, List.of(Msg.c("&7超过后按最高分判定"))));
        inventory.setItem(17, RoomMenu.item(Material.GLOWSTONE, "&a最高回合 +1", List.of()));
        holder.action(17, "rounds:+");

        inventory.setItem(22, RoomMenu.item(Material.LIME_WOOL, "&a创建房间", Arrays.asList(
                Msg.c("&7地图：&f" + draft.arena.getName()),
                Msg.c("&7模式：&f" + modeText(draft)),
                Msg.c("&7目标分数：&f" + draft.winScore),
                Msg.c("&7最高回合：&f" + draft.maxRounds))));
        holder.action(22, "create");

        inventory.setItem(26, RoomMenu.item(Material.ARROW, "&7返回", List.of()));
        holder.action(26, "back");
        player.openInventory(inventory);
    }

    public static void cycleMode(SuperNiumaPlugin plugin, Player player) {
        Draft draft = DRAFTS.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        if (draft.mode == GameMode.SOLO) {
            draft.mode = GameMode.TEAM;
            draft.teamCount = 2;
        } else if (draft.teamCount < 4) {
            draft.teamCount++;
        } else {
            draft.mode = GameMode.SOLO;
            draft.teamCount = 2;
        }
        render(plugin, player);
    }

    public static void adjustWin(SuperNiumaPlugin plugin, Player player, int delta) {
        Draft draft = DRAFTS.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        draft.winScore = Math.max(1, Math.min(100, draft.winScore + delta));
        render(plugin, player);
    }

    public static void adjustRounds(SuperNiumaPlugin plugin, Player player, int delta) {
        Draft draft = DRAFTS.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        draft.maxRounds = Math.max(1, Math.min(30, draft.maxRounds + delta));
        render(plugin, player);
    }

    public static void create(SuperNiumaPlugin plugin, Player player) {
        Draft draft = DRAFTS.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        if (plugin.rooms().roomOf(player) != null) {
            Msg.send(player, "&c你已经在一个房间里了。");
            return;
        }
        if (!draft.arena.isReady()) {
            Msg.send(player, "&c这张地图还没有配置完整。");
            return;
        }
        GameRoom room = plugin.rooms().create(draft.arena, draft.mode, draft.teamCount,
                draft.winScore, draft.maxRounds, player);
        DRAFTS.remove(player.getUniqueId());
        player.closeInventory();
        Msg.send(player, "&a房间已创建：&e" + room.id() + " &7（" + room.modeText()
                + "，目标 " + room.winScore() + " 分，最高 " + room.maxRounds() + " 回合）");
        Msg.send(player, "&7其他玩家可用 &f/sn join " + room.id() + " &7或打开菜单加入。");
    }

    private static String modeText(Draft draft) {
        return draft.mode == GameMode.SOLO ? "个人" : "团队 " + draft.teamCount + " 队";
    }
}
