package com.example.playertitles.listener;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.TitleManager;
import com.example.playertitles.gui.DonorAnvilMenu;
import com.example.playertitles.util.Text;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.view.AnvilView;

public final class DonorAnvilListener implements Listener {

    private static final DateTimeFormatter NEXT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PlayerTitlesPlugin plugin;

    public DonorAnvilListener(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        AnvilView view = event.getView();
        DonorAnvilMenu.Session session = DonorAnvilMenu.session(view.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        AnvilInventory inventory = event.getInventory();
        // 改名不消耗经验：真实铁砧的等级费用归零；产物点击仍由下面的事件拦截。
        view.setRepairCost(0);
        view.setRepairItemCountCost(0);
        inventory.setRepairCost(0);
        inventory.setRepairCostAmount(0);

        String text = clean(view.getRenameText());
        if (session.getConfirmText() != null && !session.getConfirmText().equals(text)) {
            session.setConfirmText(null);
        }
        event.setResult(text.isEmpty() || text.equals(session.getOriginalName())
                ? null
                : DonorAnvilMenu.resultItem(session, text));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        DonorAnvilMenu.Session session = DonorAnvilMenu.session(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top instanceof AnvilInventory anvil) || !(event.getView() instanceof AnvilView view)) {
            return;
        }
        // 命名牌只用于展示，任何格子都不允许拿走或放入。
        event.setCancelled(true);
        if (event.getRawSlot() != 2) {
            return;
        }
        String text = clean(view.getRenameText());
        if (text.isEmpty()) {
            return;
        }
        if (text.equals(session.getConfirmText())) {
            commit(player, session, text);
            return;
        }
        session.setConfirmText(text);
        anvil.setItem(2, DonorAnvilMenu.resultItem(session, text));
    }

    private void commit(Player player, DonorAnvilMenu.Session session, String text) {
        if (text.equals(session.getOriginalName())) {
            player.sendMessage(Text.parse("&7称号名称没有变化，未消耗冷却"));
            return;
        }
        TitleManager manager = plugin.getTitleManager();
        long remaining = manager.donorCooldownRemaining(player.getUniqueId());
        if (remaining > 0L) {
            player.sendMessage(Text.parse("&c称号修改冷却中，还需 &f"
                    + DonorAnvilMenu.formatRemaining(remaining) + " &c才能再次修改"));
            player.closeInventory();
            return;
        }
        if (!manager.renameDonorTitle(player.getUniqueId(), text)) {
            player.sendMessage(Text.parse("&c称号修改失败，请联系管理员"));
            player.closeInventory();
            return;
        }
        player.closeInventory();
        player.sendMessage(Text.parse("&a称号已修改为 ").append(Text.parse(text)));
        long cooldown = manager.donorCooldownMillis();
        if (cooldown > 0L) {
            String next = Instant.ofEpochMilli(System.currentTimeMillis() + cooldown)
                    .atZone(ZoneId.systemDefault())
                    .format(NEXT_TIME);
            player.sendMessage(Text.parse("&7下次可修改时间: &f" + next));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && DonorAnvilMenu.session(player.getUniqueId()) != null
                && event.getView().getTopInventory() instanceof AnvilInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (DonorAnvilMenu.session(player.getUniqueId()) == null) {
            return;
        }
        DonorAnvilMenu.endSession(player.getUniqueId());
        if (event.getInventory() instanceof AnvilInventory anvil) {
            DonorAnvilMenu.clear(anvil);
        }
    }

    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        return value.length() > DonorAnvilMenu.MAX_LENGTH
                ? value.substring(0, DonorAnvilMenu.MAX_LENGTH)
                : value;
    }
}
