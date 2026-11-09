package com.superniuma.mechanism;

/** 一名玩家选中的机关：机关定义 + 尺寸子类。 */
public record MechanismChoice(String optionId, MechanismDef def, MechanismDef.Variant variant) {

    public String displayName() {
        if (variant != null && !variant.id().equals("default")) {
            return def.displayName() + " &7(" + variant.name() + "&7)";
        }
        return def.displayName();
    }
}
