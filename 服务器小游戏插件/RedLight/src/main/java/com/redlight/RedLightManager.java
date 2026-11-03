package com.redlight;

import com.hub.api.ArcadeBridge;
import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import com.hub.util.LiveSidebar;
import com.hub.util.StartCountdown;
import com.hub.util.Top3;
import com.hub.util.WaitCountdown;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 一二三木头人：玩家从起跑点向终点线移动，木头人回头期间只要位置变化就被淘汰；
 * 前 {@code finish-slots} 名到达终点线的人获胜。
 */
public final class RedLightManager {

    public static final String GAME = "一二三木头人";
    public static final String ARENA = "主场地";
    public static final String ARCADE_KEY = "redlight";
    private static final String NO_COLLIDE_TEAM = "redlight_nocollide";

    public enum State {
        WAITING, RUNNING, ENDING
    }

    private enum Phase {
        MOVE, COUNTDOWN, CHECK
    }

    private final RedLightPlugin plugin;
    private final RedLightArena arena;
    private final LiveSidebar liveSidebar;
    private final WaitCountdown countdown;
    private final Random random = new Random();
    private final NamespacedKey returnKey;
    private final NamespacedKey startKey;

    private final Set<UUID> waiting = new LinkedHashSet<>();
    private final Set<UUID> playing = new LinkedHashSet<>();
    private final Set<UUID> eliminated = new LinkedHashSet<>();
    /** 按到达终点线的先后顺序记录，结算时前三个就是前三名。 */
    private final List<UUID> finished = new ArrayList<>();
    private final Map<UUID, Location> snapshots = new HashMap<>();

    private State state = State.WAITING;
    private Phase phase = Phase.MOVE;
    private StartCountdown startCountdown;
    private BukkitTask gameTask;
    private BukkitTask endTask;
    private YamlConfiguration stats;

    private int secondsLeft;
    private int tickCount;
    private int phaseTicks = 20;
    private int lastCountdownSecond = -1;
    private boolean soloTest;
    private int participants;

    private boolean finishAlongX;
    private double finishCoord;
    private int finishDirection = 1;

    public RedLightManager(RedLightPlugin plugin) {
        this.plugin = plugin;
        this.arena = new RedLightArena(plugin);
        this.liveSidebar = new LiveSidebar(plugin);
        this.returnKey = new NamespacedKey(plugin, "return_item");
        this.startKey = new NamespacedKey(plugin, "start_item");
        this.countdown = new WaitCountdown(plugin, new WaitCountdown.Host() {
            @Override
            public Collection<UUID> waitingPlayers() {
                return new ArrayList<>(waiting);
            }

            @Override
            public int minPlayers() {
                return RedLightManager.this.minPlayers();
            }

            @Override
            public int maxPlayers() {
                return RedLightManager.this.maxPlayers();
            }

            @Override
            public boolean hasOpQueued() {
                for (UUID id : waiting) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.hasPermission("redlight.admin")) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onTick(long secondsLeft) {
                for (UUID id : waiting) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        Chat.actionBar(p, "&7等待中 &7| &f开始倒计时 &e" + secondsLeft + "s");
                    }
                }
            }

            @Override
            public void onMessage(String message) {
                for (UUID id : waiting) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        Chat.send(p, message);
                    }
                }
            }

            @Override
            public void onStart() {
                startGame(false);
            }
        });
    }

    // ---------- 配置 ----------

    private int cfgInt(String key, int def) {
        return plugin.getConfig().getInt(key, def);
    }

    private double cfgDouble(String key, double def) {
        return plugin.getConfig().getDouble(key, def);
    }

    private String cfgString(String key, String def) {
        String value = plugin.getConfig().getString(key, def);
        return value == null ? def : value;
    }

    public int minPlayers() {
        return Math.max(2, cfgInt("min-players", 2));
    }

    public int maxPlayers() {
        return Math.max(minPlayers(), cfgInt("max-players", 16));
    }

    public int gameSeconds() {
        return Math.max(10, cfgInt("game-seconds", 180));
    }

    public int finishSlots() {
        return Math.max(1, cfgInt("finish-slots", 3));
    }

    public int countdownSeconds() {
        return Math.max(1, Math.min(10, cfgInt("check.countdown-seconds", 3)));
    }

    private int intervalMinSeconds() {
        return Math.max(1, cfgInt("check.interval-min-seconds", 5));
    }

    private int intervalMaxSeconds() {
        return Math.max(intervalMinSeconds(), cfgInt("check.interval-max-seconds", 12));
    }

    private int durationMinSeconds() {
        return Math.max(1, cfgInt("check.duration-min-seconds", 3));
    }

    private int durationMaxSeconds() {
        return Math.max(durationMinSeconds(), cfgInt("check.duration-max-seconds", 8));
    }

    private double moveThreshold() {
        return Math.max(0.001, cfgDouble("check.move-threshold", 0.02));
    }

    private int fallLimit() {
        return Math.max(1, cfgInt("fall-limit", 10));
    }

    private int endDelayTicks() {
        return Math.max(1, cfgInt("end-delay-seconds", 3)) * 20;
    }

    // ---------- 生命周期 ----------

    public void load() {
        ensureDefaults();
        arena.load();
        stats = PlayerStats.load(ARCADE_KEY, new File(plugin.getDataFolder(), "data.yml"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HubAPI.registerGame(GAME, GAME, "ARMOR_STAND", "c");
            reportArena();
        }, 40L);
        plugin.getLogger().info("场地" + (arena.ready() ? "已配置" : "未配置（缺 " + arena.missing() + "）")
                + "：地面 Y " + arena.groundY() + "。");
    }

    private void ensureDefaults() {
        boolean changed = false;
        changed |= setDefault("check.countdown-seconds", 3);
        changed |= setDefault("check.interval-min-seconds", 5);
        changed |= setDefault("check.interval-max-seconds", 12);
        changed |= setDefault("check.duration-min-seconds", 3);
        changed |= setDefault("check.duration-max-seconds", 8);
        changed |= setDefault("check.move-threshold", 0.02);
        changed |= setDefault("finish-slots", 3);
        changed |= setDefault("fall-limit", 10);
        changed |= setDefault("messages.start-subtitle", "&f木头人回头时不要动，先到终点线的前三名获胜");
        changed |= setDefault("messages.countdown-title", "&c&l木头人回头：{time}");
        changed |= setDefault("messages.countdown-subtitle", "&f数到 0 就不能动了");
        changed |= setDefault("messages.check-title", "&c&l别动！");
        changed |= setDefault("messages.check-subtitle", "&f被看到移动就淘汰");
        changed |= setDefault("messages.move-title", "&a&l可以动了");
        changed |= setDefault("messages.move-subtitle", "&f快向终点线跑");
        if (changed) {
            plugin.saveConfig();
        }
    }

    private boolean setDefault(String path, Object value) {
        if (plugin.getConfig().isSet(path)) {
            return false;
        }
        plugin.getConfig().set(path, value);
        return true;
    }

    public void reload() {
        if (state != State.WAITING) {
            return;
        }
        arena.load();
        reportArena();
    }

    public void shutdown() {
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (startCountdown != null) {
            startCountdown.cancelAll();
        }
        liveSidebar.hideAll();
        snapshots.clear();
        waiting.clear();
        playing.clear();
        eliminated.clear();
        finished.clear();
        state = State.WAITING;
    }

    private StartCountdown startCountdown() {
        if (startCountdown == null) {
            startCountdown = new StartCountdown(plugin);
        }
        return startCountdown;
    }

    // ---------- 加入 / 离开 ----------

    public void join(Player player) {
        UUID id = player.getUniqueId();
        if (state != State.WAITING) {
            Chat.send(player, "&c对局正在进行中，请等这一局结束后再加入。");
            return;
        }
        if (waiting.contains(id)) {
            Chat.send(player, "&e你已经在等待队列中。");
            return;
        }
        if (waiting.size() >= maxPlayers()) {
            Chat.send(player, "&c等待队列已满（" + maxPlayers() + " 人）。");
            return;
        }
        if (!arena.ready()) {
            Chat.send(player, "&c场地还没配置完整（缺 " + arena.missing() + "），请联系管理员。");
            return;
        }
        waiting.add(id);
        clearPlayer(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(arena.lobby());
        giveWaitItems(player);
        HubAPI.setGame(id, GAME);
        HubAPI.announceJoin(plugin.getName(), player.getName());
        Chat.send(player, "&a已加入" + GAME + "等待队列 &7(" + waiting.size() + "/" + maxPlayers() + ")");
        Chat.send(player, "&7人数达到 &f" + minPlayers() + " &7后自动倒计时开始。");
        countdown.update();
        reportArena();
    }

    public void leave(Player player, boolean silent) {
        UUID id = player.getUniqueId();
        boolean wasWaiting = waiting.remove(id);
        boolean wasPlaying = playing.remove(id);
        boolean wasEliminated = eliminated.remove(id);
        boolean wasFinished = finished.remove(id);
        if (!wasWaiting && !wasPlaying && !wasEliminated && !wasFinished) {
            if (!silent) {
                Chat.send(player, "&e你不在此游戏中。");
            }
            return;
        }
        snapshots.remove(id);
        clearPlayer(player);
        HubAPI.clearGame(id);
        player.setGameMode(GameMode.SURVIVAL);
        player.setCollidable(true);
        clearNoCollision(player);
        if (!silent) {
            HubAPI.teleportToLobby(player);
            Chat.send(player, "&e你已离开" + GAME + "。");
        }
        if (state == State.RUNNING && playing.isEmpty()) {
            settle(false);
        } else if (state == State.WAITING) {
            countdown.update();
        }
        updateSidebar();
        reportArena();
    }

    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        boolean wasWaiting = waiting.remove(id);
        boolean wasPlaying = playing.remove(id);
        boolean wasEliminated = eliminated.remove(id);
        if (!wasWaiting && !wasPlaying && !wasEliminated) {
            return;
        }
        snapshots.remove(id);
        if (state == State.RUNNING && playing.isEmpty()) {
            settle(false);
        } else if (state == State.WAITING) {
            countdown.update();
        }
        updateSidebar();
        reportArena();
    }

    // ---------- 开局 ----------

    public void startGame(boolean force) {
        if (state != State.WAITING) {
            return;
        }
        if (!arena.ready()) {
            broadcastWaiting("&c场地未配置完整（缺 " + arena.missing() + "），无法开始。");
            return;
        }
        int need = force ? 1 : minPlayers();
        if (waiting.size() < need) {
            broadcastWaiting("&c至少需要 " + need + " 名玩家才能开始。");
            return;
        }
        if (startCountdown().begin("main", new ArrayList<>(waiting), () -> startGame(force))) {
            return;
        }
        countdown.cancel();
        playing.clear();
        eliminated.clear();
        finished.clear();
        snapshots.clear();
        for (UUID id : new ArrayList<>(waiting)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                playing.add(id);
            }
        }
        waiting.clear();
        if (playing.isEmpty()) {
            countdown.update();
            reportArena();
            return;
        }
        participants = playing.size();
        soloTest = force && playing.size() == 1 && !ArcadeBridge.isActive();
        computeFinishAxis();
        secondsLeft = gameSeconds();
        tickCount = 0;
        state = State.RUNNING;

        List<Location> spawns = new ArrayList<>(arena.spawns());
        Collections.shuffle(spawns, random);
        int index = 0;
        for (UUID id : playing) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location spawn = spawns.isEmpty() ? arena.center() : spawns.get(index++ % spawns.size());
            clearPlayer(player);
            player.setGameMode(GameMode.ADVENTURE);
            player.setCollidable(false);
            applyNoCollision(player);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            if (spawn != null) {
                player.teleport(spawn);
                player.setFallDistance(0f);
            }
            HubAPI.setPlaying(id, GAME);
            Chat.title(player, "&6&l" + GAME,
                    cfgString("messages.start-subtitle", "&f木头人回头时不要动，先到终点线的前三名获胜"), 2);
        }
        beginMovePhase(true);
        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        updateSidebar();
        reportArena();
    }

    /** 方向由「出生点中心 → 终点线」决定：沿 X 还是 Z，正方向还是负方向。 */
    private void computeFinishAxis() {
        Location finish = arena.finish();
        List<Location> spawns = arena.spawns();
        double sx = 0;
        double sz = 0;
        for (Location spawn : spawns) {
            sx += spawn.getX();
            sz += spawn.getZ();
        }
        if (!spawns.isEmpty()) {
            sx /= spawns.size();
            sz /= spawns.size();
        }
        double dx = finish.getX() - sx;
        double dz = finish.getZ() - sz;
        finishAlongX = Math.abs(dx) >= Math.abs(dz);
        double startCoord = finishAlongX ? sx : sz;
        finishCoord = finishAlongX ? finish.getX() : finish.getZ();
        finishDirection = finishCoord >= startCoord ? 1 : -1;
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) {
            plugin.getLogger().warning("[一二三木头人] 终点线几乎和出生点重合，请检查 /redlight setfinish。");
        }
    }

    private boolean crossedFinish(Location location) {
        double coord = finishAlongX ? location.getX() : location.getZ();
        return finishDirection > 0 ? coord >= finishCoord : coord <= finishCoord;
    }

    // ---------- 主循环 ----------

    private void tick() {
        if (state != State.RUNNING) {
            return;
        }
        tickCount++;
        if (tickCount % 20 == 0) {
            secondsLeft--;
            if (secondsLeft <= 0) {
                secondsLeft = 0;
                settle(false);
                return;
            }
        }
        switch (phase) {
            case MOVE -> {
                if (--phaseTicks <= 0) {
                    beginCountdown();
                }
            }
            case COUNTDOWN -> {
                int seconds = phaseSeconds();
                if (seconds != lastCountdownSecond) {
                    lastCountdownSecond = seconds;
                    if (seconds >= 1) {
                        String title = cfgString("messages.countdown-title", "&c&l木头人回头：{time}")
                                .replace("{time}", String.valueOf(seconds));
                        showTitle(title, cfgString("messages.countdown-subtitle", "&f数到 0 就不能动了"));
                        playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
                    }
                }
                if (--phaseTicks <= 0) {
                    beginCheck();
                }
            }
            case CHECK -> {
                detectMoves();
                if (--phaseTicks <= 0) {
                    beginMovePhase(false);
                }
            }
        }
        checkFinishAndFall();
        if (state != State.RUNNING) {
            return;
        }
        if (tickCount % 20 == 0) {
            updateSidebar();
            updateActionBar();
        }
    }

    private int phaseSeconds() {
        return Math.max(0, (phaseTicks + 19) / 20);
    }

    private void beginMovePhase(boolean first) {
        phase = Phase.MOVE;
        phaseTicks = randomBetween(intervalMinSeconds(), intervalMaxSeconds()) * 20;
        snapshots.clear();
        lastCountdownSecond = -1;
        if (!first) {
            showTitle(cfgString("messages.move-title", "&a&l可以动了"),
                    cfgString("messages.move-subtitle", "&f快向终点线跑"));
            playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f);
            broadcastGame("&a木头人转过身去了，可以移动！");
        }
        updateActionBar();
    }

    private void beginCountdown() {
        phase = Phase.COUNTDOWN;
        phaseTicks = countdownSeconds() * 20;
        lastCountdownSecond = -1;
        updateActionBar();
    }

    private void beginCheck() {
        phase = Phase.CHECK;
        phaseTicks = randomBetween(durationMinSeconds(), durationMaxSeconds()) * 20;
        snapshots.clear();
        for (UUID id : playing) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                snapshots.put(id, player.getLocation().clone());
            }
        }
        showTitle(cfgString("messages.check-title", "&c&l别动！"),
                cfgString("messages.check-subtitle", "&f被看到移动就淘汰"));
        playSound(Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
        broadcastGame("&c&l木头人回头了！&f别动！");
        updateActionBar();
    }

    /** 检查阶段每 tick 对比一次位置：坐标变化超过阈值就淘汰（转头不算）。 */
    private void detectMoves() {
        double threshold = moveThreshold();
        double thresholdSq = threshold * threshold;
        for (UUID id : new ArrayList<>(playing)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location snapshot = snapshots.get(id);
            Location now = player.getLocation();
            if (snapshot == null) {
                snapshots.put(id, now.clone());
                continue;
            }
            if (now.getWorld() == null || !now.getWorld().equals(snapshot.getWorld())) {
                eliminate(player, "离开了场地");
                continue;
            }
            double dx = now.getX() - snapshot.getX();
            double dy = now.getY() - snapshot.getY();
            double dz = now.getZ() - snapshot.getZ();
            if (dx * dx + dy * dy + dz * dz > thresholdSq) {
                eliminate(player, "木头人回头时移动了");
            }
        }
    }

    private void checkFinishAndFall() {
        for (UUID id : new ArrayList<>(playing)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location location = player.getLocation();
            if (location.getWorld() == null || !location.getWorld().getName().equals(arena.worldName())) {
                eliminate(player, "离开了场地");
                continue;
            }
            if (location.getY() < arena.groundY() - fallLimit()) {
                eliminate(player, "掉出场外");
                continue;
            }
            if (crossedFinish(location)) {
                finishPlayer(player);
            }
        }
    }

    // ---------- 到达 / 淘汰 / 结算 ----------

    private void finishPlayer(Player player) {
        UUID id = player.getUniqueId();
        if (!playing.remove(id)) {
            return;
        }
        finished.add(id);
        snapshots.remove(id);
        player.setGameMode(GameMode.SPECTATOR);
        player.setFallDistance(0f);
        Location spot = arena.spectatorSpot();
        if (spot != null) {
            player.teleport(spot);
        }
        int place = finished.size();
        Chat.title(player, "&6&l第 " + place + " 名", "&f成功到达终点线！", 2);
        broadcastGame("&e" + player.getName() + " &f到达终点线，第 &e" + place + " &f名！");
        playSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
        if (place >= finishSlots() || playing.isEmpty()) {
            settle(false);
            return;
        }
        updateSidebar();
        reportArena();
    }

    public void eliminate(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (!playing.remove(id)) {
            return;
        }
        eliminated.add(id);
        snapshots.remove(id);
        player.setGlowing(false);
        player.setGameMode(GameMode.SPECTATOR);
        Location spot = arena.spectatorSpot();
        if (spot != null) {
            player.teleport(spot);
        }
        Chat.title(player, "&c你被淘汰了", "&7" + reason, 2);
        broadcastGame("&c" + player.getName() + " &7被淘汰（" + reason + "）");
        if (playing.isEmpty()) {
            settle(false);
            return;
        }
        updateSidebar();
        reportArena();
    }

    private void settle(boolean aborted) {
        if (state != State.RUNNING) {
            return;
        }
        state = State.ENDING;
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        snapshots.clear();

        List<UUID> firsts = new ArrayList<>();
        List<UUID> seconds = new ArrayList<>();
        List<UUID> thirds = new ArrayList<>();
        if (!aborted) {
            if (finished.size() > 0) {
                firsts.add(finished.get(0));
            }
            if (finished.size() > 1) {
                seconds.add(finished.get(1));
            }
            if (finished.size() > 2) {
                thirds.add(finished.get(2));
            }
        }

        if (aborted) {
            broadcastGame("&e本局已被管理员结束。");
            ArcadeBridge.aborted(ARCADE_KEY);
        } else {
            Top3.header(this::broadcastGame, GAME);
            if (finished.isEmpty()) {
                broadcastGame("&7本局无人到达终点线。");
            } else {
                Top3.broadcast(this::broadcastGame, this::nameOf, firsts, seconds, thirds);
                if (finished.size() > 3) {
                    broadcastGame("&7另外 &f" + (finished.size() - 3) + " &7人也到达了终点线。");
                }
            }
            if (soloTest) {
                broadcastGame("&e单人测试结束（不记录成绩）。");
            } else {
                Top3.award(stats, firsts, seconds, thirds);
                saveStats();
                // 没到达终点线的玩家（包括被淘汰的）不进入任何名次，因此拿不到星星
                ArcadeBridge.report(ARCADE_KEY, firsts, seconds, thirds);
            }
        }
        updateSidebar();
        reportArena();
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::finishEnd, endDelayTicks());
    }

    public void abortGame() {
        if (state == State.RUNNING) {
            settle(true);
        }
    }

    private void finishEnd() {
        endTask = null;
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                clearPlayer(player);
                player.setGameMode(GameMode.SURVIVAL);
                player.setCollidable(true);
                clearNoCollision(player);
                HubAPI.teleportToLobby(player);
            }
            HubAPI.clearGame(id);
        }
        waiting.clear();
        playing.clear();
        eliminated.clear();
        finished.clear();
        snapshots.clear();
        soloTest = false;
        state = State.WAITING;
        liveSidebar.hide(ARCADE_KEY);
        countdown.update();
        reportArena();
    }

    private void saveStats() {
        if (stats == null) {
            return;
        }
        try {
            PlayerStats.save(ARCADE_KEY, stats);
        } catch (IOException exception) {
            plugin.getLogger().warning("保存玩家记录失败: " + exception.getMessage());
        }
    }

    // ---------- 玩家状态 / 道具 ----------

    /** 玩家之间的挤压由计分板队伍的 COLLISION_RULE 控制（客户端生效），setCollidable 管不到。 */
    private void applyNoCollision(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        noCollisionTeam(main, player);
        Scoreboard current = player.getScoreboard();
        if (current != null && current != main) {
            noCollisionTeam(current, player);
        }
    }

    private void noCollisionTeam(Scoreboard board, Player player) {
        Team team = board.getTeam(NO_COLLIDE_TEAM);
        if (team == null) {
            team = board.registerNewTeam(NO_COLLIDE_TEAM);
        }
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private void clearNoCollision(Player player) {
        if (player == null) {
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        removeFromNoCollision(main, player.getName());
        Scoreboard current = player.getScoreboard();
        if (current != null && current != main) {
            removeFromNoCollision(current, player.getName());
        }
    }

    private void removeFromNoCollision(Scoreboard board, String name) {
        Team team = board.getTeam(NO_COLLIDE_TEAM);
        if (team != null) {
            team.removeEntry(name);
        }
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGlowing(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExp(0f);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void giveWaitItems(Player player) {
        player.getInventory().setItem(waitSlot("return", 8), waitItem(returnKey, "return", Material.RED_BED));
        if (player.hasPermission("redlight.admin")) {
            player.getInventory().setItem(waitSlot("start", 4), waitItem(startKey, "start", Material.PAPER));
        }
    }

    private int waitSlot(String key, int def) {
        int slot = cfgInt("wait-items." + key + ".slot", def);
        return slot >= 0 && slot <= 35 ? slot : def;
    }

    private ItemStack waitItem(NamespacedKey key, String section, Material fallback) {
        String raw = plugin.getConfig().getString("wait-items." + section + ".material", fallback.name());
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        ItemStack item = new ItemStack(material == null || material.isAir() ? fallback : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("wait-items." + section + ".name", null);
            if (name != null) {
                meta.displayName(Chat.noItalic(Chat.color(name)));
            }
            List<String> lore = plugin.getConfig().getStringList("wait-items." + section + ".lore");
            if (!lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Chat.color(line));
                }
                meta.lore(lines);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isReturnItem(ItemStack item) {
        return tagged(item, returnKey);
    }

    public boolean isStartItem(ItemStack item) {
        return tagged(item, startKey);
    }

    private boolean tagged(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // ---------- 查询 / 播报 ----------

    public RedLightArena arena() {
        return arena;
    }

    public State state() {
        return state;
    }

    public boolean isWaiting(Player player) {
        return waiting.contains(player.getUniqueId());
    }

    public boolean isPlaying(Player player) {
        return player != null && playing.contains(player.getUniqueId());
    }

    public boolean isInGame(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        return waiting.contains(id) || playing.contains(id) || eliminated.contains(id) || finished.contains(id);
    }

    public int aliveCount() {
        return playing.size();
    }

    public int finishedCount() {
        return finished.size();
    }

    public int eliminatedCount() {
        return eliminated.size();
    }

    public boolean isSoloTest() {
        return soloTest;
    }

    private Set<UUID> allPlayers() {
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(waiting);
        all.addAll(playing);
        all.addAll(eliminated);
        all.addAll(finished);
        return all;
    }

    private int randomBetween(int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo + random.nextInt(hi - lo + 1);
    }

    private void broadcastWaiting(String message) {
        for (UUID id : waiting) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.send(player, message);
            }
        }
    }

    private void broadcastGame(String message) {
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.sendTitled(player, message);
            }
        }
    }

    private void showTitle(String title, String subtitle) {
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.title(player, title, subtitle, 1);
            }
        }
    }

    private void playSound(Sound sound, float volume, float pitch) {
        for (UUID id : new ArrayList<>(playing)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private String nameOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            return player.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private void updateActionBar() {
        String bar = switch (phase) {
            case MOVE -> "&a可以移动 &7| &f到达 &e" + finished.size() + "/" + finishSlots()
                    + " &7| &f剩余 &e" + secondsLeft + "s";
            case COUNTDOWN -> "&c木头人回头：&e" + phaseSeconds() + "s &7| &f到达 &e"
                    + finished.size() + "/" + finishSlots();
            case CHECK -> "&c&l别动！ &7| &f到达 &e" + finished.size() + "/" + finishSlots()
                    + " &7| &f剩余 &e" + secondsLeft + "s";
        };
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.actionBar(player, bar);
            }
        }
    }

    public void updateSidebar() {
        if (!plugin.getConfig().getBoolean("sidebar.enabled", true)) {
            return;
        }
        Set<UUID> viewers = allPlayers();
        if (viewers.isEmpty()) {
            liveSidebar.hide(ARCADE_KEY);
            return;
        }
        List<LiveSidebar.Line> lines = new ArrayList<>();
        lines.add(new LiveSidebar.Line("§6剩余时间", Math.max(0, secondsLeft)));
        lines.add(new LiveSidebar.Line("§a已到达", finished.size()));
        lines.add(new LiveSidebar.Line("§f存活", playing.size()));
        lines.add(new LiveSidebar.Line("§c淘汰", eliminated.size()));
        liveSidebar.update(ARCADE_KEY,
                plugin.getConfig().getString("sidebar.title", "&c&l一二三木头人"),
                new ArrayList<>(viewers), lines);
        // LiveSidebar 会给玩家换计分板；换完再挂一次无碰撞队伍，否则客户端又恢复挤压
        for (UUID id : playing) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                applyNoCollision(player);
            }
        }
    }

    private void reportArena() {
        boolean running = state != State.WAITING;
        String extra = running
                ? "到达 " + finished.size() + "/" + finishSlots() + " · 存活 " + playing.size()
                : null;
        HubAPI.setArena(GAME, ARENA, running ? GameState.PLAYING : GameState.WAITING,
                waiting.size() + playing.size() + eliminated.size() + finished.size(), extra);
        HubAPI.setArenaSpectator(GAME, ARENA, arena.spectatorSpot());
    }

    public List<String> debugLines() {
        List<String> out = new ArrayList<>();
        out.add("状态 " + state + "，阶段 " + phase + "，阶段剩余 " + phaseSeconds() + "s，总剩余 "
                + secondsLeft + "s，存活 " + playing.size() + "，到达 " + finished.size()
                + "，淘汰 " + eliminated.size() + "，总人数 " + participants);
        out.add("世界 " + arena.worldName() + "，区域 " + arena.sizeText()
                + "，出生点 " + arena.spawns().size()
                + "，终点线 " + (arena.finish() == null ? "未设置" : arena.finish().getBlockX() + ","
                + arena.finish().getBlockY() + "," + arena.finish().getBlockZ())
                + "，沿 " + (finishAlongX ? "X" : "Z") + (finishDirection > 0 ? "+" : "-"));
        out.add("检查：倒数 " + countdownSeconds() + "s，间隔 " + intervalMinSeconds() + "-"
                + intervalMaxSeconds() + "s，持续 " + durationMinSeconds() + "-"
                + durationMaxSeconds() + "s，阈值 " + moveThreshold());
        return out;
    }

    public String statusText() {
        return "到达 " + finished.size() + "/" + finishSlots() + " · 存活 " + playing.size()
                + " · 剩余 " + Math.max(0, secondsLeft) + "s";
    }
}
