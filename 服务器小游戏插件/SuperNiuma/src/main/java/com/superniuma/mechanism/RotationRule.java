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
    HORIZONTAL_FLIP,

    /** 只能水平旋转 90°，右键无效。 */
    HORIZONTAL_ONLY,

    /** 完全无法旋转。 */
    NONE;

    public boolean supportsHorizontal() {
        return this == FOUR_WAY || this == SIX_WAY
                || this == HORIZONTAL_FLIP || this == HORIZONTAL_ONLY;
    }

    public boolean supportsVertical() {
        return this != HORIZONTAL_ONLY && this != NONE;
    }

    /** 右键一次是否翻 180°（否则是 90°）。 */
    public boolean verticalStepIsHalfTurn() {
        return this == VERTICAL || this == HORIZONTAL_FLIP;
    }
}
