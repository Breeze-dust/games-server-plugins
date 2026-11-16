package com.archery;

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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 箭术大师主逻辑：区域编辑、开局、靶子、箭矢命中、结算、排行榜。 */
public class ArcheryManager {

    private static final String NAMESPACE = "archery";
    private static final int TICK_PERIOD = 1;
    private static final double LINE_HEIGHT = 0.25 * 1.3;

    private final ArcheryPlugin plugin;
    private final Map<UUID, ArcherySession> sessions = new LinkedHashMap<>();
    private final Map<UUID, EditSession> edits = new LinkedHashMap<>();
    /** 正在聊天栏等待输入基础分的玩家；聊天事件是异步的，所以用并发集合。 */
    private final Set<UUID> awaitingScore = ConcurrentHashMap.newKeySet();
    private final List<LeaderBoard> leaderboards = new ArrayList<>();
    private final Random random = new Random();
    private final NamespacedKey bowKey;
    private final NamespacedKey arrowKey;
    private final NamespacedKey editStickKey;
    private final NamespacedKey editConfirmKey;
    private final NamespacedKey editSaveKey;
    private final NamespacedKey editExitKey;

    private ArcherySettings settings;
    private Location start;
    private YamlConfiguration data;

    private BukkitTask tickTask;
    private BukkitTask maintenanceTask;
    private long lastTickErrorLog;

    public ArcheryManager(ArcheryPlugin plugin) {
        this.plugin = plugin;
        this.bowKey = new NamespacedKey(plugin, "archery_bow");
        this.arrowKey = new NamespacedKey(plugin, "archery_arrow");
        this.editStickKey = new NamespacedKey(plugin, "edit_stick");
        this.editConfirmKey = new NamespacedKey(plugin, "edit_confirm");
        this.editSaveKey = new NamespacedKey(plugin, "edit_save");
        this.editExitKey = new NamespacedKey(plugin, "edit_exit");
    }

    // ---------- 加载 / 保存 ----------

    public void load() {
        settings = ArcherySettings.load(plugin.getConfig());
        start = settings.start();

        if (data == null) {
            data = PlayerStats.load(NAMESPACE, new File(plugin.getDataFolder(), "data.yml"));
        }
        loadLeaderboards();

        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK_PERIOD);
        }
        if (maintenanceTask == null) {
            maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboards, 100L, 100L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshLeaderboards, 40L);

        plugin.getLogger().info("箭术大师已加载：靶场区域 " + settings.regions.size()
                + " 个，站位" + (start == null ? "未配置" : "已配置")
                + "，时长 " + settings.gameSeconds + " 秒，记录 " + recordCount() + " 人。");
    }

    public void onDisable() {
        for (UUID id : new ArrayList<>(edits.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                exitEdit(p, true, false);
            } else {
                edits.remove(id);
                awaitingScore.remove(id);
            }
        }
        for (ArcherySession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            finish(p, s, "服务器关闭", false, true, false);
        }
    }

    public void reload() {
        for (UUID id : new ArrayList<>(edits.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                exitEdit(p, true, true);
            } else {
                edits.remove(id);
                awaitingScore.remove(id);
            }
        }
        for (ArcherySession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            finish(p, s, "重载", true, true, true);
        }
        plugin.reloadConfig();
        load();
    }

    private String configProblem(Player p) {
        World playWorld = start != null ? start.getWorld() : p.getWorld();
        if (playWorld == null) {
            return "&c世界不可用，请联系管理员。";
        }
        if (regionsFor(playWorld).isEmpty()) {
            return "&c这个世界还没有配置靶场区域，请联系管理员（/archery edit）。";
        }
        return null;
    }

    /** 只取本局世界里的区域。 */
    private List<TargetRegion> regionsFor(World world) {
        List<TargetRegion> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        for (TargetRegion region : settings.regions) {
            if (region.world().equals(world)) {
                out.add(region);
            }
        }
        return out;
    }

    private void saveRegions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TargetRegion region : settings.regions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", region.world().getName());
            map.put("min-x", region.minX());
            map.put("min-y", region.minY());
            map.put("min-z", region.minZ());
            map.put("max-x", region.maxX());
            map.put("max-y", region.maxY());
            map.put("max-z", region.maxZ());
            map.put("base-score", region.baseScore());
            list.add(map);
        }
        plugin.getConfig().set("regions", list);
        plugin.saveConfig();
    }

    // ---------- 开局 / 结束 ----------

    public void join(Player p) {
        UUID id = p.getUniqueId();
        if (edits.containsKey(id)) {
            Chat.send(p, "&e你正在编辑靶场，先右键屏障退出编辑模式。");
            return;
        }
        if (sessions.containsKey(id)) {
            Chat.send(p, "&e你已经在射箭中，用 &f/archery leave &e退出。");
            return;
        }
        String problem = configProblem(p);
        if (problem != null) {
            Chat.send(p, problem);
            return;
        }
        if (HubAPI.gameOf(id) != null) {
            Chat.send(p, "&c你正在其它游戏中，先退出再来。");
            return;
        }
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            Chat.send(p, "&c旁观模式不能射箭。");
            return;
        }

        World playWorld = start != null ? start.getWorld() : p.getWorld();
        Location returnLocation = p.getLocation().clone();
        SavedInventory inventory = new SavedInventory(p);
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.removePotionEffect(PotionEffectType.LEVITATION);
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        p.setFlying(false);
        p.setFallDistance(0f);
        p.setVelocity(new Vector(0, 0, 0));

        if (start != null && !p.teleport(start)) {
            Chat.send(p, "&c传送到射击位失败，请稍后再试。");
            return;
        }
        p.getInventory().clear();
        p.getInventory().setItem(0, createBow());
        p.getInventory().setItem(1, createArrow());

        ArcherySession session = new ArcherySession(id, p.getName(), playWorld,
                returnLocation, inventory, settings.gameSeconds * 20);
        sessions.put(id, session);
        for (int i = 0; i < settings.maxTargets; i++) {
            spawnTarget(session);
        }

        Chat.title(p, "&6&l箭术大师", "&f" + settings.gameSeconds + " 秒内射中更多靶子！", 2);
        Chat.send(p, "&6&l箭术大师 &7· &f命中得分 = 区域基础分 + 移动 +" + settings.bonusMoving
                + " + 快速 +" + settings.bonusFast + " + 缩小 +" + settings.bonusSmall
                + " &7（三项独立叠加）");
        Chat.actionBar(p, bar(session));
    }

    public void leave(Player p) {
        stop(p, "主动退出", true, true, true);
    }

    public void exitForLobby(Player p) {
        stop(p, "回主城", true, true, false);
    }

    public void stop(Player p, String reason, boolean record, boolean restoreInventory, boolean teleportBack) {
        if (p == null) {
            return;
        }
        ArcherySession session = sessions.get(p.getUniqueId());
        if (session != null) {
            finish(p, session, reason, record, restoreInventory, teleportBack);
        }
    }

    public boolean isPlaying(UUID id) {
        return sessions.containsKey(id);
    }

    private void finish(Player p, ArcherySession s, String reason,
                        boolean record, boolean restoreInventory, boolean teleportBack) {
        if (s.finished) {
            return;
        }
        s.finished = true;
        sessions.remove(s.playerId);
        clearTargets(s);
        clearArrows(s);

        int bestBefore = bestScore(s.playerId);
        boolean newBest = record && s.score > bestBefore;
        if (record) {
            recordScore(s.playerId, s.playerName, s.score);
        }

        if (p == null || !p.isOnline()) {
            return;
        }
        if (restoreInventory) {
            s.inventory.apply(p);
        }
        if (teleportBack) {
            p.teleport(s.returnLocation);
            p.setFallDistance(0f);
            p.setVelocity(new Vector(0, 0, 0));
        }
        Chat.actionBar(p, "");

        StringBuilder msg = new StringBuilder("&8[&6箭术大师&8] &7本局 &6").append(s.score).append(" &7分");
        if (newBest) {
            msg.append(" &a· 新纪录！");
        } else {
            msg.append(" &7· 最高 &6").append(bestScore(s.playerId));
        }
        msg.append(" &8(").append(reason).append(")");
        Chat.send(p, msg.toString());
        if (teleportBack) {
            Chat.title(p, "&6&l本局结束", "&f得分 &6" + s.score, 2);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }
    }

    private void clearTargets(ArcherySession s) {
        for (TargetInstance t : new ArrayList<>(s.targets)) {
            Entity entity = Bukkit.getEntity(t.displayId);
            if (entity != null) {
                entity.remove();
            }
        }
        s.targets.clear();
    }

    private void clearArrows(ArcherySession s) {
        for (UUID arrowId : new ArrayList<>(s.arrows.keySet())) {
            Entity entity = Bukkit.getEntity(arrowId);
            if (entity != null) {
                entity.remove();
            }
        }
        s.arrows.clear();
    }

    // ---------- tick ----------

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            tickSessions();
        } catch (Throwable ex) {
            long now = System.currentTimeMillis();
            if (now - lastTickErrorLog > 60_000L) {
                lastTickErrorLog = now;
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "箭术大师 tick 异常（同分钟内不再重复打印）", ex);
            }
        }
    }

    private void tickSessions() {
        for (ArcherySession s : new ArrayList<>(sessions.values())) {
            if (s.finished) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.playerId);
            if (p == null || !p.isOnline()) {
                finish(null, s, "下线", true, false, false);
                continue;
            }
            if (!p.getWorld().equals(s.world)) {
                finish(p, s, "离开区域", true, true, true);
                continue;
            }
            if (p.isFlying() || p.isGliding()) {
                finish(p, s, "使用了飞行", true, true, true);
                continue;
            }

            s.ticksLeft--;
            if (s.ticksLeft <= 0) {
                finish(p, s, "时间到", true, true, true);
                continue;
            }
            s.tickCounter++;
            if (s.tickCounter % 20 == 0) {
                ensureItems(p);
            }
            tickTargets(s);
            detectHits(p, s);
            if (s.tickCounter % 10 == 0) {
                Chat.actionBar(p, bar(s));
            }
        }
    }

    private String bar(ArcherySession s) {
        int seconds = Math.max(0, (s.ticksLeft + 19) / 20);
        return "&6箭术大师 &8· &f剩余 &e" + seconds + "s &8· &f得分 &6" + s.score
                + " &8· &f最高 &e" + bestScore(s.playerId);
    }

    // ---------- 靶子 ----------

    private void spawnTarget(ArcherySession s) {
        if (s.finished || s.targets.size() >= settings.maxTargets) {
            return;
        }
        List<TargetRegion> available = regionsFor(s.world);
        if (available.isEmpty()) {
            return;
        }
        TargetRegion region = available.get(random.nextInt(available.size()));

        boolean small = random.nextDouble() < settings.smallChance;
        double size = small ? settings.smallScale : 1.0;
        boolean moving = random.nextDouble() < settings.movingChance && settings.maxSpeed > 0.0;
        double speed = moving
                ? settings.minSpeed + random.nextDouble() * (settings.maxSpeed - settings.minSpeed)
                : 0.0;
        boolean fast = moving && speed >= settings.fastSpeed;

        Location point = region.randomPoint(random, size);
        for (int attempt = 0; attempt < 24; attempt++) {
            boolean ok = true;
            for (TargetInstance other : s.targets) {
                double dx = point.getX() - other.x;
                double dy = point.getY() - other.y;
                double dz = point.getZ() - other.z;
                if (dx * dx + dy * dy + dz * dz < 1.5 * 1.5) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                break;
            }
            point = region.randomPoint(random, size);
        }

        double vx = 0.0;
        double vz = 0.0;
        if (moving) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            vx = Math.cos(angle) * speed / 20.0;
            vz = Math.sin(angle) * speed / 20.0;
        }

        Material material = fast ? settings.fastBlock
                : (moving ? settings.movingBlock : settings.fixedBlock);
        if (!point.getChunk().isLoaded()) {
            point.getChunk().load();
        }
        Location spawnLocation = point.clone();
        float offset = (float) (-size / 2.0);
        BlockDisplay display = s.world.spawn(spawnLocation, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setShadowRadius(0f);
            entity.setShadowStrength(0f);
            entity.setTransformation(new Transformation(
                    new Vector3f(offset, offset, offset),
                    new Quaternionf(),
                    new Vector3f((float) size, (float) size, (float) size),
                    new Quaternionf()));
            if (small) {
                entity.setGlowing(true);
                entity.setGlowColorOverride(settings.smallGlowColor);
            }
        });
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(s.playerId)) {
                other.hideEntity(plugin, display);
            }
        }
        s.targets.add(new TargetInstance(display.getUniqueId(), region,
                point.getX(), point.getY(), point.getZ(), vx, vz, small, speed, fast,
                System.currentTimeMillis(), size, region.baseScore()));
    }

    private void tickTargets(ArcherySession s) {
        if (s.targets.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long lifetimeMs = settings.targetLifetimeSeconds * 1000L;
        for (TargetInstance t : new ArrayList<>(s.targets)) {
            Entity entity = Bukkit.getEntity(t.displayId);
            if (!(entity instanceof BlockDisplay display) || !display.isValid()) {
                s.targets.remove(t);
                scheduleRespawn(s);
                continue;
            }
            if (lifetimeMs > 0 && now - t.spawnedAt >= lifetimeMs) {
                display.remove();
                s.targets.remove(t);
                scheduleRespawn(s);
                continue;
            }
            if (!t.moving()) {
                continue;
            }

            double half = t.size / 2.0;
            double minX = Math.min(t.region.minX() + half, t.region.maxX() - half);
            double maxX = Math.max(t.region.minX() + half, t.region.maxX() - half);
            double minZ = Math.min(t.region.minZ() + half, t.region.maxZ() - half);
            double maxZ = Math.max(t.region.minZ() + half, t.region.maxZ() - half);

            t.x += t.vx;
            t.z += t.vz;
            if (t.x <= minX) {
                t.x = minX;
                t.vx = Math.abs(t.vx);
            } else if (t.x >= maxX) {
                t.x = maxX;
                t.vx = -Math.abs(t.vx);
            }
            if (t.z <= minZ) {
                t.z = minZ;
                t.vz = Math.abs(t.vz);
            } else if (t.z >= maxZ) {
                t.z = maxZ;
                t.vz = -Math.abs(t.vz);
            }
            display.teleport(new Location(s.world, t.x, t.y, t.z));
        }
    }

    private void scheduleRespawn(ArcherySession s) {
        UUID id = s.playerId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ArcherySession current = sessions.get(id);
            if (current != null && !current.finished) {
                spawnTarget(current);
            }
        }, Math.max(1L, (long) settings.respawnDelayTicks));
    }

    // ---------- 箭矢 ----------

    public void onArrowLaunched(Player p, Arrow arrow) {
        ArcherySession s = sessions.get(p.getUniqueId());
        if (s == null || s.finished) {
            return;
        }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setPersistent(false);
        s.arrows.put(arrow.getUniqueId(), new ArcherySession.ArrowTrack(arrow.getLocation().clone()));
    }

    public boolean ownsArrow(Arrow arrow) {
        for (ArcherySession s : sessions.values()) {
            if (s.arrows.containsKey(arrow.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** 箭命中方块/实体后立刻移除；靶子命中由每 tick 的扫掠检测负责。 */
    public void onArrowHit(Arrow arrow) {
        UUID arrowId = arrow.getUniqueId();
        for (ArcherySession s : sessions.values()) {
            if (s.arrows.remove(arrowId) != null) {
                arrow.remove();
                return;
            }
        }
    }

    private void detectHits(Player p, ArcherySession s) {
        if (s.arrows.isEmpty() || s.targets.isEmpty()) {
            return;
        }
        var iterator = s.arrows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArcherySession.ArrowTrack> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Arrow arrow) || !arrow.isValid() || arrow.isDead()) {
                iterator.remove();
                continue;
            }
            Location now = arrow.getLocation();
            Location previous = entry.getValue().previous;
            entry.getValue().previous = now.clone();
            if (now.getWorld() == null || !now.getWorld().equals(s.world)) {
                arrow.remove();
                iterator.remove();
                continue;
            }

            Vector from = previous.toVector();
            Vector delta = now.toVector().subtract(from);
            double length = delta.length();
            if (length < 1.0e-5) {
                continue;
            }
            Vector direction = delta.clone().normalize();

            TargetInstance hit = null;
            Vector hitPoint = null;
            double bestDistance = Double.MAX_VALUE;
            for (TargetInstance target : s.targets) {
                BoundingBox box = target.box();
                Vector point = null;
                if (box.contains(from)) {
                    point = from.clone();
                } else {
                    RayTraceResult result = box.rayTrace(from, direction, length + 0.05);
                    if (result != null) {
                        point = result.getHitPosition();
                    }
                }
                if (point != null) {
                    double distance = point.distanceSquared(from);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        hit = target;
                        hitPoint = point;
                    }
                }
            }
            if (hit != null) {
                arrow.remove();
                iterator.remove();
                awardHit(p, s, hit, hitPoint);
            }
        }
    }

    private void awardHit(Player p, ArcherySession s, TargetInstance target, Vector point) {
        // 原版命中事件和每 tick 扫掠可能同时到达，先移除成功的那一次才算分。
        if (!s.targets.remove(target)) {
            return;
        }
        int gained = target.score(settings);
        s.score += gained;

        Entity entity = Bukkit.getEntity(target.displayId);
        if (entity != null) {
            entity.remove();
        }
        scheduleRespawn(s);

        if (settings.sound) {
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
        }
        if (settings.particle) {
            Location at = point != null
                    ? point.toLocation(s.world)
                    : new Location(s.world, target.x, target.y, target.z);
            s.world.spawnParticle(Particle.CRIT, at, 18, 0.25, 0.25, 0.25, 0.05);
            s.world.spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.2, 0.2, 0.2, 0.01);
        }
        Chat.actionBar(p, "&a+" + gained + " &8(" + target.scoreBreakdown(settings) + ") &8· " + bar(s));
    }

    // ---------- 弓与箭 ----------

    private ItemStack createBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&6&l箭术大师之弓")));
            meta.setUnbreakable(true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
            meta.getPersistentDataContainer().set(bowKey, PersistentDataType.BYTE, (byte) 1);
            bow.setItemMeta(meta);
        }
        return bow;
    }

    private ItemStack createArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&7箭术大师之箭")));
            meta.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    public boolean isBow(ItemStack item) {
        return item != null && item.getType() == Material.BOW && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(bowKey, PersistentDataType.BYTE);
    }

    public boolean isArrow(ItemStack item) {
        return item != null && item.getType() == Material.ARROW && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE);
    }

    private void ensureItems(Player p) {
        boolean hasBow = false;
        boolean hasArrow = false;
        for (ItemStack item : p.getInventory().getContents()) {
            if (isBow(item)) {
                hasBow = true;
            } else if (isArrow(item)) {
                hasArrow = true;
            }
        }
        if (!hasBow) {
            p.getInventory().setItem(0, createBow());
        }
        if (!hasArrow) {
            p.getInventory().setItem(1, createArrow());
        }
    }

    /** 新进服的玩家看不到别人正在玩的靶子。 */
    public void hideTargetsFrom(Player p) {
        for (ArcherySession s : sessions.values()) {
            if (s.playerId.equals(p.getUniqueId())) {
                continue;
            }
            for (TargetInstance t : s.targets) {
                Entity entity = Bukkit.getEntity(t.displayId);
                if (entity != null) {
                    p.hideEntity(plugin, entity);
                }
            }
        }
    }

    // ---------- 编辑模式 ----------

    public void enterEdit(Player p) {
        UUID id = p.getUniqueId();
        if (edits.containsKey(id)) {
            Chat.send(p, "&e你已经在编辑模式。");
            return;
        }
        if (sessions.containsKey(id)) {
            Chat.send(p, "&e先退出射箭本局再编辑。");
            return;
        }
        SavedInventory inventory = new SavedInventory(p);
        EditSession edit = new EditSession(inventory, new ArrayList<>(settings.regions));
        edits.put(id, edit);

        p.getInventory().clear();
        p.getInventory().setItem(0, createEditStick());
        p.getInventory().setItem(1, createEditConfirm());
        p.getInventory().setItem(2, createEditSave());
        p.getInventory().setItem(8, createEditExit());

        Chat.send(p, "&6&l箭术大师 · 编辑模式");
        Chat.send(p, "&f1. 木棍：左键/右键点两个对角方块");
        Chat.send(p, "&f2. 下界之星：确认区域，然后在聊天栏输入基础得分");
        Chat.send(p, "&f3. 绿宝石：保存场地");
        Chat.send(p, "&f4. 屏障：退出（未保存的修改会丢弃）");
        Chat.send(p, "&7当前已加载 &f" + edit.working.size() + " &7个区域。");
    }

    public void exitEdit(Player p, boolean restoreInventory, boolean message) {
        if (p == null) {
            return;
        }
        UUID id = p.getUniqueId();
        awaitingScore.remove(id);
        EditSession edit = edits.remove(id);
        if (edit == null) {
            return;
        }
        if (restoreInventory && p.isOnline()) {
            edit.inventory.apply(p);
        }
        if (message && p.isOnline()) {
            Chat.actionBar(p, "");
            Chat.send(p, "&a已退出箭术大师编辑模式。");
        }
    }

    public boolean isEditing(UUID id) {
        return edits.containsKey(id);
    }

    public boolean isAwaitingScore(UUID id) {
        return awaitingScore.contains(id);
    }

    public boolean isEditItem(ItemStack item) {
        return isEditStick(item) || isEditConfirm(item) || isEditSave(item) || isEditExit(item);
    }

    private boolean isEditStick(ItemStack item) {
        return marked(item, editStickKey);
    }

    private boolean isEditConfirm(ItemStack item) {
        return marked(item, editConfirmKey);
    }

    private boolean isEditSave(ItemStack item) {
        return marked(item, editSaveKey);
    }

    private boolean isEditExit(ItemStack item) {
        return marked(item, editExitKey);
    }

    private static boolean marked(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void handleEditInteract(Player p, ItemStack item, Action action, Block clicked) {
        EditSession edit = edits.get(p.getUniqueId());
        if (edit == null || item == null) {
            return;
        }

        if (isEditStick(item)) {
            if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
                Chat.send(p, "&c请对着方块左键/右键设置两个对角。");
                return;
            }
            if (clicked == null) {
                Chat.send(p, "&c请对着方块点击。");
                return;
            }
            if (action == Action.LEFT_CLICK_BLOCK) {
                edit.pos1 = clicked.getLocation();
                Chat.send(p, "&a对角 1：&f" + locText(edit.pos1));
            } else {
                edit.pos2 = clicked.getLocation();
                Chat.send(p, "&a对角 2：&f" + locText(edit.pos2));
            }
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (isEditConfirm(item)) {
            if (awaitingScore.contains(p.getUniqueId())) {
                Chat.send(p, "&e先输入上一个区域的基础得分（cancel 取消）。");
                return;
            }
            if (edit.pos1 == null || edit.pos2 == null) {
                Chat.send(p, "&c先用木棍设置两个对角方块。");
                return;
            }
            if (edit.pos1.getWorld() == null || edit.pos2.getWorld() == null
                    || !edit.pos1.getWorld().equals(edit.pos2.getWorld())) {
                Chat.send(p, "&c两个对角必须在同一个世界。");
                return;
            }
            edit.pendingPos1 = edit.pos1.clone();
            edit.pendingPos2 = edit.pos2.clone();
            edit.pos1 = null;
            edit.pos2 = null;
            awaitingScore.add(p.getUniqueId());
            Chat.send(p, "&e请输入这个区域的基础得分（1-100000，输入 cancel 取消）：");
            Chat.actionBar(p, "&e在聊天栏输入区域基础得分");
            return;
        }

        if (isEditSave(item)) {
            settings.regions = new ArrayList<>(edit.working);
            saveRegions();
            edit.dirty = false;
            Chat.send(p, "&a已保存 &f" + edit.working.size() + " &a个靶场区域到 config.yml。");
            return;
        }

        if (isEditExit(item)) {
            boolean dirty = edit.dirty;
            exitEdit(p, true, true);
            if (dirty) {
                Chat.send(p, "&7未保存的修改已丢弃。");
            }
        }
    }

    /** 聊天栏输入基础分；返回 true 表示这条聊天已被编辑模式消费。 */
    public boolean handleScoreInput(Player p, String raw) {
        UUID id = p.getUniqueId();
        if (!awaitingScore.remove(id)) {
            return false;
        }
        EditSession edit = edits.get(id);
        if (edit == null || edit.pendingPos1 == null || edit.pendingPos2 == null) {
            return true;
        }
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("cancel") || text.equals("取消")) {
            edit.pendingPos1 = null;
            edit.pendingPos2 = null;
            Chat.send(p, "&7已取消本次区域。");
            return true;
        }
        int score;
        try {
            score = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            score = -1;
        }
        if (score < 1 || score > 100000) {
            awaitingScore.add(id);
            Chat.send(p, "&c请输入 1-100000 的整数（输入 cancel 取消）。");
            return true;
        }
        TargetRegion region = TargetRegion.fromCorners(edit.pendingPos1.getWorld(),
                edit.pendingPos1, edit.pendingPos2, score);
        edit.working.add(region);
        edit.pendingPos1 = null;
        edit.pendingPos2 = null;
        edit.dirty = true;
        Chat.send(p, "&a已添加区域 #" + edit.working.size() + "：&f" + region.describe());
        Chat.send(p, "&7右键绿宝石保存场地。");
        return true;
    }

    private ItemStack createEditStick() {
        return editItem(Material.STICK, "&e区域选择棒",
                List.of("&7左键：设置对角 1", "&7右键：设置对角 2"), editStickKey);
    }

    private ItemStack createEditConfirm() {
        return editItem(Material.NETHER_STAR, "&b确认区域",
                List.of("&7两个对角都设置好后右键", "&7然后在聊天栏输入基础得分"), editConfirmKey);
    }

    private ItemStack createEditSave() {
        return editItem(Material.EMERALD, "&a保存场地",
                List.of("&7把当前所有区域写入 config.yml"), editSaveKey);
    }

    private ItemStack createEditExit() {
        return editItem(Material.BARRIER, "&c退出编辑模式",
                List.of("&7未保存的修改会丢弃"), editExitKey);
    }

    private ItemStack editItem(Material material, String name, List<String> loreLines, NamespacedKey key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(name)));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Chat.noItalic(Chat.color(line)));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ---------- 管理命令 ----------

    public List<String> listRegions() {
        List<String> out = new ArrayList<>();
        if (settings.regions.isEmpty()) {
            out.add("&7还没有配置靶场区域，用 /archery edit 添加。");
            return out;
        }
        int index = 1;
        for (TargetRegion region : settings.regions) {
            out.add("&e#" + index + " &f" + region.describe());
            index++;
        }
        return out;
    }

    public boolean removeRegion(int index) {
        if (index < 1 || index > settings.regions.size()) {
            return false;
        }
        settings.regions.remove(index - 1);
        saveRegions();
        return true;
    }

    public void setStart(Player p) {
        Location loc = p.getLocation();
        settings.startWorld = loc.getWorld() == null ? "" : loc.getWorld().getName();
        settings.startX = loc.getX();
        settings.startY = loc.getY();
        settings.startZ = loc.getZ();
        settings.startYaw = loc.getYaw();
        settings.startPitch = loc.getPitch();
        start = settings.start();
        saveStart();
        Chat.send(p, "&a射击站位已设置：&f" + locText(start));
        if (start != null && regionsFor(start.getWorld()).isEmpty()) {
            Chat.send(p, "&e注意：这个世界的靶场区域还没配置，用 /archery edit 添加。");
        }
    }

    private void saveStart() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("start.world", settings.startWorld);
        cfg.set("start.x", settings.startX);
        cfg.set("start.y", settings.startY);
        cfg.set("start.z", settings.startZ);
        cfg.set("start.yaw", (double) settings.startYaw);
        cfg.set("start.pitch", (double) settings.startPitch);
        plugin.saveConfig();
    }

    public List<String> regionInfo() {
        List<String> out = new ArrayList<>();
        if (settings.regions.isEmpty()) {
            out.add("&b靶场区域：&c未配置 &7（/archery edit 进入编辑模式）");
        } else {
            out.add("&b靶场区域：&f" + settings.regions.size() + " 个");
            int index = 1;
            for (TargetRegion region : settings.regions) {
                out.add("&7#" + index + " &f" + region.describe());
                index++;
            }
        }
        out.add("&b射击站位：&f" + (start == null
                ? "&7未配置（从玩家当前位置开玩）" : locText(start)));
        out.add("&b每局时长：&f" + settings.gameSeconds + " 秒 &7· &b靶子上限：&f"
                + settings.maxTargets + " &7· &b记录：&f" + recordCount() + " 人");
        out.add("&b靶子速度：&f" + settings.minSpeed + "~" + settings.maxSpeed
                + " 格/秒 &7· &b快速线：&f" + settings.fastSpeed + " 格/秒");
        out.add("&b加成：&f移动 +" + settings.bonusMoving + " &7· &b快速 +"
                + settings.bonusFast + " &7· &b缩小 +" + settings.bonusSmall);
        return out;
    }

    public List<String> debugInfo(UUID id) {
        ArcherySession s = sessions.get(id);
        if (s == null) {
            return List.of("&7当前不在游戏中。");
        }
        List<String> out = new ArrayList<>();
        out.add("&b本局：&f得分 " + s.score + " &7剩余 " + ((s.ticksLeft + 19) / 20)
                + "s &7靶子 " + s.targets.size() + " &7飞行中的箭 " + s.arrows.size());
        for (TargetInstance t : s.targets) {
            out.add("&7- &f" + t.typeName() + " &7基础 " + t.baseScore
                    + " &7本次 " + t.score(settings) + " &7(" + fmt(t.x) + ", "
                    + fmt(t.y) + ", " + fmt(t.z) + ")"
                    + (t.moving() ? " &7速度 " + fmt(t.speed) + " 格/秒" : ""));
        }
        return out;
    }

    private static String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "?";
        }
        return loc.getWorld().getName() + " (" + fmt(loc.getX()) + ", " + fmt(loc.getY())
                + ", " + fmt(loc.getZ()) + ")";
    }

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }

    // ---------- 排行榜 ----------

    private void loadLeaderboards() {
        leaderboards.clear();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("leaderboards")) {
            Location loc = readLbLocation(raw);
            if (loc == null) {
                continue;
            }
            Object entity = raw.get("entity");
            leaderboards.add(new LeaderBoard(loc,
                    parseUuid(entity == null ? "" : String.valueOf(entity))));
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
            list.add(map);
        }
        plugin.getConfig().set("leaderboards", list);
        plugin.saveConfig();
    }

    public void placeLeaderboard(Player p) {
        Location loc = p.getLocation().clone().add(0, 0.5, 0);
        loc.setYaw(snapYaw(loc.getYaw()));
        loc.setPitch(0f);
        LeaderBoard board = new LeaderBoard(loc, null);
        leaderboards.add(board);
        TextDisplay display = createDisplay(mainLocation(board), true);
        board.entityId = display == null ? null : display.getUniqueId();
        saveLeaderboards();
        refreshLeaderboards();
        Chat.send(p, "&a已放置箭术大师排行榜（固定朝向，每 5 秒刷新）。");
    }

    public void removeLeaderboards(CommandSender sender) {
        for (LeaderBoard board : leaderboards) {
            Entity entity = board.entityId == null ? null : Bukkit.getEntity(board.entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        leaderboards.clear();
        saveLeaderboards();
        Chat.send(sender, "&a已移除全部箭术大师排行榜。");
    }

    public void refreshLeaderboards() {
        if (settings == null) {
            return;
        }
        boolean changed = false;
        for (LeaderBoard board : leaderboards) {
            if (board.loc.getWorld() == null) {
                continue;
            }
            if (!board.loc.isChunkLoaded()) {
                board.loc.getChunk().load();
            }
            Location main = mainLocation(board);
            Entity entity = board.entityId == null ? null : Bukkit.getEntity(board.entityId);
            if (!(entity instanceof TextDisplay display)) {
                TextDisplay fresh = createDisplay(main, true);
                board.entityId = fresh == null ? null : fresh.getUniqueId();
                changed = changed || board.entityId != null;
            } else {
                display.teleport(main);
                display.setBillboard(Display.Billboard.FIXED);
                display.setRotation(snapYaw(board.loc.getYaw() + 180f), 0f);
                display.text(leaderboardText());
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(130, 15, 15, 30));
            }
        }
        if (changed) {
            saveLeaderboards();
        }
    }

    private Location mainLocation(LeaderBoard board) {
        Location main = board.loc.clone();
        main.add(0, (1 + settings.topSize) / 2.0 * LINE_HEIGHT, 0);
        return main;
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
        display.text(leaderboardText());
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(130, 15, 15, 30));
        return display;
    }

    private Component leaderboardText() {
        StringBuilder sb = new StringBuilder("&6&l—— 箭术大师 · 排行榜 ——\n");
        List<TopEntry> top = topEntries();
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
                        .append(" &7- &6").append(entry.score()).append("&7 分");
            } else {
                sb.append("&8").append(i + 1).append("&7. &8空位");
            }
            if (i < settings.topSize - 1) {
                sb.append('\n');
            }
        }
        return Chat.color(sb.toString());
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

    private static float snapYaw(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        int quarter = Math.round(normalized / 90f) % 4;
        return quarter * 90f;
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

    public int bestScore(UUID id) {
        return data.getInt("best." + id, 0);
    }

    public int recordCount() {
        ConfigurationSection best = data.getConfigurationSection("best");
        return best == null ? 0 : best.getKeys(false).size();
    }

    public record TopEntry(String id, String name, int score, long at) {
    }

    private List<TopEntry> allEntries() {
        List<TopEntry> list = new ArrayList<>();
        ConfigurationSection best = data.getConfigurationSection("best");
        if (best != null) {
            for (String id : best.getKeys(false)) {
                int score = best.getInt(id);
                if (score <= 0) {
                    continue;
                }
                long at = data.getLong("bestAt." + id, 0L);
                String name = data.getString("names." + id, "未知玩家");
                list.add(new TopEntry(id, name, score, at));
            }
        }
        list.sort(Comparator.comparingInt(TopEntry::score).reversed()
                .thenComparingLong(TopEntry::at));
        return list;
    }

    public List<TopEntry> topEntries() {
        List<TopEntry> all = allEntries();
        if (all.size() > settings.topSize) {
            return new ArrayList<>(all.subList(0, settings.topSize));
        }
        return all;
    }

    public int rankOf(UUID id) {
        if (bestScore(id) <= 0) {
            return 0;
        }
        List<TopEntry> all = allEntries();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id.toString())) {
                return i + 1;
            }
        }
        return 0;
    }

    private void recordScore(UUID id, String name, int score) {
        if (score <= 0) {
            return;
        }
        int best = data.getInt("best." + id, 0);
        if (score <= best) {
            return;
        }
        data.set("best." + id, score);
        data.set("bestAt." + id, System.currentTimeMillis());
        data.set("names." + id, name);
        saveData();
        refreshLeaderboards();
    }

    public void resetRecord(UUID id) {
        data.set("best." + id, null);
        data.set("bestAt." + id, null);
        data.set("names." + id, null);
        saveData();
        refreshLeaderboards();
    }

    public void resetAllRecords() {
        data.set("best", null);
        data.set("bestAt", null);
        data.set("names", null);
        saveData();
        refreshLeaderboards();
    }

    private void saveData() {
        try {
            PlayerStats.save(NAMESPACE, data);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存箭术大师记录失败: " + ex.getMessage());
        }
    }

    private static final class LeaderBoard {
        final Location loc;
        UUID entityId;

        LeaderBoard(Location loc, UUID entityId) {
            this.loc = loc;
            this.entityId = entityId;
        }
    }

    private static final class EditSession {
        final SavedInventory inventory;
        final List<TargetRegion> working;
        Location pos1;
        Location pos2;
        Location pendingPos1;
        Location pendingPos2;
        boolean dirty;

        EditSession(SavedInventory inventory, List<TargetRegion> working) {
            this.inventory = inventory;
            this.working = working;
        }
    }
}
