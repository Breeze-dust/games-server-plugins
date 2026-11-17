package com.liarbar;

import com.hub.util.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** 本插件的自定义物品：手牌、目标牌提示、质疑钟、左轮、问号。 */
public final class LiarBarItems {

    private final NamespacedKey cardKey;
    private final NamespacedKey uiKey;

    public LiarBarItems(Plugin plugin) {
        cardKey = new NamespacedKey(plugin, "card");
        uiKey = new NamespacedKey(plugin, "ui");
    }

    private static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(Chat.noItalic(Chat.color(line)));
        }
        return out;
    }

    /** 一张手牌：直接就是钻石 / 绿宝石 / 金锭 / 下界之星。 */
    public ItemStack card(LiarBarCards.Card card, boolean selected) {
        ItemStack item = new ItemStack(card.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color(card.display())));
        if (card.rank() == LiarBarCards.WILDCARD) {
            meta.lore(lore("&7可以当任何牌用", "&8结算时算作目标牌"));
        } else {
            meta.lore(lore("&7左键选中 / 右键打出"));
        }
        meta.getPersistentDataContainer().set(cardKey, PersistentDataType.INTEGER, card.rank());
        if (selected) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        item.setItemMeta(meta);
        return item;
    }

    public LiarBarCards.Card cardOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        Integer rank = meta.getPersistentDataContainer().get(cardKey, PersistentDataType.INTEGER);
        if (rank == null || rank < 0 || rank >= LiarBarCards.MATERIALS.length) {
            return null;
        }
        return new LiarBarCards.Card(rank);
    }

    /** 槽位 6：本回合目标牌提示（就是那张牌本身，带说明）。 */
    public ItemStack tableCard(int rank) {
        ItemStack item = new ItemStack(LiarBarCards.MATERIALS[rank]);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&e目标牌：&f" + LiarBarCards.NAMES[rank])));
        meta.lore(lore("&7你打出的牌都要声称是它", "&7但没人知道是不是真的"));
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "table");
        item.setItemMeta(meta);
        return item;
    }

    /** 槽位 8：质疑钟。 */
    public ItemStack bell() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&c质疑")));
        meta.lore(lore("&7右键质疑上家最后打出的牌", "&8质疑错了，开枪的是你"));
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "bell");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBell(ItemStack item) {
        return "bell".equals(uiOf(item));
    }

    /** 左轮：铁锄头，右键开枪。 */
    public ItemStack revolver() {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&4&l左轮")));
        meta.lore(lore("&7右键：扣动扳机", "&8六格子弹，谁也说不准"));
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "revolver");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRevolver(ItemStack item) {
        return "revolver".equals(uiOf(item));
    }

    /** 揭幕时展示框里的问号。 */
    public ItemStack question() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&7？")));
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "question");
        item.setItemMeta(meta);
        return item;
    }

    /** 揭幕时展示框里的牌面（不带交互说明）。 */
    public ItemStack face(LiarBarCards.Card card) {
        ItemStack item = new ItemStack(card.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color(card.display())));
        item.setItemMeta(meta);
        return item;
    }

    private String uiOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(uiKey, PersistentDataType.STRING);
    }
}
