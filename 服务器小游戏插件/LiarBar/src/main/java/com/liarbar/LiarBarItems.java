package com.liarbar;

import com.hub.util.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** 本插件的自定义物品：手牌、出牌/质疑道具、目标牌提示、菜单、问号。 */
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
        List<String> lines = new ArrayList<>();
        if (card.rank() == LiarBarCards.WILDCARD) {
            lines.add("&7可以当任何牌用");
            lines.add("&8结算时算作目标牌");
        } else {
            lines.add("&7在出牌菜单里点击选择 / 取消");
        }
        if (selected) {
            lines.add("&a&l已选中");
            lines.add("&8再点一次取消选择");
        }
        meta.lore(lore(lines.toArray(new String[0])));
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

    /** 槽位 1（index 0）：出牌道具，右键打开选牌菜单。 */
    public ItemStack playItem(int rank, int remaining) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&a&l出牌")));
        meta.lore(lore(
                "&7右键打开选牌菜单",
                "&7本轮目标：&f" + LiarBarCards.NAMES[rank],
                "&7手中还有 &f" + remaining + " &7张牌",
                "&8第 5~9 格是手牌，别切过去"));
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "play");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPlay(ItemStack item) {
        return "play".equals(uiOf(item));
    }

    /** 槽位 2（index 1）：质疑道具。 */
    public ItemStack challenge() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&c质疑")));
        meta.lore(lore("&7右键质疑上家最后打出的牌", "&8质疑错了，开枪的是你"));
        meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "challenge");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isChallenge(ItemStack item) {
        return "challenge".equals(uiOf(item));
    }

    /** 出牌菜单里的「确认出牌」按钮。 */
    public ItemStack confirm(List<LiarBarCards.Card> selected, int max) {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Chat.noItalic(Chat.color("&a&l确认出牌")));
        List<String> lines = new ArrayList<>();
        lines.add("&7已选 &f" + selected.size() + "&7/&f" + max + " &7张");
        for (LiarBarCards.Card card : selected) {
            lines.add("&8· &f" + card.display());
        }
        lines.add("&7点击打出，并声称是目标牌");
        meta.lore(lore(lines.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    /** 菜单空位的灰色玻璃板。 */
    public ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 菜单里的本轮目标牌提示（只展示，不可点击）。 */
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

    /** 一瓶随机酒：普通药水外观（不会触发原版药水效果）。 */
    public ItemStack liquor(String displayName, int rgb) {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof PotionMeta potion) {
            potion.setColor(org.bukkit.Color.fromRGB(rgb & 0xFFFFFF));
            potion.setBasePotionType(org.bukkit.potion.PotionType.MUNDANE);
            potion.displayName(Chat.noItalic(Chat.color(displayName)));
            potion.lore(lore("&7右键饮用", "&8喝完会留下一个空酒瓶"));
            potion.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "liquor");
            item.setItemMeta(potion);
        }
        return item;
    }

    public boolean isLiquor(ItemStack item) {
        return "liquor".equals(uiOf(item));
    }

    /** 喝完酒剩下的空瓶：右键可以扔出去砸人。 */
    public ItemStack emptyBottle() {
        ItemStack item = new ItemStack(Material.GLASS_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&f空酒瓶")));
            meta.lore(lore("&7右键投掷出去砸人", "&8坐着的人不会被砸动"));
            meta.getPersistentDataContainer().set(uiKey, PersistentDataType.STRING, "bottle");
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isBottle(ItemStack item) {
        return "bottle".equals(uiOf(item));
    }

    /** 对局中的牌桌物品（手牌 / 出牌 / 质疑 / 目标提示）：禁止在背包里移动。 */
    public boolean isGameItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (cardOf(item) != null) {
            return true;
        }
        String ui = uiOf(item);
        return "play".equals(ui) || "challenge".equals(ui) || "table".equals(ui);
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

    /** 桌面盖牌：没有名字的普通纸。 */
    public ItemStack blankPaper() {
        return new ItemStack(Material.PAPER);
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
