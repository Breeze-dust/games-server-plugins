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
}
