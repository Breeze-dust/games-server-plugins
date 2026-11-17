package com.archery;

import java.util.Locale;

/** 移动靶往返运动所沿的轴。 */
public enum TargetAxis {
    X("x", "X 轴"),
    Z("z", "Z 轴");

    public final String key;
    public final String label;

    TargetAxis(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static TargetAxis fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "x", "x轴", "x-axis", "axis-x" -> X;
            case "z", "z轴", "z-axis", "axis-z" -> Z;
            default -> null;
        };
    }
}
