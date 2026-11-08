package com.lobbytoys.scratch;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.map.MapPalette;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * config.yml 里 scratch 段的快照。整段缺失时用内置默认值，保证命令开箱可用。
 * 图片放在 plugins/LobbyToys/scratch-images/ 下，奖品用 image 字段引用文件名。
 */
public final class ScratchSettings {

    public final boolean enabled;
    /** 奖品格子的行数（1-6）。 */
    public final int rows;
    /** 奖品格子的列数（1-6）。 */
    public final int columns;
    /** 刮除笔刷半径（地图像素）。 */
    public final int brushRadius;
    /** 一格被刮掉多少比例算揭开（0.2-1.0）。 */
    public final double revealThreshold;
    /** 单张卡最长存在时间（秒）。 */
    public final int sessionSeconds;
    /** 屏幕离玩家眼睛的距离（格）。 */
    public final double screenDistance;
    /** 玩家离屏幕超过这个距离就收回（格）。 */
    public final double maxDistance;
    /** 同时存在的刮刮乐上限，也是地图 id 池上限。 */
    public final int maxSessions;
    /** true = 只有点开的人能看到自己的卡面；false = 附近所有玩家都看得到。 */
    public final boolean hideFromOthers;
    /** 所有格子都刮开后是否自动收回。 */
    public final boolean closeWhenComplete;
    /** 全部刮开后多少秒收回。 */
    public final int closeDelaySeconds;
    public final List<ScratchPrize> prizes;
    private final double totalWeight;

    private ScratchSettings(boolean enabled, int rows, int columns, int brushRadius, double revealThreshold,
                            int sessionSeconds, double screenDistance, double maxDistance, int maxSessions,
                            boolean hideFromOthers, boolean closeWhenComplete, int closeDelaySeconds,
                            List<ScratchPrize> prizes) {
        this.enabled = enabled;
        this.rows = rows;
        this.columns = columns;
        this.brushRadius = brushRadius;
        this.revealThreshold = revealThreshold;
        this.sessionSeconds = sessionSeconds;
        this.screenDistance = screenDistance;
        this.maxDistance = maxDistance;
        this.maxSessions = maxSessions;
        this.hideFromOthers = hideFromOthers;
        this.closeWhenComplete = closeWhenComplete;
        this.closeDelaySeconds = closeDelaySeconds;
        this.prizes = List.copyOf(prizes);
        double sum = 0;
        for (ScratchPrize prize : prizes) {
            sum += prize.weight();
        }
        this.totalWeight = sum;
    }

    public static ScratchSettings load(Plugin plugin, FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("scratch");
        boolean enabled = section == null || section.getBoolean("enabled", true);
        int rows = clampInt(section == null ? 3 : section.getInt("rows", 3), 1, 6);
        int columns = clampInt(section == null ? 3 : section.getInt("columns", 3), 1, 6);
        int brushRadius = clampInt(section == null ? 6 : section.getInt("brush-radius", 6), 1, 16);
        double revealThreshold = clampDouble(section == null ? 0.55 : section.getDouble("reveal-threshold", 0.55), 0.2, 1.0);
        int sessionSeconds = clampInt(section == null ? 90 : section.getInt("session-seconds", 90), 5, 1800);
        double screenDistance = clampDouble(section == null ? 2.0 : section.getDouble("screen-distance", 2.0), 1.0, 6.0);
        double maxDistance = clampDouble(section == null ? 6.0 : section.getDouble("max-distance", 6.0), 2.0, 16.0);
        int maxSessions = clampInt(section == null ? 4 : section.getInt("max-sessions", 4), 1, 16);
        boolean hideFromOthers = section == null || section.getBoolean("hide-from-others", true);
        boolean closeWhenComplete = section == null || section.getBoolean("close-when-complete", true);
        int closeDelaySeconds = clampInt(section == null ? 5 : section.getInt("close-delay-seconds", 5), 0, 60);

        List<ScratchPrize> prizes = readPrizes(plugin, config);
        if (prizes.isEmpty()) {
            plugin.getLogger().warning("scratch.prizes 为空或全部无效，已使用内置默认奖品表。");
            prizes = defaultPrizes();
        }
        return new ScratchSettings(enabled, rows, columns, brushRadius, revealThreshold,
                sessionSeconds, screenDistance, maxDistance, maxSessions, hideFromOthers,
                closeWhenComplete, closeDelaySeconds, prizes);
    }

    /** 按权重随机抽一个奖品。 */
    public ScratchPrize pick(Random random) {
        if (totalWeight <= 0) {
            return prizes.get(0);
        }
        double roll = random.nextDouble() * totalWeight;
        for (ScratchPrize prize : prizes) {
            roll -= prize.weight();
            if (roll <= 0) {
                return prize;
            }
        }
        return prizes.get(prizes.size() - 1);
    }

    private static List<ScratchPrize> readPrizes(Plugin plugin, FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("scratch.prizes");
        if (section == null) {
            return List.of();
        }
        List<ScratchPrize> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String id = key.toLowerCase(Locale.ROOT);
            double weight = Math.max(0, section.getDouble(key + ".weight", 1.0));
            if (weight <= 0) {
                plugin.getLogger().warning("scratch.prizes." + key + ".weight <= 0，已跳过。");
                continue;
            }

            boolean blank = section.getBoolean(key + ".blank", false);
            Material material = null;
            if (!blank) {
                String rawMaterial = section.getString(key + ".material");
                material = rawMaterial == null ? null : Material.matchMaterial(rawMaterial.trim());
                if (material == null || material.isAir()) {
                    plugin.getLogger().warning("scratch.prizes." + key + ".material=" + rawMaterial
                            + " 无效，该奖品已按「谢谢惠顾」处理。");
                    blank = true;
                }
            }

            int amount = clampInt(section.getInt(key + ".amount", 1), 1, 64);
            String name = section.getString(key + ".name", "&f" + key);
            Color color = parseColor(section.getString(key + ".color"), material, id);
            byte paletteColor = MapPalette.matchColor(color);
            String label = sanitizeLabel(section.getString(key + ".label", defaultLabel(material, id)));
            Sound sound = parseSound(plugin, section.getString(key + ".sound"));
            String message = section.getString(key + ".message");
            List<String> commands = List.copyOf(section.getStringList(key + ".commands"));
            BufferedImage image = loadImage(plugin, section.getString(key + ".image"));

            result.add(new ScratchPrize(id, name == null ? key : name, material, amount, blank, weight,
                    paletteColor, label, sound, message, commands, image));
        }
        return result;
    }

    private static List<ScratchPrize> defaultPrizes() {
        return List.of(
                new ScratchPrize("blank", "&8谢谢惠顾", null, 0, true, 60,
                        MapPalette.matchColor(new Color(0x3A3A3A)), "SORRY", null, null, List.of(), null),
                new ScratchPrize("diamond", "&b钻石", Material.DIAMOND, 1, false, 12,
                        MapPalette.matchColor(new Color(0x4AEDD9)), "DIAMOND", Sound.ENTITY_PLAYER_LEVELUP,
                        "&b你刮到了一颗钻石！", List.of(), null),
                new ScratchPrize("emerald", "&a绿宝石", Material.EMERALD, 1, false, 12,
                        MapPalette.matchColor(new Color(0x41F384)), "EMERALD", Sound.ENTITY_PLAYER_LEVELUP,
                        "&a你刮到一颗绿宝石！", List.of(), null),
                new ScratchPrize("gold", "&6金锭", Material.GOLD_INGOT, 2, false, 16,
                        MapPalette.matchColor(new Color(0xFCEB4B)), "GOLD", Sound.BLOCK_NOTE_BLOCK_PLING,
                        "&6你刮到了 2 个金锭！", List.of(), null));
    }

    private static Color parseColor(String raw, Material material, String id) {
        if (raw != null && !raw.isBlank()) {
            try {
                return Color.decode(raw.trim());
            } catch (NumberFormatException ignored) {
                // 落到按 id 生成的默认色
            }
        }
        if (material != null) {
            return Color.getHSBColor((Math.abs(material.name().hashCode()) % 360) / 360f, 0.55f, 0.90f);
        }
        return Color.getHSBColor((Math.abs(id.hashCode()) % 360) / 360f, 0.25f, 0.35f);
    }

    private static Sound parseSound(Plugin plugin, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("scratch 奖品音效 " + raw + " 无效，已忽略。");
            return null;
        }
    }

    private static String defaultLabel(Material material, String id) {
        String source = material == null ? id : material.name().replace('_', ' ');
        return source.length() > 10 ? source.substring(0, 10) : source;
    }

    /** 地图像素字体只认 ASCII，中文/符号会被过滤。 */
    private static String sanitizeLabel(String raw) {
        if (raw == null) {
            return "?";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            boolean allowed = (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == ' ' || ch == '-' || ch == '/' || ch == '.';
            if (allowed) {
                builder.append(ch);
            }
            if (builder.length() >= 10) {
                break;
            }
        }
        String label = builder.toString().trim();
        return label.isEmpty() ? "?" : label;
    }

    private static BufferedImage loadImage(Plugin plugin, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        File directory = new File(plugin.getDataFolder(), "scratch-images");
        File file = new File(directory, fileName);
        try {
            if (!file.getCanonicalPath().startsWith(directory.getCanonicalPath())) {
                plugin.getLogger().warning("scratch 图片路径越界，已忽略：" + fileName);
                return null;
            }
            if (!file.isFile()) {
                plugin.getLogger().warning("找不到 scratch 图片：" + file.getPath());
                return null;
            }
            return ImageIO.read(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("读取 scratch 图片失败：" + file.getPath() + "（" + ex.getMessage() + "）");
            return null;
        }
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
