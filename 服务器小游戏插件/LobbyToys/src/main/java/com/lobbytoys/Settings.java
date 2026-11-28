package com.lobbytoys;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

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

    /** 椅子功能参数（哪些方块能坐由 chairs.yml 登记决定）。 */
    public final boolean chairsEnabled;
    public final double chairSeatHeight;
    public final String chairMessage;

    /** 头顶骑乘（右键玩家坐到对方头顶）。 */
    public final boolean headSitEnabled;
    public final double headSitMaxDistance;
    /** 空手长按右键多久算「甩飞」（毫秒）。 */
    public final long headSitHoldMillis;
    public final double headSitThrowPower;
    public final double headSitThrowUp;
    /** 进入这些游戏时不自动下来（其余游戏 / 离开主城都会自动下来）。 */
    public final List<String> headSitKeepGames;
    public final String headSitMessageSit;
    public final String headSitMessageSat;
    public final String headSitMessageThrown;

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
        this.chairSeatHeight = config.getDouble("chairs.seat-height", 0.45);
        String msg = config.getString("chairs.message", "&7已坐下 &8· &7再右键或按 &fShift &7起身");
        this.chairMessage = msg == null ? "" : msg;

        this.headSitEnabled = config.getBoolean("head-sit.enabled", true);
        this.headSitMaxDistance = Math.max(1.0, config.getDouble("head-sit.max-distance", 5.0));
        this.headSitHoldMillis = Math.max(200L, config.getLong("head-sit.hold-millis", 600L));
        this.headSitThrowPower = Math.max(0.0, config.getDouble("head-sit.throw-power", 1.4));
        this.headSitThrowUp = Math.max(0.0, config.getDouble("head-sit.throw-up", 0.9));
        List<String> keep = config.getStringList("head-sit.keep-games");
        this.headSitKeepGames = keep.isEmpty() ? List.of("骗子酒馆") : List.copyOf(keep);
        this.headSitMessageSit = message(config, "head-sit.messages.sit",
                "&7你坐到了 &f%mount% &7的头顶 &8· &7按 &fShift &7下来");
        this.headSitMessageSat = message(config, "head-sit.messages.sat",
                "&f%rider% &7坐到了你的头顶 &8· &fShift &7让他下来 / &7空手对着方块按住右键甩飞");
        this.headSitMessageThrown = message(config, "head-sit.messages.thrown", "&e你被甩飞了！");
    }

    private static String message(FileConfiguration config, String path, String fallback) {
        String text = config.getString(path, fallback);
        return text == null ? "" : text;
    }

    public static Settings load(FileConfiguration config) {
        return new Settings(config);
    }

    public boolean isAllowedWorld(org.bukkit.World world) {
        return world != null && worlds.contains(world.getName());
    }
}
