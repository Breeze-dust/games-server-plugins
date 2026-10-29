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

public final class AdminMenu {

    private AdminMenu() {
    }

    public static void open(PlayerTitlesPlugin plugin, Player player, int page) {
        List<Title> titles = plugin.getTitleManager().getTitles();
        int pages = Math.max(1, (titles.size() + TitleMenu.PER_PAGE - 1) / TitleMenu.PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.ADMIN, null, current);
        Component title = Text.parse("&8头衔管理")
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
            lore.add(Component.text("颜色: ", NamedTextColor.GRAY)
                    .append(Text.parse(entry.color() + entry.name())));
            lore.add(Component.text("未拥有者可见: ", NamedTextColor.GRAY)
                    .append(Component.text(entry.visible() ? "是" : "否",
                            entry.visible() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            lore.add(Component.text("拥有者: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.getTitleManager().ownerCount(entry.id()) + " 人",
                            NamedTextColor.WHITE)));
            lore.add(Component.text("左键编辑该头衔", NamedTextColor.YELLOW));
            inventory.setItem(i, GuiItems.icon(Material.NAME_TAG, entry.bracketedComponent(), lore));
        }

        if (titles.isEmpty()) {
            inventory.setItem(22, GuiItems.icon(Material.PAPER, "&7还没有头衔",
                    List.of("&7点击下方绿宝石创建头衔")));
        }

        if (current > 0) {
            inventory.setItem(45, GuiItems.icon(Material.ARROW, "&e上一页",
                    List.of("&7返回第 " + current + " 页")));
        }
        inventory.setItem(48, GuiItems.icon(Material.BARRIER, "&c关闭菜单", List.of("&7关闭当前界面")));
        inventory.setItem(49, GuiItems.icon(Material.EMERALD, "&a新建头衔",
                List.of("&7点击后在聊天栏输入 ID 与名称", "&7创建后可继续设置颜色和描述")));
        if (current < pages - 1) {
            inventory.setItem(53, GuiItems.icon(Material.ARROW, "&e下一页",
                    List.of("&7前往第 " + (current + 2) + " 页")));
        }

        player.openInventory(inventory);
    }

    public static void promptCreate(PlayerTitlesPlugin plugin, Player player) {
        player.closeInventory();
        plugin.getChatInput().request(player,
                "&e请输入新头衔的 ID（只能包含字母、数字、下划线，输入 cancel 取消）：",
                id -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (id.equalsIgnoreCase("cancel")) {
                        open(plugin, player, 0);
                        return;
                    }
                    if (!id.matches("[A-Za-z0-9_]{1,24}")) {
                        player.sendMessage(Text.parse("&cID 只能包含字母、数字、下划线，且最长 24 位"));
                        open(plugin, player, 0);
                        return;
                    }
                    if (plugin.getTitleManager().exists(id)) {
                        player.sendMessage(Text.parse("&c该 ID 已存在"));
                        open(plugin, player, 0);
                        return;
                    }
                    plugin.getChatInput().request(player,
                            "&e请输入头衔的显示名称（输入 cancel 取消）：",
                            name -> {
                                if (!player.isOnline()) {
                                    return;
                                }
                                if (name.isBlank() || name.equalsIgnoreCase("cancel")) {
                                    open(plugin, player, 0);
                                    return;
                                }
                                String display = name.length() > 32 ? name.substring(0, 32) : name;
                                plugin.getTitleManager().create(id, display, "&f", "&7暂无描述");
                                player.sendMessage(Text.parse("&a已创建头衔 &f" + id
                                        + "&a，可继续在编辑面板中修改颜色与描述"));
                                open(plugin, player, 0);
                            });
                });
    }
}
