package com.lobbytoys;

import com.hub.util.Chat;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** 玩具道具与菜单图标的构造/识别，全部靠 PDC 标记，不认材质和名字。 */
public final class ToyItems {

    private final ToyRegistry registry;
    private final NamespacedKey toyKey;
    private final NamespacedKey iconKey;
    private final NamespacedKey selectedKey;
    private final NamespacedKey lookKey;
    /** 1.0.0 用的旧标记，读到时按「分身」处理并会被自动换成新道具。 */
    private final NamespacedKey legacyKey;

    public ToyItems(Plugin plugin, ToyRegistry registry) {
        this.registry = registry;
        this.toyKey = new NamespacedKey(plugin, "toy-id");
        this.iconKey = new NamespacedKey(plugin, "menu-icon");
        this.selectedKey = new NamespacedKey(plugin, "selected-toy");
        this.lookKey = new NamespacedKey(plugin, "toy-look");
        this.legacyKey = new NamespacedKey(plugin, "clone-toy");
    }

    /** 物品栏里那个可右键的玩具。 */
    public ItemStack create(Player owner, ToyType type) {
        ToyDefinition definition = registry.get(type);
        ItemStack stack = base(owner, definition);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.lore(chatList(definition.lore()));
            meta.getPersistentDataContainer().set(toyKey, PersistentDataType.STRING, type.id());
            meta.getPersistentDataContainer().set(lookKey, PersistentDataType.STRING, lookTag(definition));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 材质 + 名字 + 描述拼成的指纹，用来判断物品栏里的旧道具是否还是旧外观。 */
    private static String lookTag(ToyDefinition definition) {
        // v2：1.1.3 及以前的道具丢了显示名（base() 没写回 ItemMeta），换前缀让它们全部重建一次
        return "v2|" + definition.material().name() + "|" + definition.name() + "|" + String.join("|", definition.lore());
    }

    /** 菜单里的图标，带「已装备 / 点击切换」，不带玩具标记（不能被当成真玩具带走）。 */
    public ItemStack createIcon(Player viewer, ToyType type, boolean equipped) {
        ToyDefinition definition = registry.get(type);
        ItemStack stack = base(viewer, definition);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(definition.lore());
            lore.add("");
            lore.add(equipped ? "&a✔ 已装备" : "&e点击切换到这个小玩具");
            meta.lore(chatList(lore));
            meta.getPersistentDataContainer().set(iconKey, PersistentDataType.STRING, type.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack base(Player owner, ToyDefinition definition) {
        ItemStack stack = new ItemStack(definition.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta instanceof SkullMeta skull) {
                skull.setPlayerProfile(owner.getPlayerProfile());
            }
            meta.displayName(Chat.color(definition.name()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static List<net.kyori.adventure.text.Component> chatList(List<String> lines) {
        List<net.kyori.adventure.text.Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(Chat.color(line));
        }
        return result;
    }

    public ToyType typeOf(ItemStack stack) {
        ToyType type = readId(stack, toyKey);
        if (type != null) {
            return type;
        }
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        boolean legacy = stack.getItemMeta().getPersistentDataContainer()
                .has(legacyKey, PersistentDataType.BYTE);
        return legacy ? ToyType.CLONE : null;
    }

    /** 配置里写的名字（带 & 颜色代码），用于聊天提示。 */
    public String name(ToyType type) {
        return registry.get(type).name();
    }

    public ToyType iconOf(ItemStack stack) {
        return readId(stack, iconKey);
    }

    public boolean isToy(ItemStack stack) {
        return typeOf(stack) != null;
    }

    private ToyType readId(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return ToyType.byId(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    /** 玩家上次选中的玩具，存在玩家 PDC 里，重启/换号后仍然记得。 */
    public ToyType selected(Player player) {
        ToyType type = ToyType.byId(
                player.getPersistentDataContainer().get(selectedKey, PersistentDataType.STRING));
        return type == null ? ToyType.CLONE : type;
    }

    /** 选中某个玩具：改玩家 PDC，并把物品栏里的玩具（没有就新发一个）换成它。 */
    public void equip(Player player, ToyType type, int preferredSlot) {
        player.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, type.id());

        PlayerInventory inventory = player.getInventory();
        ItemStack toy = create(player, type);

        if (isToy(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(toy);
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isToy(inventory.getItem(slot))) {
                inventory.setItem(slot, toy);
                return;
            }
        }
        give(inventory, toy, preferredSlot);
    }

    /** 没有玩具时给一个：优先放 preferredSlot，被占就找别的空位。 */
    public void give(PlayerInventory inventory, ItemStack toy, int preferredSlot) {
        int slot = Math.max(0, Math.min(8, preferredSlot));
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, toy);
        } else {
            inventory.addItem(toy);
        }
    }

    public boolean hasAny(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (isToy(inventory.getItemInOffHand())) {
            return true;
        }
        for (ItemStack stack : inventory.getContents()) {
            if (isToy(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按当前配置重建玩家物品栏里的玩具（原地替换，保持槽位）。
     * ItemStack 的材质是创建时定死的，改配置后不重建的话旧道具不会变。
     * 返回是否改动了物品栏。
     */
    public boolean refresh(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;

        ToyType offhand = typeOf(inventory.getItemInOffHand());
        if (offhand != null) {
            inventory.setItemInOffHand(create(player, offhand));
            changed = true;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ToyType type = typeOf(inventory.getItem(slot));
            if (type != null) {
                inventory.setItem(slot, create(player, type));
                changed = true;
            }
        }
        return changed;
    }

    /** 只替换外观已过时的玩具（材质/名字/描述跟当前配置不一致，或旧版本创建、没有指纹）。 */
    public boolean refreshStale(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;

        ItemStack offhand = inventory.getItemInOffHand();
        ToyType offhandType = typeOf(offhand);
        if (offhandType != null && isStale(offhand, offhandType)) {
            inventory.setItemInOffHand(create(player, offhandType));
            changed = true;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ToyType type = typeOf(stack);
            if (type != null && isStale(stack, type)) {
                inventory.setItem(slot, create(player, type));
                changed = true;
            }
        }
        return changed;
    }

    private boolean isStale(ItemStack stack, ToyType type) {
        String tag = stack.getItemMeta().getPersistentDataContainer().get(lookKey, PersistentDataType.STRING);
        return !lookTag(registry.get(type)).equals(tag);
    }

    /** 排障用：把物品上的玩具标记原样打出来。 */
    public String describe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "空手";
        }
        if (!stack.hasItemMeta()) {
            return stack.getType().name() + "（无 meta，不是玩具）";
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return stack.getType().name()
                + " toy-id=" + pdc.get(toyKey, PersistentDataType.STRING)
                + " look=" + pdc.get(lookKey, PersistentDataType.STRING)
                + " legacy=" + pdc.has(legacyKey, PersistentDataType.BYTE);
    }

    /** 当前配置下这个玩具应有的外观指纹，排障时一起打出来对比。 */
    public String expectedLook(ToyType type) {
        return lookTag(registry.get(type));
    }
}
