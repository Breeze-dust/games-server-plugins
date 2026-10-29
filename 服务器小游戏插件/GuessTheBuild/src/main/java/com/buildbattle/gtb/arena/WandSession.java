package com.buildbattle.gtb.arena;

import org.bukkit.Location;

/** 木棍选区会话：记录正在选区的玩家及其两个角点。 */
public class WandSession {

    public Location pos1;
    public Location pos2;

    public boolean complete() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }
}
