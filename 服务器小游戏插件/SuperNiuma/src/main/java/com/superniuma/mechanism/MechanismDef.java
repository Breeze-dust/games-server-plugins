package com.superniuma.mechanism;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

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
    private final double distance;
    private final ConfigurationSection section;
    private final List<Variant> variants;

    public MechanismDef(String id, boolean enabled, double weight, Material icon, String displayName,
                        List<String> lore, RotationRule rotation, double points, int intervalTicks,
                        int arrowRange, double arrowSpeed, int bombRadius, double lowClearRate,
                        double lowClearWeight, int lowClearMinCount, double scale, int maxOnField,
                        double height, double distance, ConfigurationSection section,
                        List<Variant> variants) {
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
        this.distance = Math.max(0.0, distance);
        this.section = section;
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

    /** 活塞的击飞距离（格）。 */
    public double distance() {
        return distance;
    }

    /** 原始配置段，供各机关读取自己的专属参数。 */
    public ConfigurationSection section() {
        return section;
    }

    /** 读取本机关专属参数（缺省返回 def）。 */
    public double param(String path, double def) {
        return section == null ? def : section.getDouble(path, def);
    }

    public int paramInt(String path, int def) {
        return section == null ? def : section.getInt(path, def);
    }

    public List<Variant> variants() {
        return variants;
    }

    /** 机关的尺寸子类。平台/尖刺用 sizeX × sizeZ 的平面尺寸；发射器用 sizeX（宽）× sizeY（高），深度固定 1。 */
    public record Variant(String id, String name, double weight, int sizeX, int sizeY, int sizeZ) {
    }
}
