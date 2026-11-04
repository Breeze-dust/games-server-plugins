package com.lobbytoys;

import org.bukkit.Material;

import java.util.List;

/**
 * 玩具清单。加新玩具：在这里加一项，再在 {@link ToyListener#onInteract} 里按类型分发即可。
 * 菜单图标、物品名、lore 都从这里取，避免三处各写一份。
 */
public enum ToyType {

    CLONE("clone", "分身玩具", Material.PLAYER_HEAD,
            List.of("&7右键 召唤 / 收回 你的分身", "&8只能在主城使用"));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<String> lore;

    ToyType(String id, String displayName, Material icon, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.lore = lore;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public List<String> lore() {
        return lore;
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
