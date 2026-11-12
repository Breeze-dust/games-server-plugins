package com.superniuma.mechanism;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
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
    private int pitchSteps;
    private int baseYawSteps;
    private Location anchor;
    private final List<Entity> ghosts = new ArrayList<>();
    private List<Location> ghostCells = List.of();
    private boolean confirmed;
    private boolean locked;
    private boolean portalSecondStage;
    private Location portalFirstAnchor;
    private BlockFace portalFirstFacing;

    private boolean rendered;
    private int renderedYawSteps;
    private int renderedPitchSteps;
    private Location renderedAnchor;

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

    /** 锁定位置时玩家朝向对应的基准 yaw；发射器等需要保持出口朝向的机关会用到。 */
    public int baseYawSteps() {
        return baseYawSteps;
    }

    public void setBaseYawSteps(int baseYawSteps) {
        this.baseYawSteps = baseYawSteps & 3;
    }

    public int pitchSteps() {
        return pitchSteps;
    }

    public void setPitchSteps(int pitchSteps) {
        this.pitchSteps = pitchSteps & 3;
    }

    public Location anchor() {
        return anchor == null ? null : anchor.clone();
    }

    public void setAnchor(Location anchor) {
        this.anchor = anchor == null ? null : anchor.clone();
    }

    public List<Entity> ghosts() {
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

    /** 机关位置是否已被第一次调整操作锁定；锁定后不再跟随玩家移动。 */
    public boolean locked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean portalSecondStage() {
        return portalSecondStage;
    }

    public void setPortalSecondStage(boolean portalSecondStage) {
        this.portalSecondStage = portalSecondStage;
    }

    public Location portalFirstAnchor() {
        return portalFirstAnchor == null ? null : portalFirstAnchor.clone();
    }

    public void setPortalFirstAnchor(Location anchor) {
        this.portalFirstAnchor = anchor == null ? null : anchor.clone();
    }

    public BlockFace portalFirstFacing() {
        return portalFirstFacing;
    }

    public void setPortalFirstFacing(BlockFace facing) {
        this.portalFirstFacing = facing;
    }

    /** 记录最后一次真正渲染出来的预览形态，放置时以它为准。 */
    public void setRendered(Location anchor, int yawSteps, int pitchSteps) {
        this.rendered = anchor != null;
        this.renderedAnchor = anchor == null ? null : anchor.clone();
        this.renderedYawSteps = yawSteps & 3;
        this.renderedPitchSteps = pitchSteps & 3;
    }

    public boolean rendered() {
        return rendered;
    }

    public int renderedYawSteps() {
        return renderedYawSteps;
    }

    public int renderedPitchSteps() {
        return renderedPitchSteps;
    }

    public Location renderedAnchor() {
        return renderedAnchor == null ? null : renderedAnchor.clone();
    }
}
