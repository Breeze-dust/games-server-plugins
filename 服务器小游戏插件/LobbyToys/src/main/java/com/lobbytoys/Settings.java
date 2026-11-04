package com.lobbytoys;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** 一份启动/重载时读好的配置快照。 */
public final class Settings {

    public final List<String> worlds;
    public final boolean giveOnJoin;
    public final int itemSlot;
    public final double spinDegreesPerTick;
    public final double bobAmplitude;
    public final int bobPeriodTicks;
    public final long cooldownMillis;
    public final boolean hideFromOwner;

    private Settings(FileConfiguration config) {
        List<String> configured = config.getStringList("worlds");
        this.worlds = configured.isEmpty() ? List.of("world") : List.copyOf(configured);
        this.giveOnJoin = config.getBoolean("give-on-join", true);
        this.itemSlot = Math.max(0, Math.min(8, config.getInt("item-slot", 8)));
        this.spinDegreesPerTick = config.getDouble("spin-degrees-per-tick", 4.0);
        this.bobAmplitude = Math.max(0.0, config.getDouble("bob-amplitude", 0.18));
        this.bobPeriodTicks = Math.max(10, config.getInt("bob-period-ticks", 60));
        this.cooldownMillis = Math.max(0L, config.getLong("cooldown-millis", 1500L));
        this.hideFromOwner = config.getBoolean("hide-from-owner", true);
    }

    public static Settings load(FileConfiguration config) {
        return new Settings(config);
    }

    public boolean isAllowedWorld(org.bukkit.World world) {
        return world != null && worlds.contains(world.getName());
    }
}
