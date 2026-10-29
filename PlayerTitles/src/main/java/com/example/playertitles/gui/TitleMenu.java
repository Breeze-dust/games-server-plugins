package com.example.playertitles.gui;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class TitleMenu {

    public static final int PER_PAGE = 45;

    private TitleMenu() {
    }

    public static void open(PlayerTitlesPlugin plugin, Player player, int page) {
        UUID uuid = player.getUniqueId();
        boolean admin = player.hasPermission("playertitles.admin");
        List<Title> titles = new ArrayList<>();
        for (Title title : plugin.getTitleManager().getTitles()) {
            // 管理员看全部；普通玩家只看「已拥有」或「未拥有者可见」的头衔。
            if (admin || title.visible() || plugin.getTitleManager().owns(uuid, title.id())) {
                titles.add(title);
            }
        }
        int pages = Math.max(1, (titles.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.TITLES, null, current);
        Component title = Text.parse(plugin.getConfig().getString("menu.title", "&8头衔菜单"))
                .append(Component.text(" (" + (current + 1) + "/" + pages + ")", NamedTextColor.DARK_GRAY));
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);

        Title active = plugin.getTitleManager().getTitle(uuid);
        int start = current * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < titles.size(); i++) {
            Title entry = titles.get(start + i);
            holder.getEntryIds().add(entry.id());

            boolean owned = plugin.getTitleManager().owns(uuid, entry.id());
            boolean selected = active != null && active.id().equals(entry.id());

            List<Component> lore = new ArrayList<>(entry.descriptionLore());
            lore.add(Component.empty());
            lore.add(owned
                    ? Component.text("✔ 已拥有", NamedTextColor.GREEN)
                    : Component.text("✖ 未拥有", NamedTextColor.RED));
            if (selected) {
                lore.add(Component.text("✔ 当前头衔", NamedTextColor.GREEN));
            } else if (owned) {
                lore.add(Component.text("点击使用此头衔", NamedTextColor.YELLOW));
            } else if (admin) {
                lore.add(Component.text("点击使用（管理员）", NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("向管理员获取后可使用", NamedTextColor.GRAY));
            }
            inventory.setItem(i, GuiItems.icon(Material.NAME_TAG, entry.bracketedComponent(), lore));
        }

        if (titles.isEmpty()) {
            inventory.setItem(22, GuiItems.icon(Material.PAPER, "&7暂无可显示头衔",
                    List.of("&7请联系管理员获取头衔")));
        }

        if (current > 0) {
            inventory.setItem(45, GuiItems.icon(Material.ARROW, "&e上一页",
                    List.of("&7返回第 " + current + " 页")));
        }
        inventory.setItem(49, GuiItems.icon(Material.BARRIER, "&c清除头衔",
                List.of("&7点击取消当前头衔", "&7头顶与聊天将不再显示头衔")));
        if (current < pages - 1) {
            inventory.setItem(53, GuiItems.icon(Material.ARROW, "&e下一页",
                    List.of("&7前往第 " + (current + 2) + " 页")));
        }

        player.openInventory(inventory);
    }
}
