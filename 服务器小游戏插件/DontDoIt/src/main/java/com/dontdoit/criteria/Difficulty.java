package com.dontdoit.criteria;

/** 触发难度：权重与官方实现一致。 */
public enum Difficulty {
    TRIGGER_DEFINITELY("trigger_definitely", 0.7, 1),
    VERY_EASY("very_easy", 0.9, 2),
    EASY("easy", 1.2, 3),
    NORMAL("normal", 1.3, 4),
    HARD("hard", 0.8, 5),
    VERY_HARD("very_hard", 0.3, 6),
    IMPOSSIBLE("impossible", 0.1, 7);

    public final String id;
    public final double weightMultiplier;
    public final int level;

    Difficulty(String id, double weightMultiplier, int level) {
        this.id = id;
        this.weightMultiplier = weightMultiplier;
        this.level = level;
    }

    public static Difficulty byId(String id) {
        for (Difficulty d : values()) {
            if (d.id.equalsIgnoreCase(id)) {
                return d;
            }
        }
        return NORMAL;
    }
}
