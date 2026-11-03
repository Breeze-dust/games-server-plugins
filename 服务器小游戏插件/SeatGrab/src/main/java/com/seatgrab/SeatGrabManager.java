package com.seatgrab;

import com.hub.api.ArcadeBridge;
import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import com.hub.util.LiveSidebar;
import com.hub.util.StartCountdown;
import com.hub.util.Top3;
import com.hub.util.WaitCountdown;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 抢位占座：每回合随机挑出若干展示框作为座位，玩家把自己的收纳袋放进去；
 * 回合结束时没坐下的玩家淘汰。可以用击退笔击退别人、空手偷走别人框里的收纳袋，
 * 被偷的袋子可以按住右键蓄力扔远（被击退笔打中则原地掉落）。
 */
public final class SeatGrabManager {

    public static final String GAME = "抢位占座";
    public static final String ARENA = "主场地";
    public static final String ARCADE_KEY = "seatgrab";

    private static final String GLOW_PREFIX = "sg_";

    /** 16 种收纳袋颜色；颜色顺序打散后分给玩家，保证同局颜色尽量不同。 */
    private static final List<DyeColor> BAG_COLORS = List.of(
            DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME,
            DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE,
            DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK, DyeColor.WHITE,
            DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BROWN, DyeColor.BLACK);

    public enum State {
        WAITING, RUNNING, ENDING
    }

    private final SeatGrabPlugin plugin;
    private final SeatGrabArena arena;
    private final LiveSidebar liveSidebar;
    private final WaitCountdown countdown;
    private final Random random = new Random();

    private final NamespacedKey bagKey;
    private final NamespacedKey bagOwnerKey;
    private final NamespacedKey bagColorKey;
    private final NamespacedKey penKey;
    private final NamespacedKey returnKey;
    private final NamespacedKey startKey;

    private final Set<UUID> waiting = new LinkedHashSet<>();
    private final Set<UUID> playing = new LinkedHashSet<>();
    private final Set<UUID> eliminated = new LinkedHashSet<>();

    private final Map<UUID, DyeColor> colors = new HashMap<>();
    /** 当前这回合把袋子放进框里的 tick（用于最后回合的前两名排序）。 */
    private final Map<UUID, Long> placedAt = new HashMap<>();
    /** 本回合最近一次放进框里的 tick（袋子被偷走后仍保留，用于同分排序）。 */
    private final Map<UUID, Long> lastPlacedAt = new HashMap<>();
    /** 手里拿着别人袋子的玩家 → 袋子所属玩家。 */
    private final Map<UUID, UUID> carrying = new LinkedHashMap<>();
    /** 正在按住右键蓄力的玩家 → 开始蓄力的 tick。 */
    private final Map<UUID, Long> charging = new HashMap<>();
    private final Set<UUID> chargeConfirmed = new LinkedHashSet<>();
    /** 掉在地上的袋子（可被失主捡回）。 */
    private final Map<UUID, Item> droppedBags = new LinkedHashMap<>();
    private final Map<UUID, Long> lastPenHit = new HashMap<>();

    private final List<ItemFrame> frames = new ArrayList<>();
    private final List<ItemFrame> seatFrames = new ArrayList<>();

    private State state = State.WAITING;
    private boolean roundActive;
    private boolean finalRound;
    private boolean soloTest;
    private int round;
    private int seatsThisRound;
    private int secondsLeft;
    private int roundTicks;
    private int participants;

    private StartCountdown startCountdown;
    private BukkitTask gameTask;
    private BukkitTask roundTask;
    private BukkitTask endTask;
    private YamlConfiguration stats;

    private World pvpWorld;
    private boolean savedPvp;
    private boolean hasSavedPvp;

    public SeatGrabManager(SeatGrabPlugin plugin) {
        this.plugin = plugin;
        this.arena = new SeatGrabArena(plugin);
        this.liveSidebar = new LiveSidebar(plugin);
        this.bagKey = new NamespacedKey(plugin, "bag");
        this.bagOwnerKey = new NamespacedKey(plugin, "bag-owner");
        this.bagColorKey = new NamespacedKey(plugin, "bag-color");
        this.penKey = new NamespacedKey(plugin, "pen");
        this.returnKey = new NamespacedKey(plugin, "return_item");
        this.startKey = new NamespacedKey(plugin, "start_item");
        this.countdown = new WaitCountdown(plugin, new WaitCountdown.Host() {
            @Override
            public Collection<UUID> waitingPlayers() {
                return new ArrayList<>(waiting);
            }

            @Override
            public int minPlayers() {
                return SeatGrabManager.this.minPlayers();
            }

            @Override
            public int maxPlayers() {
                return SeatGrabManager.this.maxPlayers();
            }

            @Override
            public boolean hasOpQueued() {
                for (UUID id : waiting) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.hasPermission("seatgrab.admin")) {
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
        return Math.max(minPlayers(), Math.min(16, cfgInt("max-players", 16)));
    }

    private int roundSeconds() {
        return Math.max(10, cfgInt("round-seconds", 45));
    }

    private int roundEndDelayTicks() {
        return Math.max(1, cfgInt("round-end-delay-seconds", 4)) * 20;
    }

    private int endDelayTicks() {
        return Math.max(1, cfgInt("end-delay-seconds", 5)) * 20;
    }

    private int maxRounds() {
        return Math.max(1, cfgInt("max-rounds", 15));
    }

    private int maxChargeTicks() {
        return (int) Math.round(Math.max(0.5, cfgDouble("charge.max-seconds", 2.0)) * 20.0);
    }

    private double chargeMinSpeed() {
        return Math.max(0.1, cfgDouble("charge.min-speed", 0.6));
    }

    private double chargeMaxSpeed() {
        return Math.max(chargeMinSpeed(), cfgDouble("charge.max-speed", 1.9));
    }

    private double chargeUp() {
        return Math.max(0.0, cfgDouble("charge.up", 0.25));
    }

    private double penHorizontal() {
        return Math.max(0.1, cfgDouble("pen.knockback-horizontal", 1.05));
    }

    private double penVertical() {
        return Math.max(0.0, cfgDouble("pen.knockback-vertical", 0.42));
    }

    private int penCooldownTicks() {
        return Math.max(0, cfgInt("pen.cooldown-ticks", 10));
    }

    /**
     * 本回合可用座位数：
     * 存活 >= 5 时 N-2（每回合淘汰 2 人）；剩 4 人时 3 个、剩 3 人时 2 个（只淘汰 1 人），
     * 保证最后一回合能决出前三名。
     */
    public int seatsFor(int alive) {
        if (alive <= 1) {
            return 1;
        }
        if (alive <= 4) {
            return alive - 1;
        }
        return alive - 2;
    }

    // ---------- 生命周期 ----------

    public void load() {
        ensureDefaults();
        arena.load();
        stats = PlayerStats.load(ARCADE_KEY, new File(plugin.getDataFolder(), "data.yml"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HubAPI.registerGame(GAME, GAME, "BUNDLE", "e");
            reportArena();
        }, 40L);
        plugin.getLogger().info("场地" + (arena.ready() ? "已配置" : "未配置（缺 " + arena.missing() + "）")
                + "：" + arena.sizeText() + "。");
    }

    private void ensureDefaults() {
        boolean changed = false;
        changed |= setDefault("min-players", 2);
        changed |= setDefault("max-players", 16);
        changed |= setDefault("round-seconds", 45);
        changed |= setDefault("round-end-delay-seconds", 4);
        changed |= setDefault("end-delay-seconds", 5);
        changed |= setDefault("max-rounds", 15);
        changed |= setDefault("pen.knockback-horizontal", 1.05);
        changed |= setDefault("pen.knockback-vertical", 0.42);
        changed |= setDefault("pen.cooldown-ticks", 10);
        changed |= setDefault("charge.max-seconds", 2.0);
        changed |= setDefault("charge.min-speed", 0.6);
        changed |= setDefault("charge.max-speed", 1.9);
        changed |= setDefault("charge.up", 0.25);
        changed |= setDefault("arena.scan-height", 10);
        changed |= setDefault("messages.start-title", "&e&l第 {round} 回合");
        changed |= setDefault("messages.start-subtitle", "&f抢一个展示框坐下！存活 {alive} 人 · 座位 {seats} 个");
        changed |= setDefault("messages.final-title", "&6&l最后回合");
        changed |= setDefault("messages.final-subtitle", "&f先放进框的两人为第 1/2 名，没放进去的是第 3 名");
        changed |= setDefault("messages.round-end-title", "&e&l回合结束");
        changed |= setDefault("messages.round-end-subtitle", "&f存活 {alive} 人，{seconds} 秒后下一回合");
        changed |= setDefault("sidebar.enabled", true);
        changed |= setDefault("sidebar.title", "&e&l抢位占座");
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
        cancelTasks();
        restoreWorldPvp();
        unlockChunks();
        clearAllGlows();
        liveSidebar.hideAll();
        waiting.clear();
        playing.clear();
        eliminated.clear();
        colors.clear();
        placedAt.clear();
        lastPlacedAt.clear();
        carrying.clear();
        charging.clear();
        chargeConfirmed.clear();
        droppedBags.clear();
        lastPenHit.clear();
        frames.clear();
        seatFrames.clear();
        state = State.WAITING;
        roundActive = false;
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
        if (!wasWaiting && !wasPlaying && !wasEliminated) {
            if (!silent) {
                Chat.send(player, "&e你不在此游戏中。");
            }
            return;
        }
        if (wasPlaying && (carrying.containsKey(id) || charging.containsKey(id))) {
            dropCarriedBag(player);
        }
        freeFrameOf(id);
        placedAt.remove(id);
        lastPlacedAt.remove(id);
        carrying.remove(id);
        charging.remove(id);
        chargeConfirmed.remove(id);
        colors.remove(id);
        clearPlayer(player);
        HubAPI.clearGame(id);
        player.setGameMode(GameMode.SURVIVAL);
        if (!silent) {
            HubAPI.teleportToLobby(player);
            Chat.send(player, "&e你已离开" + GAME + "。");
        }
        if (state == State.RUNNING && playing.isEmpty()) {
            abortMatch("没有玩家了");
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
        if (wasPlaying && (carrying.containsKey(id) || charging.containsKey(id))) {
            dropCarriedBag(player);
        }
        freeFrameOf(id);
        carrying.remove(id);
        charging.remove(id);
        chargeConfirmed.remove(id);
        placedAt.remove(id);
        lastPlacedAt.remove(id);
        lastPenHit.remove(id);
        colors.remove(id);
        // 退出时清掉局内道具，避免收纳袋/击退笔被写进玩家存档
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        if (state == State.RUNNING && playing.isEmpty()) {
            abortMatch("没有玩家了");
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
        beginMatch(force);
    }

    private void beginMatch(boolean force) {
        countdown.cancel();
        playing.clear();
        eliminated.clear();
        placedAt.clear();
        lastPlacedAt.clear();
        carrying.clear();
        charging.clear();
        chargeConfirmed.clear();
        lastPenHit.clear();
        colors.clear();
        round = 0;
        finalRound = false;
        roundActive = false;
        soloTest = false;
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
        soloTest = force && participants == 1 && !ArcadeBridge.isActive();
        assignColors();
        scanFrames();
        int needSeats = Math.max(2, seatsFor(participants));
        if (frames.size() < needSeats) {
            String message = "&c场地里的展示框不够（检测到 " + frames.size() + " 个，至少需要 " + needSeats
                    + " 个），无法开始。请管理员在场地内多放几个展示框。";
            for (UUID id : playing) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    Chat.send(player, message);
                }
            }
            waiting.addAll(playing);
            playing.clear();
            reportArena();
            countdown.update();
            return;
        }
        lockChunks();
        enableWorldPvp();
        state = State.RUNNING;
        beginRound();
    }

    private void assignColors() {
        List<DyeColor> pool = new ArrayList<>(BAG_COLORS);
        Collections.shuffle(pool, random);
        int index = 0;
        for (UUID id : playing) {
            colors.put(id, pool.get(index++ % pool.size()));
        }
    }

    private void beginRound() {
        if (state != State.RUNNING) {
            return;
        }
        round++;
        if (round > maxRounds()) {
            settleByStanding();
            return;
        }
        int alive = playing.size();
        if (alive <= 1) {
            settleByStanding();
            return;
        }
        finalRound = alive <= 3;
        seatsThisRound = seatsFor(alive);
        if (frames.size() < seatsThisRound) {
            abortMatch("展示框不足");
            return;
        }
        placedAt.clear();
        lastPlacedAt.clear();
        lastPenHit.clear();
        clearCarries();
        clearDroppedBags();
        resetFrames(seatsThisRound);

        secondsLeft = roundSeconds();
        roundTicks = 0;
        roundActive = true;

        List<Location> spawns = new ArrayList<>(arena.spawns());
        Collections.shuffle(spawns, random);
        int index = 0;
        for (UUID id : new ArrayList<>(playing)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                playing.remove(id);
                continue;
            }
            Location spawn = spawns.isEmpty() ? arena.randomSpawn(random) : spawns.get(index++ % spawns.size());
            clearPlayer(player);
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setWalkSpeed(0.2f);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().setItem(0, bagItem(id));
            player.getInventory().setItem(1, penItem());
            player.getInventory().setHeldItemSlot(0);
            HubAPI.setPlaying(id, GAME);
        }
        if (playing.isEmpty()) {
            settleByStanding();
            return;
        }

        String title = finalRound
                ? cfgString("messages.final-title", "&6&l最后回合")
                : cfgString("messages.start-title", "&e&l第 {round} 回合").replace("{round}", String.valueOf(round));
        String subtitle = finalRound
                ? cfgString("messages.final-subtitle", "&f先放进框的两人为第 1/2 名，没放进去的是第 3 名")
                : cfgString("messages.start-subtitle", "&f抢一个展示框坐下！存活 {alive} 人 · 座位 {seats} 个")
                .replace("{alive}", String.valueOf(playing.size()))
                .replace("{seats}", String.valueOf(seatsThisRound));
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.title(player, title, subtitle, 2);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            }
        }
        broadcastGame("&e第 " + round + " 回合开始：&f存活 &e" + playing.size()
                + " &f人，可用座位 &e" + seatsThisRound + " &f个。");
        updateSidebar();
        updateActionBar();
        reportArena();
        if (gameTask == null) {
            gameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    // ---------- 主循环 ----------

    private void tick() {
        if (state != State.RUNNING || !roundActive) {
            return;
        }
        roundTicks++;
        tickCharging();
        if (roundTicks % 20 == 0) {
            secondsLeft--;
            keepPlayersInside();
            updateSidebar();
            updateActionBar();
            if (secondsLeft <= 0) {
                secondsLeft = 0;
                endRound();
                return;
            }
        } else if (roundTicks % 10 == 0) {
            updateActionBar();
        }
    }

    /** 跑出场地 / 掉下去的人拉回场地内，避免整局在场地外躲着。 */
    private void keepPlayersInside() {
        for (UUID id : new ArrayList<>(playing)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location location = player.getLocation();
            boolean outside = !arena.contains(location)
                    || location.getY() < arena.groundY() - 20
                    || location.getWorld() == null
                    || !location.getWorld().getName().equals(arena.worldName());
            if (!outside) {
                continue;
            }
            Location back = arena.randomSpawn(random);
            if (back != null) {
                player.teleport(back);
                player.setFallDistance(0f);
                player.setVelocity(new Vector(0, 0, 0));
                Chat.actionBar(player, "&c已把你拉回场地内");
            }
        }
    }

    private void endRound() {
        if (state != State.RUNNING || !roundActive) {
            return;
        }
        roundActive = false;
        cancelGameTask();
        clearCarries();

        Set<UUID> seated = seatedOwners();
        List<UUID> survivors = new ArrayList<>();
        List<UUID> failed = new ArrayList<>();
        for (UUID id : new ArrayList<>(playing)) {
            if (seated.contains(id)) {
                survivors.add(id);
            } else {
                failed.add(id);
            }
        }
        survivors.sort(Comparator.comparingLong((UUID id) -> placedAt.getOrDefault(id, (long) roundTicks)));
        failed.sort(Comparator
                .comparingLong((UUID id) -> lastPlacedAt.getOrDefault(id, -1L)).reversed()
                .thenComparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));

        broadcastRoundResult(survivors, failed);

        if (finalRound) {
            List<UUID> ordered = new ArrayList<>(survivors);
            ordered.addAll(failed);
            List<UUID> firsts = new ArrayList<>();
            List<UUID> seconds = new ArrayList<>();
            List<UUID> thirds = new ArrayList<>();
            if (!ordered.isEmpty()) {
                firsts.add(ordered.get(0));
            }
            if (ordered.size() > 1) {
                seconds.add(ordered.get(1));
            }
            if (ordered.size() > 2) {
                thirds.add(ordered.get(2));
            }
            for (UUID id : new ArrayList<>(playing)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.getInventory().clear();
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
            settle(false, firsts, seconds, thirds);
            return;
        }

        for (UUID id : new ArrayList<>(failed)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                eliminate(player, "没抢到座位");
            } else {
                playing.remove(id);
                eliminated.add(id);
            }
        }
        clearRoundField();
        if (playing.size() <= 1) {
            settleByStanding();
            return;
        }
        updateSidebar();
        reportArena();
        roundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            roundTask = null;
            if (state == State.RUNNING) {
                beginRound();
            }
        }, roundEndDelayTicks());
    }

    private void broadcastRoundResult(List<UUID> survivors, List<UUID> failed) {
        StringBuilder survivorsText = new StringBuilder();
        for (UUID id : survivors) {
            if (survivorsText.length() > 0) {
                survivorsText.append("&7, ");
            }
            survivorsText.append("&a").append(nameOf(id));
        }
        broadcastGame("&e第 " + round + " 回合结束：&a坐下 &f" + (survivorsText.length() == 0 ? "无" : survivorsText.toString()));
        if (!failed.isEmpty()) {
            StringBuilder failedText = new StringBuilder();
            for (UUID id : failed) {
                if (failedText.length() > 0) {
                    failedText.append("&7, ");
                }
                failedText.append("&c").append(nameOf(id));
            }
            broadcastGame("&c没抢到座位：&f" + failedText);
        }
        String title = cfgString("messages.round-end-title", "&e&l回合结束");
        String subtitle = finalRound
                ? "&f正在结算前三名…"
                : cfgString("messages.round-end-subtitle", "&f存活 {alive} 人，{seconds} 秒后下一回合")
                .replace("{alive}", String.valueOf(survivors.size()))
                .replace("{seconds}", String.valueOf(Math.max(1, cfgInt("round-end-delay-seconds", 4))));
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.title(player, title, subtitle, 2);
            }
        }
        if (finalRound) {
            broadcastGame("&6最后回合已结束，正在结算前三名…");
        }
    }

    public void eliminate(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (!playing.remove(id)) {
            return;
        }
        eliminated.add(id);
        dropCarriedBag(player);
        freeFrameOf(id);
        placedAt.remove(id);
        lastPlacedAt.remove(id);
        player.clearActiveItem();
        player.getInventory().clear();
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setGameMode(GameMode.SPECTATOR);
        Location spot = arena.spectatorSpot();
        if (spot != null) {
            player.teleport(spot);
        }
        Chat.title(player, "&c你被淘汰了", "&7" + reason, 2);
        broadcastGame("&c" + player.getName() + " &7被淘汰（" + reason + "）");
        updateSidebar();
        updateActionBar();
        reportArena();
    }

    private void settleByStanding() {
        if (state != State.RUNNING) {
            return;
        }
        Set<UUID> seated = seatedOwners();
        List<UUID> survivors = new ArrayList<>();
        List<UUID> failed = new ArrayList<>();
        for (UUID id : new ArrayList<>(playing)) {
            if (seated.contains(id)) {
                survivors.add(id);
            } else {
                failed.add(id);
            }
        }
        survivors.sort(Comparator.comparingLong((UUID id) -> placedAt.getOrDefault(id, (long) roundTicks)));
        failed.sort(Comparator
                .comparingLong((UUID id) -> lastPlacedAt.getOrDefault(id, -1L)).reversed()
                .thenComparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));
        List<UUID> ordered = new ArrayList<>(survivors);
        ordered.addAll(failed);
        List<UUID> firsts = new ArrayList<>();
        List<UUID> seconds = new ArrayList<>();
        List<UUID> thirds = new ArrayList<>();
        if (!ordered.isEmpty()) {
            firsts.add(ordered.get(0));
        }
        if (ordered.size() > 1) {
            seconds.add(ordered.get(1));
        }
        if (ordered.size() > 2) {
            thirds.add(ordered.get(2));
        }
        settle(false, firsts, seconds, thirds);
    }

    private void settle(boolean aborted, List<UUID> firsts, List<UUID> seconds, List<UUID> thirds) {
        if (state != State.RUNNING) {
            return;
        }
        state = State.ENDING;
        roundActive = false;
        cancelTasks();
        clearCarries();
        clearDroppedBags();
        resetFrames(0);
        restoreWorldPvp();

        if (aborted) {
            broadcastGame("&e本局已被管理员结束。");
            ArcadeBridge.aborted(ARCADE_KEY);
        } else {
            Top3.header(this::broadcastGame, GAME);
            if (firsts.isEmpty() && seconds.isEmpty() && thirds.isEmpty()) {
                broadcastGame("&7本局无人完成。");
            } else {
                Top3.broadcast(this::broadcastGame, this::nameOf, firsts, seconds, thirds);
            }
            if (soloTest) {
                broadcastGame("&e单人测试结束（不记录成绩）。");
            } else {
                Top3.award(stats, firsts, seconds, thirds);
                saveStats();
                ArcadeBridge.report(ARCADE_KEY, firsts, seconds, thirds);
            }
        }
        updateSidebar();
        reportArena();
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::finishEnd, endDelayTicks());
    }

    public void abortGame() {
        if (state == State.RUNNING) {
            settle(true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private void abortMatch(String reason) {
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Chat.send(player, "&c本局中止：" + reason + "。");
            }
        }
        settle(true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private void finishEnd() {
        endTask = null;
        for (UUID id : new ArrayList<>(allPlayers())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                clearPlayer(player);
                player.setGameMode(GameMode.SURVIVAL);
                HubAPI.teleportToLobby(player);
            }
            HubAPI.clearGame(id);
        }
        unlockChunks();
        clearAllGlows();
        waiting.clear();
        playing.clear();
        eliminated.clear();
        colors.clear();
        placedAt.clear();
        lastPlacedAt.clear();
        carrying.clear();
        charging.clear();
        chargeConfirmed.clear();
        droppedBags.clear();
        lastPenHit.clear();
        frames.clear();
        seatFrames.clear();
        finalRound = false;
        soloTest = false;
        roundActive = false;
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

    // ---------- 展示框 / 发光 ----------

    /** 重新扫描场地里的展示框（只读场地内的，含发光展示框）。 */
    public List<ItemFrame> scanFrames() {
        frames.clear();
        World world = arena.world();
        if (world == null || !arena.regionConfigured()) {
            return new ArrayList<>();
        }
        int minChunkX = arena.minX() >> 4;
        int maxChunkX = arena.maxX() >> 4;
        int minChunkZ = arena.minZ() >> 4;
        int maxChunkZ = arena.maxZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.getChunkAt(cx, cz).load();
            }
        }
        List<ItemFrame> found = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(arena.frameBox())) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            if (!arena.contains(frame.getLocation())) {
                continue;
            }
            frame.setFixed(true);
            found.add(frame);
        }
        found.sort(Comparator
                .comparingDouble((ItemFrame frame) -> frame.getLocation().getX())
                .thenComparingDouble(frame -> frame.getLocation().getZ())
                .thenComparingDouble(frame -> frame.getLocation().getY()));
        frames.addAll(found);
        return new ArrayList<>(found);
    }

    /** 当前扫描到的展示框数量（管理员调试用）。 */
    public int frameCountNow() {
        return scanFrames().size();
    }

    private void resetFrames(int seats) {
        clearAllGlows();
        seatFrames.clear();
        List<ItemFrame> pool = new ArrayList<>(frames);
        Collections.shuffle(pool, random);
        for (int i = 0; i < pool.size(); i++) {
            ItemFrame frame = pool.get(i);
            if (i < seats) {
                makeSeat(frame);
                seatFrames.add(frame);
            } else {
                setPlaceholder(frame);
            }
        }
    }

    private void makeSeat(ItemFrame frame) {
        frame.setItem(new ItemStack(Material.AIR), false);
        glowSeat(frame);
    }

    private void setPlaceholder(ItemFrame frame) {
        frame.setItem(new ItemStack(Material.BUNDLE), false);
        clearGlow(frame);
    }

    public boolean isArenaFrame(Entity entity) {
        if (!(entity instanceof ItemFrame frame)) {
            return false;
        }
        for (ItemFrame known : frames) {
            if (known.getUniqueId().equals(frame.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public boolean isSeatFrame(ItemFrame frame) {
        for (ItemFrame seat : seatFrames) {
            if (seat.getUniqueId().equals(frame.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private int filledSeats() {
        int filled = 0;
        for (ItemFrame frame : seatFrames) {
            if (bagOwner(frame.getItem()) != null) {
                filled++;
            }
        }
        return filled;
    }

    private Set<UUID> seatedOwners() {
        Set<UUID> owners = new LinkedHashSet<>();
        for (ItemFrame frame : seatFrames) {
            UUID owner = bagOwner(frame.getItem());
            if (owner != null) {
                owners.add(owner);
            }
        }
        return owners;
    }

    private Team glowTeam(String teamKey, NamedTextColor color) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = GLOW_PREFIX + teamKey;
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        team.color(color);
        return team;
    }

    /** 空座位：白色发光。 */
    private void glowSeat(Entity entity) {
        glow(entity, "seat", NamedTextColor.WHITE);
    }

    /** 已放置 / 掉落的收纳袋：发光颜色取最近的收纳袋颜色。 */
    private void glowBag(Entity entity, DyeColor dye) {
        DyeColor color = dye == null ? DyeColor.WHITE : dye;
        glow(entity, color.name().toLowerCase(Locale.ROOT), glowColor(color));
    }

    private void glow(Entity entity, String teamKey, NamedTextColor color) {
        clearGlow(entity);
        entity.setGlowing(true);
        glowTeam(teamKey, color).addEntry(entity.getUniqueId().toString());
    }

    private void clearGlow(Entity entity) {
        entity.setGlowing(false);
        String entry = entity.getUniqueId().toString();
        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (team.getName().startsWith(GLOW_PREFIX) && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private void clearAllGlows() {
        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (!team.getName().startsWith(GLOW_PREFIX)) {
                continue;
            }
            for (String entry : new ArrayList<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
    }

    /** 把某个玩家放在框里的袋子取下来、框恢复成空座位（玩家被淘汰/退出时用）。 */
    private void freeFrameOf(UUID owner) {
        if (owner == null) {
            return;
        }
        for (ItemFrame frame : seatFrames) {
            UUID itemOwner = bagOwner(frame.getItem());
            if (owner.equals(itemOwner)) {
                frame.setItem(new ItemStack(Material.AIR), false);
                glowSeat(frame);
            }
        }
    }

    /** 队伍发光颜色只能是 16 种原版文字颜色，取最接近的收纳袋颜色。 */
    private NamedTextColor glowColor(DyeColor dye) {
        if (dye == null) {
            return NamedTextColor.WHITE;
        }
        return switch (dye) {
            case WHITE -> NamedTextColor.WHITE;
            case ORANGE -> NamedTextColor.GOLD;
            case MAGENTA -> NamedTextColor.LIGHT_PURPLE;
            case LIGHT_BLUE -> NamedTextColor.AQUA;
            case YELLOW -> NamedTextColor.YELLOW;
            case LIME -> NamedTextColor.GREEN;
            case PINK -> NamedTextColor.LIGHT_PURPLE;
            case GRAY -> NamedTextColor.GRAY;
            case LIGHT_GRAY -> NamedTextColor.GRAY;
            case CYAN -> NamedTextColor.DARK_AQUA;
            case PURPLE -> NamedTextColor.DARK_PURPLE;
            case BLUE -> NamedTextColor.BLUE;
            case BROWN -> NamedTextColor.GOLD;
            case GREEN -> NamedTextColor.DARK_GREEN;
            case RED -> NamedTextColor.RED;
            case BLACK -> NamedTextColor.BLACK;
        };
    }

    private void lockChunks() {
        World world = arena.world();
        if (world == null || !arena.regionConfigured()) {
            return;
        }
        for (int cx = arena.minX() >> 4; cx <= arena.maxX() >> 4; cx++) {
            for (int cz = arena.minZ() >> 4; cz <= arena.maxZ() >> 4; cz++) {
                world.addPluginChunkTicket(cx, cz, plugin);
            }
        }
    }

    private void unlockChunks() {
        World world = arena.world();
        if (world != null) {
            world.removePluginChunkTickets(plugin);
        }
    }

    private void enableWorldPvp() {
        World world = arena.world();
        if (world == null) {
            return;
        }
        if (!hasSavedPvp) {
            pvpWorld = world;
            savedPvp = world.getPVP();
            hasSavedPvp = true;
        }
        world.setPVP(true);
    }

    private void restoreWorldPvp() {
        if (hasSavedPvp && pvpWorld != null) {
            pvpWorld.setPVP(savedPvp);
        }
        pvpWorld = null;
        hasSavedPvp = false;
    }

    // ---------- 收纳袋 / 击退笔 ----------

    private Material bagMaterial(DyeColor color) {
        if (color == null) {
            return Material.BUNDLE;
        }
        Material material = Material.matchMaterial(color.name() + "_BUNDLE");
        return material == null ? Material.BUNDLE : material;
    }

    private ItemStack bagItem(UUID owner) {
        DyeColor color = colors.getOrDefault(owner, DyeColor.WHITE);
        ItemStack item = new ItemStack(bagMaterial(color));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&f&l收纳袋")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Chat.color("&7这是 &e" + nameOf(owner) + " &7的收纳袋"));
            lore.add(Chat.color("&7拿着它右键空展示框坐下"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(bagKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(bagOwnerKey, PersistentDataType.STRING, owner.toString());
            meta.getPersistentDataContainer().set(bagColorKey, PersistentDataType.STRING, color.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack penItem() {
        ItemStack item = new ItemStack(Material.END_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&b&l击退笔")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Chat.color("&7左键或右键玩家：击退对手"));
            lore.add(Chat.color("&7不会造成伤害，但会把对手打红"));
            meta.lore(lore);
            meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(penKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isBagItem(ItemStack item) {
        return tagged(item, bagKey);
    }

    public boolean isPen(ItemStack item) {
        return tagged(item, penKey);
    }

    public UUID bagOwner(ItemStack item) {
        if (!isBagItem(item) || item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(bagOwnerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public DyeColor bagColor(ItemStack item) {
        if (!isBagItem(item) || item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(bagColorKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return DyeColor.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean isOwnBag(ItemStack item, Player player) {
        UUID owner = bagOwner(item);
        return owner != null && player != null && owner.equals(player.getUniqueId());
    }

    public boolean isCarrying(Player player) {
        return player != null && carrying.containsKey(player.getUniqueId());
    }

    private boolean tagged(ItemStack item, NamespacedKey key) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // ---------- 展示框交互 ----------

    /** 玩家右键展示框：手里是自己袋子 → 放入；空手 → 偷别人的。 */
    public void handleFrameInteract(Player player, ItemFrame frame) {
        if (!roundActive || state != State.RUNNING || !playing.contains(player.getUniqueId())) {
            return;
        }
        if (!isSeatFrame(frame)) {
            Chat.actionBar(player, "&7这个展示框不是本回合的座位");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        UUID handOwner = bagOwner(hand);
        if (handOwner != null && handOwner.equals(player.getUniqueId())) {
            if (bagOwner(frame.getItem()) != null) {
                Chat.actionBar(player, "&c这个座位已经有人了");
                return;
            }
            frame.setItem(hand.clone(), false);
            player.getInventory().setItemInMainHand(null);
            placedAt.put(player.getUniqueId(), (long) roundTicks);
            lastPlacedAt.merge(player.getUniqueId(), (long) roundTicks, (a, b) -> Math.max(a, b));
            glowBag(frame, colors.getOrDefault(player.getUniqueId(), DyeColor.WHITE));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
            Chat.actionBar(player, "&a坐下了！&7座位 " + filledSeats() + "/" + seatsThisRound);
            broadcastGame("&e" + player.getName() + " &f坐上了座位 &7(" + filledSeats() + "/" + seatsThisRound + ")");
            updateSidebar();
            updateActionBar();
            return;
        }
        if (hand.isEmpty()) {
            UUID owner = bagOwner(frame.getItem());
            if (owner == null) {
                Chat.actionBar(player, "&7这个展示框里只是占位用的收纳袋，拿不走");
                return;
            }
            if (owner.equals(player.getUniqueId())) {
                Chat.actionBar(player, "&7这是你自己的收纳袋，不用拿");
                return;
            }
            ItemStack taken = frame.getItem().clone();
            frame.setItem(new ItemStack(Material.AIR), false);
            glowSeat(frame);
            player.getInventory().setItemInMainHand(taken);
            carrying.put(player.getUniqueId(), owner);
            charging.remove(player.getUniqueId());
            chargeConfirmed.remove(player.getUniqueId());
            placedAt.remove(owner);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
            Chat.title(player, "&c你抢到了收纳袋", "&7按住右键蓄力，松开右键扔出", 2);
            broadcastGame("&e" + player.getName() + " &f抢走了 &e" + nameOf(owner) + " &f的收纳袋！");
            updateSidebar();
            updateActionBar();
            return;
        }
        if (isPen(hand)) {
            Chat.actionBar(player, "&7空手才能取下别人的收纳袋");
        } else {
            Chat.actionBar(player, "&7手里拿着别人的收纳袋，先按住右键扔出去");
        }
    }

    /** 玩家右键空展示框外的空气/方块，且手里拿着偷来的袋子 → 开始蓄力。 */
    public void startCharge(Player player) {
        UUID id = player.getUniqueId();
        if (!roundActive || state != State.RUNNING || !playing.contains(id)) {
            return;
        }
        UUID owner = carrying.get(id);
        if (owner == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (bagOwner(hand) == null || !owner.equals(bagOwner(hand))) {
            carrying.remove(id);
            return;
        }
        if (charging.containsKey(id)) {
            return;
        }
        charging.put(id, (long) roundTicks);
        chargeConfirmed.remove(id);
        Chat.actionBar(player, "&e蓄力中… &7按住越久扔得越远，松开右键扔出");
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.6f);
    }

    private void tickCharging() {
        for (UUID id : new ArrayList<>(charging.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || !playing.contains(id)) {
                charging.remove(id);
                chargeConfirmed.remove(id);
                continue;
            }
            UUID owner = carrying.get(id);
            ItemStack hand = player.getInventory().getItemInMainHand();
            UUID handOwner = bagOwner(hand);
            if (owner == null || handOwner == null || !owner.equals(handOwner)) {
                charging.remove(id);
                chargeConfirmed.remove(id);
                continue;
            }
            long elapsed = roundTicks - charging.get(id);
            if (player.isHandRaised()) {
                chargeConfirmed.add(id);
                if (roundTicks % 5 == 0) {
                    Chat.actionBar(player, "&e蓄力 &f" + chargeBar(elapsed) + " &7松开右键扔出");
                }
                continue;
            }
            if (chargeConfirmed.contains(id) || elapsed >= 20) {
                throwBag(player, elapsed);
            }
        }
    }

    /** 松开右键：按按住时长决定投掷力度。 */
    public void finishCharge(Player player, int ticksHeld) {
        UUID id = player.getUniqueId();
        if (!charging.containsKey(id)) {
            return;
        }
        throwBag(player, ticksHeld);
    }

    private void throwBag(Player player, long ticksHeld) {
        UUID id = player.getUniqueId();
        if (charging.remove(id) == null) {
            return;
        }
        chargeConfirmed.remove(id);
        UUID owner = carrying.remove(id);
        if (owner == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (bagOwner(hand) == null || !owner.equals(bagOwner(hand))) {
            clearStolenItemFromInventory(player, owner);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        ItemStack bag = bagItem(owner);
        double power = Math.max(0.0, Math.min(1.0, ticksHeld / (double) Math.max(1, maxChargeTicks())));
        double speed = chargeMinSpeed() + (chargeMaxSpeed() - chargeMinSpeed()) * power;
        Vector direction = player.getLocation().getDirection();
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        }
        direction = direction.normalize();
        Vector velocity = direction.multiply(speed);
        velocity.setY(direction.getY() * speed * 0.6 + chargeUp());
        spawnDroppedBag(player.getWorld(), player.getEyeLocation().subtract(0, 0.3, 0), bag, owner, velocity);
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);
        Chat.actionBar(player, "&c你把袋子扔出去了！&7（力度 " + Math.round(power * 100) + "%）");
        updateActionBar();
    }

    /** 蓄力时被击退笔打中：袋子原地掉落，不会被扔出去。 */
    private void dropCarriedBag(Player player) {
        UUID id = player.getUniqueId();
        UUID owner = carrying.remove(id);
        charging.remove(id);
        chargeConfirmed.remove(id);
        if (player != null) {
            player.clearActiveItem();
        }
        if (owner == null) {
            return;
        }
        clearStolenItemFromInventory(player, owner);
        ItemStack bag = bagItem(owner);
        spawnDroppedBag(player.getWorld(), player.getLocation().add(0, 0.3, 0), bag, owner, new Vector(0, 0, 0));
        Chat.actionBar(player, "&c你被击退，收纳袋掉在原地了！");
    }

    private void clearStolenItemFromInventory(Player player, UUID owner) {
        if (player == null) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            UUID itemOwner = bagOwner(stack);
            if (itemOwner != null && itemOwner.equals(owner)) {
                player.getInventory().setItem(slot, null);
                return;
            }
        }
    }

    private void spawnDroppedBag(World world, Location location, ItemStack bag, UUID owner, Vector velocity) {
        Item item = world.dropItem(location, bag);
        item.setPickupDelay(0);
        item.setCanMobPickup(false);
        item.setVelocity(velocity);
        glowBag(item, colors.getOrDefault(owner, bagColor(bag)));
        droppedBags.put(item.getUniqueId(), item);
    }

    /** 只有失主能捡回自己的袋子。 */
    public void handlePickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!isBagItem(stack)) {
            return;
        }
        UUID owner = bagOwner(stack);
        if (!(event.getEntity() instanceof Player picker)
                || state != State.RUNNING
                || owner == null
                || !picker.getUniqueId().equals(owner)
                || !playing.contains(owner)) {
            event.setCancelled(true);
            return;
        }
        droppedBags.remove(event.getItem().getUniqueId());
        clearGlow(event.getItem());
        Chat.actionBar(picker, "&a你捡回了自己的收纳袋！");
        picker.playSound(picker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
    }

    // ---------- 击退笔 ----------

    /** 击退笔命中：0 伤害，但对方有原版受击动画 + 击退；蓄力中的袋子原地掉落。 */
    public boolean penHit(Player attacker, Player victim) {
        if (!roundActive || state != State.RUNNING) {
            return false;
        }
        if (!playing.contains(attacker.getUniqueId()) || !playing.contains(victim.getUniqueId())) {
            return false;
        }
        if (!isPen(attacker.getInventory().getItemInMainHand())) {
            return false;
        }
        long now = roundTicks;
        Long last = lastPenHit.get(attacker.getUniqueId());
        if (last != null && penCooldownTicks() > 0 && now - last < penCooldownTicks()) {
            return false;
        }
        lastPenHit.put(attacker.getUniqueId(), now);

        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction = attacker.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(1, 0, 0);
        }
        direction.normalize().multiply(penHorizontal());
        direction.setY(penVertical());
        victim.setVelocity(direction);
        victim.playHurtAnimation(0);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.0f);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.2f);
        if (charging.containsKey(victim.getUniqueId())) {
            dropCarriedBag(victim);
        }
        return true;
    }

    // ---------- 回合清理 ----------

    private void clearRoundField() {
        clearCarries();
        clearDroppedBags();
        resetFrames(0);
        for (UUID id : playing) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.getInventory().clear();
                player.clearActiveItem();
            }
        }
        updateSidebar();
        updateActionBar();
    }

    private void clearCarries() {
        for (UUID id : new ArrayList<>(carrying.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.clearActiveItem();
            }
        }
        carrying.clear();
        charging.clear();
        chargeConfirmed.clear();
    }

    private void clearDroppedBags() {
        for (Item item : new ArrayList<>(droppedBags.values())) {
            if (item != null && !item.isDead()) {
                clearGlow(item);
                item.remove();
            }
        }
        droppedBags.clear();
    }

    private void cancelGameTask() {
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
    }

    private void cancelTasks() {
        cancelGameTask();
        if (roundTask != null) {
            roundTask.cancel();
            roundTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (startCountdown != null) {
            startCountdown.cancelAll();
        }
    }

    // ---------- 玩家状态 ----------

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
        player.clearActiveItem();
    }

    private void giveWaitItems(Player player) {
        player.getInventory().setItem(waitSlot("return", 8), waitItem(returnKey, "return", Material.RED_BED));
        if (player.hasPermission("seatgrab.admin")) {
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

    // ---------- 查询 / 播报 ----------

    public SeatGrabArena arena() {
        return arena;
    }

    public State state() {
        return state;
    }

    public boolean isWaiting(Player player) {
        return player != null && waiting.contains(player.getUniqueId());
    }

    public boolean isPlaying(Player player) {
        return player != null && playing.contains(player.getUniqueId());
    }

    public boolean isInGame(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        return waiting.contains(id) || playing.contains(id) || eliminated.contains(id);
    }

    public int aliveCount() {
        return playing.size();
    }

    public int roundNumber() {
        return round;
    }

    public boolean isFinalRound() {
        return finalRound;
    }

    public boolean isSoloTest() {
        return soloTest;
    }

    public int seatsThisRound() {
        return seatsThisRound;
    }

    private Set<UUID> allPlayers() {
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(waiting);
        all.addAll(playing);
        all.addAll(eliminated);
        return all;
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

    private String nameOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            return player.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private String chargeBar(long ticks) {
        int filled = (int) Math.min(10, Math.max(0, Math.round(ticks * 10.0 / Math.max(1, maxChargeTicks()))));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            out.append(i < filled ? "&c■" : "&7■");
        }
        return out.toString();
    }

    public void updateActionBar() {
        if (state == State.WAITING) {
            return;
        }
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (!playing.contains(id)) {
                Chat.actionBar(player, "&7观战中 · 等待本局结束");
                continue;
            }
            StringBuilder bar = new StringBuilder();
            bar.append("&e第 ").append(round).append(" 回合");
            if (finalRound) {
                bar.append(" &6(最后)");
            }
            bar.append(" &7| &f剩余 &e").append(Math.max(0, secondsLeft)).append("s")
                    .append(" &7| &a座位 &f").append(filledSeats()).append("&7/&f").append(seatsThisRound)
                    .append(" &7| &a存活 &f").append(playing.size());
            Long chargeStart = charging.get(id);
            if (chargeStart != null) {
                bar.append(" &7| &c蓄力 &f").append(chargeBar(roundTicks - chargeStart));
            } else if (carrying.containsKey(id)) {
                bar.append(" &7| &c按住右键蓄力扔袋");
            }
            Chat.actionBar(player, bar.toString());
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
        lines.add(new LiveSidebar.Line("§6剩余 &e" + Math.max(0, secondsLeft) + "s", 4));
        lines.add(new LiveSidebar.Line("§a存活 &f" + playing.size(), 3));
        lines.add(new LiveSidebar.Line("§f座位 &e" + filledSeats() + "§7/§e" + seatsThisRound, 2));
        lines.add(new LiveSidebar.Line("§e第 " + Math.max(1, round) + " 回合" + (finalRound ? " §6(最后)" : ""), 1));
        liveSidebar.update(ARCADE_KEY,
                plugin.getConfig().getString("sidebar.title", "&e&l抢位占座"),
                new ArrayList<>(viewers), lines);
    }

    private void reportArena() {
        boolean running = state != State.WAITING;
        String extra = running
                ? "第 " + Math.max(1, round) + " 回合 · 存活 " + playing.size()
                + " · 座位 " + seatsThisRound
                : null;
        HubAPI.setArena(GAME, ARENA, running ? GameState.PLAYING : GameState.WAITING,
                waiting.size() + playing.size() + eliminated.size(), extra);
        HubAPI.setArenaSpectator(GAME, ARENA, arena.spectatorSpot());
    }

    public List<String> debugLines() {
        List<String> out = new ArrayList<>();
        out.add("状态 " + state + "，回合 " + round + "，最后回合 " + finalRound
                + "，剩余 " + secondsLeft + "s，存活 " + playing.size() + "，淘汰 " + eliminated.size()
                + "，总人数 " + participants);
        out.add("世界 " + arena.worldName() + "，区域 " + arena.sizeText()
                + "，等待点 " + (arena.lobby() == null ? "未设置" : "已设置")
                + "，出生点 " + arena.spawns().size());
        out.add("展示框 " + frames.size() + " 个，本回合座位 " + seatFrames.size()
                + "，已坐 " + filledSeats() + "，当前拿起袋子 " + carrying.size()
                + "，蓄力中 " + charging.size() + "，地上袋子 " + droppedBags.size());
        out.add("座位规则：存活>=5 → N-2；4 → 3；3 → 2；2 → 1。每回合 " + roundSeconds() + "s。");
        return out;
    }

    public String statusText() {
        if (state == State.WAITING) {
            return "等待中 " + waiting.size() + "/" + maxPlayers() + " 人";
        }
        return "第 " + Math.max(1, round) + " 回合 · 存活 " + playing.size()
                + " · 座位 " + filledSeats() + "/" + seatsThisRound
                + " · 剩余 " + Math.max(0, secondsLeft) + "s";
    }

    public String framesText() {
        int count = frameCountNow();
        return "场地内检测到 " + count + " 个展示框（自动扫描到 Y "
                + (arena.maxY() + plugin.getConfig().getInt("arena.scan-height", 10)) + "）。";
    }
}
