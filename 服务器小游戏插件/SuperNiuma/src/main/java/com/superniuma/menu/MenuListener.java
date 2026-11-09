package com.superniuma.menu;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.game.GameRoom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** 菜单点击处理。 */
public final class MenuListener implements Listener {

    private final SuperNiumaPlugin plugin;

    public MenuListener(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        String action = holder.action(event.getRawSlot());
        if (action != null) {
            handle(player, action);
        }
    }

    private void handle(Player player, String action) {
        if (action.startsWith("create:")) {
            Arena arena = plugin.arenas().get(action.substring("create:".length()));
            if (arena != null) {
                CreateRoomMenu.open(plugin, player, arena);
            }
            return;
        }
        if (action.startsWith("join:")) {
            GameRoom room = plugin.rooms().byId(action.substring("join:".length()));
            if (room == null) {
                Msg.send(player, "&c这个房间已经结束或不存在了，请点「刷新」重新读取房间列表。");
                return;
            }
            if (plugin.rooms().roomOf(player) != null) {
                Msg.send(player, "&c你已经在房间里了。");
                return;
            }
            if (plugin.rooms().join(room.id(), player)) {
                player.closeInventory();
                Msg.send(player, "&a已加入房间 &e" + room.id() + "&a。");
            } else {
                Msg.send(player, "&c加入失败（房间已开始或已满）。");
            }
            return;
        }
        if (action.startsWith("select:")) {
            GameRoom room = plugin.rooms().roomOf(player);
            if (room != null) {
                room.selectMechanism(player, action.substring("select:".length()));
            }
            return;
        }
        switch (action) {
            case "refresh", "back" -> RoomMenu.open(plugin, player);
            case "mode" -> CreateRoomMenu.cycleMode(plugin, player);
            case "win:-" -> CreateRoomMenu.adjustWin(plugin, player, -1);
            case "win:+" -> CreateRoomMenu.adjustWin(plugin, player, 1);
            case "rounds:-" -> CreateRoomMenu.adjustRounds(plugin, player, -1);
            case "rounds:+" -> CreateRoomMenu.adjustRounds(plugin, player, 1);
            case "create" -> CreateRoomMenu.create(plugin, player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.type() != MenuHolder.Type.SELECT) {
            return;
        }
        GameRoom room = plugin.rooms().roomOf(player);
        if (room != null && room.phase() == GameRoom.Phase.SELECTING) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                GameRoom current = plugin.rooms().roomOf(player);
                if (current != null && current.phase() == GameRoom.Phase.SELECTING) {
                    SelectMechanismMenu.open(plugin, current, player);
                    Msg.send(player, "&c选择环节不能关闭界面。");
                }
            });
        }
    }
}
