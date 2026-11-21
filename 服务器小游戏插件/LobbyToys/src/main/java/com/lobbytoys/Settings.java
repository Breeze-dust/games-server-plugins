package com.lobbytoys;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 一份启动/重载时读好的配置快照。 */
public final class Settings {

    public final List<String> worlds;
    public final boolean giveOnJoin;
    public final int itemSlot;
    public final double spinDegreesPerTick;
    /** 弹跳时模型最多缩小多少（0.15 = 最扁时缩到 85%）。0 = 不缩放。 */
    public final double squashAmount;
    /** 缩放弹跳的周期（刻）。 */
    public final int squashPeriodTicks;
    /** 缩小药水把玩家缩到原来的多少（0.2 - 1.0）。 */
    public final double shrinkScale;
    public final long cooldownMillis;
    public final boolean hideFromOwner;

    /** 可坐方块功能与参数。 */
    public final boolean chairsEnabled;
    public final Set<Material> chairBlocks;
    public final double chairSeatHeight;
    public final String chairMessage;

    private Settings(FileConfiguration config) {
        List<String> configured = config.getStringList("worlds");
        this.worlds = configured.isEmpty() ? List.of("world") : List.copyOf(configured);
        this.giveOnJoin = config.getBoolean("give-on-join", true);
        this.itemSlot = Math.max(0, Math.min(8, config.getInt("item-slot", 8)));
        this.spinDegreesPerTick = config.getDouble("spin-degrees-per-tick", 4.0);
        this.squashAmount = Math.max(0.0, Math.min(0.6, config.getDouble("squash-amount", 0.15)));
        this.squashPeriodTicks = Math.max(10, config.getInt("squash-period-ticks", 40));
        this.shrinkScale = Math.max(0.2, Math.min(1.0, config.getDouble("shrink-scale", 0.5)));
        this.cooldownMillis = Math.max(0L, config.getLong("cooldown-millis", 1500L));
        this.hideFromOwner = config.getBoolean("hide-from-owner", false);

        this.chairsEnabled = config.getBoolean("chairs.enabled", true);
        this.chairBlocks = resolveChairs(config);
        this.chairSeatHeight = config.getDouble("chairs.seat-height", 0.45);
        String msg = config.getString("chairs.message", "&7已坐下 &8· &7再右键或按 &fShift &7起身");
        this.chairMessage = msg == null ? "" : msg;
    }

    /** chairs.blocks 里每一项可以是材质名，也可以是原版方块标签名（如 STAIRS / SLABS / WOODEN_STAIRS）。 */
    private static Set<Material> resolveChairs(FileConfiguration config) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String raw : config.getStringList("chairs.blocks")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.trim();
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) {
                out.add(material);
                continue;
            }
            Tag<Material> tag = Bukkit.getTag("blocks",
                    NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)), Material.class);
            if (tag != null) {
                out.addAll(tag.getValues());
            }
        }
        if (out.isEmpty()) {
            out.addAll(Tag.STAIRS.getValues());
            out.addAll(Tag.SLABS.getValues());
        }
        return Collections.unmodifiableSet(out);
    }

    public static Settings load(FileConfiguration config) {
        return new Settings(config);
    }

    public boolean isAllowedWorld(org.bukkit.World world) {
        return world != null && worlds.contains(world.getName());
    }
}
