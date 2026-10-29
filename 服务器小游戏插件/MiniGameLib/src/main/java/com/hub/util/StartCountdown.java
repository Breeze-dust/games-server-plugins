package com.hub.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小游戏开局前的 title 倒计时（「游戏即将开始：3 / 2 / 1」）。
 *
 * <p>用法：在游戏的“开始”方法最前面加一行
 * <pre>
 *   if (startCountdown.begin(arenaName, playerIds, () -&gt; start(...))) return;
 * </pre>
 * 第一次调用会开始倒计时并返回 true（调用方直接 return）；倒计时结束时自动重新调用
 * 同一个开始方法，这一次 {@code begin} 返回 false，方法继续执行真正的开局逻辑。
 *
 * <p>配置（每个插件自己的 config.yml，可缺省）：
 * <pre>
 * countdown:
 *   enabled: true          # false = 不启用倒计时
 *   seconds: 3             # 倒计时秒数
 *   freeze: true           # 倒计时期间是否锁住玩家移动（仍可转头）
 *   title: '&amp;e游戏即将开始：{time}'
 *   subtitle: '&amp;7准备好'
 * </pre>
 * 缺少的键会在第一次用到时自动写回配置。
 */
public final class StartCountdown {

    private static final String PATH_ENABLED = "countdown.enabled";
    private static final String PATH_SECONDS = "countdown.seconds";
    private static final String PATH_FREEZE = "countdown.freeze";
    private static final String PATH_TITLE = "countdown.title";
    private static final String PATH_SUBTITLE = "countdown.subtitle";

    private final Plugin plugin;
    private final Map<String, Job> jobs = new HashMap<>();
    /** 刚结束、正在回调里的 key：回调中再次进入 begin 时返回 false。 */
    private final Set<String> finishing = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();

    /** 街机派对接管时置 true：所有游戏不再播自己的开局倒计时。 */
    private static volatile boolean suppressed;

    public static void setGloballySuppressed(boolean value) {
        suppressed = value;
    }

    /** 街机派对是否正在接管（自带倒计时的游戏用它决定要不要直接开局）。 */
    public static boolean isSuppressed() {
        return suppressed;
    }

    private static final class Job {
        BukkitTask task;
        int remaining;
        final Set<UUID> players;
        final Runnable onFinish;
        final boolean freeze;

        Job(Set<UUID> players, Runnable onFinish, boolean freeze, int remaining) {
            this.players = players;
            this.onFinish = onFinish;
            this.freeze = freeze;
            this.remaining = remaining;
        }
    }

    public StartCountdown(Plugin plugin) {
        this.plugin = plugin;
        ensureDefaults();
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST)
            public void onMove(PlayerMoveEvent event) {
                if (frozen.contains(event.getPlayer().getUniqueId()) && event.getTo() != null) {
                    Location from = event.getFrom();
                    Location to = event.getTo();
                    event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                            to.getYaw(), to.getPitch()));
                }
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                UUID id = event.getPlayer().getUniqueId();
                frozen.remove(id);
                for (Job job : jobs.values()) {
                    job.players.remove(id);
                }
            }
        }, plugin);
    }

    private void ensureDefaults() {
        boolean changed = false;
        if (!plugin.getConfig().isSet(PATH_ENABLED)) {
            plugin.getConfig().set(PATH_ENABLED, true);
            changed = true;
        }
        if (!plugin.getConfig().isSet(PATH_SECONDS)) {
            plugin.getConfig().set(PATH_SECONDS, 3);
            changed = true;
        }
        if (!plugin.getConfig().isSet(PATH_FREEZE)) {
            plugin.getConfig().set(PATH_FREEZE, true);
            changed = true;
        }
        if (!plugin.getConfig().isSet(PATH_TITLE)) {
            plugin.getConfig().set(PATH_TITLE, "&e游戏即将开始：{time}");
            changed = true;
        }
        if (!plugin.getConfig().isSet(PATH_SUBTITLE)) {
            plugin.getConfig().set(PATH_SUBTITLE, "&7准备好");
            changed = true;
        }
        if (changed) {
            plugin.saveConfig();
        }
    }

    /**
     * 开始倒计时；true = 这次调用刚启动倒计时（调用方应直接 return），
     * false = 倒计时刚结束（这次继续执行真正的开局逻辑）。
     */
    public boolean begin(String key, Collection<UUID> players, Runnable onFinish) {
        if (suppressed) {
            return false;
        }
        if (!plugin.getConfig().getBoolean(PATH_ENABLED, true)) {
            return false;
        }
        int seconds = Math.max(0, Math.min(10, plugin.getConfig().getInt(PATH_SECONDS, 3)));
        if (seconds <= 0) {
            return false;
        }
        if (finishing.remove(key)) {
            return false;
        }
        if (jobs.containsKey(key)) {
            return true;
        }
        boolean freeze = plugin.getConfig().getBoolean(PATH_FREEZE, true);
        Job job = new Job(new HashSet<>(players), onFinish, freeze, seconds);
        jobs.put(key, job);
        if (freeze) {
            frozen.addAll(job.players);
        }
        showTitle(job);
        job.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(key, job), 20L, 20L);
        return true;
    }

    private void tick(String key, Job job) {
        job.remaining--;
        if (job.remaining <= 0) {
            jobs.remove(key);
            if (job.task != null) {
                job.task.cancel();
            }
            if (job.freeze) {
                frozen.removeAll(job.players);
            }
            finishing.add(key);
            try {
                job.onFinish.run();
            } finally {
                finishing.remove(key);
            }
            return;
        }
        showTitle(job);
    }

    private void showTitle(Job job) {
        String title = plugin.getConfig().getString(PATH_TITLE, "&e游戏即将开始：{time}");
        String subtitle = plugin.getConfig().getString(PATH_SUBTITLE, "&7准备好");
        if (title == null) {
            title = "&e游戏即将开始：{time}";
        }
        title = title.replace("{time}", String.valueOf(job.remaining));
        for (UUID id : new ArrayList<>(job.players)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Chat.title(p, title, subtitle == null ? "" : subtitle, 1);
            }
        }
    }

    /** 取消某个 key 的倒计时（不触发回调）。 */
    public void cancel(String key) {
        Job job = jobs.remove(key);
        if (job == null) {
            return;
        }
        if (job.task != null) {
            job.task.cancel();
        }
        if (job.freeze) {
            frozen.removeAll(job.players);
        }
    }

    /** 取消所有倒计时（插件 disable 时调用）。 */
    public void cancelAll() {
        for (String key : new ArrayList<>(jobs.keySet())) {
            cancel(key);
        }
        frozen.clear();
    }

    public boolean isRunning(String key) {
        return jobs.containsKey(key);
    }

    /** 该玩家是否正被倒计时锁住（其它监听器可用来做额外限制）。 */
    public boolean isFrozen(UUID id) {
        return frozen.contains(id);
    }
}
