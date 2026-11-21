package com.dontdoit;

import com.hub.util.Chat;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 侧边栏。
 * <p>和公共库的 LiveSidebar 不同：这里用 Paper 的 {@code Score#numberFormat} 把「右侧数字」
 * 和「排序分数」解耦——排序分数固定（按队伍顺序），右侧显示的是该行队伍距离换词条的秒数，
 * 所以倒计时每秒变化时玩家名字的顺序不会跳动。
 */
public final class Sidebar {

    /** 一行：左侧文字、排序分数、右侧自定义显示（null = 空白）。 */
    public record Line(String text, int score, Component display) {
    }

    private static final class Board {
        Scoreboard scoreboard;
        Objective objective;
        String title;
        final Set<UUID> viewers = new HashSet<>();
    }

    private final Plugin plugin;
    private final Map<String, Board> boards = new HashMap<>();

    public Sidebar(Plugin plugin) {
        this.plugin = plugin;
    }

    public void update(String key, String title, Collection<UUID> viewerIds, java.util.List<Line> lines) {
        if (key == null || viewerIds == null || viewerIds.isEmpty()) {
            hide(key);
            return;
        }
        Board board = boards.get(key);
        if (board == null) {
            board = create(key, title);
            if (board == null) {
                return;
            }
        }
        if (title != null && !title.equals(board.title)) {
            board.title = title;
            board.objective.displayName(Chat.color(title));
        }

        Map<String, Line> desired = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line != null && line.text() != null && !line.text().isBlank()) {
                desired.put(line.text(), line);
            }
        }
        // 删除不再需要的行
        for (String text : new ArrayList<>(board.objective.getScoreboard().getEntries())) {
            if (!desired.containsKey(text)) {
                board.scoreboard.resetScores(text);
            }
        }
        for (Line line : desired.values()) {
            Score score = board.objective.getScore(line.text());
            score.setScore(line.score());
            Component display = line.display();
            score.numberFormat(display == null ? NumberFormat.blank() : NumberFormat.fixed(display));
        }

        Set<UUID> now = new HashSet<>();
        for (UUID id : viewerIds) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                continue;
            }
            now.add(id);
            if (!board.scoreboard.equals(p.getScoreboard())) {
                p.setScoreboard(board.scoreboard);
            }
        }
        for (UUID id : new ArrayList<>(board.viewers)) {
            if (!now.contains(id)) {
                restore(Bukkit.getPlayer(id));
            }
        }
        board.viewers.clear();
        board.viewers.addAll(now);
    }

    public void hide(String key) {
        Board board = boards.remove(key);
        if (board == null) {
            return;
        }
        for (UUID id : board.viewers) {
            restore(Bukkit.getPlayer(id));
        }
        board.viewers.clear();
    }

    public void hideAll() {
        for (String key : new ArrayList<>(boards.keySet())) {
            hide(key);
        }
    }

    private Board create(String key, String title) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Board board = new Board();
        board.scoreboard = manager.getNewScoreboard();
        board.title = title == null ? "&e小游戏" : title;
        String objectiveName = "ddi_" + sanitize(key);
        board.objective = board.scoreboard.registerNewObjective(objectiveName,
                Criteria.DUMMY, Chat.color(board.title));
        board.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        boards.put(key, board);
        return board;
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

    private static String sanitize(String key) {
        String out = key.replaceAll("[^a-zA-Z0-9_]", "_");
        return out.length() > 20 ? out.substring(0, 20) : out;
    }
}
