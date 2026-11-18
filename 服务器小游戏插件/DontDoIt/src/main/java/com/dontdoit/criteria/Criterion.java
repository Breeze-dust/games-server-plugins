package com.dontdoit.criteria;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 一条已解析的词条：类型 + 显示名 + 难度 + 参数。 */
public final class Criterion {

    public final String key;
    public final CritType type;
    public final String name;
    public final Difficulty difficulty;
    public final Map<String, String> params;
    public final boolean builtin;

    private final Map<String, Object> cache = new HashMap<>();

    public Criterion(String key, CritType type, String name, Difficulty difficulty,
                     Map<String, String> params, boolean builtin) {
        this.key = key;
        this.type = type;
        this.name = name;
        this.difficulty = difficulty;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.builtin = builtin;
    }

    public static Criterion custom(String teamName, String text) {
        Map<String, String> p = new HashMap<>();
        p.put("text", text);
        return new Criterion("custom:" + teamName + ":" + text, CritType.USER_DEFINED,
                text, Difficulty.HARD, p, true);
    }

    public String param(String key) {
        return params.get(key);
    }

    public boolean has(String key) {
        String v = params.get(key);
        return v != null && !v.trim().isEmpty();
    }

    public boolean bool(String key, boolean def) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        return Boolean.parseBoolean(v.trim());
    }

    public int intParam(String key, int def) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double doubleParam(String key, double def) {
        String v = params.get(key);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public NumRange range(String key) {
        Object cached = cache.get("range:" + key);
        if (cached instanceof NumRange r) {
            return r;
        }
        NumRange r = NumRange.parse(params.get(key));
        cache.put("range:" + key, r);
        return r;
    }

    public ValueSet items(String key) {
        return set("item:" + key, () -> ValueSet.items(params.get(key)));
    }

    public ValueSet blocks(String key) {
        return set("block:" + key, () -> ValueSet.blocks(params.get(key)));
    }

    public ValueSet entities(String key) {
        return set("entity:" + key, () -> ValueSet.entities(params.get(key)));
    }

    public ValueSet plain(String key) {
        return set("plain:" + key, () -> ValueSet.plain(params.get(key)));
    }

    private ValueSet set(String cacheKey, java.util.function.Supplier<ValueSet> supplier) {
        Object cached = cache.get(cacheKey);
        if (cached instanceof ValueSet vs) {
            return vs;
        }
        ValueSet vs = supplier.get();
        cache.put(cacheKey, vs);
        return vs;
    }

    /** 用于日志 / 调试的唯一标识。 */
    public String id() {
        return type.id + ":" + name.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return name;
    }
}
