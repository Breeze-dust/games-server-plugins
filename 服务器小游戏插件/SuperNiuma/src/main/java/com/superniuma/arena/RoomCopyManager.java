package com.superniuma.arena;

import com.superniuma.SuperNiumaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 地图副本：开房时把蓝本 pos1/pos2 区域复制到 config.yml 指定的世界，
 * 每个房间占一个互不重叠的网格槽位；房间结束时清空该区域。
 */
public final class RoomCopyManager {

    /** 复制失败，消息直接给玩家看。 */
    public static final class CopyException extends Exception {
        public CopyException(String message) {
            super(message);
        }
    }

    private static final String FILE = "instances.yml";
    private static final BlockData AIR = Material.AIR.createBlockData();

    /** 世界还没加载、留到下次加载后再清的残留副本。 */
    private record Stale(String roomId, String arenaName, String worldName, int slot,
                         int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private final SuperNiumaPlugin plugin;
    private final Map<String, RoomCopy> active = new LinkedHashMap<>();
    private final TreeSet<Integer> usedSlots = new TreeSet<>();
    private final List<Stale> stale = new ArrayList<>();

    public RoomCopyManager(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 启动清理 ----------

    /** 清理上次崩溃/强制关服留下的副本。 */
    public void load() {
        active.clear();
        usedSlots.clear();
        stale.clear();
        File file = file();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("instances");
        int cleared = 0;
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                Stale record = new Stale(key, sec.getString("arena", "?"), sec.getString("world", ""),
                        sec.getInt("slot"), sec.getInt("min-x"), sec.getInt("min-y"), sec.getInt("min-z"),
                        sec.getInt("max-x"), sec.getInt("max-y"), sec.getInt("max-z"));
                World world = record.worldName().isEmpty() ? null : Bukkit.getWorld(record.worldName());
                if (world == null) {
                    stale.add(record);
                    continue;
                }
                clearRegion(world, record.minX(), record.minY(), record.minZ(),
                        record.maxX(), record.maxY(), record.maxZ());
                cleared++;
            }
        }
        save();
        if (cleared > 0) {
            plugin.getLogger().info("已清理 " + cleared + " 个上次遗留的地图副本。");
        }
        if (!stale.isEmpty()) {
            plugin.getLogger().warning("有 " + stale.size() + " 个副本的所在世界尚未加载，稍后加载时再清理。");
        }
    }

    // ---------- 分配 / 释放 ----------

    public RoomCopy allocate(String roomId, Arena blueprint) throws CopyException {
        if (!Bukkit.isPrimaryThread()) {
            throw new CopyException("复制地图必须在主线程执行。");
        }
        if (blueprint == null || blueprint.getBuildMin() == null || blueprint.getBuildMax() == null) {
            throw new CopyException("地图还没有用 pos1/pos2 圈定范围。");
        }
        World source = blueprint.getWorld();
        if (source == null) {
            throw new CopyException("蓝图世界 " + blueprint.getWorldName() + " 未加载。");
        }
        Location buildMin = blueprint.getBuildMin();
        Location buildMax = blueprint.getBuildMax();
        int sizeX = buildMax.getBlockX() - buildMin.getBlockX() + 1;
        int sizeY = buildMax.getBlockY() - buildMin.getBlockY() + 1;
        int sizeZ = buildMax.getBlockZ() - buildMin.getBlockZ() + 1;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new CopyException("pos1/pos2 圈出的区域为空。");
        }
        long volume = (long) sizeX * sizeY * sizeZ;
        int maxBlocks = Math.max(1, plugin.getConfig().getInt("copy.max-blocks", 500000));
        if (volume > maxBlocks) {
            throw new CopyException("地图区域太大（" + volume + " 格，上限 " + maxBlocks + "）。");
        }

        String worldName = worldName();
        if (worldName == null || worldName.isBlank()) {
            throw new CopyException("config.yml 的 copy.world 没有配置。");
        }
        if (worldName.equals(source.getName())) {
            throw new CopyException("蓝图世界不能和副本世界相同（copy.world=" + worldName + "）。");
        }
        World target = ensureWorld(worldName);
        if (target == null) {
            throw new CopyException("副本世界 " + worldName + " 创建失败。");
        }

        int gap = Math.max(0, plugin.getConfig().getInt("copy.gap", 256));
        int columns = Math.max(1, plugin.getConfig().getInt("copy.columns", 4));
        int slot = nextSlot();
        int column = slot % columns;
        int row = slot / columns;
        int originX = column * (sizeX + gap);
        int originY = buildMin.getBlockY();
        int originZ = row * (sizeZ + gap);
        int maxX = originX + sizeX - 1;
        int maxY = originY + sizeY - 1;
        int maxZ = originZ + sizeZ - 1;
        if (originY < target.getMinHeight() || maxY >= target.getMaxHeight()) {
            throw new CopyException("蓝图高度超出副本世界范围（Y " + originY + ".." + maxY + "）。");
        }

        int dx = originX - buildMin.getBlockX();
        int dz = originZ - buildMin.getBlockZ();
        RoomCopy copy = new RoomCopy(roomId, blueprint.getName(), worldName, slot,
                originX, originY, originZ, maxX, maxY, maxZ,
                blueprint.translated(target, dx, 0, dz));
        active.put(roomId, copy);
        usedSlots.add(slot);
        save();
        try {
            purgeEntities(target, originX, originY, originZ, maxX, maxY, maxZ);
            copyRegion(source, buildMin.getBlockX(), buildMin.getBlockY(), buildMin.getBlockZ(),
                    target, originX, originY, originZ, sizeX, sizeY, sizeZ);
        } catch (RuntimeException ex) {
            clearRegion(target, originX, originY, originZ, maxX, maxY, maxZ);
            active.remove(roomId);
            usedSlots.remove(slot);
            save();
            throw new CopyException("复制地图失败：" + ex.getMessage());
        }
        plugin.getLogger().info("房间 " + roomId + " 的地图副本位于 " + worldName
                + " (" + originX + ", " + originY + ", " + originZ + ")，" + volume + " 格。");
        return copy;
    }

    public void release(RoomCopy copy) {
        if (copy == null || active.get(copy.roomId()) != copy) {
            return;
        }
        World world = Bukkit.getWorld(copy.worldName());
        if (world == null) {
            plugin.getLogger().warning("副本世界 " + copy.worldName() + " 未加载，跳过清理房间 "
                    + copy.roomId() + " 的地图副本。");
        } else {
            clearRegion(world, copy.minX(), copy.minY(), copy.minZ(),
                    copy.maxX(), copy.maxY(), copy.maxZ());
        }
        active.remove(copy.roomId());
        usedSlots.remove(copy.slot());
        save();
    }

    public void releaseAll() {
        for (RoomCopy copy : new ArrayList<>(active.values())) {
            release(copy);
        }
    }

    // ---------- 查询 ----------

    public String worldName() {
        return plugin.getConfig().getString("copy.world", "world_sn_rooms");
    }

    public int activeCount() {
        return active.size();
    }

    public int activeCount(String arenaName) {
        if (arenaName == null) {
            return 0;
        }
        int count = 0;
        for (RoomCopy copy : active.values()) {
            if (arenaName.equals(copy.arenaName())) {
                count++;
            }
        }
        return count;
    }

    // ---------- 内部 ----------

    private int nextSlot() {
        int slot = 0;
        while (usedSlots.contains(slot)) {
            slot++;
        }
        return slot;
    }

    private World ensureWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world != null) {
            clearStaleIn(world);
            return world;
        }
        try {
            // 与 GameWorlds 的 VOID 配方一致：空层超平坦 + the_void 群系
            WorldCreator creator = new WorldCreator(name);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.generatorSettings("{\"layers\":[],\"biome\":\"minecraft:the_void\"}");
            world = Bukkit.createWorld(creator);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("创建副本世界 " + name + " 失败：" + ex.getMessage());
            return null;
        }
        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setPVP(false);
            world.setStorm(false);
            world.setThundering(false);
            clearStaleIn(world);
        }
        return world;
    }

    /** 副本世界刚加载出来时，把之前没清掉的残留副本补清掉。 */
    private void clearStaleIn(World world) {
        List<Stale> cleared = new ArrayList<>();
        for (Stale record : stale) {
            if (record.worldName().equals(world.getName())) {
                clearRegion(world, record.minX(), record.minY(), record.minZ(),
                        record.maxX(), record.maxY(), record.maxZ());
                cleared.add(record);
            }
        }
        if (!cleared.isEmpty()) {
            stale.removeAll(cleared);
            save();
            plugin.getLogger().info("已清理 " + cleared.size() + " 个遗留的地图副本。");
        }
    }

    private static void copyRegion(World source, int sourceMinX, int sourceMinY, int sourceMinZ,
                                   World target, int targetMinX, int targetMinY, int targetMinZ,
                                   int sizeX, int sizeY, int sizeZ) {
        loadChunks(source, sourceMinX, sourceMinZ, sourceMinX + sizeX - 1, sourceMinZ + sizeZ - 1);
        loadChunks(target, targetMinX, targetMinZ, targetMinX + sizeX - 1, targetMinZ + sizeZ - 1);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int y = 0; y < sizeY; y++) {
                    BlockData from = source.getBlockAt(sourceMinX + x, sourceMinY + y, sourceMinZ + z)
                            .getBlockData();
                    Block to = target.getBlockAt(targetMinX + x, targetMinY + y, targetMinZ + z);
                    BlockData current = to.getBlockData();
                    if (from.getMaterial().isAir()) {
                        if (!current.getMaterial().isAir()) {
                            to.setBlockData(AIR, false);
                        }
                    } else if (!current.equals(from)) {
                        to.setBlockData(from, false);
                    }
                }
            }
        }
    }

    private static void clearRegion(World world, int minX, int minY, int minZ,
                                    int maxX, int maxY, int maxZ) {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }
        loadChunks(world, minX, minZ, maxX, maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        block.setBlockData(AIR, false);
                    }
                }
            }
        }
        purgeEntities(world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void purgeEntities(World world, int minX, int minY, int minZ,
                                      int maxX, int maxY, int maxZ) {
        List<Entity> remove = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(
                new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1))) {
            if (!(entity instanceof Player)) {
                remove.add(entity);
            }
        }
        for (Entity entity : remove) {
            entity.remove();
        }
    }

    private static void loadChunks(World world, int minX, int minZ, int maxX, int maxZ) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ).load();
            }
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE);
    }

    private void save() {
        File file = file();
        if (active.isEmpty() && stale.isEmpty()) {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("无法删除 " + FILE + "。");
            }
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("instances");
        for (RoomCopy copy : active.values()) {
            ConfigurationSection sec = root.createSection(copy.roomId());
            sec.set("arena", copy.arenaName());
            sec.set("world", copy.worldName());
            sec.set("slot", copy.slot());
            sec.set("min-x", copy.minX());
            sec.set("min-y", copy.minY());
            sec.set("min-z", copy.minZ());
            sec.set("max-x", copy.maxX());
            sec.set("max-y", copy.maxY());
            sec.set("max-z", copy.maxZ());
        }
        for (Stale record : stale) {
            ConfigurationSection sec = root.createSection(record.roomId());
            sec.set("arena", record.arenaName());
            sec.set("world", record.worldName());
            sec.set("slot", record.slot());
            sec.set("min-x", record.minX());
            sec.set("min-y", record.minY());
            sec.set("min-z", record.minZ());
            sec.set("max-x", record.maxX());
            sec.set("max-y", record.maxY());
            sec.set("max-z", record.maxZ());
        }
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录。");
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 " + FILE + " 失败：" + ex.getMessage());
        }
    }
}
