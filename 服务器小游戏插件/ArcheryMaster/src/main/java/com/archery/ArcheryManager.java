package com.archery;

import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
import java.util.UUID;

/** 箭术大师主逻辑：开局、靶子、箭矢命中判定、结算、排行榜。 */
public class ArcheryManager {

    private static final String NAMESPACE = "archery";
    private static final int TICK_PERIOD = 1;
    private static final double LINE_HEIGHT = 0.25 * 1.3;

    private final ArcheryPlugin plugin;
    private final Map<UUID, ArcherySession> sessions = new LinkedHashMap<>();
    private final List<LeaderBoard> leaderboards = new ArrayList<>();
    private final Random random = new Random();
    private final org.bukkit.NamespacedKey bowKey;
    private final org.bukkit.NamespacedKey arrowKey;

    private ArcherySettings settings;
    private CylinderRegion region;
    private Location start;
    private YamlConfiguration data;

    private BukkitTask tickTask;
    private BukkitTask maintenanceTask;
    private long lastTickErrorLog;

    public ArcheryManager(ArcheryPlugin plugin) {
        this.plugin = plugin;
        this.bowKey = new org.bukkit.NamespacedKey(plugin, "archery_bow");
        this.arrowKey = new org.bukkit.NamespacedKey(plugin, "archery_arrow");
    }

    // ---------- 加载 / 保存 ----------

    public void load() {
        settings = ArcherySettings.load(plugin.getConfig());
        region = buildRegion(settings);
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

        plugin.getLogger().info("箭术大师已加载：区域" + (region == null ? "未配置" : "已配置")
                + "，站位" + (start == null ? "未配置" : "已配置")
                + "，时长 " + settings.gameSeconds + " 秒，记录 " + recordCount() + " 人。");
    }

    public void onDisable() {
        for (ArcherySession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            finish(p, s, "服务器关闭", false, true, false);
        }
    }

    public void reload() {
        for (ArcherySession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            finish(p, s, "重载", true, true, true);
        }
        plugin.reloadConfig();
        load();
    }

    private CylinderRegion buildRegion(ArcherySettings s) {
        if (s.regionWorld == null || s.regionWorld.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(s.regionWorld);
        if (world == null) {
            return null;
        }
        return new CylinderRegion(world, s.centerX, s.centerZ, s.radius, s.yMin, s.yMax);
    }

    private String configProblem(Player p) {
        if (region == null) {
            return "&c靶子区域还没配置，请联系管理员（/archery setarea）。";
        }
        if (start != null && !region.world().getName().equals(start.getWorld().getName())) {
            return "&c靶子区域和射击站位不在同一个世界，请联系管理员。";
        }
        if (start == null && !p.getWorld().equals(region.world())) {
            return "&c你不在靶子区域所在的世界，请让管理员配置 /archery setstart。";
        }
        return null;
    }

    // ---------- 开局 / 结束 ----------

    public void join(Player p) {
        UUID id = p.getUniqueId();
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

        ArcherySession session = new ArcherySession(id, p.getName(), returnLocation, inventory,
                settings.gameSeconds * 20);
        sessions.put(id, session);
        for (int i = 0; i < settings.maxTargets; i++) {
            spawnTarget(session);
        }

        Chat.title(p, "&6&l箭术大师", "&f" + settings.gameSeconds + " 秒内射中更多靶子！", 2);
        Chat.send(p, "&6&l箭术大师 &7· &f固定靶 &e+" + settings.scoreFixedNormal
                + "&7，缩小靶 &e+" + settings.scoreFixedSmall
                + "&7，移动靶 &e+" + settings.scoreMovingNormal
                + "&7，缩小移动靶 &e+" + settings.scoreMovingSmall
                + "&7，快速移动靶 &e+" + settings.scoreFastNormal
                + "&7，缩小快速移动靶 &e+" + settings.scoreFastSmall);
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
            if (region == null || !p.getWorld().equals(region.world())) {
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
        if (region == null || s.finished || s.targets.size() >= settings.maxTargets) {
            return;
        }

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

        Material material = fast ? settings.fastBlock : (moving ? settings.movingBlock : settings.fixedBlock);
        if (!point.getChunk().isLoaded()) {
            point.getChunk().load();
        }
        Location spawnLocation = point.clone();
        float offset = (float) (-size / 2.0);
        BlockDisplay display = region.world().spawn(spawnLocation, BlockDisplay.class, entity -> {
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
            if (moving) {
                entity.setGlowing(true);
                entity.setGlowColorOverride(fast ? Color.RED : Color.YELLOW);
            }
        });
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(s.playerId)) {
                other.hideEntity(plugin, display);
            }
        }
        s.targets.add(new TargetInstance(display.getUniqueId(),
                point.getX(), point.getY(), point.getZ(), vx, vz, small, speed, fast,
                System.currentTimeMillis()));
    }

    private void tickTargets(ArcherySession s) {
        if (region == null || s.targets.isEmpty()) {
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
            t.x += t.vx;
            t.z += t.vz;
            double dx = t.x - region.centerX();
            double dz = t.z - region.centerZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double max = Math.max(0.1, region.radius() - t.size(settings) / 2.0);
            if (distance > max) {
                double nx = distance < 1.0e-6 ? 1.0 : dx / distance;
                double nz = distance < 1.0e-6 ? 0.0 : dz / distance;
                double dot = t.vx * nx + t.vz * nz;
                t.vx -= 2.0 * dot * nx;
                t.vz -= 2.0 * dot * nz;
                t.x = region.centerX() + nx * max;
                t.z = region.centerZ() + nz * max;
            }
            display.teleport(new Location(region.world(), t.x, t.y, t.z));
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

    public void onArrowHit(Arrow arrow) {
        for (ArcherySession s : sessions.values()) {
            if (s.arrows.remove(arrow.getUniqueId()) != null) {
                arrow.remove();
                return;
            }
        }
    }

    private void detectHits(Player p, ArcherySession s) {
        if (s.arrows.isEmpty() || s.targets.isEmpty() || region == null) {
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
            if (now.getWorld() == null || !now.getWorld().equals(region.world())) {
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
                BoundingBox box = target.box(settings);
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
        int gained = target.score(settings);
        s.score += gained;

        Entity entity = Bukkit.getEntity(target.displayId);
        if (entity != null) {
            entity.remove();
        }
        s.targets.remove(target);
        scheduleRespawn(s);

        if (settings.sound) {
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
        }
        if (settings.particle && region != null) {
            Location at = point != null
                    ? point.toLocation(region.world())
                    : new Location(region.world(), target.x, target.y, target.z);
            region.world().spawnParticle(Particle.CRIT, at, 18, 0.25, 0.25, 0.25, 0.05);
            region.world().spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.2, 0.2, 0.2, 0.01);
        }
        Chat.actionBar(p, "&a+" + gained + " &8· " + bar(s));
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

    // ---------- 管理命令 ----------

    public void setArea(Player p, double centerX, double centerZ, double radius, double yMin, double yMax) {
        double safeRadius = Math.max(1.0, Math.min(256.0, radius));
        if (yMax < yMin) {
            double swap = yMin;
            yMin = yMax;
            yMax = swap;
        }
        if (yMax - yMin < 0.5) {
            yMax = yMin + 0.5;
        }
        settings.regionWorld = p.getWorld().getName();
        settings.centerX = centerX;
        settings.centerZ = centerZ;
        settings.radius = safeRadius;
        settings.yMin = yMin;
        settings.yMax = yMax;
        region = buildRegion(settings);
        saveRegion();
        Chat.send(p, "&a靶子区域已设置：&f" + (region == null ? "无效" : region.toString()));
        if (settings.startWorld != null && !settings.startWorld.isEmpty()
                && !settings.startWorld.equals(settings.regionWorld)) {
            Chat.send(p, "&e注意：射击站位在另一个世界，请重新执行 &f/archery setstart&e。");
        }
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
        if (region != null && start != null
                && !region.world().getName().equals(start.getWorld().getName())) {
            Chat.send(p, "&e注意：射击站位和靶子区域不在同一个世界。");
        }
    }

    private void saveRegion() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("region.world", settings.regionWorld);
        cfg.set("region.center-x", settings.centerX);
        cfg.set("region.center-z", settings.centerZ);
        cfg.set("region.radius", settings.radius);
        cfg.set("region.y-min", settings.yMin);
        cfg.set("region.y-max", settings.yMax);
        plugin.saveConfig();
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
        out.add("&b靶子区域：&f" + (region == null ? "&c未配置" : region.toString()));
        out.add("&b射击站位：&f" + (start == null
                ? "&7未配置（从玩家当前位置开玩）" : locText(start)));
        out.add("&b每局时长：&f" + settings.gameSeconds + " 秒 &7· &b靶子上限：&f"
                + settings.maxTargets + " &7· &b记录：&f" + recordCount() + " 人");
        out.add("&b靶子速度：&f" + settings.minSpeed + "~" + settings.maxSpeed
                + " 格/秒 &7· &b快速线：&f" + settings.fastSpeed + " 格/秒");
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
            out.add("&7- &f" + t.typeName() + " &7(" + fmt(t.x) + ", " + fmt(t.y) + ", "
                    + fmt(t.z) + ")" + (t.moving() ? " &7速度 " + fmt(t.speed) + " 格/秒" : ""));
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
}
