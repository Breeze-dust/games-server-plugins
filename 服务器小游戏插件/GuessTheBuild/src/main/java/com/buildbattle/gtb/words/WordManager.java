package com.buildbattle.gtb.words;

import com.buildbattle.gtb.GuessBuildPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 中文分类词库：**独立存放在插件目录的 words.yml**（不再写进 config.yml），
 * 支持运行时增删词语并持久化。
 * <p>
 * 首次升级时若 config.yml 里还留有旧的 {@code words:} 段，会自动并入 words.yml
 * 并把该段从 config.yml 删掉（一次性迁移，避免老词库丢失）。
 */
public class WordManager {

    /** 词库文件名（放在插件数据目录下）。 */
    private static final String FILE_NAME = "words.yml";

    private final GuessBuildPlugin plugin;
    private final Map<String, List<String>> words = new LinkedHashMap<>();
    private final Random random = new Random();
    private final File file;
    private YamlConfiguration wordsConfig;
    private int maxLen = 5;

    public WordManager(GuessBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        reload();
    }

    /** 载入 words.yml（不存在就从 jar 里释放默认词库），并做一次 config.yml 旧词库迁移。 */
    private void loadFile() {
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        wordsConfig = YamlConfiguration.loadConfiguration(file);
        migrateFromConfig();
    }

    /** 把 config.yml 里遗留的 words: 段并进 words.yml，然后从 config.yml 移除（只会在升级时用到）。 */
    private void migrateFromConfig() {
        ConfigurationSection old = plugin.getConfig().getConfigurationSection("words");
        if (old == null || old.getKeys(false).isEmpty()) {
            return;
        }
        int moved = 0;
        for (String category : old.getKeys(false)) {
            List<String> list = new ArrayList<>(wordsConfig.getStringList(category));
            for (String word : old.getStringList(category)) {
                if (!list.contains(word)) {
                    list.add(word);
                    moved++;
                }
            }
            wordsConfig.set(category, list);
        }
        plugin.getConfig().set("words", null); // 词库从此只在 words.yml 里
        plugin.saveConfig();
        saveFile();
        plugin.getLogger().info("词库已迁移到 " + FILE_NAME + "（并入 " + moved + " 个词，config.yml 中的 words 段已移除）");
    }

    /** 保存 words.yml。 */
    private void saveFile() {
        try {
            wordsConfig.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 " + FILE_NAME + " 失败：" + e.getMessage());
        }
    }

    public void reload() {
        words.clear();
        maxLen = Math.max(1, plugin.getConfig().getInt("game.max-word-length", 5));
        loadFile();
        for (String category : wordsConfig.getKeys(false)) {
            List<String> list = wordsConfig.getStringList(category);
            if (!list.isEmpty()) {
                words.put(category, new ArrayList<>(list));
            }
        }
    }

    /** 题目最大字数（从配置读取）。 */
    public int getMaxLength() {
        return maxLen;
    }

    /** 是否在题目长度限制内。 */
    private boolean fits(String w) {
        return w.length() <= maxLen;
    }

    public boolean isEmpty() {
        for (List<String> list : words.values()) {
            if (!list.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int totalCount() {
        int n = 0;
        for (List<String> list : words.values()) {
            n += list.size();
        }
        return n;
    }

    /** 随机取一个未用过的词（限长）；所有词都用过一轮后自动重置。 */
    public Word randomWord(Set<String> used) {
        List<String> pool = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : words.entrySet()) {
            for (String w : e.getValue()) {
                if (fits(w) && !used.contains(w)) {
                    pool.add(e.getKey() + '\u0000' + w);
                }
            }
        }
        if (pool.isEmpty()) {
            // 限长后无可用词（词库全超长）：忽略限长再取；仍无则重置去重
            for (Map.Entry<String, List<String>> e : words.entrySet()) {
                for (String w : e.getValue()) {
                    if (!used.contains(w)) {
                        pool.add(e.getKey() + '\u0000' + w);
                    }
                }
            }
            if (pool.isEmpty()) {
                used.clear();
                return randomWord(used);
            }
        }
        String[] parts = pool.get(random.nextInt(pool.size())).split("\u0000", 2);
        used.add(parts[1]);
        return new Word(parts[1], parts[0]);
    }

    /**
     * 随机挑 n 个候选题目（具体词，限长且未用过）。
     * 不足 n 个时用已用过的词补足；候选本身不会标记为已用，只有最终被选中的词才去重。
     */
    public List<Word> pickWords(int n, Set<String> used) {
        List<Word> unused = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : words.entrySet()) {
            for (String w : e.getValue()) {
                if (fits(w) && !used.contains(w)) {
                    unused.add(new Word(w, e.getKey()));
                }
            }
        }
        java.util.Collections.shuffle(unused, random);
        if (unused.size() >= n) {
            return new ArrayList<>(unused.subList(0, n));
        }
        // 词库不足：从已用词里补足（限长）
        List<Word> all = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : words.entrySet()) {
            for (String w : e.getValue()) {
                if (fits(w)) {
                    all.add(new Word(w, e.getKey()));
                }
            }
        }
        // 极端情况：词库全部超长时忽略限长
        if (all.isEmpty()) {
            for (Map.Entry<String, List<String>> e : words.entrySet()) {
                for (String w : e.getValue()) {
                    all.add(new Word(w, e.getKey()));
                }
            }
        }
        java.util.Collections.shuffle(all, random);
        List<Word> out = new ArrayList<>(unused);
        for (Word w : all) {
            if (out.size() >= n) {
                break;
            }
            if (!out.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    public boolean addWord(String word, String category) {
        String cat = (category == null || category.isBlank()) ? "其他" : category.trim();
        List<String> list = new ArrayList<>(wordsConfig.getStringList(cat));
        if (list.contains(word)) {
            return false;
        }
        list.add(word);
        wordsConfig.set(cat, list);
        saveFile();
        reload();
        return true;
    }

    public boolean removeWord(String word) {
        for (String cat : wordsConfig.getKeys(false)) {
            List<String> list = new ArrayList<>(wordsConfig.getStringList(cat));
            if (list.remove(word)) {
                wordsConfig.set(cat, list);
                saveFile();
                reload();
                return true;
            }
        }
        return false;
    }

    /** 查找单词，找不到返回 null（用于 ActionBar 显示分类）。 */
    public Word find(String word) {
        for (Map.Entry<String, List<String>> e : words.entrySet()) {
            if (e.getValue().contains(word)) {
                return new Word(word, e.getKey());
            }
        }
        return null;
    }

    // ---------- 自创词黑名单（词） ----------

    /** 该自创词是否在词黑名单中（评分过低被拉黑，不能再作为自创词使用/入库）。 */
    public boolean isSelfmadeWordBanned(String word) {
        return plugin.getConfig().getStringList("game.selfmade-word-blacklist").contains(word);
    }

    /** 把自创词加入词黑名单并持久化；已存在返回 false。 */
    public boolean addSelfmadeBannedWord(String word) {
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("game.selfmade-word-blacklist"));
        if (list.contains(word)) {
            return false;
        }
        list.add(word);
        plugin.getConfig().set("game.selfmade-word-blacklist", list);
        plugin.saveConfig();
        return true;
    }

    /** 从词黑名单移除并持久化；不存在返回 false。 */
    public boolean removeSelfmadeBannedWord(String word) {
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("game.selfmade-word-blacklist"));
        if (list.remove(word)) {
            plugin.getConfig().set("game.selfmade-word-blacklist", list);
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    public List<String> getSelfmadeBannedWords() {
        return plugin.getConfig().getStringList("game.selfmade-word-blacklist");
    }

    public Map<String, List<String>> getAll() {
        return words;
    }
}
