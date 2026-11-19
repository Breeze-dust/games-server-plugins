package com.dontdoit.criteria;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 词条库：从 criteria.yml 读取（格式与官方完全一致），并提供按权重随机抽取。 */
public final class CriterionLibrary {

    private final List<Criterion> all = new ArrayList<>();

    public void load(File file) {
        all.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("criteria");
        if (root == null) {
            return;
        }
        int index = 0;
        for (String typeId : root.getKeys(false)) {
            CritType type = CritType.byId(typeId);
            if (type == null) {
                continue;
            }
            for (Map<?, ?> raw : yml.getMapList("criteria." + typeId)) {
                String name = string(raw.get("name"));
                if (name == null || name.isEmpty()) {
                    continue;
                }
                // 本插件扩展：disabled: true 的条目跳过（方便不删条目地禁用某条词条）
                if (Boolean.parseBoolean(String.valueOf(raw.get("disabled")))) {
                    continue;
                }
                Difficulty difficulty = Difficulty.byId(string(raw.get("difficulty")));
                java.util.Map<String, String> params = new java.util.HashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    String k = String.valueOf(e.getKey());
                    if (k.equals("name") || k.equals("difficulty")) {
                        continue;
                    }
                    if (e.getValue() != null) {
                        params.put(k, String.valueOf(e.getValue()));
                    }
                }
                String key = type.id + "#" + index++;
                all.add(new Criterion(key, type, name, difficulty, params, false));
            }
        }
    }

    private static String string(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    public List<Criterion> all() {
        return all;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    /**
     * 按权重随机抽一条词条。
     *
     * @param pool          候选池（词条库 + 本局动态词条）
     * @param lifePercentage 该队生命比例（0~1）
     * @param otherActive   其他队伍当前词条的 key
     * @param history       该队最近用过的词条 key（最近的在最后）
     */
    public Criterion random(List<Criterion> pool, double lifePercentage, Set<String> otherActive,
                            List<String> history, Random random) {
        if (pool.isEmpty()) {
            return null;
        }
        double[] weights = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < pool.size(); i++) {
            Criterion c = pool.get(i);
            double w = c.difficulty.weightMultiplier;
            if (otherActive.contains(c.key)) {
                w *= 0.5;
            }
            int last = history.lastIndexOf(c.key);
            if (last >= 0) {
                int distance = history.size() - 1 - last;
                w *= Math.max(0.02, -2.04 * Math.exp(-Math.pow(distance, 0.41)) + 1);
            }
            w *= lifeMultiplier(lifePercentage, c.difficulty);
            if (w <= 0) {
                w = 0.001;
            }
            weights[i] = w;
            total += w;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** 官方那条凑出来的生命比例权重曲线。 */
    private static double lifeMultiplier(double lifePercentage, Difficulty difficulty) {
        int d = difficulty.level;
        double v = -0.00565 * (d - 4) * (d - 5) * (d - 4.5) * Math.log(lifePercentage + 0.5) + 1;
        return Math.max(0.05, v);
    }

    /** 生成 4 条按 Y 高度判定的内置词条。 */
    public static List<Criterion> yLevelCriteria(int groundY, int groundOffset, int onAirOffset) {
        List<Criterion> list = new ArrayList<>(4);
        list.add(yLevel("位置高于高处（Y大于" + (groundY + onAirOffset) + "）", "above", groundY + onAirOffset));
        list.add(yLevel("位置低于高处（Y小于" + (groundY + onAirOffset) + "）", "below", groundY + onAirOffset));
        list.add(yLevel("位置高于地面（Y大于" + (groundY + groundOffset) + "）", "above", groundY + groundOffset));
        list.add(yLevel("位置低于地面（Y小于" + (groundY + groundOffset) + "）", "below", groundY + groundOffset));
        return list;
    }

    private static Criterion yLevel(String name, String mode, int border) {
        java.util.Map<String, String> p = new java.util.HashMap<>();
        p.put("mode", mode);
        p.put("border", String.valueOf(border));
        return new Criterion("y_level:" + mode + ":" + border, CritType.Y_LEVEL, name,
                Difficulty.VERY_EASY, p, true);
    }

    /** 只保留指定难度以上的词条（预留给设置界面，当前未用到）。 */
    public List<Criterion> filterMaxDifficulty(Difficulty max) {
        List<Criterion> out = new ArrayList<>();
        for (Criterion c : all) {
            if (c.difficulty.level <= max.level) {
                out.add(c);
            }
        }
        return out;
    }

    /** 供 /ddi list 用：按名字查找词条。 */
    public Criterion findByName(String name) {
        String q = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (Criterion c : all) {
            if (c.name.toLowerCase(Locale.ROOT).equals(q) || c.key.equalsIgnoreCase(q)) {
                return c;
            }
        }
        return null;
    }

    public Set<String> keys() {
        Set<String> out = new HashSet<>();
        for (Criterion c : all) {
            out.add(c.key);
        }
        return out;
    }
}
