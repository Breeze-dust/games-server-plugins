package com.liarbar;

import com.hub.util.Chat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** 本插件的全部自定义物品（手牌、桌面牌、质疑钟、离开酒馆）。 */
public final class LiarBarItems {

    private final NamespacedKey cardKey;
    private final NamespacedKey uiKey;
    private final NamespacedKey leaveKey;

    public LiarBarItems(Plugin plugin) {
        cardKey = new NamespacedKey(plugin, "card");
        uiKey = new NamespacedKey(plugin, "ui");
        leaveKey = new NamespacedKey(plugin, "leave");
    }

    /** 一张手牌；selected 时带附魔光效。 */
    public ItemStack card(LiarBarCards.Card card, boolean selected) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color(card.colored())));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Chat.noItalic(Chat.color("&7左键选中 / 右键打出")));
        lore.add(Chat.noItalic(Chat.color("&8真假由你说了算")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(cardKey, PersistentDataType.STRING, card.save());
        if (selected) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        item.setItemMeta(meta);
        return item;
    }

    public LiarBarCards.Card cardOf(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(cardKey, PersistentDataType.STRING);
        return LiarBarCards.Card.parse(raw);
    }

    /** 槽位 6 的装饰品：显示本回合桌面点数。 */
    public ItemStack tableCard(String rank) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&e本回合桌面牌：&f" + rank)));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Chat.noItalic(Chat.color("&7你打出的牌都要声称是它")));
        lore.add(Chat.noItalic(Chat.color("&7但没人知道是不是真的")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "table");
        item.setItemMeta(meta);
        return item;
    }

    /** 槽位 8 的质疑钟。 */
    public ItemStack bell() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&c质疑")));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Chat.noItalic(Chat.color("&7右键质疑上家最后打出的牌")));
        lore.add(Chat.noItalic(Chat.color("&8质疑错了，开枪的是你")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "bell");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBell(ItemStack item) {
        return "bell".equals(uiOf(item));
    }

    /** 在房间里时槽位 8 的离场道具。 */
    public ItemStack leave() {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&c离开酒馆")));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Chat.noItalic(Chat.color("&7右键返回大厅")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(leaveKey, PersistentDataType.STRING, "leave");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLeave(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(leaveKey, PersistentDataType.STRING);
    }

    private String uiOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(uiKey, PersistentDataType.STRING);
    }
}
