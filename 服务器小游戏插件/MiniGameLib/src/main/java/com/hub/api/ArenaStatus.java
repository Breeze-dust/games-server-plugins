package com.hub.api;

import org.bukkit.Location;

/**
 * 某个游戏一个场地的实时状态（主城登记的，由各游戏通过 {@link HubAPI#setArena} 上报）。
 *
 * @param name    场地名
 * @param state   等待中 / 游戏中
 * @param players 当前在场人数
 */
public record ArenaStatus(String name, GameState state, int players, Location spectator) {
}
