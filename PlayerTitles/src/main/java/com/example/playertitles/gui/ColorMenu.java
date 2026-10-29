package com.example.playertitles.gui;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.util.Text;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ColorMenu {

    public static final String[] CODES = {
            "&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7",
            "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f"
    };

    private static final String[] NAMES = {
            "黑色", "深蓝色", "深绿色", "深青色", "深红色", "深紫色", "金色", "灰色",
            "深灰色", "蓝色", "绿色", "青色", "红色", "粉红色", "黄色", "白色"
    };

    private static final Material[] MATERIALS = {
            Material.BLACK_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL,
            Material.RED_WOOL, Material.PURPLE_WOOL, Material.ORANGE_WOOL, Material.LIGHT_GRAY_WOOL,
            Material.GRAY_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIME_WOOL, Material.CYAN_WOOL,
            Material.RED_WOOL, Material.PINK_WOOL, Material.YELLOW_WOOL, Material.WHITE_WOOL
    };

    private ColorMenu() {
    }

    public static void open(PlayerTitlesPlugin plugin, Player player, String titleId) {
        Title title = plugin.getTitleManager().getTitle(titleId);
        if (title == null) {
            AdminMenu.open(plugin, player, 0);
            return;
        }

        MenuHolder holder = new MenuHolder(MenuHolder.Type.COLOR, titleId, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("选择颜色: ", NamedTextColor.DARK_GRAY).append(title.nameComponent()));
        holder.setInventory(inventory);

        for (int i = 0; i < CODES.length; i++) {
            boolean current = CODES[i].equalsIgnoreCase(title.color());
            List<Component> lore = current
                    ? List.of(Component.text("当前颜色", NamedTextColor.GREEN))
                    : List.of(Component.text("点击设为该颜色", NamedTextColor.YELLOW));
            inventory.setItem(i, GuiItems.icon(MATERIALS[i], Text.parse(CODES[i] + NAMES[i]), lore));
        }

        inventory.setItem(26, GuiItems.icon(Material.ARROW, "&7返回",
                List.of("&7回到头衔编辑面板")));
        player.openInventory(inventory);
    }

    public static boolean apply(PlayerTitlesPlugin plugin, String titleId, String code) {
        Title title = plugin.getTitleManager().getTitle(titleId);
        if (title == null) {
            return false;
        }
        plugin.getTitleManager().update(
                new Title(title.id(), title.name(), code, title.description(), title.visible()));
        return true;
    }
}
