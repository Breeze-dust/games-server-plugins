package com.hub.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * 主城（HubCore）要实现并注册给 {@link HubAPI} 的能力集合。
 * <p>
 * 拆分出来的原因：HubAPI 属于公共库（MiniGameLib），不能反过来依赖主城插件的类，
 * 所以两边用这个接口对接——主城插件在 onEnable 里 {@code HubAPI.register(service)}，
 * 小游戏插件照旧调用静态的 {@link HubAPI}。
 * <p>
 * 主城插件没装/还没启用时，HubAPI 的调用会静默变成空操作，小游戏插件不会崩。
 */
public interface HubService {

    /** 玩家进入某游戏等待室（tab 显示 [等待中·游戏名]）。 */
    void setGame(UUID player, String gameName);

    /** 游戏正式开始，玩家已加入对局（tab 显示 [已加入·游戏名]）。 */
    void setPlaying(UUID player, String gameName);

    /** 设置玩家在某游戏中的状态。 */
    void setGameState(UUID player, String gameName, GameState state);

    /** 玩家结束游戏回大厅（tab 回到大厅分区）。 */
    void clearGame(UUID player);

    /**
     * 覆盖玩家 tab 列表名字的颜色（对局内按队伍染色，如绿茵足球的红/蓝队）。
     * 主城每秒刷新 tab 时，会把该颜色套在"游戏分区名单"的名字和"玩家列表名"的名字上，
     * 状态前缀（[等待中·游戏名] / [已加入·游戏名]）保持原样。
     * 玩家 {@link #clearGame}（退出游戏/下线）时主城会自动清除覆盖。
     * 默认实现为空操作（旧版主城未实现时静默忽略）。
     *
     * @param colorCode legacy 颜色代码字符（如 "c" 红 / "9" 蓝）；null 或空串恢复默认颜色
     */
    default void setTabNameColor(UUID player, String colorCode) {
    }

    /** 运行时登记一个游戏（一般不需要：主城插件会自动扫描插件目录里带接入标记的插件）。 */
    void registerGame(String id, String displayName, String icon, String color);

    /** 上报某游戏一个场地的最新状态（中央菜单羊毛行的数据源）。 */
    void setArena(String game, String arena, GameState state, int players, String extra);

    /** 场地被删除时通知主城移除其菜单状态。 */
    void removeArena(String game, String arena);

    /**
     * 上报某场地的「观战落点」：大厅玩家点这个场地进入观战时，主城把观众传送到这里。
     * <p>
     * 坐标是静态的（场地配置里的中心点 / 等待区），加载、编辑、重载场地后调一次即可，可随时覆盖。
     * 传 {@code null} 表示清除，主城会退回「跟着某位存活玩家」。{@code arena} 传空串 = 整个游戏共用
     * （不分场地的单场游戏用）。默认实现为空操作（旧版主城没有这个能力）。
     */
    default void setArenaSpectator(String game, String arena, Location location) {
    }

    /**
     * 玩家加入某游戏时，向全服公屏播报一条加入提示。
     *
     * @param gameIdOrName 游戏标识（插件名或显示名都行，主城会归一化成显示名）
     * @param playerName   加入的玩家名
     */
    void announceJoin(String gameIdOrName, String playerName);

    /**
     * 同 {@link #announceJoin(String, String)}，但带上场地名：公屏显示成
     * {@code [游戏名][场地名] 玩家名 加入了游戏}。
     * <p>多场地的游戏（如单挑的"重锤/相扑"）用这个，玩家才知道加的是哪个场。
     * 默认实现忽略场地名，退回两参数版本。
     *
     * @param arenaName 场地名（为空则退化成不带场地的播报）
     */
    default void announceJoin(String gameIdOrName, String playerName, String arenaName) {
        announceJoin(gameIdOrName, playerName);
    }

    /** 传送到大厅并给菜单道具。 */
    void teleportToLobby(Player player);

    /** 给玩家菜单道具（不清背包）。 */
    void giveMenuItem(Player player);

    /** 给玩家"回主城"道具（右键 = 退出所有游戏回主城）。 */
    void giveHubItem(Player player);

    /** 判断物品是否为"回主城"道具。 */
    boolean isHubItem(ItemStack item);

    /** 大厅位置（未配置时返回 null）。 */
    Location getLobby();

    /**
     * 某个游戏登记的所有场地及实时状态（只读快照，按场地名排序）。
     * 街机派对用它在多场地游戏里挑空场；旧版主城没实现时返回空列表。
     *
     * @param game 游戏显示名（与 {@link #setArena} 用的名字一致）
     */
    default List<ArenaStatus> arenas(String game) {
        return List.of();
    }
}
