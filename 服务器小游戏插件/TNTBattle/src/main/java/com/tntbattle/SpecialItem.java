package com.tntbattle;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 特殊道具池中的一项：类型决定行为，其余属性从 config 读取。 */
public final class SpecialItem {

    public enum Type {
        FLAME, BLACKHOLE, DECOY, HIGHEX, BRIDGE_EGG, ANGEL, STICKY
    }

    public final String id;
    public final Type type;
    public final boolean enabled;
    public final int unlockSeconds;
    public final double weight;
    public final Material material;
    public final String name;
    public final List<String> lore;
    public final double velocity;
    public final int fuseTicks;
    public final float yield;
    public final double damage;
    public final double damageRadius;
    public final double pullRadius;
    public final double pullStrength;
    public final int pullSeconds;
    public final double lockRadius;
    public final int lockSeconds;
    public final double explosionRadius;
    public final int maxBlocks;
    public final int width;
    public final int levitationSeconds;
    public final int levitationAmplifier;
    /** 烈焰弹炸在自己附近时给发射者的竖直初速度（0 = 关闭）。 */
    public final double selfLaunch;
    public final double selfLaunchRadius;

    private SpecialItem(String id, Type type, boolean enabled, int unlockSeconds, double weight, Material material,
                        String name, List<String> lore,
                        double velocity, int fuseTicks, float yield, double damage, double damageRadius,
                        double pullRadius, double pullStrength, int pullSeconds,
                        double lockRadius, int lockSeconds, double explosionRadius,
                        int maxBlocks, int width, int levitationSeconds, int levitationAmplifier,
                        double selfLaunch, double selfLaunchRadius) {
        this.id = id;
        this.type = type;
        this.enabled = enabled;
        this.unlockSeconds = unlockSeconds;
        this.weight = weight;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.velocity = velocity;
        this.fuseTicks = fuseTicks;
        this.yield = yield;
        this.damage = damage;
        this.damageRadius = damageRadius;
        this.pullRadius = pullRadius;
        this.pullStrength = pullStrength;
        this.pullSeconds = pullSeconds;
        this.lockRadius = lockRadius;
        this.lockSeconds = lockSeconds;
        this.explosionRadius = explosionRadius;
        this.maxBlocks = maxBlocks;
        this.width = width;
        this.levitationSeconds = levitationSeconds;
        this.levitationAmplifier = levitationAmplifier;
        this.selfLaunch = selfLaunch;
        this.selfLaunchRadius = selfLaunchRadius;
    }

    public static SpecialItem fromMap(Map<?, ?> raw, int index) {
        String typeName = string(raw.get("type"), "");
        Type type;
        try {
            type = Type.valueOf(typeName.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String id = string(raw.get("id"), typeName.toLowerCase(Locale.ROOT) + "_" + index);
        boolean enabled = bool(raw.get("enabled"), true);
        double weight = number(raw.get("weight"), 20.0);
        Material material = Material.matchMaterial(string(raw.get("material"), "TNT"), false);
        if (material == null || !material.isItem()) {
            material = Material.TNT;
        }
        String name = string(raw.get("name"), "&e特殊道具");
        List<String> lore = new ArrayList<>();
        Object loreRaw = raw.get("lore");
        if (loreRaw instanceof List<?> list) {
            for (Object line : list) {
                lore.add(String.valueOf(line));
            }
        }
        return new SpecialItem(
                id, type, enabled, Math.max(0, integer(raw.get("unlock-seconds"), 0)), Math.max(0.0, weight), material, name, lore,
                number(raw.get("velocity"), 1.2),
                integer(raw.get("fuse-ticks"), 40),
                (float) number(raw.get("yield"), 4.0),
                number(raw.get("damage"), 10.0),
                number(raw.get("damage-radius"), 6.0),
                number(raw.get("pull-radius"), 8.0),
                number(raw.get("pull-strength"), 0.35),
                integer(raw.get("pull-seconds"), 4),
                number(raw.get("lock-radius"), 12.0),
                integer(raw.get("lock-seconds"), 5),
                number(raw.get("explosion-radius"), 4.0),
                integer(raw.get("max-blocks"), 30),
                Math.max(1, Math.min(9, integer(raw.get("width"), 1))),
                integer(raw.get("levitation-seconds"), 6),
                integer(raw.get("levitation-amplifier"), 0),
                number(raw.get("self-launch"), 1.0),
                Math.max(0.0, number(raw.get("self-launch-radius"), 3.0)));
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
