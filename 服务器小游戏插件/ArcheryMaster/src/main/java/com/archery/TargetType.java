package com.archery;

/** 靶子的三种运动类型。缩小是独立维度，不算在这里。 */
public enum TargetType {
    FIXED("fixed", "固定"),
    MOVING("moving", "移动"),
    FAST("fast", "快速");

    public final String key;
    public final String label;

    TargetType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static TargetType fromKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (TargetType type : values()) {
            if (type.key.equals(value) || type.label.equals(value)) {
                return type;
            }
        }
        return switch (value) {
            case "固定", "fixed" -> FIXED;
            case "移动", "moving" -> MOVING;
            case "快速", "fast" -> FAST;
            default -> null;
        };
    }
}
