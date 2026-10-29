package com.hub.api;

/**
 * 玩家在某个小游戏中的状态。
 * 游戏插件在进入等待室时置 {@link #WAITING}，游戏正式开始后置 {@link #PLAYING}。
 */
public enum GameState {

    /** 在游戏等待室排队，游戏尚未开始。 */
    WAITING,

    /** 游戏已开始，玩家已加入对局。 */
    PLAYING
}
