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

public final class TitleEditMenu {

    private TitleEditMenu() {
    }

    public static void open(PlayerTitlesPlugin plugin, Player player, String titleId) {
        Title title = plugin.getTitleManager().getTitle(titleId);
        if (title == null) {
            AdminMenu.open(plugin, player, 0);
            return;
        }

        MenuHolder holder = new MenuHolder(MenuHolder.Type.EDIT, titleId, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("编辑头衔 ", NamedTextColor.DARK_GRAY).append(title.nameComponent()));
        holder.setInventory(inventory);

        List<Component> nameLore = new ArrayList<>();
        nameLore.add(Component.text("当前: ", NamedTextColor.GRAY).append(title.nameComponent()));
        nameLore.add(Component.text("点击修改头衔名称", NamedTextColor.YELLOW));
        inventory.setItem(10, GuiItems.icon(Material.NAME_TAG, Text.parse("&e修改名称"), nameLore));

        List<Component> colorLore = new ArrayList<>();
        colorLore.add(Component.text("当前颜色: ", NamedTextColor.GRAY)
                .append(Text.parse(title.color() + "■ " + title.color())));
        colorLore.add(Component.text("点击从调色板中选择颜色", NamedTextColor.YELLOW));
        inventory.setItem(12, GuiItems.icon(Material.WHITE_WOOL, Text.parse("&e修改颜色"), colorLore));

        List<Component> descriptionLore = new ArrayList<>(title.descriptionLore());
        descriptionLore.add(Component.empty());
        descriptionLore.add(Component.text("点击修改头衔描述", NamedTextColor.YELLOW));
        inventory.setItem(14, GuiItems.icon(Material.BOOK, Text.parse("&e修改描述"), descriptionLore));

        List<Component> visibleLore = new ArrayList<>();
        visibleLore.add(Component.text("当前: ", NamedTextColor.GRAY)
                .append(Component.text(title.visible() ? "未拥有者也可见" : "仅拥有者可见",
                        title.visible() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        visibleLore.add(Component.text("控制未拥有该头衔的玩家", NamedTextColor.GRAY));
        visibleLore.add(Component.text("在菜单里能否看到它", NamedTextColor.GRAY));
        visibleLore.add(Component.text("点击切换", NamedTextColor.YELLOW));
        inventory.setItem(20, GuiItems.icon(title.visible() ? Material.LIME_DYE : Material.GRAY_DYE,
                Text.parse("&e未拥有者可见性"), visibleLore));

        inventory.setItem(16, GuiItems.icon(Material.BARRIER, "&c删除头衔",
                List.of("&7按住 Shift 点击确认删除", "&c删除后使用该头衔的玩家会失去头衔")));
        inventory.setItem(22, GuiItems.icon(Material.PLAYER_HEAD, "&b给予玩家",
                List.of("&7点击后在聊天栏输入在线玩家名字", "&7会授予拥有权并立即装备")));
        inventory.setItem(26, GuiItems.icon(Material.ARROW, "&7返回",
                List.of("&7回到头衔管理面板")));

        player.openInventory(inventory);
    }

    public static void promptRename(PlayerTitlesPlugin plugin, Player player, String titleId) {
        player.closeInventory();
        plugin.getChatInput().request(player, "&e请输入新的头衔名称（输入 cancel 取消）：", input -> {
            if (!player.isOnline()) {
                return;
            }
            if (input.isBlank() || input.equalsIgnoreCase("cancel")) {
                open(plugin, player, titleId);
                return;
            }
            Title title = plugin.getTitleManager().getTitle(titleId);
            if (title == null) {
                AdminMenu.open(plugin, player, 0);
                return;
            }
            String name = input.length() > 32 ? input.substring(0, 32) : input;
            plugin.getTitleManager().update(new Title(title.id(), name, title.color(),
                    title.description(), title.visible()));
            player.sendMessage(Text.parse("&a头衔名称已更新"));
            open(plugin, player, titleId);
        });
    }

    public static void promptDescription(PlayerTitlesPlugin plugin, Player player, String titleId) {
        player.closeInventory();
        plugin.getChatInput().request(player,
                "&e请输入新的头衔描述（支持 & 颜色代码，可用 \\n 换行，输入 cancel 取消）：",
                input -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (input.equalsIgnoreCase("cancel")) {
                        open(plugin, player, titleId);
                        return;
                    }
                    Title title = plugin.getTitleManager().getTitle(titleId);
                    if (title == null) {
                        AdminMenu.open(plugin, player, 0);
                        return;
                    }
                    String description = input.length() > 200 ? input.substring(0, 200) : input;
                    plugin.getTitleManager().update(
                            new Title(title.id(), title.name(), title.color(), description, title.visible()));
                    player.sendMessage(Text.parse("&a头衔描述已更新"));
                    open(plugin, player, titleId);
                });
    }

    public static void promptGive(PlayerTitlesPlugin plugin, Player player, String titleId) {
        player.closeInventory();
        plugin.getChatInput().request(player, "&e请输入要给予头衔的在线玩家名字（输入 cancel 取消）：", input -> {
            if (!player.isOnline()) {
                return;
            }
            if (input.isBlank() || input.equalsIgnoreCase("cancel")) {
                open(plugin, player, titleId);
                return;
            }
            Player target = Bukkit.getPlayerExact(input);
            Title title = plugin.getTitleManager().getTitle(titleId);
            if (target == null) {
                player.sendMessage(Text.parse("&c找不到在线玩家: " + input));
                open(plugin, player, titleId);
                return;
            }
            if (title == null) {
                AdminMenu.open(plugin, player, 0);
                return;
            }
            plugin.getTitleManager().grant(target.getUniqueId(), titleId);
            plugin.getTitleManager().setPlayerTitle(target.getUniqueId(), titleId);
            player.sendMessage(Component.text("已给予并装备 ", NamedTextColor.GREEN)
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" 头衔 ", NamedTextColor.GREEN))
                    .append(title.bracketedComponent()));
            target.sendMessage(Component.text("你获得了新头衔: ", NamedTextColor.GREEN)
                    .append(title.bracketedComponent()));
            open(plugin, player, titleId);
        });
    }
}
