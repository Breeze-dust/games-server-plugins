package com.hub.util;

import com.hub.api.HubAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把「主城观战者」挂到游戏自己的 HUD（计分板 / BossBar）上，并在他们离开时还原。
 *
 * <p>游戏周期性调用 {@link #viewers(String, Collection, double)} 拿到「参赛者 + 附近观战者」，
 * 再把 HUD 发给这个列表；观战者退出观战或离开场地后，下一次调用会自动把他们摘掉。
 * 比赛结束时调 {@link #restore(String)} 把剩下的一次性还原。
 *
 * <p>计分板类 HUD 会在还原时把玩家切回主计分板；BossBar 类 HUD 请自行移除观众。
 */
public final class SpectatorHud {

    private final Map<String, Set<UUID>> attached = new HashMap<>();

    /** 参赛者 + 附近的主城观战者；已经离开的观战者会被自动还原。 */
    public List<UUID> viewers(String key, Collection<UUID> participants, double radius) {
        if (participants == null || participants.isEmpty()) {
            restore(key);
            return List.of();
        }
        Set<UUID> watching = attached.computeIfAbsent(key, k -> new HashSet<>());
        List<UUID> near = HubAPI.spectatorsNear(participants, radius);
        for (UUID id : new ArrayList<>(watching)) {
            if (!near.contains(id)) {
                watching.remove(id);
                restoreScoreboard(Bukkit.getPlayer(id));
            }
        }
        watching.addAll(near);
        List<UUID> out = new ArrayList<>(participants);
        out.addAll(watching);
        return out;
    }

    /** 比赛结束：把这块 HUD 挂上的观战者全部还原。 */
    public void restore(String key) {
        Set<UUID> watching = attached.remove(key);
        if (watching == null) {
            return;
        }
        for (UUID id : watching) {
            restoreScoreboard(Bukkit.getPlayer(id));
        }
    }

    public void restoreAll() {
        for (String key : new ArrayList<>(attached.keySet())) {
            restore(key);
        }
    }

    private void restoreScoreboard(Player p) {
        if (p == null) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            p.setScoreboard(manager.getMainScoreboard());
        }
    }
}
