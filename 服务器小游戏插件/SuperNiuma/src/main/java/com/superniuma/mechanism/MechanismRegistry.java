package com.superniuma.mechanism;

import com.superniuma.SuperNiumaPlugin;
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

/** mechanisms.yml 的读取与加权抽取。 */
public final class MechanismRegistry {

    private static final String FILE = "mechanisms.yml";

    private final SuperNiumaPlugin plugin;
    private final Map<String, MechanismDef> defs = new LinkedHashMap<>();

    public MechanismRegistry(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        defs.clear();
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("mechanisms");
        if (root == null) {
            plugin.getLogger().warning(FILE + " 缺少 mechanisms 段。");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            defs.put(id, parse(id, sec));
        }
        plugin.getLogger().info("已加载 " + defs.size() + " 种机关。");
    }

    private MechanismDef parse(String id, ConfigurationSection sec) {
        boolean enabled = sec.getBoolean("enabled", true);
        double weight = sec.getDouble("weight", 10.0);
        Material icon = Material.matchMaterial(sec.getString("icon", "STONE"));
        if (icon == null) {
            icon = Material.STONE;
        }
        String name = sec.getString("name", id);
        List<String> lore = sec.getStringList("lore");
        RotationRule rotation = parseRotation(sec.getString("rotation", "four-way"));
        double points = sec.getDouble("points", 0.0);
        int interval = sec.getInt("interval-ticks", 40);
        int range = sec.getInt("arrow-range", 20);
        double arrowSpeed = sec.getDouble("arrow-speed", 1.6);
        int radius = sec.getInt("radius", 1);
        double lowRate = sec.getDouble("low-clear-rate", 0.05);
        double lowWeight = sec.getDouble("low-clear-weight", 40.0);
        int lowMin = sec.getInt("low-clear-min-count", 1);
        double scale = sec.getDouble("scale", 0.5);

        List<MechanismDef.Variant> variants = new ArrayList<>();
        ConfigurationSection variantRoot = sec.getConfigurationSection("variants");
        if (variantRoot != null) {
            for (String vid : variantRoot.getKeys(false)) {
                ConfigurationSection v = variantRoot.getConfigurationSection(vid);
                if (v == null) {
                    continue;
                }
                List<Integer> size = v.getIntegerList("size");
                int sx = size.isEmpty() ? 1 : Math.max(1, size.get(0));
                int sy = 1;
                int sz;
                if ("dispenser".equals(id)) {
                    // 发射器：size = [宽度, 高度]，深度固定 1 格，保证出口不被自己挡住。
                    sy = size.size() > 1 ? Math.max(1, size.get(1)) : 1;
                    sz = 1;
                } else {
                    sz = size.size() > 1 ? Math.max(1, size.get(1)) : sx;
                }
                variants.add(new MechanismDef.Variant(
                        vid, v.getString("name", vid), v.getDouble("weight", 10.0), sx, sy, sz));
            }
        }
        if (variants.isEmpty()) {
            variants.add(new MechanismDef.Variant("default", name, 1.0, 1, 1, 1));
        }

        return new MechanismDef(id, enabled, weight, icon, name, lore, rotation, points,
                interval, range, arrowSpeed, radius, lowRate, lowWeight, lowMin, scale, variants);
    }

    private RotationRule parseRotation(String raw) {
        if (raw == null) {
            return RotationRule.FOUR_WAY;
        }
        try {
            return RotationRule.valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return RotationRule.FOUR_WAY;
        }
    }

    public MechanismDef def(String id) {
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    public List<MechanismDef> enabled() {
        List<MechanismDef> out = new ArrayList<>();
        for (MechanismDef def : defs.values()) {
            if (def.enabled() && def.weight() > 0) {
                out.add(def);
            }
        }
        return out;
    }

    /**
     * 按权重抽一个机关。
     *
     * @param bombWeight 炸弹的临时权重；&lt; 0 表示使用配置里的权重
     */
    public MechanismDef pickDef(Random random, double bombWeight) {
        List<MechanismDef> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;
        for (MechanismDef def : defs.values()) {
            if (!def.enabled()) {
                continue;
            }
            double w = def.weight();
            if ("bomb".equals(def.id()) && bombWeight >= 0) {
                w = bombWeight;
            }
            if (w <= 0) {
                continue;
            }
            pool.add(def);
            weights.add(w);
            total += w;
        }
        if (pool.isEmpty() || total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    public MechanismDef.Variant pickVariant(MechanismDef def, Random random) {
        List<MechanismDef.Variant> variants = def.variants();
        double total = 0.0;
        for (MechanismDef.Variant v : variants) {
            total += Math.max(0.0, v.weight());
        }
        if (total <= 0) {
            return variants.get(0);
        }
        double roll = random.nextDouble() * total;
        for (MechanismDef.Variant v : variants) {
            roll -= Math.max(0.0, v.weight());
            if (roll <= 0) {
                return v;
            }
        }
        return variants.get(variants.size() - 1);
    }
}
