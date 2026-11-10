package com.example.playertitles.gui;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** 管理员页面里的「募捐者自定义头衔」子菜单。 */
public final class DonorTitlesMenu {

    private DonorTitlesMenu() {
    }

    public static void open(PlayerTitlesPlugin plugin, Player player, int page) {
        List<Title> titles = plugin.getTitleManager().getDonorTitles();
        int pages = Math.max(1, (titles.size() + TitleMenu.PER_PAGE - 1) / TitleMenu.PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.DONOR_TITLES, null, current);
        Component title = Text.parse("&8募捐者自定义头衔")
                .append(Component.text(" (" + (current + 1) + "/" + pages + ")", NamedTextColor.DARK_GRAY));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);

        int start = current * TitleMenu.PER_PAGE;
        for (int i = 0; i < TitleMenu.PER_PAGE && start + i < titles.size(); i++) {
            Title entry = titles.get(start + i);
            holder.getEntryIds().add(entry.id());

            List<Component> lore = new ArrayList<>(entry.descriptionLore());
            lore.add(Component.empty());
            lore.add(Component.text("ID: ", NamedTextColor.GRAY)
                    .append(Component.text(entry.id(), NamedTextColor.WHITE)));
            lore.add(Component.text("所属玩家: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getTitleManager().donorNameOf(entry.id()), NamedTextColor.WHITE)));
            lore.add(Component.text("左键编辑该头衔", NamedTextColor.YELLOW));
            inventory.setItem(i, GuiItems.icon(Material.NAME_TAG, entry.bracketedComponent(), lore));
        }

        if (titles.isEmpty()) {
            inventory.setItem(22, GuiItems.icon(Material.PAPER, "&7还没有募捐者专属头衔",
                    List.of("&7用 /titles donor add <玩家> 添加")));
        }

        if (current > 0) {
            inventory.setItem(45, GuiItems.icon(Material.ARROW, "&e上一页",
                    List.of("&7返回第 " + current + " 页")));
        }
        inventory.setItem(48, GuiItems.icon(Material.BARRIER, "&c关闭菜单", List.of("&7关闭当前界面")));
        inventory.setItem(49, GuiItems.icon(Material.ARROW, "&7返回管理面板",
                List.of("&7回到全部头衔列表")));
        if (current < pages - 1) {
            inventory.setItem(53, GuiItems.icon(Material.ARROW, "&e下一页",
                    List.of("&7前往第 " + (current + 2) + " 页")));
        }

        player.openInventory(inventory);
    }
}
