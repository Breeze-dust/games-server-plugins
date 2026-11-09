package com.superniuma.mechanism;

import org.bukkit.Material;

import java.util.List;

/** mechanisms.yml 里的一种机关。 */
public final class MechanismDef {

    private final String id;
    private final boolean enabled;
    private final double weight;
    private final Material icon;
    private final String displayName;
    private final List<String> lore;
    private final RotationRule rotation;
    private final double points;
    private final int intervalTicks;
    private final int arrowRange;
    private final double arrowSpeed;
    private final int bombRadius;
    private final double lowClearRate;
    private final double lowClearWeight;
    private final int lowClearMinCount;
    private final double scale;
    private final int maxOnField;
    private final double height;
    private final List<Variant> variants;

    public MechanismDef(String id, boolean enabled, double weight, Material icon, String displayName,
                        List<String> lore, RotationRule rotation, double points, int intervalTicks,
                        int arrowRange, double arrowSpeed, int bombRadius, double lowClearRate,
                        double lowClearWeight, int lowClearMinCount, double scale, int maxOnField,
                        double height, List<Variant> variants) {
        this.id = id;
        this.enabled = enabled;
        this.weight = Math.max(0.0, weight);
        this.icon = icon;
        this.displayName = displayName;
        this.lore = List.copyOf(lore);
        this.rotation = rotation;
        this.points = points;
        this.intervalTicks = Math.max(1, intervalTicks);
        this.arrowRange = Math.max(1, arrowRange);
        this.arrowSpeed = arrowSpeed;
        this.bombRadius = Math.max(0, bombRadius);
        this.lowClearRate = lowClearRate;
        this.lowClearWeight = Math.max(0.0, lowClearWeight);
        this.lowClearMinCount = Math.max(0, lowClearMinCount);
        this.scale = scale;
        this.maxOnField = Math.max(0, maxOnField);
        this.height = Math.max(0.0, height);
        this.variants = List.copyOf(variants);
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public double weight() {
        return weight;
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public RotationRule rotation() {
        return rotation;
    }

    public double points() {
        return points;
    }

    public int intervalTicks() {
        return intervalTicks;
    }

    public int arrowRange() {
        return arrowRange;
    }

    public double arrowSpeed() {
        return arrowSpeed;
    }

    public int bombRadius() {
        return bombRadius;
    }

    public double lowClearRate() {
        return lowClearRate;
    }

    public double lowClearWeight() {
        return lowClearWeight;
    }

    public int lowClearMinCount() {
        return lowClearMinCount;
    }

    public double scale() {
        return scale;
    }

    /** 该机关在场上同时存在的最大数量（金币用；0 表示不允许出现）。 */
    public int maxOnField() {
        return maxOnField;
    }

    /** 弹簧的弹射高度（格）。 */
    public double height() {
        return height;
    }

    public List<Variant> variants() {
        return variants;
    }

    /** 机关的尺寸子类。平台/尖刺用 sizeX × sizeZ 的平面尺寸；发射器用 sizeX（宽）× sizeY（高），深度固定 1。 */
    public record Variant(String id, String name, double weight, int sizeX, int sizeY, int sizeZ) {
    }
}
