package com.hub.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 标记"这是主城菜单 GUI"。
 * <p>
 * 用 holder 而不是标题来识别菜单：OP 可以随便改 menu.title，
 * 标题比对（旧写法）在标题非法/为空时会误判，holder 不会。
 */
public class MenuHolder implements InventoryHolder {

    private final MenuLayout.Place place;
    private final String game;
    private Inventory inventory;

    public MenuHolder(MenuLayout.Place place, String game) {
        this.place = place;
        this.game = game;
    }

    public MenuLayout.Place place() {
        return place;
    }

    /** 子菜单所属游戏；主菜单为 null。 */
    public String game() {
        return game;
    }

    /** 子菜单里的槽位由场地在 config 里的键决定，命令里没写槽位时用不上，统一返回 -1。 */
    public int slot() {
        return -1;
    }

    /** Bukkit 要求 holder 能取回 inventory（用 setInventory 由 MenuLayout 回填）。 */
    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
