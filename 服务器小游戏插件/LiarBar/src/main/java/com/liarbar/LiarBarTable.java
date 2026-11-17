package com.liarbar;

import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
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

    /** 槽位约定：0~4 手牌，6 桌面牌，8 质疑钟/离开酒馆。 */
    public static final int TABLE_SLOT = 6;
    public static final int BELL_SLOT = 8;

    private static final class Seat {
        final Location chair;
        final Location sit;
        UUID occupant;
        ArmorStand stand;

        Seat(Location chair, Location sit) {
            this.chair = chair;
            this.sit = sit;
        }
    }

    private static final class Revolver {
        final int bullet;
        int pulls;

        Revolver(int chambers) {
            this.bullet = ThreadLocalRandom.current().nextInt(Math.max(1, chambers));
        }
    }

    private final Plugin plugin;
    private final LiarBarManager manager;
    private final LiarBarSettings settings;
    private final String name;
    private final List<Seat> seats = new ArrayList<>();
    private Location center;

    private State state = State.WAITING;
    private BukkitTask countdownTask;
    private BukkitTask tickTask;
    private BukkitTask endTask;

    /** 存活玩家（按座位顺序）。 */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Revolver> guns = new HashMap<>();
    private final Map<UUID, Set<Integer>> selections = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    private int round;
    private int tableRank;
    private int turnIndex;
    private int secondsLeft;
    private boolean transition;
    private int pileCount;
    private UUID lastPlayOwner;
    private List<LiarBarCards.Card> lastPlayCards;

    public LiarBarTable(Plugin plugin, LiarBarManager manager, LiarBarSettings settings,
                        String name, List<Location> chairs) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.name = name;
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
        return seats.size() >= settings.playersPerTable && occupiedCount() >= settings.playersPerTable;
    }

    public boolean hasSeatFor(Player player) {
        return seatOf(player) != null;
    }

    public boolean hasChair(org.bukkit.block.Block block) {
        for (Seat seat : seats) {
            if (seat.chair.getWorld() != null
                    && seat.chair.getWorld().equals(block.getWorld())
                    && seat.chair.getBlockX() == block.getX()
                    && seat.chair.getBlockY() == block.getY()
                    && seat.chair.getBlockZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    private Seat seatOf(Player player) {
        for (Seat seat : seats) {
            if (player.getUniqueId().equals(seat.occupant)) {
                return seat;
            }
        }
        return null;
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
        manager.bind(player, this);
        player.teleport(free.sit);
        ArmorStand stand = spawnSeatStand(free);
        free.stand = stand;
        if (stand != null) {
            stand.addPassenger(player);
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        Chat.actionBar(player, "&a已入座并准备（" + occupiedCount() + "/" + settings.playersPerTable + "）");
        tableSound(Sound.UI_BUTTON_CLICK, 0.9f, 1.4f);
        afterSeatChange();
        if (isFull() && state == State.WAITING) {
            beginCountdown();
        }
        return true;
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

    /** 对局中离座 = 放弃本局（座位腾出来，其他人可以继续观战/结束）。 */
    private void eliminateQuitter(UUID id) {
        if (!order.remove(id)) {
            return;
        }
        guns.remove(id);
        broadcastSeated("&7" + nameOf(id) + " 离开了牌桌，视为出局。");
        if (order.size() <= 1) {
            endGame(order.isEmpty() ? null : order.get(0));
        } else {
            manager.reportArena(this);
        }
    }

    private void afterSeatChange() {
        manager.reportArena(this);
        if (state == State.STARTING && !isFull()) {
            cancelCountdown();
        }
    }

    /** 对局中是否禁止起身（潜行下坐骑会被拦下来）。 */
    public boolean blocksStanding() {
        return state == State.PLAYING || state == State.ENDING;
    }

    // ---------- 倒计时与开局 ----------

    private void beginCountdown() {
        if (countdownTask != null) {
            return;
        }
        state = State.STARTING;
        manager.reportArena(this);
        countdownTask = new BukkitRunnable() {
            int left = settings.startCountdownSeconds;

            @Override
            public void run() {
                if (state != State.STARTING || !isFull()) {
                    cancelCountdown();
                    return;
                }
                if (left <= 0) {
                    cancel();
                    countdownTask = null;
                    startGame();
                    return;
                }
                for (Seat seat : seats) {
                    Player p = playerOf(seat.occupant);
                    if (p != null) {
                        Chat.title(p, "&e" + left, "&7四人对局即将开始", 1);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                    }
                }
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (state == State.STARTING) {
            state = State.WAITING;
            manager.reportArena(this);
        }
    }

    /** 管理员 /liarbar start 强制开局。 */
    public void forceStart() {
        if (state != State.WAITING) {
            return;
        }
        cancelCountdown();
        startGame();
    }

    private void startGame() {
        if (occupiedCount() < settings.playersPerTable) {
            return;
        }
        state = State.PLAYING;
        transition = false;
        round = 0;
        order.clear();
        guns.clear();
        selections.clear();
        names.clear();
        for (Seat seat : seats) {
            if (seat.occupant == null) {
                continue;
            }
            UUID id = seat.occupant;
            order.add(id);
            names.put(id, nameOf(id));
            guns.put(id, new Revolver(settings.chambers));
            Player p = playerOf(id);
            if (p != null) {
                HubAPI.setPlaying(id, LiarBarManager.GAME);
                p.setGameMode(GameMode.ADVENTURE);
                p.setFoodLevel(20);
                Chat.title(p, "&6&l骗子酒馆", "&f坐稳了，别被唬住", 3);
                p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.7f);
            }
        }
        manager.reportArena(this);
        startRound(null);
    }

    // ---------- 回合 ----------

    private void startRound(UUID starter) {
        if (state != State.PLAYING) {
            return;
        }
        transition = false;
        round++;
        tableRank = ThreadLocalRandom.current().nextInt(LiarBarCards.RANKS.length);
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
                Chat.title(p, "&e第 " + round + " 回合", "&7桌面点数：&e" + LiarBarCards.RANKS[tableRank]
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
                    manager.items().tableCard(LiarBarCards.RANKS[tableRank]));
            p.getInventory().setItem(BELL_SLOT, manager.items().bell());
            p.updateInventory();
            selections.computeIfAbsent(id, k -> new HashSet<>()).clear();
        }
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
        lastPlayOwner = player.getUniqueId();
        lastPlayCards = cards;
        pileCount += cards.size();
        broadcastSeated("&e" + player.getName() + " &f打出 &e" + cards.size()
                + " &f张牌，声称是 &e" + LiarBarCards.RANKS[tableRank]);
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
        List<LiarBarCards.Card> cards = lastPlayCards == null ? List.of() : lastPlayCards;

        StringBuilder reveal = new StringBuilder();
        boolean lie = false;
        for (LiarBarCards.Card card : cards) {
            if (reveal.length() > 0) {
                reveal.append("&7、");
            }
            reveal.append(card.colored());
            if (card.rank() != tableRank) {
                lie = true;
            }
        }
        transition = true;
        broadcastSeated("&c" + nameOf(challenger) + " &f质疑 &e" + nameOf(accused) + "&f！翻开：&f" + reveal);
        broadcastSeated(lie ? "&c&l他说谎了！" : "&a&l全是真牌！");
        tableSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        for (Seat seat : seats) {
            Player p = playerOf(seat.occupant);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.7f);
            }
        }

        UUID shooter = lie ? accused : challenger;
        int shooterIndex = order.indexOf(shooter);
        boolean died = pullTrigger(shooter);

        lastPlayOwner = null;
        lastPlayCards = null;
        pileCount = 0;
        if (state != State.PLAYING) {
            return;
        }
        UUID starter;
        if (!died) {
            starter = shooter;
        } else if (order.isEmpty()) {
            starter = null;
        } else {
            int index = shooterIndex < 0 ? 0 : shooterIndex;
            starter = order.get(index % order.size());
        }
        final UUID next = starter;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    if (state == State.PLAYING) {
                        startRound(next);
                    }
                }, settings.roundDelaySeconds * 20L);
        updateActionBars();
    }

    /** 扣一次扳机，返回是否中弹出局。 */
    private boolean pullTrigger(UUID shooter) {
        Revolver gun = guns.computeIfAbsent(shooter, k -> new Revolver(settings.chambers));
        int chamber = gun.pulls % settings.chambers;
        gun.pulls++;
        boolean fires = chamber == gun.bullet;
        Player p = playerOf(shooter);
        if (fires) {
            broadcastSeated("&4&l砰！&f" + nameOf(shooter) + " 中枪倒下了。");
            for (Seat seat : seats) {
                Player viewer = playerOf(seat.occupant);
                if (viewer != null) {
                    Location loc = viewer.getLocation().add(0, 1, 0);
                    viewer.getWorld().spawnParticle(Particle.CRIT, loc, 40, 0.4, 0.4, 0.4, 0.15);
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
                }
            }
            if (p != null) {
                Chat.title(p, "&c&l你中枪了", "&7留在座位观战到本局结束", 3);
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
            }
            eliminate(shooter);
            return true;
        }
        int untilBullet = Math.floorMod(gun.bullet - (gun.pulls % settings.chambers), settings.chambers);
        broadcastSeated("&a咔哒…&f" + nameOf(shooter) + " 空枪，活了下来 &7（弹巢还剩 "
                + untilBullet + " 格才到子弹）");
        if (p != null) {
            Chat.title(p, "&a咔哒…", "&7你活了下来", 2);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        }
        return false;
    }

    private void eliminate(UUID id) {
        if (!order.remove(id)) {
            return;
        }
        Player p = playerOf(id);
        if (p != null) {
            p.getInventory().clear();
            p.updateInventory();
        }
        guns.remove(id);
        selections.remove(id);
        if (order.size() <= 1) {
            endGame(order.isEmpty() ? null : order.get(0));
        } else {
            manager.reportArena(this);
        }
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
        String bar = "&e桌面：&f" + LiarBarCards.RANKS[tableRank]
                + " &7| &e已出：&f" + claimedCount()
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
                Chat.actionBar(p, "&e正在结算本回合…");
            } else if (p.getUniqueId().equals(current)) {
                Chat.actionBar(p, bar + " &a&l← 你的回合");
            } else {
                Chat.actionBar(p, bar);
            }
        }
    }

    private int claimedCount() {
        return pileCount;
    }

    // ---------- 结算 ----------

    private void endGame(UUID winner) {
        if (state == State.ENDING) {
            return;
        }
        state = State.ENDING;
        transition = true;
        cancelTick();
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
            Player p = playerOf(seat.occupant);
            if (p != null) {
                clearSeat(seat, p);
                p.getInventory().clear();
                manager.giveLeaveItem(p);
                p.setGameMode(GameMode.ADVENTURE);
                p.updateInventory();
                HubAPI.setGame(p.getUniqueId(), LiarBarManager.GAME);
                Chat.title(p, "&e本局结束", "&7从椅子上下来，想再来一局就重新坐下", 3);
            } else {
                clearSeat(seat, null);
            }
        }
        clearGameState();
        manager.reportArena(this);
    }

    private void clearGameState() {
        order.clear();
        guns.clear();
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
        cancelCountdown();
        cancelTick();
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        state = State.WAITING;
        for (Seat seat : new ArrayList<>(seats)) {
            if (seat.occupant == null) {
                continue;
            }
            Player p = playerOf(seat.occupant);
            clearSeat(seat, p);
            if (p != null) {
                p.getInventory().clear();
                manager.giveLeaveItem(p);
                p.updateInventory();
                HubAPI.setGame(p.getUniqueId(), LiarBarManager.GAME);
            }
        }
        clearGameState();
        manager.reportArena(this);
    }

    /** 玩家离开房间/下线：清座位；对局中视为放弃。 */
    public void removePlayer(Player player) {
        Seat seat = seatOf(player);
        if (seat != null) {
            clearSeat(seat, player);
        }
        selections.remove(player.getUniqueId());
        names.remove(player.getUniqueId());
    }

    public void shutdown() {
        forceStop();
    }

    // ---------- 辅助 ----------

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

    /** 供 HubCore 中央菜单显示的场地状态。 */
    public GameState hubState() {
        return state == State.PLAYING || state == State.ENDING ? GameState.PLAYING : GameState.WAITING;
    }

    public String hubExtra() {
        if (state == State.PLAYING) {
            return "第 " + round + " 回合";
        }
        if (state == State.STARTING) {
            return "即将开局";
        }
        if (state == State.ENDING) {
            return "结算中";
        }
        return null;
    }
}
