package com.superniuma.game;

import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.mechanism.MechanismChoice;
import com.superniuma.mechanism.MechanismDef;
import com.superniuma.mechanism.MechanismInstance;
import com.superniuma.mechanism.MechanismManager;
import com.superniuma.mechanism.PlacementSession;
import com.superniuma.menu.SelectMechanismMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 一个房间：选机关 -> 摆机关 -> 跑酷 -> 记分。 */
public final class GameRoom {

    public enum Phase {
        WAITING, SELECTING, PLACING, RUNNING, SCORING, ENDED
    }

    public static final String GAME_NAME = "超级牛马";

    private final SuperNiumaPlugin plugin;
    private final String id;
    private final Arena arena;
    private final GameMode mode;
    private final int teamCount;
    private final int winScore;
    private final int maxRounds;
    private final int maxPlayers;

    private final List<Participant> participants = new ArrayList<>();
    private final Map<Integer, TeamState> teams = new LinkedHashMap<>();
    private final List<MechanismInstance> mechanisms = new ArrayList<>();
    private final Map<UUID, PlacementSession> sessions = new LinkedHashMap<>();
    private final List<MechanismChoice> selectionPool = new ArrayList<>();
    private final Map<String, Integer> selectionSlots = new LinkedHashMap<>();
    private final Map<String, UUID> optionOwners = new HashMap<>();
    private final Random random = new Random();

    private final Map<UUID, ItemStack[]> savedInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, Scoreboard> savedBoards = new HashMap<>();
    private final Map<UUID, Integer> dropCounts = new HashMap<>();
    private final Map<UUID, Long> lastDropMillis = new HashMap<>();

    private Scoreboard scoreboard;
    private Team noCollision;
    private BukkitTask task;

    private Phase phase = Phase.WAITING;
    private int phaseTicks;
    private int autoCloseTicks = -1;
    private int round;
    private RoundData roundData = new RoundData();
    private double lastClearRate = 1.0;
    private UUID host;
    private boolean ending;

    public GameRoom(SuperNiumaPlugin plugin, String id, Arena arena, GameMode mode, int teamCount,
                    int winScore, int maxRounds, int maxPlayers, Player host) {
        this.plugin = plugin;
        this.id = id;
        this.arena = arena;
        this.mode = mode;
        this.teamCount = Math.max(1, Math.min(4, teamCount));
        this.winScore = winScore;
        this.maxRounds = maxRounds;
        this.maxPlayers = maxPlayers;
        this.host = host.getUniqueId();
        if (mode == GameMode.TEAM) {
            for (TeamState team : TeamState.palette(this.teamCount)) {
                teams.put(team.id(), team);
            }
        }
        addParticipant(host);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ---------- 基本信息 ----------

    public String id() {
        return id;
    }

    public Arena arena() {
        return arena;
    }

    public GameMode mode() {
        return mode;
    }

    public int winScore() {
        return winScore;
    }

    public int maxRounds() {
        return maxRounds;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isRunning() {
        return phase == Phase.RUNNING;
    }

    public int round() {
        return round;
    }

    public UUID host() {
        return host;
    }

    public int size() {
        return participants.size();
    }

    public List<Participant> participants() {
        return participants;
    }

    public List<MechanismInstance> mechanisms() {
        return mechanisms;
    }

    public Map<Integer, TeamState> teams() {
        return teams;
    }

    public List<MechanismChoice> selectionPool() {
        return selectionPool;
    }

    /** 本回合所有玩家共用的机关槽位（optionId -> 箱子槽位）。 */
    public Map<String, Integer> selectionSlots() {
        return selectionSlots;
    }

    public UUID optionOwner(String optionId) {
        return optionOwners.get(optionId);
    }

    public Participant participant(Player player) {
        return participant(player.getUniqueId());
    }

    public Participant participant(UUID uuid) {
        for (Participant participant : participants) {
            if (participant.uuid().equals(uuid)) {
                return participant;
            }
        }
        return null;
    }

    public List<Player> onlinePlayers() {
        List<Player> out = new ArrayList<>();
        for (Participant participant : participants) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    /** 还在跑酷、可以触发机关判定的玩家。 */
    public List<Player> activePlayers() {
        List<Player> out = new ArrayList<>();
        for (Participant participant : participants) {
            if (participant.finished() || participant.failed()) {
                continue;
            }
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                out.add(player);
            }
        }
        return out;
    }

    public Material woolFor(UUID uuid) {
        if (mode == GameMode.TEAM) {
            Participant participant = participant(uuid);
            if (participant != null) {
                TeamState team = teams.get(participant.teamId());
                if (team != null) {
                    return team.wool();
                }
            }
        }
        return Material.WHITE_WOOL;
    }

    // ---------- 加入 / 离开 ----------

    public boolean addParticipant(Player player) {
        if (phase != Phase.WAITING || participants.size() >= maxPlayers || participant(player) != null) {
            return false;
        }
        Participant participant = new Participant(player.getUniqueId(), player.getName());
        participant.setTeamId(assignTeam());
        participants.add(participant);
        if (mode == GameMode.TEAM) {
            TeamState team = teams.get(participant.teamId());
            if (team != null) {
                team.members().add(player.getUniqueId());
            }
        }
        saveInventory(player);
        setupCollisionBoard(player);
        HubAPI.setGame(player.getUniqueId(), GAME_NAME);
        HubAPI.announceJoin(plugin.getName(), player.getName(), arena.getName());
        updateArenaStatus();
        Location spawn = arena.getSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setCanPickupItems(false);
        broadcast("&a" + player.getName() + " 加入了房间（" + participants.size() + "/" + maxPlayers + "）。");
        return true;
    }

    private int assignTeam() {
        if (mode == GameMode.SOLO) {
            return 0;
        }
        int best = 0;
        int bestSize = Integer.MAX_VALUE;
        for (TeamState team : teams.values()) {
            if (team.members().size() < bestSize) {
                bestSize = team.members().size();
                best = team.id();
            }
        }
        return best;
    }

    public void removeParticipant(Player player) {
        Participant participant = participant(player);
        if (participant == null) {
            return;
        }
        participants.remove(participant);
        PlacementSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            plugin.mechanismManager().clearPreview(session);
        }
        if (mode == GameMode.TEAM) {
            TeamState team = teams.get(participant.teamId());
            if (team != null) {
                team.members().remove(player.getUniqueId());
            }
        }
        if (player.isOnline()) {
            restore(player);
        }
        HubAPI.clearGame(player.getUniqueId());
        if (participants.isEmpty()) {
            cleanup();
            plugin.rooms().removeRoom(this);
            return;
        }
        if (host.equals(player.getUniqueId())) {
            host = participants.get(0).uuid();
            broadcast("&e房主已转移给 " + participants.get(0).name() + "。");
        }
        if (phase != Phase.WAITING && phase != Phase.ENDED) {
            participant.setFailed(true);
            roundData.failed().add(player.getUniqueId());
            if (participants.size() < plugin.getConfig().getInt("room.min-players", 2)) {
                abort("人数不足，对局结束。");
                return;
            }
        }
        updateArenaStatus();
        broadcast("&7" + player.getName() + " 离开了房间。");
    }

    // ---------- 开局 ----------

    public boolean startGame(Player starter) {
        if (phase != Phase.WAITING) {
            Msg.send(starter, "&c房间已经开始了。");
            return false;
        }
        if (!starter.getUniqueId().equals(host) && !starter.hasPermission("sn.admin")) {
            Msg.send(starter, "&c只有房主能开始游戏。");
            return false;
        }
        int min = plugin.getConfig().getInt("room.min-players", 2);
        if (participants.size() < min) {
            Msg.send(starter, "&c至少需要 " + min + " 名玩家才能开始。");
            return false;
        }
        if (!arena.isReady()) {
            Msg.send(starter, "&c这张地图还没有配置完整（出生点/起点/终点/建造范围）。");
            return false;
        }
        assignTeamsBalanced();
        round = 0;
        startRound();
        return true;
    }

    private void assignTeamsBalanced() {
        if (mode != GameMode.TEAM) {
            return;
        }
        for (TeamState team : teams.values()) {
            team.members().clear();
            team.resetScore();
        }
        List<Participant> shuffled = new ArrayList<>(participants);
        java.util.Collections.shuffle(shuffled, random);
        for (int i = 0; i < shuffled.size(); i++) {
            int teamId = i % teams.size();
            shuffled.get(i).setTeamId(teamId);
            teams.get(teamId).members().add(shuffled.get(i).uuid());
        }
    }

    private void startRound() {
        round++;
        roundData = new RoundData();
        autoCloseTicks = -1;
        optionOwners.clear();
        selectionPool.clear();
        selectionSlots.clear();
        sessions.clear();
        for (Participant participant : participants) {
            participant.resetRound();
        }
        // 上一回合被吃掉的金币重新出现；其余机关跨回合保留。
        plugin.mechanismManager().respawnCoins(this);
        buildSelectionPool();

        phase = Phase.SELECTING;
        phaseTicks = secondsToTicks(plugin.getConfig().getInt("selection.duration-seconds", 20));

        for (Participant participant : participants) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) {
                continue;
            }
            player.teleport(arena.getSpawn());
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            clearGameInventory(player);
            HubAPI.setPlaying(player.getUniqueId(), GAME_NAME);
            if (mode == GameMode.TEAM) {
                TeamState team = teams.get(participant.teamId());
                if (team != null) {
                    HubAPI.setTabNameColor(player.getUniqueId(), String.valueOf(team.color().getChar()));
                }
            }
            SelectMechanismMenu.open(plugin, this, player);
        }
        updateArenaStatus();
        broadcast("");
        broadcast("&e&l第 " + round + " 回合 &7（目标 " + winScore + " 分）");
        broadcast("&e请选择你的机关，持续 " + plugin.getConfig().getInt("selection.duration-seconds", 20) + " 秒。");
    }

    private void buildSelectionPool() {
        List<MechanismDef> enabled = plugin.mechanisms().enabled();
        if (enabled.isEmpty()) {
            return;
        }
        MechanismDef bomb = plugin.mechanisms().def("bomb");
        boolean lowClear = bomb != null && bomb.enabled() && round > 1 && lastClearRate < bomb.lowClearRate();
        int forced = lowClear ? Math.min(participants.size(), bomb.lowClearMinCount()) : 0;

        for (int i = 0; i < participants.size(); i++) {
            MechanismDef def;
            if (forced > 0) {
                def = bomb;
                forced--;
            } else {
                def = plugin.mechanisms().pickDef(random, lowClear ? bomb.lowClearWeight() : -1);
            }
            if (def == null) {
                def = enabled.get(random.nextInt(enabled.size()));
            }
            MechanismDef.Variant variant = plugin.mechanisms().pickVariant(def, random);
            selectionPool.add(new MechanismChoice("opt-" + i + "-" + def.id(), def, variant));
        }

        // 同一回合所有玩家看到同一套槽位；只在回合开始时洗一次。
        selectionSlots.clear();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);
        for (int i = 0; i < selectionPool.size() && i < slots.size(); i++) {
            selectionSlots.put(selectionPool.get(i).optionId(), slots.get(i));
        }
    }

    // ---------- 选择机关 ----------

    public void selectMechanism(Player player, String optionId) {
        if (phase != Phase.SELECTING) {
            return;
        }
        Participant participant = participant(player);
        if (participant == null) {
            return;
        }
        if (participant.choice() != null) {
            Msg.send(player, "&c你已经选过机关了。");
            return;
        }
        MechanismChoice option = null;
        for (MechanismChoice choice : selectionPool) {
            if (choice.optionId().equals(optionId)) {
                option = choice;
                break;
            }
        }
        if (option == null) {
            return;
        }
        UUID owner = optionOwners.get(optionId);
        if (owner != null && !owner.equals(player.getUniqueId())) {
            Msg.send(player, "&c这个机关已经被别人选了。");
            return;
        }
        participant.setChoice(option);
        participant.setChosenOptionId(optionId);
        optionOwners.put(optionId, player.getUniqueId());
        broadcast("&e" + player.getName() + " 选择了 " + ChatColor.stripColor(Msg.c(option.def().displayName())) + "。");
        refreshSelectionMenus();
    }

    public void refreshSelectionMenus() {
        for (Player player : onlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof com.superniuma.menu.MenuHolder holder
                    && holder.type() == com.superniuma.menu.MenuHolder.Type.SELECT) {
                SelectMechanismMenu.refresh(plugin, this, player);
            }
        }
    }

    public String selectionStatus(String optionId) {
        UUID owner = optionOwners.get(optionId);
        if (owner == null) {
            return null;
        }
        Participant participant = participant(owner);
        return participant == null ? "" : participant.name();
    }

    private void finishSelection() {
        List<MechanismChoice> remaining = new ArrayList<>(selectionPool);
        if (optionOwners != null) {
            remaining.removeIf(choice -> optionOwners.containsKey(choice.optionId()));
        }
        for (Participant participant : participants) {
            if (participant.choice() != null) {
                continue;
            }
            if (remaining.isEmpty()) {
                break;
            }
            MechanismChoice choice = remaining.remove(0);
            participant.setChoice(choice);
            participant.setChosenOptionId(choice.optionId());
            optionOwners.put(choice.optionId(), participant.uuid());
        }
        for (Player player : onlinePlayers()) {
            player.closeInventory();
        }

        phase = Phase.PLACING;
        phaseTicks = secondsToTicks(plugin.getConfig().getInt("placement.duration-seconds", 90));
        int startDistance = plugin.getConfig().getInt("placement.start-distance", 3);
        for (Participant participant : participants) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null || participant.choice() == null) {
                continue;
            }
            player.teleport(arena.getSpawn());
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            givePlacementItems(player, participant.choice());
            PlacementSession session = new PlacementSession(player, participant.choice(), startDistance);
            sessions.put(player.getUniqueId(), session);
        }
        broadcast("&e摆放环节开始：飞到想要的位置，按左键/右键/滚轮锁定机关，Q 两次确认。");
    }

    private void givePlacementItems(Player player, MechanismChoice choice) {
        clearGameInventory(player);
        Material icon = choice.def().icon();
        if ("platform".equals(choice.def().id())) {
            icon = woolFor(player.getUniqueId());
        }
        ItemStack tool = new ItemStack(icon);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.c(choice.def().displayName() + " &7（调整中）"));
            meta.setLore(Arrays.asList(
                    Msg.c("&7左键：水平旋转 90°"),
                    Msg.c("&7右键：竖直翻转 90°（尖刺类每次 180°）"),
                    Msg.c("&7滚轮：沿视线前后/上下移动"),
                    Msg.c("&7Q 两次：确认放置")));
            meta.getPersistentDataContainer().set(plugin.itemKey(), PersistentDataType.BYTE, (byte) 1);
            tool.setItemMeta(meta);
        }
        for (int slot = 0; slot < 9; slot++) {
            player.getInventory().setItem(slot, tool.clone());
        }
        player.getInventory().setHeldItemSlot(4);
        player.updateInventory();
    }

    // ---------- 摆放交互 ----------

    public PlacementSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void rotateLeft(Player player) {
        PlacementSession session = session(player);
        if (phase == Phase.PLACING && session != null && !session.confirmed()) {
            if (session.pendingConfirm()) {
                Msg.actionBar(player, "&e已按 Q，再按一次确认放置");
                return;
            }
            plugin.mechanismManager().rotateLeft(session);
        }
    }

    public void rotateRight(Player player) {
        PlacementSession session = session(player);
        if (phase == Phase.PLACING && session != null && !session.confirmed()) {
            if (session.pendingConfirm()) {
                Msg.actionBar(player, "&e已按 Q，再按一次确认放置");
                return;
            }
            plugin.mechanismManager().rotateRight(session);
        }
    }

    public void scroll(Player player, boolean forward) {
        PlacementSession session = session(player);
        if (phase == Phase.PLACING && session != null && !session.confirmed()) {
            if (session.pendingConfirm()) {
                Msg.actionBar(player, "&e已按 Q，再按一次确认放置");
                return;
            }
            plugin.mechanismManager().scroll(session, forward);
        }
    }

    public void confirmPlacement(Player player) {
        PlacementSession session = session(player);
        if (phase != Phase.PLACING || session == null || session.confirmed()) {
            return;
        }
        MechanismManager.PlaceResult result = plugin.mechanismManager().place(this, session);
        if (!result.success()) {
            session.setPendingConfirm(false);
            Msg.send(player, "&c" + result.error());
            return;
        }
        session.setConfirmed(true);
        plugin.mechanismManager().clearPreview(session);
        Participant participant = participant(player);
        if (participant != null) {
            participant.setPlaced(true);
        }
        int placed = 0;
        int total = 0;
        for (Participant other : participants) {
            if (other.choice() == null) {
                continue;
            }
            total++;
            if (other.placed()) {
                placed++;
            }
        }
        broadcast("&7" + player.getName() + " 已放置机关（" + placed + "/" + total + "）");
        Msg.send(player, "&a机关已放置！");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
    }

    public void handleDrop(Player player) {
        if (phase != Phase.PLACING) {
            return;
        }
        PlacementSession session = session(player);
        if (session == null || session.confirmed()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastDropMillis.getOrDefault(player.getUniqueId(), 0L);
        boolean fresh = now - last > 3000L;
        if (fresh) {
            session.setPendingConfirm(false);
        }
        int count = fresh ? 0 : dropCounts.getOrDefault(player.getUniqueId(), 0);
        count++;
        lastDropMillis.put(player.getUniqueId(), now);
        dropCounts.put(player.getUniqueId(), count);
        if (count >= 2) {
            dropCounts.remove(player.getUniqueId());
            confirmPlacement(player);
        } else {
            session.setPendingConfirm(true);
            Msg.send(player, "&e再按一次 Q 确认放置。");
        }
    }

    // ---------- 跑酷 ----------

    private void beginRunning() {
        for (PlacementSession session : sessions.values()) {
            if (!session.confirmed()) {
                MechanismManager.PlaceResult result = plugin.mechanismManager().place(this, session);
                if (result.success()) {
                    session.setConfirmed(true);
                }
            }
            plugin.mechanismManager().clearPreview(session);
        }

        phase = Phase.RUNNING;
        phaseTicks = secondsToTicks(plugin.getConfig().getInt("running.duration-seconds", 180));

        Location start = arena.getStart();
        for (Participant participant : participants) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) {
                continue;
            }
            player.teleport(start);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            clearGameInventory(player);
            player.setFireTicks(0);
        }
        broadcast("&a&l跑酷开始！到达终点即可过关。");
        for (Player player : onlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    public void handleMove(Player player) {
        if (phase != Phase.RUNNING) {
            return;
        }
        Participant participant = participant(player);
        if (participant == null || participant.finished() || participant.failed()) {
            return;
        }
        Location location = player.getLocation();
        if (location.getY() < arena.getVoidY()) {
            onPlayerFailed(player, null, "掉出了地图");
            return;
        }
        if (arena.inFinish(location)) {
            finishRound(player);
        }
    }

    private void finishRound(Player player) {
        Participant participant = participant(player);
        if (participant == null || participant.finished() || participant.failed()) {
            return;
        }
        participant.setFinished(true);
        participant.setFinishOrder(roundData.finishOrder().size() + 1);
        participant.setFinishTime(System.currentTimeMillis());
        roundData.finishOrder().add(player.getUniqueId());
        roundData.finishTimes().put(player.getUniqueId(), participant.finishTime());
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        broadcast("&a" + player.getName() + " 到达终点！（第 " + participant.finishOrder() + " 名）");
    }

    public void onPlayerFailed(Player player, UUID mechanismOwner, String reason) {
        if (phase != Phase.RUNNING) {
            return;
        }
        Participant participant = participant(player);
        if (participant == null || participant.finished() || participant.failed()) {
            return;
        }
        participant.setFailed(true);
        roundData.failed().add(player.getUniqueId());
        if (mechanismOwner != null && !mechanismOwner.equals(player.getUniqueId())) {
            roundData.addTrapFail(mechanismOwner);
            Participant owner = participant(mechanismOwner);
            if (owner != null) {
                owner.addRoundTrapFails(1);
            }
        }
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        broadcast("&c" + player.getName() + " 失败：" + reason);
    }

    public void onCoinTouched(Player player, MechanismInstance instance) {
        Participant participant = participant(player);
        if (participant == null) {
            return;
        }
        double points = instance.def().points();
        participant.addRoundCoinPoints(points);
        roundData.addCoin(player.getUniqueId(), points);
        Msg.send(player, "&6拾取金币 +" + format(points) + " 分");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    private boolean allDone() {
        for (Participant participant : participants) {
            if (participant.finished() || participant.failed()) {
                continue;
            }
            if (Bukkit.getPlayer(participant.uuid()) != null) {
                return false;
            }
        }
        return true;
    }

    /** 所有在线玩家都已经选好机关。 */
    private boolean allChosen() {
        for (Participant participant : participants) {
            if (Bukkit.getPlayer(participant.uuid()) == null) {
                continue;
            }
            if (participant.choice() == null) {
                return false;
            }
        }
        return true;
    }

    /** 所有有机关的玩家都确认放置（离线玩家视为已放好）。 */
    private boolean allPlaced() {
        for (Participant participant : participants) {
            if (participant.choice() == null) {
                continue;
            }
            if (Bukkit.getPlayer(participant.uuid()) == null) {
                continue;
            }
            PlacementSession session = sessions.get(participant.uuid());
            if (session == null || !session.confirmed()) {
                return false;
            }
        }
        return true;
    }

    // ---------- 记分 ----------

    private void beginScoring() {
        for (Participant participant : participants) {
            if (!participant.finished() && !participant.failed()) {
                participant.setFailed(true);
                roundData.failed().add(participant.uuid());
                Player player = Bukkit.getPlayer(participant.uuid());
                if (player != null) {
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    Msg.send(player, "&c时间到，你没能到达终点。");
                }
            }
        }

        int finishers = roundData.finishOrder().size();
        lastClearRate = participants.isEmpty() ? 0.0 : (double) finishers / participants.size();

        if (mode == GameMode.TEAM) {
            scoreTeams();
        } else {
            scoreSolo();
        }

        phase = Phase.SCORING;
        phaseTicks = secondsToTicks(plugin.getConfig().getInt("scoring.display-seconds", 5));

        broadcast("");
        broadcast("&e&l第 " + round + " 回合结算");
        if (mode == GameMode.TEAM) {
            for (TeamState team : teams.values()) {
                broadcast(team.coloredName() + " &f" + format(team.score()) + " 分");
            }
        } else {
            List<Participant> sorted = new ArrayList<>(participants);
            sorted.sort(Comparator.comparingDouble(Participant::score).reversed());
            for (Participant participant : sorted) {
                broadcast("&f" + participant.name() + " &7- &e" + format(participant.score()) + " 分");
            }
        }
        broadcast("&7通关率 " + Math.round(lastClearRate * 100) + "%");
    }

    private void scoreTeams() {
        Map<Integer, Integer> finishersPerTeam = new HashMap<>();
        Map<Integer, Long> fastestPerTeam = new HashMap<>();
        for (Participant participant : participants) {
            if (!participant.finished()) {
                continue;
            }
            int teamId = participant.teamId();
            finishersPerTeam.merge(teamId, 1, Integer::sum);
            fastestPerTeam.merge(teamId, participant.finishTime(), Math::min);
        }

        Set<Integer> passed = new HashSet<>(finishersPerTeam.keySet());
        if (!passed.isEmpty()) {
            double pass = passed.size() == 1
                    ? configDouble("scoring.team.only-pass-points", 1.5)
                    : configDouble("scoring.team.pass-points", 1.0);
            for (int teamId : passed) {
                addTeamScore(teamId, pass);
            }
            int fastestTeam = passed.stream()
                    .min(Comparator.comparingLong(fastestPerTeam::get))
                    .orElse(-1);
            if (fastestTeam >= 0) {
                addTeamScore(fastestTeam, configDouble("scoring.team.fastest-bonus", 0.5));
            }
            int most = passed.stream().mapToInt(finishersPerTeam::get).max().orElse(0);
            if (most > 0) {
                for (int teamId : passed) {
                    if (finishersPerTeam.get(teamId) == most) {
                        addTeamScore(teamId, configDouble("scoring.team.most-finishers-bonus", 0.5));
                    }
                }
            }
        }
        for (Participant participant : participants) {
            if (participant.roundCoinPoints() > 0) {
                addTeamScore(participant.teamId(), participant.roundCoinPoints());
            }
        }
    }

    private void scoreSolo() {
        List<Participant> finishers = new ArrayList<>();
        for (Participant participant : participants) {
            if (participant.finished()) {
                finishers.add(participant);
            }
        }
        finishers.sort(Comparator.comparingLong(Participant::finishTime));
        if (!finishers.isEmpty()) {
            double pass = finishers.size() == 1
                    ? configDouble("scoring.solo.only-pass-points", 1.5)
                    : configDouble("scoring.solo.pass-points", 1.0);
            for (Participant participant : finishers) {
                participant.addScore(pass);
            }
            finishers.get(0).addScore(configDouble("scoring.solo.fastest-bonus", 0.5));

            int mostFails = 0;
            for (Participant participant : participants) {
                mostFails = Math.max(mostFails, participant.roundTrapFails());
            }
            if (mostFails > 0) {
                double bonus = configDouble("scoring.solo.trap-bonus", 0.5);
                for (Participant participant : participants) {
                    if (participant.roundTrapFails() == mostFails) {
                        participant.addScore(bonus);
                    }
                }
            }
        }
        for (Participant participant : participants) {
            if (participant.roundCoinPoints() > 0) {
                participant.addScore(participant.roundCoinPoints());
            }
        }
    }

    private void addTeamScore(int teamId, double amount) {
        TeamState team = teams.get(teamId);
        if (team != null) {
            team.addScore(amount);
        }
    }

    // ---------- 胜负 ----------

    private void afterScoring() {
        List<Participant> winners = resolveWinner();
        if (winners != null) {
            endGame(winners);
            return;
        }
        if (round >= maxRounds) {
            broadcast("&6平局！进入加赛回合。");
        }
        nextRound();
    }

    /** 返回冠军玩家；null 表示还没有唯一赢家。 */
    private List<Participant> resolveWinner() {
        if (mode == GameMode.TEAM) {
            double best = Double.NEGATIVE_INFINITY;
            for (TeamState team : teams.values()) {
                if (!team.members().isEmpty()) {
                    best = Math.max(best, team.score());
                }
            }
            if (best == Double.NEGATIVE_INFINITY) {
                return null;
            }
            List<TeamState> top = new ArrayList<>();
            for (TeamState team : teams.values()) {
                if (!team.members().isEmpty() && Math.abs(team.score() - best) < 1.0E-6) {
                    top.add(team);
                }
            }
            boolean reached = best >= winScore;
            boolean finishedAtMax = round >= maxRounds;
            boolean force = round >= maxRounds + 10;
            if (top.size() == 1 && (reached || finishedAtMax)) {
                return membersOf(top.get(0));
            }
            if (force && !top.isEmpty()) {
                return membersOf(top.get(0));
            }
            return null;
        }

        double best = Double.NEGATIVE_INFINITY;
        for (Participant participant : participants) {
            best = Math.max(best, participant.score());
        }
        if (best == Double.NEGATIVE_INFINITY) {
            return null;
        }
        List<Participant> top = new ArrayList<>();
        for (Participant participant : participants) {
            if (Math.abs(participant.score() - best) < 1.0E-6) {
                top.add(participant);
            }
        }
        boolean reached = best >= winScore;
        boolean finishedAtMax = round >= maxRounds;
        boolean force = round >= maxRounds + 10;
        if (top.size() == 1 && (reached || finishedAtMax)) {
            return top;
        }
        if (force && !top.isEmpty()) {
            return List.of(top.get(0));
        }
        return null;
    }

    private List<Participant> membersOf(TeamState team) {
        List<Participant> out = new ArrayList<>();
        for (Participant participant : participants) {
            if (participant.teamId() == team.id()) {
                out.add(participant);
            }
        }
        return out;
    }

    private void nextRound() {
        sessions.clear();
        startRound();
    }

    private void endGame(List<Participant> winners) {
        phase = Phase.ENDED;
        String names = winners.stream().map(Participant::name).reduce((a, b) -> a + "、" + b).orElse("?");
        broadcast("");
        broadcast("&6&l游戏结束！冠军：" + names);
        for (Player player : onlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        cleanup();
        plugin.rooms().removeRoom(this);
    }

    private void abort(String reason) {
        broadcast("&c" + reason);
        cleanup();
        plugin.rooms().removeRoom(this);
    }

    public void leave(Player player, boolean toLobby) {
        removeParticipant(player);
        if (toLobby && player.isOnline()) {
            HubAPI.teleportToLobby(player);
        }
    }

    /** 移除机关、还原方块、恢复玩家状态。可重复调用。 */
    public void cleanup() {
        if (ending) {
            return;
        }
        ending = true;
        phase = Phase.ENDED;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (PlacementSession session : sessions.values()) {
            plugin.mechanismManager().clearPreview(session);
        }
        sessions.clear();
        plugin.mechanismManager().removeAll(this);
        mechanisms.clear();
        for (Participant participant : new ArrayList<>(participants)) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                player.closeInventory();
                restore(player);
                HubAPI.clearGame(player.getUniqueId());
                if (player.isOnline()) {
                    HubAPI.teleportToLobby(player);
                }
            }
        }
        updateArenaStatus();
    }

    public void shutdown() {
        cleanup();
    }

    private void restore(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] inventory = savedInventory.remove(uuid);
        ItemStack[] armor = savedArmor.remove(uuid);
        if (inventory != null) {
            player.getInventory().setContents(inventory);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setCanPickupItems(true);
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        }
        Scoreboard previous = savedBoards.remove(uuid);
        player.setScoreboard(previous != null
                ? previous
                : Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void clearGameInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void saveInventory(Player player) {
        UUID uuid = player.getUniqueId();
        if (savedInventory.containsKey(uuid)) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        savedInventory.put(uuid, copy);
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack[] armorCopy = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            armorCopy[i] = armor[i] == null ? null : armor[i].clone();
        }
        savedArmor.put(uuid, armorCopy);
    }

    private void setupCollisionBoard(Player player) {
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            noCollision = scoreboard.registerNewTeam("sn_nocollision");
            noCollision.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        savedBoards.putIfAbsent(player.getUniqueId(), player.getScoreboard());
        noCollision.addEntry(player.getName());
        player.setScoreboard(scoreboard);
    }

    // ---------- 机关承载 ----------

    public void addMechanism(MechanismInstance instance) {
        mechanisms.add(instance);
    }

    public void removeMechanism(MechanismInstance instance) {
        mechanisms.remove(instance);
    }

    public boolean occupiedByMechanism(Location location) {
        for (MechanismInstance instance : mechanisms) {
            if (instance.occupies(location)) {
                return true;
            }
        }
        return false;
    }

    /** 机关之间的碰撞保持关闭；玩家之间的碰撞由计分板团队控制。 */
    public void applyMechanismCollision(MechanismInstance instance) {
        // 骨架阶段：机关不与玩家发生物理碰撞，尖刺/箭矢使用自定义判定。
    }

    // ---------- 状态 ----------

    public void updateArenaStatus() {
        if (arena == null) {
            return;
        }
        GameState state = phase == Phase.WAITING ? GameState.WAITING : GameState.PLAYING;
        String extra = phase == Phase.WAITING
                ? null
                : "第 " + Math.max(1, round) + "/" + maxRounds + " 回合";
        HubAPI.setArena(GAME_NAME, arena.getName(), state, participants.size(), extra);
    }

    public String statusText() {
        return switch (phase) {
            case WAITING -> "等待中";
            case SELECTING -> "选择机关";
            case PLACING -> "摆放机关";
            case RUNNING -> "跑酷中";
            case SCORING -> "记分中";
            case ENDED -> "已结束";
        };
    }

    public String modeText() {
        return mode == GameMode.TEAM ? "团队 " + teamCount + " 队" : "个人";
    }

    // ---------- 心跳 ----------

    private void tick() {
        if (ending) {
            return;
        }
        switch (phase) {
            case SELECTING -> {
                phaseTicks--;
                if (allChosen()) {
                    if (autoCloseTicks < 0) {
                        int seconds = Math.max(0, plugin.getConfig()
                                .getInt("selection.auto-close-seconds", 2));
                        autoCloseTicks = seconds * 20;
                        if (autoCloseTicks == 0) {
                            finishSelection();
                            return;
                        }
                        broadcast("&a所有人都选好了，" + seconds + " 秒后开始摆放机关。");
                    }
                    autoCloseTicks--;
                    if (autoCloseTicks <= 0) {
                        finishSelection();
                        return;
                    }
                } else {
                    autoCloseTicks = -1;
                }
                if (phaseTicks <= 0) {
                    finishSelection();
                }
            }
            case PLACING -> {
                for (PlacementSession session : sessions.values()) {
                    if (!session.confirmed()) {
                        plugin.mechanismManager().updatePreview(session, arena);
                    }
                }
                phaseTicks--;
                if (phaseTicks % 20 == 0) {
                    for (PlacementSession session : sessions.values()) {
                        if (!session.locked() && !session.confirmed()) {
                            Msg.actionBar(session.player(),
                                    "&e飞到想要的位置，按左键/右键/滚轮锁定机关");
                        }
                    }
                }
                if (allPlaced()) {
                    broadcast("&a所有机关都已放置，提前开始跑酷！");
                    beginRunning();
                } else if (phaseTicks <= 0) {
                    beginRunning();
                }
            }
            case RUNNING -> {
                plugin.mechanismManager().tick(this);
                phaseTicks--;
                if (allDone() || phaseTicks <= 0) {
                    beginScoring();
                }
            }
            case SCORING -> {
                phaseTicks--;
                if (phaseTicks <= 0) {
                    afterScoring();
                }
            }
            default -> {
            }
        }
    }

    private int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }

    private double configDouble(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    public void broadcast(String message) {
        for (Player player : onlinePlayers()) {
            Msg.raw(player, message);
        }
    }

    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
