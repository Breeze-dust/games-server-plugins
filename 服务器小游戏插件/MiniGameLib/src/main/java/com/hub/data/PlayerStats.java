package com.hub.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 全服唯一的玩家记录文件（战绩 / 击杀 / 玩家名）。
 *
 * <p>文件：{@code server/plugins/MiniGameLib/players.yml}
 * <pre>
 * version: 1
 * games:
 *   duel:        # 每个插件的命名空间，内容 == 该插件原来的 data.yml
 *     wins:
 *       per-arena: {...}
 *       total: {...}
 *   gtb:
 *     firsts: {...}
 * </pre>
 *
 * <p>各插件依旧用一个 {@link YamlConfiguration} 读写自己那一块（互相看不见），
 * 只是落盘统一到这一个文件：{@link #save} 会**重新读盘 → 只替换自己那块 → 原子替换文件**，
 * 所以多个插件轮流保存不会互相覆盖，也不会因为写一半崩溃而丢整个文件。
 *
 * <p>老版本的 {@code plugins/<插件>/data.yml} 在第一次加载时自动迁移进来（旧文件保留不删），
 * 迁移后旧文件不再被读取。
 *
 * <p>备份/上传：只需要保存/还原这一个 {@code players.yml}；
 * 命令行见仓库根目录 {@code players-backup.ps1}。
 */
public final class PlayerStats {

    /** 唯一的玩家记录文件名。 */
    public static final String FILE_NAME = "players.yml";

    private static final String ROOT = "games.";
    private static final String VERSION_KEY = "version";
    private static final int VERSION = 1;

    /** 末段键名形如 UUID 的，算一条“某玩家”的记录。 */
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private PlayerStats() {
    }

    /** server/plugins/MiniGameLib/players.yml */
    public static File file() {
        File dir = null;
        try {
            Plugin lib = Bukkit.getPluginManager().getPlugin("MiniGameLib");
            if (lib != null) {
                dir = lib.getDataFolder();
            }
        } catch (Throwable ignored) {
            // 没有跑在服务端里（离线自检 / 单元测试）：退回工作目录
        }
        if (dir == null) {
            dir = new File("plugins", "MiniGameLib");
        }
        return new File(dir, FILE_NAME);
    }

    /**
     * 读某个插件的记录块。
     *
     * @param namespace  命名空间（duel / gtb / hotpotato / tntrun / murdermystery / skypit）
     * @param legacyFile 老 data.yml；仅在自己那块还不存在时用来做一次性迁移，可为 null
     * @return 该插件的独立视图（改它不影响别的插件；落盘请调 {@link #save}）
     */
    public static YamlConfiguration load(String namespace, File legacyFile) {
        YamlConfiguration view = new YamlConfiguration();
        YamlConfiguration root = readRoot();

        ConfigurationSection sec = root.getConfigurationSection(ROOT + namespace);
        if (sec != null) {
            copy(sec, view);
            return view;
        }

        if (legacyFile != null && legacyFile.isFile()) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(legacyFile);
            if (!old.getKeys(false).isEmpty()) {
                copy(old, view);
                try {
                    root.set(ROOT + namespace, old);
                    root.set(VERSION_KEY, VERSION);
                    write(root);
                    log("已迁移 " + legacyFile.getName() + " -> " + FILE_NAME + " [" + namespace + "]："
                            + old.getValues(true).size() + " 条记录（旧文件保留，之后不再读取）");
                } catch (IOException ex) {
                    log("迁移 " + legacyFile.getName() + " 失败（本次仍读到旧数据，稍后保存会再写一次）：" + ex.getMessage());
                }
            }
        }
        return view;
    }

    /**
     * 把某个插件的记录块写回唯一文件（先读盘合并，再原子替换）。
     *
     * @throws IOException 写盘失败时抛出，调用方照旧 catch 打警告即可
     */
    public static void save(String namespace, ConfigurationSection view) throws IOException {
        YamlConfiguration root = readRoot();
        root.set(ROOT + namespace, null);
        ConfigurationSection target = root.createSection(ROOT + namespace);
        copy(view, target);
        root.set(VERSION_KEY, VERSION);
        write(root);
    }

    /** 每个命名空间的玩家数（按 UUID 去重），用于启动日志 / 排障。 */
    public static Map<String, Integer> playerCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        YamlConfiguration root = readRoot();
        ConfigurationSection games = root.getConfigurationSection("games");
        if (games == null) {
            return out;
        }
        for (String ns : games.getKeys(false)) {
            ConfigurationSection sec = games.getConfigurationSection(ns);
            if (sec == null) {
                continue;
            }
            Set<String> ids = new LinkedHashSet<>();
            for (String path : sec.getValues(true).keySet()) {
                int dot = path.lastIndexOf('.');
                String leaf = (dot < 0) ? path : path.substring(dot + 1);
                if (UUID.matcher(leaf).matches()) {
                    ids.add(leaf.toLowerCase());
                }
            }
            out.put(ns, ids.size());
        }
        return out;
    }

    /** 形如 {@code duel=3, gtb=5}；文件不存在或没有记录时返回 {@code 空}。 */
    public static String summary() {
        Map<String, Integer> counts = playerCounts();
        if (counts.isEmpty()) {
            return "空";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ---------- 内部 ----------

    private static YamlConfiguration readRoot() {
        File f = file();
        YamlConfiguration root = (f.isFile())
                ? YamlConfiguration.loadConfiguration(f)
                : new YamlConfiguration();
        root.options().setHeader(java.util.List.of(
                "服务器唯一的玩家记录文件（战绩 / 击杀 / 玩家名）—— 由 MiniGameLib 统一读写。",
                "备份或换服：保存/还原这一个文件即可（仓库根目录 players-backup.ps1）。",
                "结构：games.<命名空间> = 各插件原来的 data.yml 内容；不要手改层级。",
                "打包上传：pack-upload.ps1 默认不覆盖本文件，避免上传时冲掉线上玩家记录。"));
        return root;
    }

    private static void write(YamlConfiguration root) throws IOException {
        File f = file();
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory()) {
            dir.mkdirs();
        }
        File tmp = new File(dir, FILE_NAME + ".tmp");
        root.save(tmp);
        try {
            Files.move(tmp.toPath(), f.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copy(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            to.set(key, from.get(key));
        }
    }

    private static void log(String msg) {
        try {
            Bukkit.getLogger().info("[MiniGameLib] " + msg);
        } catch (Throwable ignored) {
            // 离线自检（没有服务端实例）时退回标准输出
            System.out.println("[MiniGameLib] " + msg);
        }
    }
}
