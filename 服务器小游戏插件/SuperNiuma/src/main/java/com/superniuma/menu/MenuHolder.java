package com.superniuma.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** 菜单容器的标记与槽位动作。 */
public final class MenuHolder implements InventoryHolder {

    public enum Type {
        MAIN, CREATE, SELECT
    }

    private final Type type;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void action(int slot, String action) {
        actions.put(slot, action);
    }

    public String action(int slot) {
        return actions.get(slot);
    }
}
