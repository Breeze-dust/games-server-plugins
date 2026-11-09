package com.superniuma.menu;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.game.GameRoom;
import com.superniuma.mechanism.MechanismChoice;
import com.superniuma.mechanism.MechanismDef;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 选择一个机关（20 秒，不可自己关闭）。 */
public final class SelectMechanismMenu {

    private static final Random RANDOM = new Random();

    private SelectMechanismMenu() {
    }

    public static void open(SuperNiumaPlugin plugin, GameRoom room, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SELECT);
        Inventory inventory = Bukkit.createInventory(holder, 27, Msg.c("&e选择机关 - 第 " + room.round() + " 回合"));
        holder.setInventory(inventory);
        fill(room, player, inventory, holder);
        player.openInventory(inventory);
    }

    public static void refresh(SuperNiumaPlugin plugin, GameRoom room, Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)
                || holder.type() != MenuHolder.Type.SELECT) {
            return;
        }
        Inventory inventory = holder.getInventory();
        inventory.clear();
        fill(room, player, inventory, holder);
        player.updateInventory();
    }

    private static void fill(GameRoom room, Player player, Inventory inventory, MenuHolder holder) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, RANDOM);

        int index = 0;
        for (MechanismChoice choice : room.selectionPool()) {
            if (index >= slots.size()) {
                break;
            }
            int slot = slots.get(index++);
            inventory.setItem(slot, item(room, player, choice));
            holder.action(slot, "select:" + choice.optionId());
        }
        for (; index < slots.size(); index++) {
            inventory.setItem(slots.get(index), RoomMenu.item(
                    Material.GRAY_STAINED_GLASS_PANE, "&8", List.of()));
        }
    }

    private static ItemStack item(GameRoom room, Player player, MechanismChoice choice) {
        MechanismDef def = choice.def();
        Material icon = def.icon();
        if ("platform".equals(def.id())) {
            icon = room.woolFor(player.getUniqueId());
        }
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = def.displayName();
        if (choice.variant() != null && !choice.variant().id().equals("default")) {
            name = name + " &7(" + choice.variant().name() + "&7)";
        }
        meta.setDisplayName(Msg.c(name));

        List<String> lore = new ArrayList<>();
        for (String line : def.lore()) {
            lore.add(Msg.c(line));
        }
        lore.add(Msg.c("&8----------------"));
        UUID owner = room.optionOwner(choice.optionId());
        if (owner == null) {
            lore.add(Msg.c("&a点击选择这个机关"));
        } else if (owner.equals(player.getUniqueId())) {
            lore.add(Msg.c("&a你已选择这个机关"));
        } else {
            String ownerName = room.selectionStatus(choice.optionId());
            lore.add(Msg.c("&c该机关已被【" + ownerName + "】选择"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
