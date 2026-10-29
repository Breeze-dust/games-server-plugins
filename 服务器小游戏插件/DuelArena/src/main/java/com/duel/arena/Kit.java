package com.duel.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 对战物品配置：主手（快捷栏 0）、快捷栏 1~8、物品栏 27 格、副手、四件盔甲。
 * 两个玩家共用同一份 Kit。
 */
public class Kit {

    public static final int HOTBAR = 9;
    public static final int SLOTS = 36; // 快捷栏 9 + 物品栏 27

    /** 0-8 快捷栏（0=主手），9-35 物品栏。 */
    private final ItemStack[] slots = new ItemStack[SLOTS];
    private ItemStack offhand;
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;

    public Kit() {
    }

    public ItemStack getSlot(int i) {
        return i >= 0 && i < SLOTS ? slots[i] : null;
    }

    public void setSlot(int i, ItemStack item) {
        if (i >= 0 && i < SLOTS) {
            slots[i] = item;
        }
    }

    public ItemStack getOffhand() {
        return offhand;
    }

    public void setOffhand(ItemStack offhand) {
        this.offhand = offhand;
    }

    public ItemStack getArmor(int index) {
        return switch (index) {
            case 0 -> helmet;
            case 1 -> chestplate;
            case 2 -> leggings;
            case 3 -> boots;
            default -> null;
        };
    }

    public void setArmor(int index, ItemStack item) {
        switch (index) {
            case 0 -> helmet = item;
            case 1 -> chestplate = item;
            case 2 -> leggings = item;
            case 3 -> boots = item;
            default -> {
            }
        }
    }

    /** 是否为空（没有任何物品）。 */
    public boolean isEmpty() {
        for (ItemStack it : slots) {
            if (it != null) {
                return false;
            }
        }
        return offhand == null && helmet == null && chestplate == null && leggings == null && boots == null;
    }

    /** 发放到玩家身上（先清空背包）。 */
    public void apply(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null) {
                inv.setItem(i, slots[i].clone());
            }
        }
        inv.setItemInOffHand(offhand == null ? null : offhand.clone());
        inv.setHelmet(helmet == null ? null : helmet.clone());
        inv.setChestplate(chestplate == null ? null : chestplate.clone());
        inv.setLeggings(leggings == null ? null : leggings.clone());
        inv.setBoots(boots == null ? null : boots.clone());
    }

    /** 保存到配置段。 */
    public void save(ConfigurationSection sec) {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null) {
                sec.set("slots." + i, slots[i]);
            } else {
                sec.set("slots." + i, null);
            }
        }
        sec.set("offhand", offhand);
        sec.set("helmet", helmet);
        sec.set("chestplate", chestplate);
        sec.set("leggings", leggings);
        sec.set("boots", boots);
    }

    /** 从配置段读取。 */
    public static Kit load(ConfigurationSection sec) {
        Kit kit = new Kit();
        if (sec == null) {
            return kit;
        }
        for (int i = 0; i < SLOTS; i++) {
            kit.slots[i] = sec.getItemStack("slots." + i);
        }
        kit.offhand = sec.getItemStack("offhand");
        kit.helmet = sec.getItemStack("helmet");
        kit.chestplate = sec.getItemStack("chestplate");
        kit.leggings = sec.getItemStack("leggings");
        kit.boots = sec.getItemStack("boots");
        return kit;
    }
}
