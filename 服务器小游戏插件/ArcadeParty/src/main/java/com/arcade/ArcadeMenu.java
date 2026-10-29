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
    private static final int SLOT_GAMES = 4;
    private static final int SLOT_MAX_PLAYERS = 12;
    private static final int SLOT_TRANSITION = 20;
    private static final int SLOT_CLOSE = 49;
    private static final int GAME_START = 27;

    private final ArcadePartyPlugin plugin;
    private final ArcadePartyManager manager;
    private Inventory inventory;

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

        ConfigurationSection games = plugin.getConfig().getConfigurationSection("games");
        int slot = GAME_START;
        if (games != null) {
            for (String key : games.getKeys(false)) {
                if (slot >= 54) {
                    break;
                }
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
                inventory.setItem(slot++, item);
            }
        }
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Chat.noItalic(Chat.color("&c关闭")));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(SLOT_CLOSE, close);
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
        ConfigurationSection games = plugin.getConfig().getConfigurationSection("games");
        if (games != null && slot >= GAME_START) {
            List<String> keys = new ArrayList<>(games.getKeys(false));
            int index = slot - GAME_START;
            if (index >= 0 && index < keys.size()) {
                manager.toggleGame(keys.get(index));
                refresh();
                return true;
            }
        }
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
