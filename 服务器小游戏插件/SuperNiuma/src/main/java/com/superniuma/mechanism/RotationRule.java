package com.superniuma.mechanism;

/** 机关的旋转规则。 */
public enum RotationRule {

    /** 左键水平转 90°，右键上下翻 180°。 */
    FOUR_WAY,

    /** 左键水平转 90°，右键上下翻 180°（发射器朝向）。 */
    SIX_WAY,

    /** 只能上下翻 180°。 */
    VERTICAL;

    public boolean supportsHorizontal() {
        return this != VERTICAL;
    }

    public boolean supportsVertical() {
        return true;
    }
}
