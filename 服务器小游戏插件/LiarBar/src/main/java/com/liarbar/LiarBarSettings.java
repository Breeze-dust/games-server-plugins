package com.liarbar;

import org.bukkit.configuration.file.FileConfiguration;

/** settings 段。 */
public final class LiarBarSettings {

    public int playersPerTable = 4;
    public int startCountdownSeconds = 3;
    public int handSize = 5;
    public int maxPlayCards = 3;
    public int turnSeconds = 30;
    public int roundDelaySeconds = 4;
    public int endDelaySeconds = 5;
    public int chambers = 6;

    public void load(FileConfiguration c) {
        playersPerTable = clamp(c.getInt("settings.players-per-table", 4), 2, 4);
        startCountdownSeconds = clamp(c.getInt("settings.start-countdown-seconds", 3), 1, 30);
        handSize = clamp(c.getInt("settings.hand-size", 5), 1, 5);
        maxPlayCards = clamp(c.getInt("settings.max-play-cards", 3), 1, 5);
        turnSeconds = clamp(c.getInt("settings.turn-seconds", 30), 5, 300);
        roundDelaySeconds = clamp(c.getInt("settings.round-delay-seconds", 4), 1, 30);
        endDelaySeconds = clamp(c.getInt("settings.end-delay-seconds", 5), 1, 60);
        chambers = clamp(c.getInt("settings.chambers", 6), 2, 12);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
