package com.archery;

import java.util.Locale;

/** 区域可配置的刷新权重/保底项。前三个是运动类型，后两个是大小。 */
public enum RegionMetric {
    FIXED("fixed", "固定"),
    MOVING("moving", "移动"),
    FAST("fast", "快速"),
    NORMAL("normal", "正常"),
    SMALL("small", "缩小");

    public final String key;
    public final String label;

    RegionMetric(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static RegionMetric fromKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (RegionMetric metric : values()) {
            if (metric.key.equals(value) || metric.label.equals(value)) {
                return metric;
            }
        }
        return switch (value) {
            case "固定" -> FIXED;
            case "移动" -> MOVING;
            case "快速" -> FAST;
            case "正常", "普通" -> NORMAL;
            case "缩小", "小" -> SMALL;
            default -> null;
        };
    }
}
