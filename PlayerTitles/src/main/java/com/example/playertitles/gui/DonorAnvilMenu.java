package com.example.playertitles.gui;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.TitleManager;
import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

public final class DonorAnvilMenu {

    public static final int MAX_LENGTH = Title.MAX_NAME_LENGTH;

    /** 每个打开中玩家的铁砧会话；真实铁砧菜单没有自定义 holder，只能按玩家关联。 */
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private DonorAnvilMenu() {
    }

    public static final class Session {
        private final String titleId;
        private final String originalName;
        private String confirmText;

        private Session(String titleId, String originalName) {
            this.titleId = titleId;
            this.originalName = originalName;
        }

        public String getTitleId() {
            return titleId;
        }

        public String getOriginalName() {
            return originalName;
        }

        public String getConfirmText() {
            return confirmText;
        }

        public void setConfirmText(String confirmText) {
            this.confirmText = confirmText;
        }
    }

    public static Session session(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    public static void endSession(UUID uuid) {
        SESSIONS.remove(uuid);
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

        // 用真实的铁砧菜单：只有它会触发 PrepareAnvilEvent、把输入框文本送到服务端，
        // 也才能把经验费用改成 0。createInventory(ANVIL) 只是假菜单，客户端会自己算产物。
        if (!(player.openAnvil(player.getLocation(), true) instanceof AnvilView view)) {
            player.sendMessage(Text.parse("&c无法打开铁砧界面，请联系管理员"));
            return false;
        }
        SESSIONS.put(uuid, new Session(titleId, plain(title.name())));
        view.getTopInventory().setItem(0, inputItem(title));
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

    public static ItemStack resultItem(Session session, String text) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse(text).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        boolean confirming = session.getConfirmText() != null && session.getConfirmText().equals(text);
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

    /** 关掉铁砧界面后清空输入/产物，避免命名牌被带走。 */
    public static void clear(AnvilInventory inventory) {
        inventory.setItem(0, null);
        inventory.setItem(1, null);
        inventory.setItem(2, null);
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
