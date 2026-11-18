package com.dontdoit.criteria;

/** 词条类型：id 与 criteria.yml 里的键一一对应；带 * 的是插件内置类型。 */
public enum CritType {
    EAT_ITEM("eat_item"),
    WALK_DISTANCE("walk_distance"),
    BREAK_BLOCK("break_block"),
    PICK_UP_ITEM("pick_up_item"),
    FALL_DISTANCE("fall_distance"),
    CRAFT_ITEM("craft_item"),
    STATUS_EFFECT("status_effect"),
    HURT_ENTITY("hurt_entity"),
    HURT_BY_ENTITY("hurt_by_entity"),
    KILL_ENTITY("kill_entity"),
    KILLED_BY_ENTITY("killed_by_entity"),
    JUMP("jump"),
    OPEN_INVENTORY("open_inventory"),
    TRANSFER_ITEM("transfer_item"),
    INTERACT_BLOCK_WITH_ITEM("interact_block_with_item"),
    DROP_ITEM("drop_item"),
    PLACE_BLOCK("place_block"),
    PHYSICAL_ACTION("physical_action"),
    STANDING_ON_BLOCK("standing_on_block"),
    IN_BLOCK("in_block"),
    DISTANCE_TO_ENTITY("distance_to_entity"),
    ROTATION("rotation"),
    HOLDING_ITEM("holding_item"),
    HEALTH("health"),
    FOOD_LEVEL("food_level"),
    SURROUNDED_BY_BLOCK("surrounded_by_block"),
    INVENTORY_CONTAINING_ITEM("inventory_containing_item"),
    HEIGHTMAP_MATCHING_POS("heightmap_matching_pos"),
    RESPAWN_TIME("respawn_time"),
    MOVE_TIME("move_time"),
    CRITERIA_HOLD_TIME("criteria_hold_time"),
    AFTER_DEATH_TIME("after_death_time"),
    LIFE_COUNT("life_count"),
    LEVEL_UP("level_up"),
    BUCKET_OPERATION("bucket_operation"),
    SELECTED_HOTBAR_SLOT("selected_hotbar_slot"),

    /** 内置：按 Y 高度判定。 */
    Y_LEVEL("y_level"),
    /** 内置：每队自定义词条。 */
    USER_DEFINED("user_defined"),
    /** 内置：绑定后立即触发（自爆词条）。 */
    IMMEDIATELY_TRIGGER("immediately_trigger");

    public final String id;

    CritType(String id) {
        this.id = id;
    }

    public static CritType byId(String id) {
        for (CritType t : values()) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    /** 状态型词条：由每秒的判定任务检查，而不是由事件触发。 */
    public boolean isPeriodic() {
        switch (this) {
            case STATUS_EFFECT:
            case STANDING_ON_BLOCK:
            case IN_BLOCK:
            case DISTANCE_TO_ENTITY:
            case ROTATION:
            case HOLDING_ITEM:
            case HEALTH:
            case FOOD_LEVEL:
            case SURROUNDED_BY_BLOCK:
            case INVENTORY_CONTAINING_ITEM:
            case HEIGHTMAP_MATCHING_POS:
            case RESPAWN_TIME:
            case MOVE_TIME:
            case CRITERIA_HOLD_TIME:
            case AFTER_DEATH_TIME:
            case LIFE_COUNT:
            case Y_LEVEL:
                return true;
            default:
                return false;
        }
    }
}
