package com.superniuma.game;

import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.arena.RoomCopy;
import com.superniuma.arena.RoomCopyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 所有房间的登记表。 */
public final class RoomManager {

    private final SuperNiumaPlugin plugin;
    private final Map<String, GameRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, GameRoom> byPlayer = new HashMap<>();
    private final Map<String, RoomCopy> leases = new HashMap<>();
    /** 登录后还留着对局席位、等待自动送回房间的玩家。 */
    private final Set<UUID> rejoiningSoon = new HashSet<>();

    public RoomManager(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    public GameRoom create(Arena arena, GameMode mode, int teamCount, int winScore,
                           int maxRounds, Player host) throws RoomCopyManager.CopyException {
        String id = newId();
        RoomCopy copy = plugin.copies().allocate(id, arena);
        int maxPlayers = plugin.getConfig().getInt("room.default-max-players", 8);
        GameRoom room;
        try {
            room = new GameRoom(plugin, id, copy.arena(), mode, teamCount, winScore,
                    maxRounds, maxPlayers, host);
        } catch (RuntimeException ex) {
            plugin.copies().release(copy);
            throw ex;
        }
        rooms.put(id, room);
        leases.put(id, copy);
        byPlayer.put(host.getUniqueId(), room);
        return room;
    }

    private String newId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.Random random = new java.util.Random();
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder builder = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String id = builder.toString();
            if (!rooms.containsKey(id)) {
                return id;
            }
        }
        return "R" + System.currentTimeMillis() % 1000;
    }

    public GameRoom byId(String id) {
        return id == null ? null : rooms.get(id.toUpperCase(Locale.ROOT));
    }

    public GameRoom roomOf(Player player) {
        return player == null ? null : rooms.get(roomIdOf(player));
    }

    private String roomIdOf(Player player) {
        GameRoom room = byPlayer.get(player.getUniqueId());
        return room == null ? null : room.id();
    }

    public boolean join(String roomId, Player player) {
        GameRoom room = byId(roomId);
        if (room == null) {
            return false;
        }
        GameRoom current = byPlayer.get(player.getUniqueId());
        if (current != null && current != room) {
            return false;
        }
        if (!room.addParticipant(player)) {
            return false;
        }
        byPlayer.put(player.getUniqueId(), room);
        return true;
    }

    public void leave(Player player, boolean toLobby) {
        GameRoom room = byPlayer.remove(player.getUniqueId());
        if (room != null) {
            room.leave(player, toLobby);
        }
    }

    /** 掉线：对局中保留席位等重连，等待室直接退出。 */
    public void quit(Player player) {
        GameRoom room = byPlayer.get(player.getUniqueId());
        if (room == null) {
            return;
        }
        if (!room.handleQuit(player)) {
            byPlayer.remove(player.getUniqueId());
        }
    }

    /** 玩家登录时先记下：如果还留着对局席位，稍后自动送回对局。 */
    public void onLogin(Player player) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            rejoiningSoon.add(player.getUniqueId());
        }
    }

    /**
     * 玩家登录后主城会自动把玩家传回大厅（这会调用"回主城"回调）。
     * 保留席位的玩家这次不算退出对局，改为稍后送回房间。
     *
     * @return true 表示拦下了这次回主城
     */
    public boolean resumeAfterLogin(Player player) {
        if (!rejoiningSoon.remove(player.getUniqueId())) {
            return false;
        }
        GameRoom room = byPlayer.get(player.getUniqueId());
        if (room == null) {
            return false;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            GameRoom current = byPlayer.get(player.getUniqueId());
            if (current != null) {
                Msg.send(player, "&a正在把你送回房间 &e" + current.id() + "&a…");
                current.addParticipant(player);
            }
        }, 5L);
        return true;
    }

    public void removeRoom(GameRoom room) {
        if (room == null) {
            return;
        }
        rooms.remove(room.id());
        byPlayer.entrySet().removeIf(entry -> entry.getValue() == room);
        RoomCopy copy = leases.remove(room.id());
        if (copy != null) {
            plugin.copies().release(copy);
        }
        if (room.arena() != null) {
            HubAPI.setArena(GameRoom.GAME_NAME, room.arena().getName(), GameState.WAITING, 0, null);
        }
    }

    public Collection<GameRoom> all() {
        return new ArrayList<>(rooms.values());
    }

    public List<String> ids() {
        return new ArrayList<>(rooms.keySet());
    }

    public void shutdown() {
        for (GameRoom room : new ArrayList<>(rooms.values())) {
            room.shutdown();
        }
        plugin.copies().releaseAll();
        leases.clear();
        rooms.clear();
        byPlayer.clear();
        rejoiningSoon.clear();
    }
}
