package com.lobbytoys;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.List;

/** 某一种玩具在配置里最终生效的外观：名字 / 代表道具 / 药水类型 / 描述。 */
public record ToyDefinition(String id, String name, Material material, PotionType potion, List<String> lore) {
}
