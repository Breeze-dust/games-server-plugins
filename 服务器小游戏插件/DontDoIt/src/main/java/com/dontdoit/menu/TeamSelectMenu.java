package com.dontdoit.menu;

import com.dontdoit.DontDoItManager;
import com.dontdoit.DontDoItPlugin;
import com.dontdoit.Game;
import com.dontdoit.Team;
import com.dontdoit.TeamColor;
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

/** 等待室的选队菜单：所有队伍颜色一屏展示，点一下就能换队。 */
public final class TeamSelectMenu {

    public static final String TITLE = "&8选队菜单";

    private TeamSelectMenu() {
    }

    public static ItemStack item(DontDoItManager manager, Game game, Player player) {
        ItemStack stack = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Team team = game.teamOf(player);
            meta.displayName(Chat.noItalic(Chat.color("&e&l选队菜单 &7(右键)")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Chat.color("&7当前队伍：" + (team == null ? "&8未选择" : team.displayName())));
            lore.add(Chat.color("&7点击打开，点队伍图标即可加入"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static void open(DontDoItPlugin plugin, DontDoItManager manager, Game game, Player player) {
        List<Team> teams = game.teams();
        int rows = Math.max(2, (teams.size() + 1 + 8) / 9);
        rows = Math.min(6, rows);
        Inventory inv = Bukkit.createInventory(null, rows * 9, Chat.color(TITLE));
        List<Team> shown = teams.subList(0, Math.min(teams.size(), rows * 9 - 1));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Chat.noItalic(Chat.color(" ")));
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        Team mine = game.teamOf(player);
        for (int i = 0; i < shown.size(); i++) {
            Team team = shown.get(i);
            TeamColor color = team.getColor();
            ItemStack icon = new ItemStack(color.icon);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(Chat.noItalic(Chat.color(color.coloredName()
                        + (team == mine ? " &a（当前）" : ""))));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Chat.color("&7人数：&f" + team.size() + "&7/&f" + manager.maxPlayersPerTeam()));
                if (!team.isEmpty()) {
                    StringBuilder names = new StringBuilder("&f");
                    for (Player p : team.members()) {
                        if (names.length() > 2) {
                            names.append("&7, &f");
                        }
                        names.append(p.getName());
                    }
                    lore.add(Chat.color("&7成员：" + names));
                }
                lore.add(Chat.color(team == mine ? "&a你已在这支队伍" : "&e点击加入"));
                meta.lore(lore);
                if (team == mine) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                icon.setItemMeta(meta);
            }
            inv.setItem(i, icon);
        }

        ItemStack leave = new ItemStack(Material.BARRIER);
        ItemMeta leaveMeta = leave.getItemMeta();
        if (leaveMeta != null) {
            leaveMeta.displayName(Chat.noItalic(Chat.color("&c退出当前队伍")));
            leaveMeta.lore(List.of(Chat.color("&7仍然留在等待室，重新选队即可")));
            leave.setItemMeta(leaveMeta);
        }
        inv.setItem(shown.size(), leave);

        player.openInventory(inv);
    }

    /** @return 是否处理了这次点击 */
    public static boolean handleClick(DontDoItManager manager, Player player, InventoryClickEvent event) {
        if (!(event.getView().title().equals(Chat.color(TITLE)))) {
            return false;
        }
        event.setCancelled(true);
        Game game = manager.gameOf(player);
        if (game == null || game.state() != Game.State.WAITING) {
            player.closeInventory();
            return true;
        }
        int slot = event.getRawSlot();
        List<Team> shown = game.teams().subList(0, Math.min(game.teams().size(), 53));
        if (slot >= 0 && slot < shown.size()) {
            game.joinTeam(player, shown.get(slot).getColor());
            player.closeInventory();
        } else if (slot == shown.size()) {
            Team team = game.teamOf(player);
            if (team != null) {
                team.remove(player);
                player.closeInventory();
                Chat.send(player, "&7你退出了 " + team.displayName());
                manager.refreshWaitItems(player);
            }
        }
        return true;
    }
}
