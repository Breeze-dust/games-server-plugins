package com.superniuma.mechanism;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 已经放到世界里的一个机关。 */
public final class MechanismInstance {

    private final String id;
    private final MechanismDef def;
    private final MechanismDef.Variant variant;
    private final UUID owner;
    private final Location anchor;
    private final int yawSteps;
    private final int pitchSteps;
    private final BlockFace facing;
    private final List<PlacedBlock> blocks = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private int cooldown;
    private boolean removed;

    public MechanismInstance(MechanismDef def, MechanismDef.Variant variant, UUID owner,
                             Location anchor, int yawSteps, int pitchSteps, BlockFace facing) {
        this.id = UUID.randomUUID().toString();
        this.def = def;
        this.variant = variant;
        this.owner = owner;
        this.anchor = anchor.clone();
        this.yawSteps = yawSteps & 3;
        this.pitchSteps = pitchSteps & 3;
        this.facing = facing;
        this.cooldown = def.intervalTicks();
    }

    public String id() {
        return id;
    }

    public MechanismDef def() {
        return def;
    }

    public MechanismDef.Variant variant() {
        return variant;
    }

    public UUID owner() {
        return owner;
    }

    public Location anchor() {
        return anchor.clone();
    }

    public int yawSteps() {
        return yawSteps;
    }

    public int pitchSteps() {
        return pitchSteps;
    }

    public BlockFace facing() {
        return facing;
    }

    public List<PlacedBlock> blocks() {
        return blocks;
    }

    public List<Entity> entities() {
        return entities;
    }

    public int cooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public boolean removed() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    /** 机关是否占据某个方块。 */
    public boolean occupies(Location loc) {
        for (PlacedBlock block : blocks) {
            if (sameBlock(block.location(), loc)) {
                return true;
            }
        }
        return false;
    }

    /** 机关是否有任意一个方块落在以 center 为中心、半径 radius 的立方体内。 */
    public boolean intersectsCube(Location center, int radius) {
        if (blocks.isEmpty()) {
            return Math.abs(anchor.getBlockX() - center.getBlockX()) <= radius
                    && Math.abs(anchor.getBlockY() - center.getBlockY()) <= radius
                    && Math.abs(anchor.getBlockZ() - center.getBlockZ()) <= radius;
        }
        for (PlacedBlock block : blocks) {
            Location loc = block.location();
            if (Math.abs(loc.getBlockX() - center.getBlockX()) <= radius
                    && Math.abs(loc.getBlockY() - center.getBlockY()) <= radius
                    && Math.abs(loc.getBlockZ() - center.getBlockZ()) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
