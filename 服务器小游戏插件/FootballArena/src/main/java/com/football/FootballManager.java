package com.football;

import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import com.hub.util.LiveSidebar;
import com.hub.util.StartCountdown;
import com.hub.util.SetupMode;
import com.hub.util.WaitCountdown;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 绿茵足球核心：排队选队、开赛分配、开球/踢球/进球、计时与金球、战绩。
 * <p>
 * 阶段流转：WAITING（等待选队）→ 开赛准备 prepareMatch（分队/传送/发装备）→ StartCountdown 倒计时
 * → launchMatch 开计时并开球 → KICKOFF（球冻在开球点、开球方拿力度靴）→ PLAY（自由比赛）
 * → 进球 GOAL_PAUSE（3 秒定格）→ KICKOFF（失球方开球、全员回出生点）→ … → 结算回主城。
 * 没有出界：球到 pos1/pos2 的水平边界会被隐形墙按 bounce 弹回（球门区域不设墙，球能飞进门）；
 * 球与玩家之间也没有实体碰撞，只有左键击球能改变球速。
 */
public class FootballManager {

    private static final String GAME_NAME = "绿茵足球";
    private static final long GOAL_PAUSE_MS = 3000L;
    private static final int KICKOFF_TIMEOUT_S = 10;

    private final FootballPlugin plugin;
    private final LiveSidebar liveSidebar;
    private int boardTick;
    private final Map<String, FootballArena> arenas = new LinkedHashMap<>();
    private final Map<UUID, String> playerArena = new LinkedHashMap<>();
    private final Map<String, FootballBall> balls = new LinkedHashMap<>();      // 场名 -> 球
    private final Map<String, BukkitTask> pauseTasks = new LinkedHashMap<>();   // 场名 -> 进球定格任务
    private final Map<String, Long> kickoffDeadline = new LinkedHashMap<>();    // 场名 -> 开球超时时刻
    private final Map<UUID, Long> sprintCooldown = new LinkedHashMap<>();       // 玩家 -> 冲刺冷却到点
    private final Map<UUID, Long> jumpCooldown = new LinkedHashMap<>();
    private BukkitTask ballTask; // 球物理 + 进球/出界判定 + 动作栏（全局一个，轮询所有对局）

    private YamlConfiguration data;
    private final FootballItems items;
    private final SetupMode setup;
    private final Map<UUID, FootballArena> setupTargets = new LinkedHashMap<>();
    private final Map<String, List<Location>> joinSigns = new LinkedHashMap<>();
    private final Map<UUID, String> pendingSign = new LinkedHashMap<>();
    private final Map<String, WaitCountdown> waits = new LinkedHashMap<>();

    public FootballManager(FootballPlugin plugin) {
        this.plugin = plugin;
        this.liveSidebar = new LiveSidebar(plugin);
        this.items = new FootballItems(plugin);
        this.setup = new SetupMode(plugin, "fb-setup-tool");
    }

    public FootballItems items() {
        return items;
    }

    /** 有没有玩家在场（等待室也算）—— /fb reload 的判定。 */
    public boolean hasPlayers() {
        return !playerArena.isEmpty();
    }

    /** reload：重读 config.yml 的数值项；场地 / 告示牌等结构仍以启动时载入的为准。 */
    public void reload() {
        plugin.reloadConfig();
    }

    public void load() {
        startCountdown();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("arenas");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                arenas.put(name, FootballArena.load(name, sec.getConfigurationSection(name)));
            }
        }
        // 战绩（唯一文件 plugins/MiniGameLib/players.yml，命名空间 football；新插件无旧 data.yml）
        data = PlayerStats.load("football", new File(plugin.getDataFolder(), "data.yml"));
        ConfigurationSection js = plugin.getConfig().getConfigurationSection("join-signs");
        if (js != null) {
            for (String name : js.getKeys(false)) {
                List<?> list = js.getList(name);
                if (list == null) {
                    continue;
                }
                List<Location> locs = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Location loc) {
                        locs.add(loc);
                    }
                }
                if (!locs.isEmpty()) {
                    joinSigns.put(name, locs);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (FootballArena a : arenas.values()) {
                reportArena(a);
            }
        }, 40L);
        startBallTask();
    }

    /** 关服兜底：清掉所有场的展示实体球（球实体 setPersistent(false) 不落盘，这里防热重载残留）。 */
    public void cleanupBalls() {
        for (FootballBall ball : balls.values()) {
            ball.remove();
        }
        balls.clear();
    }

    public void saveAll() {
        ConfigurationSection sec = plugin.getConfig().createSection("arenas");
        for (Map.Entry<String, FootballArena> e : arenas.entrySet()) {
            e.getValue().save(sec.createSection(e.getKey()));
        }
        ConfigurationSection js = plugin.getConfig().createSection("join-signs");
        for (Map.Entry<String, List<Location>> e : joinSigns.entrySet()) {
            js.set(e.getKey(), e.getValue());
        }
        plugin.saveConfig();
        saveData();
        reportAllArenas();
    }

    private void reportAllArenas() {
        if (!plugin.isEnabled()) {
            return;
        }
        for (FootballArena a : arenas.values()) {
            reportArena(a);
        }
    }

    private void saveData() {
        if (data == null) {
            return;
        }
        try {
            PlayerStats.save("football", data);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("保存战绩失败：" + ex.getMessage());
        }
    }

    public FootballArena getArena(String name) {
        return arenas.get(name);
    }

    public Map<String, FootballArena> getArenas() {
        return arenas;
    }

    public boolean createArena(String name) {
        if (arenas.containsKey(name)) {
            return false;
        }
        arenas.put(name, new FootballArena(name));
        return true;
    }

    public boolean removeArena(String name) {
        FootballArena a = arenas.get(name);
        if (a == null) {
            return false;
        }
        if (a.getState() != FootballArena.State.WAITING || !a.getPlayers().isEmpty()) {
            return false;
        }
        arenas.remove(name);
        WaitCountdown w = waits.remove(name);
        if (w != null) {
            w.cancel();
        }
        FootballBall ball = balls.remove(name);
        if (ball != null) {
            ball.remove();
        }
        plugin.getConfig().set("arenas." + name, null);
        joinSigns.remove(name);
        plugin.getConfig().set("join-signs." + name, null);
        plugin.saveConfig();
        HubAPI.removeArena(GAME_NAME, name);
        return true;
    }

    public boolean renameArena(String oldName, String newName) {
        if (oldName == null || oldName.equals(newName) || !validName(newName) || arenas.containsKey(newName)) {
            return false;
        }
        FootballArena a = arenas.get(oldName);
        if (a == null || a.getState() == FootballArena.State.RUNNING) {
            return false;
        }
        arenas.remove(oldName);
        a.setName(newName);
        arenas.put(newName, a);
        for (Map.Entry<UUID, String> e : playerArena.entrySet()) {
            if (e.getValue().equals(oldName)) {
                e.setValue(newName);
            }
        }
        for (Map.Entry<UUID, String> e : pendingSign.entrySet()) {
            if (e.getValue().equals(oldName)) {
                e.setValue(newName);
            }
        }
        WaitCountdown wt = waits.remove(oldName);
        if (wt != null) {
            waits.put(newName, wt);
        }
        FootballBall ball = balls.remove(oldName);
        if (ball != null) {
            balls.put(newName, ball);
        }
        List<Location> locs = joinSigns.remove(oldName);
        if (locs != null) {
            joinSigns.put(newName, locs);
            refreshJoinSigns(newName);
        }
        saveAll();
        HubAPI.removeArena(GAME_NAME, oldName);
        reportArena(a);
        return true;
    }

    /** 场地名校验：1~20 字符，仅中英文/数字/下划线/短横线（与其它游戏一致）。 */
    public static boolean validName(String name) {
        if (name == null || name.isEmpty() || name.length() > 20) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private void reportArena(FootballArena a) {
        boolean playing = a.getState() == FootballArena.State.RUNNING;
        String extra = playing ? "比分 " + a.getScoreRed() + ":" + a.getScoreBlue() : null;
        HubAPI.setArena(GAME_NAME, a.getName(),
                playing ? GameState.PLAYING : GameState.WAITING, a.getPlayers().size(), extra);
        HubAPI.setArenaSpectator(GAME_NAME, a.getName(), a.getCenter());
    }

    // ---------- 玩家会话 ----------

    public String arenaOf(UUID id) {
        return playerArena.get(id);
    }

    public boolean join(Player p, String arenaName) {
        FootballArena a = arenas.get(arenaName);
        if (a == null) {
            msg(p, "&c场 &e" + arenaName + "&c 不存在。");
            return false;
        }
        UUID id = p.getUniqueId();
        if (playerArena.containsKey(id)) {
            msg(p, "&c你已在其他场。");
            return false;
        }
        if (a.getState() == FootballArena.State.RUNNING) {
            msg(p, "&c该场正在游戏中。");
            return false;
        }
        if (!a.isReady()) {
            msg(p, "&c该场未配置完成，还缺：" + String.join("、", a.missingPoints()));
            return false;
        }
        int max = plugin.getConfig().getInt("max-players", 10);
        if (a.getPlayers().size() >= max) {
            msg(p, "&c人数已满（&e" + max + "&c）。");
            return false;
        }
        playerArena.put(id, arenaName);
        a.getPlayers().add(id);
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        Location lobby = a.getLobbyPoint() != null ? a.getLobbyPoint() : a.getCenter();
        p.teleport(lobby);
        HubAPI.setGame(id, GAME_NAME);
        items.giveWaitingItems(p, p.isOp() || p.hasPermission("fb.admin"));
        wait(a).giveReadyItem(p);
        msg(p, "&a你已加入 &e" + arenaName + "&a，右键 &c红队旗 &f/ &9蓝队旗 &f选队，开赛后自动分到人少的一队。");
        broadcast(a, "&e" + p.getName() + "&f 加入了（&e" + a.getPlayers().size() + "&f 人，红 "
                + a.getTeamRed().size() + "/蓝 " + a.getTeamBlue().size() + "）");
        HubAPI.announceJoin(plugin.getName(), p.getName(), a.getName());
        wait(a).update();
        reportArena(a);
        return true;
    }

    public void leave(Player p) {
        leave(p, false);
    }

    /** silent=true 用于主城回主城回调（不在场中时别刷提示，见 docs/开发须知.md 第 5 节）。 */
    public void leave(Player p, boolean silent) {
        UUID id = p.getUniqueId();
        String name = playerArena.remove(id);
        items.removeJerseyItem(p); // 不管在不在场，都把锁死的队服道具清掉
        if (name == null) {
            if (!silent) {
                msg(p, "&c你不在任何场中。");
            }
            return;
        }
        FootballArena a = arenas.get(name);
        if (a != null) {
            a.getPlayers().remove(id);
            a.removeFromTeams(id);
            clearTeamColor(p);
            sprintCooldown.remove(id);
            jumpCooldown.remove(id);
            HubAPI.clearGame(id);
            broadcast(a, "&e" + p.getName() + "&f 离开了（剩 &e" + a.getPlayers().size() + "&f 人）");
            if (a.getState() == FootballArena.State.RUNNING) {
                if (a.getTeamRed().isEmpty() || a.getTeamBlue().isEmpty()) {
                    // 一队走光：另一队直接获胜
                    FootballArena.Team winner = a.getTeamRed().isEmpty()
                            ? FootballArena.Team.BLUE : FootballArena.Team.RED;
                    endMatch(a, winner);
                } else {
                    reportArena(a);
                }
            } else {
                reportArena(a);
                wait(a).update();
            }
            // 倒计时期间离场：重建倒计时，把离场玩家从冻结名单里摘出去（否则回大厅还动不了）
            restartStartCountdown(a);
        }
        p.setGameMode(GameMode.SURVIVAL);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        HubAPI.teleportToLobby(p);
        msg(p, "&7你已离开。");
    }

    /** 等待阶段选队（旗帜）。开赛后不可再选。 */
    public void pickTeam(Player p, FootballArena.Team team) {
        String name = playerArena.get(p.getUniqueId());
        if (name == null) {
            return;
        }
        FootballArena a = arenas.get(name);
        if (a == null || a.getState() != FootballArena.State.WAITING) {
            return;
        }
        a.assignTeam(p.getUniqueId(), team);
        HubAPI.setTabNameColor(p.getUniqueId(), teamColorCode(team));
        msg(p, "&a你选择了 " + team.display() + "&a（当前 红 " + a.getTeamRed().size()
                + " : 蓝 " + a.getTeamBlue().size() + "&a），开赛时人数不平衡会自动调剂。");
    }

    // ---------- 开赛 ----------

    public boolean start(Player op, String arenaName) {
        FootballArena a = arenas.get(arenaName);
        if (a == null) {
            msg(op, "&c场不存在。");
            return false;
        }
        if (a.getState() == FootballArena.State.RUNNING) {
            msg(op, "&c该场已在倒计时或游戏中。");
            return false;
        }
        int min = plugin.getConfig().getInt("min-players", 2);
        if (a.getPlayers().size() < min) {
            msg(op, "&c人数不足（至少 &e" + min + "&c 人，当前 &e" + a.getPlayers().size() + "&c）。");
            return false;
        }
        startMatch(a);
        return true;
    }

    private StartCountdown startCountdown;

    private StartCountdown startCountdown() {
        if (startCountdown == null) {
            startCountdown = new StartCountdown(plugin);
        }
        return startCountdown;
    }

    /**
     * 倒计时期间有人离场时重建倒计时：StartCountdown 的开局冻结名单在 begin 时就固定了，
     * 不重建的话离场玩家回大厅后要一直冻到倒计时结束。
     */
    private void restartStartCountdown(FootballArena a) {
        if (startCountdown == null || !startCountdown.isRunning(a.getName())) {
            return;
        }
        startCountdown.cancel(a.getName());
        if (a.getState() != FootballArena.State.RUNNING || a.getPlayers().isEmpty()) {
            return;
        }
        if (!startCountdown.begin(a.getName(), new ArrayList<>(a.getPlayers()), () -> launchMatch(a))) {
            launchMatch(a);
        }
    }

    /** 开赛倒计时（已分队、已传送、冻结中）：这期间不让提前用冲刺/高跳道具。 */
    private boolean inStartCountdown(FootballArena a) {
        return startCountdown != null && startCountdown.isRunning(a.getName());
    }

    /**
     * 开局入口：先把队伍分好、把所有人传送到各自出生点、发好装备，再开始倒计时；
     * 倒计时结束（{@link #launchMatch}）才真正开球并启动比赛计时。
     * <p>
     * 旧顺序是"倒计时 → 分队/传送"：倒计时期间玩家还挤在等待点、队伍也没最终确定，
     * 看不出自己分到哪队（2026-11-15 反馈）。现在倒计时一开始就已经站在本队出生点、穿好队服。
     */
    private void startMatch(FootballArena a) {
        if (a.getState() == FootballArena.State.RUNNING) {
            return; // 已在倒计时/对局中，重复触发（连点开始纸）不再开第二次
        }
        prepareMatch(a);
        if (startCountdown().begin(a.getName(), new ArrayList<>(a.getPlayers()), () -> launchMatch(a))) {
            return;
        }
        launchMatch(a); // 倒计时被关掉（countdown.enabled=false）或全局抑制时直接开球
    }

    /** 开赛准备（倒计时之前）：分队调平 → 传送出生点 → 发对局装备/队色/比分板。 */
    private void prepareMatch(FootballArena a) {
        a.setState(FootballArena.State.RUNNING);
        a.resetMatch();
        cancelWait(a);
        balanceTeams(a);
        reportArena(a);
        for (UUID id : a.getPlayers()) {
            HubAPI.setPlaying(id, GAME_NAME);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(20);
                p.setFoodLevel(20);
                p.setFallDistance(0);
            }
        }
        equipAll(a);
        refreshLiveBoard(a);
        resetPositions(a);
        plugin.getLogger().info("比赛准备：" + a.getName() + "（红 " + a.getTeamRed().size()
                + " vs 蓝 " + a.getTeamBlue().size() + "），开始倒计时");
    }

    /** 倒计时结束：正式开球并开始比赛计时（比赛时间从这一刻算起，倒计时不吃比赛时间）。 */
    private void launchMatch(FootballArena a) {
        if (a.getState() != FootballArena.State.RUNNING) {
            return; // 倒计时期间人走光/场被结算，别再开球
        }
        int seconds = plugin.getConfig().getInt("match-seconds", 300);
        a.setMatchTotalSeconds(seconds);
        a.setMatchEndMs(System.currentTimeMillis() + seconds * 1000L);
        plugin.getLogger().info("比赛开始：" + a.getName() + "（红 " + a.getTeamRed().size()
                + " vs 蓝 " + a.getTeamBlue().size() + "）");
        broadcast(a, "&6&l========== 比赛开始 ==========");
        broadcast(a, "&c红队 " + a.getTeamRed().size() + " 人 &fVS &9蓝队 " + a.getTeamBlue().size() + " 人"
                + "&7（先到 " + plugin.getConfig().getInt("goals-to-win", 3)
                + " 球或 " + seconds / 60 + " 分钟）");
        // 随机先开球的一方
        a.setKickoffTeam(java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                ? FootballArena.Team.RED : FootballArena.Team.BLUE);
        spawnBall(a, a.getCenter());
        // 装备在 prepareMatch 已发好，这里只补一次队服/队色（倒计时期间被扒甲也能穿回）
        refreshLiveBoard(a);
        resetPositions(a);
        startKickoff(a);
    }

    /**
     * 开赛分配：已选队的保留；没选的进人少的一队；一边多出超过 1 人时把"后加入大队"的人调剂到小队
     * （保证两边人数差 ≤ 1，即每边不超过总人数的一半向上取整）。
     */
    private void balanceTeams(FootballArena a) {
        List<UUID> unassigned = new ArrayList<>();
        for (UUID id : a.getPlayers()) {
            if (a.teamOf(id) == null) {
                unassigned.add(id);
            }
        }
        for (UUID id : unassigned) {
            FootballArena.Team to = a.getTeamRed().size() <= a.getTeamBlue().size()
                    ? FootballArena.Team.RED : FootballArena.Team.BLUE;
            a.assignTeam(id, to);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                msg(p, "&7你被自动分到了 " + to.display() + "&7。");
            }
        }
        while (Math.abs(a.getTeamRed().size() - a.getTeamBlue().size()) > 1) {
            FootballArena.Team big = a.getTeamRed().size() > a.getTeamBlue().size()
                    ? FootballArena.Team.RED : FootballArena.Team.BLUE;
            List<UUID> bigTeam = a.teamOf(big);
            UUID moved = bigTeam.get(bigTeam.size() - 1);
            a.assignTeam(moved, big.other());
            Player p = Bukkit.getPlayer(moved);
            if (p != null) {
                msg(p, "&e为了平衡人数，你被调剂到了 " + big.other().display() + "&e。");
            }
            broadcast(a, "&7人数平衡：" + big.display() + "&7 的 &e"
                    + nameOf(moved) + "&7 调剂到 " + big.other().display());
        }
        // 队色：记分板队伍染头顶名 + HubAPI.setTabNameColor 染 tab 名字（只染色不设前缀，不碰 HubCore 的 tab 前缀）
        for (UUID id : a.getTeamRed()) {
            applyTeamColor(Bukkit.getPlayer(id), NamedTextColor.RED);
        }
        for (UUID id : a.getTeamBlue()) {
            applyTeamColor(Bukkit.getPlayer(id), NamedTextColor.BLUE);
        }
    }

    /** 全员发对局装备（按队伍颜色）。 */
    private void equipAll(FootballArena a) {
        for (UUID id : a.getTeamRed()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                items.giveMatchKit(p, FootballArena.Team.RED);
            }
        }
        for (UUID id : a.getTeamBlue()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                items.giveMatchKit(p, FootballArena.Team.BLUE);
            }
        }
    }

    /** 全员传回本队出生点（进球后的重新开球用）。 */
    private void resetPositions(FootballArena a) {
        for (UUID id : a.getTeamRed()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && a.getSpawnRed() != null) {
                p.setFallDistance(0);
                p.teleport(a.getSpawnRed());
            }
        }
        for (UUID id : a.getTeamBlue()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && a.getSpawnBlue() != null) {
                p.setFallDistance(0);
                p.teleport(a.getSpawnBlue());
            }
        }
    }

    /** 在指定点生成球（读 ball 配置段：材质/大小；材质必须是方块，BlockDisplay 用）。 */
    private void spawnBall(FootballArena a, Location at) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("ball.material", "WHITE_CONCRETE"));
        if (material == null || !material.isBlock()) {
            material = Material.WHITE_CONCRETE;
        }
        double scale = plugin.getConfig().getDouble("ball.scale", 0.9);
        FootballBall ball = balls.computeIfAbsent(a.getName(), n -> new FootballBall(plugin));
        ball.spawn(at, material, scale);
    }

    // ---------- 开球 ----------

    /** 开始一次开球：球回中央开球点，全员回出生点，开球方全队拿力度靴。 */
    private void startKickoff(FootballArena a) {
        cancelPause(a);
        a.setPhase(FootballArena.Phase.KICKOFF);
        a.setLastTouch(null, null);
        FootballBall ball = balls.get(a.getName());
        Location spot = a.getCenter();
        if (ball != null && spot != null) {
            ball.freeze(spot);
            ball.nudgeToGround();
        }
        resetPositions(a);
        // 开球方全队拿力度靴（力度 1~4），对方保持普通装备但踢球无效
        FootballArena.Team kt = a.getKickoffTeam();
        for (UUID id : a.teamOf(kt)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                items.giveKickoffBoots(p);
                msg(p, kt.display() + "&a 开球：&f选一只开球靴，&a左键&a把球往对方半场开！力度越大越高越远。");
            }
        }
        broadcast(a, kt.display() + "&e 开球！球在中央，等待 "
                + KICKOFF_TIMEOUT_S + " 秒内不开自动开出。");
        kickoffDeadline.put(a.getName(), System.currentTimeMillis() + KICKOFF_TIMEOUT_S * 1000L);
    }

    /** 开球被踢出（或超时自动开出）后的收尾：换回普通装备、阶段切 PLAY。 */
    private void releaseKickoff(FootballArena a) {
        if (a.getPhase() != FootballArena.Phase.KICKOFF) {
            return;
        }
        a.setPhase(FootballArena.Phase.PLAY);
        kickoffDeadline.remove(a.getName());
        for (UUID id : a.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                items.clearKickoffBoots(p);
                items.giveMatchKit(p, a.teamOf(id));
            }
        }
    }

    // ---------- 踢球 ----------

    /**
     * 玩家左键挥动踢球。kickoffPower > 0 = 手里拿的是开球靴（只在开球阶段、开球方有效）；
     * 否则任何物品（含空手）都是"击球"：水平 1.0 + 垂直 0.45 的平抽，无冷却。
     */
    public void handleKick(Player p, int kickoffPower) {
        String name = playerArena.get(p.getUniqueId());
        if (name == null) {
            return;
        }
        FootballArena a = arenas.get(name);
        if (a == null || a.getState() != FootballArena.State.RUNNING) {
            return;
        }
        FootballBall ball = balls.get(name);
        if (ball == null || !ball.isAlive()) {
            return;
        }
        FootballArena.Team team = a.teamOf(p.getUniqueId());
        if (team == null) {
            return;
        }
        FootballArena.Phase phase = a.getPhase();
        double range = plugin.getConfig().getDouble("kick.range", 3.0);
        double dist = ball.distanceTo(p.getLocation().add(0, 1.0, 0));
        if (dist > range) {
            return; // 挥空：没够到球，不作任何提示（否则左键空气刷屏）
        }
        Vector dir = ball.location().toVector().subtract(p.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.0001) {
            dir = p.getLocation().getDirection().setY(0);
        }
        dir.normalize();

        if (kickoffPower > 0) {
            if (phase != FootballArena.Phase.KICKOFF) {
                return;
            }
            if (team != a.getKickoffTeam()) {
                msg(p, "&c现在由 " + a.getKickoffTeam().display() + "&c 开球，先防守一下！");
                return;
            }
            double min = plugin.getConfig().getDouble("kick.kickoff-min-power", 0.6);
            double max = plugin.getConfig().getDouble("kick.kickoff-max-power", 2.2);
            double ratio = plugin.getConfig().getDouble("kick.kickoff-lift-ratio", 0.45);
            // 力度沿用旧 8 档刻度（除以 7）：只发 1~4 档，但 1~4 的数值与旧版完全一致；
            // 不能按 1~4 直接铺满 min~max，否则力度 4 = 2.2 又高又陡，球近乎垂直飞
            int level = Math.max(1, Math.min(FootballItems.KICKOFF_MAX_LEVEL, kickoffPower));
            double power = min + (max - min) * (level - 1) / 7.0;
            Vector v = dir.clone().multiply(power);
            v.setY(power * ratio);
            ball.setVelocity(v);
            a.setLastTouch(p.getUniqueId(), team);
            releaseKickoff(a);
            kickEffects(p, ball);
            broadcast(a, team.display() + "&e 的 &f" + p.getName() + "&e 开出球（力度 &f" + kickoffPower + "&e）！比赛开始！");
        } else {
            if (phase != FootballArena.Phase.PLAY) {
                return;
            }
            double power = plugin.getConfig().getDouble("kick.forward-power", 1.0);
            double lift = plugin.getConfig().getDouble("kick.forward-lift", 0.45);
            Vector v = dir.clone().multiply(power);
            v.setY(lift); // 半人高的抛物线：往前为主、带一点升力
            ball.setVelocity(v);
            a.setLastTouch(p.getUniqueId(), team);
            kickEffects(p, ball);
        }
    }

    /** 踢球反馈：挥手动作用 + 音效 + 球上粒子。 */
    private void kickEffects(Player p, FootballBall ball) {
        p.swingMainHand();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.3f);
        Location l = ball.location();
        if (l != null) {
            l.getWorld().spawnParticle(Particle.CRIT, l, 8, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // ---------- 冲刺 / 高跳 ----------

    public void useSprint(Player p) {
        String name = playerArena.get(p.getUniqueId());
        if (name == null) {
            return;
        }
        FootballArena a = arenas.get(name);
        if (a == null || a.getState() != FootballArena.State.RUNNING || inStartCountdown(a)) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = sprintCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            Chat.actionBar(p, "&7冲刺冷却中：" + ((until - now) / 1000 + 1) + "s");
            return;
        }
        int seconds = plugin.getConfig().getInt("sprint-seconds", 5);
        int cooldown = plugin.getConfig().getInt("sprint-cooldown", 5);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, false, true, true));
        sprintCooldown.put(p.getUniqueId(), now + (seconds + cooldown) * 1000L);
        p.playSound(p.getLocation(), Sound.ENTITY_RABBIT_JUMP, 0.8f, 1.4f);
        Chat.actionBar(p, "&b冲刺！" + seconds + " 秒速度提升");
    }

    public void useJump(Player p) {
        String name = playerArena.get(p.getUniqueId());
        if (name == null) {
            return;
        }
        FootballArena a = arenas.get(name);
        if (a == null || a.getState() != FootballArena.State.RUNNING || inStartCountdown(a)) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = jumpCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            Chat.actionBar(p, "&7高跳冷却中：" + ((until - now) / 1000 + 1) + "s");
            return;
        }
        int cooldown = plugin.getConfig().getInt("jump-cooldown", 5);
        Vector v = p.getVelocity();
        v.setY(0.95);
        p.setVelocity(v);
        p.setFallDistance(0); // 落地不掉血（对局本身也免伤，这里双保险）
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.25, 0.05, 0.25, 0.02);
        jumpCooldown.put(p.getUniqueId(), now + cooldown * 1000L);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.6f);
        Chat.actionBar(p, "&d高跳！");
    }

    // ---------- 球物理 + 判定（全局任务） ----------

    private void startBallTask() {
        if (ballTask != null) {
            return;
        }
        ballTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 4L, 1L);
    }

    private void tickAll() {
        updateLiveBoards();
        for (FootballArena a : arenas.values()) {
            if (a.getState() != FootballArena.State.RUNNING) {
                continue;
            }
            FootballBall ball = balls.get(a.getName());
            if (ball == null || !ball.isAlive()) {
                continue;
            }
            // 球与玩家之间没有任何实体碰撞：人站在球路上、贴着球都不会推球/挡球，
            // 只有左键击球（handleKick）才会改变球的速度。
            // 物理：每 2 tick 推进一步（带场地边界隐形墙，球到边线反弹）
            if (plugin.getConfig().getBoolean("ball.enabled-physics", true)) {
                if (Bukkit.getCurrentTick() % 2 == 0) {
                    ball.step(2,
                            plugin.getConfig().getDouble("ball.gravity", 0.08),
                            plugin.getConfig().getDouble("ball.drag", 0.985),
                            plugin.getConfig().getDouble("ball.bounce", 0.6),
                            plugin.getConfig().getDouble("ball.ground-friction", 0.86),
                            plugin.getConfig().getDouble("ball.rest-speed", 0.05),
                            boundaryOf(a, ball));
                }
            }
            if (a.getPhase() == FootballArena.Phase.PLAY) {
                // 进球：球门区域在边界墙上开了口，球能越过门线进网
                FootballArena.Team goal = a.goalAt(ball.location());
                if (goal != null) {
                    handleGoal(a, goal);
                    continue;
                }
            }
            recoverIfFellOutOfWorld(a, ball);
            if (a.getPhase() == FootballArena.Phase.KICKOFF
                    && kickoffDeadline.containsKey(a.getName())
                    && System.currentTimeMillis() > kickoffDeadline.get(a.getName())) {
                autoKickoff(a);
            }
            if (Bukkit.getCurrentTick() % 20 == 0) {
                tickActionBar(a);
            }
        }
    }

    /**
     * 给球物理用的场地水平边界（隐形墙）：球心到 pos1/pos2 围成的矩形边线就反弹。
     * 边界扣掉球半径，球的边缘正好贴线；球门区域不设墙（让球能越过门线进球）。
     */
    private FootballBall.Boundary boundaryOf(FootballArena a, FootballBall ball) {
        Location p1 = a.getPos1();
        Location p2 = a.getPos2();
        Location loc = ball.location();
        if (p1 == null || p2 == null || p1.getWorld() == null || loc == null || loc.getWorld() == null
                || !loc.getWorld().equals(p1.getWorld())) {
            return null;
        }
        double r = ball.getRadius();
        double minX = Math.min(p1.getX(), p2.getX()) + r;
        double maxX = Math.max(p1.getX(), p2.getX()) - r;
        double minZ = Math.min(p1.getZ(), p2.getZ()) + r;
        double maxZ = Math.max(p1.getZ(), p2.getZ()) - r;
        return new FootballBall.Boundary(minX, maxX, minZ, maxZ, l -> a.goalAt(l, r) != null);
    }

    /** 兜底：球穿过地板洞掉到世界最低高度以下时放回开球点（不算出界、不重置站位、不换开球方）。 */
    private void recoverIfFellOutOfWorld(FootballArena a, FootballBall ball) {
        Location loc = ball.location();
        if (loc == null || loc.getWorld() == null || a.getCenter() == null) {
            return;
        }
        if (loc.getY() < loc.getWorld().getMinHeight() + 1) {
            ball.freeze(a.getCenter());
            ball.nudgeToGround();
            broadcast(a, "&7球掉出场地，已放回开球点。");
        }
    }

    /** 进球：side = 被进球的球门（红门被进 = 蓝队得分）。 */
    private void handleGoal(FootballArena a, FootballArena.Team side) {
        FootballArena.Team scorer = side.other();
        a.addScore(scorer);
        FootballBall ball = balls.get(a.getName());
        if (ball != null) {
            Location l = ball.location();
            if (l != null) {
                l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1, 0, 0, 0, 0);
                l.getWorld().playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.6f);
            }
        }
        broadcast(a, "&6&l⚽ GOAL！ " + scorer.display() + "&e 攻破 " + side.display() + "&e 的球门！"
                + " 比分 &c" + a.getScoreRed() + " &f: &9" + a.getScoreBlue());
        for (UUID id : a.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Chat.title(p, "&6&l⚽ GOAL!", scorer.display() + "&e 得分！&c" + a.getScoreRed()
                        + " &f: &9" + a.getScoreBlue(), 2);
            }
        }
        reportArena(a);
        // 胜负判定：金球模式先进球直接结束；常规模式达到目标球数结束
        if (a.isGoldenGoal()) {
            endMatch(a, scorer);
            return;
        }
        int goalsToWin = plugin.getConfig().getInt("goals-to-win", 3);
        if (a.getScoreRed() >= goalsToWin || a.getScoreBlue() >= goalsToWin) {
            endMatch(a, a.getScoreRed() > a.getScoreBlue() ? FootballArena.Team.RED : FootballArena.Team.BLUE);
            return;
        }
        // GOAL_PAUSE 定格几秒后重置站位、由失球方开球
        a.setPhase(FootballArena.Phase.GOAL_PAUSE);
        FootballBall b = balls.get(a.getName());
        if (b != null) {
            b.freeze(null);
        }
        a.setKickoffTeam(side); // 失球方开球
        BukkitTask old = pauseTasks.remove(a.getName());
        if (old != null) {
            old.cancel();
        }
        pauseTasks.put(a.getName(), Bukkit.getScheduler().runTaskLater(plugin,
                () -> startKickoff(a), GOAL_PAUSE_MS / 50L));
    }

    /** 开球超时：自动往对方半场开出中等力度。 */
    private void autoKickoff(FootballArena a) {
        FootballArena.Team kt = a.getKickoffTeam();
        FootballBall ball = balls.get(a.getName());
        if (ball == null || !ball.isAlive() || a.getCenter() == null) {
            releaseKickoff(a);
            return;
        }
        // 朝对方球门方向开（球冻在中央开球点，方向以球当前位置为基准）
        Location goalCenter = goalCenterOf(a, kt.other());
        Location from = ball.location();
        Vector dir = goalCenter != null && from != null
                ? goalCenter.toVector().subtract(from.toVector()).setY(0)
                : new Vector(1, 0, 0);
        if (dir.lengthSquared() < 0.001) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();
        Vector v = dir.clone().multiply(plugin.getConfig().getDouble("kick.kickoff-min-power", 0.6) + 0.4);
        v.setY(0.35);
        ball.setVelocity(v);
        releaseKickoff(a);
        broadcast(a, "&7超时未开球，自动开出！");
    }

    private Location goalCenterOf(FootballArena a, FootballArena.Team team) {
        Location p1 = team == FootballArena.Team.RED ? a.getGoalRedPos1() : a.getGoalBluePos1();
        Location p2 = team == FootballArena.Team.RED ? a.getGoalRedPos2() : a.getGoalBluePos2();
        if (p1 == null || p2 == null) {
            return null;
        }
        return new Location(p1.getWorld(),
                (p1.getX() + p2.getX()) / 2, (p1.getY() + p2.getY()) / 2, (p1.getZ() + p2.getZ()) / 2);
    }

    /** 每秒动作栏：比分 + 剩余时间（/金球倒计时）。 */
    private void tickActionBar(FootballArena a) {
        long leftMs = a.getMatchEndMs() - System.currentTimeMillis();
        long left = Math.max(0, leftMs / 1000);
        String score = "&c红 " + a.getScoreRed() + " &f: &9蓝 " + a.getScoreBlue();
        String timePart;
        if (a.isGoldenGoal()) {
            timePart = "&6&l金球加时 &7先进球获胜 &e剩 " + left + "s";
        } else {
            timePart = "&7剩余 &e" + (left / 60) + ":" + String.format("%02d", left % 60);
        }
        String phaseHint = switch (a.getPhase()) {
            case KICKOFF -> {
                // 开球超时倒数（动作栏每秒刷新，到 0 由 autoKickoff 自动开出）
                long deadline = kickoffDeadline.getOrDefault(a.getName(),
                        System.currentTimeMillis() + KICKOFF_TIMEOUT_S * 1000L);
                long kickLeft = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
                yield "&e【开球倒计时 &f" + kickLeft + "s&e】";
            }
            case GOAL_PAUSE -> "&6【进球！】";
            default -> "";
        };
        for (UUID id : a.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                Chat.actionBar(p, score + " &8| " + timePart + " " + phaseHint);
            }
        }
        if (leftMs <= 0 && a.getPhase() == FootballArena.Phase.PLAY) {
            // 常规时间到：分出胜负 / 进入金球加时 / 平局
            if (a.isGoldenGoal()) {
                endMatch(a, null);
            } else if (a.leadingTeam() != null) {
                endMatch(a, a.leadingTeam());
            } else {
                int golden = plugin.getConfig().getInt("golden-goal-seconds", 120);
                a.setGoldenGoal(true);
                a.setMatchTotalSeconds(golden);
                a.setMatchEndMs(System.currentTimeMillis() + golden * 1000L);
                broadcast(a, "&6&l常规时间结束，比分相同！&e进入金球加时（" + golden / 60 + " 分钟，先进球获胜）");
            }
        }
    }

    // ---------- 结算 ----------

    private void updateLiveBoards() {
        if (++boardTick % 20 != 0) {
            return;
        }
        for (FootballArena a : arenas.values()) {
            if (a.getState() != FootballArena.State.RUNNING) {
                continue;
            }
            refreshLiveBoard(a);
        }
    }

    /**
     * 刷一场的比分板 + 队色高亮 + 队服。
     * <p>
     * 顺序很重要：{@link LiveSidebar#update} 会给对局玩家 {@code setScoreboard} 一块独立记分板，
     * 换完板再挂队伍色才有意义——发光描边的颜色取的是"客户端当前那块记分板"上的队伍色，
     * 只挂在主记分板上客户端根本看不到，发光会退化成白色（2026-11-15 实测反馈）。
     */
    private void refreshLiveBoard(FootballArena a) {
        List<LiveSidebar.Line> lines = new ArrayList<>();
        lines.add(new LiveSidebar.Line("§c红队", a.getScoreRed()));
        lines.add(new LiveSidebar.Line("§9蓝队", a.getScoreBlue()));
        liveSidebar.update("football:" + a.getName(), "&a&l绿茵足球 · 比分", a.getPlayers(), lines);
        refreshTeamColors();
        ensureJerseys(a);
    }

    /**
     * 对局玩家必须一直穿着本队队服、物品栏最后一格也必须放着本队「队服」展示道具：
     * 开赛发一次，之后每秒兜底补穿/补放（重生、被其它插件扒甲/清背包、玩家自己脱掉都算）。
     * 身上那件是真盔甲；最后一格那件带 PDC 标记，由 FootballListener 锁死不能穿、不能丢。
     */
    private void ensureJerseys(FootballArena a) {
        for (UUID id : a.getTeamRed()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                if (!items.wearingJersey(p, FootballArena.Team.RED)) {
                    items.wearJersey(p, FootballArena.Team.RED);
                }
                items.ensureJerseyItem(p, FootballArena.Team.RED);
            }
        }
        for (UUID id : a.getTeamBlue()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                if (!items.wearingJersey(p, FootballArena.Team.BLUE)) {
                    items.wearJersey(p, FootballArena.Team.BLUE);
                }
                items.ensureJerseyItem(p, FootballArena.Team.BLUE);
            }
        }
    }

    private void endMatch(FootballArena a, FootballArena.Team winner) {
        liveSidebar.hide("football:" + a.getName());
        // 倒计时中途结算：先把倒计时（含冻结）停掉，再把人送回主城
        if (startCountdown != null) {
            startCountdown.cancel(a.getName());
        }
        cancelPause(a);
        kickoffDeadline.remove(a.getName());
        a.setState(FootballArena.State.WAITING);
        // 注意：这里不能先 resetMatch，否则终场比分会被清成 0:0、胜方队伍也空了（战绩发不出去）。
        reportArena(a);
        FootballBall ball = balls.remove(a.getName());
        if (ball != null) {
            ball.remove();
        }
        broadcast(a, "&6&l========== 比赛结束 ==========");
        if (winner != null) {
            broadcast(a, winner.display() + " &e&l获胜！&7（终场比分 &c" + a.getScoreRed() + " &f: &9" + a.getScoreBlue() + "&7）");
            for (UUID id : new ArrayList<>(a.teamOf(winner))) {
                recordWin(id);
            }
        } else {
            broadcast(a, "&e平局！&7（终场比分 &c" + a.getScoreRed() + " &f: &9" + a.getScoreBlue() + "&7）");
        }
        broadcast(a, "&6==============================");
        for (UUID id : new ArrayList<>(a.getPlayers())) {
            Player p = Bukkit.getPlayer(id);
            clearTeamColor(p);
            sprintCooldown.remove(id);
            jumpCooldown.remove(id);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.SURVIVAL);
                p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                items.removeJerseyItem(p);
                HubAPI.clearGame(id);
                playerArena.remove(id);
                HubAPI.teleportToLobby(p);
            } else {
                HubAPI.clearGame(id);
                playerArena.remove(id);
            }
        }
        a.getPlayers().clear();
        a.clearTeams();   // 结算完再清队（胜场已按队伍发完）
        a.resetMatch();   // 清比分/阶段/计时，给下一场用
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        String name = playerArena.remove(id);
        p.setGameMode(GameMode.SURVIVAL);
        items.removeJerseyItem(p); // 掉线别把锁死的队服带进玩家存档
        if (name == null) {
            return;
        }
        FootballArena a = arenas.get(name);
        if (a == null) {
            return;
        }
        a.getPlayers().remove(id);
        a.removeFromTeams(id);
        clearTeamColor(p);
        sprintCooldown.remove(id);
        jumpCooldown.remove(id);
        HubAPI.clearGame(id);
        reportArena(a);
        if (a.getState() == FootballArena.State.RUNNING
                && (a.getTeamRed().isEmpty() || a.getTeamBlue().isEmpty())) {
            FootballArena.Team winner = a.getTeamRed().isEmpty()
                    ? FootballArena.Team.BLUE : FootballArena.Team.RED;
            endMatch(a, winner);
        } else if (a.getState() == FootballArena.State.WAITING) {
            wait(a).update();
        }
    }

    public void handleJoin(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        if (playerArena.containsKey(p.getUniqueId())) {
            return;
        }
        // 大厅/重进服的玩家身上不该留着对局里锁死的队服道具（崩溃/关服残留的这里清掉）
        items.removeJerseyItem(p);
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
            plugin.getLogger().info("玩家 " + p.getName() + " 加入时处于观战模式，已恢复为生存模式。");
        }
        java.util.Set<String> fbWorlds = new java.util.HashSet<>();
        for (FootballArena a : arenas.values()) {
            if (a.getCenter() != null && a.getCenter().getWorld() != null) {
                fbWorlds.add(a.getCenter().getWorld().getName());
            }
        }
        if (fbWorlds.contains(p.getWorld().getName())) {
            HubAPI.teleportToLobby(p);
            plugin.getLogger().info("玩家 " + p.getName() + " 不在场但位于对局世界，已送回主城。");
        }
    }

    // ---------- 等待倒计时 ----------

    private WaitCountdown wait(FootballArena a) {
        return waits.computeIfAbsent(a.getName(), n -> new WaitCountdown(plugin, new WaitCountdown.Host() {
            @Override
            public Collection<UUID> waitingPlayers() {
                return a.getPlayers();
            }

            @Override
            public int minPlayers() {
                return plugin.getConfig().getInt("min-players", 2);
            }

            @Override
            public int maxPlayers() {
                return plugin.getConfig().getInt("max-players", 10);
            }

            @Override
            public boolean hasOpQueued() {
                for (UUID id : a.getPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOp()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onTick(long secondsLeft) {
                for (UUID id : a.getPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        Chat.actionBar(p, "&6开始倒计时：&e" + secondsLeft + "s &8（红 "
                                + a.getTeamRed().size() + " / 蓝 " + a.getTeamBlue().size() + "）");
                    }
                }
            }

            @Override
            public void onMessage(String message) {
                broadcast(a, message);
            }

            @Override
            public void onStart() {
                startMatch(a);
            }
        }));
    }

    private void cancelWait(FootballArena a) {
        WaitCountdown w = waits.get(a.getName());
        if (w != null) {
            w.cancel();
        }
    }

    // ---------- 队色（记分板队伍，只染色不设前缀） ----------

    private static final String RED_TEAM = "fb_red";
    private static final String BLUE_TEAM = "fb_blue";

    /** 队伍对应的 tab 名字色号（legacy）：红=c，蓝=9。 */
    private static String teamColorCode(FootballArena.Team team) {
        return team == FootballArena.Team.RED ? "c" : "9";
    }

    /** 取得（必要时注册）某块记分板上的本插件队色队伍，并保证队伍颜色正确。 */
    private Team teamOn(Scoreboard sb, String teamName, NamedTextColor color) {
        Team team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.registerNewTeam(teamName);
        }
        team.color(color);
        return team;
    }

    /**
     * 把玩家挂进队色队伍：主记分板 + 玩家当前真正在看的那块记分板。
     * 只挂主记分板时，LiveSidebar 换过板的玩家发光是白的（见 {@link #refreshLiveBoard}）。
     */
    private void applyTeamColor(Player p, NamedTextColor color) {
        if (p == null || !p.isOnline()) {
            return;
        }
        String teamName = color == NamedTextColor.RED ? RED_TEAM : BLUE_TEAM;
        teamOn(Bukkit.getScoreboardManager().getMainScoreboard(), teamName, color).addEntry(p.getName());
        teamOn(p.getScoreboard(), teamName, color).addEntry(p.getName());
        // 队伍高亮：原版发光描边会取"客户端看到的记分板"上的队伍色（红/蓝），隔墙也能看见
        p.setGlowing(true);
        HubAPI.setTabNameColor(p.getUniqueId(), color == NamedTextColor.RED ? "c" : "9");
    }

    /**
     * 把每块在用的记分板上的 fb_red/fb_blue 对齐到真实队伍：
     * 记分板每被换一次（LiveSidebar 开赛/重开比分板、HubCore 刷新），队伍就要重挂一次。
     * 队伍名在一块记分板上是全局的，所以这里按"所有进行中的场"取并集，多个场同时开着也不会互相摘人。
     */
    private void refreshTeamColors() {
        Set<Scoreboard> boards = new LinkedHashSet<>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        Set<String> red = new LinkedHashSet<>();
        Set<String> blue = new LinkedHashSet<>();
        for (FootballArena a : arenas.values()) {
            if (a.getState() != FootballArena.State.RUNNING) {
                continue;
            }
            collectTeam(a.getTeamRed(), red, boards);
            collectTeam(a.getTeamBlue(), blue, boards);
        }
        for (Scoreboard sb : boards) {
            syncTeam(sb, RED_TEAM, NamedTextColor.RED, red);
            syncTeam(sb, BLUE_TEAM, NamedTextColor.BLUE, blue);
        }
    }

    private void collectTeam(Collection<UUID> ids, Set<String> names, Set<Scoreboard> boards) {
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                names.add(p.getName());
                boards.add(p.getScoreboard());
                // 发光标志偶尔会被别的流程清掉（重生/换模式），每秒兜底点亮一次
                p.setGlowing(true);
            }
        }
    }

    /** 队伍内容对齐到"该在队里的人"：多出来的摘掉，少的补上。 */
    private void syncTeam(Scoreboard sb, String teamName, NamedTextColor color, Set<String> wanted) {
        Team team = teamOn(sb, teamName, color);
        for (String entry : new ArrayList<>(team.getEntries())) {
            if (!wanted.contains(entry)) {
                team.removeEntry(entry);
            }
        }
        for (String entry : wanted) {
            team.addEntry(entry);
        }
    }

    private void clearTeamColor(Player p) {
        if (p == null) {
            return;
        }
        // 发光标志是实体数据，退出/结算/掉线都要主动摘掉（HubCore 回主城也会兜底清一次）
        if (p.isOnline()) {
            p.setGlowing(false);
            removeFromTeams(p.getScoreboard(), p.getName());
        }
        HubAPI.setTabNameColor(p.getUniqueId(), null);
        removeFromTeams(Bukkit.getScoreboardManager().getMainScoreboard(), p.getName());
    }

    private void removeFromTeams(Scoreboard sb, String entry) {
        for (String teamName : List.of(RED_TEAM, BLUE_TEAM)) {
            Team team = sb.getTeam(teamName);
            if (team == null) {
                continue;
            }
            team.removeEntry(entry);
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }

    // ---------- 战绩 ----------

    private void recordWin(UUID id) {
        if (data == null) {
            return;
        }
        data.set("wins." + id, getWins(id) + 1);
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            data.set("names." + id, p.getName());
        }
        saveData();
    }

    public int getWins(UUID id) {
        return data == null ? 0 : data.getInt("wins." + id, 0);
    }

    /** OP 清空全部战绩。 */
    public void resetStats() {
        if (data != null) {
            for (String k : new ArrayList<>(data.getKeys(false))) {
                data.set(k, null);
            }
            saveData();
        }
    }

    // ---------- 配置模式 ----------

    private final SetupMode.Handler setupHandler = new SetupMode.Handler() {
        @Override
        public void onTool(Player p, String arenaName, String toolId, Action action, Block clicked) {
            FootballArena a = setupTargets.get(p.getUniqueId());
            if (a == null) {
                a = arenas.get(arenaName);
            }
            if (a == null) {
                exitSetup(p);
                return;
            }
            String name = a.getName();
            boolean left = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR;
            boolean right = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;
            switch (toolId) {
                case "pos" -> {
                    if (left && clicked != null) {
                        a.setPos1(clicked.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 范围角1：&f" + shortLoc(clicked.getLocation()));
                        saveAll();
                    } else if (right && clicked != null) {
                        a.setPos2(clicked.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 范围角2：&f" + shortLoc(clicked.getLocation()));
                        saveAll();
                    }
                }
                case "center" -> {
                    if (right) {
                        a.setCenter(p.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 开球点：&f" + shortLoc(p.getLocation()));
                        saveAll();
                    }
                }
                case "spawn-red" -> {
                    if (right) {
                        a.setSpawnRed(p.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 红队出生点：&f" + shortLoc(p.getLocation()));
                        saveAll();
                    }
                }
                case "spawn-blue" -> {
                    if (right) {
                        a.setSpawnBlue(p.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 蓝队出生点：&f" + shortLoc(p.getLocation()));
                        saveAll();
                    }
                }
                case "goal-red" -> {
                    if (left && clicked != null) {
                        a.setGoalRedPos1(clicked.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 红门角1：&f" + shortLoc(clicked.getLocation())
                                + (a.getGoalRedPos2() != null ? "（红门已定）" : "（右键定红门角2）"));
                        saveAll();
                    } else if (right && clicked != null) {
                        a.setGoalRedPos2(clicked.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 红门角2：&f" + shortLoc(clicked.getLocation())
                                + (a.getGoalRedPos1() != null ? "（红门已定）" : "（左键定红门角1）"));
                        saveAll();
                    }
                }
                case "goal-blue" -> {
                    if (left && clicked != null) {
                        a.setGoalBluePos1(clicked.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 蓝门角1：&f" + shortLoc(clicked.getLocation())
                                + (a.getGoalBluePos2() != null ? "（蓝门已定）" : "（右键定蓝门角2）"));
                        saveAll();
                    } else if (right && clicked != null) {
                        a.setGoalBluePos2(clicked.getLocation());
                        msg(p, "&a已设置 &e" + name + "&a 蓝门角2：&f" + shortLoc(clicked.getLocation())
                                + (a.getGoalBluePos1() != null ? "（蓝门已定）" : "（左键定蓝门角1）"));
                        saveAll();
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void onSave(Player p, String arenaName) {
            FootballArena a = setupTargets.get(p.getUniqueId());
            saveAll();
            msg(p, "&a已保存场 &e" + (a != null ? a.getName() : arenaName) + "&a。");
        }
    };

    private List<SetupMode.Tool> setupTools() {
        return List.of(
                SetupMode.Tool.both(Material.STICK, "pos", "&b场地范围", "&7左键=角1&8/&7右键=角2"),
                SetupMode.Tool.right(Material.GOLDEN_HOE, "center", "&e开球点", "&7右键=球放在这"),
                SetupMode.Tool.right(Material.RED_WOOL, "spawn-red", "&c红队出生点", "&7右键=红队站这"),
                SetupMode.Tool.right(Material.BLUE_WOOL, "spawn-blue", "&9蓝队出生点", "&7右键=蓝队站这"),
                SetupMode.Tool.both(Material.BRICKS, "goal-red", "&c红队球门", "&7左键=角1&8/&7右键=角2&7（红队守）"),
                SetupMode.Tool.both(Material.STONE_BRICKS, "goal-blue", "&9蓝队球门", "&7左键=角1&8/&7右键=角2&7（蓝队守）"),
                SetupMode.Tool.right(Material.EMERALD, "save", "&6保存场", "&7右键=保存当前配置"),
                SetupMode.Tool.right(Material.BARRIER, "exit", "&c退出配置模式", "&7右键=结束并恢复背包"));
    }

    public boolean isInSetup(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && !setup.isEditing(p)) {
            setupTargets.remove(id);
        }
        return setupTargets.containsKey(id);
    }

    public boolean enterSetup(Player p, String arenaName) {
        FootballArena a = arenas.get(arenaName);
        if (a == null) {
            msg(p, "&c场 &e" + arenaName + "&c 不存在。");
            return false;
        }
        if (setup.isEditing(p)) {
            msg(p, "&c你已在配置模式中，先退出（屏障工具）再进入其他场。");
            return false;
        }
        setupTargets.put(p.getUniqueId(), a);
        setup.enter(p, arenaName, setupTools(), setupHandler);
        msg(p, "&a已进入 &e" + arenaName + "&a 的配置模式：");
        msg(p, "&b木棍&7 场地范围（左1右2） &e金锄头&7 开球点");
        msg(p, "&c红羊毛&7 红队出生 &9蓝羊毛&7 蓝队出生");
        msg(p, "&c红砖&7 红门（左1右2） &f石砖&7 蓝门（左1右2）");
        msg(p, "&6绿宝石&7 保存  &c屏障&7 退出");
        msg(p, "&7提示：球门要圈住门框内的空间（含底线），球心进圈即判进球。");
        return true;
    }

    public void exitSetup(Player p) {
        setupTargets.remove(p.getUniqueId());
        setup.exit(p);
        msg(p, "&7已退出配置模式，背包已恢复。");
    }

    public boolean handleSetupTool(Player p, Action action, ItemStack hand, Block clicked) {
        return setup.handleInteract(p, action, hand, clicked);
    }

    // ---------- 加入告示牌 ----------

    public void placeJoinSign(Player p, String arenaName) {
        if (arenas.get(arenaName) == null) {
            msg(p, "&c场不存在。");
            return;
        }
        pendingSign.put(p.getUniqueId(), arenaName);
        msg(p, "&a请右键点击一个告示牌，将其变为 &e" + arenaName + "&a 的加入牌。");
    }

    public boolean handleSignBind(Player p, Location loc) {
        String arenaName = pendingSign.remove(p.getUniqueId());
        if (arenaName == null) {
            return false;
        }
        joinSigns.computeIfAbsent(arenaName, k -> new ArrayList<>()).removeIf(x -> sameBlock(x, loc));
        joinSigns.computeIfAbsent(arenaName, k -> new ArrayList<>()).add(loc);
        refreshJoinSigns(arenaName);
        saveAll();
        msg(p, "&a已将告示牌变为 &e" + arenaName + "&a 的加入牌。");
        return true;
    }

    public boolean isJoinSign(Location loc) {
        for (List<Location> locs : joinSigns.values()) {
            for (Location l : locs) {
                if (sameBlock(l, loc)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onSignClick(Player p, Location loc) {
        for (Map.Entry<String, List<Location>> e : joinSigns.entrySet()) {
            for (Location l : e.getValue()) {
                if (sameBlock(l, loc)) {
                    join(p, e.getKey());
                    return;
                }
            }
        }
    }

    public void refreshJoinSigns(String arenaName) {
        List<Location> locs = joinSigns.get(arenaName);
        if (locs == null) {
            return;
        }
        FootballArena a = arenas.get(arenaName);
        int waiting = a == null ? 0 : a.getPlayers().size();
        boolean running = a != null && a.getState() == FootballArena.State.RUNNING;
        for (Location loc : locs) {
            if (!(loc.getBlock().getState() instanceof Sign sign)) {
                continue;
            }
            sign.line(0, Chat.color("&a[加入] &e" + arenaName));
            sign.line(1, Chat.color("&7绿茵足球 右键加入"));
            sign.line(2, Chat.color(running ? "&c比赛中" : "&7等待：" + waiting + "人"));
            sign.line(3, Chat.color("&7战绩：/fb stats"));
            sign.update();
        }
    }

    // ---------- 辅助 ----------

    private void cancelPause(FootballArena a) {
        BukkitTask t = pauseTasks.remove(a.getName());
        if (t != null) {
            t.cancel();
        }
    }

    private void broadcast(FootballArena a, String msg) {
        for (UUID id : a.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                msg(p, msg);
            }
        }
    }

    private void msg(Player p, String msg) {
        Chat.sendTitled(p, msg);
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p == null ? id.toString().substring(0, 6) : p.getName();
    }

    private String shortLoc(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
