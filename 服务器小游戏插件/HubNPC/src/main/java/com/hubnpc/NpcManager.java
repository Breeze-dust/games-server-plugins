package com.hubnpc;

import com.destroystokyo.paper.SkinParts;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NPC 的生成、持久化、皮肤与右键指令。 */
final class NpcManager {

    private static final long CLICK_COOLDOWN_MS = 400L;

    private final HubNPCPlugin plugin;
    private final NamespacedKey keyId;
    private final File file;
    private final Map<String, NpcData> npcs = new LinkedHashMap<>();
    private final Map<UUID, String> idByEntity = new HashMap<>();
    private final Map<UUID, Map<String, Long>> clickCooldown = new HashMap<>();

    NpcManager(HubNPCPlugin plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey(plugin, "npc-id");
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ---------------------------------------------------------------- 持久化

    void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            NpcData data = readNpc(id, section);
            if (data == null) {
                plugin.getLogger().warning("NPC 数据损坏，已跳过：" + id);
                continue;
            }
            npcs.put(id, data);
            idByEntity.put(data.entityId, id);
        }
    }

    private static NpcData readNpc(String id, ConfigurationSection section) {
        String uuidText = section.getString("entity");
        if (uuidText == null) {
            return null;
        }
        NpcData data = new NpcData(id);
        try {
            data.entityId = UUID.fromString(uuidText);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        data.world = section.getString("world", "world");
        data.x = section.getDouble("x");
        data.y = section.getDouble("y");
        data.z = section.getDouble("z");
        data.yaw = (float) section.getDouble("yaw");
        data.pitch = (float) section.getDouble("pitch");
        data.displayName = section.getString("name", id);
        data.skinName = section.getString("skin-name");
        data.skinValue = section.getString("skin-value");
        data.skinSignature = section.getString("skin-signature");
        data.commands.addAll(section.getStringList("commands"));
        return data;
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("npcs");
        for (NpcData data : npcs.values()) {
            ConfigurationSection section = root.createSection(data.id);
            section.set("entity", data.entityId.toString());
            section.set("world", data.world);
            section.set("x", data.x);
            section.set("y", data.y);
            section.set("z", data.z);
            section.set("yaw", data.yaw);
            section.set("pitch", data.pitch);
            section.set("name", data.displayName);
            section.set("skin-name", data.skinName);
            section.set("skin-value", data.skinValue);
            section.set("skin-signature", data.skinSignature);
            section.set("commands", data.commands);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 npcs.yml 失败：" + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- 查询

    NpcData get(String id) {
        return npcs.get(id);
    }

    Collection<NpcData> all() {
        return npcs.values();
    }

    Collection<String> ids() {
        return npcs.keySet();
    }

    int size() {
        return npcs.size();
    }

    /** NPC 实体的业务 id；不是本插件生成的 NPC 时返回 null。 */
    String idOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = idByEntity.get(entity.getUniqueId());
        if (id != null) {
            return id;
        }
        id = entity.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (id != null) {
            idByEntity.put(entity.getUniqueId(), id);
        }
        return id;
    }

    boolean isNpc(Entity entity) {
        return idOf(entity) != null;
    }

    Mannequin entity(NpcData data) {
        return entity(data, false);
    }

    Mannequin entity(NpcData data, boolean loadChunk) {
        World world = Bukkit.getWorld(data.world);
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntity(data.entityId);
        if (entity instanceof Mannequin mannequin) {
            return mannequin;
        }
        if (!loadChunk) {
            return null;
        }
        int chunkX = Location.locToBlock(data.x) >> 4;
        int chunkZ = Location.locToBlock(data.z) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            try {
                world.getChunkAt(chunkX, chunkZ).load();
            } catch (Throwable t) {
                return null;
            }
        }
        entity = world.getEntity(data.entityId);
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    // ---------------------------------------------------------------- 生成 / 修改

    NpcData create(String id, String displayName, Location location) {
        NpcData data = new NpcData(id);
        data.displayName = displayName;
        data.updateLocation(location);

        Mannequin mannequin = location.getWorld().spawn(
                location, Mannequin.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        data.entityId = mannequin.getUniqueId();
        apply(mannequin, data);

        npcs.put(id, data);
        idByEntity.put(data.entityId, id);
        save();
        return data;
    }

    void apply(Mannequin mannequin, NpcData data) {
        mannequin.setPersistent(true);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.setInvulnerable(true);
        mannequin.setSilent(true);
        mannequin.setGravity(false);
        mannequin.setCollidable(false);
        mannequin.setAI(false);
        mannequin.setImmovable(true);
        mannequin.setSkinParts(SkinParts.allParts());
        // 传 null 才会隐藏描述行；Component.empty() 仍然会渲染成一整行空白。
        mannequin.setDescription(null);
        mannequin.customName(Text.parse(data.displayName));
        mannequin.setCustomNameVisible(true);
        mannequin.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, data.id);
        if (data.hasSkinData()) {
            try {
                mannequin.setProfile(
                        SkinService.profile(data.skinName, data.skinValue, data.skinSignature));
            } catch (Throwable t) {
                plugin.getLogger().warning("应用 NPC 皮肤失败（" + data.id + "）：" + t.getMessage());
            }
        }
    }

    boolean remove(String id) {
        NpcData data = npcs.remove(id);
        if (data == null) {
            return false;
        }
        idByEntity.remove(data.entityId);
        Mannequin mannequin = entity(data, true);
        if (mannequin != null) {
            mannequin.remove();
        }
        save();
        return true;
    }

    void rename(NpcData data, String displayName) {
        data.displayName = displayName;
        Mannequin mannequin = entity(data);
        if (mannequin != null) {
            mannequin.customName(Text.parse(displayName));
        }
        save();
    }

    void move(NpcData data, Location location) {
        data.updateLocation(location);
        Mannequin mannequin = entity(data, true);
        if (mannequin != null) {
            mannequin.teleport(location);
            mannequin.setRotation(location.getYaw(), location.getPitch());
            mannequin.setBodyYaw(location.getYaw());
        }
        save();
    }

    // ---------------------------------------------------------------- 朝向玩家

    private double gazeRange;
    private int gazeInterval;
    private BukkitTask gazeTask;

    /** 玩家进入 range 格时 NPC 转身看他；range <= 0 表示关闭。 */
    void startGaze(double range, int intervalTicks) {
        if (gazeTask != null) {
            gazeTask.cancel();
            gazeTask = null;
        }
        gazeRange = range;
        gazeInterval = Math.max(1, intervalTicks);
        if (range <= 0) {
            return;
        }
        gazeTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::updateGaze, gazeInterval, gazeInterval);
    }

    double gazeRange() {
        return gazeRange;
    }

    int gazeInterval() {
        return gazeInterval;
    }

    private void updateGaze() {
        if (npcs.isEmpty()) {
            return;
        }
        double rangeSq = gazeRange * gazeRange;
        for (NpcData data : npcs.values()) {
            Mannequin mannequin = entity(data);
            if (mannequin == null) {
                continue;
            }
            Player target = nearestPlayer(mannequin, rangeSq);
            if (target == null) {
                face(mannequin, data.yaw, data.pitch);
            } else {
                faceToward(mannequin, target);
            }
        }
    }

    private static Player nearestPlayer(Mannequin mannequin, double rangeSq) {
        Player nearest = null;
        double best = rangeSq;
        Location at = mannequin.getLocation();
        for (Player player : mannequin.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(at);
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void faceToward(Mannequin mannequin, Player player) {
        double dx = player.getX() - mannequin.getX();
        double dz = player.getZ() - mannequin.getZ();
        double dy = player.getEyeLocation().getY() - mannequin.getEyeLocation().getY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        face(mannequin, yaw, pitch);
    }

    /** 角度没变就不写，省掉每轮无意义的实体元数据包。 */
    private static void face(Mannequin mannequin, float yaw, float pitch) {
        boolean same = Math.abs(wrapDegrees(mannequin.getYaw() - yaw)) < 0.5f
                && Math.abs(mannequin.getPitch() - pitch) < 0.5f
                && Math.abs(wrapDegrees(mannequin.getBodyYaw() - yaw)) < 0.5f;
        if (same) {
            return;
        }
        mannequin.setRotation(yaw, pitch);
        mannequin.setBodyYaw(yaw);
    }

    private static float wrapDegrees(float angle) {
        angle %= 360f;
        if (angle >= 180f) {
            angle -= 360f;
        } else if (angle < -180f) {
            angle += 360f;
        }
        return angle;
    }

    void setSkin(NpcData data, String playerName, SkinService.SkinData skin) {
        data.skinName = playerName;
        data.skinValue = skin.value();
        data.skinSignature = skin.signature();
        Mannequin mannequin = entity(data);
        if (mannequin != null) {
            mannequin.setProfile(SkinService.profile(skin));
        }
        save();
    }

    void resetSkin(NpcData data) {
        data.skinName = null;
        data.skinValue = null;
        data.skinSignature = null;
        Mannequin mannequin = entity(data);
        if (mannequin != null) {
            mannequin.setProfile(Mannequin.defaultProfile());
        }
        save();
    }

    void addCommand(NpcData data, String command) {
        data.commands.add(command);
        save();
    }

    boolean removeCommand(NpcData data, int index) {
        if (index < 1 || index > data.commands.size()) {
            return false;
        }
        data.commands.remove(index - 1);
        save();
        return true;
    }

    void clearCommands(NpcData data) {
        data.commands.clear();
        save();
    }

    // ---------------------------------------------------------------- 右键

    /** 处理右键；返回 false 表示这个实体不是本插件的 NPC。 */
    boolean handleClick(Player player, Entity entity) {
        String id = idOf(entity);
        if (id == null) {
            return false;
        }
        NpcData data = npcs.get(id);
        if (data == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        Map<String, Long> timestamps =
                clickCooldown.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        Long last = timestamps.get(id);
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return true;
        }
        timestamps.put(id, now);

        for (String raw : data.commands) {
            runCommand(player, data, raw);
        }
        return true;
    }

    private void runCommand(Player player, NpcData data, String raw) {
        String command = raw
                .replace("{player}", player.getName())
                .replace("{npc}", data.id)
                .replace("{world}", player.getWorld().getName());
        if (command.regionMatches(true, 0, "console:", 0, "console:".length())) {
            dispatch(Bukkit.getConsoleSender(), command.substring("console:".length()));
        } else if (command.regionMatches(true, 0, "player:", 0, "player:".length())) {
            dispatch(player, command.substring("player:".length()));
        } else {
            dispatch(player, command);
        }
    }

    private void dispatch(CommandSender sender, String command) {
        String clean = command.trim();
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) {
            return;
        }
        try {
            Bukkit.dispatchCommand(sender, clean);
        } catch (Throwable t) {
            plugin.getLogger().warning("NPC 指令执行失败：/" + clean + " - " + t.getMessage());
        }
    }

    void clearCooldowns(UUID playerId) {
        clickCooldown.remove(playerId);
    }

    // ---------------------------------------------------------------- 副本清理

    /**
     * 带本插件标签、但不是 npcs.yml 里登记的那只实体（复制粘贴区块、回档等留下的副本）。
     * 只能看到已加载区块里的实体。
     */
    List<Mannequin> findOrphans() {
        List<Mannequin> orphans = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Mannequin mannequin)) {
                    continue;
                }
                String id = mannequin.getPersistentDataContainer()
                        .get(keyId, PersistentDataType.STRING);
                if (id == null) {
                    continue;
                }
                NpcData data = npcs.get(id);
                if (data != null && data.entityId.equals(mannequin.getUniqueId())) {
                    continue;
                }
                orphans.add(mannequin);
            }
        }
        return orphans;
    }

    int removeAll(Collection<Mannequin> entities) {
        int removed = 0;
        for (Mannequin mannequin : entities) {
            if (!mannequin.isValid()) {
                continue;
            }
            mannequin.remove();
            removed++;
        }
        return removed;
    }

    // ---------------------------------------------------------------- 区块加载

    void onEntitiesLoaded(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof Mannequin mannequin) {
                String id = mannequin.getPersistentDataContainer()
                        .get(keyId, PersistentDataType.STRING);
                if (id == null) {
                    continue;
                }
                NpcData data = npcs.get(id);
                if (data != null) {
                    idByEntity.put(entity.getUniqueId(), id);
                    apply(mannequin, data);
                }
            }
        }
    }

    /** 插件启用时把已经加载的 NPC 再套一遍设置（防止旧实体缺标记）。 */
    void reapplyLoaded() {
        List<Entity> loaded = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            loaded.addAll(world.getEntities());
        }
        onEntitiesLoaded(loaded);
    }
}
