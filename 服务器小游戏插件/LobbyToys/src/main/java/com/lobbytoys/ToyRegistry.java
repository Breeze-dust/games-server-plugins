package com.lobbytoys;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 config.yml 的 toys.&lt;id&gt; 段读成 {@link ToyDefinition}。
 * 配置里没写、写错材质/药水名的项都退回 {@link ToyType} 的缺省值并打一条警告，删掉整段也不会坏。
 */
public final class ToyRegistry {

    private final Plugin plugin;
    private final Map<ToyType, ToyDefinition> definitions = new EnumMap<>(ToyType.class);

    public ToyRegistry(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        reload(config);
    }

    public void reload(FileConfiguration config) {
        definitions.clear();
        for (ToyType type : ToyType.values()) {
            String base = "toys." + type.id();

            String name = config.getString(base + ".name", type.defaultName());
            if (name == null || name.isBlank()) {
                name = type.defaultName();
            }

            Material material = readMaterial(config, base, type);
            PotionType potion = readPotion(config, base, type);

            List<String> lore = config.getStringList(base + ".lore");
            if (lore.isEmpty()) {
                lore = type.defaultLore();
            }

            definitions.put(type, new ToyDefinition(type.id(), name, material, potion, List.copyOf(lore)));
        }
    }

    private Material readMaterial(FileConfiguration config, String base, ToyType type) {
        String raw = config.getString(base + ".material");
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material != null && !material.isAir()) {
            return material;
        }
        if (raw != null && !raw.isBlank()) {
            plugin.getLogger().warning(base + ".material=" + raw
                    + " 不是有效的 Bukkit 材质名，已回退为 " + type.defaultMaterial().name()
                    + "（水瓶要写 POTION + potion: WATER，没有 WATER_BOTTLE 这个材质）");
        }
        return type.defaultMaterial();
    }

    private PotionType readPotion(FileConfiguration config, String base, ToyType type) {
        String raw = config.getString(base + ".potion");
        if (raw == null || raw.isBlank()) {
            return type.defaultPotion();
        }
        try {
            return PotionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(base + ".potion=" + raw
                    + " 不是有效的药水类型（如 WATER / AWKWARD / STRENGTH），已忽略。");
            return type.defaultPotion();
        }
    }

    public ToyDefinition get(ToyType type) {
        ToyDefinition definition = definitions.get(type);
        if (definition != null) {
            return definition;
        }
        return new ToyDefinition(type.id(), type.defaultName(), type.defaultMaterial(),
                type.defaultPotion(), type.defaultLore());
    }
}
