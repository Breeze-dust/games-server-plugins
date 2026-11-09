package com.superniuma.menu;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.game.GameRoom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 主界面：选地图创建房间 + 加入已有房间。 */
public final class RoomMenu {

    private RoomMenu() {
    }

    public static void open(SuperNiumaPlugin plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, 54, Msg.c("&e超级牛马 - 房间"));
        holder.setInventory(inventory);

        List<Arena> arenas = plugin.arenas().all();
        int slot = 0;
        for (Arena arena : arenas) {
            if (slot >= 9) {
                break;
            }
            List<String> lore = new ArrayList<>(Arrays.asList(
                    Msg.c("&7模式：个人 / 团队"),
                    Msg.c("&7目标分数、最高回合可调"),
                    Msg.c(arena.isReady() ? "&a点击创建房间" : "&c地图尚未配置完整")));
            inventory.setItem(slot, item(Material.GRASS_BLOCK, "&e" + arena.getName(), lore));
            holder.action(slot, "create:" + arena.getName());
            slot++;
        }
        if (arenas.isEmpty()) {
            inventory.setItem(4, item(Material.BARRIER, "&c还没有地图", Arrays.asList(
                    Msg.c("&7先用命令创建并配置一张地图："),
                    Msg.c("&f/sn arena create <名字>"),
                    Msg.c("&f/sn arena setspawn <名字>"),
                    Msg.c("&f/sn arena setstart <名字>"),
                    Msg.c("&f/sn arena setpos1 <名字>"),
                    Msg.c("&f/sn arena setpos2 <名字>"),
                    Msg.c("&f/sn arena setfinish1 <名字>"),
                    Msg.c("&f/sn arena setfinish2 <名字>"))));
        }

        int roomSlot = 27;
        for (GameRoom room : plugin.rooms().all()) {
            if (roomSlot >= 36) {
                break;
            }
            List<String> lore = Arrays.asList(
                    Msg.c("&7地图：&f" + room.arena().getName()),
                    Msg.c("&7模式：&f" + room.modeText()),
                    Msg.c("&7状态：&f" + room.statusText()),
                    Msg.c("&7人数：&f" + room.size() + "/" + room.maxPlayers()),
                    Msg.c("&a点击加入"));
            inventory.setItem(roomSlot, item(Material.OAK_DOOR,
                    "&a房间 " + room.id(), lore));
            holder.action(roomSlot, "join:" + room.id());
            roomSlot++;
        }
        if (plugin.rooms().all().isEmpty()) {
            inventory.setItem(31, item(Material.GRAY_STAINED_GLASS_PANE, "&7当前没有房间", List.of()));
        }

        inventory.setItem(49, item(Material.COMPASS, "&e刷新", List.of(Msg.c("&7重新读取房间列表"))));
        holder.action(49, "refresh");
        player.openInventory(inventory);
    }

    static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.c(name));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
