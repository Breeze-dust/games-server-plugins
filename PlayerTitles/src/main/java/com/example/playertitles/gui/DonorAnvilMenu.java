package com.example.playertitles.gui;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.TitleManager;
import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DonorAnvilMenu {

    public static final int MAX_LENGTH = 32;

    private DonorAnvilMenu() {
    }

    public static boolean open(PlayerTitlesPlugin plugin, Player player) {
        UUID uuid = player.getUniqueId();
        TitleManager manager = plugin.getTitleManager();
        if (!manager.isDonor(uuid)) {
            player.sendMessage(Text.parse("&c你不在募捐感谢名单中，无法自定义称号"));
            return false;
        }
        long remaining = manager.donorCooldownRemaining(uuid);
        if (remaining > 0L) {
            player.sendMessage(Text.parse("&c称号修改冷却中，还需 &f" + formatRemaining(remaining)
                    + " &c才能再次修改"));
            return false;
        }
        String titleId = manager.ensureDonorTitle(uuid);
        Title title = titleId == null ? null : manager.getTitle(titleId);
        if (title == null) {
            player.sendMessage(Text.parse("&c你的专属头衔数据异常，请联系管理员"));
            return false;
        }

        DonorAnvilHolder holder = new DonorAnvilHolder(uuid, titleId, plain(title.name()));
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL,
                Text.parse("&8自定义称号"));
        holder.setInventory(inventory);
        inventory.setItem(0, inputItem(title));
        player.openInventory(inventory);
        return true;
    }

    private static ItemStack inputItem(Title title) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title.nameComponent().decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.parse("&7在上方输入栏填写新的称号").decoration(TextDecoration.ITALIC, false));
        lore.add(Text.parse("&7然后点击右侧命名牌两次确认").decoration(TextDecoration.ITALIC, false));
        lore.add(Text.parse("&8最多 " + MAX_LENGTH + " 个字符，支持 &颜色代码")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack resultItem(DonorAnvilHolder holder, String text) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse(text).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        boolean confirming = holder.getConfirmText() != null && holder.getConfirmText().equals(text);
        if (confirming) {
            lore.add(Text.parse("&e再次点击确认修改").decoration(TextDecoration.ITALIC, false));
            lore.add(Text.parse("&7确认后进入冷却，请检查名称是否正确")
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Text.parse("&7点击一次准备修改").decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(Text.parse(text)).trim();
    }

    public static String formatRemaining(long millis) {
        long totalMinutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append(" 天 ");
        }
        if (hours > 0L) {
            builder.append(hours).append(" 小时 ");
        }
        if (days == 0L) {
            builder.append(minutes).append(" 分钟");
        }
        return builder.toString().trim();
    }
}
