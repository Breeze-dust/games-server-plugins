package com.lobbytoys;

import org.bukkit.Material;

import java.util.List;

/** 某一种玩具在配置里最终生效的外观：名字 / 代表道具 / 描述。 */
public record ToyDefinition(String id, String name, Material material, List<String> lore) {
}
