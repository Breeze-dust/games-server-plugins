package com.dontdoit.criteria;

import org.bukkit.entity.Player;

import java.util.function.Function;
import java.util.function.Predicate;

/** 判定上下文：场地地面高度、当前 tick、参赛玩家与状态表。 */
public final class MatchContext {

    public final int groundY;
    public final long tick;
    private final Predicate<Player> participant;
    private final Function<Player, PlayerState> states;

    public MatchContext(int groundY, long tick, Predicate<Player> participant,
                        Function<Player, PlayerState> states) {
        this.groundY = groundY;
        this.tick = tick;
        this.participant = participant;
        this.states = states;
    }

    public boolean isParticipant(Player player) {
        return participant != null && participant.test(player);
    }

    public PlayerState state(Player player) {
        return states == null ? null : states.apply(player);
    }
}
