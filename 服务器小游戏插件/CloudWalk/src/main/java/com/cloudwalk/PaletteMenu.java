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
import java.util.UUID;

/** 方块选择菜单：列出配置里的方块池，点击切换是否在跑酷里出现。 */
public class PaletteMenu implements InventoryHolder {

    public static final int SLOT_ENABLE_ALL = 45;
    public static final int SLOT_CLOSE = 53;
    private static final int ITEM_LIMIT = 45;

    private final CloudWalkManager manager;
    private final UUID owner;
    private final List<Material> palette;
    private final Map<Integer, Material> slots = new HashMap<>();
    private final Inventory inventory;
    private boolean dirty;

    public PaletteMenu(CloudWalkManager manager, Player player) {
        this.manager = manager;
        this.owner = player.getUniqueId();
        this.palette = manager.palette();
        this.inventory = Bukkit.createInventory(this, 54, Chat.color("&8云中漫步 · 选择方块"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID owner() {
        return owner;
    }

    public boolean dirty() {
        return dirty;
    }

    public void render() {
        List<Material> enabled = manager.enabledBlocks(owner);
        inventory.clear();
        slots.clear();
        int slot = 0;
        for (Material material : palette) {
            if (slot >= ITEM_LIMIT) {
                break;
            }
            slots.put(slot, material);
            inventory.setItem(slot, paletteItem(material, enabled.contains(material)));
            slot++;
        }
        inventory.setItem(SLOT_ENABLE_ALL, button(Material.LIME_DYE, "&a全部启用", "&7点击启用方块池里的全部方块"));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "&c关闭并保存", "&7点击关闭菜单"));
    }

    /** @return true 表示要关闭菜单 */
    public boolean onClick(int slot) {
        Material material = slots.get(slot);
        if (material != null) {
            manager.toggleBlock(owner, material);
            dirty = true;
            render();
            return false;
        }
        if (slot == SLOT_ENABLE_ALL) {
            manager.selectAllBlocks(owner);
            dirty = true;
            render();
            return false;
        }
        return slot == SLOT_CLOSE;
    }

    private ItemStack paletteItem(Material material, boolean enabled) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.lore(List.of(
                    Chat.color(enabled ? "&a✔ 已启用" : "&8✘ 未启用"),
                    Chat.color("&7点击切换")));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(name)));
            meta.lore(List.of(Chat.color(lore)));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
