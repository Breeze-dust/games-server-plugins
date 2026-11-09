package com.superniuma.game;

/** 房间模式。 */
public enum GameMode {
    SOLO,
    TEAM;

    public static GameMode parse(String raw) {
        if (raw == null) {
            return SOLO;
        }
        return raw.equalsIgnoreCase("team") || raw.equalsIgnoreCase("团队") ? TEAM : SOLO;
    }
}
