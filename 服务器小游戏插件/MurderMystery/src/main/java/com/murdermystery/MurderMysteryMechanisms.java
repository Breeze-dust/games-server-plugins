package com.murdermystery;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 读取 {@code plugins/MurderMystery/mechanisms.yml}（机关参数，手写、插件不改写）
 * 与 {@code mechanisms-data.yml}（已登记的拉杆 / 传送点，由 /mm teleporter 命令写入）。
 *
 * <p>机关数据按「机关类型 -> 场地名 -> 坐标」三级存储：登记时就绑定场地，
 * 使用时只会读取同一场地的数据，不同地图之间不会串。
 */
public final class MurderMysteryMechanisms {

    private static final String CONFIG_FILE = "mechanisms.yml";
    private static final String DATA_FILE = "mechanisms-data.yml";
    /** 传送器类型 id（以后新增拉杆机关就加自己的类型）。 */
    public static final String TELEPORTER = "teleporter";
    /** 老虎机类型 id。 */
    public static final String SLOT_MACHINE = "slot-machine";

    /** 售货机货物：道具 id + 权重。 */
    public record Good(String itemId, double weight) {
    }

    private final MurderMysteryPlugin plugin;
    /** 机关类型 -> 场地名 -> 拉杆坐标。 */
    private final Map<String, Map<String, List<Location>>> levers = new LinkedHashMap<>();
    /** 机关类型 -> 场地名 -> 落点坐标。 */
    private final Map<String, Map<String, List<Location>>> points = new LinkedHashMap<>();

    private boolean teleporterEnabled = true;
    private int teleporterCostGold = 5;
    private double teleporterCooldownSeconds = 5.0;
    private int teleporterMinDistance = 3;
    private String teleporterMessage = "&d传送器嗡的一声，你被传到了别处……";
    private String teleporterNoTargetMessage = "&c传送器还没有其他可用的传送点。";
    private String teleporterSound = "ENTITY_ENDERMAN_TELEPORT";

    // 售货机
    private boolean vendingEnabled = true;
    private int vendingCostGold = 2;
    private double vendingCooldownSeconds = 3.0;
    private final List<Good> vendingGoods = new ArrayList<>();

    // 老虎机
    private boolean slotMachineEnabled = true;
    private int slotCostGold = 2;
    private double slotCooldownSeconds = 1.0;
    private final List<MurderMysteryItems.EffectDef> slotEffects = new ArrayList<>();

    public MurderMysteryMechanisms(MurderMysteryPlugin plugin) {
        this.plugin = plugin;
        ensureType(TELEPORTER);
        ensureType(SLOT_MACHINE);
    }

    public void load() {
        load(loc -> null);
    }

    /**
     * @param arenaResolver 旧格式数据没有场地字段时，用这个把坐标解析回场地名；解析不到就丢弃
     */
    public void load(Function<Location, String> arenaResolver) {
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(configFile);
        teleporterEnabled = yml.getBoolean("teleporter.enabled", true);
        teleporterCostGold = Math.max(0, yml.getInt("teleporter.cost-gold", 5));
        teleporterCooldownSeconds = Math.max(0.0, yml.getDouble("teleporter.cooldown-seconds", 5.0));
        teleporterMinDistance = Math.max(0, yml.getInt("teleporter.min-distance", 3));
        teleporterMessage = yml.getString("teleporter.message", teleporterMessage);
        teleporterNoTargetMessage = yml.getString("teleporter.no-target-message", teleporterNoTargetMessage);
        teleporterSound = yml.getString("teleporter.sound", teleporterSound);
        loadVending(yml);
        loadSlotMachine(yml);
        loadData(arenaResolver);
    }

    private void loadVending(YamlConfiguration yml) {
        vendingEnabled = yml.getBoolean("vending.enabled", true);
        vendingCostGold = Math.max(0, yml.getInt("vending.cost-gold", 2));
        vendingCooldownSeconds = Math.max(0.0, yml.getDouble("vending.cooldown-seconds", 3.0));
        vendingGoods.clear();
        parseGoods(yml.getConfigurationSection("vending.goods"), vendingGoods);
        if (!vendingGoods.isEmpty()) {
            return;
        }
        // 兼容上一版：售货机配置还在 items.yml 的 vending 段
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            return;
        }
        YamlConfiguration itemsYml = YamlConfiguration.loadConfiguration(itemsFile);
        parseGoods(itemsYml.getConfigurationSection("vending.goods"), vendingGoods);
        if (!vendingGoods.isEmpty()) {
            plugin.getLogger().warning("售货机配置仍在 items.yml 的 vending 段；建议移到 mechanisms.yml，当前会继续兼容读取。");
        }
    }

    private void parseGoods(ConfigurationSection root, List<Good> out) {
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String itemId = sec.getString("item", key).toLowerCase(Locale.ROOT);
            double weight = Math.max(0.0, sec.getDouble("weight", 1.0));
            if (weight > 0) {
                out.add(new Good(itemId, weight));
            }
        }
    }

    private void loadSlotMachine(YamlConfiguration yml) {
        slotMachineEnabled = yml.getBoolean("slot-machine.enabled", true);
        slotCostGold = Math.max(0, yml.getInt("slot-machine.cost-gold", 2));
        slotCooldownSeconds = Math.max(0.0, yml.getDouble("slot-machine.cooldown-seconds", 1.0));
        slotEffects.clear();
        ConfigurationSection root = yml.getConfigurationSection("slot-machine.effects");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec != null) {
                slotEffects.add(new MurderMysteryItems.EffectDef(key, sec));
            }
        }
    }

    private void loadData(Function<Location, String> arenaResolver) {
        levers.clear();
        points.clear();
        ensureType(TELEPORTER);
        File dataFile = new File(plugin.getDataFolder(), DATA_FILE);
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        readArenaMap(yml, "levers", levers);
        readArenaMap(yml, "points", points);

        // 旧版本数据没有场地分组：按坐标是否落在某个场地里迁移一次
        boolean migrated = false;
        migrated |= migrateLegacyList(yml, "levers." + TELEPORTER, levers, TELEPORTER, arenaResolver);
        migrated |= migrateLegacyList(yml, TELEPORTER + ".levers", levers, TELEPORTER, arenaResolver);
        migrated |= migrateLegacyList(yml, "points." + TELEPORTER, points, TELEPORTER, arenaResolver);
        migrated |= migrateLegacyList(yml, TELEPORTER + ".points", points, TELEPORTER, arenaResolver);
        if (migrated) {
            plugin.getLogger().info("机关数据已按场地迁移（旧数据没有场地分组）。");
            saveData();
        }
    }

    private void readArenaMap(YamlConfiguration yml, String rootPath,
                              Map<String, Map<String, List<Location>>> target) {
        ConfigurationSection root = yml.getConfigurationSection(rootPath);
        if (root == null) {
            return;
        }
        for (String type : root.getKeys(false)) {
            ConfigurationSection arenaSection = root.getConfigurationSection(type);
            if (arenaSection == null) {
                continue; // 旧格式的列表，交给 migrateLegacyList
            }
            for (String arena : arenaSection.getKeys(false)) {
                List<Location> list = bucket(target, type, arena);
                list.addAll(readLocations(yml, rootPath + "." + type + "." + arena));
            }
        }
    }

    private boolean migrateLegacyList(YamlConfiguration yml, String path,
                                      Map<String, Map<String, List<Location>>> target,
                                      String type, Function<Location, String> arenaResolver) {
        List<Location> legacy = readLocations(yml, path);
        if (legacy.isEmpty()) {
            return false;
        }
        boolean migrated = false;
        for (Location loc : legacy) {
            String arena = arenaResolver == null ? null : arenaResolver.apply(loc);
            if (arena == null || arena.isBlank()) {
                plugin.getLogger().warning("机关旧数据 " + path + " 有坐标不属于任何场地，已丢弃：" + loc);
                continue;
            }
            bucket(target, type, arena).add(loc);
            migrated = true;
        }
        return migrated;
    }

    private List<Location> readLocations(YamlConfiguration yml, String path) {
        List<Location> out = new ArrayList<>();
        List<?> list = yml.getList(path);
        if (list == null) {
            return out;
        }
        for (Object o : list) {
            if (o instanceof Location loc) {
                out.add(loc);
            }
        }
        return out;
    }

    /** 登记/注销拉杆或传送点后立刻落盘（只写数据文件，不碰手写的 mechanisms.yml）。 */
    public void saveData() {
        YamlConfiguration yml = new YamlConfiguration();
        writeArenaMap(yml.createSection("levers"), levers);
        writeArenaMap(yml.createSection("points"), points);
        try {
            yml.save(new File(plugin.getDataFolder(), DATA_FILE));
        } catch (IOException ex) {
            plugin.getLogger().warning("保存机关数据失败：" + ex.getMessage());
        }
    }

    private void writeArenaMap(ConfigurationSection root, Map<String, Map<String, List<Location>>> source) {
        for (Map.Entry<String, Map<String, List<Location>>> typeEntry : source.entrySet()) {
            ConfigurationSection typeSection = root.createSection(typeEntry.getKey());
            for (Map.Entry<String, List<Location>> arenaEntry : typeEntry.getValue().entrySet()) {
                if (!arenaEntry.getValue().isEmpty()) {
                    typeSection.set(arenaEntry.getKey(), new ArrayList<>(arenaEntry.getValue()));
                }
            }
        }
    }

    private void ensureType(String type) {
        levers.computeIfAbsent(type, k -> new LinkedHashMap<>());
        points.computeIfAbsent(type, k -> new LinkedHashMap<>());
    }

    private List<Location> bucket(Map<String, Map<String, List<Location>>> target, String type, String arena) {
        String key = type.toLowerCase(Locale.ROOT);
        return target.computeIfAbsent(key, k -> new LinkedHashMap<>())
                .computeIfAbsent(arena, k -> new ArrayList<>());
    }

    public boolean teleporterEnabled() {
        return teleporterEnabled;
    }

    /** 每次使用传送器扣除的金锭数量（0 = 免费）。 */
    public int teleporterCostGold() {
        return teleporterCostGold;
    }

    public double teleporterCooldownSeconds() {
        return teleporterCooldownSeconds;
    }

    public int teleporterMinDistance() {
        return teleporterMinDistance;
    }

    public String teleporterMessage() {
        return teleporterMessage;
    }

    public String teleporterNoTargetMessage() {
        return teleporterNoTargetMessage;
    }

    public String teleporterSound() {
        return teleporterSound;
    }

    public boolean vendingEnabled() {
        return vendingEnabled;
    }

    /** 每次抽货扣除的金锭数量（0 = 免费）。 */
    public int vendingCostGold() {
        return vendingCostGold;
    }

    public double vendingCooldownSeconds() {
        return vendingCooldownSeconds;
    }

    /** 按权重抽一件售货机货物；allowed 过滤掉当前玩家不能拿的货物（如杀手专属）。 */
    public Good pickGood(Random random, Predicate<String> allowed) {
        List<Good> pool = new ArrayList<>();
        double total = 0.0;
        for (Good good : vendingGoods) {
            if (allowed != null && !allowed.test(good.itemId())) {
                continue;
            }
            pool.add(good);
            total += good.weight();
        }
        if (pool.isEmpty() || total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        Good last = pool.get(pool.size() - 1);
        for (Good good : pool) {
            roll -= good.weight();
            if (roll <= 0) {
                return good;
            }
        }
        return last;
    }

    public boolean slotMachineEnabled() {
        return slotMachineEnabled;
    }

    /** 每次启动老虎机扣除的金锭数量（0 = 免费）。 */
    public int slotCostGold() {
        return slotCostGold;
    }

    public double slotCooldownSeconds() {
        return slotCooldownSeconds;
    }

    /** 某个场地的老虎机拉杆；arena 为 null 时返回全部场地的。 */
    public List<Location> slotMachineLevers(String arena) {
        return flatten(levers.get(SLOT_MACHINE), arena);
    }

    /** 按权重抽一个老虎机效果；全部关闭/权重为 0 时返回 null。 */
    public MurderMysteryItems.EffectDef pickSlotEffect(Random random) {
        List<MurderMysteryItems.EffectDef> pool = new ArrayList<>();
        double total = 0.0;
        for (MurderMysteryItems.EffectDef effect : slotEffects) {
            if (!effect.enabled() || effect.weight() <= 0) {
                continue;
            }
            pool.add(effect);
            total += effect.weight();
        }
        if (pool.isEmpty() || total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        MurderMysteryItems.EffectDef last = pool.get(pool.size() - 1);
        for (MurderMysteryItems.EffectDef effect : pool) {
            roll -= effect.weight();
            if (roll <= 0) {
                return effect;
            }
        }
        return last;
    }

    /** 某个场地的传送器拉杆；arena 为 null 时返回所有场地的。 */
    public List<Location> teleporterLevers(String arena) {
        return flatten(levers.get(TELEPORTER), arena);
    }

    /** 某个场地的传送落点；arena 为 null 时返回所有场地的。 */
    public List<Location> teleporterPoints(String arena) {
        return flatten(points.get(TELEPORTER), arena);
    }

    /** 数据里出现过的场地名（按登记顺序）。 */
    public List<String> teleporterArenaNames() {
        List<String> names = new ArrayList<>();
        for (Map<String, List<Location>> byArena : List.of(
                levers.getOrDefault(TELEPORTER, Map.of()),
                points.getOrDefault(TELEPORTER, Map.of()))) {
            for (String arena : byArena.keySet()) {
                if (!names.contains(arena)) {
                    names.add(arena);
                }
            }
        }
        return names;
    }

    private List<Location> flatten(Map<String, List<Location>> byArena, String arena) {
        List<Location> out = new ArrayList<>();
        if (byArena == null) {
            return out;
        }
        if (arena != null) {
            out.addAll(byArena.getOrDefault(arena, List.of()));
            return out;
        }
        for (List<Location> list : byArena.values()) {
            out.addAll(list);
        }
        return out;
    }

    /** 这个拉杆属于哪种机关（teleporter / 以后新增的类型）；没登记返回 null。 */
    public String leverTypeAt(Location loc) {
        for (Map.Entry<String, Map<String, List<Location>>> typeEntry : levers.entrySet()) {
            for (List<Location> list : typeEntry.getValue().values()) {
                for (Location lever : list) {
                    if (sameBlock(lever, loc)) {
                        return typeEntry.getKey();
                    }
                }
            }
        }
        return null;
    }

    public boolean isLeverOfType(String type, Location loc) {
        return type != null && type.equalsIgnoreCase(leverTypeAt(loc));
    }

    /** 这个拉杆登记在哪个场地；没登记返回 null。 */
    public String leverArenaAt(Location loc) {
        for (Map<String, List<Location>> byArena : levers.values()) {
            for (Map.Entry<String, List<Location>> entry : byArena.entrySet()) {
                for (Location lever : entry.getValue()) {
                    if (sameBlock(lever, loc)) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    /** 登记一根拉杆：绑定到指定场地和机关类型。 */
    public boolean addLever(String type, String arena, Location loc) {
        if (type == null || type.isBlank() || arena == null || arena.isBlank()) {
            return false;
        }
        if (leverTypeAt(loc) != null) {
            return false; // 一根拉杆只能绑定一种机关
        }
        ensureType(type.toLowerCase(Locale.ROOT));
        bucket(levers, type, arena).add(loc);
        saveData();
        return true;
    }

    /** 注销拉杆（不管它属于哪种机关、哪个场地）。 */
    public boolean removeLever(Location loc) {
        for (Map<String, List<Location>> byArena : levers.values()) {
            for (List<Location> list : byArena.values()) {
                for (int i = 0; i < list.size(); i++) {
                    if (sameBlock(list.get(i), loc)) {
                        list.remove(i);
                        saveData();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void addPoint(String type, String arena, Location loc) {
        if (type == null || type.isBlank() || arena == null || arena.isBlank()) {
            return;
        }
        ensureType(type.toLowerCase(Locale.ROOT));
        bucket(points, type, arena).add(loc);
        saveData();
    }

    /** 按 1 开始的序号删除某场地的传送点；越界返回 false。 */
    public boolean removePoint(String type, String arena, int indexOneBased) {
        Map<String, List<Location>> byArena = points.get(type.toLowerCase(Locale.ROOT));
        if (byArena == null || arena == null) {
            return false;
        }
        List<Location> list = byArena.get(arena);
        int i = indexOneBased - 1;
        if (list == null || i < 0 || i >= list.size()) {
            return false;
        }
        list.remove(i);
        saveData();
        return true;
    }

    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
