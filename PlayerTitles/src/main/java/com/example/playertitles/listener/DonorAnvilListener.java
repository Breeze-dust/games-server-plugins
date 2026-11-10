package com.example.playertitles.listener;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.TitleManager;
import com.example.playertitles.gui.DonorAnvilHolder;
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

public final class DonorAnvilListener implements Listener {

    private static final DateTimeFormatter NEXT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PlayerTitlesPlugin plugin;

    public DonorAnvilListener(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof DonorAnvilHolder holder)) {
            return;
        }
        AnvilInventory inventory = event.getInventory();
        // 改名不消耗经验：价格归零，点击产物也由下面的事件全部拦截。
        inventory.setRepairCost(0);
        inventory.setRepairCostAmount(0);
        String text = clean(inventory.getRenameText());
        event.setResult(text.isEmpty() || text.equals(holder.getOriginalName())
                ? null
                : DonorAnvilMenu.resultItem(holder, text));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DonorAnvilHolder holder)) {
            return;
        }
        // 命名牌只用于展示，任何格子都不允许拿走或放入。
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() != 2
                || !(top instanceof AnvilInventory anvil)) {
            return;
        }
        String text = clean(anvil.getRenameText());
        if (text.isEmpty()) {
            return;
        }
        if (text.equals(holder.getConfirmText())) {
            commit(player, holder, text);
            return;
        }
        holder.setConfirmText(text);
        anvil.setItem(2, DonorAnvilMenu.resultItem(holder, text));
    }

    private void commit(Player player, DonorAnvilHolder holder, String text) {
        if (text.equals(holder.getOriginalName())) {
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
        if (event.getInventory().getHolder() instanceof DonorAnvilHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DonorAnvilHolder) {
            event.getInventory().clear();
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
