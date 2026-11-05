package com.arcade;

import com.hub.util.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 管理员的派对设置菜单（在 Hub 菜单里用 arcade menu 打开）。 */
public final class ArcadeMenu implements InventoryHolder {

    public static final String TITLE = "街机派对设置";

    /** 第 1 行：三个数值设置 + 关闭。 */
    private static final int SLOT_GAMES = 0;
    private static final int SLOT_MAX_PLAYERS = 2;
    private static final int SLOT_TRANSITION = 4;
    private static final int SLOT_CLOSE = 8;
    /** 第 2~5 行：小游戏网格，每页 GAME_END - GAME_START 个。 */
    private static final int GAME_START = 9;
    private static final int GAME_END = 45;
    private static final int PAGE_SIZE = GAME_END - GAME_START;
    /** 第 6 行：翻页。 */
    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;

    private final ArcadePartyPlugin plugin;
    private final ArcadePartyManager manager;
    private Inventory inventory;
    private int page;

    public ArcadeMenu(ArcadePartyPlugin plugin, ArcadePartyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player) {
        inventory = Bukkit.createInventory(this, 54, Chat.color(TITLE));
        refresh();
        player.openInventory(inventory);
    }

    public void refresh() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        inventory.setItem(SLOT_GAMES, setting(Material.BOOK, "&e每局小游戏个数",
                manager.gamesPerParty(), "左键 +1 / 右键 -1"));
        inventory.setItem(SLOT_MAX_PLAYERS, setting(Material.PLAYER_HEAD, "&e派对最多玩家",
                manager.maxPlayers(), "左键 +1 / 右键 -1"));
        inventory.setItem(SLOT_TRANSITION, setting(Material.CLOCK, "&e过渡准备时间",
                manager.transitionSeconds(), "单位：秒，左键 +1 / 右键 -1"));

        List<String> keys = gameKeys();
        int pages = pageCount(keys.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < keys.size(); i++) {
            inventory.setItem(GAME_START + i, gameItem(keys.get(from + i)));
        }

        inventory.setItem(SLOT_PREV, navItem(page > 0, "← 上一页",
                page > 0 ? "翻到第 " + page + " 页" : "已经是第一页"));
        inventory.setItem(SLOT_NEXT, navItem(page < pages - 1, "下一页 →",
                page < pages - 1 ? "翻到第 " + (page + 2) + " 页" : "已经是最后一页"));
        inventory.setItem(SLOT_PAGE, pageItem(page, pages, keys.size()));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Chat.noItalic(Chat.color("&c关闭")));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(SLOT_CLOSE, close);
    }

    private ItemStack gameItem(String key) {
        boolean enabled = manager.gameEnabled(key);
        String display = manager.displayOf(key);
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color((enabled ? "&a" : "&7") + display)));
            List<Component> lore = new ArrayList<>();
            lore.add(Chat.noItalic(Chat.color("&7标识：&f" + key)));
            lore.add(Chat.noItalic(Chat.color("&7房间上限：&f" + manager.gameMaxPlayers(key) + " 人")));
            lore.add(Chat.noItalic(Chat.color("&7加入命令：&f"
                    + plugin.getConfig().getString("games." + key + ".join-command", ""))));
            lore.add(Chat.noItalic(Chat.color("&7开始命令：&f"
                    + plugin.getConfig().getString("games." + key + ".start-command", ""))));
            lore.add(Chat.noItalic(Chat.color(enabled ? "&a已启用" : "&c已禁用")));
            lore.add(Chat.noItalic(Chat.color("&e点击切换启用/禁用")));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navItem(boolean enabled, String label, String hint) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color((enabled ? "&e" : "&8") + label)));
            List<Component> lore = new ArrayList<>();
            lore.add(Chat.noItalic(Chat.color("&7" + hint)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageItem(int page, int pages, int total) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(
                    "&e第 &f" + (page + 1) + " &7/ &f" + pages + " &e页")));
            List<Component> lore = new ArrayList<>();
            lore.add(Chat.noItalic(Chat.color("&7共 &f" + total + " &7个小游戏，每页 &f"
                    + PAGE_SIZE + " &7个")));
            lore.add(Chat.noItalic(Chat.color("&7用底部的箭头翻页")));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack setting(Material material, String name, int value, String hint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(name + " &f" + value)));
            List<Component> lore = new ArrayList<>();
            lore.add(Chat.noItalic(Chat.color("&7" + hint)));
            lore.add(Chat.noItalic(Chat.color("&7当前：&e" + value)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> gameKeys() {
        ConfigurationSection games = plugin.getConfig().getConfigurationSection("games");
        if (games == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(games.getKeys(false));
    }

    private static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** 处理点击；返回 true 表示点击已被消费。 */
    public boolean handleClick(Player player, int slot, boolean rightClick, boolean shift) {
        int step = (shift ? 5 : 1) * (rightClick ? -1 : 1);
        if (slot == SLOT_GAMES) {
            manager.setGamesPerParty(manager.gamesPerParty() + step);
            refresh();
            return true;
        }
        if (slot == SLOT_MAX_PLAYERS) {
            manager.setMaxPlayers(manager.maxPlayers() + step);
            refresh();
            return true;
        }
        if (slot == SLOT_TRANSITION) {
            manager.setTransitionSeconds(manager.transitionSeconds() + step);
            refresh();
            return true;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return true;
        }
        if (slot == SLOT_PREV || slot == SLOT_NEXT) {
            int target = page + (slot == SLOT_PREV ? -1 : 1);
            if (target >= 0 && target < pageCount(gameKeys().size())) {
                page = target;
                refresh();
            }
            return true;
        }
        if (slot >= GAME_START && slot < GAME_END) {
            List<String> keys = gameKeys();
            int index = page * PAGE_SIZE + (slot - GAME_START);
            if (index < keys.size()) {
                manager.toggleGame(keys.get(index));
                refresh();
            }
            return true;
        }
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
