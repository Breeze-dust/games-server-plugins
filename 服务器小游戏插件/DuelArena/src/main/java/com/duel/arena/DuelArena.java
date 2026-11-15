package com.duel.arena;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 单个单挑场：范围（不允许离开）、两个出生点、共用物品配置、开局药水效果、运行时对战状态。 */
public class DuelArena {

    public enum State {
        WAITING,   // 等待玩家加入
        FIGHTING   // 对战中
    }

    /** 一条开局药水效果：durationSeconds = 0 表示整局有效。 */
    public record EffectSpec(PotionEffectType type, int durationSeconds, int amplifier) {

        /** 整局有效的真实时长（30 分钟，远长于任何一场单挑）。 */
        private static final int WHOLE_FIGHT_TICKS = 20 * 60 * 30;

        public int durationTicks() {
            return durationSeconds <= 0 ? WHOLE_FIGHT_TICKS : durationSeconds * 20;
        }

        public EffectSpec withAmplifier(int value) {
            return new EffectSpec(type, durationSeconds, value);
        }

        public EffectSpec withDurationSeconds(int value) {
            return new EffectSpec(type, value, amplifier);
        }
    }

    private String name;
    private Location pos1;
    private Location pos2;
    private Location spawn1;
    private Location manualLobby;
    private Location spawn2;
    private Kit kit = new Kit();
    private final Map<PotionEffectType, EffectSpec> effects = new LinkedHashMap<>();

    // 运行时状态（不持久化）
    private State state = State.WAITING;
    private final List<UUID> queue = new ArrayList<>();
    private UUID fighter1;
    private UUID fighter2;
    private long fightStartMs;

    public DuelArena(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** 改名（场地重命名时由管理器调用；倒计时任务内 getName() 会随之返回新名）。 */
    public void setName(String name) {
        this.name = name;
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public Kit getKit() {
        return kit;
    }

    public void setKit(Kit kit) {
        this.kit = kit;
    }

    // ---------- 开局药水效果 ----------

    /** 已配置的开局药水效果（按配置顺序）。 */
    public Collection<EffectSpec> getEffects() {
        return effects.values();
    }

    public EffectSpec getEffect(PotionEffectType type) {
        return effects.get(type);
    }

    public boolean hasEffect(PotionEffectType type) {
        return effects.containsKey(type);
    }

    public void setEffect(PotionEffectType type, int durationSeconds, int amplifier) {
        effects.put(type, new EffectSpec(type, Math.max(0, durationSeconds), Math.max(0, amplifier)));
    }

    public void removeEffect(PotionEffectType type) {
        effects.remove(type);
    }

    public void clearEffects() {
        effects.clear();
    }

    /** 开局发给玩家（调用前应已清空旧效果）。 */
    public void applyEffects(Player p) {
        for (EffectSpec e : effects.values()) {
            p.addPotionEffect(new PotionEffect(e.type(), e.durationTicks(), e.amplifier(), false, true, true));
        }
    }

    /** 配置是否齐全（范围 + 两个出生点）。 */
    public boolean isReady() {
        return pos1 != null && pos2 != null && spawn1 != null && spawn2 != null;
    }

    /** 立方体范围（含边界）。 */
    public boolean contains(Location loc) {
        if (!isReady() || loc == null || !loc.getWorld().equals(pos1.getWorld())) {
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

    public Location getManualLobby() { return manualLobby; }
    public void setManualLobby(Location l) { this.manualLobby = l; }

    /** 等待点：手动设置优先，否则范围中心上方。 */
    public Location getLobbyPoint() {
        if (manualLobby != null) return manualLobby;
        Location a = pos1 == null ? spawn1 : pos1;
        Location b = pos2 == null ? spawn2 : pos2;
        if (a == null) {
            a = spawn1;
            b = spawn2;
        }
        if (a == null) {
            return null;
        }
        int x = (a.getBlockX() + b.getBlockX()) / 2;
        int y = Math.max(a.getBlockY(), b.getBlockY()) + 3;
        int z = (a.getBlockZ() + b.getBlockZ()) / 2;
        return new Location(a.getWorld(), x + 0.5, y, z + 0.5, 0, 0);
    }

    // ---------- 运行时状态 ----------

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public List<UUID> getQueue() {
        return queue;
    }

    public UUID getFighter1() {
        return fighter1;
    }

    public void setFighter1(UUID fighter1) {
        this.fighter1 = fighter1;
    }

    public UUID getFighter2() {
        return fighter2;
    }

    public void setFighter2(UUID fighter2) {
        this.fighter2 = fighter2;
    }

    public long getFightStartMs() {
        return fightStartMs;
    }

    public void setFightStartMs(long fightStartMs) {
        this.fightStartMs = fightStartMs;
    }

    /** 对方玩家。 */
    public UUID opponentOf(UUID id) {
        if (id.equals(fighter1)) {
            return fighter2;
        }
        if (id.equals(fighter2)) {
            return fighter1;
        }
        return null;
    }

    /** 玩家是否在本场对战中。 */
    public boolean isFighter(UUID id) {
        return id.equals(fighter1) || id.equals(fighter2);
    }

    /** 保存场配置到配置段。 */
    public void save(ConfigurationSection sec) {
        sec.set("pos1", pos1);
        sec.set("pos2", pos2);
        sec.set("spawn1", spawn1);
        sec.set("manual-lobby", manualLobby);
        sec.set("spawn2", spawn2);
        kit.save(sec.createSection("kit"));
        if (effects.isEmpty()) {
            sec.set("effects", null);
        } else {
            ConfigurationSection eff = sec.createSection("effects");
            for (EffectSpec e : effects.values()) {
                ConfigurationSection s = eff.createSection(e.type().getKey().toString());
                s.set("duration", e.durationSeconds());
                s.set("amplifier", e.amplifier());
            }
        }
    }

    /** 从配置段读取。 */
    public static DuelArena load(String name, ConfigurationSection sec) {
        DuelArena a = new DuelArena(name);
        a.pos1 = (Location) sec.get("pos1");
        a.pos2 = (Location) sec.get("pos2");
        a.spawn1 = (Location) sec.get("spawn1");
        a.manualLobby = (Location) sec.get("manual-lobby");
        a.spawn2 = (Location) sec.get("spawn2");
        a.kit = Kit.load(sec.getConfigurationSection("kit"));
        ConfigurationSection eff = sec.getConfigurationSection("effects");
        if (eff != null) {
            for (String key : eff.getKeys(false)) {
                PotionEffectType type = effectType(key);
                if (type == null) {
                    continue;
                }
                ConfigurationSection s = eff.getConfigurationSection(key);
                int duration = s == null ? 0 : Math.max(0, s.getInt("duration", 0));
                int amplifier = s == null ? 0 : Math.max(0, s.getInt("amplifier", 0));
                a.effects.put(type, new EffectSpec(type, duration, amplifier));
            }
        }
        return a;
    }

    /** 配置里的效果键 → PotionEffectType（兼容带/不带 minecraft: 前缀）。 */
    private static PotionEffectType effectType(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            NamespacedKey nk = key.contains(":")
                    ? NamespacedKey.fromString(key)
                    : NamespacedKey.minecraft(key);
            return nk == null ? null : Registry.EFFECT.get(nk);
        } catch (IllegalArgumentException ex) {
            return null; // 配置里写了非法效果 ID：跳过，别让整个场加载失败
        }
    }
}
