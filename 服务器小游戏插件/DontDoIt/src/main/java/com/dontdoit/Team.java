package com.dontdoit;

import com.dontdoit.criteria.Criterion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 一支队伍：颜色、成员、规则数量（生命）、当前词条。 */
public final class Team {

    private final TeamColor color;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final List<String> history = new ArrayList<>();

    private int lives;
    private boolean eliminated;
    private Criterion criterion;
    private long criterionSince;

    private String customCriteria;

    private int jumpCount;
    private int placeCount;
    private double walkDistance;

    private long guessReadyTick;

    public Team(TeamColor color, int lives) {
        this.color = color;
        this.lives = lives;
    }

    public TeamColor getColor() {
        return color;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = Math.max(0, lives);
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public Criterion getCriterion() {
        return criterion;
    }

    public void setCriterion(Criterion criterion, long now) {
        this.criterion = criterion;
        this.criterionSince = now;
        this.jumpCount = 0;
        this.placeCount = 0;
        this.walkDistance = 0;
        if (criterion != null) {
            history.add(criterion.key);
            while (history.size() > 20) {
                history.remove(0);
            }
        }
    }

    public long getCriterionSince() {
        return criterionSince;
    }

    public List<String> getHistory() {
        return history;
    }

    public String getCustomCriteria() {
        return customCriteria;
    }

    public void setCustomCriteria(String customCriteria) {
        this.customCriteria = customCriteria;
    }

    public int getJumpCount() {
        return jumpCount;
    }

    public int addJump() {
        return ++jumpCount;
    }

    public int getPlaceCount() {
        return placeCount;
    }

    public int addPlace() {
        return ++placeCount;
    }

    public double getWalkDistance() {
        return walkDistance;
    }

    public double addWalk(double distance) {
        walkDistance += distance;
        return walkDistance;
    }

    public long getGuessReadyTick() {
        return guessReadyTick;
    }

    public void setGuessReadyTick(long tick) {
        this.guessReadyTick = tick;
    }

    public Set<UUID> playerIds() {
        return players;
    }

    public Collection<? extends Player> members() {
        List<Player> out = new ArrayList<>();
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }

    public int size() {
        return players.size();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean contains(Player player) {
        return player != null && players.contains(player.getUniqueId());
    }

    public void add(Player player) {
        players.add(player.getUniqueId());
    }

    public void remove(Player player) {
        players.remove(player.getUniqueId());
    }

    public void clearMembers() {
        players.clear();
    }

    public String displayName() {
        return color.coloredName();
    }
}
