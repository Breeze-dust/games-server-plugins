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
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一张桌子 = 一个独立场次：4 个台阶座椅、自己的倒计时、自己的牌局。
 * 多张桌子可以同时开局，互不影响。
 */
public final class LiarBarTable {

    public enum State {WAITING, STARTING, PLAYING, ENDING}

    /** 槽位约定：0 出牌道具，1 质疑道具，2 准备道具，4~8 手牌。 */
    public static final int PLAY_SLOT = 0;
    public static final int CHALLENGE_SLOT = 1;
    public static final int READY_SLOT = 2;
    public static final int HAND_FIRST_SLOT = 4;
    public static final int HAND_LAST_SLOT = 8;
    public static final int HAND_SLOTS = HAND_LAST_SLOT - HAND_FIRST_SLOT + 1;

    /** 名字标签计数 team 的名字序号，保证每次挂载都有唯一 team。 */
    private static final AtomicInteger COUNTER_TEAM_SEQ = new AtomicInteger();

    /** 出牌菜单：0~4 对应手牌槽 4~8，6 目标牌，8 确认出牌。 */
    private static final int MENU_SIZE = 9;
    private static final int MENU_TARGET_SLOT = 6;
    private static final int MENU_CONFIRM_SLOT = 8;

    private static final class Seat {
        final Location chair;
        final Location sit;
        UUID occupant;
        ArmorStand stand;
        /** 被岩浆吞没后摆在桌面中间的头颅方块；null 表示没有。 */
        Location headBlock;
        Location dispenser;
        Location button;
        /** 这个座位自己的左轮：只有坐在这里的人扣扳机才会转。 */
        int gunBulletAt;
        int gunPulls;
        /** 对局中挂名字标签后缀用的 team；null 表示当前没挂。 */
        String counterTeam;
        String counterEntry;
        /** 玩家原来的 team（如 PlayerTitles 的称号队）及其前缀，离局时还原。 */
        String prevTeam;
        Component prevPrefix;

        Seat(Location chair, Location sit) {
            this.chair = chair;
            this.sit = sit;
        }
    }

    /** 出牌菜单：0~4 对应快捷栏 4~8 的手牌，6 目标牌，8 确认出牌。 */
    public static final class PlayMenu implements InventoryHolder {

        private final LiarBarTable table;
        private final UUID owner;
        private final Inventory inventory;

        PlayMenu(LiarBarTable table, UUID owner) {
            this.table = table;
            this.owner = owner;
            this.inventory = Bukkit.createInventory(this, MENU_SIZE,
                    Chat.noItalic(Chat.color("&8出牌 &7| &e目标："
                            + LiarBarCards.NAMES[table.tableRank])));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        /** 按当前手牌与已选状态重画菜单。 */
        public void render() {
            Player p = Bukkit.getPlayer(owner);
            if (p == null) {
                return;
            }
            ItemStack filler = table.manager.items().filler();
            for (int i = 0; i < MENU_SIZE; i++) {
                inventory.setItem(i, filler);
            }
            Set<Integer> selected = table.selections.getOrDefault(owner, Set.of());
            List<LiarBarCards.Card> chosen = new ArrayList<>();
            for (int i = 0; i < HAND_SLOTS; i++) {
                int slot = HAND_FIRST_SLOT + i;
                LiarBarCards.Card card = table.manager.items()
                        .cardOf(p.getInventory().getItem(slot));
                if (card == null) {
                    continue;
                }
                boolean on = selected.contains(slot);
                inventory.setItem(i, table.manager.items().card(card, on));
                if (on) {
                    chosen.add(card);
                }
            }
            inventory.setItem(MENU_TARGET_SLOT, table.manager.items().tableCard(table.tableRank));
            inventory.setItem(MENU_CONFIRM_SLOT,
                    table.manager.items().confirm(chosen, table.settings.maxPlayCards));
        }

        /** 菜单点击：0~4 切换选中，8 确认出牌。 */
        public void click(int slot) {
            Player p = Bukkit.getPlayer(owner);
            if (p == null) {
                return;
            }
            if (slot >= 0 && slot < HAND_SLOTS) {
                table.toggleSelect(p, HAND_FIRST_SLOT + slot);
                render();
                return;
            }
            if (slot == MENU_CONFIRM_SLOT) {
                table.confirmPlay(p);
            }
        }

        /** 菜单关闭时从桌子的记录里移除。 */
        public void onClose() {
            table.openMenus.remove(owner, this);
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
    /** 打开的出牌菜单（每名玩家最多一个）。 */
    private final Map<UUID, PlayMenu> openMenus = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    /** 入座时槽位 2 原来的物品（发准备道具前备份）。 */
    private final Map<UUID, ItemStack> readyBackup = new HashMap<>();

    private int round;
    private int tableRank;
    private int turnIndex;
    private int secondsLeft;
    private boolean transition;
    /** 上家刚打光手牌：本轮下家只能质疑，不能出牌。 */
    private boolean mustChallenge;
    private int pileCount;
    private UUID lastPlayOwner;
    private List<LiarBarCards.Card> lastPlayCards;
    /** 当前被高亮的出牌人；切换回合时先清掉他的高亮。 */
    private UUID highlightedPlayer;

    // 揭幕 / 开枪
    private final List<ItemFrame> frames = new ArrayList<>();
    /** 本次出牌真正放了纸的展示框，按出牌顺序；某个框没放成时为 null。 */
    private final List<ItemFrame> cardFrames = new ArrayList<>();
    private UUID pendingShooter;
    private int pendingShooterIndex;
    private BukkitTask fireTimeout;
    private BukkitTask revealTask;
    private BukkitTask waterTask;
    private BukkitTask flowTask;
    private BukkitTask fluidGuardTask;
    private final Map<UUID, BukkitTask> burnTasks = new HashMap<>();
    private final List<Block> fluidBlocks = new ArrayList<>();
    /** 流体从按钮位置流出时，暂存按钮方块，结算结束后恢复。 */
    private Block fluidButtonBlock;
    private org.bukkit.block.data.BlockData fluidButtonData;
    /** 正在放水/岩浆的柱体范围：用来拦下原版流体的自行扩散，保证一格一格往下走。 */
    private World fluidColumnWorld;
    private int fluidColumnX;
    private int fluidColumnZ;
    private int fluidColumnMinY;
    private int fluidColumnMaxY;
    /** 桌子中间显示本轮目标牌的旋转掉落物（展示实体，不可拾取）。 */
    private ItemDisplay targetDisplay;
    private TextDisplay targetText;
    private BukkitTask targetSpinTask;
    private float targetSpinAngle;

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
        removeCounters();
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

    /** 桌子中心（椅子坐标平均值）；还没登记椅子时为 null。 */
    public Location getCenter() {
        return center == null ? null : center.clone();
    }

    /** 主城观战者进这张桌子时落在哪：桌面中心上方 2 格。 */
    public Location spectatorPoint() {
        return center == null ? null : center.clone().add(0, 2.0, 0);
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
        return seatAt(block) != null;
    }

    private Seat seatAt(Block block) {
        if (block == null) {
            return null;
        }
        for (Seat seat : seats) {
            if (sameBlock(seat.chair, block)) {
                return seat;
            }
        }
        return null;
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
    /** 不指定椅子时坐到第一把空位（/liarbar join 用）。 */
    public boolean sit(Player player) {
        return sit(player, null);
    }

    /** 右键哪把椅子就坐哪把；chairBlock 为 null 时找第一把空位。 */
    public boolean sit(Player player, Block chairBlock) {
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
        Seat free;
        if (chairBlock != null) {
            free = seatAt(chairBlock);
            if (free == null) {
                Chat.actionBar(player, "&c这不是这张桌子的椅子。");
                return false;
            }
            if (free.occupant != null) {
                Chat.actionBar(player, "&c这个座位已经有人了，换一把椅子坐下。");
                return false;
            }
        } else {
            free = null;
            for (Seat seat : seats) {
                if (seat.occupant == null) {
                    free = seat;
                    break;
                }
            }
        }
        if (free == null) {
            Chat.actionBar(player, "&c这张桌子还没配好椅子，等管理员登记。");
            return false;
        }
        free.occupant = player.getUniqueId();
        names.put(player.getUniqueId(), player.getName());
        free.dispenser = findDispenser(free);
        free.button = findButton(free.dispenser);
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

    /** 发射器六个面上有没有贴按钮（绑定座位时自动检测）。 */
    private Location findButton(Location dispenser) {
        if (dispenser == null || dispenser.getWorld() == null) {
            return null;
        }
        Block block = dispenser.getBlock();
        for (BlockFace face : BlockFace.values()) {
            if (!face.isCartesian() || face == BlockFace.SELF) {
                continue;
            }
            Block around = block.getRelative(face);
            if (Tag.BUTTONS.isTagged(around.getType())) {
                return around.getLocation();
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
        removeCounter(seat);
        seat.occupant = null;
        seat.dispenser = null;
        seat.button = null;
        if (seat.headBlock != null) {
            Block head = seat.headBlock.getBlock();
            if (head.getType() == Material.PLAYER_HEAD) {
                head.setType(Material.AIR, false);
            }
            seat.headBlock = null;
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
        if (id.equals(lastPlayOwner)) {
            mustChallenge = false;
            lastPlayOwner = null;
            lastPlayCards = null;
        }
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
        loadSeatGuns();
        manager.reportArena(this);
        startRound(null);
    }

    /** 每局开始：每个有人的座位重新装一把枪，并把 0/N 挂到玩家名字标签后缀上。 */
    private void loadSeatGuns() {
        for (Seat seat : seats) {
            removeCounter(seat);
            if (seat.occupant == null) {
                continue;
            }
            seat.gunBulletAt = ThreadLocalRandom.current().nextInt(settings.chambers);
            seat.gunPulls = 0;
            showCounter(seat);
        }
    }

    /**
     * 把本座位的计数挂到玩家名字标签后面（team 后缀）。玩家原来若在别的 team
     * （例如 PlayerTitles 的称号队），先记住并复制它的前缀，离局时再还原。
     */
    private void showCounter(Seat seat) {
        removeCounter(seat);
        Player player = seat.occupant == null ? null : playerOf(seat.occupant);
        if (player == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = player.getName();
        Team old = board.getEntryTeam(entry);
        seat.prevTeam = old == null ? null : old.getName();
        seat.prevPrefix = old == null ? null : old.prefix();
        String teamName = nextCounterTeamName(board);
        Team team;
        try {
            team = board.registerNewTeam(teamName);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (seat.prevPrefix != null) {
            team.prefix(seat.prevPrefix);
        }
        team.suffix(Chat.color(counterText(seat)));
        team.addEntry(entry);
        seat.counterTeam = teamName;
        seat.counterEntry = entry;
    }

    /** 扣一次扳机后刷新名字标签后缀。 */
    private void updateCounter(Seat seat) {
        if (seat == null || seat.counterTeam == null) {
            return;
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(seat.counterTeam);
        if (team != null) {
            team.suffix(Chat.color(counterText(seat)));
        }
    }

    /** 移除计数后缀，并把玩家放回原来的 team（通常是称号队）。 */
    private void removeCounter(Seat seat) {
        if (seat == null || seat.counterTeam == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(seat.counterTeam);
        if (team != null) {
            if (seat.counterEntry != null) {
                team.removeEntry(seat.counterEntry);
            }
            team.unregister();
        }
        if (seat.counterEntry != null && seat.prevTeam != null) {
            Team old = board.getTeam(seat.prevTeam);
            if (old == null && seat.prevPrefix != null) {
                try {
                    old = board.registerNewTeam(seat.prevTeam);
                    old.prefix(seat.prevPrefix);
                } catch (IllegalArgumentException e) {
                    // 同名 team 刚好被别的插件重建，直接用现成的
                    old = board.getTeam(seat.prevTeam);
                }
            }
            if (old != null) {
                old.addEntry(seat.counterEntry);
            }
        }
        seat.counterTeam = null;
        seat.counterEntry = null;
        seat.prevTeam = null;
        seat.prevPrefix = null;
    }

    private void removeCounters() {
        for (Seat seat : seats) {
            removeCounter(seat);
        }
    }

    private static String nextCounterTeamName(Scoreboard board) {
        for (int i = 0; i < 100; i++) {
            String name = "lb" + Integer.toHexString(COUNTER_TEAM_SEQ.incrementAndGet());
            if (board.getTeam(name) == null) {
                return name;
            }
        }
        return "lb" + Integer.toHexString(COUNTER_TEAM_SEQ.incrementAndGet());
    }

    /** 清掉上次异常退出遗留的计数 team（只在没有牌局时调用）。 */
    static void clearCounterTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith("lb")) {
                team.unregister();
            }
        }
    }

    private String counterText(Seat seat) {
        return " &7(&f" + seat.gunPulls + "&7/&f" + settings.chambers + "&7)";
    }

    // ---------- 回合 ----------

    private void startRound(UUID starter) {
        if (state != State.PLAYING) {
            return;
        }
        removeFrames();
        clearColumn();
        closeAllMenus();
        transition = false;
        mustChallenge = false;
        round++;
        tableRank = ThreadLocalRandom.current().nextInt(LiarBarCards.NORMAL_RANKS);
        lastPlayOwner = null;
        lastPlayCards = null;
        pileCount = 0;
        dealRound();
        spawnTargetDisplay();
        if (starter != null && order.contains(starter)) {
            turnIndex = order.indexOf(starter);
        } else {
            turnIndex = ThreadLocalRandom.current().nextInt(Math.max(1, order.size()));
        }
        secondsLeft = settings.turnSeconds;
        UUID turnOwner = currentPlayer();
        for (UUID id : order) {
            if (id.equals(turnOwner)) {
                continue; // 出牌人单独弹「轮到你出牌！」
            }
            Player p = playerOf(id);
            if (p != null) {
                Chat.title(p, "&e第 " + round + " 回合", "&7目标牌：&f" + LiarBarCards.NAMES[tableRank]
                        + " &7（打出的牌都要声称是它）", 2);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
        }
        announceTurn(turnOwner);
        ensureTickTask();
        updateActionBars();
        manager.reportArena(this);
    }

    private void dealRound() {
        closeAllMenus();
        List<LiarBarCards.Card> deck = LiarBarCards.shuffledDeck();
        int index = 0;
        for (UUID id : order) {
            Player p = playerOf(id);
            if (p == null) {
                continue;
            }
            p.getInventory().clear();
            p.getInventory().setItem(PLAY_SLOT, manager.items().playItem(tableRank, 0));
            p.getInventory().setItem(CHALLENGE_SLOT, manager.items().challenge());
            for (int i = 0; i < settings.handSize && index < deck.size(); i++) {
                p.getInventory().setItem(HAND_FIRST_SLOT + i,
                        manager.items().card(deck.get(index++), false));
            }
            refreshPlayItem(p);
            p.getInventory().setHeldItemSlot(PLAY_SLOT);
            p.updateInventory();
            selections.computeIfAbsent(id, k -> new HashSet<>()).clear();
            Chat.send(p, "&8[&e提示&8] &7按 &f1 &7右键出牌；&c第 5~9 格是你的手牌，切过去会被别人看到。");
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
                Chat.actionBar(p, mustChallenge
                        ? "&c超时，自动质疑。"
                        : "&c超时，自动打出一张牌。");
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
        if (mustChallenge) {
            if (lastPlayOwner != null) {
                challenge(p);
            } else {
                mustChallenge = false;
                advanceTurn();
            }
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
        for (int slot = HAND_FIRST_SLOT; slot <= HAND_LAST_SLOT; slot++) {
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
        if (slot == null || slot < HAND_FIRST_SLOT || slot > HAND_LAST_SLOT) {
            return;
        }
        LiarBarCards.Card card = manager.items().cardOf(player.getInventory().getItem(slot));
        if (card == null) {
            return;
        }
        Set<Integer> selected = selections.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (!selected.remove(slot)) {
            if (selected.size() >= settings.maxPlayCards) {
                Chat.actionBar(player, "&c最多一次声称 " + settings.maxPlayCards + " 张。");
                return;
            }
            selected.add(slot);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.8f);
        }
        Chat.actionBar(player, "&e已选 &f" + selected.size() + " &e张，点菜单底部绿色按钮出牌。");
        PlayMenu menu = openMenus.get(player.getUniqueId());
        if (menu != null) {
            menu.render();
        }
    }

    public void openPlayMenu(Player player) {
        if (!isTurn(player)) {
            Chat.actionBar(player, "&7还没轮到你。");
            return;
        }
        if (mustChallenge) {
            Chat.actionBar(player, "&c上家打光了手牌，你必须先质疑！");
            return;
        }
        if (transition) {
            Chat.actionBar(player, "&e正在结算，稍等。");
            return;
        }
        PlayMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            menu = new PlayMenu(this, player.getUniqueId());
            openMenus.put(player.getUniqueId(), menu);
        }
        if (player.getOpenInventory().getTopInventory().getHolder() == menu) {
            menu.render();
            return;
        }
        menu.render();
        player.openInventory(menu.getInventory());
    }

    public void confirmPlay(Player player) {
        if (!isTurn(player)) {
            Chat.actionBar(player, "&7还没轮到你。");
            return;
        }
        Set<Integer> selected = new LinkedHashSet<>(
                selections.getOrDefault(player.getUniqueId(), Set.of()));
        if (selected.isEmpty()) {
            Chat.actionBar(player, "&e先在菜单里点选 1~" + settings.maxPlayCards + " 张牌。");
            return;
        }
        if (!play(player, new ArrayList<>(selected), false)) {
            PlayMenu menu = openMenus.get(player.getUniqueId());
            if (menu != null) {
                menu.render();
            }
        }
    }

    public void closeMenu(Player player) {
        if (player == null) {
            return;
        }
        PlayMenu menu = openMenus.remove(player.getUniqueId());
        if (menu != null
                && player.getOpenInventory().getTopInventory().getHolder() == menu) {
            player.closeInventory();
        }
    }

    public void closeAllMenus() {
        for (PlayMenu menu : new ArrayList<>(openMenus.values())) {
            Player p = Bukkit.getPlayer(menu.owner);
            if (p != null
                    && p.getOpenInventory().getTopInventory().getHolder() == menu) {
                p.closeInventory();
            }
        }
        openMenus.clear();
    }

    private int handCards(Player player) {
        int count = 0;
        for (int slot = HAND_FIRST_SLOT; slot <= HAND_LAST_SLOT; slot++) {
            if (manager.items().cardOf(player.getInventory().getItem(slot)) != null) {
                count++;
            }
        }
        return count;
    }

    private void refreshPlayItem(Player player) {
        ItemStack item = player.getInventory().getItem(PLAY_SLOT);
        if (item == null || !manager.items().isPlay(item)) {
            return;
        }
        player.getInventory().setItem(PLAY_SLOT,
                manager.items().playItem(tableRank, handCards(player)));
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
        if (mustChallenge) {
            Chat.actionBar(player, "&c上家打光了手牌，你必须先质疑！");
            return false;
        }
        Set<Integer> unique = new LinkedHashSet<>(slots);
        if (unique.isEmpty() || unique.size() > settings.maxPlayCards) {
            Chat.actionBar(player, "&c一次只能出 1~" + settings.maxPlayCards + " 张牌。");
            return false;
        }
        List<LiarBarCards.Card> cards = new ArrayList<>();
        for (int slot : unique) {
            if (slot < HAND_FIRST_SLOT || slot > HAND_LAST_SLOT) {
                Chat.actionBar(player, "&c只能从第 5~9 格选手牌。");
                return false;
            }
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
        refreshPlayItem(player);
        player.updateInventory();
        lastPlayOwner = player.getUniqueId();
        lastPlayCards = cards;
        pileCount += cards.size();
        showPlayCards(seatOf(player), cards);
        broadcastSeated("&e" + player.getName() + " &f打出 &e" + cards.size()
                + " &f张牌，手上剩余 &e" + handCards(player) + " &f张牌");
        if (handCards(player) == 0 && order.size() > 1) {
            mustChallenge = true;
            UUID next = order.get((turnIndex + 1) % order.size());
            Chat.broadcast("&4[骗子酒馆] &e&l" + player.getName()
                    + " &r&f在「" + name + "」打光了手牌！&c下家 &e" + nameOf(next)
                    + " &c必须质疑！");
            tableSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
        }
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
        mustChallenge = false;
        UUID challenger = player.getUniqueId();
        UUID accused = lastPlayOwner;
        List<LiarBarCards.Card> cards = lastPlayCards == null
                ? new ArrayList<>() : new ArrayList<>(lastPlayCards);

        boolean lie = cards.stream().anyMatch(card -> !card.matches(tableRank));
        UUID shooter = lie ? accused : challenger;

        transition = true;
        closeAllMenus();
        clearTurnHighlight();
        pendingShooter = shooter;
        pendingShooterIndex = Math.max(0, order.indexOf(shooter));
        broadcastSeated("&c" + nameOf(challenger) + " &f质疑 &e" + nameOf(accused) + "&f！");
        tableSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        beginReveal(cards, lie, shooter);
    }

    /** 质疑后的翻牌：只翻被质疑者出牌时摆下的那排纸，一张一张翻。 */
    private void beginReveal(List<LiarBarCards.Card> cards, boolean lie, UUID shooter) {
        int count = Math.min(cards.size(), cardFrames.size());
        if (count <= 0) {
            // 展示框意外缺失（例如出牌时区块未加载），直接进入结算
            finishReveal(lie, shooter);
            return;
        }

        for (int i = 0; i < count; i++) {
            final int index = i;
            final LiarBarCards.Card card = cards.get(i);
            final ItemFrame frame = cardFrames.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state != State.PLAYING) {
                    return;
                }
                if (frame != null && frame.isValid()) {
                    frame.setItem(manager.items().face(card), false);
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

        long finish = (long) settings.revealDelayTicks * (count + 2);
        revealTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.PLAYING) {
                return;
            }
            finishReveal(lie, shooter);
        }, Math.max(20L, finish));
    }

    /** 翻牌结束：不删展示框，真实牌留到下一回合开始。 */
    private void finishReveal(boolean lie, UUID shooter) {
        broadcastSeated(lie ? "&c&l他说谎了！" : "&a&l全是真牌！");
        broadcastSeated("&e" + nameOf(shooter) + " &f必须按下头顶发射器上的按钮启动机关。");
        promptFire(shooter);
        scheduleFireTimeout();
    }

    /** 出牌时在座位前的桌沿摆 3 个平放展示框，并按张数放纸。 */
    private void showPlayCards(Seat seat, List<LiarBarCards.Card> cards) {
        removeFrames();
        if (seat == null || cards == null || cards.isEmpty()) {
            return;
        }
        List<Location> points = playFramePoints(seat);
        if (points.size() < 3) {
            return;
        }
        ItemFrame[] slots = new ItemFrame[3];
        for (int i = 0; i < 3; i++) {
            try {
                ItemFrame frame = seat.sit.getWorld().spawn(points.get(i), ItemFrame.class);
                frame.setFixed(true);
                frame.setVisible(true);
                frame.setInvulnerable(true);
                frame.setPersistent(false);
                frame.setFacingDirection(BlockFace.UP, false);
                slots[i] = frame;
                frames.add(frame);
            } catch (RuntimeException ignored) {
                // 位置不合法时跳过这一格
            }
        }
        int count = Math.min(3, cards.size());
        int[] used = paperSlots(count);
        for (int i = 0; i < count; i++) {
            ItemFrame frame = slots[used[i]];
            cardFrames.add(frame);
            if (frame != null) {
                frame.setItem(manager.items().blankPaper(), false);
            }
        }
    }

    /** 三个展示框：椅子朝桌子走一格，再上一层（桌面），沿桌沿横排。 */
    private List<Location> playFramePoints(Seat seat) {
        List<Location> points = new ArrayList<>();
        if (center == null) {
            return points;
        }
        int dx = Integer.signum(center.getBlockX() - seat.chair.getBlockX());
        int dz = Integer.signum(center.getBlockZ() - seat.chair.getBlockZ());
        if (dx == 0 && dz == 0) {
            return points;
        }
        int frontX = seat.chair.getBlockX() + dx;
        int frontY = seat.chair.getBlockY() + 1;
        int frontZ = seat.chair.getBlockZ() + dz;
        // 与椅子朝向垂直的水平方向
        int px = -dz;
        int pz = dx;
        for (int offset = -1; offset <= 1; offset++) {
            points.add(new Location(seat.chair.getWorld(),
                    frontX + px * offset + 0.5,
                    frontY,
                    frontZ + pz * offset + 0.5));
        }
        return points;
    }

    /** 1 张放中间，2 张放左右两边，3 张全放。 */
    private int[] paperSlots(int count) {
        if (count <= 1) {
            return new int[]{1};
        }
        if (count == 2) {
            return new int[]{0, 2};
        }
        return new int[]{0, 1, 2};
    }

    private void removeFrames() {
        for (ItemFrame frame : frames) {
            if (frame != null && frame.isValid()) {
                frame.remove();
            }
        }
        frames.clear();
        cardFrames.clear();
    }

    /** 桌子中间放一个旋转的目标牌掉落物（展示实体，不可拾取）+ 文字说明。 */
    private void spawnTargetDisplay() {
        removeTargetDisplay();
        if (center == null) {
            return;
        }
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        // ItemDisplay 才能把掉落物模型放大；底边留在原位置，向上放大，避免压到下面的文字。
        Location itemLoc = center.clone().add(0, 2.45, 0);
        Location textLoc = center.clone().add(0, 1.55, 0);
        targetDisplay = world.spawn(itemLoc, ItemDisplay.class, display -> {
            display.setItemStack(manager.items().face(new LiarBarCards.Card(tableRank)));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setSilent(true);
            display.setPersistent(false);
            display.setDisplayWidth(1.4f);
            display.setDisplayHeight(1.4f);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1.4f, 1.4f, 1.4f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)));
        });

        targetText = world.spawn(textLoc, TextDisplay.class, display -> {
            display.text(Chat.color("&e本轮目标：&f" + LiarBarCards.NAMES[tableRank]));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setPersistent(false);
        });

        targetSpinAngle = 0f;
        targetSpinTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (targetDisplay == null || !targetDisplay.isValid()) {
                if (targetSpinTask != null) {
                    targetSpinTask.cancel();
                    targetSpinTask = null;
                }
                return;
            }
            targetSpinAngle = (targetSpinAngle + 5f) % 360f;
            targetDisplay.setRotation(targetSpinAngle, 0f);
        }, 2L, 2L);
    }

    private void removeTargetDisplay() {
        if (targetSpinTask != null) {
            targetSpinTask.cancel();
            targetSpinTask = null;
        }
        if (targetDisplay != null) {
            if (targetDisplay.isValid()) {
                targetDisplay.remove();
            }
            targetDisplay = null;
        }
        if (targetText != null) {
            if (targetText.isValid()) {
                targetText.remove();
            }
            targetText = null;
        }
    }

    private void promptFire(UUID id) {
        Player p = playerOf(id);
        if (p == null) {
            return;
        }
        Seat seat = seatOf(id);
        boolean hasButton = seat != null && seat.button != null;
        if (hasButton) {
            Chat.title(p, "&4&l轮到你开枪", "&7按下头顶发射器上的按钮", 3);
            Chat.actionBar(p, "&c按头顶发射器上的按钮开枪");
            p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.8f, 1.0f);
        } else {
            Chat.title(p, "&4&l轮到你开枪", "&7没找到按钮，" + settings.fireWaitSeconds
                    + " 秒后自动开枪", 3);
            Chat.actionBar(p, "&c没找到发射器按钮，" + settings.fireWaitSeconds + " 秒后自动开枪");
        }
    }

    /** 这张桌子是否有这个按钮（发射器上的开火按钮）。 */
    public boolean hasButton(Block block) {
        for (Seat seat : seats) {
            if (seat.button != null && sameBlock(seat.button, block)) {
                return true;
            }
        }
        return false;
    }

    /** 按下座椅对应的按钮：轮到自己时才真正开枪。 */
    public void pressButton(Player player, Block block) {
        for (Seat seat : seats) {
            if (seat.button == null || !sameBlock(seat.button, block)) {
                continue;
            }
            if (state != State.PLAYING || pendingShooter == null) {
                Chat.actionBar(player, "&7现在不是开枪的时候。");
                return;
            }
            if (!pendingShooter.equals(player.getUniqueId())) {
                Chat.actionBar(player, "&7这一枪该 &e" + nameOf(pendingShooter) + " &7开。");
                return;
            }
            fireRevolver(pendingShooter);
            return;
        }
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

        Seat seat = seatOf(shooter);
        boolean fires;
        if (seat == null) {
            // 座位已经没了（座位重建等异常情况）：这一枪不再结算
            fires = false;
        } else {
            int chamber = Math.floorMod(seat.gunPulls, settings.chambers);
            seat.gunPulls++;
            fires = chamber == seat.gunBulletAt;
            updateCounter(seat);
        }
        Location dispenser = seat == null ? null : seat.dispenser;
        if (fires) {
            broadcastSeated("&4&l砰！&f" + nameOf(shooter) + " 的枪响了。");
            tableSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
            startBurn(shooter, seat, dispenser);
        } else {
            // 空枪提示带上「已扣扳机次数/弹巢数」，和名字标签后缀的计数保持一致
            String pulls = seat == null ? ""
                    : "&7（&f" + seat.gunPulls + "&7/&f" + settings.chambers + "&7）";
            broadcastSeated("&a咔哒…&f" + nameOf(shooter) + " 活了下来" + pulls);
            tableSound(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
            startWater(shooter, seat, dispenser);
        }
    }

    // ---------- 岩浆 / 水 ----------

    private void startWater(UUID id, Seat seat, Location dispenser) {
        // 空枪结果已经在聊天栏播报过了，这里不再弹屏幕中央的大字
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
                if (elapsed >= totalTicks || p.getHealth() <= settings.burnStopHealth) {
                    cancel();
                    burnTasks.remove(id);
                    eliminateByFire(id);
                    afterShot(id, true);
                    return;
                }
                p.setFireTicks(60);
                // 走一次真实伤害事件，其他玩家能看到受击变红；伤害量压住，保证不会真的死。
                double damage = Math.min(settings.burnDamage,
                        Math.max(0.5, p.getHealth() - settings.burnStopHealth));
                p.setNoDamageTicks(0);
                manager.allowNextDamage(id);
                try {
                    p.damage(damage);
                } finally {
                    manager.clearDamageAllowed(id);
                }
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0),
                        12, 0.35, 0.5, 0.35, 0.02);
                elapsed += settings.burnIntervalTicks;
            }
        }.runTaskTimer(plugin, 0L, settings.burnIntervalTicks);
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
        int topY;
        int columnX = seat.chair.getBlockX();
        int columnZ = seat.chair.getBlockZ();
        if (seat.button != null) {
            // 从按钮位置流出；按钮方块先记下来，结算结束后恢复
            fluidButtonBlock = seat.button.getBlock();
            fluidButtonData = fluidButtonBlock.getBlockData();
            topY = seat.button.getBlockY();
            columnX = seat.button.getBlockX();
            columnZ = seat.button.getBlockZ();
        } else if (dispenser != null) {
            topY = dispenser.getBlockY() - 1;
        } else {
            topY = seat.chair.getBlockY() + 3;
        }
        if (topY < bottomY) {
            topY = bottomY;
        }
        final int floorY = bottomY;
        final int x = columnX;
        final int z = columnZ;
        final int[] y = {topY};
        // 标记柱体范围：原版流体想自己往下/往旁边蔓延时，由事件监听拦掉
        fluidColumnWorld = world;
        fluidColumnX = x;
        fluidColumnZ = z;
        fluidColumnMinY = floorY - 1;
        fluidColumnMaxY = topY + 1;
        startFluidGuard();
        if (dispenser != null) {
            world.playSound(dispenser, Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
        }
        int interval = material == Material.LAVA
                ? settings.lavaFlowTicksPerBlock : settings.waterFlowTicksPerBlock;
        flowTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.PLAYING) {
                    cancel();
                    flowTask = null;
                    clearColumn();
                    return;
                }
                Block block = world.getBlockAt(x, y[0], z);
                boolean buttonCell = fluidButtonBlock != null
                        && sameBlock(block.getLocation(), fluidButtonBlock);
                if (buttonCell || block.getType().isAir()
                        || block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    block.setType(material, false);
                    fluidBlocks.add(block);
                    dripEffect(world, material, x, y[0], z);
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
        }.runTaskTimer(plugin, 2L, interval);
    }

    /** 每往下走一格时的一点点水花/火星，让“一格一格流”看得更清楚。 */
    private void dripEffect(World world, Material material, int x, int y, int z) {
        Location at = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        if (material == Material.WATER) {
            world.spawnParticle(Particle.SPLASH, x + 0.5, y + 0.9, z + 0.5,
                    6, 0.18, 0.05, 0.18, 0.02);
            world.playSound(at, Sound.BLOCK_WATER_AMBIENT, 0.35f, 1.5f);
        } else {
            world.spawnParticle(Particle.LAVA, x + 0.5, y + 0.5, z + 0.5,
                    3, 0.15, 0.2, 0.15, 0.0);
            world.playSound(at, Sound.BLOCK_LAVA_POP, 0.35f, 1.2f);
        }
    }

    private void startFluidGuard() {
        if (fluidGuardTask != null) {
            return;
        }
        fluidGuardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::clearSides, 1L, 1L);
    }

    private void stopFluidGuard() {
        if (fluidGuardTask != null) {
            fluidGuardTask.cancel();
            fluidGuardTask = null;
        }
    }

    /** 清掉水/岩浆的水平邻居和“抢跑”的下方邻居，保证只能按定时器一格一格往下走。 */
    private void clearSides() {
        for (Block block : fluidBlocks) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH,
                    BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
                Block side = block.getRelative(face);
                if ((side.getType() == Material.LAVA || side.getType() == Material.WATER)
                        && !fluidBlocks.contains(side)) {
                    side.setType(Material.AIR, false);
                }
            }
        }
    }

    /** 这格是否落在本桌正在放水/岩浆的柱体范围里。 */
    public boolean inFluidColumn(Block block) {
        return fluidColumnWorld != null
                && block.getWorld().equals(fluidColumnWorld)
                && Math.abs(block.getX() - fluidColumnX) <= 1
                && Math.abs(block.getZ() - fluidColumnZ) <= 1
                && block.getY() >= fluidColumnMinY
                && block.getY() <= fluidColumnMaxY;
    }

    private void clearColumn() {
        stopFluidGuard();
        clearSides();
        for (Block block : fluidBlocks) {
            if (block.getType() == Material.LAVA || block.getType() == Material.WATER) {
                block.setType(Material.AIR, false);
            }
        }
        fluidBlocks.clear();
        fluidColumnWorld = null;
        // 流体是从按钮位置流出来的：结算结束把按钮装回去
        if (fluidButtonBlock != null && fluidButtonData != null) {
            fluidButtonBlock.setBlockData(fluidButtonData, false);
        }
        fluidButtonBlock = null;
        fluidButtonData = null;
    }

    private void eliminateByFire(UUID id) {
        if (!order.remove(id)) {
            return;
        }
        if (id.equals(lastPlayOwner)) {
            mustChallenge = false;
            lastPlayOwner = null;
            lastPlayCards = null;
        }
        if (id.equals(highlightedPlayer)) {
            clearTurnHighlight();
        }
        Seat seat = seatOf(id);
        Player p = playerOf(id);
        closeMenu(p);
        manager.clearDamageAllowed(id);
        // 结果只走聊天栏，不再弹屏幕中央的大字
        broadcastSeated("&c" + nameOf(id) + " 被岩浆吞没了，转为观战。");
        if (p != null) {
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
                spawnHead(seat, p);
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

    /**
     * 被岩浆吞没：不要倒下的尸体，直接把该玩家的头颅摆到桌面
     * 三个展示框正中间的那一格上，当作留在桌上的“遗物”。
     */
    private void spawnHead(Seat seat, Player player) {
        List<Location> points = playFramePoints(seat);
        if (points.size() < 3) {
            return;
        }
        Location middle = points.get(1);
        World world = middle.getWorld();
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ());
        removeFrameAt(block);
        if (!block.getType().isAir()
                && block.getType() != Material.WATER && block.getType() != Material.LAVA) {
            return;
        }
        block.setType(Material.PLAYER_HEAD, false);
        if (block.getState() instanceof Skull skull) {
            skull.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            skull.update(true, false);
        }
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(headFacing(seat, middle));
            block.setBlockData(rotatable, false);
        }
        seat.headBlock = block.getLocation();
        Location at = middle.clone().add(0, 0.4, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 24, 0.25, 0.2, 0.25, 0.01);
        world.playSound(middle, Sound.ENTITY_PLAYER_HURT, 0.9f, 0.6f);
    }

    /** 桌上的头颅朝向死者自己的座位。 */
    private BlockFace headFacing(Seat seat, Location at) {
        int dx = Integer.signum(seat.chair.getBlockX() - at.getBlockX());
        int dz = Integer.signum(seat.chair.getBlockZ() - at.getBlockZ());
        if (dx > 0) {
            return BlockFace.EAST;
        }
        if (dx < 0) {
            return BlockFace.WEST;
        }
        if (dz > 0) {
            return BlockFace.SOUTH;
        }
        if (dz < 0) {
            return BlockFace.NORTH;
        }
        return BlockFace.SOUTH;
    }

    /** 中间那一格如果还留着本轮出牌的展示框，先摘掉，避免和头颅重叠。 */
    private void removeFrameAt(Block block) {
        for (ItemFrame frame : new ArrayList<>(frames)) {
            if (sameBlock(frame.getLocation(), block)) {
                if (frame.isValid()) {
                    frame.remove();
                }
                frames.remove(frame);
                cardFrames.remove(frame);
            }
        }
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
        closeAllMenus();
        turnIndex = (turnIndex + 1) % order.size();
        secondsLeft = settings.turnSeconds;
        if (mustChallenge) {
            announceForcedChallenge(currentPlayer());
        } else {
            announceTurn(currentPlayer());
        }
        updateActionBars();
    }

    /**
     * 轮到某个玩家出牌：在他的屏幕中央弹出「轮到你出牌！」，
     * 同时给他加发光效果，让桌边所有人都能一眼看出该谁出牌。
     */
    private void announceTurn(UUID id) {
        clearTurnHighlight();
        if (id == null || state != State.PLAYING) {
            return;
        }
        Player p = playerOf(id);
        if (p == null) {
            return;
        }
        Chat.title(p, "&e&l轮到你出牌！",
                "&7目标牌：&f" + LiarBarCards.NAMES[tableRank]
                        + " &8| &7第 " + round + " 回合", 2);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.6f);
        // 轮到谁就把谁切回第 1 格，避免他低头翻手牌时被旁边的人看到
        p.getInventory().setHeldItemSlot(PLAY_SLOT);
        Chat.send(p, "&7右键 &f1 &7号格的出牌道具，打开菜单选牌。");
        // 持续到回合结束再留一点余量，防止出牌瞬间高亮先消失
        int ticks = (Math.max(1, settings.turnSeconds) + 5) * 20;
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0,
                false, false, false), true);
        highlightedPlayer = id;
    }

    /**
     * 上家打光手牌后的强制质疑：不给开牌菜单，直接把下家的手持切到质疑道具，
     * 超时也会由 {@link #autoPlay(UUID)} 自动质疑。
     */
    private void announceForcedChallenge(UUID id) {
        clearTurnHighlight();
        if (id == null || state != State.PLAYING) {
            return;
        }
        if (lastPlayOwner == null) {
            mustChallenge = false;
            announceTurn(id);
            return;
        }
        Player p = playerOf(id);
        if (p == null) {
            return;
        }
        String accused = nameOf(lastPlayOwner);
        Chat.title(p, "&c&l必须质疑！", "&e" + accused + " &f已打光手牌", 2);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
        p.getInventory().setHeldItemSlot(CHALLENGE_SLOT);
        Chat.send(p, "&c" + accused + " 已打光手牌，你必须质疑，不能出牌！");
        Chat.send(p, "&7右键 &f2 &7号格的质疑道具。");
        int ticks = (Math.max(1, settings.turnSeconds) + 5) * 20;
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0,
                false, false, false), true);
        highlightedPlayer = id;
    }

    /** 清掉当前出牌人的发光高亮（换人、质疑结算、结束对局时调用）。 */
    private void clearTurnHighlight() {
        if (highlightedPlayer == null) {
            return;
        }
        Player p = playerOf(highlightedPlayer);
        if (p != null) {
            p.removePotionEffect(PotionEffectType.GLOWING);
        }
        highlightedPlayer = null;
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
                + " &7| &e轮到：&a&l" + currentName
                + " &7| &f剩余 &e" + Math.max(0, secondsLeft) + "s"
                + (mustChallenge ? " &7| &c&l强制质疑" : "");
        List<UUID> seated = new ArrayList<>();
        for (Seat seat : seats) {
            Player p = playerOf(seat.occupant);
            if (p == null) {
                continue;
            }
            seated.add(p.getUniqueId());
            if (!order.contains(p.getUniqueId())) {
                String info = transition ? "&e结算中…" : bar;
                Chat.actionBar(p, info + " &7| 你已出局（剩 " + order.size() + " 人）");
            } else if (transition) {
                Chat.actionBar(p, "&e结算中…");
            } else if (p.getUniqueId().equals(current)) {
                Chat.actionBar(p, mustChallenge
                        ? bar + " &c&l← 必须质疑"
                        : bar + " &a&l← 你的回合");
            } else {
                Chat.actionBar(p, bar);
            }
        }
        // 主城观战者：只看离自己最近的那张桌子的对局栏，避免几张桌子互相覆盖
        for (UUID id : HubAPI.spectatorsNear(seated, 32.0)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || manager.nearestTable(p.getLocation()) != this) {
                continue;
            }
            String info = transition ? "&e结算中…" : bar;
            Chat.actionBar(p, "&7观战 &8| " + info);
        }
    }

    // ---------- 结算 ----------

    private void endGame(UUID winner) {
        if (state == State.ENDING) {
            return;
        }
        state = State.ENDING;
        transition = true;
        closeAllMenus();
        clearTurnHighlight();
        cancelTick();
        cancelPendingTasks();
        if (winner != null) {
            broadcastSeated("&6&l" + nameOf(winner) + " &f赢得了本局！");
            Chat.broadcast("&4[骗子酒馆] &e" + nameOf(winner) + " &f在「" + name + "」赢得了本局！");
            manager.recordWin(winner);
            Player p = playerOf(winner);
            if (p != null) {
                // 胜利结果已经在聊天栏播报，这里只留音效，不再弹屏幕中央的大字
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
        removeTargetDisplay();
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
                Chat.send(p, "&e本局结束：从椅子上下来，想再来一局就重新坐下。");
            }
        }
        clearGameState();
        manager.reportArena(this);
    }

    private void clearGameState() {
        closeAllMenus();
        clearTurnHighlight();
        removeCounters();
        order.clear();
        selections.clear();
        names.clear();
        lastPlayOwner = null;
        lastPlayCards = null;
        pileCount = 0;
        transition = false;
        mustChallenge = false;
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
        closeMenu(player);
        if (player.getUniqueId().equals(highlightedPlayer)) {
            clearTurnHighlight();
        }
        Seat seat = seatOf(player);
        if (seat != null) {
            if (player.getUniqueId().equals(pendingShooter)) {
                // 先结算这一枪再清座：枪跟着座位走，清座后数据就没了
                fireRevolver(pendingShooter);
            }
            clearSeat(seat, player);
        }
        selections.remove(player.getUniqueId());
        names.remove(player.getUniqueId());
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
