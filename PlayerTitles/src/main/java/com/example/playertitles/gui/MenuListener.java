package com.example.playertitles.gui;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {

    private final PlayerTitlesPlugin plugin;

    public MenuListener(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        switch (holder.getType()) {
            case TITLES -> handleTitles(player, holder, slot);
            case ADMIN -> handleAdmin(player, holder, slot);
            case EDIT -> handleEdit(player, holder, slot, event.isShiftClick());
            case COLOR -> handleColor(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleTitles(Player player, MenuHolder holder, int slot) {
        if (slot >= 0 && slot < TitleMenu.PER_PAGE) {
            if (slot >= holder.getEntryIds().size()) {
                return;
            }
            String id = holder.getEntryIds().get(slot);
            Title title = plugin.getTitleManager().getTitle(id);
            if (title == null) {
                TitleMenu.open(plugin, player, holder.getPage());
                return;
            }
            boolean owned = plugin.getTitleManager().owns(player.getUniqueId(), id);
            if (!owned) {
                if (!player.hasPermission("playertitles.admin")) {
                    player.sendMessage(Component.text("你还没有这个头衔", NamedTextColor.RED));
                    return;
                }
                plugin.getTitleManager().grant(player.getUniqueId(), id);
            }
            plugin.getTitleManager().setPlayerTitle(player.getUniqueId(), id);
            player.sendMessage(Component.text("已选择头衔: ", NamedTextColor.GREEN)
                    .append(title.bracketedComponent()));
            TitleMenu.open(plugin, player, holder.getPage());
            return;
        }
        switch (slot) {
            case 45 -> TitleMenu.open(plugin, player, holder.getPage() - 1);
            case 49 -> {
                plugin.getTitleManager().setPlayerTitle(player.getUniqueId(), null);
                player.sendMessage(Component.text("已清除当前头衔", NamedTextColor.YELLOW));
                TitleMenu.open(plugin, player, holder.getPage());
            }
            case 53 -> TitleMenu.open(plugin, player, holder.getPage() + 1);
            default -> {
            }
        }
    }

    private void handleAdmin(Player player, MenuHolder holder, int slot) {
        if (slot >= 0 && slot < TitleMenu.PER_PAGE) {
            if (slot < holder.getEntryIds().size()) {
                TitleEditMenu.open(plugin, player, holder.getEntryIds().get(slot));
            }
            return;
        }
        switch (slot) {
            case 45 -> AdminMenu.open(plugin, player, holder.getPage() - 1);
            case 48 -> player.closeInventory();
            case 49 -> AdminMenu.promptCreate(plugin, player);
            case 53 -> AdminMenu.open(plugin, player, holder.getPage() + 1);
            default -> {
            }
        }
    }

    private void handleEdit(Player player, MenuHolder holder, int slot, boolean shiftClick) {
        String titleId = holder.getTitleId();
        Title title = plugin.getTitleManager().getTitle(titleId);
        if (title == null) {
            AdminMenu.open(plugin, player, 0);
            return;
        }
        switch (slot) {
            case 10 -> TitleEditMenu.promptRename(plugin, player, titleId);
            case 12 -> ColorMenu.open(plugin, player, titleId);
            case 14 -> TitleEditMenu.promptDescription(plugin, player, titleId);
            case 20 -> {
                boolean next = !title.visible();
                plugin.getTitleManager().update(new Title(title.id(), title.name(), title.color(),
                        title.description(), next));
                player.sendMessage(Text.parse(next
                        ? "&a未拥有该头衔的玩家现在可以在菜单中看到它"
                        : "&c未拥有该头衔的玩家现在看不到它"));
                TitleEditMenu.open(plugin, player, titleId);
            }
            case 16 -> {
                if (!shiftClick) {
                    player.sendMessage(Text.parse("&c按住 Shift 点击以确认删除该头衔"));
                    return;
                }
                plugin.getTitleManager().delete(titleId);
                player.sendMessage(Component.text("已删除头衔 ", NamedTextColor.RED)
                        .append(title.bracketedComponent()));
                AdminMenu.open(plugin, player, 0);
            }
            case 22 -> TitleEditMenu.promptGive(plugin, player, titleId);
            case 26 -> AdminMenu.open(plugin, player, 0);
            default -> {
            }
        }
    }

    private void handleColor(Player player, MenuHolder holder, int slot) {
        String titleId = holder.getTitleId();
        if (slot >= 0 && slot < ColorMenu.CODES.length) {
            if (ColorMenu.apply(plugin, titleId, ColorMenu.CODES[slot])) {
                TitleEditMenu.open(plugin, player, titleId);
            } else {
                AdminMenu.open(plugin, player, 0);
            }
            return;
        }
        if (slot == 26) {
            TitleEditMenu.open(plugin, player, titleId);
        }
    }
}
