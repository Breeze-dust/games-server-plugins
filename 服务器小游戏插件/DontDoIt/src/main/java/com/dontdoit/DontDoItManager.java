package com.dontdoit;

import com.dontdoit.criteria.CriterionLibrary;
import com.dontdoit.menu.SettingsMenu;
import com.dontdoit.menu.TeamSelectMenu;
import com.hub.api.GameState;
import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/** 插件总控：配置、场地、等待室道具、对局生命周期、战绩。 */
public final class DontDoItManager {

    public static final String GAME = "不要做挑战";
    public static final String STATS_NAMESPACE = "dontdoit";

    private final DontDoItPlugin plugin;
    private final Map<String, DontDoItArena> arenas = new LinkedHashMap<>();
    private final Map<String, Game> games = new LinkedHashMap<>();
    private final Map<UUID, Game> playerGame = new LinkedHashMap<>();
    private final CriterionLibrary library = new CriterionLibrary();
    private final Sidebar sidebar;
    private final Random random = new Random();

    private YamlConfiguration stats;

    private int minTeams = 2;
    private int minPlayers = 2;
    private int maxTeams = 16;
    private int maxPlayersPerTeam = 4;
    private boolean soloMode = true;
    private int lives = 15;
    private int criteriaChangeSeconds = 120;
    private int startCountdownSeconds = 5;
    private int guessCooldownSeconds = 120;
    private int guessSuccessLives = 1;
    private int guessFailedLives = 1;
    private int diamondThreshold = 10;
    private int bedrockDepth = 11;
    private int yLevelOffsetGround = 1;
    private int yLevelOffsetOnAir = 14;
    private int worldBorderPadding = 4;
    private boolean allowUnbalancedTeams;
    private boolean customCriteriaEnabled = true;
    private boolean guessCriteriaEnabled = true;
    private boolean diamondBehaviorEnabled = true;
    private boolean nightVision = true;
    private boolean useWorldBorder = true;
    private boolean yLevelCriteriaEnabled = true;
    private boolean restoreMap = true;
    private boolean pvpEnabled = true;
    private boolean mobSpawningEnabled = true;
    private boolean keepInventoryOnDeath = false;
    private boolean broadcastServerWide = false;
    private int restoreTopOffset = 16;
    private boolean restoreToMaxHeight = true;
    private String diamondBehavior = "reduce_others_life";
    private Difficulty gameDifficulty = Difficulty.NORMAL;
    private List<ArenaGenerator.OreRule> oreRules = List.of();

    public DontDoItManager(DontDoItPlugin plugin) {
        this.plugin = plugin;
        this.sidebar = new Sidebar(plugin);
    }

    // ================== 加载 ==================

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        readConfig();
        copyCriteria();
        library.load(new File(plugin.getDataFolder(), "criteria.yml"));
        if (library.isEmpty()) {
            plugin.getLogger().warning("criteria.yml 没有读到任何词条，请检查文件格式。");
        } else {
            plugin.getLogger().info("已加载 " + library.all().size() + " 条词条。");
        }

        arenas.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arenas");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                DontDoItArena arena = new DontDoItArena(name);
                arena.load(section.getConfigurationSection(name));
                arenas.put(name, arena);
            }
        }
        if (arenas.isEmpty()) {
            DontDoItArena arena = new DontDoItArena("default");
            arenas.put("default", arena);
            saveArenas();
        }

        games.clear();
        playerGame.clear();
        for (String name : new ArrayList<>(arenas.keySet())) {
            Game game = new Game(this, arenas.get(name));
            games.put(name, game);
            reportArena(game);
        }

        restoreLeftoverWorldRules();
        stats = PlayerStats.load(STATS_NAMESPACE, new File(plugin.getDataFolder(), "data.yml"));
    }

    private void copyCriteria() {
        File target = new File(plugin.getDataFolder(), "criteria.yml");
        if (target.isFile()) {
            return;
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                return;
            }
            try (var in = plugin.getResource("criteria.yml")) {
                if (in != null) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法写出 criteria.yml: " + e.getMessage());
        }
    }

    private void readConfig() {
        FileConfiguration c = plugin.getConfig();
        minTeams = Math.max(2, c.getInt("min-teams", 2));
        minPlayers = Math.max(2, c.getInt("min-players", 2));
        maxTeams = Math.min(48, Math.max(2, c.getInt("max-teams", 16)));
        maxPlayersPerTeam = Math.max(1, c.getInt("max-players-per-team", 4));
        soloMode = c.getBoolean("solo-mode", true);
        allowUnbalancedTeams = c.getBoolean("allow-unbalanced-teams", false);
        lives = Math.max(1, c.getInt("lives", 15));
        criteriaChangeSeconds = Math.max(10, c.getInt("criteria-change-seconds", 120));
        startCountdownSeconds = Math.max(0, c.getInt("start-countdown-seconds", 5));
        customCriteriaEnabled = c.getBoolean("custom-criteria-enabled", true);
        guessCriteriaEnabled = c.getBoolean("guess-criteria-enabled", true);
        guessCooldownSeconds = Math.max(0, c.getInt("guess-cooldown-seconds", 120));
        guessSuccessLives = Math.max(1, c.getInt("guess-success-lives", 1));
        guessFailedLives = Math.max(1, c.getInt("guess-failed-lives", 1));
        diamondBehaviorEnabled = c.getBoolean("diamond-behavior-enabled", true);
        diamondBehavior = c.getString("diamond-behavior", "reduce_others_life");
        diamondThreshold = Math.max(1, c.getInt("diamond-behavior-disabled-threshold", 10));
        nightVision = c.getBoolean("night-vision", true);
        useWorldBorder = c.getBoolean("use-world-border", true);
        worldBorderPadding = Math.max(0, c.getInt("world-border-padding", 4));
        yLevelCriteriaEnabled = c.getBoolean("enable-y-level-criteria", true);
        restoreMap = c.getBoolean("restore-map", true);
        pvpEnabled = c.getBoolean("pvp-enabled", true);
        mobSpawningEnabled = c.getBoolean("mob-spawning-enabled", true);
        keepInventoryOnDeath = c.getBoolean("keep-inventory-on-death", false);
        broadcastServerWide = c.getBoolean("broadcast-server-wide", false);
        restoreTopOffset = Math.max(0, c.getInt("restore-top-offset", 16));
        restoreToMaxHeight = c.getBoolean("restore-to-max-height", true);
        yLevelOffsetGround = c.getInt("y-level-offset-ground", 1);
        yLevelOffsetOnAir = c.getInt("y-level-offset-on-air", 14);
        bedrockDepth = Math.max(2, c.getInt("bedrock-depth", 11));
        try {
            gameDifficulty = Difficulty.valueOf(c.getString("game-difficulty", "NORMAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            gameDifficulty = Difficulty.NORMAL;
        }
        List<ArenaGenerator.OreRule> rules = new ArrayList<>();
        addOre(rules, Material.ANDESITE, c.getMapList("andesite-generation"));
        addOre(rules, Material.COAL_ORE, c.getMapList("coal-ore-generation"));
        addOre(rules, Material.IRON_ORE, c.getMapList("iron-ore-generation"));
        addOre(rules, Material.DIAMOND_ORE, c.getMapList("diamond-ore-generation"));
        oreRules = List.copyOf(rules);
    }

    private void addOre(List<ArenaGenerator.OreRule> rules, Material material, List<Map<?, ?>> list) {
        for (Map<?, ?> map : list) {
            Object depth = map.get("depth");
            Object ratio = map.get("ratio");
            if (depth == null || ratio == null) {
                continue;
            }
            String[] parts = String.valueOf(depth).trim().split("\\.\\.");
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
                double r = Double.parseDouble(String.valueOf(ratio).trim());
                rules.add(new ArenaGenerator.OreRule(material, min, max, r));
            } catch (NumberFormatException ignored) {
                // 跳过写错的条目
            }
        }
    }

    public void saveArenas() {
        FileConfiguration c = plugin.getConfig();
        c.set("arenas", null);
        for (Map.Entry<String, DontDoItArena> e : arenas.entrySet()) {
            ConfigurationSection section = c.createSection("arenas." + e.getKey());
            e.getValue().save(section);
        }
        plugin.saveConfig();
    }

    public void saveSettings() {
        FileConfiguration c = plugin.getConfig();
        c.set("custom-criteria-enabled", customCriteriaEnabled);
        c.set("guess-criteria-enabled", guessCriteriaEnabled);
        c.set("diamond-behavior-enabled", diamondBehaviorEnabled);
        c.set("diamond-behavior", diamondBehavior);
        c.set("night-vision", nightVision);
        c.set("use-world-border", useWorldBorder);
        c.set("enable-y-level-criteria", yLevelCriteriaEnabled);
        c.set("restore-map", restoreMap);
        plugin.saveConfig();
    }

    public void shutdown() {
        for (Game game : games.values()) {
            game.shutdown();
        }
        sidebar.hideAll();
        saveStats();
    }

    // ================== 对外访问 ==================

    public DontDoItPlugin plugin() {
        return plugin;
    }

    public org.bukkit.plugin.Plugin loggerPlugin() {
        return plugin;
    }

    public java.util.logging.Logger getLogger() {
        return plugin.getLogger();
    }

    public String gameName() {
        return GAME;
    }

    public CriterionLibrary library() {
        return library;
    }

    public Sidebar sidebar() {
        return sidebar;
    }

    public Random random() {
        return random;
    }

    public int minTeams() {
        return minTeams;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxTeams() {
        return maxTeams;
    }

    public int maxPlayersPerTeam() {
        return maxPlayersPerTeam;
    }

    /** 单人模式下每队上限固定为 1。 */
    public int effectiveMaxPerTeam() {
        return soloMode ? 1 : maxPlayersPerTeam;
    }

    public boolean soloMode() {
        return soloMode;
    }

    public boolean allowUnbalancedTeams() {
        return allowUnbalancedTeams;
    }

    public int lives() {
        return lives;
    }

    public int criteriaChangeSeconds() {
        return criteriaChangeSeconds;
    }

    public int startCountdownSeconds() {
        return startCountdownSeconds;
    }

    public boolean customCriteriaEnabled() {
        return customCriteriaEnabled;
    }

    public void setCustomCriteriaEnabled(boolean value) {
        customCriteriaEnabled = value;
        saveSettings();
    }

    public boolean guessCriteriaEnabled() {
        return guessCriteriaEnabled;
    }

    public void setGuessCriteriaEnabled(boolean value) {
        guessCriteriaEnabled = value;
        saveSettings();
    }

    public int guessCooldownSeconds() {
        return guessCooldownSeconds;
    }

    public int guessSuccessLives() {
        return guessSuccessLives;
    }

    public int guessFailedLives() {
        return guessFailedLives;
    }

    public boolean diamondBehaviorEnabled() {
        return diamondBehaviorEnabled;
    }

    public void setDiamondBehaviorEnabled(boolean value) {
        diamondBehaviorEnabled = value;
        saveSettings();
    }

    public String diamondBehavior() {
        return diamondBehavior;
    }

    public void setDiamondBehavior(String value) {
        diamondBehavior = "add_self_life".equalsIgnoreCase(value) ? "add_self_life" : "reduce_others_life";
        saveSettings();
    }

    public int diamondThreshold() {
        return diamondThreshold;
    }

    public boolean nightVision() {
        return nightVision;
    }

    public void setNightVision(boolean value) {
        nightVision = value;
        saveSettings();
    }

    public boolean useWorldBorder() {
        return useWorldBorder;
    }

    public void setUseWorldBorder(boolean value) {
        useWorldBorder = value;
        saveSettings();
    }

    public boolean yLevelCriteriaEnabled() {
        return yLevelCriteriaEnabled;
    }

    public void setYLevelCriteriaEnabled(boolean value) {
        yLevelCriteriaEnabled = value;
        saveSettings();
    }

    public boolean pvpEnabled() {
        return pvpEnabled;
    }

    public boolean mobSpawningEnabled() {
        return mobSpawningEnabled;
    }

    public boolean keepInventoryOnDeath() {
        return keepInventoryOnDeath;
    }

    public boolean broadcastServerWide() {
        return broadcastServerWide;
    }

    public boolean restoreMap() {
        return restoreMap;
    }

    public void setRestoreMap(boolean value) {
        restoreMap = value;
        saveSettings();
    }

    public int restoreTopOffset() {
        return restoreTopOffset;
    }

    public boolean restoreToMaxHeight() {
        return restoreToMaxHeight;
    }

    /** 地图基线的顶部 Y：默认一直记录到世界最高处。 */
    public int restoreTopFor(org.bukkit.World world, int centerY, int stoneY) {
        if (restoreToMaxHeight) {
            return world.getMaxHeight() - 1;
        }
        return Math.min(world.getMaxHeight() - 1,
                Math.max(centerY, stoneY) + restoreTopOffset);
    }

    public java.io.File baselineFile(DontDoItArena arena) {
        String safe = arena.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        return new java.io.File(plugin.getDataFolder(), "arena-" + safe + ".baseline");
    }

    /** 读取场地基线；没有或损坏时返回 null。 */
    public MapBaseline loadBaseline(DontDoItArena arena) {
        java.io.File file = baselineFile(arena);
        if (!file.isFile()) {
            return null;
        }
        try {
            return MapBaseline.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning("读取地图快照失败（" + file.getName() + "）：" + e.getMessage());
            return null;
        }
    }

    private java.io.File worldRulesFile(DontDoItArena arena) {
        String safe = arena.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        return new java.io.File(plugin.getDataFolder(), "applied-rules-" + safe + ".yml");
    }

    /** 对局开始时把世界原规则落盘，防止服务器中途崩溃/重启后 PVP 等标志留在对局值上。 */
    public void saveWorldRules(DontDoItArena arena, org.bukkit.World world,
                               boolean pvp, boolean mobSpawning, boolean keepInventory) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("world", world.getName());
        yml.set("pvp", pvp);
        yml.set("mob-spawning", mobSpawning);
        yml.set("keep-inventory", keepInventory);
        try {
            yml.save(worldRulesFile(arena));
        } catch (IOException e) {
            plugin.getLogger().warning("保存世界规则记录失败：" + e.getMessage());
        }
    }

    public void clearWorldRules(DontDoItArena arena) {
        java.io.File file = worldRulesFile(arena);
        if (file.isFile() && !file.delete()) {
            plugin.getLogger().warning("删除世界规则记录失败：" + file.getName());
        }
    }

    /** 启动时如果发现上次对局没走完还原流程，把世界规则恢复回去。 */
    public void restoreLeftoverWorldRules() {
        for (DontDoItArena arena : arenas.values()) {
            java.io.File file = worldRulesFile(arena);
            if (!file.isFile()) {
                continue;
            }
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                String worldName = yml.getString("world", arena.getWorldName());
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world != null) {
                    world.setPVP(yml.getBoolean("pvp", world.getPVP()));
                    world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING,
                            yml.getBoolean("mob-spawning", true));
                    world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY,
                            yml.getBoolean("keep-inventory", false));
                    plugin.getLogger().info("已恢复场地 " + arena.getName() + " 上次未还原的世界规则（"
                            + worldName + "）");
                }
                if (!file.delete()) {
                    plugin.getLogger().warning("删除世界规则记录失败：" + file.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("恢复世界规则记录失败：" + e.getMessage());
            }
        }
    }

    public void saveBaseline(DontDoItArena arena, MapBaseline baseline) {
        try {
            baseline.save(baselineFile(arena));
        } catch (Exception e) {
            plugin.getLogger().warning("保存地图快照失败：" + e.getMessage());
        }
    }

    public int yLevelOffsetGround() {
        return yLevelOffsetGround;
    }

    public int yLevelOffsetOnAir() {
        return yLevelOffsetOnAir;
    }

    public int worldBorderPadding() {
        return worldBorderPadding;
    }

    public int bedrockDepth() {
        return bedrockDepth;
    }

    public List<ArenaGenerator.OreRule> oreRules() {
        return oreRules;
    }

    public Difficulty gameDifficulty() {
        return gameDifficulty;
    }

    // ================== 场地 / 对局 ==================

    public Map<String, DontDoItArena> arenas() {
        return arenas;
    }

    public DontDoItArena arena(String name) {
        return arenas.get(name);
    }

    public Game game(String name) {
        return games.get(name);
    }

    public Map<String, Game> games() {
        return games;
    }

    public Game gameOf(Player player) {
        return player == null ? null : playerGame.get(player.getUniqueId());
    }

    public boolean hasArena(String name) {
        return arenas.containsKey(name);
    }

    public String createArena(String name) {
        if (arenas.containsKey(name)) {
            return "场地已存在：" + name;
        }
        DontDoItArena arena = new DontDoItArena(name);
        arenas.put(name, arena);
        Game game = new Game(this, arena);
        games.put(name, game);
        saveArenas();
        reportArena(game);
        return null;
    }

    public void removeArena(String name) {
        Game game = games.remove(name);
        DontDoItArena arena = arenas.get(name);
        if (game != null) {
            // 先把人送回主城、还原地图与世界规则，再删场地
            game.reset();
            game.shutdown();
        }
        if (arena != null) {
            java.io.File baseline = baselineFile(arena);
            if (baseline.isFile() && !baseline.delete()) {
                plugin.getLogger().warning("删除地图基线失败：" + baseline.getName());
            }
        }
        arenas.remove(name);
        saveArenas();
        HubAPI.removeArena(GAME, name);
    }

    public void reload() {
        load();
    }

    public DontDoItArena defaultArena() {
        if (arenas.isEmpty()) {
            return null;
        }
        return arenas.values().iterator().next();
    }

    public void reportArena(Game game) {
        if (game == null || game.arena() == null) {
            return;
        }
        HubAPI.setArena(GAME, game.arena().getName(), game.hubState(),
                game.playerCount() + game.playingCount(), game.hubExtra());
    }

    // ================== 加入 / 离开 ==================

    public String join(Player player, String arenaName) {
        DontDoItArena arena = arenaName == null ? defaultArena() : arenas.get(arenaName);
        if (arena == null) {
            return "场地不存在：" + arenaName;
        }
        Game game = games.get(arena.getName());
        if (game == null) {
            return "场地未加载。";
        }
        if (game.state() != Game.State.WAITING) {
            return "这个场地正在游戏中，等下一局。";
        }
        Game current = playerGame.get(player.getUniqueId());
        if (current != null && current != game) {
            leave(player, false);
        }
        Location lobby = arena.lobby();
        if (lobby == null) {
            return "场地等待点未配置（/ddi setlobby）。";
        }
        playerGame.put(player.getUniqueId(), game);
        game.addWaiting(player);
        player.teleport(lobby);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        giveWaitItems(player, game);
        HubAPI.setGame(player.getUniqueId(), GAME);
        HubAPI.announceJoin(plugin.getName(), player.getName(), arena.getName());
        game.onJoinMessage(player);
        reportArena(game);
        return null;
    }

    public void leave(Player player, boolean silent) {
        Game game = playerGame.remove(player.getUniqueId());
        if (game == null) {
            return;
        }
        game.leave(player, silent);
        if (!silent) {
            player.getInventory().clear();
            HubAPI.clearGame(player.getUniqueId());
            HubAPI.teleportToLobby(player);
        }
        reportArena(game);
    }

    public void unmapPlayer(Player player) {
        if (player != null) {
            playerGame.remove(player.getUniqueId());
        }
    }

    public void forget(Game game) {
        for (Team t : game.teams()) {
            for (Player p : t.members()) {
                playerGame.remove(p.getUniqueId());
            }
        }
        for (UUID id : new ArrayList<>(game.waitingIds())) {
            playerGame.remove(id);
        }
        reportArena(game);
    }

    // ================== 等待室道具 ==================

    public void giveWaitItems(Player player, Game game) {
        player.getInventory().clear();
        player.getInventory().setItem(1, TeamSelectMenu.item(this, game, player));
        game.countdown().giveReadyItem(player);
        if (customCriteriaEnabled) {
            player.getInventory().setItem(3, customCriteriaBook());
        }
        if (player.isOp() || player.hasPermission("ddi.admin")) {
            player.getInventory().setItem(4, startPaper());
        }
        if (!com.hub.util.WaitCountdown.isGloballySuppressed()) {
            ItemStack bed = new ItemStack(Material.RED_BED);
            ItemMeta meta = bed.getItemMeta();
            if (meta != null) {
                meta.displayName(Chat.noItalic(Chat.color("&c退出游戏 &7(右键)")));
                bed.setItemMeta(meta);
            }
            player.getInventory().setItem(8, bed);
        }
    }

    public void refreshWaitItems(Player player) {
        Game game = gameOf(player);
        if (game == null) {
            return;
        }
        player.getInventory().setItem(1, TeamSelectMenu.item(this, game, player));
    }

    private ItemStack startPaper() {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&a&l开始游戏 &7(右键)")));
            paper.setItemMeta(meta);
        }
        return paper;
    }

    private ItemStack customCriteriaBook() {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color("&d自定义词条 &7(签名后生效)")));
            meta.lore(List.of(Chat.color("&7在第一页写下你队伍的禁止事项"),
                    Chat.color("&7签名为《词条》后生效")));
            book.setItemMeta(meta);
        }
        return book;
    }

    public boolean isStartPaper(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || item.getItemMeta() == null) {
            return false;
        }
        if (!item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String name = Chat.toLegacy(item.getItemMeta().displayName());
        return name.contains("开始游戏");
    }

    public boolean isLeaveBed(ItemStack item) {
        return item != null && item.getType() == Material.RED_BED;
    }

    public boolean isTeamMenuItem(ItemStack item) {
        if (item == null || item.getType() != Material.WHITE_WOOL || item.getItemMeta() == null) {
            return false;
        }
        if (!item.getItemMeta().hasDisplayName()) {
            return false;
        }
        return Chat.toLegacy(item.getItemMeta().displayName()).contains("选队菜单");
    }

    // ================== 自定义词条 ==================

    public String setCustomCriteria(Player player, String text) {
        Game game = gameOf(player);
        if (game == null) {
            return "你不在等待室。";
        }
        if (!customCriteriaEnabled) {
            return "自定义词条未开启。";
        }
        if (game.state() != Game.State.WAITING) {
            return "只能在开始前设置自定义词条。";
        }
        Team team = game.teamOf(player);
        if (team == null) {
            return "请先用选队菜单选择队伍。";
        }
        String clean = text == null ? "" : text.trim();
        if (clean.length() > 40) {
            clean = clean.substring(0, 40);
        }
        team.setCustomCriteria(clean.isEmpty() ? null : clean);
        if (clean.isEmpty()) {
            game.broadcastGame("&7" + player.getName() + " 取消了 " + team.displayName() + " &7的自定义词条。");
        } else {
            game.broadcastGame("&f" + player.getName() + " &7把 " + team.displayName()
                    + " &7的自定义词条设置为：&f" + clean);
        }
        return null;
    }

    // ================== 战绩 ==================

    public void recordWin(Team team) {
        if (stats == null) {
            return;
        }
        for (Player p : team.members()) {
            String path = "firsts." + p.getUniqueId();
            stats.set(path, stats.getInt(path, 0) + 1);
            stats.set("names." + p.getUniqueId(), p.getName());
        }
        saveStats();
    }

    public int winsOf(UUID id) {
        return stats == null ? 0 : stats.getInt("firsts." + id, 0);
    }

    private void saveStats() {
        if (stats == null) {
            return;
        }
        try {
            PlayerStats.save(STATS_NAMESPACE, stats);
        } catch (IOException e) {
            plugin.getLogger().warning("保存玩家记录失败: " + e.getMessage());
        }
    }

    // ================== 工具 ==================

    public void eachGame(Consumer<Game> action) {
        for (Game game : games.values()) {
            action.accept(game);
        }
    }

    public SettingsMenu settingsMenu() {
        return new SettingsMenu(this);
    }

    public static GameState hubState(Game game) {
        return game.state() == Game.State.RUNNING ? GameState.PLAYING : GameState.WAITING;
    }

    public boolean isInGameWorld(Player player) {
        Game game = gameOf(player);
        return game != null && game.arena().world() != null
                && player.getWorld().equals(game.arena().world());
    }

    public World worldOf(Game game) {
        return game == null || game.arena() == null ? null : game.arena().world();
    }

    public void broadcastArena(Game game, String message) {
        if (game != null) {
            game.broadcastGame(message);
        }
    }

    public int playerCount(Game game) {
        return game == null ? 0 : game.playerCount();
    }
}
