package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** 小玩具切换菜单（HubCore 主菜单里那格「小玩具」点开的就是它）。 */
public final class ToyMenu implements Listener {

    private static final int ROWS = 3;
    private static final int CENTER_SLOT = 13;
    private static final ToyMenuHolder HOLDER = new ToyMenuHolder();

    private final LobbyToysPlugin plugin;
    private final ToyItems items;

    public ToyMenu(LobbyToysPlugin plugin, ToyItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(HOLDER, ROWS * 9, Chat.color("&8小玩具"));
        fillBorder(inventory);

        ToyType selected = items.selected(player);
        ToyType[] all = ToyType.values();
        int start = CENTER_SLOT - (all.length - 1);
        for (int i = 0; i < all.length; i++) {
            int slot = start + i * 2;
            if (slot < 0 || slot >= ROWS * 9) {
                continue;
            }
            inventory.setItem(slot, items.createIcon(player, all[i], all[i] == selected));
        }
        player.openInventory(inventory);
    }

    private static void fillBorder(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.color("&8 "));
            filler.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ToyMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            return;
        }
        ToyType type = items.iconOf(event.getCurrentItem());
        if (type == null) {
            return;
        }

        items.equip(player, type, plugin.settings().itemSlot);
        Chat.send(player, "&a已切换小玩具：&f" + type.displayName());
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    private static final class ToyMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
