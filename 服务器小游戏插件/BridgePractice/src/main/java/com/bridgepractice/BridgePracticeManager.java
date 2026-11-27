package com.bridgepractice;

import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import com.hub.util.LiveSidebar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** 搭路练习核心：多房间、自动补方块、虚空重置、计时与纪录。 */
public final class BridgePracticeManager {

    public static final String GAME = "搭路练习";
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{1,20}$");
    private static final String NAMESPACE = "bridgepractice";

    private final BridgePracticePlugin plugin;
    private final File coursesFile;
    private final NamespacedKey itemKey;
    private final LiveSidebar liveSidebar;
    private final Map<String, PracticeCourse> courses = new LinkedHashMap<>();
    private final Map<UUID, BridgeSession> sessions = new LinkedHashMap<>();
    private final Map<String, RoomState> rooms = new LinkedHashMap<>();

    private YamlConfiguration stats;
    private boolean dirtyStats;
    private BukkitTask tickTask;
    private BukkitTask refillTask;
    private BukkitTask saveTask;

    private int giveAmount;
    private int fallDepth;
    private int respawnDelay;
    private int successDelay;
    private double endRadius;
    private int topSize;
    private Material practiceBlock;
    private int clearMargin;
    private boolean cleanupOnStartup;

    public BridgePracticeManager(BridgePracticePlugin plugin) {
        this.plugin = plugin;
        this.coursesFile = new File(plugin.getDataFolder(), "courses.yml");
        this.itemKey = new NamespacedKey(plugin, "practice_item");
        this.liveSidebar = new LiveSidebar(plugin);
    }

    // ---------- 加载 ----------

    public void load() {
        readSettings();
        loadCourses();
        stats = PlayerStats.load(NAMESPACE, new File(plugin.getDataFolder(), "data.yml"));
        if (cleanupOnStartup) {
            cleanupAllCourses();
        }
        startTasks();
        plugin.getLogger().info("已加载 " + courses.size() + " 个搭路练习房间，练习方块 "
                + practiceBlock.name() + "。");
        for (PracticeCourse course : courses.values()) {
            plugin.getLogger().info(" - " + course.name() + "：" + (course.ready()
                    ? "起点/终点已设置" : "起点或终点未设置"));
        }
    }

    public void reload() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                leave(player, false);
            }
        }
        cancelTasks();
        plugin.reloadConfig();
        readSettings();
        loadCourses();
        stats = PlayerStats.load(NAMESPACE, new File(plugin.getDataFolder(), "data.yml"));
        dirtyStats = false;
        startTasks();
    }

    private void readSettings() {
        String raw = plugin.getConfig().getString("practice-block", "WHITE_WOOL");
        Material parsed = raw == null ? null : Material.matchMaterial(raw);
        practiceBlock = parsed == null ? Material.WHITE_WOOL : parsed;
        giveAmount = Math.max(1, Math.min(practiceBlock.getMaxStackSize(),
                plugin.getConfig().getInt("give-amount", 64)));
        fallDepth = Math.max(1, plugin.getConfig().getInt("fall-depth", 25));
        respawnDelay = Math.max(1, plugin.getConfig().getInt("respawn-delay-ticks", 10));
        successDelay = Math.max(1, plugin.getConfig().getInt("success-delay-ticks", 60));
        endRadius = Math.max(0.5, plugin.getConfig().getDouble("end-radius", 2.5));
        topSize = Math.max(1, plugin.getConfig().getInt("top-size", 10));
        clearMargin = Math.max(0, plugin.getConfig().getInt("clear-margin", 8));
        cleanupOnStartup = plugin.getConfig().getBoolean("cleanup-on-startup", true);
    }

    private void startTasks() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        int interval = Math.max(5, plugin.getConfig().getInt("refill-interval-ticks", 20));
        refillTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refillAll, interval, interval);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushStats, 100L, 100L);
    }

    private void cancelTasks() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    public void shutdown() {
        cancelTasks();
        for (BridgeSession session : new ArrayList<>(sessions.values())) {
            clearBlocks(session);
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && player.isOnline()) {
                session.savedState.apply(player);
            }
            HubAPI.clearGame(session.playerId);
        }
        sessions.clear();
        rooms.clear();
        liveSidebar.hideAll();
        if (dirtyStats) {
            saveStats();
        }
    }

    // ---------- 房间 ----------

    private void loadCourses() {
        courses.clear();
        if (!coursesFile.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(coursesFile);
        ConfigurationSection root = data.getConfigurationSection("courses");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            PracticeCourse course = new PracticeCourse(name);
            course.load(section);
            courses.put(name, course);
        }
    }

    public void saveCourses() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(List.of(
                "搭路练习房间 —— 由 /bridge create、/bridge setstart、/bridge setend 写入。",
                "不要手改坐标；名字不要带点号。"));
        ConfigurationSection root = data.createSection("courses");
        for (PracticeCourse course : courses.values()) {
            course.write(root.createSection(course.name()));
        }
        try {
            data.save(coursesFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("保存 courses.yml 失败: " + exception.getMessage());
        }
    }

    public List<PracticeCourse> courses() {
        return new ArrayList<>(courses.values());
    }

    public PracticeCourse course(String name) {
        if (name == null) {
            return null;
        }
        PracticeCourse exact = courses.get(name);
        if (exact != null) {
            return exact;
        }
        for (PracticeCourse course : courses.values()) {
            if (course.name().equalsIgnoreCase(name)) {
                return course;
            }
        }
        return null;
    }

    public String createCourse(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            return "名字只能包含中文、字母、数字、下划线和连字符，长度 1~20。";
        }
        if (course(name) != null) {
            return "已经有叫这个名字的房间了。";
        }
        courses.put(name, new PracticeCourse(name));
        saveCourses();
        return null;
    }

    public String deleteCourse(String name) {
        PracticeCourse course = course(name);
        if (course == null) {
            return "没有这个房间。";
        }
        if (playerCount(course.name()) > 0) {
            return "这个房间正有人在练习，等他们离开再删除。";
        }
        courses.remove(course.name());
        saveCourses();
        return null;
    }

    public String setStart(String name, Location location) {
        return setStart(name, location, 0);
    }

    /**
     * 设置起点。
     *
     * @param index 0 = 设成一个点（两个端点重合）；1 / 2 = 起点线的两个端点
     */
    public String setStart(String name, Location location, int index) {
        PracticeCourse course = course(name);
        if (course == null) {
            return "没有这个房间。";
        }
        if (playerCount(course.name()) > 0) {
            return "这个房间正有人在练习，等他们离开再改起点。";
        }
        if (index == 1 || index == 2) {
            course.setStartLinePoint(index, location);
        } else {
            course.setStartPoint(location);
        }
        saveCourses();
        return null;
    }

    public String setSpawn(String name, Location location) {
        PracticeCourse course = course(name);
        if (course == null) {
            return "没有这个房间。";
        }
        if (playerCount(course.name()) > 0) {
            return "这个房间正有人在练习，等他们离开再改出生点。";
        }
        course.setSpawn(location);
        saveCourses();
        return null;
    }

    /** 设置房间虚空高度：玩家 Y 低于它就算掉进虚空。 */
    public String setVoid(String name, int y) {
        PracticeCourse course = course(name);
        if (course == null) {
            return "没有这个房间。";
        }
        if (playerCount(course.name()) > 0) {
            return "这个房间正有人在练习，等他们离开再改虚空高度。";
        }
        course.setFallY(y);
        saveCourses();
        return null;
    }

    public String setEnd(String name, Location location) {
        return setEnd(name, location, 0);
    }

    /**
     * 设置终点。
     *
     * @param index 0 = 设成一个点（两个端点重合）；1 / 2 = 终点线的两个端点
     */
    public String setEnd(String name, Location location, int index) {
        PracticeCourse course = course(name);
        if (course == null) {
            return "没有这个房间。";
        }
        if (playerCount(course.name()) > 0) {
            return "这个房间正有人在练习，等他们离开再改终点。";
        }
        if (index == 1 || index == 2) {
            course.setEndLinePoint(index, location);
        } else {
            course.setEndPoint(location);
        }
        saveCourses();
        return null;
    }

    public String setIcon(String name, Material icon) {
        PracticeCourse course = course(name);
        if (course == null) {
            return "没有这个房间。";
        }
        course.setIcon(icon);
        saveCourses();
        return null;
    }

    /** 沿「起点→终点线最近点」走廊移除练习方块（用于清理崩溃/异常退出留下的方块）。 */
    public int clearCourseArea(PracticeCourse course) {
        if (course == null || !course.ready()) {
            return 0;
        }
        Location start = course.start();
        Location end = course.facingTarget();
        if (start == null || end == null || start.getWorld() == null
                || !start.getWorld().equals(end.getWorld())) {
            return 0;
        }
        int minY = Math.min(start.getBlockY(), end.getBlockY()) - 4;
        int maxY = Math.max(start.getBlockY(), end.getBlockY()) + 1;
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(length));
        java.util.Set<Pos> seen = new java.util.HashSet<>();
        int removed = 0;
        for (int step = 0; step <= steps; step++) {
            double ratio = (double) step / steps;
            int centerX = (int) Math.round(start.getX() + dx * ratio);
            int centerZ = (int) Math.round(start.getZ() + dz * ratio);
            for (int offsetX = -clearMargin; offsetX <= clearMargin; offsetX++) {
                for (int offsetZ = -clearMargin; offsetZ <= clearMargin; offsetZ++) {
                    int x = centerX + offsetX;
                    int z = centerZ + offsetZ;
                    for (int y = minY; y <= maxY; y++) {
                        Pos pos = new Pos(start.getWorld().getName(), x, y, z);
                        if (!seen.add(pos)) {
                            continue;
                        }
                        Block block = start.getWorld().getBlockAt(x, y, z);
                        if (block.getType() == practiceBlock) {
                            block.setType(Material.AIR, false);
                            removed++;
                        }
                    }
                }
            }
        }
        return removed;
    }

    private void cleanupAllCourses() {
        for (PracticeCourse course : courses.values()) {
            int removed = clearCourseArea(course);
            if (removed > 0) {
                plugin.getLogger().info("已清理房间「" + course.name() + "」的残留练习方块 "
                        + removed + " 个。");
            }
        }
    }

    public int playerCount(String courseName) {
        RoomState room = rooms.get(courseName);
        return room == null ? 0 : room.players.size();
    }

    public String ownerName(String courseName) {
        RoomState room = rooms.get(courseName);
        if (room == null || room.owner == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(room.owner);
        return player == null ? nameOf(room.owner) : player.getName();
    }

    public boolean allowsJoin(String courseName) {
        RoomState room = rooms.get(courseName);
        return room == null || room.players.isEmpty() || room.allowJoin;
    }

    public boolean allowsBreakOthers(String courseName) {
        RoomState room = rooms.get(courseName);
        return room != null && room.allowBreakOthers;
    }

    public boolean isOwner(Player player) {
        BridgeSession session = session(player);
        if (session == null) {
            return false;
        }
        RoomState room = rooms.get(session.course.name());
        return room != null && player.getUniqueId().equals(room.owner);
    }

    public void toggleJoin(Player player) {
        BridgeSession session = session(player);
        if (session == null) {
            Chat.send(player, "&e你不在练习中。");
            return;
        }
        RoomState room = rooms.get(session.course.name());
        if (room == null) {
            return;
        }
        if (!player.getUniqueId().equals(room.owner)) {
            Chat.send(player, "&c只有房主可以设置房间是否允许加入。");
            return;
        }
        room.allowJoin = !room.allowJoin;
        giveRoomTool(player, room.allowJoin);
        String state = room.allowJoin ? "&a开放加入" : "&c关闭加入";
        for (UUID id : room.players) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                Chat.send(member, "&e房主 &f" + player.getName() + " &e把房间设为 " + state + "&e。");
            }
        }
    }

    public void toggleBreakOthers(Player player) {
        BridgeSession session = session(player);
        if (session == null) {
            Chat.send(player, "&e你不在练习中。");
            return;
        }
        RoomState room = rooms.get(session.course.name());
        if (room == null) {
            return;
        }
        if (!player.getUniqueId().equals(room.owner)) {
            Chat.send(player, "&c只有房主可以设置能不能挖别人的方块。");
            return;
        }
        room.allowBreakOthers = !room.allowBreakOthers;
        giveBreakTool(player, room.allowBreakOthers);
        String state = room.allowBreakOthers ? "&a允许" : "&c禁止";
        for (UUID id : room.players) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) {
                Chat.send(member, "&e房主 &f" + player.getName()
                        + " &e把「挖别人方块」设为 " + state + "&e。");
            }
        }
    }

    /** 房主允许时，拆掉房间里其他玩家本次搭的方块。返回是否处理成功。 */
    public boolean breakOtherPlayerBlock(BridgeSession breaker, Block block) {
        RoomState room = rooms.get(breaker.course.name());
        if (room == null || !room.allowBreakOthers || block == null) {
            return false;
        }
        Pos pos = Pos.of(block);
        for (BridgeSession other : sessions.values()) {
            if (other != breaker
                    && other.course.name().equals(breaker.course.name())
                    && other.placedBlocks.remove(pos)) {
                return true;
            }
        }
        return false;
    }

    private void removeFromRoom(BridgeSession session) {        RoomState room = rooms.get(session.course.name());
        if (room == null) {
            return;
        }
        room.players.remove(session.playerId);
        if (session.playerId.equals(room.owner)) {
            room.owner = room.players.isEmpty() ? null : room.players.iterator().next();
            if (room.owner != null) {
                transferOwner(session.course, room.owner);
            }
        }
        if (room.players.isEmpty()) {
            rooms.remove(session.course.name());
        }
    }

    private void transferOwner(PracticeCourse course, UUID newOwner) {
        Player player = Bukkit.getPlayer(newOwner);
        if (player == null) {
            return;
        }
        RoomState room = rooms.get(course.name());
        giveRoomTool(player, room == null || room.allowJoin);
        giveBreakTool(player, room != null && room.allowBreakOthers);
        Chat.send(player, "&e你现在是房间「&f" + course.name()
                + "&e」的房主，右键第 6、7 格可以设置房间。");
    }

    // ---------- 加入 / 离开 ----------

    public boolean isPracticing(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public BridgeSession session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public boolean tryJoin(Player player, String courseName) {
        PracticeCourse course = course(courseName);
        if (course == null) {
            Chat.send(player, "&c没有这个房间：&f" + courseName + " &7（用 &f/bridge list &7查看）");
            return false;
        }
        return join(player, course);
    }

    public boolean join(Player player, PracticeCourse course) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) {
            Chat.send(player, "&e你已经在练习中了。");
            return false;
        }
        if (!player.hasPermission("bridgepractice.play")) {
            Chat.send(player, "&c你没有参与搭路练习的权限。");
            return false;
        }
        if (!course.ready()) {
            Chat.send(player, "&c房间「" + course.name() + "」还没设置好起点或终点，请管理员先设置。");
            return false;
        }
        RoomState room = rooms.get(course.name());
        if (room != null && !room.players.isEmpty() && !room.allowJoin) {
            Chat.send(player, "&c房间「" + course.name() + "」已关闭加入，等房主开放后再进。");
            return false;
        }
        int fallY = course.fallY(fallDepth);
        PlayerState saved = PlayerState.capture(player);
        BridgeSession session = new BridgeSession(id, course, saved, fallY);
        sessions.put(id, session);
        RoomState state = rooms.computeIfAbsent(course.name(), key -> new RoomState());
        boolean firstInRoom = state.players.isEmpty();
        state.players.add(id);
        if (state.owner == null) {
            state.owner = id;
        }
        prepare(player, course);
        HubAPI.setPlaying(id, GAME);
        Chat.title(player, "&b&l搭路练习", "&f" + course.name() + " &7· 方块自动补满");
        Chat.send(player, "&a已进入房间 &f" + course.name() + "&a，向前搭路吧！掉下虚空会自动回到出生点。");
        if (firstInRoom) {
            Chat.send(player, "&e你是这个房间的房主，右键第 6、7 格可以设置挖方块/加入。");
        }
        Chat.send(player, "&7第 1 格：练习方块（自动补满） &8| &e第 8 格：回出生点 &8| &c第 9 格：离开（回主城）");
        return true;
    }

    private void prepare(Player player, PracticeCourse course) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        inventory.setItem(0, blocks());

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setFireTicks(0);
        player.setFallDistance(0);
        setFullHealth(player);
        player.setFoodLevel(20);
        player.setSaturation(20f);

        givePracticeTools(player, course);
        teleportToSpawn(player, course);
    }

    public boolean leave(Player player) {
        return leave(player, true);
    }

    /**
     * 离开练习。
     *
     * @param toLobby true = 恢复背包后回主城出生点；false = 只恢复背包（回主城由主城插件负责）
     */
    public boolean leave(Player player, boolean toLobby) {
        if (player == null) {
            return false;
        }
        BridgeSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        removeFromRoom(session);
        clearBlocks(session);
        session.savedState.apply(player);
        liveSidebar.hide(sidebarKey(session.playerId));
        if (toLobby) {
            HubAPI.teleportToLobby(player);
        }
        HubAPI.clearGame(player.getUniqueId());
        Chat.send(player, "&7已离开搭路练习。");
        return true;
    }

    /** 玩家下线：清掉本次方块并释放房间，不做背包恢复。 */
    public void quit(Player player) {
        if (player == null) {
            return;
        }
        BridgeSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        removeFromRoom(session);
        clearBlocks(session);
        liveSidebar.hide(sidebarKey(session.playerId));
        HubAPI.clearGame(player.getUniqueId());
    }

    /** 主动回到起点（命令/道具）。 */
    public void resetToStart(Player player) {
        BridgeSession session = session(player);
        if (session == null) {
            return;
        }
        resetRun(session);
    }

    // ---------- 每 tick 判定 ----------

    private void tick() {
        long now = System.currentTimeMillis();
        for (BridgeSession session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (session.pending) {
                continue;
            }
            Location location = player.getLocation();
            Location start = session.course.start();
            if (location.getWorld() == null || start == null
                    || !location.getWorld().equals(start.getWorld())) {
                Chat.actionBar(player, "&e你离开了练习房间，正在送回出生点…");
                resetRun(session);
                continue;
            }
            if (location.getY() < session.fallY) {
                onFall(session, player, now);
                continue;
            }
            if (session.course.hasEnd()
                    && session.course.isAtEnd(location, session.course.endRadius(endRadius))) {
                onFinish(session, player, now);
                continue;
            }
            if (session.started()) {
                double moved = session.course.distanceToStart(location);
                if (moved > session.maxDisplacement) {
                    session.maxDisplacement = moved;
                }
            }
            if (now - session.lastHud >= 400) {
                session.lastHud = now;
                updateSidebar(player, session, now);
            }
        }
    }

    private void startRun(BridgeSession session, Player player, long now) {
        session.startedAt = now;
        String path = "plays." + session.course.name() + "." + session.playerId;
        stats.set(path, stats.getInt(path, 0) + 1);
        dirtyStats = true;
        Chat.actionBar(player, "&a&l计时开始！");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    private void onFall(BridgeSession session, Player player, long now) {
        session.pending = true;
        String path = "falls." + session.course.name() + "." + session.playerId;
        stats.set(path, stats.getInt(path, 0) + 1);
        dirtyStats = true;
        long elapsed = session.started() ? session.elapsed(now) : 0;
        reportRun(player, false, elapsed, session.maxDisplacement, session.runBlocks);
        sendRoomBest(player, session.course.name());
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.9f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(session.playerId) != session) {
                return;
            }
            resetRun(session);
        }, respawnDelay);
    }

    private void onFinish(BridgeSession session, Player player, long now) {
        session.pending = true;
        long elapsed = session.started() ? session.elapsed(now) : 1L;
        if (!session.started()) {
            String playPath = "plays." + session.course.name() + "." + session.playerId;
            stats.set(playPath, stats.getInt(playPath, 0) + 1);
            dirtyStats = true;
        }
        double displacement = session.course.horizontalLength();
        boolean newBest = recordFinish(session, player, elapsed);
        boolean courseTop = isCourseTop(session.course.name(), session.playerId);

        reportRun(player, true, elapsed, displacement, session.runBlocks);
        sendRoomBest(player, session.course.name());
        Chat.title(player, newBest ? "&a&l新个人纪录！" : "&a&l到达终点！",
                "&f用时 " + formatTime(elapsed));
        long best = bestOf(session.course.name(), session.playerId);
        if (best > 0) {
            Chat.send(player, "&7你的最佳：&b" + formatTime(best));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        if (newBest && courseTop) {
            Chat.broadcast("&e[搭路练习] &f" + player.getName() + " &7在房间 &b"
                    + session.course.name() + " &7刷新纪录：&a" + formatTime(elapsed));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(session.playerId) != session) {
                return;
            }
            resetRun(session);
        }, successDelay);
    }

    /** 掉落/到达终点后的本次数据：时间、水平位移、平均速度、放置方块数。 */
    private void reportRun(Player player, boolean finished, long elapsed, double displacement, int blocks) {
        double seconds = elapsed > 0 ? elapsed / 1000.0 : 0;
        double speed = seconds > 0 ? displacement / seconds : 0;
        Chat.send(player, "&6===== " + (finished ? "本次成绩" : "本次掉落") + " =====");
        Chat.send(player, "&7搭路时间：&f" + (elapsed > 0 ? formatTime(elapsed) : "--"));
        Chat.send(player, "&7水平位移：&f" + String.format(Locale.ROOT, "%.1f", displacement) + " 格");
        Chat.send(player, "&7平均速度：&f"
                + (seconds > 0 ? String.format(Locale.ROOT, "%.2f 格/秒", speed) : "--"));
        Chat.send(player, "&7放置方块：&f" + blocks + " 个");
    }

    private void sendRoomBest(Player player, String courseName) {
        List<TopEntry> top = topEntries(courseName);
        if (top.isEmpty()) {
            Chat.send(player, "&7房间最快：&8暂无纪录");
            return;
        }
        TopEntry best = top.get(0);
        Chat.send(player, "&7房间最快：&f" + best.name() + " &a" + formatTime(best.millis()));
    }

    /** 回到出生点、清掉本次搭的方块，开始下一轮。 */
    private void resetRun(BridgeSession session) {
        clearBlocks(session);
        session.runBlocks = 0;
        session.maxDisplacement = 0;
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && player.isOnline()) {
            teleportToSpawn(player, session.course);
            setFullHealth(player);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
            player.setFallDistance(0);
            givePracticeTools(player, session.course);
            ensureBlocks(player);
        }
        session.startedAt = 0;
        session.pending = false;
    }

    private void teleportToSpawn(Player player, PracticeCourse course) {
        Location spawn = course.spawnPoint();
        if (spawn == null) {
            return;
        }
        Location face = course.endCenter();
        if (face == null) {
            face = course.start();
        }
        PracticeCourse.faceEnd(spawn, face);
        player.teleport(spawn);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    /** 右侧计分板：用时 / 距终点 / 个人最佳（每个练习者一块，互不影响）。 */
    private void updateSidebar(Player player, BridgeSession session, long now) {
        double distance = session.course.horizontalDistanceToEnd(player.getLocation());
        String away = distance >= 0 ? String.format(Locale.ROOT, "%.1f", distance) : "--";
        long best = bestOf(session.course.name(), session.playerId);
        List<LiveSidebar.Line> lines = new ArrayList<>();
        lines.add(session.started()
                ? new LiveSidebar.Line("§b用时 §f" + formatTime(session.elapsed(now)), 3)
                : new LiveSidebar.Line("§e放下第一个方块开始计时", 3));
        lines.add(new LiveSidebar.Line("§b距终点 §f" + away + " 格", 2));
        lines.add(new LiveSidebar.Line("§b个人最佳 §f" + (best > 0 ? formatTime(best) : "--"), 1));
        liveSidebar.update(sidebarKey(session.playerId), "&b&l搭路练习",
                List.of(session.playerId), lines);
    }

    private String sidebarKey(UUID id) {
        return "bridge:" + id;
    }

    // ---------- 方块跟踪与清理 ----------

    public void trackPlacement(BridgeSession session, Block block) {
        if (session != null && block != null) {
            session.placedBlocks.add(Pos.of(block));
        }
    }

    /** 玩家放下一个方块：记录它、必要时开始计时，并稍后补满物品栏。 */
    public void notePlacement(Player player, Block block) {
        BridgeSession session = session(player);
        if (session == null) {
            return;
        }
        trackPlacement(session, block);
        session.runBlocks++;
        if (!session.pending && !session.started()) {
            startRun(session, player, System.currentTimeMillis());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isPracticing(player)) {
                ensureBlocks(player);
            }
        });
    }

    public boolean untrackPlacement(BridgeSession session, Block block) {
        return session != null && block != null && session.placedBlocks.remove(Pos.of(block));
    }

    public boolean isTrackedByAnySession(Block block) {
        if (block == null) {
            return false;
        }
        Pos pos = Pos.of(block);
        for (BridgeSession session : sessions.values()) {
            if (session.placedBlocks.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private void clearBlocks(BridgeSession session) {
        Material blockType = practiceBlock;
        for (Pos pos : session.placedBlocks) {
            Block block = pos.block();
            if (block != null && block.getType() == blockType) {
                block.setType(Material.AIR, false);
            }
        }
        session.placedBlocks.clear();
    }

    // ---------- 自动补方块 ----------

    private void refillAll() {
        for (BridgeSession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && player.isOnline()) {
                ensureBlocks(player);
            }
        }
    }

    public void ensureBlocks(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean found = false;
        for (int slot = 0; slot <= 35; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == practiceBlock) {
                found = true;
                if (item.getAmount() < giveAmount) {
                    ItemStack toppedUp = item.clone();
                    toppedUp.setAmount(giveAmount);
                    inventory.setItem(slot, toppedUp);
                }
            }
        }
        if (found) {
            return;
        }
        ItemStack stack = new ItemStack(practiceBlock, giveAmount);
        int held = inventory.getHeldItemSlot();
        ItemStack heldItem = inventory.getItem(held);
        if (heldItem == null || heldItem.getType().isAir()) {
            inventory.setItem(held, stack);
            return;
        }
        for (int slot = 0; slot <= 6; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, stack);
                return;
            }
        }
        int empty = inventory.firstEmpty();
        if (empty >= 0) {
            inventory.setItem(empty, stack);
        } else {
            inventory.setItem(held, stack);
        }
    }

    private void givePracticeTools(Player player, PracticeCourse course) {
        if (findTool(player, PracticeItem.RESET) == null) {
            giveTool(player, PracticeItem.RESET);
        }
        if (findTool(player, PracticeItem.LEAVE) == null) {
            giveTool(player, PracticeItem.LEAVE);
        }
        RoomState room = rooms.get(course.name());
        if (room != null && player.getUniqueId().equals(room.owner)) {
            giveRoomTool(player, room.allowJoin);
            giveBreakTool(player, room.allowBreakOthers);
        }
    }

    private ItemStack blocks() {
        return new ItemStack(practiceBlock, giveAmount);
    }

    // ---------- 练习道具 ----------

    public enum PracticeItem {
        RESET, LEAVE, ROOM, ROOM_BREAK
    }

    private void giveTool(Player player, PracticeItem item) {
        ItemStack stack = tool(item);
        int slot = item == PracticeItem.RESET ? 7 : 8;
        int existing = findToolSlot(player, item);
        player.getInventory().setItem(existing >= 0 ? existing : slot, stack);
    }

    private void giveRoomTool(Player player, boolean allowJoin) {
        ItemStack stack = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(allowJoin
                    ? "&e房间加入：&a开放 &7(右键切换)"
                    : "&e房间加入：&c关闭 &7(右键切换)")));
            meta.lore(List.of(Chat.noItalic(Chat.color(allowJoin
                    ? "&7当前允许其他玩家进入这个房间"
                    : "&7当前关闭，其他玩家无法进入"))));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING,
                    PracticeItem.ROOM.name());
            stack.setItemMeta(meta);
        }
        int slot = findToolSlot(player, PracticeItem.ROOM);
        player.getInventory().setItem(slot >= 0 ? slot : 6, stack);
    }

    private void giveBreakTool(Player player, boolean allow) {
        ItemStack stack = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(allow
                    ? "&e挖别人方块：&a允许 &7(右键切换)"
                    : "&e挖别人方块：&c禁止 &7(右键切换)")));
            meta.lore(List.of(Chat.noItalic(Chat.color(allow
                    ? "&7房内玩家可以拆别人本次搭的方块"
                    : "&7只能拆自己本次搭的方块"))));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING,
                    PracticeItem.ROOM_BREAK.name());
            stack.setItemMeta(meta);
        }
        int slot = findToolSlot(player, PracticeItem.ROOM_BREAK);
        player.getInventory().setItem(slot >= 0 ? slot : 5, stack);
    }

    private ItemStack tool(PracticeItem item) {
        Material material = item == PracticeItem.RESET ? Material.NETHER_STAR : Material.BARRIER;
        String name = item == PracticeItem.RESET ? "&e回到出生点 &7(右键)" : "&c离开练习 &7(右键)";
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(name)));
            meta.lore(List.of(Chat.noItalic(Chat.color(item == PracticeItem.RESET
                    ? "&7清掉本次搭的方块，回到出生点"
                    : "&7恢复背包并离开练习"))));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, item.name());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public PracticeItem practiceItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return PracticeItem.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ItemStack findTool(Player player, PracticeItem item) {
        int slot = findToolSlot(player, item);
        return slot < 0 ? null : player.getInventory().getItem(slot);
    }

    private int findToolSlot(Player player, PracticeItem item) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 35; slot++) {
            if (practiceItem(inventory.getItem(slot)) == item) {
                return slot;
            }
        }
        return -1;
    }

    // ---------- 纪录 ----------

    public long bestOf(String courseName, UUID playerId) {
        if (stats == null || courseName == null || playerId == null) {
            return -1;
        }
        long value = stats.getLong("best." + courseName + "." + playerId, -1);
        return value <= 0 ? -1 : value;
    }

    public int finishesOf(String courseName, UUID playerId) {
        return stats == null ? 0 : stats.getInt("finishes." + courseName + "." + playerId, 0);
    }

    public int fallsOf(String courseName, UUID playerId) {
        return stats == null ? 0 : stats.getInt("falls." + courseName + "." + playerId, 0);
    }

    public record TopEntry(UUID id, String name, long millis) {
    }

    public List<TopEntry> topEntries(String courseName) {
        List<TopEntry> list = new ArrayList<>();
        if (stats == null) {
            return list;
        }
        ConfigurationSection section = stats.getConfigurationSection("best." + courseName);
        if (section == null) {
            return list;
        }
        for (String rawId : section.getKeys(false)) {
            long millis = section.getLong(rawId, -1);
            if (millis <= 0) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            list.add(new TopEntry(id, nameOf(id), millis));
        }
        list.sort(Comparator.comparingLong(TopEntry::millis));
        if (list.size() > topSize) {
            return new ArrayList<>(list.subList(0, topSize));
        }
        return list;
    }

    public String topName(String courseName) {
        List<TopEntry> top = topEntries(courseName);
        return top.isEmpty() ? null : top.get(0).name();
    }

    private boolean recordFinish(BridgeSession session, Player player, long elapsed) {
        UUID id = session.playerId;
        String course = session.course.name();
        String bestPath = "best." + course + "." + id;
        long before = stats.getLong(bestPath, -1);
        boolean newBest = before <= 0 || elapsed < before;
        if (newBest) {
            stats.set(bestPath, elapsed);
            stats.set("bestAt." + course + "." + id, System.currentTimeMillis());
        }
        stats.set("names." + id, player.getName());
        String finishPath = "finishes." + course + "." + id;
        stats.set(finishPath, stats.getInt(finishPath, 0) + 1);
        dirtyStats = true;
        return newBest;
    }

    private boolean isCourseTop(String courseName, UUID id) {
        List<TopEntry> top = topEntries(courseName);
        return !top.isEmpty() && top.get(0).id().equals(id);
    }

    private String nameOf(UUID id) {
        String stored = stats == null ? null : stats.getString("names." + id);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? "未知玩家" : offline.getName();
    }

    private void flushStats() {
        if (dirtyStats) {
            saveStats();
        }
    }

    private void saveStats() {
        try {
            PlayerStats.save(NAMESPACE, stats);
            dirtyStats = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("保存搭路练习纪录失败: " + exception.getMessage());
        }
    }

    private void setFullHealth(Player player) {
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;
        player.setHealth(Math.max(1.0, maxHealth));
    }

    public static String formatTime(long millis) {
        if (millis <= 0) {
            return "--";
        }
        double seconds = millis / 1000.0;
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.2f 秒", seconds);
        }
        long minutes = (long) (seconds / 60);
        return String.format(Locale.ROOT, "%d 分 %.2f 秒", minutes, seconds - minutes * 60);
    }

    /** 运行时房间状态：房主、是否允许加入、房内玩家（按进房顺序）。 */
    private static final class RoomState {
        UUID owner;
        boolean allowJoin = true;
        boolean allowBreakOthers;
        final java.util.LinkedHashSet<UUID> players = new java.util.LinkedHashSet<>();
    }
}
