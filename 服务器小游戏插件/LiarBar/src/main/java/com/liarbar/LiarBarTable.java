package com.liarbar;

import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.util.Chat;
import com.hub.util.WaitCountdown;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一张桌子 = 一个独立场次：4 个台阶座椅、自己的倒计时、自己的牌局。
 * 多张桌子可以同时开局，互不影响。
 */
public final class LiarBarTable {

    public enum State {WAITING, STARTING, PLAYING, ENDING}

    /** 槽位约定：0~4 手牌，6 目标牌提示，8 质疑钟；准备道具占槽位 2（与公共库一致）。 */
    public static final int TABLE_SLOT = 6;
    public static final int BELL_SLOT = 8;
    public static final int READY_SLOT = 2;

    private static final class Seat {
        final Location chair;
        final Location sit;
        UUID occupant;
        ArmorStand stand;
        Mannequin corpse;
        Location dispenser;

        Seat(Location chair, Location sit) {
            this.chair = chair;
            this.sit = sit;
        }
    }

    private final Plugin plugin;
    private final LiarBarManager manager;
    private final LiarBarSettings settings;
    private final String name;
    private final List<Seat> seats = new ArrayList<>();
    private Location center;

    private State state = State.WAITING;
    private BukkitTask tickTask;
    private BukkitTask endTask;
    private final WaitCountdown countdown;

    /** 存活玩家（按座位顺序）。 */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Set<Integer>> selections = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    /** 入座时槽位 2 原来的物品（发准备道具前备份）。 */
    private final Map<UUID, ItemStack> readyBackup = new HashMap<>();

    private int round;
    private int tableRank;
    private int turnIndex;
    private int secondsLeft;
    private boolean transition;
    private int pileCount;
    private UUID lastPlayOwner;
    private List<LiarBarCards.Card> lastPlayCards;

    // 左轮（整张桌子共用一把，开局随机起始位置，扣一次转一格）
    private int cylinderStart;
    private int bulletAt;
    private int pulls;

    // 揭幕 / 开枪
    private final List<ItemFrame> frames = new ArrayList<>();
    private UUID pendingShooter;
    private int pendingShooterIndex;
    private BukkitTask fireTimeout;
    private BukkitTask revealTask;
    private BukkitTask waterTask;
    private BukkitTask flowTask;
    private BukkitTask fluidGuardTask;
    private final Map<UUID, BukkitTask> burnTasks = new HashMap<>();
    private final List<Block> fluidBlocks = new ArrayList<>();

    public LiarBarTable(Plugin plugin, LiarBarManager manager, LiarBarSettings settings,
                        String name, List<Location> chairs) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.name = name;
        this.countdown = new WaitCountdown(plugin, new WaitCountdown.Host() {
            @Override
            public Collection<UUID> waitingPlayers() {
                List<UUID> ids = new ArrayList<>();
                for (Seat seat : seats) {
                    if (seat.occupant != null) {
                        ids.add(seat.occupant);
                    }
                }
                return ids;
            }

            @Override
            public int minPlayers() {
                return settings.playersPerTable;
            }

            @Override
            public int maxPlayers() {
                return settings.playersPerTable;
            }

            @Override
            public boolean hasOpQueued() {
                return false;
            }

            @Override
            public void onTick(long secondsLeft) {
                for (Seat seat : seats) {
                    Player p = playerOf(seat.occupant);
                    if (p != null) {
                        Chat.actionBar(p, "&6" + secondsLeft + "s 后自动开始…");
                    }
                }
            }

            @Override
            public void onMessage(String message) {
                broadcastSeated(message);
            }

            @Override
            public void onStart() {
                startGame();
            }

            @Override
            public String startMessage(int seconds) {
                return "&6" + seconds + "s 后自动开局…";
            }
        });
        setSeats(chairs);
    }

    // ---------- 座位 ----------

    /** 重建座椅（只在没人入座、非牌局中时允许）。 */
    public void setSeats(List<Location> chairs) {
        seats.clear();
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (Location chair : chairs) {
            cx += chair.getBlockX() + 0.5;
            cy += chair.getBlockY() + 0.5;
            cz += chair.getBlockZ() + 0.5;
        }
        int count = Math.max(1, chairs.size());
        center = chairs.isEmpty() ? null
                : new Location(chairs.get(0).getWorld(), cx / count, cy / count, cz / count);
        for (Location chair : chairs) {
            Location sit = chair.getBlock().getLocation().add(0.5, 0.45, 0.5);
            if (center != null) {
                Location target = center.clone().subtract(sit);
                if (target.toVector().lengthSquared() > 0.0001) {
                    sit.setDirection(target.toVector());
                }
            }
            sit.setPitch(0);
            seats.add(new Seat(chair.getBlock().getLocation(), sit));
        }
    }

    public String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public int seatCount() {
        return seats.size();
    }

    public int occupiedCount() {
        int n = 0;
        for (Seat seat : seats) {
            if (seat.occupant != null) {
                n++;
            }
        }
        return n;
    }

    public boolean isFull() {
        return seats.size() >= settings.playersPerTable
                && occupiedCount() >= settings.playersPerTable;
    }

    public boolean isSeated(Player player) {
        return seatOf(player) != null;
    }

    public boolean hasChair(Block block) {
        for (Seat seat : seats) {
            if (sameBlock(seat.chair, block)) {
                return true;
            }
        }
        return false;
    }

    private Seat seatOf(Player player) {
        return player == null ? null : seatOf(player.getUniqueId());
    }

    private Seat seatOf(UUID id) {
        if (id == null) {
            return null;
        }
        for (Seat seat : seats) {
            if (id.equals(seat.occupant)) {
                return seat;
            }
        }
        return null;
    }

    private static boolean sameBlock(Location loc, Block block) {
        return loc.getWorld() != null
                && loc.getWorld().equals(block.getWorld())
                && loc.getBlockX() == block.getX()
                && loc.getBlockY() == block.getY()
                && loc.getBlockZ() == block.getZ();
    }

    /** 右键椅子：入座并视为准备。 */
    public boolean sit(Player player) {
        if (state == State.PLAYING || state == State.ENDING) {
            Chat.actionBar(player, "&c这张桌子正在对局中，等本局结束再入座。");
            return false;
        }
        if (seatOf(player) != null) {
            return false;
        }
        if (occupiedCount() >= settings.playersPerTable) {
            Chat.actionBar(player, "&c这张桌子坐满了。");
            return false;
        }
        Seat free = null;
        for (Seat seat : seats) {
            if (seat.occupant == null) {
                free = seat;
                break;
            }
        }
        if (free == null) {
            Chat.actionBar(player, "&c这张桌子还没配好椅子，等管理员登记。");
            return false;
        }
        free.occupant = player.getUniqueId();
        names.put(player.getUniqueId(), player.getName());
        free.dispenser = findDispenser(free);
        manager.bind(player, this);
        player.teleport(free.sit);
        ArmorStand stand = spawnSeatStand(free);
        free.stand = stand;
        if (stand != null) {
            stand.addPassenger(player);
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        giveReadyItem(player);
        Chat.actionBar(player, "&a已入座（" + occupiedCount() + "/" + settings.playersPerTable
                + "），右键准备道具或直接等四人坐满");
        tableSound(Sound.UI_BUTTON_CLICK, 0.9f, 1.4f);
        afterSeatChange();
        return true;
    }

    private Location findDispenser(Seat seat) {
        World world = seat.chair.getWorld();
        if (world == null) {
            return null;
        }
        for (int dy = 1; dy <= 8; dy++) {
            Block block = seat.chair.clone().add(0, dy, 0).getBlock();
            if (block.getType() != Material.DISPENSER) {
                continue;
            }
            if (block.getBlockData() instanceof Directional directional
                    && directional.getFacing() == BlockFace.DOWN) {
                return block.getLocation();
            }
        }
        return null;
    }

    private ArmorStand spawnSeatStand(Seat seat) {
        World world = seat.sit.getWorld();
        if (world == null) {
            return null;
        }
        ArmorStand stand = (ArmorStand) world.spawnEntity(seat.sit, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCollidable(false);
        stand.setPersistent(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomNameVisible(false);
        return stand;
    }

    /** 右键自己的椅子：起身。 */
    public void standUp(Player player) {
        if (state == State.PLAYING || state == State.ENDING) {
            Chat.actionBar(player, "&c对局中不能起身，想离开请用 &e/liarbar leave&c。");
            return;
        }
        Seat seat = seatOf(player);
        if (seat == null) {
            return;
        }
        clearSeat(seat, player);
        Chat.actionBar(player, "&7你起身离开了座位。");
        tableSound(Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.7f, 1.2f);
    }

    /** 玩家自己按下潜行/下坐骑。 */
    public void onVoluntaryDismount(Player player) {
        Seat seat = seatOf(player);
        if (seat == null) {
            return;
        }
        clearSeat(seat, player);
    }

    private void clearSeat(Seat seat, Player player) {
        UUID id = seat.occupant;
        seat.occupant = null;
        seat.dispenser = null;
        if (seat.corpse != null) {
            seat.corpse.remove();
            seat.corpse = null;
        }
        if (id != null) {
            countdown.removeReady(id);
            if (player != null) {
                restoreReadyItem(player);
            } else {
                readyBackup.remove(id);
            }
        }
        if (seat.stand != null) {
            ArmorStand stand = seat.stand;
            seat.stand = null;
            if (id != null) {
                manager.markForceDismount(id);
            }
            try {
                if (player != null && player.getVehicle() == stand) {
                    player.leaveVehicle();
                }
            } finally {
                if (id != null) {
                    manager.clearForceDismount(id);
                }
            }
            stand.remove();
        }
        if (id != null) {
            manager.unbind(id);
            selections.remove(id);
            if (state == State.PLAYING) {
                eliminateQuitter(id);
            }
        }
        afterSeatChange();
    }

    /** 对局中离座 = 放弃本局。 */
    private void eliminateQuitter(UUID id) {
        if (!order.remove(id)) {
            return;
        }
        broadcastSeated("&7" + nameOf(id) + " 离开了牌桌，视为出局。");
        if (order.size() <= 1) {
            endGame(order.isEmpty() ? null : order.get(0));
        } else {
            manager.reportArena(this);
        }
    }

    private void afterSeatChange() {
        manager.reportArena(this);
        evaluateStart();
    }

    /**
     * 开局条件：坐满 {@code players-per-table} 人直接开；不足时只要至少 {@code min-players} 人
     * 且所有入座玩家都点了准备，也开（二人局 / 三人局）。
     * <p>
     * 公共库 WaitCountdown 的 update() 只在人数≥min 时才检查「全员准备」，所以这里自己判定；
     * 准备绑定仍然走 WaitCountdown（tab 才能显示已准备），倒计时也用它。
     */
    public void evaluateStart() {
        if (state != State.WAITING) {
            return;
        }
        int seated = occupiedCount();
        if (seated < settings.minPlayers) {
            if (countdown.isRunning()) {
                countdown.cancel();
            }
            return;
        }
        boolean allReady = true;
        for (Seat seat : seats) {
            if (seat.occupant != null && !countdown.isReady(seat.occupant)) {
                allReady = false;
                break;
            }
        }
        if (seated >= settings.playersPerTable || allReady) {
            if (!countdown.isRunning()) {
                countdown.start(WaitCountdown.FULL_SECONDS);
            }
        } else if (countdown.isRunning()) {
            countdown.cancel();
        }
    }

    // ---------- 准备道具 ----------

    /** 给入座玩家发准备道具（槽位 2；原来的物品先备份，起身/开局前还原）。 */
    private void giveReadyItem(Player player) {
        UUID id = player.getUniqueId();
        if (!readyBackup.containsKey(id)) {
            ItemStack old = player.getInventory().getItem(READY_SLOT);
            readyBackup.put(id, old == null ? new ItemStack(Material.AIR) : old.clone());
        }
        countdown.giveReadyItem(player);
        player.updateInventory();
    }

    private void restoreReadyItem(Player player) {
        UUID id = player.getUniqueId();
        ItemStack backup = readyBackup.remove(id);
        if (backup == null) {
            return;
        }
        player.getInventory().setItem(READY_SLOT,
                backup.getType() == Material.AIR ? null : backup);
        player.updateInventory();
    }

    /** 对局中是否禁止起身（潜行下坐骑会被拦下来）。 */
    public boolean blocksStanding() {
        return state == State.PLAYING || state == State.ENDING;
    }

    // ---------- 开局 ----------

    /** 管理员 /liarbar start 强制开局。 */
    public void forceStart() {
        if (state != State.WAITING) {
            return;
        }
        startGame();
    }

    private void startGame() {
        if (occupiedCount() < settings.minPlayers) {
            return;
        }
        countdown.cancel();
        state = State.PLAYING;
        transition = false;
        round = 0;
        order.clear();
        selections.clear();
        names.clear();
        for (Seat seat : seats) {
            if (seat.occupant == null) {
                continue;
            }
            UUID id = seat.occupant;
            order.add(id);
            names.put(id, nameOf(id));
            Player p = playerOf(id);
            if (p != null) {
                countdown.removeReady(id);
                restoreReadyItem(p);
                manager.captureSnapshot(p);
                HubAPI.setPlaying(id, LiarBarManager.GAME);
                p.setGameMode(GameMode.ADVENTURE);
                p.setFoodLevel(20);
                Chat.title(p, "&6&l骗子酒馆", "&f坐稳了，别被唬住", 3);
                p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.7f);
            }
        }
        reloadCylinder();
        manager.reportArena(this);
        startRound(null);
    }

    private void reloadCylinder() {
        cylinderStart = ThreadLocalRandom.current().nextInt(settings.chambers);
        bulletAt = ThreadLocalRandom.current().nextInt(settings.chambers);
        pulls = 0;
    }

    // ---------- 回合 ----------

    private void startRound(UUID starter) {
        if (state != State.PLAYING) {
            return;
        }
        clearColumn();
        transition = false;
        round++;
        tableRank = ThreadLocalRandom.current().nextInt(LiarBarCards.NORMAL_RANKS);
        lastPlayOwner = null;
        lastPlayCards = null;
        pileCount = 0;
        dealRound();
        if (starter != null && order.contains(starter)) {
            turnIndex = order.indexOf(starter);
        } else {
            turnIndex = ThreadLocalRandom.current().nextInt(Math.max(1, order.size()));
        }
        secondsLeft = settings.turnSeconds;
        for (UUID id : order) {
            Player p = playerOf(id);
            if (p != null) {
                Chat.title(p, "&e第 " + round + " 回合", "&7目标牌：&f" + LiarBarCards.NAMES[tableRank]
                        + " &7（打出的牌都要声称是它）", 2);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
        }
        ensureTickTask();
        updateActionBars();
        manager.reportArena(this);
    }

    private void dealRound() {
        List<LiarBarCards.Card> deck = LiarBarCards.shuffledDeck();
        int index = 0;
        for (UUID id : order) {
            Player p = playerOf(id);
            if (p == null) {
                continue;
            }
            p.getInventory().clear();
            for (int slot = 0; slot < settings.handSize && index < deck.size(); slot++) {
                p.getInventory().setItem(slot, manager.items().card(deck.get(index++), false));
            }
            p.getInventory().setItem(TABLE_SLOT,
                    manager.items().tableCard(tableRank));
            p.getInventory().setItem(BELL_SLOT, manager.items().bell());
            p.updateInventory();
            selections.computeIfAbsent(id, k -> new HashSet<>()).clear();
        }
        manager.refreshDisguises();
    }

    private void ensureTickTask() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void cancelTick() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        if (state != State.PLAYING) {
            cancelTick();
            return;
        }
        if (transition) {
            updateActionBars();
            return;
        }
        secondsLeft--;
        if (secondsLeft <= 0) {
            UUID current = currentPlayer();
            if (current == null) {
                cancelTick();
                return;
            }
            Player p = playerOf(current);
            if (p != null) {
                Chat.actionBar(p, "&c超时，自动打出一张牌。");
            }
            autoPlay(current);
            return;
        }
        updateActionBars();
    }

    private void autoPlay(UUID id) {
        Player p = playerOf(id);
        if (p == null) {
            advanceTurn();
            return;
        }
        Integer slot = firstCardSlot(p);
        if (slot != null) {
            play(p, List.of(slot), true);
        } else if (lastPlayOwner != null && !lastPlayOwner.equals(id)) {
            challenge(p);
        } else {
            advanceTurn();
        }
    }

    private Integer firstCardSlot(Player p) {
        for (int slot = 0; slot < settings.handSize; slot++) {
            if (manager.items().cardOf(p.getInventory().getItem(slot)) != null) {
                return slot;
            }
        }
        return null;
    }

    // ---------- 出牌 / 质疑 ----------

    public void toggleSelect(Player player, Integer slot) {
        if (!isTurn(player)) {
            Chat.actionBar(player, "&7还没轮到你。");
            return;
        }
        if (slot == null || slot < 0 || slot >= settings.handSize) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        LiarBarCards.Card card = manager.items().cardOf(item);
        if (card == null) {
            return;
        }
        Set<Integer> selected = selections.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (selected.contains(slot)) {
            selected.remove(slot);
            player.getInventory().setItem(slot, manager.items().card(card, false));
        } else {
            if (selected.size() >= settings.maxPlayCards) {
                Chat.actionBar(player, "&c最多一次声称 " + settings.maxPlayCards + " 张。");
                return;
            }
            selected.add(slot);
            player.getInventory().setItem(slot, manager.items().card(card, true));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.8f);
        }
        player.updateInventory();
        Chat.actionBar(player, "&e已选 &f" + selected.size() + " &e张，右键打出。");
        manager.refreshDisguise(player);
    }

    public void playSelected(Player player) {
        if (!isTurn(player)) {
            Chat.actionBar(player, "&7还没轮到你。");
            return;
        }
        Set<Integer> selected = new LinkedHashSet<>(
                selections.getOrDefault(player.getUniqueId(), Set.of()));
        if (selected.isEmpty()) {
            Chat.actionBar(player, "&e先左键选中 1~" + settings.maxPlayCards + " 张牌。");
            return;
        }
        play(player, new ArrayList<>(selected), false);
    }

    private boolean play(Player player, List<Integer> slots, boolean auto) {
        if (!isTurn(player)) {
            if (!auto) {
                Chat.actionBar(player, "&7还没轮到你。");
            }
            return false;
        }
        if (transition) {
            Chat.actionBar(player, "&e正在结算，稍等。");
            return false;
        }
        Set<Integer> unique = new LinkedHashSet<>(slots);
        if (unique.isEmpty() || unique.size() > settings.maxPlayCards) {
            Chat.actionBar(player, "&c一次只能出 1~" + settings.maxPlayCards + " 张牌。");
            return false;
        }
        List<LiarBarCards.Card> cards = new ArrayList<>();
        for (int slot : unique) {
            LiarBarCards.Card card = manager.items().cardOf(player.getInventory().getItem(slot));
            if (card == null) {
                Chat.actionBar(player, "&c选中的牌不见了，重新选。");
                return false;
            }
            cards.add(card);
        }
        for (int slot : unique) {
            player.getInventory().setItem(slot, null);
        }
        selections.getOrDefault(player.getUniqueId(), new HashSet<>()).clear();
        player.updateInventory();
        manager.refreshDisguise(player);
        lastPlayOwner = player.getUniqueId();
        lastPlayCards = cards;
        pileCount += cards.size();
        broadcastSeated("&e" + player.getName() + " &f打出 &e" + cards.size()
                + " &f张牌，声称是 &f" + LiarBarCards.NAMES[tableRank]);
        tableSound(Sound.BLOCK_WOOD_PLACE, 0.7f, 1.1f);
        advanceTurn();
        return true;
    }

    public void challenge(Player player) {
        if (!isTurn(player)) {
            Chat.actionBar(player, "&7还没轮到你。");
            return;
        }
        if (transition) {
            return;
        }
        if (lastPlayOwner == null) {
            Chat.actionBar(player, "&7本轮还没有人出牌，不能质疑。");
            return;
        }
        UUID challenger = player.getUniqueId();
        UUID accused = lastPlayOwner;
        List<LiarBarCards.Card> cards = lastPlayCards == null
                ? new ArrayList<>() : new ArrayList<>(lastPlayCards);

        boolean lie = cards.stream().anyMatch(card -> !card.matches(tableRank));
        UUID shooter = lie ? accused : challenger;

        transition = true;
        pendingShooter = shooter;
        pendingShooterIndex = Math.max(0, order.indexOf(shooter));
        broadcastSeated("&c" + nameOf(challenger) + " &f质疑 &e" + nameOf(accused) + "&f！");
        tableSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        beginReveal(cards, lie, shooter);
    }

    /** 揭幕环节：每名玩家面前出现出牌数量个展示框，问号 → 从左到右翻开。 */
    private void beginReveal(List<LiarBarCards.Card> cards, boolean lie, UUID shooter) {
        List<LiarBarCards.Card> sorted = new ArrayList<>(cards);
        // 目标牌（含万能牌）尽量放前面
        sorted.sort(Comparator.comparingInt(card -> card.matches(tableRank) ? 0 : 1));
        int count = Math.max(1, Math.min(settings.maxPlayCards, sorted.size()));

        frames.clear();
        for (Seat seat : seats) {
            if (seat.occupant == null) {
                continue;
            }
            List<Location> points = revealPoints(seat, count);
            for (Location point : points) {
                try {
                    ItemFrame frame = seat.sit.getWorld().spawn(point, ItemFrame.class);
                    frame.setFixed(true);
                    frame.setVisible(true);
                    frame.setInvulnerable(true);
                    frame.setItem(manager.items().question(), false);
                    frame.setFacingDirection(frameFacing(seat), true);
                    frames.add(frame);
                } catch (RuntimeException ignored) {
                    // 位置不合法时跳过这一格
                }
            }
        }

        for (int i = 0; i < sorted.size(); i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state != State.PLAYING) {
                    return;
                }
                LiarBarCards.Card card = sorted.get(index);
                for (ItemFrame frame : frames) {
                    if (frame.isValid()) {
                        frame.setItem(manager.items().face(card), false);
                    }
                }
                broadcastSeated("&7揭晓第 &f" + (index + 1) + " &7张：&f" + card.display());
                for (Seat seat : seats) {
                    Player p = playerOf(seat.occupant);
                    if (p != null) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.7f,
                                1.0f + index * 0.15f);
                    }
                }
            }, (long) settings.revealDelayTicks * (i + 1));
        }

        long finish = (long) settings.revealDelayTicks * (sorted.size() + 2);
        revealTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeFrames();
            if (state != State.PLAYING) {
                return;
            }
            broadcastSeated(lie ? "&c&l他说谎了！" : "&a&l全是真牌！");
            broadcastSeated("&e" + nameOf(shooter) + " &f必须扣动左轮扳机…");
            giveRevolver(shooter);
            scheduleFireTimeout();
        }, Math.max(20L, finish));
    }

    private List<Location> revealPoints(Seat seat, int count) {
        List<Location> points = new ArrayList<>();
        if (center == null) {
            return points;
        }
        Location flat = new Location(seat.sit.getWorld(), seat.sit.getX(), seat.sit.getY(), seat.sit.getZ());
        Vector dir = new Vector(flat.getX() - center.getX(), 0, flat.getZ() - center.getZ());
        if (dir.lengthSquared() < 0.0001) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        Location base = center.clone().add(dir.clone().multiply(2.2));
        base.setY(seat.sit.getY() + 1.15);
        double[] offsets = count >= 3 ? new double[]{-0.6, 0, 0.6}
                : count == 2 ? new double[]{-0.35, 0.35} : new double[]{0};
        for (double offset : offsets) {
            points.add(base.clone().add(perp.clone().multiply(offset)));
        }
        return points;
    }

    private BlockFace frameFacing(Seat seat) {
        if (center == null) {
            return BlockFace.NORTH;
        }
        Vector dir = new Vector(center.getX() - seat.sit.getX(), 0, center.getZ() - seat.sit.getZ());
        // 展示框的 facing 指向它背靠的方向，所以让它朝玩家
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
            return dir.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return dir.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private void removeFrames() {
        for (ItemFrame frame : frames) {
            if (frame != null && frame.isValid()) {
                frame.remove();
            }
        }
        frames.clear();
    }

    private void giveRevolver(UUID id) {
        Player p = playerOf(id);
        if (p == null) {
            return;
        }
        p.getInventory().setItem(7, manager.items().revolver());
        p.updateInventory();
        manager.refreshDisguise(p);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 0.8f);
        Chat.title(p, "&4&l轮到你开枪", "&7右键手里的铁锄头", 3);
    }

    private void scheduleFireTimeout() {
        if (fireTimeout != null) {
            fireTimeout.cancel();
        }
        fireTimeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            fireTimeout = null;
            if (state == State.PLAYING && pendingShooter != null) {
                Player p = playerOf(pendingShooter);
                if (p != null) {
                    Chat.actionBar(p, "&c超时，自动扣动扳机。");
                }
                fireRevolver(pendingShooter);
            }
        }, settings.fireWaitSeconds * 20L);
    }

    /** 玩家右键左轮（或超时自动）开枪。 */
    public void fireRevolver(UUID id) {
        if (state != State.PLAYING || pendingShooter == null || !pendingShooter.equals(id)) {
            return;
        }
        if (fireTimeout != null) {
            fireTimeout.cancel();
            fireTimeout = null;
        }
        UUID shooter = pendingShooter;
        pendingShooter = null;
        Player p = playerOf(shooter);
        if (p != null) {
            p.getInventory().setItem(7, null);
            p.updateInventory();
        }

        int chamber = Math.floorMod(cylinderStart + pulls, settings.chambers);
        pulls++;
        boolean fires = chamber == bulletAt;

        Seat seat = seatOf(shooter);
        Location dispenser = seat == null ? null : seat.dispenser;
        if (fires) {
            broadcastSeated("&4&l砰！&f" + nameOf(shooter) + " 的枪响了。");
            tableSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
            reloadCylinder();
            startBurn(shooter, seat, dispenser);
        } else {
            broadcastSeated("&a咔哒…&f" + nameOf(shooter) + " 是空枪，活了下来。");
            tableSound(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
            startWater(shooter, seat, dispenser);
        }
    }

    // ---------- 岩浆 / 水 ----------

    private void startWater(UUID id, Seat seat, Location dispenser) {
        Player p = playerOf(id);
        if (p != null) {
            Chat.title(p, "&a咔哒…", "&7空枪，你活了下来", 2);
        }
        startFlow(seat, dispenser, Material.WATER, () -> waterTask = Bukkit.getScheduler()
                .runTaskLater(plugin, () -> {
                    waterTask = null;
                    clearColumn();
                    afterShot(id, false);
                }, settings.waterSeconds * 20L));
    }

    private void startBurn(UUID id, Seat seat, Location dispenser) {
        startFlow(seat, dispenser, Material.LAVA, () -> beginBurnDamage(id));
    }

    private void beginBurnDamage(UUID id) {
        int totalTicks = settings.burnSeconds * 20;
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                clearSides();
                Player p = playerOf(id);
                if (state != State.PLAYING || p == null) {
                    cancel();
                    burnTasks.remove(id);
                    afterShot(id, true);
                    return;
                }
                if (elapsed >= totalTicks || p.getHealth() <= 3.0) {
                    cancel();
                    burnTasks.remove(id);
                    eliminateByFire(id);
                    afterShot(id, true);
                    return;
                }
                p.setFireTicks(60);
                // 走一次真实伤害事件，其他玩家能看到受击变红；伤害量压住，保证不会真的死。
                double damage = Math.min(settings.burnDamage,
                        Math.max(1.0, p.getHealth() - 1.5));
                p.setNoDamageTicks(0);
                manager.allowNextDamage(id);
                try {
                    p.damage(damage);
                } finally {
                    manager.clearDamageAllowed(id);
                }
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0),
                        12, 0.35, 0.5, 0.35, 0.02);
                elapsed += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        burnTasks.put(id, holder[0]);
    }

    /**
     * 从发射口一格一格往下流到玩家头顶（每格间隔 {@code flow-ticks-per-block}），
     * 到位后回调；全程清掉水平邻居，不会横向蔓延。
     */
    private void startFlow(Seat seat, Location dispenser, Material material, Runnable onArrive) {
        clearColumn();
        if (flowTask != null) {
            flowTask.cancel();
            flowTask = null;
        }
        if (seat == null) {
            onArrive.run();
            return;
        }
        World world = seat.chair.getWorld();
        if (world == null) {
            onArrive.run();
            return;
        }
        int bottomY = seat.chair.getBlockY() + 1;
        int topY = dispenser != null ? dispenser.getBlockY() - 1 : seat.chair.getBlockY() + 3;
        if (topY < bottomY) {
            topY = bottomY;
        }
        final int floorY = bottomY;
        final int[] y = {topY};
        startFluidGuard();
        if (dispenser != null) {
            world.playSound(dispenser, Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
        }
        flowTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.PLAYING) {
                    cancel();
                    flowTask = null;
                    clearColumn();
                    return;
                }
                Block block = world.getBlockAt(seat.chair.getBlockX(), y[0], seat.chair.getBlockZ());
                if (block.getType().isAir() || block.getType() == Material.WATER
                        || block.getType() == Material.LAVA) {
                    block.setType(material, false);
                    fluidBlocks.add(block);
                }
                clearSides();
                if (y[0] <= floorY) {
                    cancel();
                    flowTask = null;
                    onArrive.run();
                    return;
                }
                y[0]--;
            }
        }.runTaskTimer(plugin, settings.flowTicksPerBlock, settings.flowTicksPerBlock);
    }

    private void startFluidGuard() {
        if (fluidGuardTask != null) {
            return;
        }
        fluidGuardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::clearSides, 1L, 2L);
    }

    private void stopFluidGuard() {
        if (fluidGuardTask != null) {
            fluidGuardTask.cancel();
            fluidGuardTask = null;
        }
    }

    /** 清掉水/岩浆的水平邻居，保证只往下流。 */
    private void clearSides() {
        for (Block block : fluidBlocks) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH,
                    BlockFace.EAST, BlockFace.WEST}) {
                Block side = block.getRelative(face);
                if ((side.getType() == Material.LAVA || side.getType() == Material.WATER)
                        && !fluidBlocks.contains(side)) {
                    side.setType(Material.AIR, false);
                }
            }
        }
    }

    private void clearColumn() {
        stopFluidGuard();
        for (Block block : fluidBlocks) {
            if (block.getType() == Material.LAVA || block.getType() == Material.WATER) {
                block.setType(Material.AIR, false);
            }
        }
        fluidBlocks.clear();
    }

    private void eliminateByFire(UUID id) {
        if (!order.remove(id)) {
            return;
        }
        Seat seat = seatOf(id);
        Player p = playerOf(id);
        manager.clearDamageAllowed(id);
        if (p != null) {
            Chat.title(p, "&c&l你被岩浆吞没了", "&7留在座位上观战", 3);
            p.setFireTicks(0);
            p.setHealth(p.getMaxHealth());
            if (seat != null) {
                if (seat.stand != null) {
                    manager.markForceDismount(id);
                    try {
                        if (p.getVehicle() == seat.stand) {
                            p.leaveVehicle();
                        }
                    } finally {
                        manager.clearForceDismount(id);
                    }
                    seat.stand.remove();
                    seat.stand = null;
                }
                spawnCorpse(seat, p);
                p.teleport(seat.sit.clone().add(0, 1.4, 0));
            }
            p.setGameMode(GameMode.SPECTATOR);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    p.getLocation(), 60, 0.5, 0.8, 0.5, 0.3);
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    p.getLocation(), 40, 0.4, 0.6, 0.4, 0.02);
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
        }
        if (order.size() <= 1) {
            endGame(order.isEmpty() ? null : order.get(0));
        } else {
            manager.reportArena(this);
        }
    }

    /** 死亡尸体：和密室杀手一样用 Mannequin（完整玩家模型 + 死者皮肤）躺在地上。 */
    private void spawnCorpse(Seat seat, Player player) {
        World world = seat.sit.getWorld();
        if (world == null) {
            return;
        }
        Location spawnAt = seat.sit.clone().add(0, 0.55, 0);
        Mannequin corpse = world.spawn(spawnAt, Mannequin.class, mannequin -> {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            mannequin.setPose(Pose.SLEEPING);
            mannequin.setImmovable(true);
            mannequin.setGravity(false);
            mannequin.setInvulnerable(true);
            mannequin.setSilent(true);
            mannequin.setCollidable(false);
            mannequin.setPersistent(false);
            mannequin.setDescription(Component.empty());
            mannequin.setCustomNameVisible(false);
        });
        seat.corpse = corpse;
    }

    /** 一次开枪流程结束：安排下一回合（或本局结束）。 */
    private void afterShot(UUID shooter, boolean died) {
        if (state != State.PLAYING) {
            return;
        }
        UUID starter;
        if (!died) {
            starter = shooter;
        } else if (order.isEmpty()) {
            starter = null;
        } else {
            starter = order.get(pendingShooterIndex % order.size());
        }
        lastPlayOwner = null;
        lastPlayCards = null;
        pileCount = 0;
        final UUID next = starter;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == State.PLAYING) {
                startRound(next);
            }
        }, settings.roundDelaySeconds * 20L);
    }

    private void advanceTurn() {
        if (order.isEmpty()) {
            return;
        }
        turnIndex = (turnIndex + 1) % order.size();
        secondsLeft = settings.turnSeconds;
        updateActionBars();
    }

    private UUID currentPlayer() {
        if (order.isEmpty()) {
            return null;
        }
        return order.get(turnIndex % order.size());
    }

    private boolean isTurn(Player player) {
        return state == State.PLAYING && !transition
                && player.getUniqueId().equals(currentPlayer());
    }

    private void updateActionBars() {
        if (state != State.PLAYING) {
            return;
        }
        UUID current = currentPlayer();
        String currentName = current == null ? "—" : nameOf(current);
        String bar = "&e目标：&f" + LiarBarCards.NAMES[tableRank]
                + " &7| &e已出：&f" + pileCount
                + " &7| &e轮到：&f" + currentName
                + " &7| &f剩余 &e" + Math.max(0, secondsLeft) + "s";
        for (Seat seat : seats) {
            Player p = playerOf(seat.occupant);
            if (p == null) {
                continue;
            }
            if (!order.contains(p.getUniqueId())) {
                Chat.actionBar(p, "&7你已出局，观战中（剩 " + order.size() + " 人）");
            } else if (transition) {
                Chat.actionBar(p, "&e结算中…");
            } else if (p.getUniqueId().equals(current)) {
                Chat.actionBar(p, bar + " &a&l← 你的回合");
            } else {
                Chat.actionBar(p, bar);
            }
        }
    }

    // ---------- 结算 ----------

    private void endGame(UUID winner) {
        if (state == State.ENDING) {
            return;
        }
        state = State.ENDING;
        transition = true;
        cancelTick();
        cancelPendingTasks();
        if (winner != null) {
            broadcastSeated("&6&l" + nameOf(winner) + " &f赢得了本局！");
            Chat.broadcast("&4[骗子酒馆] &e" + nameOf(winner) + " &f在「" + name + "」赢得了本局！");
            manager.recordWin(winner);
            Player p = playerOf(winner);
            if (p != null) {
                Chat.title(p, "&6&l胜利", "&f你是最后的幸存者", 4);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        } else {
            broadcastSeated("&7本局中止，没有产生胜者。");
        }
        manager.reportArena(this);
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::finishRound,
                settings.endDelaySeconds * 20L);
    }

    private void cancelPendingTasks() {
        removeFrames();
        if (fireTimeout != null) {
            fireTimeout.cancel();
            fireTimeout = null;
        }
        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }
        if (waterTask != null) {
            waterTask.cancel();
            waterTask = null;
        }
        if (flowTask != null) {
            flowTask.cancel();
            flowTask = null;
        }
        for (BukkitTask task : burnTasks.values()) {
            task.cancel();
        }
        burnTasks.clear();
        pendingShooter = null;
        clearColumn();
    }

    /** 本局结束：全员从座椅上下来，留在房间里等下一局。 */
    private void finishRound() {
        endTask = null;
        if (state != State.ENDING) {
            return;
        }
        state = State.WAITING;
        for (Seat seat : new ArrayList<>(seats)) {
            if (seat.occupant == null) {
                continue;
            }
            UUID id = seat.occupant;
            Player p = playerOf(id);
            Location where = seat.sit.clone();
            clearSeat(seat, p);
            if (p != null) {
                manager.restoreSnapshot(p);
                p.setGameMode(GameMode.ADVENTURE);
                p.setFireTicks(0);
                p.teleport(where);
                p.updateInventory();
                HubAPI.setGame(id, LiarBarManager.GAME);
                Chat.title(p, "&e本局结束", "&7从椅子上下来，想再来一局就重新坐下", 3);
            }
        }
        clearGameState();
        manager.reportArena(this);
    }

    private void clearGameState() {
        order.clear();
        selections.clear();
        names.clear();
        lastPlayOwner = null;
        lastPlayCards = null;
        pileCount = 0;
        transition = false;
        round = 0;
        turnIndex = 0;
        secondsLeft = 0;
    }

    /** 管理员 /liarbar stop，或插件关闭。 */
    public void forceStop() {
        countdown.cancel();
        cancelTick();
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        cancelPendingTasks();
        state = State.WAITING;
        for (Seat seat : new ArrayList<>(seats)) {
            if (seat.occupant == null) {
                continue;
            }
            UUID id = seat.occupant;
            Player p = playerOf(id);
            clearSeat(seat, p);
            if (p != null) {
                manager.restoreSnapshot(p);
                p.setGameMode(GameMode.ADVENTURE);
                p.setFireTicks(0);
                p.updateInventory();
                HubAPI.setGame(id, LiarBarManager.GAME);
            }
        }
        clearGameState();
        manager.reportArena(this);
    }

    /** 玩家离开座位/下线：清座位；对局中视为放弃。 */
    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        Seat seat = seatOf(player);
        if (seat != null) {
            clearSeat(seat, player);
        }
        selections.remove(player.getUniqueId());
        names.remove(player.getUniqueId());
        if (player.getUniqueId().equals(pendingShooter)) {
            fireRevolver(pendingShooter);
        }
    }

    public void shutdown() {
        forceStop();
    }

    public void refreshArena() {
        manager.reportArena(this);
    }

    private Player playerOf(UUID id) {
        return id == null ? null : Bukkit.getPlayer(id);
    }

    private String nameOf(UUID id) {
        if (id == null) {
            return "？";
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String stored = names.get(id);
        return stored == null ? "？" : stored;
    }

    private void broadcastSeated(String message) {
        for (Seat seat : seats) {
            Player p = playerOf(seat.occupant);
            if (p != null) {
                Chat.send(p, message);
            }
        }
    }

    private void tableSound(Sound sound, float volume, float pitch) {
        for (Seat seat : seats) {
            Player p = playerOf(seat.occupant);
            if (p != null) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }

    public GameState hubState() {
        return state == State.PLAYING || state == State.ENDING ? GameState.PLAYING : GameState.WAITING;
    }

    public String hubExtra() {
        if (state == State.PLAYING) {
            return "第 " + round + " 回合";
        }
        if (countdown.isRunning()) {
            return "即将开局";
        }
        if (state == State.ENDING) {
            return "结算中";
        }
        return null;
    }
}
