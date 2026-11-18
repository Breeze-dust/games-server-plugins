package com.liarbar;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 酒名与喝完效果配置（独立文件 liquor.yml，可在线上直接改）。 */
public final class LiarBarLiquor {

    private static final List<String> DEFAULT_PREFIXES = List.of(
            "陈年的", "冒泡的", "发霉的", "不明来历的", "烧喉的", "温热的",
            "廉价的", "珍藏的", "过期的", "神秘的", "烈性的", "苦涩的");
    private static final List<String> DEFAULT_NAMES = List.of(
            "伏特加", "威士忌", "朗姆酒", "龙舌兰", "白兰地", "金酒",
            "二锅头", "清酒", "梅酒", "黄酒", "苦艾酒", "朗姆可乐");

    private final Plugin plugin;
    private final LiarBarItems items;

    private List<String> prefixes = DEFAULT_PREFIXES;
    private List<String> names = DEFAULT_NAMES;
    private int nauseaMinSeconds = 4;
    private int nauseaMaxSeconds = 12;
    private double drinkSeconds = 2.5;
    private int giveCooldownSeconds = 30;
    private double throwSpeed = 1.4;
    private double knockbackHorizontal = 0.45;
    private double knockbackVertical = 0.2;
    private double bottleDamage = 0.5;

    public LiarBarLiquor(Plugin plugin, LiarBarItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "liquor.yml");
        if (!file.isFile()) {
            plugin.saveResource("liquor.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        List<String> loadedPrefixes = config.getStringList("prefixes");
        List<String> loadedNames = config.getStringList("names");
        prefixes = loadedPrefixes.isEmpty() ? DEFAULT_PREFIXES : loadedPrefixes;
        names = loadedNames.isEmpty() ? DEFAULT_NAMES : loadedNames;

        nauseaMinSeconds = Math.max(1, config.getInt("nausea.min-seconds", 4));
        nauseaMaxSeconds = Math.max(nauseaMinSeconds, config.getInt("nausea.max-seconds", 12));
        drinkSeconds = Math.max(0.5, Math.min(10.0, config.getDouble("drink.seconds", 2.5)));
        giveCooldownSeconds = Math.max(0, config.getInt("give-cooldown-seconds", 30));
        throwSpeed = Math.max(0.2, config.getDouble("throw.speed", 1.4));
        knockbackHorizontal = Math.max(0.0, config.getDouble("throw.knockback-horizontal", 0.45));
        knockbackVertical = Math.max(0.0, config.getDouble("throw.knockback-vertical", 0.2));
        bottleDamage = Math.max(0.0, config.getDouble("throw.damage", 0.5));
    }

    /** 随机名字 + 随机颜色的酒。 */
    public ItemStack randomLiquor() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String prefix = prefixes.get(random.nextInt(prefixes.size()));
        String name = names.get(random.nextInt(names.size()));
        String display = "&6" + prefix + "·" + name;
        return items.liquor(display, random.nextInt(0xFFFFFF));
    }

    /** 喝完后的反胃时长（tick）。 */
    public int randomNauseaTicks() {
        int seconds = nauseaMinSeconds
                + ThreadLocalRandom.current().nextInt(nauseaMaxSeconds - nauseaMinSeconds + 1);
        return seconds * 20;
    }

    /** 喝酒过程时长（tick）。 */
    public int drinkTicks() {
        return Math.max(10, (int) Math.round(drinkSeconds * 20));
    }

    /** 玩家自助领酒指令的冷却（秒）。 */
    public int giveCooldownSeconds() {
        return giveCooldownSeconds;
    }

    public double throwSpeed() {
        return throwSpeed;
    }

    public double knockbackHorizontal() {
        return knockbackHorizontal;
    }

    public double knockbackVertical() {
        return knockbackVertical;
    }

    public double bottleDamage() {
        return bottleDamage;
    }
}
