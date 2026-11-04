package com.lobbytoys;

import com.destroystokyo.paper.SkinParts;
import com.hub.util.Chat;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家最多一个分身：Mannequin 站在玩家所在位置，每刻跟随 + 自转 + 正弦上下弹动。
 * 玩家本体用隐身效果藏起来，镜头/朝向一律不动。
 */
public final class CloneManager {

    private static final int INVISIBILITY_TICKS = 20 * 60;

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

        sessions.put(player.getUniqueId(),
                new Session(clone, player.hasPotionEffect(PotionEffectType.INVISIBILITY), base.getYaw()));
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
        clone.setGravity(false);
        clone.setSilent(true);
        clone.setPersistent(false);
        clone.setCustomNameVisible(false);
        clone.setMainHand(player.getMainHand());

        SkinParts.Mutable parts = SkinParts.allParts().mutableCopy();
        clone.setSkinParts(parts);

        copyEquipment(player, clone);

        Location start = player.getLocation();
        start.setPitch(0.0f);
        clone.teleport(start);
    }

    private static void copyEquipment(Player source, Mannequin target) {
        EntityEquipment from = source.getEquipment();
        EntityEquipment to = target.getEquipment();
        if (from == null || to == null) {
            return;
        }
        to.setHelmet(orAir(from.getHelmet()));
        to.setChestplate(orAir(from.getChestplate()));
        to.setLeggings(orAir(from.getLeggings()));
        to.setBoots(orAir(from.getBoots()));
        to.setItemInMainHand(orAir(from.getItemInMainHand()));
        to.setItemInOffHand(orAir(from.getItemInOffHand()));
    }

    private static ItemStack orAir(ItemStack stack) {
        return stack == null ? new ItemStack(org.bukkit.Material.AIR) : stack;
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
                    || !session.clone.isValid()) {
                iterator.remove();
                teardown(player, session, false);
                continue;
            }

            session.yaw = (session.yaw + settings.spinDegreesPerTick) % 360.0;
            session.phase++;

            double bob = Math.sin(session.phase * (Math.PI * 2.0 / settings.bobPeriodTicks))
                    * settings.bobAmplitude;
            Location base = player.getLocation();
            Location target = base.clone();
            target.setY(base.getY() + bob);
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
        private double yaw;
        private double phase;

        private Session(Mannequin clone, boolean hadInvisibility, float startYaw) {
            this.clone = clone;
            this.hadInvisibility = hadInvisibility;
            this.yaw = startYaw;
            this.phase = 0;
        }
    }
}
