package com.example.playertitles.listener;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.gui.MenuHolder;
import com.example.playertitles.gui.TitleMenu;
import com.example.playertitles.util.Text;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MenuItemListener implements Listener {

    private final PlayerTitlesPlugin plugin;
    private final NamespacedKey menuItemKey;

    public MenuItemListener(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
        this.menuItemKey = new NamespacedKey(plugin, "title_menu_item");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!itemEnabled() || hubProvidesEntry()) {
            Bukkit.getScheduler().runTask(plugin, () -> removeMenuItems(event.getPlayer()));
            return;
        }
        scheduleGive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (!itemEnabled() || hubProvidesEntry()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeMenuItems(event.getPlayer()), 20L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveItem(event.getPlayer()), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!itemEnabled() || hubProvidesEntry()) {
            Bukkit.getScheduler().runTask(plugin, () -> removeMenuItems(event.getPlayer()));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> giveItem(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMenuItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("playertitles.use")) {
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
            return;
        }
        TitleMenu.open(plugin, player, 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean clickedMenu = isMenuItem(event.getCurrentItem());
        boolean cursorMenu = isMenuItem(event.getCursor());
        boolean hotbarMenu = false;
        if (event.getAction() == InventoryAction.HOTBAR_SWAP
                || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            int button = event.getHotbarButton();
            if (button >= 0) {
                hotbarMenu = isMenuItem(player.getInventory().getItem(button));
            }
        }
        if (!clickedMenu && !cursorMenu && !hotbarMenu) {
            return;
        }
        event.setCancelled(true);
        if (clickedMenu
                && event.getClickedInventory() instanceof PlayerInventory
                && !(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)
                && player.hasPermission("playertitles.use")) {
            Bukkit.getScheduler().runTask(plugin, () -> TitleMenu.open(plugin, player, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenuItem);
    }

    private void scheduleGive(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> giveItem(player));
        int retry = plugin.getConfig().getInt("menu-item.retry-ticks", 40);
        if (retry > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> giveItem(player), retry);
        }
    }

    private void giveItem(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (!itemEnabled() || hubProvidesEntry()) {
            removeMenuItems(player);
            return;
        }
        if (!player.hasPermission("playertitles.use")) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        removeMenuItems(player);
        int slot = Math.max(0, Math.min(35, plugin.getConfig().getInt("menu-item.slot", 8)));
        if (!isEmpty(inventory.getItem(slot))) {
            slot = findEmptySlot(inventory, slot);
        }
        inventory.setItem(slot, createItem());
    }

    /** 从背包里清掉插件发的头衔菜单道具（含 HubCore 接管入口后的旧道具）。 */
    private void removeMenuItems(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMenuItem(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }

    /** HubCore 已经在主城菜单右下角提供头衔入口时，不再往背包发下界之星。 */
    private boolean hubProvidesEntry() {
        return Bukkit.getPluginManager().getPlugin("HubCore") != null;
    }

    private int findEmptySlot(PlayerInventory inventory, int preferred) {
        for (int i = 0; i < 9; i++) {
            if (i != preferred && isEmpty(inventory.getItem(i))) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (isEmpty(inventory.getItem(i))) {
                return i;
            }
        }
        return preferred;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private ItemStack createItem() {
        Material material = Material.matchMaterial(
                plugin.getConfig().getString("menu-item.material", "NETHER_STAR"), false);
        if (material == null || !material.isItem()) {
            material = Material.NETHER_STAR;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse(plugin.getConfig().getString("menu-item.name", "&e头衔菜单"))
                .decoration(TextDecoration.ITALIC, false));
        List<String> lore = plugin.getConfig().getStringList("menu-item.lore");
        meta.lore(lore.stream()
                .map(line -> Text.parse(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE);
    }

    private boolean itemEnabled() {
        return plugin.getConfig().getBoolean("menu-item.enabled", true);
    }
}
