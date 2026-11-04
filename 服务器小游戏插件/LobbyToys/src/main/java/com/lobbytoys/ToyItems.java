package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** 玩具道具的构造与识别，靠 PDC 标记而不是材质/名字。 */
public final class ToyItems {

    private final NamespacedKey key;

    public ToyItems(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "clone-toy");
    }

    public ItemStack create(Player owner) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setPlayerProfile(owner.getPlayerProfile());
            meta.displayName(Chat.color("&b分身玩具"));
            meta.lore(List.of(
                    Chat.color("&7右键 召唤 / 收回 你的分身"),
                    Chat.color("&8只能在主城使用")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isToy(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PLAYER_HEAD || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
