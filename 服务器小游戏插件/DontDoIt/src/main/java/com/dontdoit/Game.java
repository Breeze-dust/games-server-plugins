package com.dontdoit;

import com.dontdoit.criteria.Criterion;
import com.dontdoit.criteria.CriterionMatcher;
import com.dontdoit.criteria.MatchContext;
import com.dontdoit.criteria.PlayerState;
import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.util.Chat;
import com.hub.util.LiveSidebar;
import com.hub.util.WaitCountdown;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 一个场地的对局：等待、开局、判定、淘汰、结算。 */
public final class Game {

    public enum State { WAITING, STARTING, RUNNING, ENDED }

    private final DontDoItManager mgr;
    private final DontDoItArena arena;
    private final Set<UUID> waiting = new LinkedHashSet<>();
    private final List<Team> teams = new ArrayList<>();
    private final Map<UUID, Team> teamOf = new HashMap<>();
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Map<UUID, Boolean> grounded = new HashMap<>();
    private final WaitCountdown countdown;
    private static final CriterionMatcher MATCHER = new CriterionMatcher();
    private final Random random = new Random();

    private State state = State.WAITING;
    private long tick;
    private long lastSidebar = -100;
    private BukkitTask tickTask;
    private BukkitTask actionTask;
    private List<Criterion> pool = List.of();
    private int groundY;
    private boolean borderApplied;
    private boolean rulesApplied;
    private BukkitTask startTask;
    private int startSecondsLeft;
    private org.bukkit.command.CommandSender pendingStarter;
    private boolean prevPvp;
    private boolean prevMobSpawning;
    private boolean prevKeepInventory;
    private MapBaseline baseline;

    public Game(DontDoItManager mgr, DontDoItArena arena) {
        this.mgr = mgr;
        this.arena = arena;
        for (TeamColor color : TeamColor.first(mgr.maxTeams())) {
            teams.add(new Team(color, mgr.lives()));
        }
        this.countdown = new WaitCountdown(mgr.plugin(), new WaitCountdown.Host() {
            @Override
            public Collection<UUID> waitingPlayers() {
                return state == State.WAITING ? waiting : java.util.Set.of();
            }

            @Override
            public int minPlayers() {
                return mgr.minPlayers();
            }

            @Override
            public int maxPlayers() {
                return Math.max(mgr.minPlayers(), mgr.maxTeams() * mgr.maxPlayersPerTeam());
            }

            @Override
            public boolean hasOpQueued() {
                if (state != State.WAITING) {
                    return false;
                }
                for (UUID id : waiting) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOp()) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onTick(long secondsLeft) {
                forEachWaiting(p -> Chat.actionBar(p, "&e" + secondsLeft + " 秒后自动开始…"));
            }

            @Override
            public void onMessage(String message) {
                if (state != State.WAITING) {
                    return;
                }
                forEachWaiting(p -> Chat.send(p, message));
            }

            @Override
            public void onStart() {
                autoAssignTeams();
                start(null);
            }

            @Override
            public String startMessage(int seconds) {
                return "&6" + seconds + "s 后自动开始…（每队至少 1 人，用选队菜单分配队伍）";
            }
        });
    }

    public DontDoItArena arena() {
        return arena;
    }

    /** 让下次开局重新从磁盘读取地图基线（/ddi savemap 后调用）。 */
    public void invalidateBaseline() {
        baseline = null;
    }

    public State state() {
        return state;
    }

    public WaitCountdown countdown() {
        return countdown;
    }

    public List<Team> teams() {
        return teams;
    }

    public Set<UUID> waitingIds() {
        return waiting;
    }

    public boolean isWaiting(Player player) {
        return waiting.contains(player.getUniqueId());
    }

    public Team teamOf(Player player) {
        return player == null ? null : teamOf.get(player.getUniqueId());
    }

    public Team teamOf(UUID id) {
        return teamOf.get(id);
    }

    public int playerCount() {
        return waiting.size();
    }

    // ================== 等待室 ==================

    public void addWaiting(Player player) {
        waiting.add(player.getUniqueId());
        states.putIfAbsent(player.getUniqueId(), new PlayerState(player, tick));
        countdown.update();
    }

    public void removeWaiting(Player player) {
        waiting.remove(player.getUniqueId());
        countdown.removeReady(player.getUniqueId());
        countdown.update();
    }

    public void onJoinMessage(Player player) {
        Chat.send(player, "&e你加入了 &d不要做挑战 &7（" + arena.getName() + "）");
        Chat.send(player, "&7用快捷栏的 &f选队菜单 &7选队伍；准备好后右键 &a准备 &7道具。");
        if (mgr.customCriteriaEnabled()) {
            Chat.send(player, "&7用快捷栏的 &f书与笔 &7可以写一条自定义词条。");
        }
    }

    // ================== 选队 ==================

    public void joinTeam(Player player, TeamColor color) {
        if (state != State.WAITING) {
            Chat.send(player, "&c对局已经开始，不能再换队。");
            return;
        }
        Team target = teamByColor(color);
        if (target == null) {
            Chat.send(player, "&c队伍不存在。");
            return;
        }
        Team current = teamOf.get(player.getUniqueId());
        if (current == target) {
            Chat.send(player, "&7你已经在 " + target.displayName() + " &7了。");
            return;
        }
        if (target.size() >= mgr.effectiveMaxPerTeam()) {
            Chat.send(player, "&c这支队伍已满（" + mgr.effectiveMaxPerTeam() + " 人）。");
            return;
        }
        if (!mgr.soloMode() && !mgr.allowUnbalancedTeams()) {
            int smallest = Integer.MAX_VALUE;
            for (Team t : teams) {
                if (t != target) {
                    smallest = Math.min(smallest, t.size());
                }
            }
            if (target.size() > smallest) {
                Chat.send(player, "&c人数不平衡：只能加入人数最少的队伍。");
                return;
            }
        }
        if (current != null) {
            current.remove(player);
        }
        target.add(player);
        teamOf.put(player.getUniqueId(), target);
        HubAPI.setTabNameColor(player.getUniqueId(), target.getColor().legacy);
        Chat.send(player, "&a你加入了 " + target.displayName());
        mgr.refreshWaitItems(player);
        countdown.update();
    }

    public Team teamByColor(TeamColor color) {
        for (Team t : teams) {
            if (t.getColor() == color) {
                return t;
            }
        }
        return null;
    }

    public Team smallestTeam() {
        Team best = null;
        for (Team t : teams) {
            if (best == null || t.size() < best.size()) {
                best = t;
            }
        }
        return best;
    }

    // ================== 开局 ==================

    /** 点击开始 / 自动开局入口：校验后先倒数，再真正开局。 */
    public boolean start(org.bukkit.command.CommandSender starter) {
        if (state != State.WAITING) {
            if (starter != null) {
                Chat.send(starter, "&c这个场地已经开始了。");
            }
            return false;
        }
        List<Team> playing = new ArrayList<>();
        for (Team t : teams) {
            if (!t.isEmpty()) {
                playing.add(t);
            }
        }
        if (playing.size() < mgr.minTeams()) {
            if (starter != null) {
                Chat.send(starter, "&c至少需要 " + mgr.minTeams() + " 支有人的队伍才能开始。");
            } else {
                broadcastGame("&c人数不足：至少需要 " + mgr.minTeams() + " 支有人的队伍。");
            }
            return false;
        }
        int totalPlayers = 0;
        for (Team t : playing) {
            totalPlayers += t.size();
        }
        if (totalPlayers < mgr.minPlayers()) {
            if (starter != null) {
                Chat.send(starter, "&c至少需要 " + mgr.minPlayers() + " 名玩家才能开始。");
            }
            return false;
        }
        if (mgr.library().isEmpty()) {
            if (starter != null) {
                Chat.send(starter, "&c词条库为空，请检查 plugins/DontDoIt/criteria.yml。");
            }
            return false;
        }
        if (arena.world() == null || arena.center() == null) {
            if (starter != null) {
                Chat.send(starter, "&c场地世界不存在：" + arena.getWorldName());
            }
            return false;
        }
        int seconds = mgr.startCountdownSeconds();
        if (seconds <= 0) {
            return begin(starter);
        }
        countdown.cancel();
        state = State.STARTING;
        pendingStarter = starter;
        startSecondsLeft = seconds;
        announceStartCountdown(startSecondsLeft);
        startTask = Bukkit.getScheduler().runTaskTimer(mgr.plugin(), this::tickStartCountdown, 20L, 20L);
        return true;
    }

    private void tickStartCountdown() {
        if (state != State.STARTING) {
            cancelStartTask();
            return;
        }
        startSecondsLeft--;
        if (startSecondsLeft <= 0) {
            cancelStartTask();
            if (!begin(pendingStarter)) {
                state = State.WAITING;
                broadcastGame("&c开局失败，倒计时已取消。");
            }
            pendingStarter = null;
            return;
        }
        announceStartCountdown(startSecondsLeft);
    }

    private void announceStartCountdown(int seconds) {
        for (Player p : recipients()) {
            Chat.title(p, "&e&l游戏即将开始", "&f" + seconds, 1);
            Chat.actionBar(p, "&e" + seconds + " 秒后开始…");
        }
    }

    private void cancelStartTask() {
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }
    }

    public boolean begin(org.bukkit.command.CommandSender starter) {
        if (state != State.WAITING && state != State.STARTING) {
            if (starter != null) {
                Chat.send(starter, "&c这个场地已经开始了。");
            }
            return false;
        }
        List<Team> playing = new ArrayList<>();
        for (Team t : teams) {
            if (!t.isEmpty()) {
                playing.add(t);
            }
        }
        if (playing.size() < mgr.minTeams()) {
            if (starter != null) {
                Chat.send(starter, "&c至少需要 " + mgr.minTeams() + " 支有人的队伍才能开始。");
            } else {
                broadcastGame("&c人数不足：至少需要 " + mgr.minTeams() + " 支有人的队伍。");
            }
            return false;
        }
        int totalPlayers = 0;
        for (Team t : playing) {
            totalPlayers += t.size();
        }
        if (totalPlayers < mgr.minPlayers()) {
            if (starter != null) {
                Chat.send(starter, "&c至少需要 " + mgr.minPlayers() + " 名玩家才能开始。");
            }
            return false;
        }
        if (mgr.library().isEmpty()) {
            if (starter != null) {
                Chat.send(starter, "&c词条库为空，请检查 plugins/DontDoIt/criteria.yml。");
            }
            return false;
        }
        World world = arena.world();
        Location center = arena.center();
        if (world == null || center == null) {
            if (starter != null) {
                Chat.send(starter, "&c场地世界不存在：" + arena.getWorldName());
            }
            return false;
        }

        groundY = center.getBlockY();
        if (mgr.restoreMap()) {
            if (baseline == null) {
                baseline = mgr.loadBaseline(arena);
            }
            if (baseline != null) {
                if (baseline.width() != restoreWidth()
                        || baseline.maxY() < mgr.restoreTopFor(world, center.getBlockY(),
                        arena.getStoneY() == Integer.MIN_VALUE ? center.getBlockY() : arena.getStoneY())) {
                    mgr.getLogger().warning("场地 " + arena.getName() + " 的地图基线尺寸与当前场地不一致"
                            + "（基线 " + baseline.width() + " 宽、顶部 Y=" + baseline.maxY()
                            + "）：请清理残留方块后执行 /ddi savemap " + arena.getName());
                }
                int changed = baseline.restore(world);
                if (changed > 0) {
                    mgr.getLogger().info("场地 " + arena.getName() + " 开局前还原了 " + changed + " 个方块");
                }
            }
        }
        int stoneY = ArenaGenerator.detectStoneY(arena);
        if (stoneY == Integer.MIN_VALUE) {
            if (starter != null) {
                Chat.send(starter, "&c在场地中心往下 30 格内找不到石层（需要至少 25% 石头类方块）。请用 /ddi setcenter 重新定位。");
            }
            return false;
        }
        if (mgr.restoreMap() && baseline == null) {
            int bottom = ArenaGenerator.bedrockY(stoneY, mgr.bedrockDepth());
            int top = mgr.restoreTopFor(world, center.getBlockY(), stoneY);
            int width = restoreWidth();
            if (top > bottom) {
                int minX = center.getBlockX() - width / 2;
                int minZ = center.getBlockZ() - width / 2;
                baseline = MapBaseline.capture(world, minX, bottom, minZ, width, top - bottom + 1);
                mgr.saveBaseline(arena, baseline);
                mgr.getLogger().info("场地 " + arena.getName() + " 已保存地图基线："
                        + baseline.volume() + " 个方块 + " + baseline.entityCount() + " 个实体（"
                        + width + "×" + (top - bottom + 1) + "×" + width
                        + "，Y " + bottom + "~" + top + "）");
            }
        }
        int generated = ArenaGenerator.generate(arena, mgr.oreRules(), mgr.bedrockDepth(), random);
        if (generated == Integer.MIN_VALUE) {
            if (starter != null) {
                Chat.send(starter, "&c场地生成失败，请检查 /ddi gen " + arena.getName() + "。");
            }
            return false;
        }
        arena.setStoneY(stoneY);
        mgr.getLogger().info("场地 " + arena.getName() + " 石层 Y=" + stoneY);

        if (mgr.useWorldBorder()) {
            WorldBorder border = world.getWorldBorder();
            arena.saveBorder(border.getSize(), border.getCenter());
            border.setCenter(center);
            border.setSize(arena.side() + mgr.worldBorderPadding() * 2.0);
            borderApplied = true;
        }
        if (mgr.gameDifficulty() != null) {
            world.setDifficulty(mgr.gameDifficulty());
        }
        applyWorldRules(world);

        pool = new ArrayList<>(mgr.library().all());
        if (mgr.yLevelCriteriaEnabled()) {
            pool.addAll(com.dontdoit.criteria.CriterionLibrary.yLevelCriteria(
                    groundY, mgr.yLevelOffsetGround(), mgr.yLevelOffsetOnAir()));
        }

        countdown.cancel();
        state = State.RUNNING;
        tick = 0;
        states.clear();
        grounded.clear();
        for (Team t : playing) {
            t.setLives(mgr.lives());
            t.setEliminated(false);
        }
        spawnTeams(playing);
        for (Team t : playing) {
            for (Player p : t.members()) {
                states.put(p.getUniqueId(), new PlayerState(p, tick));
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(Math.min(20.0, p.getHealth()));
                p.setFoodLevel(20);
                p.setExp(0);
                p.setLevel(0);
                p.getInventory().clear();
                p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                if (mgr.nightVision()) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.NIGHT_VISION,
                            Integer.MAX_VALUE, 0, false, false, false));
                }
                HubAPI.setPlaying(p.getUniqueId(), mgr.gameName());
            }
            assignCriterion(t, null);
        }
        broadcastGame("&a&l游戏开始！&7每个人会随机到自己不可见禁止词条，若做了词条内容则会扣除一点规则血量！");
        broadcastGame("&7每 " + mgr.criteriaChangeSeconds() + " 秒自动更换一次词条。");

        tickTask = Bukkit.getScheduler().runTaskTimer(mgr.plugin(), this::tickTask, 5L, 5L);
        actionTask = Bukkit.getScheduler().runTaskTimer(mgr.plugin(), this::actionTask, 20L, 20L);
        updateSidebar(true);
        mgr.reportArena(this);
        return true;
    }

    private void spawnTeams(List<Team> playing) {
        Location center = arena.center();
        World world = arena.world();
        int count = Math.max(1, playing.size());
        double radius = Math.max(2.0, arena.radius() * 0.55);
        for (int i = 0; i < playing.size(); i++) {
            double angle = (2 * Math.PI * i) / count;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location spot = new Location(world, x + 0.5, y, z + 0.5,
                    (float) (angle * 180 / Math.PI + 90), 0);
            for (Player p : playing.get(i).members()) {
                p.teleport(spot);
                p.setRespawnLocation(spot, true);
            }
        }
    }

    // ================== 词条 ==================

    private void assignCriterion(Team team, Criterion old) {
        Criterion picked;
        if (old == null && mgr.customCriteriaEnabled() && team.getCustomCriteria() != null
                && !team.getCustomCriteria().isBlank()) {
            picked = Criterion.custom(team.getColor().name, team.getCustomCriteria());
        } else {
            Set<String> otherKeys = new java.util.HashSet<>();
            for (Team t : teams) {
                if (t != team && t.getCriterion() != null && !t.isEliminated()) {
                    otherKeys.add(t.getCriterion().key);
                }
            }
            double pct = mgr.lives() <= 0 ? 1 : (double) team.getLives() / mgr.lives();
            picked = mgr.library().random(pool, pct, otherKeys, team.getHistory(), random);
        }
        team.setCriterion(picked, tick);
    }

    public void forceCriterion(Team team, Criterion criterion) {
        if (state != State.RUNNING || team == null) {
            return;
        }
        team.setCriterion(criterion, tick);
        updateSidebar(true);
    }

    public void changeCriterion(Team team, boolean announce) {        if (state != State.RUNNING || team.isEliminated()) {
            return;
        }
        Criterion old = team.getCriterion();
        assignCriterion(team, old);
        if (announce && old != null) {
            notifyGame(teamLabel(team) + " &e更换了词条，上一个词条是：&f" + old.name);
            for (Player p : team.members()) {
                Chat.title(p, "&e词条更换", "&7上一个词条：&f" + old.name, 2);
            }
        }
        updateSidebar(true);
    }

    public void trigger(Team team, Player who, Criterion criterion) {
        if (state != State.RUNNING || team.isEliminated() || criterion == null
                || team.getCriterion() != criterion) {
            return;
        }
        String whoName = who == null ? team.displayName() : "&f" + who.getName();
        notifyGame(whoName + " &6触发了规则 &e" + criterion.name);
        for (Player p : team.members()) {
            Chat.title(p, "&c" + criterion.name, whoName + " &6触发了规则", 2);
        }
        for (Player p : recipients()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1f, 1f);
            p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0),
                    200, 0.5, 0.5, 0.5, 0.0);
        }
        team.setLives(team.getLives() - 1);
        if (team.getLives() <= 0) {
            eliminate(team);
        } else {
            assignCriterion(team, criterion);
        }
        updateSidebar(true);
        mgr.getLogger().info("队伍 " + team.getColor().id + " 触发词条 " + criterion.name
                + "，剩余 " + team.getLives() + "，触发者 " + (who == null ? "-" : who.getName()));
    }

    public void eliminate(Team team) {
        if (team.isEliminated()) {
            return;
        }
        team.setLives(0);
        team.setEliminated(true);
        team.setCriterion(null, tick);
        notifyGame(teamLabel(team) + " &4已淘汰");
        Location spectator = arena.spectator();
        for (Player p : team.members()) {
            if (spectator != null) {
                p.teleport(spectator);
            }
            p.setGameMode(GameMode.SPECTATOR);
            Chat.title(p, teamLabel(team), "&4已淘汰", 3);
        }
        updateSidebar(true);
        checkEnd();
    }

    public void checkEnd() {
        List<Team> alive = aliveTeams();
        if (alive.size() <= 1) {
            endGame(alive.isEmpty() ? null : alive.get(0));
        }
    }

    private int countTeamsWithPlayers() {
        int n = 0;
        for (Team t : teams) {
            if (!t.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public List<Team> aliveTeams() {
        List<Team> out = new ArrayList<>();
        for (Team t : teams) {
            if (!t.isEmpty() && !t.isEliminated()) {
                out.add(t);
            }
        }
        return out;
    }

    private void endGame(Team winner) {
        if (state != State.RUNNING) {
            return;
        }
        state = State.ENDED;
        stopTasks();
        if (winner == null) {
            notifyGame("&7不要做挑战：&c无人获胜");
        } else {
            notifyGame(teamLabel(winner) + " &6获得胜利！");
            for (Player p : recipients()) {
                Chat.title(p, teamLabel(winner), "&6获得胜利", 4);
            }
            mgr.recordWin(winner);
        }
        updateSidebar(true);
        Bukkit.getScheduler().runTaskLater(mgr.plugin(), () -> reset(), 100L);
    }

    private void stopTasks() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (actionTask != null) {
            actionTask.cancel();
            actionTask = null;
        }
    }

    // ================== 每 tick ==================

    private void tickTask() {
        if (state != State.RUNNING) {
            return;
        }
        tick += 5;
        MatchContext ctx = context();
        for (Team team : teams) {
            if (team.isEliminated() || team.isEmpty() || team.getCriterion() == null) {
                continue;
            }
            if (team.getCriterion().type.isPeriodic()) {
                for (Player p : team.members()) {
                    if (MATCHER.matchesPeriodic(p, team, team.getCriterion(), ctx)) {
                        trigger(team, p, team.getCriterion());
                        break;
                    }
                }
            }
            if (tick - team.getCriterionSince() >= mgr.criteriaChangeSeconds() * 20L) {
                changeCriterion(team, true);
            }
        }
        if (tick - lastSidebar >= 20) {
            updateSidebar(false);
        }
        checkEnd();
    }

    private void actionTask() {
        if (state != State.RUNNING) {
            return;
        }
        for (Team team : teams) {
            if (team.isEliminated() || team.getCriterion() == null) {
                continue;
            }
            long left = Math.max(0, mgr.criteriaChangeSeconds() - (tick - team.getCriterionSince()) / 20);
            for (Player p : team.members()) {
                Chat.actionBar(p, "&a距离更换词条还有：&c" + left + "秒  &a剩余规则数量：&c" + team.getLives());
            }
        }
    }

    private MatchContext context() {
        return new MatchContext(groundY, tick,
                p -> teamOf.containsKey(p.getUniqueId()),
                p -> states.computeIfAbsent(p.getUniqueId(), id -> new PlayerState(p, tick)));
    }

    // ================== 事件入口 ==================

    public void handleEvent(Player player, Event event) {
        if (state != State.RUNNING) {
            return;
        }
        Team team = teamOf.get(player.getUniqueId());
        if (team == null || team.isEliminated()) {
            return;
        }
        Criterion c = team.getCriterion();
        if (c == null || c.type.isPeriodic()) {
            return;
        }
        if (c.type == com.dontdoit.criteria.CritType.PLACE_BLOCK) {
            int need = c.intParam("count", 0);
            if (need > 0 && team.addPlace() < need) {
                return;
            }
        }
        if (MATCHER.matchesEvent(player, team, c, event, context())) {
            trigger(team, player, c);
        }
    }

    public void onMove(Player player, Location from, Location to) {
        if (state != State.RUNNING) {
            return;
        }
        Team team = teamOf.get(player.getUniqueId());
        if (team == null || team.isEliminated() || team.getCriterion() == null) {
            return;
        }
        Criterion c = team.getCriterion();
        if (c.type == com.dontdoit.criteria.CritType.WALK_DISTANCE) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.001 && distance < 5) {
                if (team.addWalk(distance) >= c.doubleParam("distance", 40)) {
                    trigger(team, player, c);
                }
            }
        } else if (c.type == com.dontdoit.criteria.CritType.FALL_DISTANCE) {
            if (player.getFallDistance() >= c.doubleParam("distance", 5) && !player.isOnGround()) {
                trigger(team, player, c);
            }
        }
    }

    /** 由移动事件调用：只在“刚离地且向上”的那一次算一次跳跃。 */
    public void onMoveState(Player player) {
        if (state != State.RUNNING) {
            return;
        }
        PlayerState st = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerState(player, tick));
        if (!player.isOnGround()) {
            st.wasAir = true;
            st.maxFallDistance = Math.max(st.maxFallDistance, player.getFallDistance());
        } else if (st.wasAir) {
            // 刚落地：用这一跳记录到的最大下落距离判定「坠落 X 格」
            Team team = teamOf.get(player.getUniqueId());
            if (team != null && !team.isEliminated() && team.getCriterion() != null
                    && team.getCriterion().type == com.dontdoit.criteria.CritType.FALL_DISTANCE
                    && st.maxFallDistance >= team.getCriterion().doubleParam("distance", 5)) {
                trigger(team, player, team.getCriterion());
            }
            st.wasAir = false;
            st.maxFallDistance = 0;
        }
        boolean now = player.isOnGround();
        Boolean was = grounded.put(player.getUniqueId(), now);
        if (was != null && was && !now && player.getVelocity().getY() > 0) {
            onJump(player);
        }
    }

    public void onJump(Player player) {
        if (state != State.RUNNING) {
            return;
        }        Team team = teamOf.get(player.getUniqueId());
        if (team == null || team.isEliminated()) {
            return;
        }
        Criterion c = team.getCriterion();
        if (c == null || c.type != com.dontdoit.criteria.CritType.JUMP) {
            return;
        }
        int need = c.intParam("count", 0);
        if (need > 0 && team.addJump() < need) {
            return;
        }
        trigger(team, player, c);
    }

    public void onDeath(Player player) {
        if (state != State.RUNNING) {
            return;
        }
        PlayerState st = states.get(player.getUniqueId());
        if (st != null) {
            st.onDeath(tick);
        }
    }

    public void onRespawn(Player player) {
        if (state != State.RUNNING) {
            return;
        }
        Team team = teamOf.get(player.getUniqueId());
        PlayerState st = states.get(player.getUniqueId());
        if (team == null || st == null) {
            return;
        }
        Criterion c = team.getCriterion();
        if (c != null && c.type == com.dontdoit.criteria.CritType.RESPAWN_TIME
                && MATCHER.matchesRespawnDelay(player, c, context())) {
            trigger(team, player, c);
        }
        st.onRespawn();
    }

    public void onDiamond(Player player) {
        if (state != State.RUNNING || !mgr.diamondBehaviorEnabled()) {
            return;
        }
        Team team = teamOf.get(player.getUniqueId());
        if (team == null || team.isEliminated()) {
            return;
        }
        if (team.getLives() >= mgr.diamondThreshold()) {
            return;
        }
        if ("add_self_life".equalsIgnoreCase(mgr.diamondBehavior())) {
            team.setLives(Math.min(mgr.lives(), team.getLives() + 1));
            notifyGame("&f" + player.getName() + " &6获得了 &b钻石&6，"
                    + teamLabel(team) + " &a规则数量 +1");
        } else {
            List<Team> others = new ArrayList<>();
            for (Team t : aliveTeams()) {
                if (t != team) {
                    others.add(t);
                }
            }
            if (others.isEmpty()) {
                return;
            }
            Team target = others.get(random.nextInt(others.size()));
            target.setLives(Math.max(0, target.getLives() - 1));
            notifyGame("&f" + player.getName() + " &6获得了 &b钻石&6，"
                    + teamLabel(target) + " &c规则数量 -1");
            if (target.getLives() <= 0) {
                eliminate(target);
            }
        }
        updateSidebar(true);
    }

    /** 由 /ddi trigger 调用：只有自定义词条能被别人手动触发。 */
    public boolean triggerCustom(Team team, Player by) {
        if (state != State.RUNNING || team == null || team.isEliminated()) {
            return false;
        }
        Criterion c = team.getCriterion();
        if (c == null || c.type != com.dontdoit.criteria.CritType.USER_DEFINED) {
            return false;
        }
        trigger(team, by, c);
        return true;
    }

    /** 猜词条：猜对 / 猜错后都会换词条。 */
    public String guess(Player guesser, Team target, boolean correct) {
        if (state != State.RUNNING || !mgr.guessCriteriaEnabled()) {
            return "猜词条玩法未开启";
        }
        if (target == null || target.isEliminated() || target.isEmpty()) {
            return "目标队伍不存在或已淘汰";
        }
        Team mine = teamOf.get(guesser.getUniqueId());
        if (mine == target) {
            return "不能猜自己队伍的词条";
        }
        Criterion old = target.getCriterion();
        if (old == null) {
            return "目标队伍没有词条";
        }
        if (tick < target.getGuessReadyTick()) {
            long left = (target.getGuessReadyTick() - tick) / 20;
            return "该队伍还在猜词条冷却中（" + left + " 秒）";
        }
        if (correct) {
            target.setLives(Math.min(mgr.lives(), target.getLives() + mgr.guessSuccessLives()));
            notifyGame("&f" + guesser.getName() + " &a猜中了 " + teamLabel(target)
                    + " &a的词条，规则数量 +" + mgr.guessSuccessLives());
        } else {
            target.setLives(Math.max(0, target.getLives() - mgr.guessFailedLives()));
            notifyGame("&f" + guesser.getName() + " &c猜错了 " + teamLabel(target)
                    + " &c的词条，规则数量 -" + mgr.guessFailedLives());
        }
        target.setGuessReadyTick(tick + mgr.guessCooldownSeconds() * 20L);
        if (target.getLives() <= 0) {
            eliminate(target);
        } else {
            changeCriterion(target, false);
        }
        updateSidebar(true);
        return null;
    }

    // ================== 侧边栏 ==================

    public void updateSidebar(boolean force) {
        if (state == State.WAITING) {
            hideSidebars();
            return;
        }
        if (!force && tick - lastSidebar < 20) {
            return;
        }
        lastSidebar = tick;
        for (Team viewerTeam : teams) {
            String key = sidebarKey(viewerTeam);
            if (viewerTeam.isEmpty()) {
                mgr.sidebar().hide(key);
                continue;
            }
            List<UUID> viewers = new ArrayList<>();
            for (Player p : viewerTeam.members()) {
                viewers.add(p.getUniqueId());
            }
            if (viewers.isEmpty()) {
                mgr.sidebar().hide(key);
                continue;
            }
            List<LiveSidebar.Line> lines = new ArrayList<>();
            int score = 100;
            int eliminated = 0;
            for (Team t : teams) {
                if (t.isEmpty()) {
                    continue;
                }
                if (t.isEliminated()) {
                    eliminated++;
                    continue;
                }
                if (t == viewerTeam && !viewerTeam.isEliminated()) {
                    continue; // 自己的词条不显示（这是游戏规则）
                }
                String criterion = t.getCriterion() == null ? "—" : t.getCriterion().name;
                lines.add(new LiveSidebar.Line(
                        "§" + t.getColor().legacy + sidebarName(t)
                                + " §7❤" + t.getLives()
                                + " §" + t.getColor().legacy + criterion,
                        score--));
            }
            if (eliminated > 0) {
                lines.add(new LiveSidebar.Line("§8已淘汰 §7" + eliminated + " 队", 0));
            }
            if (lines.isEmpty()) {
                lines.add(new LiveSidebar.Line("§7等待其他队伍…", 0));
            }
            mgr.sidebar().update(key, "§d§l※ 禁止事项 ※", viewers, lines);
        }
    }

    private String sidebarName(Team team) {
        if (mgr.soloMode()) {
            for (Player p : team.members()) {
                return p.getName();
            }
        }
        return team.getColor().name;
    }

    /** 播报里用的名字：单人模式显示玩家名，组队模式显示队名。 */
    public String teamLabel(Team team) {
        if (mgr.soloMode()) {
            for (Player p : team.members()) {
                return "&" + team.getColor().legacy + p.getName();
            }
            return team.getColor().coloredName();
        }
        return team.displayName();
    }

    private String sidebarKey(Team team) {
        return "dontdoit:" + arena.getName() + ":" + team.getColor().id;
    }

    private void hideSidebars() {
        for (Team t : teams) {
            mgr.sidebar().hide(sidebarKey(t));
        }
    }

    // ================== 结束 / 重置 ==================

    public void reset() {
        stopTasks();
        cancelStartTask();
        countdown.cancel();
        restoreBorder();
        restoreWorldRules();
        List<UUID> waitingCopy = new ArrayList<>(waiting);
        waiting.clear();
        state = State.WAITING;
        hideSidebars();
        for (Team t : teams) {
            for (Player p : new ArrayList<>(t.members())) {
                mgr.unmapPlayer(p);
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                HubAPI.clearGame(p.getUniqueId());
                HubAPI.teleportToLobby(p);
            }
            t.clearMembers();
            t.setEliminated(false);
            t.setLives(mgr.lives());
            t.setCriterion(null, tick);
            t.setCustomCriteria(null);
        }
        for (UUID id : waitingCopy) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                mgr.unmapPlayer(p);
                p.setGameMode(GameMode.SURVIVAL);
                HubAPI.clearGame(id);
                HubAPI.teleportToLobby(p);
            }
        }
        teamOf.clear();
        states.clear();
        arena.clearBorder();
        if (mgr.restoreMap() && baseline == null) {
            // 插件重载/服务器重启后 Game 是新建的，这里补加载一次基线
            baseline = mgr.loadBaseline(arena);
        }
        if (mgr.restoreMap() && baseline != null) {
            World world = arena.world();
            if (world != null) {
                int changed = baseline.restore(world);
                if (changed > 0) {
                    mgr.getLogger().info("场地 " + arena.getName() + " 结束后还原了 " + changed + " 个方块");
                }
            }
        }
        mgr.forget(this);
        mgr.reportArena(this);
    }

    /** 插件关闭：只收尾，不传送玩家。 */
    public void shutdown() {
        stopTasks();
        cancelStartTask();
        countdown.cancel();
        restoreBorder();
        restoreWorldRules();
        hideSidebars();
    }

    /** 还原区域的水平宽度：世界边界留白也算在内，否则留白圈里放的方块清不掉。 */
    private int restoreWidth() {
        int pad = mgr.useWorldBorder() ? mgr.worldBorderPadding() : 0;
        return arena.side() + pad * 2;
    }

    /** 可玩范围 = 场地边长 + 世界边界留白，与世界边界、地图基线一致。 */
    public boolean isInsideArena(Location loc) {
        Location center = arena.center();
        if (center == null || loc == null || loc.getWorld() == null
                || !loc.getWorld().equals(center.getWorld())) {
            return false;
        }
        int half = restoreWidth() / 2;
        return Math.abs(loc.getBlockX() - center.getBlockX()) <= half
                && Math.abs(loc.getBlockZ() - center.getBlockZ()) <= half;
    }

    private void applyWorldRules(World world) {
        prevPvp = world.getPVP();
        prevMobSpawning = Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRule.DO_MOB_SPAWNING));
        prevKeepInventory = Boolean.TRUE.equals(world.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY));
        mgr.saveWorldRules(arena, world, prevPvp, prevMobSpawning, prevKeepInventory);
        world.setPVP(mgr.pvpEnabled());
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, mgr.mobSpawningEnabled());
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, mgr.keepInventoryOnDeath());
        rulesApplied = true;
        mgr.getLogger().info("场地 " + arena.getName() + " 对局规则（应用后读回）：pvp=" + world.getPVP()
                + "，刷怪=" + world.getGameRuleValue(org.bukkit.GameRule.DO_MOB_SPAWNING)
                + "，保留背包=" + world.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY));
    }

    private void restoreWorldRules() {
        if (!rulesApplied) {
            return;
        }
        World world = arena.world();
        if (world != null) {
            world.setPVP(prevPvp);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, prevMobSpawning);
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, prevKeepInventory);
        }
        mgr.clearWorldRules(arena);
        rulesApplied = false;
    }

    private void restoreBorder() {
        if (!borderApplied) {
            return;
        }
        World world = arena.world();
        if (world != null) {
            WorldBorder border = world.getWorldBorder();
            if (arena.getSavedBorderSize() > 0) {
                border.setSize(arena.getSavedBorderSize());
                if (arena.getSavedBorderCenter() != null) {
                    border.setCenter(arena.getSavedBorderCenter());
                }
            }
        }
        borderApplied = false;
    }

    /** 玩家离开（退出游戏 / 回主城 / 下线）。 */
    public void leave(Player player, boolean silent) {
        UUID id = player.getUniqueId();
        waiting.remove(id);
        countdown.removeReady(id);
        Team team = teamOf.remove(id);
        if (team != null) {
            team.remove(player);
            HubAPI.setTabNameColor(id, null);
            if (state == State.RUNNING && team.isEmpty() && !team.isEliminated()) {
                eliminate(team);
            }
        }
        if (state == State.STARTING && countTeamsWithPlayers() < mgr.minTeams()) {
            cancelStartTask();
            pendingStarter = null;
            state = State.WAITING;
            broadcastGame("&c人数不足，开局倒计时已取消。");
        }
        states.remove(id);
        if (state == State.RUNNING) {
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
        }
        if (!silent) {
            HubAPI.clearGame(id);
        }
        updateSidebar(true);
        countdown.update();
    }

    /** 本局消息的收件人：所有参赛/等待玩家 + 附近的观战者（去重）。 */
    public List<Player> recipients() {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>(waiting);
        for (Team t : teams) {
            for (Player p : t.members()) {
                ids.add(p.getUniqueId());
            }
        }
        List<Player> out = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        try {
            for (UUID id : HubAPI.spectatorsNear(ids, 96.0)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && !out.contains(p)) {
                    out.add(p);
                }
            }
        } catch (Throwable ignored) {
            // 没有主城/旧版主城时自动忽略
        }
        return out;
    }

    /** 本局播报：默认只发给本局玩家和附近观战者；broadcast-server-wide=true 时全服。 */
    public void notifyGame(String message) {
        if (mgr.broadcastServerWide()) {
            Bukkit.broadcast(Chat.color(message));
            return;
        }
        for (Player p : recipients()) {
            Chat.send(p, message);
        }
    }

    public void broadcastGame(String message) {
        // 等待室里的玩家同时也在某支队伍里，必须去重，否则开局播报会发两遍
        for (Player p : recipients()) {
            Chat.send(p, message);
        }
    }

    private void forEachWaiting(java.util.function.Consumer<Player> action) {
        for (UUID id : waiting) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                action.accept(p);
            }
        }
    }

    /** 玩家在等待室但还没选队时，自动分到人最少的队伍（开局前调用）。 */
    public void autoAssignTeams() {
        for (UUID id : new ArrayList<>(waiting)) {
            if (teamOf.containsKey(id)) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            Team smallest = smallestTeam();
            if (smallest != null && smallest.size() < mgr.effectiveMaxPerTeam()) {
                smallest.add(p);
                teamOf.put(id, smallest);
                HubAPI.setTabNameColor(id, smallest.getColor().legacy);
            }
        }
    }

    public boolean anyOpWaiting() {
        for (UUID id : waiting) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOp()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 运维自检：用两个“假队伍成员”跑一遍开局 → 触发 → 淘汰 → 结算 → 重置，
     * 不传送真实玩家、不需要有人在线。用来在服务器里确认场地与流程是否正常。
     */
    public String selfTest() {
        if (state != State.WAITING) {
            return "场地正在对局中，先 /ddi stop。";
        }
        Team a = null;
        Team b = null;
        for (Team t : teams) {
            if (a == null) {
                a = t;
            } else if (b == null) {
                b = t;
            }
        }
        if (a == null || b == null) {
            return "队伍数量不足。";
        }
        a.playerIds().add(UUID.randomUUID());
        b.playerIds().add(UUID.randomUUID());
        StringBuilder out = new StringBuilder();
        boolean ok;
        try {
            ok = begin(null);
        } catch (RuntimeException e) {
            a.clearMembers();
            b.clearMembers();
            mgr.getLogger().log(java.util.logging.Level.SEVERE, "自检开局异常", e);
            return "开局抛出异常：" + e;
        }
        if (!ok) {
            a.clearMembers();
            b.clearMembers();
            return "开局失败（石层/人数/词条库问题，见控制台日志）。";
        }
        out.append("开局成功，状态=").append(state)
                .append("；场地石层 Y=").append(arena.getStoneY())
                .append("；队伍数=").append(aliveTeams().size());

        Criterion ca = a.getCriterion();
        out.append("；队伍1词条=").append(ca == null ? "null" : ca.name);
        if (ca != null) {
            int before = a.getLives();
            trigger(a, null, ca);
            out.append("；触发后规则数 ").append(before).append("->").append(a.getLives());
        }
        if (!a.isEliminated()) {
            a.setLives(1);
            if (a.getCriterion() != null) {
                trigger(a, null, a.getCriterion());
            } else {
                eliminate(a);
            }
            out.append("；队伍1").append(a.isEliminated() ? "已淘汰" : "未淘汰");
        }
        if (!b.isEliminated()) {
            b.setLives(0);
            eliminate(b);
            out.append("；队伍2已淘汰");
        }
        out.append("；结算后状态=").append(state);
        mgr.getLogger().info("DontDoIt 自检完成：" + out);
        reset();
        out.append("；已重置");
        return out.toString();
    }

    /** 供管理器上报场地状态。 */
    public int playingCount() {        int n = 0;
        for (Team t : teams) {
            n += t.size();
        }
        return n;
    }

    public GameState hubState() {
        return state == State.RUNNING || state == State.STARTING
                ? GameState.PLAYING : GameState.WAITING;
    }

    public String hubExtra() {
        if (state != State.RUNNING) {
            return null;
        }
        return "剩余 " + aliveTeams().size() + " 队";
    }
}
