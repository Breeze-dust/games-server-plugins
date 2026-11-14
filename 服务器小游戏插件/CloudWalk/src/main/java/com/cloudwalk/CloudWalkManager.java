package com.cloudwalk;

import com.hub.api.GameTitles;
import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 云中漫步核心：通道分配、真实方块路径、落地判定、结算、最高分记录。
 *
 * <p>每名玩家一份区域副本：沿 X（或 Z）并排、逐层抬高，互不重叠。
 * 所有方块读写都在主线程（2 tick 一次的循环里完成）。
 */
public class CloudWalkManager {

    private static final String NAMESPACE = "cloudwalk";
    private static final int TICK_PERIOD = 1;
    private static final long PAGE_VIEW_MILLIS = 20_000L;
    /** 全息文字的行高（字体行高 0.25 × 显示缩放 1.3）。 */
    private static final double LINE_HEIGHT = 0.25 * 1.3;
    /** 主榜最后一行与底部「下一页」之间的空隙。 */
    private static final double FOOTER_GAP = 0.35;
    private static final String FOOTER_TEXT = "&e&l[ 下一页 >>> ]";

    private final CloudWalkPlugin plugin;
    private final Map<UUID, ParkourSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, List<Material>> playerBlocks = new LinkedHashMap<>();
    private final Map<UUID, SavedInventory> inventories = new LinkedHashMap<>();
    private final Map<String, PathGenerator> generators = new LinkedHashMap<>();
    private final Map<UUID, PageView> pageViews = new LinkedHashMap<>();
    private final Random random = new Random();
    private final NamespacedKey toolKey;
    private final NamespacedKey soundKey;

    private CloudWalkSettings settings;
    private LaneBox baseBox;
    private BlockPos baseStart;
    private YamlConfiguration data;

    private BukkitTask tickTask;
    private BukkitTask leaderboardTask;
    private boolean[] lanesUsed = new boolean[0];
    private int tickCounter;

    private final List<LeaderBoard> leaderboards = new ArrayList<>();

    public CloudWalkManager(CloudWalkPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "palette_tool");
        this.soundKey = new NamespacedKey(plugin, "sound_toggle");
    }

    // ---------- 加载 / 保存 ----------

    public void load() {
        settings = CloudWalkSettings.load(plugin.getConfig());
        baseBox = buildBaseBox(settings);
        baseStart = buildBaseStart(settings);
        generators.clear();
        for (CloudWalkSettings.Difficulty difficulty : settings.difficulties) {
            generators.put(difficulty.id, new PathGenerator(difficulty.distances, settings.heights,
                    difficulty.allow4JumpUp, settings.turnChance));
        }
        playerBlocks.clear();
        ensureLaneArray();
        clearPageViews();

        if (data == null) {
            data = PlayerStats.load(NAMESPACE, new File(plugin.getDataFolder(), "data.yml"));
        }
        migrateLegacyRecords();
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_PERIOD);
        }

        // 头衔定义提前建好；排行榜与头衔每 5 秒兜底同步一次（头衔插件可能比本插件晚启用）
        for (CloudWalkSettings.Difficulty difficulty : settings.difficulties) {
            if (difficulty.enabled) {
                GameTitles.ensure(difficulty.titleId, difficulty.titleName,
                        difficulty.titleColor, difficulty.titleDesc);
            }
        }
        loadLeaderboards();
        if (leaderboardTask == null) {
            leaderboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maintenance, 100L, 100L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::maintenance, 40L);
        syncTitles();

        plugin.getLogger().info("云中漫步已加载：区域" + (baseBox == null ? "未配置" : "已配置")
                + "，起点" + (baseStart == null ? "未配置" : "已配置")
                + "，通道上限 " + settings.maxLanes
                + "，最高分记录 " + recordCount() + " 人。");
    }

    public void onDisable() {
        clearPageViews();
        for (ParkourSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            finish(p, s, "服务器关闭", false, false);
        }
    }

    public void reload() {
        for (ParkourSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            finish(p, s, "重载", true, true);
        }
        plugin.reloadConfig();
        load();
    }

    private LaneBox buildBaseBox(CloudWalkSettings s) {
        if (s.regionWorld == null || s.regionWorld.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(s.regionWorld);
        if (world == null) {
            return null;
        }
        return new LaneBox(world,
                Math.min(s.x1, s.x2), Math.max(s.x1, s.x2),
                Math.min(s.y1, s.y2), Math.max(s.y1, s.y2),
                Math.min(s.z1, s.z2), Math.max(s.z1, s.z2));
    }

    private BlockPos buildBaseStart(CloudWalkSettings s) {
        if (s.startWorld == null || s.startWorld.isEmpty() || baseBox == null) {
            return null;
        }
        if (!baseBox.world().getName().equals(s.startWorld)) {
            return null;
        }
        return new BlockPos(s.startX, s.startY, s.startZ);
    }

    private void ensureLaneArray() {
        if (lanesUsed.length == settings.maxLanes) {
            return;
        }
        boolean[] next = new boolean[settings.maxLanes];
        for (ParkourSession s : sessions.values()) {
            if (s.lane >= 0 && s.lane < next.length) {
                next[s.lane] = true;
            }
        }
        lanesUsed = next;
    }

    private void applyAndSave() {
        baseBox = buildBaseBox(settings);
        baseStart = buildBaseStart(settings);
        ensureLaneArray();
        saveSettingsToConfig();
    }

    private void saveSettingsToConfig() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("region.world", settings.regionWorld);
        cfg.set("region.x1", settings.x1);
        cfg.set("region.y1", settings.y1);
        cfg.set("region.z1", settings.z1);
        cfg.set("region.x2", settings.x2);
        cfg.set("region.y2", settings.y2);
        cfg.set("region.z2", settings.z2);

        cfg.set("start.world", settings.startWorld);
        cfg.set("start.x", settings.startX);
        cfg.set("start.y", settings.startY);
        cfg.set("start.z", settings.startZ);

        if (settings.lobby != null && settings.lobby.getWorld() != null) {
            cfg.set("lobby.world", settings.lobby.getWorld().getName());
            cfg.set("lobby.x", settings.lobby.getX());
            cfg.set("lobby.y", settings.lobby.getY());
            cfg.set("lobby.z", settings.lobby.getZ());
            cfg.set("lobby.yaw", (double) settings.lobby.getYaw());
            cfg.set("lobby.pitch", (double) settings.lobby.getPitch());
        }
        plugin.saveConfig();
    }

    // ---------- 管理命令用的设置 ----------

    public boolean isConfigured() {
        return baseBox != null && baseStart != null && baseBox.contains(baseStart);
    }

    public void setPos1(Player p) {
        Location loc = p.getLocation();
        settings.regionWorld = loc.getWorld().getName();
        settings.x1 = loc.getBlockX();
        settings.y1 = loc.getBlockY();
        settings.z1 = loc.getBlockZ();
        applyAndSave();
        Chat.send(p, "&a区域角点 1：&f" + settings.regionWorld + " "
                + settings.x1 + ", " + settings.y1 + ", " + settings.z1);
    }

    public void setPos2(Player p) {
        Location loc = p.getLocation();
        settings.regionWorld = loc.getWorld().getName();
        settings.x2 = loc.getBlockX();
        settings.y2 = loc.getBlockY();
        settings.z2 = loc.getBlockZ();
        applyAndSave();
        Chat.send(p, "&a区域角点 2：&f" + settings.regionWorld + " "
                + settings.x2 + ", " + settings.y2 + ", " + settings.z2);
    }

    public void setStart(Player p) {
        Location loc = p.getLocation();
        settings.startWorld = loc.getWorld().getName();
        settings.startX = loc.getBlockX();
        settings.startY = loc.getBlockY() - 1;
        settings.startZ = loc.getBlockZ();
        applyAndSave();
        Chat.send(p, "&a起点方块：&f" + settings.startWorld + " "
                + settings.startX + ", " + settings.startY + ", " + settings.startZ
                + " &7（玩家会站在它上面）");
        if (baseBox != null && baseStart != null && !baseBox.contains(baseStart)) {
            Chat.send(p, "&e注意：这个起点不在跑酷区域内，生成会失败。");
        }
    }

    public void setLobby(Player p) {
        settings.lobby = p.getLocation().clone();
        saveSettingsToConfig();
        Chat.send(p, "&a回大厅位置已保存。");
    }

    public List<String> regionInfo() {
        List<String> out = new ArrayList<>();
        if (baseBox == null) {
            out.add("&c跑酷区域未配置（/cloudwalk pos1、pos2）。");
        } else {
            out.add("&b区域：&f" + baseBox.world().getName()
                    + " &7(" + baseBox.minX() + "," + baseBox.minY() + "," + baseBox.minZ() + ")"
                    + " ~ (" + baseBox.maxX() + "," + baseBox.maxY() + "," + baseBox.maxZ() + ")");
        }
        if (baseStart == null) {
            out.add("&c起点未配置（/cloudwalk setstart）。");
        } else {
            boolean inside = baseBox != null && baseBox.contains(baseStart);
            out.add("&b起点：&f" + baseStart.x() + "," + baseStart.y() + "," + baseStart.z()
                    + (inside ? "" : " &c(不在区域内)"));
        }
        out.add("&b通道：&f" + settings.axis + " 轴并排，间隔 " + settings.laneGap
                + " 格，每层抬高 " + settings.heightStep + " 格，上限 " + settings.maxLanes
                + " 条，当前 &b" + sessions.size() + " &f人在跑。");
        StringBuilder difficultyLine = new StringBuilder();
        for (CloudWalkSettings.Difficulty difficulty : settings.difficulties) {
            if (difficultyLine.length() > 0) {
                difficultyLine.append(" &7/ ");
            }
            difficultyLine.append(difficulty.enabled ? difficulty.color : "&8")
                    .append(difficulty.name)
                    .append(difficulty.enabled ? "" : "(未开放)")
                    .append("&7 距离").append(Arrays.toString(difficulty.distances))
                    .append(difficulty.allow4JumpUp ? "(含4+上升)" : "");
        }
        out.add("&b难度：" + difficultyLine);
        out.add("&b生成：&f高度 " + Arrays.toString(settings.heights)
                + "，拐弯概率 " + settings.turnChance
                + "，预生成 " + settings.queueLength + " 块，方块池 " + settings.palette.size() + " 种");
        return out;
    }

    // ---------- 加入 / 退出 ----------

    public void openDifficultyMenu(Player p) {
        if (sessions.containsKey(p.getUniqueId())) {
            Chat.send(p, "&e你已经在跑酷中，用 &f/cloudwalk leave &e退出。");
            return;
        }
        p.openInventory(new DifficultyMenu(this, p).getInventory());
    }

    public void join(Player p, CloudWalkSettings.Difficulty difficulty) {
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id)) {
            Chat.send(p, "&e你已经在跑酷中，用 &f/cloudwalk leave &e退出。");
            return;
        }
        if (difficulty == null || !difficulty.enabled) {
            Chat.send(p, "&c这个难度还没有开放。");
            return;
        }
        if (!isConfigured()) {
            Chat.send(p, "&c跑酷区域还没配置，请联系管理员。");
            return;
        }
        if (HubAPI.gameOf(id) != null) {
            Chat.send(p, "&c你正在其它游戏中，先退出再来。");
            return;
        }
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            Chat.send(p, "&c旁观模式不能跑酷。");
            return;
        }
        PathGenerator generator = generators.get(difficulty.id);
        if (generator == null) {
            Chat.send(p, "&c难度配置异常，请联系管理员。");
            return;
        }

        int lane = allocateLane();
        if (lane < 0) {
            Chat.send(p, "&c跑道已满（上限 " + settings.maxLanes + " 人），稍后再来。");
            return;
        }

        LaneBox box = laneBox(lane);
        BlockPos start = translatedStart(lane);
        if (!box.contains(start)) {
            freeLane(lane);
            Chat.send(p, "&c起点不在跑酷区域内，请联系管理员。");
            return;
        }

        float yaw = p.getLocation().getYaw();
        Direction heading = Direction.fromYaw(yaw);
        List<BlockPos> path = new ArrayList<>();
        path.add(start);
        Direction cursor = heading;
        for (int i = 0; i < settings.queueLength; i++) {
            BlockPos tail = path.get(path.size() - 1);
            BlockPos next = generator.next(tail, cursor, new HashSet<>(path), box);
            if (next == null) {
                freeLane(lane);
                Chat.send(p, "&c这块区域放不下起始路径，请检查区域大小。");
                return;
            }
            cursor = Direction.between(tail, next);
            if (cursor == null) {
                cursor = heading;
            }
            path.add(next);
        }

        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.LEVITATION);
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        p.setFlying(false);

        // 进入时视角正对第一块：用起点到第一块的方向，而不是玩家原来的朝向
        Direction firstDirection = path.size() > 1 ? Direction.between(start, path.get(1)) : heading;
        float viewYaw = firstDirection == null ? yaw : firstDirection.yaw();
        Location spawn = new Location(box.world(),
                start.x() + 0.5, start.y() + 1.0, start.z() + 0.5, viewYaw, 0f);
        if (!p.teleport(spawn)) {
            freeLane(lane);
            Chat.send(p, "&c传送到起点失败，请稍后再试。");
            return;
        }
        p.setFallDistance(0f);
        p.setVelocity(new Vector(0, 0, 0));

        for (BlockPos pos : path) {
            place(box.world(), pos, pickMaterial(id));
        }
        for (int i = 1; i < path.size(); i++) {
            sparkle(box.world(), path.get(i));
        }

        inventories.put(id, new SavedInventory(p));
        p.getInventory().clear();
        p.getInventory().setItem(0, createTool());
        p.getInventory().setItem(1, createSoundItem(id));

        ParkourSession session = new ParkourSession(id, p.getName(), lane, box, difficulty);
        session.path.addAll(path);
        session.heading = cursor;
        sessions.put(id, session);
        updateGlow(session, path.get(1));

        Chat.send(p, "&b&l云中漫步 &7· " + difficulty.color + difficulty.name
                + " &7· &f跳到前方方块，每块 +1 分；掉下去本局结束。");
        Chat.actionBar(p, actionBarText(session));
    }

    public void leave(Player p) {
        stop(p, "主动退出", true, true);
    }

    public void exitForLobby(Player p) {
        stop(p, "回主城", true, false);
    }

    public void stop(Player p, String reason, boolean record, boolean teleport) {
        ParkourSession session = sessions.get(p.getUniqueId());
        if (session != null) {
            finish(p, session, reason, record, teleport);
        }
    }

    public boolean isPlaying(UUID id) {
        return sessions.containsKey(id);
    }

    public boolean isPathBlock(Block block) {
        for (ParkourSession s : sessions.values()) {
            if (!s.box.world().equals(block.getWorld())) {
                continue;
            }
            for (BlockPos pos : s.path) {
                if (pos.x() == block.getX() && pos.y() == block.getY() && pos.z() == block.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------- 每 2 tick ----------

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        tickCounter++;
        boolean showBar = tickCounter % 10 == 0;

        for (ParkourSession s : new ArrayList<>(sessions.values())) {
            if (s.finished) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.playerId);
            if (p == null || !p.isOnline()) {
                finish(null, s, "下线", true, false);
                continue;
            }
            if (!p.getWorld().equals(s.box.world())) {
                finish(p, s, "离开区域", true, false);
                continue;
            }

            Location loc = p.getLocation();
            if (s.path.size() > 1) {
                BlockPos target = s.path.get(1);
                double dx = loc.getX() - (target.x() + 0.5);
                double dz = loc.getZ() - (target.z() + 0.5);
                double feetY = loc.getY();
                // 落在目标方块顶面附近：水平 0.85 格内、脚部 Y 在顶面 -0.1~+0.6。
                // 不判断 Y 速度：玩家落地后立刻起跳（连跳）时，采样到 +0.4 仍然算数。
                // 也不用 Player#isOnGround（已弃用，且依赖客户端上报）。
                if (dx * dx + dz * dz <= 0.85 * 0.85
                        && feetY >= target.y() + 0.9
                        && feetY < target.y() + 1.6) {
                    score(p, s, target);
                    if (s.finished) {
                        continue;
                    }
                }
            }

            if (loc.getY() < s.current().y() - 3 || !s.box.containsXZ(loc)) {
                finish(p, s, "掉落", true, true);
                continue;
            }
            if (p.isFlying() || p.isGliding()) {
                finish(p, s, "使用了飞行", true, true);
                continue;
            }
            if (showBar) {
                Chat.actionBar(p, actionBarText(s));
            }
        }
    }

    private void score(Player p, ParkourSession s, BlockPos landed) {
        s.score++;

        if (settings.sound && soundEnabled(s.playerId)) {
            float pitch = Math.min(2.0f, 1.0f + s.score * 0.02f);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, pitch);
        }
        if (settings.particle) {
            p.getWorld().spawnParticle(Particle.END_ROD,
                    landed.x() + 0.5, landed.y() + 1.0, landed.z() + 0.5,
                    10, 0.3, 0.15, 0.3, 0.01);
        }

        BlockPos old = s.path.remove(0);
        setAir(s.box.world(), old);

        BlockPos tail = s.path.get(s.path.size() - 1);
        PathGenerator generator = generators.get(s.difficulty.id);
        BlockPos next = generator == null ? null
                : generator.next(tail, s.heading, new HashSet<>(s.path), s.box);
        if (next == null) {
            finish(p, s, "路径到达区域边界", true, true);
            return;
        }
        Direction direction = Direction.between(tail, next);
        if (direction != null) {
            s.heading = direction;
        }
        place(s.box.world(), next, pickMaterial(s.playerId));
        s.path.add(next);
        sparkle(s.box.world(), next);
        updateGlow(s, s.path.get(1));
        Chat.actionBar(p, actionBarText(s));
    }

    private void finish(Player p, ParkourSession s, String reason, boolean record, boolean teleport) {
        if (s.finished) {
            return;
        }
        s.finished = true;
        sessions.remove(s.playerId);
        freeLane(s.lane);

        if (s.glow != null) {
            s.glow.remove();
            s.glow = null;
        }
        for (BlockPos pos : s.path) {
            setAir(s.box.world(), pos);
        }
        s.path.clear();

        SavedInventory saved = inventories.get(s.playerId);
        if (saved != null && p != null && p.isOnline()) {
            inventories.remove(s.playerId);
            saved.apply(p);
        }

        int bestBefore = bestScore(s.playerId, s.difficulty.id);
        boolean newBest = record && s.score > bestBefore;
        if (record) {
            recordScore(s.playerId, s.playerName, s.score, s.difficulty.id);
        }

        if (p != null && p.isOnline()) {
            StringBuilder msg = new StringBuilder("&8[&b云中漫步&8] &7").append(s.difficulty.color)
                    .append(s.difficulty.name).append(" &7· &f本局 &b").append(s.score).append(" &7分");
            if (newBest) {
                msg.append(" &a· 新纪录！");
            } else {
                msg.append(" &7· 最高 &b").append(bestScore(s.playerId, s.difficulty.id));
            }
            msg.append(" &8(").append(reason).append(")");
            Chat.send(p, msg.toString());
            if (teleport) {
                returnToLobby(p);
            }
        }
    }

    private void returnToLobby(Player p) {
        if (HubAPI.isReady()) {
            HubAPI.teleportToLobby(p);
            return;
        }
        Location target = settings.lobby != null ? settings.lobby : HubAPI.getLobby();
        if (target != null) {
            p.teleport(target);
        }
    }

    private String actionBarText(ParkourSession s) {
        return s.difficulty.color + s.difficulty.name + " &8· &b本局 &e" + s.score
                + " &8· &6最高 &e" + bestScore(s.playerId, s.difficulty.id);
    }

    // ---------- 通道 ----------

    private int allocateLane() {
        for (int i = 0; i < lanesUsed.length; i++) {
            if (!lanesUsed[i]) {
                lanesUsed[i] = true;
                return i;
            }
        }
        return -1;
    }

    private void freeLane(int lane) {
        if (lane >= 0 && lane < lanesUsed.length) {
            lanesUsed[lane] = false;
        }
    }

    private int[] laneOffset(int lane) {
        int width = settings.axis.equals("z")
                ? baseBox.maxZ() - baseBox.minZ() + 1
                : baseBox.maxX() - baseBox.minX() + 1;
        int distance = lane * (width + settings.laneGap);
        int height = lane * settings.heightStep;
        return settings.axis.equals("z")
                ? new int[]{0, height, distance}
                : new int[]{distance, height, 0};
    }

    private LaneBox laneBox(int lane) {
        int[] offset = laneOffset(lane);
        return baseBox.translate(offset[0], offset[1], offset[2]);
    }

    private BlockPos translatedStart(int lane) {
        int[] offset = laneOffset(lane);
        return baseStart.offset(offset[0], offset[1], offset[2]);
    }

    // ---------- 方块 ----------

    private void place(World world, BlockPos pos, Material material) {
        world.getBlockAt(pos.x(), pos.y(), pos.z()).setType(material, false);
    }

    private void setAir(World world, BlockPos pos) {
        world.getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR, false);
    }

    // ---------- 方块池 / 物品栏 ----------

    public List<Material> palette() {
        return settings.palette;
    }

    public List<Material> enabledBlocks(UUID id) {
        List<Material> cached = playerBlocks.get(id);
        if (cached != null) {
            return cached;
        }
        List<Material> list = new ArrayList<>();
        for (String name : data.getStringList("blocks." + id)) {
            Material material = Material.matchMaterial(name);
            if (material != null && settings.palette.contains(material) && !list.contains(material)) {
                list.add(material);
            }
        }
        if (list.isEmpty()) {
            list.addAll(settings.palette);
        }
        playerBlocks.put(id, list);
        return list;
    }

    public void toggleBlock(UUID id, Material material) {
        List<Material> list = enabledBlocks(id);
        if (list.contains(material)) {
            if (list.size() > 1) {
                list.remove(material);
            }
        } else {
            list.add(material);
        }
    }

    public void selectAllBlocks(UUID id) {
        List<Material> list = enabledBlocks(id);
        list.clear();
        list.addAll(settings.palette);
    }

    public void saveBlocks(UUID id) {
        List<Material> list = playerBlocks.get(id);
        if (list == null) {
            return;
        }
        data.set("blocks." + id, list.stream().map(Material::name).toList());
        saveData();
    }

    public void openPalette(Player p) {
        p.openInventory(new PaletteMenu(this, p).getInventory());
    }

    private Material pickMaterial(UUID id) {
        List<Material> pool = enabledBlocks(id);
        if (pool.isEmpty()) {
            pool = settings.palette;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    public boolean isPaletteTool(ItemStack item) {
        return item != null && item.getType() == Material.BLAZE_ROD
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public boolean isSoundItem(ItemStack item) {
        return item != null && item.getType() == Material.NOTE_BLOCK
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(soundKey, PersistentDataType.BYTE);
    }

    public boolean soundEnabled(UUID id) {
        return data.getBoolean("sound." + id, true);
    }

    /** @return 切换后的状态：true = 音效开启 */
    public boolean toggleSound(UUID id) {
        boolean enabled = !soundEnabled(id);
        data.set("sound." + id, enabled);
        saveData();
        return enabled;
    }

    public void refreshSoundItem(Player p) {
        PlayerInventory inventory = p.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isSoundItem(inventory.getItem(i))) {
                inventory.setItem(i, createSoundItem(p.getUniqueId()));
                return;
            }
        }
    }

    private ItemStack createSoundItem(UUID id) {
        boolean enabled = soundEnabled(id);
        ItemStack item = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(enabled ? "&a音效：开启" : "&c音效：关闭")));
            meta.lore(List.of(Chat.color("&7右键切换跑酷音效")));
            meta.getPersistentDataContainer().set(soundKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTool() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&e&l选择跑酷方块")));
            meta.lore(List.of(Chat.color("&7右键打开方块选择菜单")));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 玩家上线：恢复上次没来得及归还的物品栏，清掉残留的选择道具，并隐藏别人的私有榜单页。 */
    public void handleJoin(Player p) {
        SavedInventory saved = inventories.remove(p.getUniqueId());
        if (saved != null) {
            saved.apply(p);
        }
        PlayerInventory inventory = p.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isPaletteTool(inventory.getItem(i)) || isSoundItem(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
        for (PageView view : pageViews.values()) {
            Entity entity = Bukkit.getEntity(view.entityId);
            if (entity != null) {
                p.hideEntity(plugin, entity);
            }
        }
    }

    public List<CloudWalkSettings.Difficulty> difficulties() {
        return settings.difficulties;
    }

    public CloudWalkSettings.Difficulty difficultyById(String id) {
        return settings.difficulty(id);
    }

    public CloudWalkSettings.Difficulty defaultDifficulty() {
        return settings.defaultDifficulty();
    }

    // ---------- 下一个方块的发光标记 / 新方块粒子 ----------

    /** 用 BlockDisplay 给下一个目标方块套一层发光轮廓（不跟随玩家视线）。 */
    private void updateGlow(ParkourSession s, BlockPos target) {
        World world = s.box.world();
        BlockData data = world.getBlockAt(target.x(), target.y(), target.z()).getBlockData();
        Location loc = new Location(world, target.x(), target.y(), target.z());
        if (s.glow == null || !s.glow.isValid()) {
            s.glow = world.spawn(loc, BlockDisplay.class, display -> {
                display.setBlock(data);
                display.setGlowing(true);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setShadowRadius(0f);
                display.setShadowStrength(0f);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.005f, -0.005f, -0.005f),
                        new Quaternionf(),
                        new Vector3f(1.01f, 1.01f, 1.01f),
                        new Quaternionf()));
            });
        } else {
            s.glow.teleport(loc);
            s.glow.setBlock(data);
            s.glow.setGlowing(true);
        }
    }

    /** 新方块生成时的亮绿色粒子，约 0.65 秒后消失。 */
    private void sparkle(World world, BlockPos pos) {
        Location center = new Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
        for (int i = 0; i < 3; i++) {
            long delay = 1L + i * 6L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, center, 14, 0.45, 0.45, 0.45, 0.01);
                }
            }, delay);
        }
    }

    // ---------- 排行榜全息 ----------

    private void maintenance() {
        refreshLeaderboards();
        refreshPageViews();
        syncTitles();
        prunePageViews();
    }

    private void loadLeaderboards() {
        leaderboards.clear();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("leaderboards")) {
            Location loc = readLbLocation(raw);
            if (loc == null) {
                continue;
            }
            Object entity = raw.get("entity");
            Object footer = raw.get("footer");
            Object stand = raw.get("interact") != null ? raw.get("interact") : raw.get("stand");
            leaderboards.add(new LeaderBoard(loc,
                    parseUuid(entity == null ? "" : String.valueOf(entity)),
                    parseUuid(footer == null ? "" : String.valueOf(footer)),
                    parseUuid(stand == null ? "" : String.valueOf(stand))));
        }
    }

    private void saveLeaderboards() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LeaderBoard board : leaderboards) {
            if (board.loc.getWorld() == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", board.loc.getWorld().getName());
            map.put("x", board.loc.getX());
            map.put("y", board.loc.getY());
            map.put("z", board.loc.getZ());
            map.put("yaw", (double) board.loc.getYaw());
            map.put("pitch", (double) board.loc.getPitch());
            map.put("entity", board.entityId == null ? "" : board.entityId.toString());
            map.put("footer", board.footerId == null ? "" : board.footerId.toString());
            map.put("interact", board.interactionId == null ? "" : board.interactionId.toString());
            list.add(map);
        }
        plugin.getConfig().set("leaderboards", list);
        plugin.saveConfig();
    }

    public void placeLeaderboard(Player p) {
        Location loc = p.getLocation().clone().add(0, 0.5, 0);
        loc.setYaw(snapYaw(loc.getYaw()));
        loc.setPitch(0f);
        LeaderBoard board = new LeaderBoard(loc, null, null, null);
        leaderboards.add(board);
        board.entityId = spawnDisplay(mainLocation(board, settings.topSize));
        board.footerId = spawnFooter(loc);
        board.interactionId = spawnInteraction(loc);
        saveLeaderboards();
        refreshLeaderboards();
        Chat.send(p, "&a已放置云中漫步排行榜：底部「下一页 >>>」那一行是唯一的点击区（右键切换难度）。");
    }

    public void removeLeaderboards(CommandSender sender) {
        clearPageViews();
        for (LeaderBoard board : leaderboards) {
            Entity entity = board.entityId == null ? null : Bukkit.getEntity(board.entityId);
            if (entity != null) {
                entity.remove();
            }
            Entity stand = board.interactionId == null ? null : Bukkit.getEntity(board.interactionId);
            if (stand != null) {
                stand.remove();
            }
            Entity footer = board.footerId == null ? null : Bukkit.getEntity(board.footerId);
            if (footer != null) {
                footer.remove();
            }
        }
        leaderboards.clear();
        saveLeaderboards();
        Chat.send(sender, "&a已移除全部云中漫步排行榜。");
    }

    public void refreshLeaderboards() {
        boolean changed = false;
        for (LeaderBoard board : leaderboards) {
            if (board.loc.getWorld() == null || !board.loc.isChunkLoaded()) {
                continue;
            }
            CloudWalkSettings.Difficulty defaultDifficulty = settings.defaultDifficulty();
            Location mainLoc = mainLocation(board, settings.topSize);
            Entity entity = board.entityId == null ? null : Bukkit.getEntity(board.entityId);
            if (!(entity instanceof TextDisplay display)) {
                board.entityId = spawnDisplay(mainLoc);
                changed = true;
                Entity fresh = board.entityId == null ? null : Bukkit.getEntity(board.entityId);
                if (fresh != null) {
                    // 新生成的基础榜也要对正在看私有页的玩家隐藏
                    for (Map.Entry<UUID, PageView> entry : pageViews.entrySet()) {
                        Player viewer = Bukkit.getPlayer(entry.getKey());
                        if (viewer != null) {
                            viewer.hideEntity(plugin, fresh);
                        }
                    }
                }
            } else {
                // 已有实体也重设位置与朝向：旧版本放的榜会在下一次刷新时被摆正
                display.teleport(mainLoc);
                display.setBillboard(Display.Billboard.FIXED);
                display.setRotation(snapYaw(board.loc.getYaw() + 180f), 0f);
                applyLeaderboardStyle(display, settings.defaultDifficulty());
            }

            Entity footer = board.footerId == null ? null : Bukkit.getEntity(board.footerId);
            if (!(footer instanceof TextDisplay)) {
                if (footer != null) {
                    footer.remove();
                }
                board.footerId = spawnFooter(board.loc);
                changed = true;
            }

            Entity stand = board.interactionId == null ? null : Bukkit.getEntity(board.interactionId);
            if (!(stand instanceof Interaction)) {
                if (stand != null) {
                    // 旧版本放的是隐形盔甲架，换成 Interaction 点击区
                    stand.remove();
                }
                board.interactionId = spawnInteraction(board.loc);
                changed = true;
            }
        }
        if (changed) {
            saveLeaderboards();
        }
    }

    private static Location mainLocation(LeaderBoard board, int entries) {
        // TextDisplay 的文字块以实体位置为垂直中心：把整块往上挪，让最后一行刚好落在「下一页」上方
        Location main = board.loc.clone();
        main.add(0, (1 + entries) / 2.0 * LINE_HEIGHT + FOOTER_GAP, 0);
        return main;
    }

    private UUID spawnDisplay(Location loc) {
        TextDisplay display = createDisplay(loc, true);
        return display == null ? null : display.getUniqueId();
    }

    private TextDisplay createDisplay(Location loc, boolean persistent) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        if (!loc.isChunkLoaded()) {
            loc.getChunk().load();
        }
        Location spawnLoc = loc.clone();
        spawnLoc.setPitch(0f);
        TextDisplay display = world.spawn(spawnLoc, TextDisplay.class);
        display.setBillboard(Display.Billboard.FIXED);
        // 只朝四个正方向之一，俯仰角固定 0
        display.setRotation(snapYaw(loc.getYaw() + 180f), 0f);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(200);
        display.setViewRange(64);
        display.setPersistent(persistent);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
        applyLeaderboardStyle(display, settings.defaultDifficulty());
        return display;
    }

    /** 榜单底部的点击区：只覆盖「下一页 >>>」那一行。 */
    private UUID spawnInteraction(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        Interaction interaction = world.spawn(loc.clone(), Interaction.class);
        interaction.setInteractionWidth(3.2f);
        interaction.setInteractionHeight(1.1f);
        interaction.setResponsive(true);
        interaction.setPersistent(false);
        interaction.setInvulnerable(true);
        interaction.setRotation(snapYaw(loc.getYaw() + 180f), 0f);
        return interaction.getUniqueId();
    }

    /** 底部静态的「下一页 >>>」文字（主榜单独放在它上方）。 */
    private UUID spawnFooter(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        if (!loc.isChunkLoaded()) {
            loc.getChunk().load();
        }
        Location spawnLoc = loc.clone();
        spawnLoc.setPitch(0f);
        TextDisplay display = world.spawn(spawnLoc, TextDisplay.class);
        display.setBillboard(Display.Billboard.FIXED);
        display.setRotation(snapYaw(loc.getYaw() + 180f), 0f);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(200);
        display.setViewRange(64);
        display.setPersistent(true);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
        display.text(Chat.color(FOOTER_TEXT));
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(130, 15, 15, 30));
        return display.getUniqueId();
    }

    private void applyLeaderboardStyle(TextDisplay display, CloudWalkSettings.Difficulty difficulty) {
        display.text(difficultyText(difficulty));
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(130, 15, 15, 30));
    }

    private Component difficultyText(CloudWalkSettings.Difficulty difficulty) {
        StringBuilder sb = new StringBuilder(difficulty.color)
                .append("&l—— 云中漫步 · ").append(difficulty.name).append("榜 ——\n");
        List<TopEntry> top = topEntries(difficulty.id);
        for (int i = 0; i < settings.topSize; i++) {
            if (i < top.size()) {
                TopEntry entry = top.get(i);
                int rank = i + 1;
                String color = switch (i) {
                    case 0 -> "&e";
                    case 1, 2 -> "&f";
                    default -> "&7";
                };
                sb.append(color).append(rank).append("&7. &f").append(entry.name())
                        .append(" &7- ").append(difficulty.color).append(entry.score()).append("&7 分");
            } else {
                sb.append("&8").append(i + 1).append("&7. &8空位");
            }
            if (i < settings.topSize - 1) {
                sb.append('\n');
            }
        }
        return Chat.color(sb.toString());
    }

    // ---------- 榜单翻页（每名玩家私有一份，20 秒后恢复默认页） ----------

    public boolean isBoardInteraction(UUID id) {
        if (id == null) {
            return false;
        }
        for (LeaderBoard board : leaderboards) {
            if (id.equals(board.interactionId)) {
                return true;
            }
        }
        return false;
    }

    public void handleBoardClick(Player p, UUID interactionId) {
        for (LeaderBoard board : leaderboards) {
            if (interactionId.equals(board.interactionId)) {
                nextPage(p, board);
                return;
            }
        }
    }

    private void nextPage(Player p, LeaderBoard board) {
        List<CloudWalkSettings.Difficulty> enabled = new ArrayList<>();
        for (CloudWalkSettings.Difficulty difficulty : settings.difficulties) {
            if (difficulty.enabled) {
                enabled.add(difficulty);
            }
        }
        if (enabled.isEmpty()) {
            return;
        }

        PageView view = pageViews.get(p.getUniqueId());
        String current = view == null ? settings.defaultDifficulty().id : view.difficultyId;
        int index = 0;
        for (int i = 0; i < enabled.size(); i++) {
            if (enabled.get(i).id.equals(current)) {
                index = i;
                break;
            }
        }
        CloudWalkSettings.Difficulty next = enabled.get((index + 1) % enabled.size());
        Location pageLoc = mainLocation(board, settings.topSize);

        TextDisplay display = null;
        if (view != null) {
            Entity entity = Bukkit.getEntity(view.entityId);
            if (entity instanceof TextDisplay existing && existing.isValid()) {
                display = existing;
                display.teleport(pageLoc);
            } else {
                removePageView(p.getUniqueId());
            }
        }
        if (display == null) {
            display = createDisplay(pageLoc, false);
            if (display == null) {
                return;
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p)) {
                    other.hideEntity(plugin, display);
                }
            }
            Entity base = board.entityId == null ? null : Bukkit.getEntity(board.entityId);
            if (base != null) {
                p.hideEntity(plugin, base);
            }
        }
        display.text(difficultyText(next));
        pageViews.put(p.getUniqueId(), new PageView(display.getUniqueId(), next.id, board,
                System.currentTimeMillis() + PAGE_VIEW_MILLIS));
    }

    public void removePageView(UUID playerId) {
        PageView view = pageViews.remove(playerId);
        if (view == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(view.entityId);
        if (entity != null) {
            entity.remove();
        }
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline() && view.board.entityId != null) {
            Entity base = Bukkit.getEntity(view.board.entityId);
            if (base != null) {
                p.showEntity(plugin, base);
            }
        }
    }

    private void prunePageViews() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(pageViews.keySet())) {
            PageView view = pageViews.get(id);
            if (view != null && view.expiresAt <= now) {
                removePageView(id);
            }
        }
    }

    private void refreshPageViews() {
        for (UUID playerId : new ArrayList<>(pageViews.keySet())) {
            PageView view = pageViews.get(playerId);
            if (view == null) {
                continue;
            }
            CloudWalkSettings.Difficulty difficulty = settings.difficulty(view.difficultyId);
            if (difficulty == null) {
                removePageView(playerId);
                continue;
            }
            if (!(Bukkit.getEntity(view.entityId) instanceof TextDisplay display) || !display.isValid()) {
                // 私有页实体丢了：撤掉视图，让主榜重新可见，避免玩家盯着一块空板
                removePageView(playerId);
                continue;
            }
            display.teleport(mainLocation(view.board, settings.topSize));
            display.text(difficultyText(difficulty));
        }
    }

    private void clearPageViews() {
        for (UUID id : new ArrayList<>(pageViews.keySet())) {
            removePageView(id);
        }
        pageViews.clear();
    }

    private static Location readLbLocation(Map<?, ?> raw) {
        Object worldName = raw.get("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(String.valueOf(worldName));
        if (world == null) {
            return null;
        }
        return new Location(world,
                toDouble(raw.get("x")), toDouble(raw.get("y")), toDouble(raw.get("z")),
                (float) toDouble(raw.get("yaw")), (float) toDouble(raw.get("pitch")));
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /** 把任意 yaw 吸附到最近的 0/90/180/270，避免排行榜出现斜角。 */
    private static float snapYaw(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        int quarter = Math.round(normalized / 90f) % 4;
        return quarter * 90f;
    }

    // ---------- 头衔 ----------

    /** 每个难度前十持有对应头衔；高难度前十同时持有所有更低难度的头衔（但不出现在低难度榜上）。 */
    private void syncTitles() {
        boolean changed = false;
        Set<String> inherited = new LinkedHashSet<>();
        List<CloudWalkSettings.Difficulty> difficulties = settings.difficulties;

        for (int i = difficulties.size() - 1; i >= 0; i--) {
            CloudWalkSettings.Difficulty difficulty = difficulties.get(i);
            String key = "title-holders." + difficulty.id;
            List<String> previous = new ArrayList<>(data.getStringList(key));

            if (!difficulty.enabled) {
                for (String id : previous) {
                    UUID uuid = parseUuid(id);
                    if (uuid != null) {
                        GameTitles.revoke(uuid, difficulty.titleId);
                    }
                }
                if (!previous.isEmpty()) {
                    data.set(key, null);
                    changed = true;
                }
                // 未开放的难度不贡献自己的前十，但更高难度的持有者继续往下传
                continue;
            }

            Set<String> current = new LinkedHashSet<>(inherited);
            for (TopEntry entry : topEntries(difficulty.id)) {
                current.add(entry.id());
            }
            for (String id : current) {
                UUID uuid = parseUuid(id);
                // 用 owns 比对而不是只和上次的列表比：头衔插件晚于本插件加载时也能补发
                if (uuid != null && !GameTitles.owns(uuid, difficulty.titleId)) {
                    GameTitles.grantOrCreate(uuid, difficulty.titleId, difficulty.titleName,
                            difficulty.titleColor, difficulty.titleDesc);
                }
            }
            for (String id : previous) {
                if (current.contains(id)) {
                    continue;
                }
                UUID uuid = parseUuid(id);
                if (uuid != null) {
                    GameTitles.revoke(uuid, difficulty.titleId);
                }
            }

            List<String> next = new ArrayList<>(current);
            if (!previous.equals(next)) {
                data.set(key, next);
                changed = true;
            }
            inherited = current;
        }
        if (changed) {
            saveData();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---------- 记录 ----------

    public int bestScore(UUID id, String difficultyId) {
        return data.getInt("best." + difficultyId + "." + id, 0);
    }

    public int recordCount() {
        int count = 0;
        for (CloudWalkSettings.Difficulty difficulty : settings.difficulties) {
            ConfigurationSection best = data.getConfigurationSection("best." + difficulty.id);
            if (best != null) {
                count += best.getKeys(false).size();
            }
        }
        return count;
    }

    public record TopEntry(String id, String name, int score, long at) {
    }

    private List<TopEntry> allEntries(String difficultyId) {
        List<TopEntry> list = new ArrayList<>();
        ConfigurationSection best = data.getConfigurationSection("best." + difficultyId);
        if (best != null) {
            for (String id : best.getKeys(false)) {
                int score = best.getInt(id);
                if (score <= 0) {
                    continue;
                }
                long at = data.getLong("bestAt." + difficultyId + "." + id, 0L);
                String name = data.getString("names." + id, "未知玩家");
                list.add(new TopEntry(id, name, score, at));
            }
        }
        list.sort(Comparator.comparingInt(TopEntry::score).reversed()
                .thenComparingLong(TopEntry::at));
        return list;
    }

    public List<TopEntry> topEntries(String difficultyId) {
        Set<String> excluded = harderTopIds(difficultyId);
        List<TopEntry> list = new ArrayList<>();
        for (TopEntry entry : allEntries(difficultyId)) {
            if (!excluded.contains(entry.id())) {
                list.add(entry);
            }
        }
        if (list.size() > settings.topSize) {
            return new ArrayList<>(list.subList(0, settings.topSize));
        }
        return list;
    }

    /** 更难的榜前十不计入较低难度榜：困难榜前十不出现在中/简单，中等榜前十不出现在简单。 */
    private Set<String> harderTopIds(String difficultyId) {
        Set<String> excluded = new HashSet<>();
        CloudWalkSettings.Difficulty current = settings.difficulty(difficultyId);
        if (current == null) {
            return excluded;
        }
        for (CloudWalkSettings.Difficulty other : settings.difficulties) {
            if (other.enabled && other.order > current.order) {
                for (TopEntry entry : topEntries(other.id)) {
                    excluded.add(entry.id());
                }
            }
        }
        return excluded;
    }

    public int rankOf(UUID id, String difficultyId) {
        if (bestScore(id, difficultyId) <= 0) {
            return 0;
        }
        Set<String> excluded = harderTopIds(difficultyId);
        int rank = 1;
        for (TopEntry entry : allEntries(difficultyId)) {
            if (excluded.contains(entry.id())) {
                continue;
            }
            if (entry.id().equals(id.toString())) {
                return rank;
            }
            rank++;
        }
        return 0;
    }

    private void recordScore(UUID id, String name, int score, String difficultyId) {
        if (score <= 0) {
            return;
        }
        String path = "best." + difficultyId + "." + id;
        int best = data.getInt(path, 0);
        if (score <= best) {
            return;
        }
        data.set(path, score);
        data.set("bestAt." + difficultyId + "." + id, System.currentTimeMillis());
        data.set("names." + id, name);
        saveData();
        syncTitles();
        refreshLeaderboards();
    }

    public void resetRecord(UUID id) {
        for (CloudWalkSettings.Difficulty difficulty : settings.difficulties) {
            data.set("best." + difficulty.id + "." + id, null);
            data.set("bestAt." + difficulty.id + "." + id, null);
        }
        data.set("names." + id, null);
        saveData();
        syncTitles();
        refreshLeaderboards();
    }

    /** 旧版单一难度的记录一次性搬到中等难度。 */
    private void migrateLegacyRecords() {
        boolean changed = false;
        ConfigurationSection best = data.getConfigurationSection("best");
        if (best != null) {
            for (String key : new ArrayList<>(best.getKeys(false))) {
                if (settings.difficulty(key) != null) {
                    continue;
                }
                int score = best.getInt(key);
                long at = data.getLong("bestAt." + key, 0L);
                data.set("best.medium." + key, score);
                if (at > 0) {
                    data.set("bestAt.medium." + key, at);
                }
                data.set("best." + key, null);
                data.set("bestAt." + key, null);
                changed = true;
            }
        }
        if (data.isList("title-holders")) {
            data.set("title-holders.medium", data.getStringList("title-holders"));
            data.set("title-holders", null);
            changed = true;
        }
        if (changed) {
            saveData();
        }
    }

    private void saveData() {
        try {
            PlayerStats.save(NAMESPACE, data);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存跑酷记录失败: " + ex.getMessage());
        }
    }

    private static final class LeaderBoard {
        final Location loc;
        UUID entityId;
        UUID footerId;
        UUID interactionId;

        LeaderBoard(Location loc, UUID entityId, UUID footerId, UUID interactionId) {
            this.loc = loc;
            this.entityId = entityId;
            this.footerId = footerId;
            this.interactionId = interactionId;
        }
    }

    private static final class PageView {
        final UUID entityId;
        final String difficultyId;
        final LeaderBoard board;
        final long expiresAt;

        PageView(UUID entityId, String difficultyId, LeaderBoard board, long expiresAt) {
            this.entityId = entityId;
            this.difficultyId = difficultyId;
            this.board = board;
            this.expiresAt = expiresAt;
        }
    }

    /** 进跑酷前的物品栏快照，结束时原样还原（与 SkyPit 相同的做法）。 */
    private static final class SavedInventory {

        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        SavedInventory(Player player) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] all = inventory.getContents();
            if (all.length == 41) {
                contents = Arrays.copyOfRange(all, 0, 36);
                armor = Arrays.copyOfRange(all, 36, 40);
                offhand = all[40];
            } else {
                contents = all.clone();
                armor = inventory.getArmorContents().clone();
                offhand = inventory.getItemInOffHand();
            }
        }

        void apply(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) {
                    inventory.setItem(i, contents[i].clone());
                }
            }
            inventory.setArmorContents(armor);
            inventory.setItemInOffHand(offhand == null ? null : offhand.clone());
        }
    }
}
