package com.archery;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一名玩家的一局射箭。 */
public final class ArcherySession {

    public final UUID playerId;
    public final String playerName;
    /** 进入游戏前的位置和视角，结束时原样回去。 */
    public final Location returnLocation;
    public final SavedInventory inventory;
    public final List<TargetInstance> targets = new ArrayList<>();
    /** 自己射出的、还在飞行的箭：记录上一刻位置用于扫掠命中检测。 */
    public final Map<UUID, ArrowTrack> arrows = new LinkedHashMap<>();

    public int score;
    public int ticksLeft;
    public int tickCounter;
    public boolean finished;

    public ArcherySession(UUID playerId, String playerName, Location returnLocation,
                          SavedInventory inventory, int ticksLeft) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.returnLocation = returnLocation;
        this.inventory = inventory;
        this.ticksLeft = ticksLeft;
    }

    public static final class ArrowTrack {
        public Location previous;

        public ArrowTrack(Location previous) {
            this.previous = previous;
        }
    }
}
