package com.cloudwalk;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 选择难度的 27 格菜单。 */
public class DifficultyMenu implements InventoryHolder {

    private final Map<Integer, CloudWalkSettings.Difficulty> slots = new HashMap<>();
    private final Inventory inventory;

    public DifficultyMenu(CloudWalkManager manager, Player player) {
        this.inventory = Bukkit.createInventory(this, 27, Chat.color("&8云中漫步 · 选择难度"));

        List<CloudWalkSettings.Difficulty> difficulties = manager.difficulties();
        int[] positions = {11, 13, 15};
        for (int i = 0; i < difficulties.size() && i < positions.length; i++) {
            CloudWalkSettings.Difficulty difficulty = difficulties.get(i);
            slots.put(positions[i], difficulty);
            inventory.setItem(positions[i], icon(difficulty));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Chat.color(" "));
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    public CloudWalkSettings.Difficulty difficultyAt(int slot) {
        return slots.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private ItemStack icon(CloudWalkSettings.Difficulty difficulty) {
        Material material = switch (difficulty.id) {
            case "easy" -> Material.LIME_WOOL;
            case "medium" -> Material.YELLOW_WOOL;
            default -> Material.RED_WOOL;
        };
        if (!difficulty.enabled) {
            material = Material.BARRIER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(difficulty.color + "&l" + difficulty.name)));
            List<String> lore = !difficulty.enabled
                    ? List.of("&7尚未开放，敬请期待")
                    : switch (difficulty.id) {
                        case "easy" -> List.of(
                                "&7保持当前难度",
                                "&7不会出现「空 3 格 + 上升 1 格」",
                                "&a点击开始");
                        case "medium" -> List.of(
                                "&7在简单基础上加入「空 3 格 + 上升 1 格」",
                                "&a点击开始");
                        default -> List.of("&a点击开始");
                    };
            meta.lore(lore.stream().map(Chat::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
