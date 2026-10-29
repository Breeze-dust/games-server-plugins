package com.example.playertitles;

import com.example.playertitles.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class TitleManager {

    private static final String TEAM_PREFIX = "pt_";

    private final PlayerTitlesPlugin plugin;
    private final File file;
    private final Map<String, Title> titles = new LinkedHashMap<>();
    private final Map<UUID, String> assigned = new ConcurrentHashMap<>();
    /** 玩家拥有的头衔（可多个）；与"当前装备"分开存。 */
    private final Map<UUID, Set<String>> owned = new ConcurrentHashMap<>();

    public TitleManager(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "titles.yml");
    }

    public synchronized void load() {
        titles.clear();
        assigned.clear();
        owned.clear();

        boolean firstLoad = !file.exists();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection titleSection = data.getConfigurationSection("titles");
        if (titleSection != null) {
            for (String id : titleSection.getKeys(false)) {
                String name = titleSection.getString(id + ".name", id);
                String color = Text.normalizeColor(titleSection.getString(id + ".color", "&f"));
                String description = titleSection.getString(id + ".description", "&7暂无描述");
                boolean visible = titleSection.getBoolean(id + ".visible", true);
                titles.put(id, new Title(id, name, color == null ? "&f" : color, description, visible));
            }
        }

        ConfigurationSection playerSection = data.getConfigurationSection("players");
        if (playerSection != null) {
            for (String key : playerSection.getKeys(false)) {
                String titleId = playerSection.getString(key);
                if (titleId == null || !titles.containsKey(titleId)) {
                    continue;
                }
                try {
                    assigned.put(UUID.fromString(key), titleId);
                } catch (IllegalArgumentException ignored) {
                    // 忽略损坏的 UUID
                }
            }
        }

        ConfigurationSection ownedSection = data.getConfigurationSection("owned");
        if (ownedSection != null) {
            for (String key : ownedSection.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                Set<String> ids = new HashSet<>();
                for (String id : ownedSection.getStringList(key)) {
                    if (titles.containsKey(id)) {
                        ids.add(id);
                    }
                }
                if (!ids.isEmpty()) {
                    owned.put(uuid, ids);
                }
            }
        }

        // 老数据只有"当前装备"；正在装备的头衔一律视为已拥有，避免升级后丢权。
        boolean migrated = false;
        for (Map.Entry<UUID, String> entry : assigned.entrySet()) {
            if (owned.computeIfAbsent(entry.getKey(), key -> new HashSet<>()).add(entry.getValue())) {
                migrated = true;
            }
        }

        if (firstLoad && titles.isEmpty()) {
            titles.put("xinshou", new Title("xinshou", "新手", "&a", "&7刚刚加入服务器的新玩家"));
            save();
        } else if (migrated) {
            save();
        }
    }

    public synchronized void reload() {
        cleanupTeams();
        load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyNametag(player);
        }
    }

    public synchronized void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Title title : titles.values()) {
            String path = "titles." + title.id();
            data.set(path + ".name", title.name());
            data.set(path + ".color", title.color());
            data.set(path + ".description", title.description());
            data.set(path + ".visible", title.visible());
        }
        for (Map.Entry<UUID, String> entry : assigned.entrySet()) {
            data.set("players." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, Set<String>> entry : owned.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                data.set("owned." + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录");
            }
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("保存 titles.yml 失败: " + exception.getMessage());
        }
    }

    public synchronized boolean exists(String id) {
        return titles.containsKey(id);
    }

    public synchronized Title getTitle(String id) {
        return titles.get(id);
    }

    public synchronized List<Title> getTitles() {
        return List.copyOf(titles.values());
    }

    public synchronized Title getTitle(UUID uuid) {
        String id = assigned.get(uuid);
        return id == null ? null : titles.get(id);
    }

    public synchronized String getTitleId(UUID uuid) {
        return assigned.get(uuid);
    }

    public synchronized void setPlayerTitle(UUID uuid, String titleId) {
        if (titleId == null) {
            assigned.remove(uuid);
        } else if (titles.containsKey(titleId) && owns(uuid, titleId)) {
            assigned.put(uuid, titleId);
        } else {
            return;
        }
        save();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            applyNametag(player);
        }
    }

    /** 玩家是否拥有某个头衔。 */
    public synchronized boolean owns(UUID uuid, String titleId) {
        if (titleId == null) {
            return false;
        }
        Set<String> ids = owned.get(uuid);
        return ids != null && ids.contains(titleId);
    }

    /** 玩家拥有的头衔 ID（只读副本）。 */
    public synchronized Set<String> ownedIds(UUID uuid) {
        Set<String> ids = owned.get(uuid);
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    /** 管理员授予头衔拥有权（不自动装备）。 */
    public synchronized void grant(UUID uuid, String titleId) {
        if (!titles.containsKey(titleId)) {
            return;
        }
        owned.computeIfAbsent(uuid, key -> new HashSet<>()).add(titleId);
        save();
    }

    /** 批量授予；只写一次盘。返回本次新增拥有的人数。 */
    public synchronized int grantAll(Collection<UUID> uuids, String titleId) {
        if (!titles.containsKey(titleId)) {
            return 0;
        }
        int granted = 0;
        for (UUID uuid : uuids) {
            if (uuid == null) {
                continue;
            }
            if (owned.computeIfAbsent(uuid, key -> new HashSet<>()).add(titleId)) {
                granted++;
            }
        }
        if (granted > 0) {
            save();
        }
        return granted;
    }

    /** 收回一个头衔；如果它正被装备则同时卸下。 */
    public synchronized void revoke(UUID uuid, String titleId) {
        Set<String> ids = owned.get(uuid);
        if (ids == null) {
            return;
        }
        ids.remove(titleId);
        if (ids.isEmpty()) {
            owned.remove(uuid);
        }
        if (titleId != null && titleId.equals(assigned.get(uuid))) {
            assigned.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                applyNametag(player);
            }
        }
        save();
    }

    /** 收回该玩家全部头衔（并卸下当前头衔）。 */
    public synchronized void revokeAll(UUID uuid) {
        owned.remove(uuid);
        if (assigned.remove(uuid) != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                applyNametag(player);
            }
        }
        save();
    }

    /** 有多少玩家拥有该头衔。 */
    public synchronized int ownerCount(String titleId) {
        int count = 0;
        for (Set<String> ids : owned.values()) {
            if (ids.contains(titleId)) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean create(String id, String name, String color, String description) {
        if (titles.containsKey(id)) {
            return false;
        }
        titles.put(id, new Title(id, name, color, description));
        save();
        return true;
    }

    public synchronized void update(Title title) {
        if (!titles.containsKey(title.id())) {
            return;
        }
        titles.put(title.id(), title);
        save();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (title.id().equals(assigned.get(player.getUniqueId()))) {
                applyNametag(player);
            }
        }
    }

    public synchronized boolean delete(String id) {
        if (titles.remove(id) == null) {
            return false;
        }
        List<UUID> affected = new ArrayList<>();
        assigned.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(id)) {
                affected.add(entry.getKey());
                return true;
            }
            return false;
        });
        for (Set<String> ids : owned.values()) {
            ids.remove(id);
        }
        owned.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        save();
        unregisterTeam(id);
        for (UUID uuid : affected) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeFromTeams(player);
            }
        }
        return true;
    }

    public synchronized void applyNametag(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = player.getName();
        clearEntry(board, entry);

        Title title = getTitle(player.getUniqueId());
        if (title == null) {
            return;
        }
        String teamName = teamName(title.id());
        Team team = board.getTeam(teamName);
        if (team == null) {
            try {
                team = board.registerNewTeam(teamName);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("无法创建头衔计分板队伍 " + teamName + ": " + exception.getMessage());
                return;
            }
        }
        team.prefix(title.bracketPrefix());
        team.addEntry(entry);
    }

    public synchronized void removeFromTeams(Player player) {
        clearEntry(Bukkit.getScoreboardManager().getMainScoreboard(), player.getName());
    }

    public synchronized void cleanupTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (!team.getName().startsWith(TEAM_PREFIX)) {
                continue;
            }
            for (String entry : new ArrayList<>(team.getEntries())) {
                team.removeEntry(entry);
            }
            team.unregister();
        }
    }

    private void clearEntry(Scoreboard board, String entry) {
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.removeEntry(entry);
            }
        }
    }

    private void unregisterTeam(String id) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(id));
        if (team != null) {
            team.unregister();
        }
    }

    private String teamName(String id) {
        String clean = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (clean.isEmpty()) {
            clean = "t";
        }
        if (clean.length() > 9) {
            clean = clean.substring(0, 9);
        }
        String hash = Integer.toHexString(id.hashCode());
        if (hash.length() > 3) {
            hash = hash.substring(hash.length() - 3);
        }
        return TEAM_PREFIX + clean + "_" + hash;
    }
}
