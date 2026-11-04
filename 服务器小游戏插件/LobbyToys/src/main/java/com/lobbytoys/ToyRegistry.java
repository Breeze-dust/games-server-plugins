package com.lobbytoys;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 把 config.yml 的 toys.&lt;id&gt; 段读成 {@link ToyDefinition}。
 * 配置里没写、写错材质名的项都退回 {@link ToyType} 的缺省值，删掉整段也不会坏。
 */
public final class ToyRegistry {

    private final Map<ToyType, ToyDefinition> definitions = new EnumMap<>(ToyType.class);

    public ToyRegistry(FileConfiguration config) {
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

            Material material = Material.matchMaterial(
                    config.getString(base + ".material", type.defaultMaterial().name()));
            if (material == null || material.isAir()) {
                material = type.defaultMaterial();
            }

            List<String> lore = config.getStringList(base + ".lore");
            if (lore.isEmpty()) {
                lore = type.defaultLore();
            }

            definitions.put(type, new ToyDefinition(type.id(), name, material, List.copyOf(lore)));
        }
    }

    public ToyDefinition get(ToyType type) {
        ToyDefinition definition = definitions.get(type);
        if (definition != null) {
            return definition;
        }
        return new ToyDefinition(type.id(), type.defaultName(), type.defaultMaterial(), type.defaultLore());
    }
}
