package com.liarbar;

import org.bukkit.configuration.file.FileConfiguration;

/** settings 段。 */
public final class LiarBarSettings {

    public int playersPerTable = 4;
    /** 少于坐满时，至少几人全部准备好才能开局（两人局 / 三人局）。 */
    public int minPlayers = 2;
    public int handSize = 5;
    public int maxPlayCards = 3;
    public int turnSeconds = 30;
    public int roundDelaySeconds = 4;
    public int endDelaySeconds = 5;
    public int chambers = 6;
    /** 揭幕时每张牌之间的间隔（tick）。 */
    public int revealDelayTicks = 14;
    /** 拿到左轮后多久不开枪就自动开（秒）。 */
    public int fireWaitSeconds = 15;
    /** 岩浆焚烧持续时间（秒）。 */
    public int burnSeconds = 6;
    /** 每次灼烧伤害（半颗心 = 1）；默认拉高，岩浆浇头后很快就会倒下。 */
    public double burnDamage = 8.0;
    /** 灼烧伤害的间隔（tick）。 */
    public int burnIntervalTicks = 5;
    /** 血条降到这个值就转观战（不会真的死）。 */
    public double burnStopHealth = 2.0;
    /** 空枪出水持续时间（秒）。 */
    public int waterSeconds = 3;
    /** 水/岩浆每往下流一格间隔多少 tick（20 tick = 1 秒，一格一格慢慢流）。 */
    public int flowTicksPerBlock = 20;
    /** 岩浆单独的流速（tick/格），默认跟 flowTicksPerBlock 一致。 */
    public int lavaFlowTicksPerBlock = 20;
    /** 水单独的流速（tick/格），默认跟 flowTicksPerBlock 一致。 */
    public int waterFlowTicksPerBlock = 20;

    public void load(FileConfiguration c) {
        playersPerTable = clamp(c.getInt("settings.players-per-table", 4), 2, 4);
        minPlayers = clamp(c.getInt("settings.min-players", 2), 2, playersPerTable);
        handSize = clamp(c.getInt("settings.hand-size", 5), 1, 5);
        maxPlayCards = clamp(c.getInt("settings.max-play-cards", 3), 1, 5);
        turnSeconds = clamp(c.getInt("settings.turn-seconds", 30), 5, 300);
        roundDelaySeconds = clamp(c.getInt("settings.round-delay-seconds", 4), 1, 30);
        endDelaySeconds = clamp(c.getInt("settings.end-delay-seconds", 5), 1, 60);
        chambers = clamp(c.getInt("settings.chambers", 6), 2, 12);
        revealDelayTicks = clamp(c.getInt("settings.reveal-delay-ticks", 14), 4, 100);
        fireWaitSeconds = clamp(c.getInt("settings.fire-wait-seconds", 15), 5, 120);
        burnSeconds = clamp(c.getInt("settings.burn-seconds", 6), 2, 30);
        burnDamage = Math.max(0.5, Math.min(20.0, c.getDouble("settings.burn-damage", 8.0)));
        burnIntervalTicks = clamp(c.getInt("settings.burn-interval-ticks", 5), 2, 100);
        burnStopHealth = Math.max(2.0, Math.min(18.0,
                c.getDouble("settings.burn-stop-health", 2.0)));
        waterSeconds = clamp(c.getInt("settings.water-seconds", 3), 1, 20);
        flowTicksPerBlock = clamp(c.getInt("settings.flow-ticks-per-block", 20), 1, 60);
        lavaFlowTicksPerBlock = clamp(
                c.getInt("settings.lava-flow-ticks-per-block", flowTicksPerBlock), 1, 60);
        waterFlowTicksPerBlock = clamp(
                c.getInt("settings.water-flow-ticks-per-block", flowTicksPerBlock), 1, 60);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
