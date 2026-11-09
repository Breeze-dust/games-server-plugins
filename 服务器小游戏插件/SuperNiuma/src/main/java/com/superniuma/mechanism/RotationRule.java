package com.superniuma.mechanism;

/** 机关的旋转规则。 */
public enum RotationRule {

    /** 左键水平转 90°，右键上下翻 180°。 */
    FOUR_WAY,

    /** 左键水平转 90°，右键上下翻 180°（发射器朝向）。 */
    SIX_WAY,

    /** 只能上下翻 180°。 */
    VERTICAL,

    /** 左键水平转 90°，右键上下翻 180°（尖刺这类）。 */
    HORIZONTAL_FLIP;

    public boolean supportsHorizontal() {
        return this != VERTICAL;
    }

    public boolean supportsVertical() {
        return true;
    }

    /** 右键一次是否翻 180°（否则是 90°）。 */
    public boolean verticalStepIsHalfTurn() {
        return this == VERTICAL || this == HORIZONTAL_FLIP;
    }
}
