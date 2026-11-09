package com.superniuma.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 单个回合内的临时数据。 */
public final class RoundData {

    private final long startMillis = System.currentTimeMillis();
    private final List<UUID> finishOrder = new ArrayList<>();
    private final Map<UUID, Long> finishTimes = new HashMap<>();
    private final Set<UUID> failed = new HashSet<>();
    private final Map<UUID, Double> coinPoints = new HashMap<>();
    private final Map<UUID, Integer> trapFails = new HashMap<>();

    public long startMillis() {
        return startMillis;
    }

    public List<UUID> finishOrder() {
        return finishOrder;
    }

    public Map<UUID, Long> finishTimes() {
        return finishTimes;
    }

    public Set<UUID> failed() {
        return failed;
    }

    public Map<UUID, Double> coinPoints() {
        return coinPoints;
    }

    public Map<UUID, Integer> trapFails() {
        return trapFails;
    }

    public void addCoin(UUID player, double points) {
        coinPoints.merge(player, points, Double::sum);
    }

    public void addTrapFail(UUID owner) {
        if (owner != null) {
            trapFails.merge(owner, 1, Integer::sum);
        }
    }
}
