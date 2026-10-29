package com.hub.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小游戏内的实时名次侧边栏（右侧计分板）。
 *
 * <p>按 key（一般是“游戏:场地”）维护一块计分板，游戏每秒调用一次 {@link #update} 刷新内容；
 * 结束时调用 {@link #hide} 把玩家还原成主计分板。玩家退出会自动从所有板子移除。
 *
 * <pre>
 *   live.update("jumpfill", "&#x26;b&#x26;l跳水填格", playerIds, List.of(
 *       new LiveSidebar.Line("&#x26;e玩家A", 12),
 *       new LiveSidebar.Line("&#x26;f玩家B", 8)));
 *   live.hide("jumpfill");
 * </pre>
 *
 * 右侧数字就是 Line 的 score；条目按分数从高到低自动排序（同分按文字排序）。
 */
public final class LiveSidebar {

    /** 一行内容：显示文字 + 右侧数字。 */
    public record Line(String text, int score) {
    }

    private static final class Board {
        Scoreboard scoreboard;
        Objective objective;
        String title;
        final Map<String, Integer> entries = new LinkedHashMap<>();
        final Set<UUID> players = new HashSet<>();
    }

    private final Plugin plugin;
    private final Map<String, Board> boards = new HashMap<>();

    public LiveSidebar(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                UUID id = event.getPlayer().getUniqueId();
                for (Board board : boards.values()) {
                    board.players.remove(id);
                }
            }
        }, plugin);
    }

    /** 刷新（或创建）一块侧边栏，并把 players 全部切到这块板子。 */
    public void update(String key, String title, Collection<UUID> players, List<Line> lines) {
        if (key == null || players == null || players.isEmpty()) {
            hide(key);
            return;
        }
        Board board = boards.get(key);
        if (board == null) {
            board = create(key, title);
        }
        if (title != null && !title.equals(board.title)) {
            board.title = title;
            board.objective.displayName(Chat.color(title));
        }
        Map<String, Integer> desired = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line != null && line.text() != null && !line.text().isBlank()) {
                desired.put(line.text(), line.score());
            }
        }
        List<String> remove = new ArrayList<>();
        for (String text : board.entries.keySet()) {
            if (!desired.containsKey(text)) {
                remove.add(text);
            }
        }
        for (String text : remove) {
            board.scoreboard.resetScores(text);
            board.entries.remove(text);
        }
        for (Map.Entry<String, Integer> e : desired.entrySet()) {
            board.scoreboard.getObjective(board.objective.getName())
                    .getScore(e.getKey()).setScore(e.getValue());
            board.entries.put(e.getKey(), e.getValue());
        }
        Set<UUID> now = new HashSet<>();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                continue;
            }
            now.add(id);
            if (!p.getScoreboard().equals(board.scoreboard)) {
                p.setScoreboard(board.scoreboard);
            }
        }
        for (UUID id : new ArrayList<>(board.players)) {
            if (!now.contains(id)) {
                restore(Bukkit.getPlayer(id));
            }
        }
        board.players.clear();
        board.players.addAll(now);
    }

    /** 关闭并还原这块侧边栏。 */
    public void hide(String key) {
        Board board = boards.remove(key);
        if (board == null) {
            return;
        }
        for (UUID id : board.players) {
            restore(Bukkit.getPlayer(id));
        }
        board.players.clear();
    }

    /** 插件关闭时把所有板子还回去。 */
    public void hideAll() {
        for (String key : new ArrayList<>(boards.keySet())) {
            hide(key);
        }
    }

    private Board create(String key, String title) {
        Board board = new Board();
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board.scoreboard = manager == null ? null : manager.getNewScoreboard();
        if (board.scoreboard != null) {
            String safeTitle = title == null ? "&e小游戏" : title;
            board.title = safeTitle;
            board.objective = board.scoreboard.registerNewObjective("live_" + sanitize(key),
                    Criteria.DUMMY, Chat.color(safeTitle));
            board.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        boards.put(key, board);
        return board;
    }

    private static String sanitize(String key) {
        String out = key.replaceAll("[^a-zA-Z0-9_]", "_");
        return out.length() > 16 ? out.substring(0, 16) : out;
    }

    private void restore(Player player) {
        if (player == null) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }
}
