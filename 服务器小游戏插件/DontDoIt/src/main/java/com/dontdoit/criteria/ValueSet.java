package com.dontdoit.criteria;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 词条参数里的「值集合」：支持逗号分隔多个 id、{@code *} 通配、{@code #tag} 标签、
 * 以及 {@code !id} 排除。格式与官方 criteria.yml 完全一致。
 */
public final class ValueSet {

    private final Set<String> positives = new HashSet<>();
    private final Set<String> negatives = new HashSet<>();
    private boolean wildcard;
    private final boolean empty;

    private ValueSet(String spec, String registry) {
        String text = spec == null ? "" : spec.trim();
        if (text.isEmpty() || text.equals("*")) {
            wildcard = true;
            empty = true;
            return;
        }
        boolean any = false;
        for (String raw : text.split(",")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            boolean neg = token.startsWith("!");
            if (neg) {
                token = token.substring(1).trim();
            }
            if (token.equals("*")) {
                wildcard = true;
                any = true;
                continue;
            }
            Set<String> resolved = new HashSet<>();
            if (token.startsWith("#")) {
                resolved.addAll(resolveTag(token.substring(1), registry));
            } else {
                resolved.add(token);
            }
            if (neg) {
                negatives.addAll(resolved);
            } else {
                positives.addAll(resolved);
                any = true;
            }
        }
        empty = !any && negatives.isEmpty();
        if (empty) {
            wildcard = true;
        }
    }

    public static ValueSet of(String spec, String registry) {
        return new ValueSet(spec, registry);
    }

    public static ValueSet blocks(String spec) {
        return new ValueSet(spec, Tag.REGISTRY_BLOCKS);
    }

    public static ValueSet items(String spec) {
        return new ValueSet(spec, Tag.REGISTRY_ITEMS);
    }

    public static ValueSet entities(String spec) {
        return new ValueSet(spec, Tag.REGISTRY_ENTITY_TYPES);
    }

    public static ValueSet plain(String spec) {
        return new ValueSet(spec, null);
    }

    public boolean matches(String id) {
        if (id == null) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (negatives.contains(key)) {
            return false;
        }
        return wildcard || positives.contains(key);
    }

    public boolean matches(Material material) {
        return material != null && matches(material.getKey().getKey());
    }

    public boolean matches(EntityType type) {
        return type != null && matches(type.getKey().getKey());
    }

    public boolean isWildcard() {
        return wildcard;
    }

    private static Set<String> resolveTag(String name, String registry) {
        Set<String> out = new HashSet<>();
        if ("air".equals(name)) {
            out.add("air");
            out.add("cave_air");
            out.add("void_air");
            return out;
        }
        if (registry == null) {
            return out;
        }
        NamespacedKey key = NamespacedKey.minecraft(name);
        try {
            if (Tag.REGISTRY_ENTITY_TYPES.equals(registry)) {
                Tag<EntityType> tag = Bukkit.getTag(registry, key, EntityType.class);
                if (tag != null) {
                    for (EntityType t : tag.getValues()) {
                        out.add(t.getKey().getKey());
                    }
                }
            } else {
                @SuppressWarnings("unchecked")
                Tag<Material> tag = Bukkit.getTag(registry, key, Material.class);
                if (tag != null) {
                    for (Material m : tag.getValues()) {
                        out.add(m.getKey().getKey());
                    }
                }
            }
        } catch (Throwable ignored) {
            // 标签不存在时按空集合处理
        }
        return out;
    }
}
