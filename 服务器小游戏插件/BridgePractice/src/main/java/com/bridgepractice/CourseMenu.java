package com.bridgepractice;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /bridge 打开的房间选择菜单。 */
public final class CourseMenu implements InventoryHolder, Listener {

    private final BridgePracticeManager manager;
    private Inventory inventory;

    public CourseMenu(BridgePracticeManager manager) {
        this.manager = manager;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        List<PracticeCourse> courses = manager.courses();
        int size = Math.max(18, Math.min(54, ((courses.size() + 1 + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(this, size, Chat.color("&8搭路练习 · 选择房间"));
        int slot = 0;
        for (PracticeCourse course : courses) {
            if (slot >= size - 1) {
                break;
            }
            inv.setItem(slot++, courseItem(course));
        }
        inv.setItem(size - 1, closeItem());
        this.inventory = inv;
        player.openInventory(inv);
    }

    private ItemStack courseItem(PracticeCourse course) {
        Material icon;
        if (!course.ready()) {
            icon = Material.BARRIER;
        } else {
            icon = course.icon();
        }
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Chat.noItalic(Chat.color("&b" + course.name())));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (!course.ready()) {
            lore.add(line("&c起点或终点还没设置"));
            lore.add(line("&7请管理员用 setstart / setend 设置"));
        } else {
            int players = manager.playerCount(course.name());
            if (players > 0) {
                lore.add(line("&a" + players + " 人在练 &8| &7房主 " + manager.ownerName(course.name())));
                lore.add(line(manager.allowsJoin(course.name())
                        ? "&a开放加入" : "&c已关闭加入"));
                lore.add(line("&7挖别人方块："
                        + (manager.allowsBreakOthers(course.name()) ? "&a允许" : "&c禁止")));
            } else {
                lore.add(line("&a空闲，可进入"));
            }
        }
        lore.add(line("&7最快纪录："));
        List<BridgePracticeManager.TopEntry> top = manager.topEntries(course.name());
        if (top.isEmpty()) {
            lore.add(line(" &8暂无"));
        } else {
            int shown = Math.min(3, top.size());
            for (int index = 0; index < shown; index++) {
                BridgePracticeManager.TopEntry entry = top.get(index);
                lore.add(line(" &7#" + (index + 1) + " &f" + entry.name() + " &a"
                        + BridgePracticeManager.formatTime(entry.millis())));
            }
        }
        lore.add(line(""));
        lore.add(line("&e点击进入"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack closeItem() {
        ItemStack stack = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&c关闭")));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private net.kyori.adventure.text.Component line(String text) {
        return Chat.noItalic(Chat.color(text));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CourseMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        if (slot == top.getSize() - 1) {
            player.closeInventory();
            return;
        }
        List<PracticeCourse> courses = manager.courses();
        if (slot >= courses.size()) {
            return;
        }
        player.closeInventory();
        manager.tryJoin(player, courses.get(slot).name());
    }
}
