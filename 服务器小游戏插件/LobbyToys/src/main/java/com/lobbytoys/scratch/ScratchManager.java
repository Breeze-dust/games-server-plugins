package com.lobbytoys.scratch;

import com.hub.util.Chat;
import com.lobbytoys.LobbyToysPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 刮刮乐的会话管理：生成/回收 item frame 屏幕、分配地图 id、把左键长按换算成刮除。
 * 不依赖任何场地，也不进入小玩具菜单，只能通过 /lobbytoys scratch 打开（需要权限）。
 */
public final class ScratchManager {

    /** 客户端按住左键时，挥臂事件之间存在间隔；这段时间内每 2 刻补一次采样，手感才连续。 */
    private static final long HOLD_WINDOW_MILLIS = 700L;
    /** 两次采样之间至少隔这么久，避免同一刻重复刮。 */
    private static final long STROKE_INTERVAL_MILLIS = 60L;

    private final LobbyToysPlugin plugin;
    private final Map<UUID, ScratchCard> sessions = new HashMap<>();
    private final Map<String, Deque<MapView>> mapPools = new HashMap<>();

    private ScratchSettings settings;
    private int mapsCreated;
    private BukkitTask ticker;

    public ScratchManager(LobbyToysPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        closeAll("&7配置已重载，刮刮乐已收回。");
        this.settings = ScratchSettings.load(plugin, plugin.getConfig());
    }

    public void start() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    public void shutdown() {
        closeAll(null);
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public ScratchSettings settings() {
        return settings;
    }

    /** 为玩家生成一张刮刮乐。返回是否成功。 */
    public boolean open(Player player, int rows, int columns) {
        if (settings == null || !settings.enabled) {
            Chat.send(player, "&c刮刮乐当前未开启（config.yml 的 scratch.enabled）。");
            return false;
        }
        if (!plugin.settings().isAllowedWorld(player.getWorld())) {
            Chat.send(player, "&c刮刮乐只能在主城使用。");
            return false;
        }
        close(player.getUniqueId(), null);

        rows = ScratchSettings.clampInt(rows, 1, 6);
        columns = ScratchSettings.clampInt(columns, 1, 6);

        BlockFace playerFacing = cardinalFacing(player);
        BlockFace frameFacing = playerFacing.getOppositeFace();
        Vector screenDirection = playerFacing.getDirection();
        World world = player.getWorld();
        Location eye = player.getEyeLocation();

        Block target = findAirBlock(world, eye, screenDirection);
        if (target == null) {
            Chat.send(player, "&c你面前没有足够的空间放刮刮乐，请转个身再试。");
            return false;
        }
        Location frameLocation = target.getLocation().add(0.5, 0.5, 0.5);
        Vector normal = frameFacing.getDirection();
        Vector right = new Vector(0, 1, 0).crossProduct(normal).normalize();
        Vector up = new Vector(0, 1, 0);
        Vector center = frameLocation.toVector().add(normal.clone().multiply(0.5));

        MapView mapView = acquireMap(world);
        if (mapView == null) {
            Chat.send(player, "&c刮刮乐同时使用的人数已满，请稍后再试。");
            return false;
        }

        ItemFrame frame = null;
        try {
            frame = world.spawn(frameLocation, ItemFrame.class, entity -> {
                entity.setFacingDirection(frameFacing, true);
                entity.setPersistent(false);
                entity.setFixed(true);
                entity.setInvulnerable(true);
                entity.setItemDropChance(0.0f);
                entity.setRotation(Rotation.NONE);
            });

            ScratchCard card = new ScratchCard(player, settings, rows, columns, frame, mapView,
                    center, right, up, normal);
            mapView.addRenderer(card.renderer());
            frame.setItem(createMapItem(mapView), false);

            sessions.put(player.getUniqueId(), card);
            if (settings.hideFromOthers) {
                hideFromOthers(frame, player);
            }

            Chat.send(player, "&e刮刮乐已生成：&f" + rows + " 行 × " + columns + " 列"
                    + "&e，&f按住左键&e在卡面上刮开涂层。");
            Chat.actionBar(player, "&7刮开越多，拿到的奖品越多；&f右键不会误触");
            return true;
        } catch (RuntimeException ex) {
            if (frame != null && frame.isValid()) {
                frame.remove();
            }
            releaseMap(mapView);
            plugin.getLogger().warning("生成刮刮乐失败：" + ex.getMessage());
            Chat.send(player, "&c刮刮乐生成失败，请稍后再试。");
            return false;
        }
    }

    private ItemStack createMapItem(MapView mapView) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        if (stack.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(mapView);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 从眼前往外找一格空气，优先用配置里的距离，近了就往回收。 */
    private Block findAirBlock(World world, Location eye, Vector direction) {
        double distance = settings.screenDistance;
        while (distance >= 1.0) {
            Vector point = eye.toVector().add(direction.clone().multiply(distance));
            Block block = world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ());
            if (block.getType().isAir()) {
                return block;
            }
            distance -= 0.5;
        }
        return null;
    }

    /** Minecraft 的 yaw：0 = 南（+Z），90 = 西（-X），180 = 北（-Z），270 = 东（+X）。 */
    private static BlockFace cardinalFacing(Player player) {
        double yaw = ((player.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) {
            return BlockFace.SOUTH;
        }
        if (yaw < 135) {
            return BlockFace.WEST;
        }
        if (yaw < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private void hideFromOthers(ItemFrame frame, Player owner) {
        for (Player other : frame.getWorld().getPlayers()) {
            if (!other.equals(owner)) {
                other.hideEntity(plugin, frame);
            }
        }
    }

    /** 新玩家进服时，把已经存在的刮刮乐屏幕从他那边藏掉（他只该看到自己的）。 */
    public void hideFrom(Player joiner) {
        if (!settings.hideFromOthers) {
            return;
        }
        for (ScratchCard card : sessions.values()) {
            if (!card.player().equals(joiner) && card.frame().getWorld().equals(joiner.getWorld())) {
                joiner.hideEntity(plugin, card.frame());
            }
        }
    }

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<ScratchCard> closing = new ArrayList<>();
        for (ScratchCard card : sessions.values()) {
            if (card.expired() || card.outOfRange()) {
                closing.add(card);
                continue;
            }
            if (now - card.lastSwingAt() <= HOLD_WINDOW_MILLIS) {
                sample(card);
            }
        }
        for (ScratchCard card : closing) {
            close(card, "&7刮刮乐已收回。");
        }
    }

    /** 玩家挥了一下手臂（按住左键会持续触发），立刻按视线采样一次。 */
    public void onSwing(Player player) {
        ScratchCard card = sessions.get(player.getUniqueId());
        if (card == null) {
            return;
        }
        card.markSwing();
        sample(card);
    }

    private void sample(ScratchCard card) {
        Player player = card.player();
        if (!player.isOnline()) {
            return;
        }
        double[] point = card.screenCoords(player);
        if (point == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - card.lastStrokeAt() < STROKE_INTERVAL_MILLIS) {
            return;
        }
        List<int[]> revealed = card.scratch(point[0], point[1], settings.brushRadius);
        player.sendMap(card.mapView());
        for (int[] cell : revealed) {
            award(card, cell[0], cell[1]);
        }
    }

    private void award(ScratchCard card, int row, int column) {
        ScratchPrize prize = card.prizeAt(row, column);
        if (prize == null) {
            return;
        }
        Player player = card.player();

        if (prize.givesItem()) {
            ItemStack stack = new ItemStack(prize.material(), prize.amount());
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack left : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        Chat.actionBar(player, "&e第 " + (row + 1) + " 行第 " + (column + 1) + " 列：&f" + prize.name());
        if (prize.message() != null && !prize.message().isBlank()) {
            Chat.send(player, prize.message());
        }
        if (prize.sound() != null) {
            player.playSound(player.getLocation(), prize.sound(), 1.0f, 1.0f);
        }
        for (String command : prize.commands()) {
            String resolved = command
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%prize%", prize.id());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
        Bukkit.getPluginManager().callEvent(new ScratchCardPrizeEvent(player, prize.id(), row, column));
    }

    public void close(UUID playerId, String message) {
        ScratchCard card = sessions.get(playerId);
        if (card == null) {
            return;
        }
        close(card, message);
    }

    private void close(ScratchCard card, String message) {
        sessions.remove(card.player().getUniqueId(), card);
        if (card.frame().isValid()) {
            card.frame().remove();
        }
        releaseMap(card.mapView());
        if (message != null && card.player().isOnline()) {
            Chat.send(card.player(), message);
        }
    }

    private void closeAll(String message) {
        for (ScratchCard card : new ArrayList<>(sessions.values())) {
            close(card, message);
        }
    }

    public boolean isCardFrame(Entity entity) {
        for (ScratchCard card : sessions.values()) {
            if (card.frame().equals(entity)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private MapView acquireMap(World world) {
        Deque<MapView> pool = mapPools.computeIfAbsent(world.getName(), key -> new ArrayDeque<>());
        MapView view = pool.pollFirst();
        if (view != null) {
            removeRenderers(view);
            return view;
        }
        int limit = Math.max(1, settings.maxSessions);
        if (mapsCreated >= limit) {
            return null;
        }
        mapsCreated++;
        MapView created = Bukkit.createMap(world);
        created.setScale(MapView.Scale.CLOSEST);
        created.setTrackingPosition(false);
        created.setUnlimitedTracking(false);
        created.setLocked(false);
        removeRenderers(created);
        return created;
    }

    private void releaseMap(MapView view) {
        removeRenderers(view);
        view.setLocked(false);
        mapPools.computeIfAbsent(view.getWorld().getName(), key -> new ArrayDeque<>()).addLast(view);
    }

    private static void removeRenderers(MapView view) {
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(renderer);
        }
    }
}
