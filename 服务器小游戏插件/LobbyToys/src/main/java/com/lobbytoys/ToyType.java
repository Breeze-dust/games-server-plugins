package com.lobbytoys;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.List;

/**
 * 玩具种类（行为）。显示名、描述、代表道具都可以在 config.yml 的 toys.<id> 里覆盖，
 * 这里的值只是缺省值。加新玩具：在这里加一项，再在 {@link ToyListener#onInteract} 的 switch 里加分支。
 */
public enum ToyType {

    CLONE("clone", "&b分身玩具", Material.PLAYER_HEAD, null,
            List.of("&7右键 召唤 / 收回 你的分身", "&8只能在主城使用")),

    SHRINK("shrink", "&d缩小药水", Material.POTION, PotionType.THICK,
            List.of("&7右键 缩小 / 恢复自己的身形", "&8只能在主城使用"));

    private final String id;
    private final String defaultName;
    private final Material defaultMaterial;
    private final PotionType defaultPotion;
    private final List<String> defaultLore;

    ToyType(String id, String defaultName, Material defaultMaterial, PotionType defaultPotion,
            List<String> defaultLore) {
        this.id = id;
        this.defaultName = defaultName;
        this.defaultMaterial = defaultMaterial;
        this.defaultPotion = defaultPotion;
        this.defaultLore = defaultLore;
    }

    public String id() {
        return id;
    }

    public String defaultName() {
        return defaultName;
    }

    public Material defaultMaterial() {
        return defaultMaterial;
    }

    public PotionType defaultPotion() {
        return defaultPotion;
    }

    public List<String> defaultLore() {
        return defaultLore;
    }

    public static ToyType byId(String id) {
        if (id == null) {
            return null;
        }
        for (ToyType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
