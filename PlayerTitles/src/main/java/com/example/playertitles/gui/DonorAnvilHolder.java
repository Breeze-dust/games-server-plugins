package com.example.playertitles.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DonorAnvilHolder implements InventoryHolder {

    private final UUID playerId;
    private final String titleId;
    private final String originalName;
    private String confirmText;
    private Inventory inventory;

    public DonorAnvilHolder(UUID playerId, String titleId, String originalName) {
        this.playerId = playerId;
        this.titleId = titleId;
        this.originalName = originalName;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getTitleId() {
        return titleId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getConfirmText() {
        return confirmText;
    }

    public void setConfirmText(String confirmText) {
        this.confirmText = confirmText;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
