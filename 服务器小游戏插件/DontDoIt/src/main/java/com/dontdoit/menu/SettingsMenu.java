package com.dontdoit.menu;

import com.dontdoit.DontDoItManager;
import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 游戏内设置菜单：玩法开关点一下立即生效，并写回 config.yml。 */
public final class SettingsMenu {

    public static final String TITLE = "&8不要做挑战 · 设置";

    private final DontDoItManager manager;

    public SettingsMenu(DontDoItManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Chat.color(TITLE));
        fill(inv);
        inv.setItem(10, toggle(Material.WRITABLE_BOOK, "&d自定义词条",
                manager.customCriteriaEnabled(),
                "&7每队可以用书与笔写一条自定义词条",
                "&7别人看到你做了可以手动触发"));
        inv.setItem(11, toggle(Material.NAME_TAG, "&e猜词条",
                manager.guessCriteriaEnabled(),
                "&7玩家可以猜其他队伍的词条",
                "&7猜对 +" + manager.guessSuccessLives() + "，猜错 -" + manager.guessFailedLives() + "，冷却 "
                        + manager.guessCooldownSeconds() + " 秒"));
        inv.setItem(12, toggle(Material.DIAMOND, "&b钻石回血",
                manager.diamondBehaviorEnabled(),
                "&7挖到钻石时生效"));
        inv.setItem(13, toggle(Material.EMERALD, "&a钻石行为："
                        + ("add_self_life".equalsIgnoreCase(manager.diamondBehavior()) ? "自己 +1" : "其他队 -1"),
                "add_self_life".equalsIgnoreCase(manager.diamondBehavior()),
                "&7点一下切换：其他队 -1 ↔ 自己 +1"));
        inv.setItem(14, toggle(Material.POTION, "&5无限夜视", manager.nightVision(),
                "&7对局期间给所有玩家夜视"));
        inv.setItem(15, toggle(Material.BARRIER, "&7世界边界", manager.useWorldBorder(),
                "&7开局把世界边界缩到场地范围",
                "&7重置时还原"));
        inv.setItem(16, toggle(Material.ARROW, "&fY 高度词条", manager.yLevelCriteriaEnabled(),
                "&7加入「位置高于高处 / 低于地面」这类词条"));
        ItemStack close = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = close.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&c关闭")));
            close.setItemMeta(meta);
        }
        inv.setItem(26, close);
        player.openInventory(inv);
    }

    private ItemStack toggle(Material material, String name, boolean enabled, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(name + (enabled ? " &a[开]" : " &c[关]"))));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Chat.color(line));
            }
            lore.add(Chat.color(enabled ? "&a点击关闭" : "&c点击开启"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fill(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(" ")));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    public boolean handleClick(Player player, InventoryClickEvent event) {
        if (!event.getView().title().equals(Chat.color(TITLE))) {
            return false;
        }
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case 10 -> manager.setCustomCriteriaEnabled(!manager.customCriteriaEnabled());
            case 11 -> manager.setGuessCriteriaEnabled(!manager.guessCriteriaEnabled());
            case 12 -> manager.setDiamondBehaviorEnabled(!manager.diamondBehaviorEnabled());
            case 13 -> manager.setDiamondBehavior("add_self_life".equalsIgnoreCase(manager.diamondBehavior())
                    ? "reduce_others_life" : "add_self_life");
            case 14 -> manager.setNightVision(!manager.nightVision());
            case 15 -> manager.setUseWorldBorder(!manager.useWorldBorder());
            case 16 -> manager.setYLevelCriteriaEnabled(!manager.yLevelCriteriaEnabled());
            case 26 -> {
                player.closeInventory();
                return true;
            }
            default -> {
                return true;
            }
        }
        open(player);
        return true;
    }
}
