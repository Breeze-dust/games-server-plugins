package com.example.playertitles.api;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 头衔插件对其他插件暴露的只读接口，通过 Bukkit ServicesManager 获取。
 */
public interface TitleProvider {

    /**
     * @return 玩家当前头衔的 ID，没有头衔时返回 null
     */
    @Nullable
    String getTitleId(UUID playerId);

    /**
     * @return 带头尾括号和悬浮描述的头衔组件 {@code 【头衔】}，没有头衔时返回 null
     */
    @Nullable
    Component getTitleComponent(UUID playerId);

    /**
     * 让插件重新应用一次头顶头衔队伍（默认空实现，兼容旧版）。
     * 其他插件临时占用计分板队伍/名牌后，用它把头衔恢复回来。
     */
    default void refresh(UUID playerId) {
    }

    /**
     * 给玩家发放一个已存在的头衔（不自动装备）。
     *
     * @return true = 本次新获得；false = 已拥有 / 头衔不存在 / 旧版插件没有此方法
     */
    default boolean grant(UUID playerId, String titleId) {
        return false;
    }

    /**
     * 发头衔；头衔定义不存在时先按给定内容创建，再发放。
     * 小游戏据此把"完成某事"直接变成一个头衔，不需要管理员预先建头衔。
     *
     * @return true = 本次新获得；false = 已拥有 / 参数无效 / 旧版插件没有此方法
     */
    default boolean grantOrCreate(UUID playerId, String titleId,
                                  String name, String color, String description) {
        return false;
    }

    /** 玩家是否已拥有某个头衔。 */
    default boolean owns(UUID playerId, String titleId) {
        return false;
    }
}
