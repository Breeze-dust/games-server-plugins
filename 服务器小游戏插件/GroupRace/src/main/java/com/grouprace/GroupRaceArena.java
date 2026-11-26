package com.grouprace;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 群友竞速赛的赛道：等待点、起跑点、出界方框、按顺序排列的圆环。 */
public final class GroupRaceArena {

    private final JavaPlugin plugin;
    private Location lobby;
    private Location start;
    private Location pos1;
    private Location pos2;
    private final List<GroupRaceRing> rings = new ArrayList<>();
    /** 本局自动生成的随机赛道（不写回配置）；为空时用配置里的 rings。 */
    private final List<GroupRaceRing> runtime = new ArrayList<>();

    public GroupRaceArena(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // 先清掉上一次自动生成的随机环（正常关服会清；这里兜住崩服遗留）
        clearPersistedRuntime();
        FileConfiguration cfg = plugin.getConfig();
        lobby = readLocation(cfg, "arena.lobby");
        start = readLocation(cfg, "arena.start");
        pos1 = readLocation(cfg, "arena.pos1");
        pos2 = readLocation(cfg, "arena.pos2");
        rings.clear();
        int index = 0;
        for (Map<?, ?> map : cfg.getMapList("rings")) {
            GroupRaceRing ring = GroupRaceRing.fromMap(index, map);
            if (ring != null) {
                rings.add(ring);
                index++;
            }
        }
        runtime.clear();
    }

    public void save() {
        // 先重读，避免把内存里的旧配置写回去
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("arena.lobby", lobby);
        cfg.set("arena.start", start);
        cfg.set("arena.pos1", pos1);
        cfg.set("arena.pos2", pos2);
        List<Map<String, Object>> list = new ArrayList<>();
        for (GroupRaceRing ring : rings) {
            list.add(ring.toMap());
        }
        cfg.set("rings", list);
        plugin.saveConfig();
    }

    public void buildRings(Material normalMaterial, Material finishMaterial) {
        for (GroupRaceRing ring : rings()) {
            ring.build(ring.finish() ? finishMaterial : normalMaterial);
        }
    }

    public void clearRings() {
        for (GroupRaceRing ring : runtime) {
            ring.clear();
        }
        runtime.clear();
        for (GroupRaceRing ring : rings) {
            ring.clear();
        }
        rings.clear();
    }

    public boolean isReady() {
        if (lobby == null || start == null || pos1 == null || pos2 == null) {
            return false;
        }
        if (autoCourseEnabled()) {
            // 自动生成模式下只需要固定终点环，普通环开局随机生成
            return !rings.isEmpty() && rings.get(rings.size() - 1).finish();
        }
        List<GroupRaceRing> course = rings();
        return course.size() >= 2 && course.get(course.size() - 1).finish()
                && !course.get(0).finish();
    }

    private boolean autoCourseEnabled() {
        return plugin.getConfig().getBoolean("ring.auto.enabled", true);
    }

    public String missing() {
        List<String> missing = new ArrayList<>();
        if (lobby == null) {
            missing.add("lobby");
        }
        if (start == null) {
            missing.add("start");
        }
        if (pos1 == null) {
            missing.add("pos1");
        }
        if (pos2 == null) {
            missing.add("pos2");
        }
        List<GroupRaceRing> course = rings();
        if (autoCourseEnabled()) {
            if (rings.isEmpty() || !rings.get(rings.size() - 1).finish()) {
                missing.add("终点环（/race addfinish）");
            }
        } else if (course.size() < 2) {
            missing.add("至少 1 个普通环 + 1 个终点环");
        } else if (!course.get(course.size() - 1).finish()) {
            missing.add("终点环");
        }
        return missing.isEmpty() ? "无" : String.join("、", missing);
    }

    public boolean inside(Location loc) {
        if (loc == null || pos1 == null || pos2 == null || pos1.getWorld() == null) {
            return false;
        }
        if (loc.getWorld() == null || !loc.getWorld().equals(pos1.getWorld())) {
            return false;
        }
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        // 只比较 X/Z：Y 不做限制（等价于自动覆盖世界最低到最高）
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    /** 起跑位置：以 start 为中心，沿垂直方向散开；并朝向第一个圆环。 */
    public Location startLocation(int index, int total, double spread) {
        if (start == null) {
            return lobby == null ? null : lobby.clone();
        }
        Location location = start.clone();
        Vector forward = location.getDirection().setY(0.0);
        if (forward.lengthSquared() < 0.001) {
            forward = new Vector(0.0, 0.0, 1.0);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
        location.add(right.multiply((index - (total - 1) / 2.0) * spread));
        GroupRaceRing first = firstRing();
        if (first != null) {
            Vector direction = first.center().toVector().subtract(location.toVector());
            if (direction.lengthSquared() > 0.001) {
                location.setDirection(direction);
            }
        }
        return location;
    }

    public GroupRaceRing firstRing() {
        List<GroupRaceRing> course = rings();
        return course.isEmpty() ? null : course.get(0);
    }

    public GroupRaceRing lastRing() {
        List<GroupRaceRing> course = rings();
        return course.isEmpty() ? null : course.get(course.size() - 1);
    }

    public GroupRaceRing ring(int index) {
        List<GroupRaceRing> course = rings();
        if (index < 0 || index >= course.size()) {
            return null;
        }
        return course.get(index);
    }

    public GroupRaceRing nextRing(int index) {
        return ring(index + 1);
    }

    /** 实际使用的赛道：本局自动生成过就用它，否则用配置里的环列表。 */
    public List<GroupRaceRing> rings() {
        return runtime.isEmpty() ? rings : runtime;
    }

    /** 配置里保存的环（自动生成不会写回这里）。 */
    public List<GroupRaceRing> configuredRings() {
        return rings;
    }

    /** 丢弃本局自动生成的环（只清它自己，不动配置里保存的环方块）。 */
    public void clearRuntime() {
        for (GroupRaceRing ring : runtime) {
            ring.clear();
        }
        runtime.clear();
        File file = runtimeFile();
        if (file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private File runtimeFile() {
        return new File(plugin.getDataFolder(), "auto-course.yml");
    }

    /** 把本局随机赛道写盘：崩服时下次启动能按坐标把方块清掉，不会越积越多。 */
    private void persistRuntime() {
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (GroupRaceRing ring : runtime) {
            list.add(ring.toMap());
        }
        yml.set("rings", list);
        try {
            yml.save(runtimeFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("保存自动赛道失败：" + exception.getMessage());
        }
    }

    private void clearPersistedRuntime() {
        File file = runtimeFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        int index = 0;
        for (Map<?, ?> map : yml.getMapList("rings")) {
            GroupRaceRing ring = GroupRaceRing.fromMap(index++, map);
            if (ring != null) {
                ring.clear();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * 随机生成一条赛道：出生点固定、配置里的终点环固定，中间的普通环随机摆放在两者之间。
     * 生成结果只放进 runtime，不写回配置；下一局重新生成，所以每局场地都不一样。
     *
     * @param maxRings  总环数上限（含终点环）
     * @param spacing   环与环之间的目标距离（格）
     * @param jitter    随机偏移幅度（格），0 = 一条直线
     * @param ringRadius 普通环半径
     * @return 生成的总环数（含终点环）；0 = 条件不足未生成
     */
    public int generateAutoCourse(int maxRings, double spacing, double jitter,
                                  double ringRadius, Random random) {
        GroupRaceRing finish = null;
        for (int i = rings.size() - 1; i >= 0; i--) {
            if (rings.get(i).finish()) {
                finish = rings.get(i);
                break;
            }
        }
        if (finish == null || start == null || pos1 == null || pos2 == null) {
            return 0;
        }
        clearRuntime();

        Location from = start.clone().add(0.0, 1.2, 0.0);
        Location to = finish.center();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0) {
            return 0;
        }
        int total = Math.max(2, maxRings);
        int ringsTotal = (int) Math.round(distance / Math.max(1.0, spacing)) + 1;
        ringsTotal = Math.max(2, Math.min(total, ringsTotal));

        double minX = Math.min(pos1.getX(), pos2.getX()) + ringRadius + 1;
        double maxX = Math.max(pos1.getX(), pos2.getX()) - ringRadius - 1;
        double minZ = Math.min(pos1.getZ(), pos2.getZ()) + ringRadius + 1;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) - ringRadius - 1;
        if (minX > maxX) {
            minX = Math.min(pos1.getX(), pos2.getX());
            maxX = Math.max(pos1.getX(), pos2.getX());
        }
        if (minZ > maxZ) {
            minZ = Math.min(pos1.getZ(), pos2.getZ());
            maxZ = Math.max(pos1.getZ(), pos2.getZ());
        }
        double minY = Math.min(pos1.getY(), pos2.getY()) + 2;
        double maxY = Math.max(pos1.getY(), pos2.getY()) - 2;
        if (minY > maxY) {
            minY = Math.min(from.getY(), to.getY());
            maxY = Math.max(from.getY(), to.getY());
        }

        Vector dir = new Vector(dx, 0.0, dz).normalize();
        Vector perp = new Vector(-dir.getZ(), 0.0, dir.getX());

        List<Location> points = new ArrayList<>();
        points.add(from);
        double amp = Math.max(0.0, jitter);
        for (int i = 1; i < ringsTotal; i++) {
            double t = (double) i / ringsTotal;
            Location p = from.clone().add(dx * t, 0.0, dz * t);
            if (amp > 0.0) {
                p.add(perp.clone().multiply((random.nextDouble() * 2 - 1) * amp));
                p.add(0.0, (random.nextDouble() * 2 - 1) * amp * 0.6, 0.0);
            }
            p.setX(clamp(p.getX(), minX, maxX));
            p.setZ(clamp(p.getZ(), minZ, maxZ));
            p.setY(clamp(p.getY(), minY, maxY));
            points.add(p);
        }
        points.add(to);

        for (int i = 0; i < points.size() - 1; i++) {
            Location center = points.get(i);
            Vector normal = points.get(i + 1).toVector().subtract(center.toVector());
            boolean isFinish = i == points.size() - 2;
            runtime.add(new GroupRaceRing(i, center, normal, isFinish ? finish.radius() : ringRadius, isFinish));
        }
        persistRuntime();
        return runtime.size();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Location getLobby() {
        return lobby == null ? null : lobby.clone();
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby == null ? null : lobby.clone();
    }

    public Location getStart() {
        return start == null ? null : start.clone();
    }

    public void setStart(Location start) {
        this.start = start == null ? null : start.clone();
    }

    public Location getPos1() {
        return pos1 == null ? null : pos1.clone();
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1 == null ? null : pos1.clone();
    }

    public Location getPos2() {
        return pos2 == null ? null : pos2.clone();
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2 == null ? null : pos2.clone();
    }

    /** 兼容旧式 Location 配置与字符串；返回 null 表示未设置/世界未加载。 */
    private static Location readLocation(FileConfiguration cfg, String path) {
        Object value = cfg.get(path);
        if (value instanceof Location location) {
            return location.getWorld() == null ? null : location.clone();
        }
        if (value instanceof String raw && !raw.isBlank()) {
            String[] parts = raw.split(",");
            if (parts.length >= 4) {
                World world = org.bukkit.Bukkit.getWorld(parts[0].trim());
                if (world != null) {
                    try {
                        double x = Double.parseDouble(parts[1].trim());
                        double y = Double.parseDouble(parts[2].trim());
                        double z = Double.parseDouble(parts[3].trim());
                        float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0f;
                        float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0f;
                        return new Location(world, x, y, z, yaw, pitch);
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
            }
        }
        return null;
    }

    /** 用于调试/命令输出。 */
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lobby", lobby == null ? "未设置" : lobby.toString());
        out.put("start", start == null ? "未设置" : start.toString());
        out.put("pos1", pos1 == null ? "未设置" : pos1.toString());
        out.put("pos2", pos2 == null ? "未设置" : pos2.toString());
        out.put("rings", rings.size());
        return out;
    }
}
