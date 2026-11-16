package com.archery;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;

/** 进游戏前的物品栏快照，结束时原样还原（与 SkyPit / CloudWalk 相同）。 */
final class SavedInventory {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;

    SavedInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] all = inventory.getContents();
        if (all.length == 41) {
            contents = Arrays.copyOfRange(all, 0, 36);
            armor = Arrays.copyOfRange(all, 36, 40);
            offhand = all[40];
        } else {
            contents = all.clone();
            armor = inventory.getArmorContents().clone();
            offhand = inventory.getItemInOffHand();
        }
    }

    void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                inventory.setItem(i, contents[i].clone());
            }
        }
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(offhand == null ? null : offhand.clone());
    }
}
