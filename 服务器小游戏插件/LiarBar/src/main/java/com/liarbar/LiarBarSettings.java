package com.liarbar;

import org.bukkit.configuration.file.FileConfiguration;

/** settings 段。 */
public final class LiarBarSettings {

    public int playersPerTable = 4;
    /** 少于坐满时，至少几人全部准备好才能开局（两人局 / 三人局）。 */
    public int minPlayers = 2;
    public int startCountdownSeconds = 3;
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
    /** 空枪出水持续时间（秒）。 */
    public int waterSeconds = 3;
    /** 水/岩浆每往下流一格间隔多少 tick。 */
    public int flowTicksPerBlock = 6;

    public void load(FileConfiguration c) {
        playersPerTable = clamp(c.getInt("settings.players-per-table", 4), 2, 4);
        minPlayers = clamp(c.getInt("settings.min-players", 2), 2, playersPerTable);
        startCountdownSeconds = clamp(c.getInt("settings.start-countdown-seconds", 3), 1, 30);
        handSize = clamp(c.getInt("settings.hand-size", 5), 1, 5);
        maxPlayCards = clamp(c.getInt("settings.max-play-cards", 3), 1, 5);
        turnSeconds = clamp(c.getInt("settings.turn-seconds", 30), 5, 300);
        roundDelaySeconds = clamp(c.getInt("settings.round-delay-seconds", 4), 1, 30);
        endDelaySeconds = clamp(c.getInt("settings.end-delay-seconds", 5), 1, 60);
        chambers = clamp(c.getInt("settings.chambers", 6), 2, 12);
        revealDelayTicks = clamp(c.getInt("settings.reveal-delay-ticks", 14), 4, 100);
        fireWaitSeconds = clamp(c.getInt("settings.fire-wait-seconds", 15), 5, 120);
        burnSeconds = clamp(c.getInt("settings.burn-seconds", 6), 2, 30);
        waterSeconds = clamp(c.getInt("settings.water-seconds", 3), 1, 20);
        flowTicksPerBlock = clamp(c.getInt("settings.flow-ticks-per-block", 6), 1, 40);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
