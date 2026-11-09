package com.superniuma.mechanism;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一名玩家在摆放环节里的半透明预览状态。 */
public final class PlacementSession {

    private final Player player;
    private final MechanismChoice choice;
    private int distance;
    private int yawSteps;
    private boolean flipped;
    private Location anchor;
    private final List<BlockDisplay> ghosts = new ArrayList<>();
    private List<Location> ghostCells = List.of();
    private boolean confirmed;

    public PlacementSession(Player player, MechanismChoice choice, int startDistance) {
        this.player = player;
        this.choice = choice;
        this.distance = startDistance;
    }

    public Player player() {
        return player;
    }

    public UUID owner() {
        return player.getUniqueId();
    }

    public MechanismChoice choice() {
        return choice;
    }

    public int distance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int yawSteps() {
        return yawSteps;
    }

    public void setYawSteps(int yawSteps) {
        this.yawSteps = yawSteps & 3;
    }

    public boolean flipped() {
        return flipped;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    public Location anchor() {
        return anchor == null ? null : anchor.clone();
    }

    public void setAnchor(Location anchor) {
        this.anchor = anchor == null ? null : anchor.clone();
    }

    public List<BlockDisplay> ghosts() {
        return ghosts;
    }

    public List<Location> ghostCells() {
        return ghostCells;
    }

    public void setGhostCells(List<Location> ghostCells) {
        this.ghostCells = ghostCells == null ? List.of() : ghostCells;
    }

    public boolean confirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
