package com.hub.api;

import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * 小游戏 → 头衔插件（PlayerTitles）的桥：小游戏判定"玩家完成了某个条件"后，调这里发头衔。
 *
 * <p>头衔插件通过 Bukkit Services 暴露 {@code com.example.playertitles.api.TitleProvider}；
 * 本类用反射调它的方法，所以小游戏插件只需要依赖 MiniGameLib，不需要编译期依赖 PlayerTitles。
 * 没装头衔插件、或头衔插件版本太旧没有对应方法时，所有调用都是空操作并返回 false。
 *
 * <p>典型用法（条件成立的那一行）：
 * <pre>{@code
 * boolean unlocked = GameTitles.grantOrCreate(id, "mm_no_knife",
 *         "无声之刃", "&4", "&7密室杀手：不使用刀赢得一局比赛");
 * if (unlocked) {
 *     Chat.send(player, "&6你获得了头衔 &4[无声之刃]&6！");
 * }
 * }</pre>
 *
 * <p>头衔内容：{@link #grantOrCreate} 只在头衔 ID 还不存在时按传入的名字/颜色/描述创建；
 * 已存在（例如管理员在 PlayerTitles 里改过）就沿用现有定义。
 */
public final class GameTitles {

    private static final String PROVIDER_CLASS = "com.example.playertitles.api.TitleProvider";

    private GameTitles() {
    }

    /**
     * 发放一个已存在的头衔（不自动装备）。
     *
     * @return true = 本次新获得；false = 已拥有 / 头衔不存在 / 没装头衔插件
     */
    public static boolean grant(UUID player, String titleId) {
        return call("grant", new Class<?>[]{UUID.class, String.class}, player, titleId);
    }

    /**
     * 发放头衔；头衔定义不存在时先按给定内容创建（对所有玩家可见）再发放。
     *
     * @return true = 本次新获得；false = 已拥有 / 参数无效 / 没装头衔插件
     */
    public static boolean grantOrCreate(UUID player, String titleId,
                                        String name, String color, String description) {
        return call("grantOrCreate",
                new Class<?>[]{UUID.class, String.class, String.class, String.class, String.class},
                player, titleId, name, color, description);
    }

    /** 玩家是否已拥有某个头衔（没装头衔插件时返回 false）。 */
    public static boolean owns(UUID player, String titleId) {
        return call("owns", new Class<?>[]{UUID.class, String.class}, player, titleId);
    }

    /**
     * 确保头衔定义存在（不发给任何人）。
     * 小游戏在 onEnable 里调一次，头衔就会提前出现在管理菜单里，而不是等第一个玩家达成后才出现。
     *
     * @return true = 本次新建；false = 已存在 / 没装头衔插件
     */
    public static boolean ensure(String titleId, String name, String color, String description) {
        return call("ensureTitle",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                titleId, name, color, description);
    }

    // ---------- 反射桥 ----------

    private static boolean call(String method, Class<?>[] types, Object... args) {
        Object provider = provider();
        if (provider == null) {
            return false;
        }
        try {
            Object result = providerType().getMethod(method, types).invoke(provider, args);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException ex) {
            // 旧版头衔插件没有这个方法：静默跳过，不影响游戏逻辑
            return false;
        }
    }

    private static Object provider() {
        Class<?> type = providerType();
        return type == null ? null : Bukkit.getServicesManager().load(type);
    }

    private static Class<?> providerType() {
        for (Class<?> known : Bukkit.getServicesManager().getKnownServices()) {
            if (PROVIDER_CLASS.equals(known.getName())) {
                return known;
            }
        }
        return null;
    }
}
