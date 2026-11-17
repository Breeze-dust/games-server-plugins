package com.liarbar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 房间与桌子（场次）配置：位置、椅子登记、读写 config.yml。 */
public final class LiarBarRoom {

    private final Plugin plugin;
    private Location spawn;
    private Location pos1;
    private Location pos2;
    private final Map<String, List<Location>> seats = new LinkedHashMap<>();
    /** 读配置时有世界没加载，稍微延后再读一次。 */
    private boolean unresolved;

    public LiarBarRoom(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration c) {
        unresolved = false;
        ConfigurationSection spawnSec = c.getConfigurationSection("room.spawn");
        ConfigurationSection pos1Sec = c.getConfigurationSection("room.pos1");
        ConfigurationSection pos2Sec = c.getConfigurationSection("room.pos2");
        spawn = readLocation(spawnSec);
        pos1 = readLocation(pos1Sec);
        pos2 = readLocation(pos2Sec);
        if (pending(spawnSec) || pending(pos1Sec) || pending(pos2Sec)) {
            unresolved = true;
        }
        seats.clear();
        ConfigurationSection tables = c.getConfigurationSection("tables");
        if (tables != null) {
            for (String name : tables.getKeys(false)) {
                List<Location> list = new ArrayList<>();
                for (Map<?, ?> map : tables.getMapList(name + ".seats")) {
                    Location loc = mapToLocation(map);
                    if (loc != null) {
                        list.add(loc);
                    } else {
                        unresolved = true;
                    }
                }
                if (!list.isEmpty()) {
                    seats.put(name, list);
                }
            }
        }
    }

    public boolean unresolved() {
        return unresolved;
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    public void setSpawn(Location loc) {
        spawn = loc.clone();
    }

    public void setCorners(Location a, Location b) {
        pos1 = a == null ? null : a.clone();
        pos2 = b == null ? null : b.clone();
    }

    public void setPos1(Location loc) {
        pos1 = loc.clone();
    }

    public void setPos2(Location loc) {
        pos2 = loc.clone();
    }

    public Location pos1() {
        return pos1 == null ? null : pos1.clone();
    }

    public Location pos2() {
        return pos2 == null ? null : pos2.clone();
    }

    public boolean configured() {
        return spawn != null && !seats.isEmpty();
    }

    public Map<String, List<Location>> tables() {
        return seats;
    }

    public List<Location> seatsOf(String table) {
        return seats.getOrDefault(table, List.of());
    }

    public boolean isChair(Block block) {
        for (List<Location> list : seats.values()) {
            for (Location loc : list) {
                if (sameBlock(loc, block)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String tableOfChair(Block block) {
        for (Map.Entry<String, List<Location>> entry : seats.entrySet()) {
            for (Location loc : entry.getValue()) {
                if (sameBlock(loc, block)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public boolean addChair(String table, Location loc) {
        List<Location> list = seats.computeIfAbsent(table, k -> new ArrayList<>());
        for (Location exist : list) {
            if (sameBlock(exist, loc.getBlock())) {
                return false;
            }
        }
        list.add(loc.getBlock().getLocation());
        return true;
    }

    public void removeTable(String table) {
        seats.remove(table);
    }

    /** 玩家是否站在房间区域内（没设 pos1/pos2 时退回等待点周围 48 格）。 */
    public boolean inRoom(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (pos1 != null && pos2 != null) {
            if (loc.getWorld() != pos1.getWorld()) {
                return false;
            }
            int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
            int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
            int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
            int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
            int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
            int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
            return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                    && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                    && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }
        if (spawn != null && loc.getWorld() == spawn.getWorld()) {
            double dx = loc.getX() - spawn.getX();
            double dz = loc.getZ() - spawn.getZ();
            double dy = loc.getY() - spawn.getY();
            return dx * dx + dz * dz <= 48 * 48 && Math.abs(dy) <= 48;
        }
        return false;
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();
        c.set("room", null);
        ConfigurationSection room = c.createSection("room");
        room.set("world", spawn == null ? "" : spawn.getWorld().getName());
        writeLocation(room.createSection("spawn"), spawn);
        writeLocation(room.createSection("pos1"), pos1);
        writeLocation(room.createSection("pos2"), pos2);

        c.set("tables", null);
        ConfigurationSection tables = c.createSection("tables");
        for (Map.Entry<String, List<Location>> entry : seats.entrySet()) {
            ConfigurationSection table = tables.createSection(entry.getKey());
            List<Map<String, Object>> list = new ArrayList<>();
            for (Location loc : entry.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("world", loc.getWorld().getName());
                map.put("x", loc.getX());
                map.put("y", loc.getY());
                map.put("z", loc.getZ());
                list.add(map);
            }
            table.set("seats", list);
        }
        plugin.saveConfig();
    }

    private static boolean sameBlock(Location loc, Block block) {
        return loc.getWorld() != null
                && loc.getWorld().equals(block.getWorld())
                && loc.getBlockX() == block.getX()
                && loc.getBlockY() == block.getY()
                && loc.getBlockZ() == block.getZ();
    }

    private static void writeLocation(ConfigurationSection sec, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        sec.set("world", loc.getWorld().getName());
        sec.set("x", loc.getX());
        sec.set("y", loc.getY());
        sec.set("z", loc.getZ());
        sec.set("yaw", (double) loc.getYaw());
        sec.set("pitch", (double) loc.getPitch());
    }

    private static Location readLocation(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        String worldName = sec.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    /** 配置里写了世界名，但那个世界当前没加载。 */
    private static boolean pending(ConfigurationSection sec) {
        if (sec == null) {
            return false;
        }
        String worldName = sec.getString("world", "");
        return !worldName.isEmpty() && readLocation(sec) == null;
    }

    private Location mapToLocation(Map<?, ?> map) {
        Object worldName = map.get("world");
        if (!(worldName instanceof String name) || name.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            return null;
        }
        double x = asDouble(map.get("x"));
        double y = asDouble(map.get("y"));
        double z = asDouble(map.get("z"));
        return new Location(world, x, y, z);
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
