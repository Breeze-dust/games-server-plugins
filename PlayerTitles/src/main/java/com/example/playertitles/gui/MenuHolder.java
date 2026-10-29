package com.example.playertitles.gui;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    public enum Type {
        TITLES, ADMIN, EDIT, COLOR
    }

    private final Type type;
    private final String titleId;
    private final int page;
    private final List<String> entryIds = new ArrayList<>();
    private Inventory inventory;

    public MenuHolder(Type type, String titleId, int page) {
        this.type = type;
        this.titleId = titleId;
        this.page = page;
    }

    public Type getType() {
        return type;
    }

    public String getTitleId() {
        return titleId;
    }

    public int getPage() {
        return page;
    }

    public List<String> getEntryIds() {
        return entryIds;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
