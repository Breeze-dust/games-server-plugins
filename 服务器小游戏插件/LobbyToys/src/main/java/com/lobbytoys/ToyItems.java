package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** 玩具道具与菜单图标的构造/识别，全部靠 PDC 标记，不认材质和名字。 */
public final class ToyItems {

    private final NamespacedKey toyKey;
    private final NamespacedKey iconKey;
    private final NamespacedKey selectedKey;

    public ToyItems(Plugin plugin) {
        this.toyKey = new NamespacedKey(plugin, "toy-id");
        this.iconKey = new NamespacedKey(plugin, "menu-icon");
        this.selectedKey = new NamespacedKey(plugin, "selected-toy");
    }

    /** 物品栏里那个可右键的玩具。 */
    public ItemStack create(Player owner, ToyType type) {
        ItemStack stack = base(owner, type);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.lore(chatList(type.lore()));
            meta.getPersistentDataContainer().set(toyKey, PersistentDataType.STRING, type.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 菜单里的图标，带「已装备 / 点击切换」，不带玩具标记（不能被当成真玩具带走）。 */
    public ItemStack createIcon(Player viewer, ToyType type, boolean equipped) {
        ItemStack stack = base(viewer, type);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(type.lore());
            lore.add("");
            lore.add(equipped ? "&a✔ 已装备" : "&e点击切换到这个小玩具");
            meta.lore(chatList(lore));
            meta.getPersistentDataContainer().set(iconKey, PersistentDataType.STRING, type.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack base(Player owner, ToyType type) {
        ItemStack stack = new ItemStack(type.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta instanceof SkullMeta skull) {
                skull.setPlayerProfile(owner.getPlayerProfile());
            }
            meta.displayName(Chat.color("&b" + type.displayName()));
        }
        return stack;
    }

    private static List<net.kyori.adventure.text.Component> chatList(List<String> lines) {
        List<net.kyori.adventure.text.Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(Chat.color(line));
        }
        return result;
    }

    public ToyType typeOf(ItemStack stack) {
        return readId(stack, toyKey);
    }

    public ToyType iconOf(ItemStack stack) {
        return readId(stack, iconKey);
    }

    public boolean isToy(ItemStack stack) {
        return typeOf(stack) != null;
    }

    private ToyType readId(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return ToyType.byId(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    /** 玩家上次选中的玩具，存在玩家 PDC 里，重启/换号后仍然记得。 */
    public ToyType selected(Player player) {
        ToyType type = ToyType.byId(
                player.getPersistentDataContainer().get(selectedKey, PersistentDataType.STRING));
        return type == null ? ToyType.CLONE : type;
    }

    /** 选中某个玩具：改玩家 PDC，并把物品栏里的玩具（没有就新发一个）换成它。 */
    public void equip(Player player, ToyType type, int preferredSlot) {
        player.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, type.id());

        PlayerInventory inventory = player.getInventory();
        ItemStack toy = create(player, type);

        if (isToy(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(toy);
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isToy(inventory.getItem(slot))) {
                inventory.setItem(slot, toy);
                return;
            }
        }
        give(inventory, toy, preferredSlot);
    }

    /** 没有玩具时给一个：优先放 preferredSlot，被占就找别的空位。 */
    public void give(PlayerInventory inventory, ItemStack toy, int preferredSlot) {
        int slot = Math.max(0, Math.min(8, preferredSlot));
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, toy);
        } else {
            inventory.addItem(toy);
        }
    }

    public boolean hasAny(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (isToy(inventory.getItemInOffHand())) {
            return true;
        }
        for (ItemStack stack : inventory.getContents()) {
            if (isToy(stack)) {
                return true;
            }
        }
        return false;
    }
}
