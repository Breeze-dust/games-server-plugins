package com.lobbytoys;

import com.destroystokyo.paper.SkinParts;
import com.hub.util.Chat;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家最多一个分身：Mannequin 站在玩家所在位置，每刻跟随 + 自转 + 缩放弹跳。
 * 玩家本体用隐身效果藏起来，镜头/朝向一律不动。
 */
public final class CloneManager {

    private static final int INVISIBILITY_TICKS = 20 * 60;
    /** 分身专用队伍：碰撞规则 NEVER，保证不推玩家、也不被玩家推。 */
    private static final String NO_COLLISION_TEAM = "lobbytoys";

    private final LobbyToysPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public CloneManager(LobbyToysPlugin plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void toggle(Player player) {
        Settings settings = plugin.settings();
        if (!player.hasPermission("lobbytoys.use")) {
            Chat.actionBar(player, "&c你没有使用小玩具的权限");
            return;
        }
        if (!settings.isAllowedWorld(player.getWorld())) {
            Chat.actionBar(player, "&c分身玩具只能在主城使用");
            return;
        }
        if (plugin.isPlaying(player)) {
            Chat.actionBar(player, "&c游戏中不能使用小玩具");
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < settings.cooldownMillis) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);

        if (sessions.containsKey(player.getUniqueId())) {
            stop(player.getUniqueId(), true);
        } else {
            start(player);
        }
    }

    public boolean isActive(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** 配置里的 hide-from-owner 改了以后，让已经在场的分身立刻按新设置显示/隐藏。 */
    public void refreshVisibility() {
        boolean hide = plugin.settings().hideFromOwner;
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (hide) {
                player.hideEntity(plugin, entry.getValue().clone);
            } else {
                player.showEntity(plugin, entry.getValue().clone);
            }
        }
    }

    public void stop(UUID playerId, boolean notify) {
        Session session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        teardown(Bukkit.getPlayer(playerId), session, notify);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            leaveNoCollisionTeam(session.clone);
            if (session.clone.isValid()) {
                session.clone.remove();
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.showEntity(plugin, session.clone);
                if (!session.hadInvisibility) {
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
        }
        sessions.clear();
    }

    private void start(Player player) {
        Location base = player.getLocation();
        Mannequin clone;
        try {
            clone = player.getWorld().spawn(base, Mannequin.class);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("生成分身失败：" + ex.getMessage());
            Chat.actionBar(player, "&c分身生成失败，请看控制台");
            return;
        }

        configure(clone, player);
        if (plugin.settings().hideFromOwner) {
            player.hideEntity(plugin, clone);
        }

        sessions.put(player.getUniqueId(), new Session(
                clone,
                player.hasPotionEffect(PotionEffectType.INVISIBILITY),
                base.getYaw(),
                clone.getAttribute(Attribute.SCALE)));
        applyInvisibility(player);
        Chat.actionBar(player, plugin.settings().hideFromOwner
                ? "&a分身已开启 &7（其他玩家能看到你的分身）"
                : "&a分身已开启");
    }

    private void configure(Mannequin clone, Player player) {
        clone.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
        clone.setDescription(Component.empty());
        clone.setImmovable(true);
        clone.setInvulnerable(true);
        clone.setCollidable(false);
        clone.setNoPhysics(true);
        clone.setGravity(false);
        clone.setSilent(true);
        clone.setPersistent(false);
        clone.setCustomNameVisible(false);
        clone.setMainHand(player.getMainHand());

        joinNoCollisionTeam(clone);

        SkinParts.Mutable parts = SkinParts.allParts().mutableCopy();
        clone.setSkinParts(parts);

        clone.getEquipment().clear();

        Location start = player.getLocation();
        start.setPitch(0.0f);
        clone.teleport(start);
    }

    /**
     * 分身和玩家站在同一坐标，必须完全不参与实体推挤，否则原版分离会把玩家顶开。
     * COLLISION_RULE=NEVER 的队伍会让 EntitySelector.pushableBy 直接返回空集。
     */
    private static void joinNoCollisionTeam(Mannequin clone) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(NO_COLLISION_TEAM);
        if (team == null) {
            team = board.registerNewTeam(NO_COLLISION_TEAM);
        }
        if (team.getOption(Team.Option.COLLISION_RULE) != Team.OptionStatus.NEVER) {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        team.addEntity(clone);
    }

    private static void leaveNoCollisionTeam(Mannequin clone) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(NO_COLLISION_TEAM);
        if (team != null) {
            team.removeEntry(clone.getUniqueId().toString());
        }
    }

    private void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        ticks++;
        Settings settings = plugin.settings();

        Iterator<Map.Entry<UUID, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());

            if (player == null || !player.isOnline() || player.isDead()
                    || !settings.isAllowedWorld(player.getWorld())
                    || !session.clone.isValid()
                    || (ticks % 20 == 0 && plugin.isPlaying(player))) {
                iterator.remove();
                teardown(player, session, false);
                continue;
            }

            session.yaw = (session.yaw + settings.spinDegreesPerTick) % 360.0;
            session.phase++;

            if (session.scale != null && settings.squashAmount > 0.0) {
                // 0..1 的正弦波：0 = 原大，1 = 压到最扁
                double wave = (Math.sin(session.phase * (Math.PI * 2.0 / settings.squashPeriodTicks)) + 1.0) / 2.0;
                session.scale.setBaseValue(1.0 - settings.squashAmount * wave);
            }

            Location target = player.getLocation();
            target.setYaw((float) session.yaw);
            target.setPitch(0.0f);

            session.clone.teleport(target);
            session.clone.setBodyYaw((float) session.yaw);

            if (ticks % 20 == 0) {
                applyInvisibility(player);
            }
        }
    }

    private static void applyInvisibility(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (current != null && current.getDuration() > 200) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, INVISIBILITY_TICKS, 0, false, false, false));
    }

    private void teardown(Player player, Session session, boolean notify) {
        leaveNoCollisionTeam(session.clone);
        if (session.clone.isValid()) {
            session.clone.remove();
        }
        if (player != null && player.isOnline()) {
            player.showEntity(plugin, session.clone);
            if (!session.hadInvisibility) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            if (notify) {
                Chat.actionBar(player, "&7分身已收回");
            }
        }
    }

    private static final class Session {
        private final Mannequin clone;
        private final boolean hadInvisibility;
        private final AttributeInstance scale;
        private double yaw;
        private double phase;

        private Session(Mannequin clone, boolean hadInvisibility, float startYaw, AttributeInstance scale) {
            this.clone = clone;
            this.hadInvisibility = hadInvisibility;
            this.scale = scale;
            this.yaw = startYaw;
            this.phase = 0;
        }
    }
}
