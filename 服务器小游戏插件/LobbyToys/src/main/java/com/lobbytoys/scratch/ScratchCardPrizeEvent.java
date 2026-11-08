package com.lobbytoys.scratch;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 刮开一格奖品时触发。其他插件可以监听它来做「事件效果」，
 * 不想写代码的话，也可以在奖品配置里用 commands 直接跑命令。
 */
public class ScratchCardPrizeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String prizeId;
    private final int row;
    private final int column;

    public ScratchCardPrizeEvent(Player player, String prizeId, int row, int column) {
        this.player = player;
        this.prizeId = prizeId;
        this.row = row;
        this.column = column;
    }

    public Player getPlayer() {
        return player;
    }

    public String getPrizeId() {
        return prizeId;
    }

    /** 0 开始的行号。 */
    public int getRow() {
        return row;
    }

    /** 0 开始的列号。 */
    public int getColumn() {
        return column;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
