package com.murdermystery;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 读取 {@code plugins/MurderMystery/items.yml}：道具总表、售货机货物与 GT混沌汽水事件配置。
 * 只负责解析数据，物品的真实生成和事件执行在 {@link MurderMysteryManager}。
 */
public final class MurderMysteryItems {

    private static final String FILE_NAME = "items.yml";

    public record ItemDef(String id, String type, boolean enabled, Material material,
                          int amount, String name, List<String> lore) {
    }

    public record Good(String itemId, double weight) {
    }

    public static final class EffectDef {
        private final String key;
        private final ConfigurationSection section;
        private final boolean enabled;
        private final double weight;
        private final boolean allowMurderer;

        private EffectDef(String key, ConfigurationSection section) {
            this.key = key;
            this.section = section;
            this.enabled = section.getBoolean("enabled", true);
            this.weight = Math.max(0.0, section.getDouble("weight", 1.0));
            this.allowMurderer = section.getBoolean("allow-murderer", true);
        }

        public String key() {
            return key;
        }

        public boolean enabled() {
            return enabled;
        }

        public double weight() {
            return weight;
        }

        public boolean allowMurderer() {
            return allowMurderer;
        }

        public int getInt(String path, int def) {
            return section.getInt(path, def);
        }

        public double getDouble(String path, double def) {
            return section.getDouble(path, def);
        }

        public String getString(String path, String def) {
            String value = section.getString(path);
            return value == null || value.isBlank() ? def : value;
        }

        public boolean getBoolean(String path, boolean def) {
            return section.getBoolean(path, def);
        }
    }

    private final MurderMysteryPlugin plugin;
    private final Map<String, ItemDef> items = new LinkedHashMap<>();
    private final List<Good> goods = new ArrayList<>();
    private final List<EffectDef> effects = new ArrayList<>();
    private double vendingCooldownSeconds = 3.0;
    private int vendingCostGold = 2;

    public MurderMysteryItems(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
    }

    /** 重新读取 items.yml；文件不存在时先从 jar 释放一份默认配置。 */
    public void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        Map<String, ItemDef> parsedItems = new LinkedHashMap<>();
        ConfigurationSection itemRoot = yml.getConfigurationSection("items");
        if (itemRoot != null) {
            for (String id : itemRoot.getKeys(false)) {
                ConfigurationSection sec = itemRoot.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                Material material = Material.matchMaterial(sec.getString("material", "STONE"));
                if (material == null) {
                    material = Material.STONE;
                }
                parsedItems.put(id.toLowerCase(Locale.ROOT), new ItemDef(
                        id.toLowerCase(Locale.ROOT),
                        sec.getString("type", "plain").toLowerCase(Locale.ROOT),
                        sec.getBoolean("enabled", true),
                        material,
                        Math.max(1, sec.getInt("amount", 1)),
                        sec.getString("name", ""),
                        sec.getStringList("lore")));
            }
        }

        List<Good> parsedGoods = new ArrayList<>();
        ConfigurationSection goodsRoot = yml.getConfigurationSection("vending.goods");
        if (goodsRoot != null) {
            for (String key : goodsRoot.getKeys(false)) {
                ConfigurationSection sec = goodsRoot.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                String itemId = sec.getString("item", key).toLowerCase(Locale.ROOT);
                double weight = Math.max(0.0, sec.getDouble("weight", 1.0));
                if (weight > 0) {
                    parsedGoods.add(new Good(itemId, weight));
                }
            }
        }

        List<EffectDef> parsedEffects = new ArrayList<>();
        ConfigurationSection effectRoot = yml.getConfigurationSection("effects.gt-soda");
        if (effectRoot != null) {
            for (String key : effectRoot.getKeys(false)) {
                ConfigurationSection sec = effectRoot.getConfigurationSection(key);
                if (sec != null) {
                    parsedEffects.add(new EffectDef(key, sec));
                }
            }
        }

        if (parsedItems.isEmpty() && !yml.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("items.yml 里没有解析到任何道具（items 段为空），请检查格式。");
        }

        items.clear();
        items.putAll(parsedItems);
        goods.clear();
        goods.addAll(parsedGoods);
        effects.clear();
        effects.addAll(parsedEffects);
        vendingCooldownSeconds = Math.max(0.0, yml.getDouble("vending.cooldown-seconds", 3.0));
        vendingCostGold = Math.max(0, yml.getInt("vending.cost-gold", 2));

        for (Good good : goods) {
            if (!items.containsKey(good.itemId())) {
                plugin.getLogger().warning("items.yml：售货机货物「" + good.itemId() + "」在 items 里不存在，已忽略。");
            }
        }
        plugin.getLogger().info("密室杀手道具：已加载 " + items.size() + " 件道具、"
                + goods.size() + " 件售货机货物、" + effects.size() + " 个汽水事件。");
    }

    public ItemDef item(String id) {
        return items.get(id == null ? null : id.toLowerCase(Locale.ROOT));
    }

    /** 按 key 取 GT混沌汽水事件（找不到返回 null）。 */
    public EffectDef effect(String key) {
        for (EffectDef effect : effects) {
            if (effect.key().equalsIgnoreCase(key)) {
                return effect;
            }
        }
        return null;
    }

    public double vendingCooldownSeconds() {
        return vendingCooldownSeconds;
    }

    /** 每次从售货机抽货扣除的金锭数量（0 = 免费）。 */
    public int vendingCostGold() {
        return vendingCostGold;
    }

    /** 按权重抽一件货物；没有可用货物时返回 null。 */
    public Good pickGood(Random random) {
        List<Good> pool = new ArrayList<>();
        double total = 0.0;
        for (Good good : goods) {
            ItemDef def = item(good.itemId());
            if (def != null && def.enabled()) {
                pool.add(good);
                total += good.weight();
            }
        }
        if (pool.isEmpty() || total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        Good last = pool.get(pool.size() - 1);
        for (Good good : pool) {
            roll -= good.weight();
            if (roll <= 0) {
                return good;
            }
        }
        return last;
    }

    /** 按权重抽一个汽水事件；杀手触发时跳过 allow-murderer=false 的事件。 */
    public EffectDef pickEffect(boolean murderer, Random random) {
        List<EffectDef> pool = new ArrayList<>();
        double total = 0.0;
        for (EffectDef effect : effects) {
            if (!effect.enabled() || effect.weight() <= 0) {
                continue;
            }
            if (murderer && !effect.allowMurderer()) {
                continue;
            }
            pool.add(effect);
            total += effect.weight();
        }
        if (pool.isEmpty() || total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        EffectDef last = pool.get(pool.size() - 1);
        for (EffectDef effect : pool) {
            roll -= effect.weight();
            if (roll <= 0) {
                return effect;
            }
        }
        return last;
    }
}
