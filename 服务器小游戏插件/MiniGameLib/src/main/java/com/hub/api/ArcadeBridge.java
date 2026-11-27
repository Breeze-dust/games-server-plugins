package com.hub.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 街机派对桥：小游戏在结算时把自己的前三名上报给街机派对。
 *
 * <p>只有被 ArcadeParty 接管的对局才需要上报；街机派对没装/没注册时调用是空操作。
 * 用法（在各小游戏结算、统计完名次之后）：
 * <pre>
 *   ArcadeBridge.report("tntbattle", firsts, seconds, thirds);
 * </pre>
 * 三个集合里的玩家都拿对应名次的星星（多人并列第一就都算第一）。
 */
public final class ArcadeBridge {

    /** 街机派对注册的接收器。 */
    public interface Listener {

        /**
         * 一局小游戏结束。
         *
         * @param gameKey 游戏标识（ArcadeParty 配置里的 games.&lt;key&gt;）
         * @param firsts  第一名（可能多人）
         * @param seconds 第二名（可能多人 / 可能为空）
         * @param thirds  第三名（可能多人 / 可能为空）
         */
        void onRoundResult(String gameKey, List<UUID> firsts, List<UUID> seconds, List<UUID> thirds);

        /** 小游戏没有正常结算（例如被管理员停掉）；街机派对按无成绩处理。 */
        default void onRoundAborted(String gameKey) {
        }

        /**
         * 该玩家是否正在某个街机派对的对局里（主城 tab 据此把条目显示成「街机派对」，
         * 而不是当前正在跑的那个迷你小游戏）。
         */
        default boolean isInRound(java.util.UUID player) {
            return false;
        }

        /** 街机派对当前是否在跑（等待室阶段为 false）。主城观战用来判断"换局后要不要继续跟着看"。 */
        default boolean isPartyRunning() {
            return false;
        }

        /** 街机派对这一局正在玩的小游戏显示名（不在对局中返回 null）。 */
        default String currentGameDisplay() {
            return null;
        }

        /** 街机派对在主城注册表里的显示名（观战菜单里的那一行）；没注册返回 null。 */
        default String partyGameName() {
            return null;
        }

        /** 街机派对的观战场地名（主城用它查观战落点）。 */
        default String partyArenaName() {
            return "";
        }
    }

    private static volatile Listener listener;

    private ArcadeBridge() {
    }

    public static void register(Listener impl) {
        listener = impl;
    }

    public static void unregister(Listener impl) {
        if (listener == impl) {
            listener = null;
        }
    }

    /** 街机派对是否在接管（游戏可用来决定要不要额外上报）。 */
    public static boolean isActive() {
        return listener != null;
    }

    /** 该玩家是否正在街机派对的对局里（派对没装/没在跑时为 false）。 */
    public static boolean isInRound(java.util.UUID player) {
        Listener l = listener;
        return l != null && l.isInRound(player);
    }

    /** 街机派对当前是否在跑（等待室阶段为 false）。 */
    public static boolean isPartyRunning() {
        Listener l = listener;
        return l != null && l.isPartyRunning();
    }

    /** 街机派对这一局正在玩的小游戏显示名；不在对局中返回 null。 */
    public static String currentGameDisplay() {
        Listener l = listener;
        return l == null ? null : l.currentGameDisplay();
    }

    /** 街机派对在主城注册表里的显示名；没注册返回 null。 */
    public static String partyGameName() {
        Listener l = listener;
        return l == null ? null : l.partyGameName();
    }

    /** 街机派对的观战场地名。 */
    public static String partyArenaName() {
        Listener l = listener;
        return l == null ? "" : l.partyArenaName();
    }

    public static void report(String gameKey, Collection<UUID> firsts,
                              Collection<UUID> seconds, Collection<UUID> thirds) {
        Listener l = listener;
        if (l == null) {
            return;
        }
        l.onRoundResult(gameKey, new ArrayList<>(firsts),
                new ArrayList<>(seconds), new ArrayList<>(thirds));
    }

    public static void aborted(String gameKey) {
        Listener l = listener;
        if (l != null) {
            l.onRoundAborted(gameKey);
        }
    }
}
