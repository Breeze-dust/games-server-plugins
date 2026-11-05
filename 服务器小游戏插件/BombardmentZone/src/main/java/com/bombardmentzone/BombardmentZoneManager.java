package com.bombardmentzone;

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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 轰炸区：海盗船炮口向小岛随机落弹，玩家只有一颗心，躲炮火、别落水，最后存活者获胜。 */
public final class BombardmentZoneManager {

    public static final String GAME = "轰炸区";
    public static final String ARENA = "主场地";
    public static final String ARCADE_KEY = "bombard";
    private static final String STATS_NAMESPACE = "bombard";
    private static final String NO_COLLIDE_TEAM = "bombard_nocollide";
    private static final double ONE_HEART = 2.0;
    private static final double DEFAULT_MAX_HEALTH = 20.0;

    public enum State {
        WAITING, RUNNING, ENDING
    }

    /** 飞行中的炮弹。 */
    private static final class Shell {
        private final UUID entityId;
        private final Location target;
        private final Vector step;
        private final int totalTicks;
        private int ticks;

        private Shell(UUID entityId, Location target, Vector step, int totalTicks) {
            this.entityId = entityId;
            this.target = target;
            this.step = step;
            this.totalTicks = totalTicks;
        }
    }

    private final BombardmentZonePlugin plugin;
    private final BombardmentZoneArena arena;
    private final LiveSidebar liveSidebar;
    private final WaitCountdown countdown;
    private final Random random = new Random();
    private final NamespacedKey returnKey;
    private final NamespacedKey startKey;

    private final Set<UUID> waiting = new LinkedHashSet<>();
    private final Set<UUID> playing = new LinkedHashSet<>();
    private final Set<UUID> eliminated = new LinkedHashSet<>();
    private final List<UUID> eliminationOrder = new ArrayList<>();
    private final List<Location> landing = new ArrayList<>();
    private final List<Shell> shells = new ArrayList<>();
    private final Map<UUID, Double> originalMaxHealth = new LinkedHashMap<>();

    private State state = State.WAITING;
    private StartCountdown startCountdown;
    private BukkitTask gameTask;
    private YamlConfiguration stats;

    private int secondsLeft;
    private int elapsedTicks;
    private int startDelayTicks;
    private int fireCooldown;
    private int secondCounter;
    private boolean latePhase;
    private boolean soloTest;
    private int shellsFired;

    public BombardmentZoneManager(BombardmentZonePlugin plugin) {
        this.plugin = plugin;
        this.arena = new BombardmentZoneArena(plugin);
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
                return BombardmentZoneManager.this.minPlayers();
            }

            @Override
            public int maxPlayers() {
                return BombardmentZoneManager.this.maxPlayers();
            }

            @Override
            public boolean hasOpQueued() {
                for (UUID id : waiting) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.hasPermission("bombard.admin")) {
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

    public int minPlayers() {
        return Math.max(2, cfgInt("min-players", 2));
    }

    public int maxPlayers() {
        return Math.max(minPlayers(), cfgInt("max-players", 16));
    }

    public int gameSeconds() {
        return Math.max(30, cfgInt("game-seconds", 300));
    }

    public int lateStartSeconds() {
        return Math.max(1, cfgInt("phases.late-start-seconds", 90));
    }

    private int earlyIntervalTicks() {
        return Math.max(1, cfgInt("phases.early-interval-ticks", 40));
    }

    private int lateIntervalTicks() {
        return Math.max(1, cfgInt("phases.late-interval-ticks", 10));
    }

    private int lateCannonsPerVolley() {
        return Math.max(1, cfgInt("phases.late-cannons-per-volley", 2));
    }

    private int maxInFlight() {
        return Math.max(1, cfgInt("phases.max-in-flight", 30));
    }

    private int endDelayTicks() {
        return Math.max(1, cfgInt("end-delay-seconds", 4)) * 20;
    }

    private double explosionRadius() {
        return Math.max(1.0, cfgDouble("shell.explosion-radius", 3.5));
    }

    private Material shellMaterial() {
        String raw = plugin.getConfig().getString("shell.material", "BLACK_WOOL");
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        return material == null || material.isAir() ? Material.BLACK_WOOL : material;
    }

    private Set<Material> landingMaterials() {
        Set<Material> out = new LinkedHashSet<>();
        for (String raw : plugin.getConfig().getStringList("landing.materials")) {
            Material material = Material.matchMaterial(raw);
            if (material != null && !material.isAir()) {
                out.add(material);
            }
        }
        if (out.isEmpty()) {
            out.add(Material.GRASS_BLOCK);
            out.add(Material.DIRT);
            out.add(Material.STONE);
        }
        return out;
    }

    // ---------- 生命周期 ----------

    public void load() {
        ensureDefaults();
        arena.load();
        stats = PlayerStats.load(STATS_NAMESPACE, new File(plugin.getDataFolder(), "data.yml"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HubAPI.registerGame(GAME, GAME, "TNT", "c");
            reportArena();
        }, 40L);
        plugin.getLogger().info("场地" + (arena.ready() ? "已配置" : "未配置（缺 " + arena.missing() + "）")
                + "：地面 Y " + arena.groundY() + "。");
    }

    private void ensureDefaults() {
        boolean changed = false;
        changed |= setDefault("phases.late-start-seconds", 90);
        changed |= setDefault("phases.early-interval-ticks", 40);
        changed |= setDefault("phases.late-interval-ticks", 10);
        changed |= setDefault("phases.late-cannons-per-volley", 2);
        changed |= setDefault("phases.max-in-flight", 30);
        changed |= setDefault("shell.speed", 1.6);
        changed |= setDefault("shell.size", 0.7);
        changed |= setDefault("shell.explosion-radius", 3.5);
        changed |= setDefault("shell.material", "BLACK_WOOL");
        changed |= setDefault("landing.scan-above", 10);
        changed |= setDefault("landing.scan-below", 12);
        changed |= setDefault("debug-shells", false);
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
        if (startCountdown != null) {
            startCountdown.cancelAll();
        }
        removeShells();
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restoreMaxHealth(player);
            }
        }
        liveSidebar.hideAll();
        waiting.clear();
        playing.clear();
        eliminated.clear();
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
        restoreMaxHealth(player);
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
        if (wasEliminated) {
            eliminationOrder.remove(id);
        }
        clearPlayer(player);
        restoreMaxHealth(player);
        HubAPI.clearGame(id);
        player.setGameMode(GameMode.SURVIVAL);
        player.setCollidable(true);
        clearNoCollision(player);
        if (!silent) {
            HubAPI.teleportToLobby(player);
            Chat.send(player, "&e你已离开" + GAME + "。");
        }
        if (state == State.RUNNING && wasPlaying) {
            checkWin();
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
        restoreMaxHealth(player);
        if (state == State.RUNNING && wasPlaying) {
            eliminationOrder.add(id);
            checkWin();
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
        scanLanding();
        if (landing.isEmpty()) {
            broadcastWaiting("&c场地区域里没找到任何可落点的方块，请检查 landing.materials 和 pos1/pos2。");
            return;
        }
        countdown.cancel();
        playing.clear();
        eliminated.clear();
        eliminationOrder.clear();
        originalMaxHealth.clear();
        playing.addAll(waiting);
        waiting.clear();
        soloTest = force && playing.size() == 1 && !ArcadeBridge.isActive();
        secondsLeft = gameSeconds();
        elapsedTicks = 0;
        secondCounter = 0;
        startDelayTicks = Math.max(0, cfgInt("start-delay-seconds", 3)) * 20;
        fireCooldown = earlyIntervalTicks();
        latePhase = false;
        shellsFired = 0;
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
            applyOneHeart(player);
            player.setGameMode(GameMode.SURVIVAL);
            player.setCollidable(false);
            applyNoCollision(player);
            if (spawn != null) {
                player.teleport(spawn);
                player.setFallDistance(0f);
            }
            HubAPI.setPlaying(id, GAME);
            Chat.title(player, "&c&l" + GAME, soloTest ? "&e单人测试模式" : "&f躲开炮火，别掉进水里", 2);
        }
        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        updateSidebar();
        reportArena();
        if (plugin.getConfig().getBoolean("debug-shells", false)) {
            plugin.getLogger().info("[轰炸区] 开局：可落点 " + landing.size() + " 个，炮口 " + arena.cannons().size()
                    + " 个，玩家 " + playing.size() + " 人。");
        }
    }

    // ---------- 主循环 ----------

    private void tick() {
        if (state != State.RUNNING) {
            return;
        }
        if (startDelayTicks > 0) {
            startDelayTicks--;
            if (startDelayTicks == 0) {
                broadcastGame("&e&l第一阶段 &7- &f零星炮火，注意头顶！");
            }
            tickShells();
        } else {
            elapsedTicks++;
            if (!latePhase && elapsedTicks >= lateStartSeconds() * 20) {
                latePhase = true;
                for (UUID id : allPlayers()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        Chat.title(player, "&c&l饱和轰炸", "&f多个炮口同时开火，炮弹更密集！", 2);
                    }
                }
                broadcastGame("&4&l✖ 第二阶段：饱和轰炸 ✖ &f多个炮口同时开火！");
                World world = arena.world();
                if (world != null && arena.center() != null) {
                    world.playSound(arena.center(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
                }
            }
            if (--fireCooldown <= 0) {
                fireVolley();
                fireCooldown = latePhase ? lateIntervalTicks() : earlyIntervalTicks();
            }
            tickShells();
            checkWater();
        }
        if (state != State.RUNNING) {
            return;
        }
        if (++secondCounter % 20 == 0) {
            secondsLeft--;
            if (secondsLeft <= 0) {
                secondsLeft = 0;
                endGame(true);
                return;
            }
            updateSidebar();
            updateActionBar();
        }
    }

    private void fireVolley() {
        List<Location> cannons = arena.cannons();
        if (cannons.isEmpty() || landing.isEmpty()) {
            return;
        }
        Collections.shuffle(cannons, random);
        int shots = Math.min(latePhase ? lateCannonsPerVolley() : 1, cannons.size());
        for (int i = 0; i < shots; i++) {
            if (shells.size() >= maxInFlight()) {
                return;
            }
            fireShell(cannons.get(i));
        }
    }

    private void fireShell(Location cannon) {
        if (landing.isEmpty()) {
            return;
        }
        Location target = landing.get(random.nextInt(landing.size())).clone();
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        Vector direction = target.toVector().subtract(cannon.toVector());
        double distance = direction.length();
        if (distance < 2) {
            return;
        }
        direction.normalize();
        double speed = Math.max(0.5, cfgDouble("shell.speed", 1.6));
        int flightTicks = (int) Math.max(6, Math.min(200, Math.ceil(distance / speed)));
        Location spawnAt = cannon.clone().add(direction.clone().multiply(2.0));
        float size = (float) Math.max(0.2, Math.min(2.0, cfgDouble("shell.size", 0.7)));
        BlockData data = shellMaterial().createBlockData();
        BlockDisplay display = world.spawn(spawnAt, BlockDisplay.class, entity -> {
            entity.setBlock(data);
            entity.setTransformation(new Transformation(
                    new Vector3f(-size / 2f, -size / 2f, -size / 2f), new Quaternionf(),
                    new Vector3f(size, size, size), new Quaternionf()));
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            // 让客户端在两三个 tick 内插值到新位置；否则每 tick 的绝对传送会直接吸附，
            // 低速度时表现为一顿一顿、偶尔跳一段。
            entity.setTeleportDuration(Math.max(1, Math.min(20,
                    cfgInt("shell.teleport-duration-ticks", 2))));
        });
        Vector step = target.toVector().subtract(spawnAt.toVector()).multiply(1.0 / flightTicks);
        shells.add(new Shell(display.getUniqueId(), target, step, flightTicks));
        shellsFired++;
    }

    private void tickShells() {
        if (shells.isEmpty()) {
            return;
        }
        for (Iterator<Shell> iterator = shells.iterator(); iterator.hasNext(); ) {
            Shell shell = iterator.next();
            Entity entity = Bukkit.getEntity(shell.entityId);
            if (!(entity instanceof BlockDisplay display) || !display.isValid() || display.isDead()) {
                iterator.remove();
                continue;
            }
            shell.ticks++;
            display.teleport(display.getLocation().add(shell.step));
            Location location = display.getLocation();
            boolean hitBlock = shell.ticks > 3 && isSolid(location.getBlock());
            if (hitBlock || shell.ticks >= shell.totalTicks) {
                display.remove();
                iterator.remove();
                explode(hitBlock ? location : shell.target);
            } else {
                display.getWorld().spawnParticle(Particle.SMOKE, location, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    private boolean isSolid(Block block) {
        Material type = block.getType();
        return !type.isAir() && !block.isLiquid()
                && type != Material.LIGHT && type != Material.STRUCTURE_VOID
                && type != Material.BARRIER && type != Material.FIRE;
    }

    private void explode(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double radius = explosionRadius();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1, 0, 0, 0, 0);
        // 粒子数量和散布范围跟着爆炸半径走：半径越大，火球/烟团越大
        int bursts = (int) Math.max(12, Math.min(90, Math.round(radius * 10)));
        world.spawnParticle(Particle.EXPLOSION, location, bursts,
                radius * 0.35, radius * 0.25, radius * 0.35, 0.0);
        int smoke = (int) Math.max(10, Math.min(70, Math.round(radius * 6)));
        world.spawnParticle(Particle.LARGE_SMOKE, location, smoke,
                radius * 0.45, radius * 0.25, radius * 0.45, 0.01);
        int edge = (int) Math.max(12, Math.min(72, Math.round(radius * 12)));
        world.spawnParticle(Particle.SMOKE, location, edge, radius, 0.15, radius, 0.0);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);

        double radiusSq = radius * radius;
        List<Player> hit = new ArrayList<>();
        for (UUID id : playing) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location at = player.getLocation();
            if (at.getWorld() == null || !at.getWorld().equals(world)) {
                continue;
            }
            if (at.distanceSquared(location) <= radiusSq) {
                hit.add(player);
            }
        }
        for (Player player : hit) {
            eliminate(player, "被炮弹炸飞");
        }
    }

    private void checkWater() {
        for (UUID id : new ArrayList<>(playing)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            Location location = player.getLocation();
            if (player.isInWater() || location.getBlock().getType() == Material.WATER) {
                eliminate(player, "掉进了水里");
            } else if (location.getY() < player.getWorld().getMinHeight() - 1) {
                eliminate(player, "掉出了场地");
            }
        }
    }

    // ---------- 淘汰 / 结算 ----------

    public void eliminate(Player player, String reason) {
        UUID id = player.getUniqueId();
        if (!playing.remove(id)) {
            return;
        }
        eliminated.add(id);
        eliminationOrder.add(id);
        player.setGlowing(false);
        restoreMaxHealth(player);
        player.setGameMode(GameMode.SPECTATOR);
        Location spot = arena.spectatorSpot();
        if (spot != null) {
            player.teleport(spot);
        }
        Chat.title(player, "&c你被淘汰了", "&7" + reason, 2);
        broadcastGame("&c" + player.getName() + " &7被淘汰（" + reason + "）");
        if (state == State.RUNNING) {
            checkWin();
        }
        updateSidebar();
        reportArena();
    }

    private void checkWin() {
        if (state != State.RUNNING) {
            return;
        }
        if (playing.size() <= 1) {
            endGame(false);
        }
    }

    private void endGame(boolean timeUp) {
        if (state == State.RUNNING) {
            finishGame(timeUp, true);
        }
    }

    public void abortGame() {
        if (state == State.RUNNING) {
            finishGame(false, false);
        }
    }

    private void finishGame(boolean timeUp, boolean award) {
        state = State.ENDING;
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        removeShells();

        List<UUID> ranking = ranking();
        List<UUID> firsts = new ArrayList<>();
        if (!playing.isEmpty()) {
            firsts.addAll(playing);
        } else if (!ranking.isEmpty()) {
            firsts.add(ranking.get(0));
        }
        List<UUID> rest = new ArrayList<>();
        for (UUID id : ranking) {
            if (!firsts.contains(id)) {
                rest.add(id);
            }
        }
        List<UUID> seconds = rest.size() > 0 ? List.of(rest.get(0)) : List.of();
        List<UUID> thirds = rest.size() > 1 ? List.of(rest.get(1)) : List.of();

        if (!award) {
            broadcastGame("&e本局已被管理员结束。");
            ArcadeBridge.aborted(ARCADE_KEY);
        } else {
            Top3.header(this::broadcastGame, GAME);
            if (firsts.isEmpty()) {
                broadcastGame("&7本局无人存活。");
            } else {
                Top3.broadcast(this::broadcastGame, this::nameOf, firsts, seconds, thirds);
                if (timeUp) {
                    broadcastGame("&7时间到，剩余玩家并列获胜。");
                } else if (firsts.size() == 1) {
                    broadcastGame("&6&l" + nameOf(firsts.get(0)) + " &f活到了最后！");
                }
            }
        }
        if (award) {
            if (soloTest) {
                broadcastGame("&e单人测试结束（不记录成绩）。");
            } else {
                Top3.award(stats, firsts, seconds, thirds);
                saveStats();
                ArcadeBridge.report(ARCADE_KEY, firsts, seconds, thirds);
            }
        }
        updateSidebar();
        Bukkit.getScheduler().runTaskLater(plugin, this::finishEnd, endDelayTicks());
    }

    private List<UUID> ranking() {
        List<UUID> out = new ArrayList<>(playing);
        List<UUID> reversed = new ArrayList<>(eliminationOrder);
        Collections.reverse(reversed);
        out.addAll(reversed);
        return out;
    }

    private String nameOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? id.toString().substring(0, 8) : player.getName();
    }

    private void finishEnd() {
        removeShells();
        for (UUID id : allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                clearPlayer(player);
                restoreMaxHealth(player);
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
        eliminationOrder.clear();
        originalMaxHealth.clear();
        shellsFired = 0;
        soloTest = false;
        state = State.WAITING;
        liveSidebar.hide("bombard");
        countdown.update();
        reportArena();
    }

    private void saveStats() {
        if (stats == null) {
            return;
        }
        try {
            PlayerStats.save(STATS_NAMESPACE, stats);
        } catch (IOException exception) {
            plugin.getLogger().warning("保存玩家记录失败: " + exception.getMessage());
        }
    }

    // ---------- 落点扫描 ----------

    /** 扫描场地范围内配置的落点方块，记录每个柱子最高的那一块的上表面。 */
    public void scanLanding() {
        landing.clear();
        World world = arena.world();
        if (world == null || !arena.regionConfigured()) {
            return;
        }
        Set<Material> materials = landingMaterials();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                int startY = Math.min(maxY, world.getHighestBlockYAt(x, z) + 1);
                for (int y = startY; y >= minY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!materials.contains(block.getType())) {
                        continue;
                    }
                    // 只认露天的方块：被水或其它方块盖住的（水下地面、埋在地下的）不算落点
                    if (!block.getRelative(BlockFace.UP).getType().isAir()) {
                        break;
                    }
                    landing.add(new Location(world, x + 0.5, y + 1.0, z + 0.5));
                    break;
                }
            }
        }
    }

    public int landingCount() {
        return landing.size();
    }

    private void removeShells() {
        for (Shell shell : shells) {
            Entity entity = Bukkit.getEntity(shell.entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        shells.clear();
    }

    // ---------- 玩家状态 / 道具 ----------

    private void applyOneHeart(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            originalMaxHealth.putIfAbsent(player.getUniqueId(), attribute.getBaseValue());
            attribute.setBaseValue(ONE_HEART);
        }
        player.setHealth(ONE_HEART);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    private void restoreMaxHealth(Player player) {
        Double original = originalMaxHealth.remove(player.getUniqueId());
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(original == null ? DEFAULT_MAX_HEALTH : original);
        }
        player.setHealth(player.getMaxHealth());
    }

    /** 玩家之间的挤压由计分板队伍的 COLLISION_RULE 控制（客户端生效）。 */
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
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setExp(0f);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void giveWaitItems(Player player) {
        player.getInventory().setItem(waitSlot("return", 8), waitItem(returnKey, "return", Material.RED_BED));
        if (player.hasPermission("bombard.admin")) {
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

    // ---------- 查询 / 上报 ----------

    public BombardmentZoneArena arena() {
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
        UUID id = player.getUniqueId();
        return waiting.contains(id) || playing.contains(id) || eliminated.contains(id);
    }

    public int aliveCount() {
        return playing.size();
    }

    public int shellsInFlight() {
        return shells.size();
    }

    public boolean isLatePhase() {
        return latePhase;
    }

    public boolean isSoloTest() {
        return soloTest;
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

    private void updateActionBar() {
        String phaseText = latePhase ? "&4&l饱和轰炸" : "&e零星炮火";
        String bar = phaseText
                + " &7| &f剩余 &e" + Math.max(0, secondsLeft) + "s"
                + " &7| &f存活 &e" + playing.size();
        if (!latePhase) {
            int left = Math.max(0, lateStartSeconds() - elapsedTicks / 20);
            bar += " &7| &f饱和轰炸还有 &c" + left + "s";
        }
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
            liveSidebar.hide("bombard");
            return;
        }
        List<LiveSidebar.Line> lines = new ArrayList<>();
        lines.add(new LiveSidebar.Line("§6剩余时间", Math.max(0, secondsLeft)));
        lines.add(new LiveSidebar.Line("§f存活", playing.size()));
        lines.add(new LiveSidebar.Line("§c炮弹", shells.size()));
        liveSidebar.update("bombard",
                plugin.getConfig().getString("sidebar.title", "&c&l轰炸区 · 躲避炮火"),
                new ArrayList<>(viewers), lines);
        for (UUID id : playing) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                applyNoCollision(player);
            }
        }
    }

    private void reportArena() {
        boolean running = state != State.WAITING;
        String extra = running ? "剩余 " + secondsLeft + "s · " + playing.size() + " 人" : null;
        HubAPI.setArena(GAME, ARENA, running ? GameState.PLAYING : GameState.WAITING,
                waiting.size() + playing.size() + eliminated.size(), extra);
        HubAPI.setArenaSpectator(GAME, ARENA, arena.spectatorSpot());
    }

    public List<String> debugLines() {
        List<String> out = new ArrayList<>();
        out.add("状态 " + state + "，剩余 " + secondsLeft + "s，阶段 " + (latePhase ? "饱和轰炸" : "零星炮火")
                + "，存活 " + playing.size() + "，飞行中炮弹 " + shells.size() + "，累计发射 " + shellsFired);
        out.add("世界 " + arena.worldName() + "，区域 " + arena.sizeText()
                + "，出生点 " + arena.spawns().size() + "，炮口 " + arena.cannons().size()
                + "，可落点 " + landing.size() + " 个");
        return out;
    }

    public String roundText() {
        return Math.max(0, secondsLeft) + "s";
    }
}
