package com.superniuma.game;

import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 所有房间的登记表。 */
public final class RoomManager {

    private final SuperNiumaPlugin plugin;
    private final Map<String, GameRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, GameRoom> byPlayer = new HashMap<>();

    public RoomManager(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    public GameRoom create(Arena arena, GameMode mode, int teamCount, int winScore,
                           int maxRounds, Player host) {
        String id = newId();
        int maxPlayers = plugin.getConfig().getInt("room.default-max-players", 8);
        GameRoom room = new GameRoom(plugin, id, arena, mode, teamCount, winScore,
                maxRounds, maxPlayers, host);
        rooms.put(id, room);
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
        if (byPlayer.containsKey(player.getUniqueId())) {
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

    public void removeRoom(GameRoom room) {
        if (room == null) {
            return;
        }
        rooms.remove(room.id());
        byPlayer.entrySet().removeIf(entry -> entry.getValue() == room);
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
        rooms.clear();
        byPlayer.clear();
    }
}
