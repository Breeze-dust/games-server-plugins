package com.luckypillar;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 「任意物品」随机池（纯静态，无状态）。
 * <p>
 * 设计稿 §5：没有硬性权重表，每次随机给 1 件物品；排除「崩服/作弊」「过强」「刷怪蛋」三类，
 * 另外对「纯材料 / 装饰」和「能放置但踩不住的方块（旗帜、火把…）」降权（正常 4 : 低价值 1）。
 * 水桶、岩浆桶明确保留（可以用来杀人或自杀，属于玩法）。
 * <p>
 * 现在物品按 <b>武器 / 工具 / 装备 / 方块 / 其他</b> 五类分配概率：
 * 先按 config 里的类别权重抽一个类别，再在该类别里按「正常 4 : 低价值 1」抽具体物品。
 * 武器/工具/装备抽中后仍按 {@code loot-enchant-chance} 追加低等级附魔。
 * <p>
 * 要调池子只改本文件顶部三张常量表 + {@link #buildPool()} 的过滤条件，不用碰 Manager。
 */
public final class LuckyPillarLoot {

    /** 物品类别（config 里各有一条权重）。 */
    public enum Category {
        WEAPON("武器"),
        TOOL("工具"),
        ARMOR("装备"),
        BLOCK("方块"),
        OTHER("其他");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** 崩服 / 作弊类（设计稿 §5.2-1）。 */
    private static final Set<Material> BLACKLIST_ABUSE = EnumSet.of(
            Material.BEDROCK,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.BARRIER,
            Material.LIGHT,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.DEBUG_STICK,
            Material.KNOWLEDGE_BOOK,
            Material.FILLED_MAP,
            Material.BUNDLE);

    /** 过强类（设计稿 §5.2-2）。 */
    private static final Set<Material> BLACKLIST_OP = EnumSet.of(
            Material.ELYTRA,
            Material.BEACON,
            Material.CONDUIT,
            Material.TOTEM_OF_UNDYING,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.END_CRYSTAL,
            Material.RESPAWN_ANCHOR);

    /** 药水随机效果池：抽到 POTION 时从这里随机一种（设计稿 §11-4，增删只改这里）。 */
    private static final List<PotionType> POTION_POOL = List.of(
            PotionType.SWIFTNESS,
            PotionType.LONG_SWIFTNESS,
            PotionType.STRENGTH,
            PotionType.LONG_STRENGTH,
            PotionType.REGENERATION,
            PotionType.LONG_REGENERATION,
            PotionType.HEALING,
            PotionType.SLOW_FALLING,
            PotionType.LONG_SLOW_FALLING,
            PotionType.LEAPING,
            PotionType.LONG_LEAPING,
            PotionType.FIRE_RESISTANCE,
            PotionType.LONG_FIRE_RESISTANCE,
            PotionType.NIGHT_VISION,
            PotionType.LONG_NIGHT_VISION,
            PotionType.INVISIBILITY,
            PotionType.LONG_INVISIBILITY,
            PotionType.WATER_BREATHING,
            PotionType.LONG_WATER_BREATHING,
            PotionType.POISON,
            PotionType.HARMING,
            PotionType.SLOWNESS,
            PotionType.WEAKNESS,
            PotionType.LUCK);

    /** 附魔书随机附魔池（空 = 全部附魔随机；设计稿 §11-4）。 */
    private static final List<Enchantment> ENCHANT_POOL = List.of();

    /** 权重：正常物品 4，低价值物品 1（见 {@link #isLowValue(Material)}）。 */
    private static final int WEIGHT_NORMAL = 4;
    private static final int WEIGHT_LOW = 1;

    /** 武器 / 工具 / 装备：按名字后缀识别（木/石/铁/金/钻石/下界合金 各档都覆盖）。 */
    private static final String[] WEAPON_SUFFIX = {"_SWORD"};
    private static final String[] TOOL_SUFFIX = {"_PICKAXE", "_AXE", "_SHOVEL", "_HOE"};
    private static final String[] ARMOR_SUFFIX = {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"};

    /** 后缀认不出来的装备（弓/弩/三叉戟/重锤 算武器；盾算装备）。 */
    private static final Set<Material> WEAPON_EXTRA = EnumSet.of(
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.MACE);
    private static final Set<Material> TOOL_EXTRA = EnumSet.of(
            Material.FISHING_ROD, Material.SHEARS, Material.FLINT_AND_STEEL, Material.BRUSH,
            Material.CARROT_ON_A_STICK, Material.WARPED_FUNGUS_ON_A_STICK);
    private static final Set<Material> ARMOR_EXTRA = EnumSet.of(Material.SHIELD);

    /**
     * 「既不能使用又不能放置」的纯材料（合成原料 / 装饰，拿到手里没用）。
     * <p>除了这张表，{@link #isLowValue(Material)} 还按名字批量识别：
     * 唱片、唱片碎片、旗帜图案、陶片、锻造模板、染料、种子。
     */
    private static final Set<Material> JUNK = EnumSet.of(
            Material.GUNPOWDER, Material.SUGAR, Material.PAPER, Material.STRING, Material.BONE,
            Material.BONE_MEAL, Material.FEATHER, Material.LEATHER, Material.SLIME_BALL,
            Material.SPIDER_EYE, Material.FERMENTED_SPIDER_EYE, Material.RABBIT_FOOT,
            Material.RABBIT_HIDE, Material.PHANTOM_MEMBRANE, Material.GHAST_TEAR,
            Material.BLAZE_POWDER, Material.BLAZE_ROD, Material.MAGMA_CREAM, Material.CLAY_BALL,
            Material.FLINT, Material.CHARCOAL, Material.COAL, Material.AMETHYST_SHARD,
            Material.GLOWSTONE_DUST, Material.REDSTONE, Material.NETHER_WART,
            Material.GLASS_BOTTLE, Material.BOWL, Material.STICK, Material.BRICK,
            Material.NETHER_BRICK, Material.NETHERITE_SCRAP, Material.NETHERITE_INGOT,
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.GOLD_NUGGET,
            Material.IRON_INGOT, Material.IRON_NUGGET, Material.COPPER_INGOT,
            Material.LAPIS_LAZULI, Material.QUARTZ, Material.PRISMARINE_SHARD,
            Material.PRISMARINE_CRYSTALS, Material.NAUTILUS_SHELL, Material.HEART_OF_THE_SEA,
            Material.TURTLE_SCUTE, Material.ARMADILLO_SCUTE, Material.INK_SAC,
            Material.GLOW_INK_SAC, Material.DRAGON_BREATH,
            Material.ECHO_SHARD, Material.ENDER_EYE, Material.FIREWORK_STAR,
            Material.FIREWORK_ROCKET, Material.SHULKER_SHELL, Material.NAME_TAG, Material.LEAD,
            Material.SADDLE, Material.GOAT_HORN, Material.COMPASS, Material.RECOVERY_COMPASS,
            Material.CLOCK, Material.MAP);

    private static final Random RANDOM = new Random();

    /** 物品池（Material 全集过滤后的结果，类加载时算一次）。 */
    private static final List<Material> POOL = Collections.unmodifiableList(buildPool());

    /** 每个类别一份子池（与权重一一对应，类加载时算一次）。 */
    private static final Map<Category, Bucket> BUCKETS = buildBuckets();

    /** 类别权重（与 {@link Category#values()} 一一对应；Manager 从 config 读入）。 */
    private static final double[] CATEGORY_WEIGHTS = defaultWeights();

    /** 类别权重总和（{@link #random()} 用）。 */
    private static double totalWeight = sumWeights();

    /** 附魔率：武器 / 工具 / 装备额外带一个低等级附魔的概率（0~1）。 */
    private static double enchantChance = 0.35;

    /** 附魔等级上限（默认 1 = 只有 I 级）。 */
    private static int enchantMaxLevel = 1;

    /** 全部附魔（首次用到时从注册表取一次）。 */
    private static List<Enchantment> enchants;

    private LuckyPillarLoot() {
    }

    /** 物品池大小（日志 / 自检用）。 */
    public static int poolSize() {
        return POOL.size();
    }

    /** 低价值（降权）物品数（日志 / 调参用）。 */
    public static int lowValueCount() {
        int n = 0;
        for (Material m : POOL) {
            if (safeLowValue(m)) {
                n++;
            }
        }
        return n;
    }

    /** 某个类别的物品数（日志 / 调参用）。 */
    public static int categoryCount(Category category) {
        Bucket b = BUCKETS.get(category);
        return b == null ? 0 : b.items().size();
    }

    /** 某个类别的当前权重。 */
    public static double weightOf(Category category) {
        return CATEGORY_WEIGHTS[category.ordinal()];
    }

    /** 当前类别权重的一行摘要（日志 / 开局播报用）。 */
    public static String weightSummary() {
        StringBuilder sb = new StringBuilder();
        for (Category c : Category.values()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(c.label).append(' ').append(fmtWeight(weightOf(c)));
        }
        return sb.toString();
    }

    /**
     * 随机 1 件幸运物品（数量恒为 1，设计稿 §0-12）。
     * <p>先按类别权重抽一类（{@link Category}），再在该类子池里按
     * 「正常 {@link #WEIGHT_NORMAL} : 低价值 {@link #WEIGHT_LOW}」抽具体物品；
     * 抽中武器 / 工具 / 装备时按 {@link #enchantChance} 追加一个低等级附魔。
     */
    public static ItemStack random() {
        if (POOL.isEmpty()) {
            return new ItemStack(Material.COBBLESTONE);
        }
        Category category = rollCategory();
        Bucket bucket = BUCKETS.get(category);
        if (bucket == null || bucket.items().isEmpty()) {
            bucket = BUCKETS.get(Category.OTHER);
        }
        if (bucket == null || bucket.items().isEmpty()) {
            bucket = BUCKETS.get(Category.BLOCK);
        }
        ItemStack item = bucket == null || bucket.items().isEmpty()
                ? byMaterial(POOL.get(RANDOM.nextInt(POOL.size())))
                : byMaterial(pick(bucket));
        if (isGearCategory(category) && RANDOM.nextDouble() < enchantChance) {
            try {
                enchantLow(item);
            } catch (Throwable ignored) {
                // 附魔失败就发白板装备，绝不能因为一件物品把整局打断
            }
        }
        return item;
    }

    /** 是否是武器 / 工具 / 装备（名字后缀 + 显式表）。 */
    public static boolean isGear(Material m) {
        return isGearCategory(categoryOf(m));
    }

    /** 武器 / 工具 / 装备子池的本体数量（日志用）。 */
    public static int gearCount() {
        return categoryCount(Category.WEAPON) + categoryCount(Category.TOOL) + categoryCount(Category.ARMOR);
    }

    /** 当前附魔率（0~1）。 */
    public static double enchantChance() {
        return enchantChance;
    }

    /** 当前附魔等级上限。 */
    public static int enchantMaxLevel() {
        return enchantMaxLevel;
    }

    /**
     * 由 {@code LuckyPillarManager#load} 从 config.yml 读入。
     * 权重为相对值（负数按 0 处理；全 0 时退回默认 25/20/10/25/20）；
     * 附魔率非法值收敛到 0~1，等级收敛到 1~3。
     */
    public static void configure(double weapon, double tool, double armor, double block, double other,
                                 double enchantChance, int enchantMaxLevel) {
        double[] weights = {weapon, tool, armor, block, other};
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            if (Double.isNaN(weights[i]) || weights[i] < 0) {
                weights[i] = 0;
            }
            sum += weights[i];
        }
        if (sum <= 0) {
            weights = defaultWeights();
            sum = sumWeights(weights);
        }
        System.arraycopy(weights, 0, CATEGORY_WEIGHTS, 0, weights.length);
        totalWeight = sum;
        LuckyPillarLoot.enchantChance = clamp01(enchantChance, 0.35);
        LuckyPillarLoot.enchantMaxLevel = Math.max(1, Math.min(3, enchantMaxLevel));
    }

    /** 物品属于哪一类（先判装备，再判方块；都不沾的进「其他」）。 */
    public static Category categoryOf(Material m) {
        if (m == null) {
            return Category.OTHER;
        }
        if (WEAPON_EXTRA.contains(m) || endsWithAny(m, WEAPON_SUFFIX)) {
            return Category.WEAPON;
        }
        if (TOOL_EXTRA.contains(m) || endsWithAny(m, TOOL_SUFFIX)) {
            return Category.TOOL;
        }
        if (ARMOR_EXTRA.contains(m) || endsWithAny(m, ARMOR_SUFFIX)) {
            return Category.ARMOR;
        }
        try {
            if (m.isBlock()) {
                return Category.BLOCK;
            }
        } catch (Throwable ignored) {
            // 注册表查询异常时归到「其他」，不影响抽物品
        }
        return Category.OTHER;
    }

    private static boolean isGearCategory(Category category) {
        return category == Category.WEAPON || category == Category.TOOL || category == Category.ARMOR;
    }

    private static boolean endsWithAny(Material m, String[] suffixes) {
        String n = m.name();
        for (String suffix : suffixes) {
            if (n.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /** 给一件装备挂 1 个随机低等级附魔（只挑「能附在这件东西上」且不是诅咒的）。 */
    private static void enchantLow(ItemStack item) {
        List<Enchantment> all = allEnchants();
        if (all.isEmpty()) {
            return;
        }
        List<Enchantment> usable = new ArrayList<>();
        for (Enchantment e : all) {
            if (e == null || isCurse(e)) {
                continue;
            }
            try {
                if (e.canEnchantItem(item)) {
                    usable.add(e);
                }
            } catch (Throwable ignored) {
                // 单条附魔查询失败就跳过这一条
            }
        }
        if (usable.isEmpty()) {
            return;
        }
        Enchantment ench = usable.get(RANDOM.nextInt(usable.size()));
        int max = Math.min(enchantMaxLevel, Math.max(1, ench.getMaxLevel()));
        item.addUnsafeEnchantment(ench, 1 + RANDOM.nextInt(max));
    }

    /** 绑定诅咒 / 消失诅咒不给（低等级也不给，纯粹恶心人）。 */
    private static boolean isCurse(Enchantment e) {
        try {
            return e.getKey().getKey().endsWith("_curse");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double clamp01(double v, double fallback) {
        if (Double.isNaN(v)) {
            return fallback;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String fmtWeight(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static double[] defaultWeights() {
        return new double[]{25, 20, 10, 25, 20};
    }

    private static double sumWeights() {
        return sumWeights(CATEGORY_WEIGHTS);
    }

    private static double sumWeights(double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        return total;
    }

    private static Category rollCategory() {
        if (totalWeight <= 0) {
            return Category.OTHER;
        }
        double roll = RANDOM.nextDouble() * totalWeight;
        for (Category c : Category.values()) {
            roll -= CATEGORY_WEIGHTS[c.ordinal()];
            if (roll < 0) {
                return c;
            }
        }
        return Category.OTHER;
    }

    private static Material pick(Bucket bucket) {
        int roll = RANDOM.nextInt(bucket.total());
        int[] weights = bucket.weights();
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return bucket.items().get(i);
            }
        }
        return bucket.items().get(bucket.items().size() - 1);
    }

    private static boolean safeLowValue(Material m) {
        try {
            return isLowValue(m);
        } catch (Throwable ignored) {
            // isSolid() 之类的注册表查询万一不可用，就当普通物品，绝不能因此让插件起不来
            return false;
        }
    }

    private static Map<Category, Bucket> buildBuckets() {
        Map<Category, List<Material>> lists = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            lists.put(c, new ArrayList<>());
        }
        for (Material m : POOL) {
            lists.get(categoryOf(m)).add(m);
        }
        Map<Category, Bucket> out = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            List<Material> items = Collections.unmodifiableList(lists.get(c));
            int[] weights = new int[items.size()];
            int total = 0;
            for (int i = 0; i < items.size(); i++) {
                weights[i] = safeLowValue(items.get(i)) ? WEIGHT_LOW : WEIGHT_NORMAL;
                total += weights[i];
            }
            out.put(c, new Bucket(items, weights, total));
        }
        return out;
    }

    /** 低价值：纯材料 / 装饰，或「能放下但踩不住」的方块（旗帜、火把、告示牌、花…）。 */
    private static boolean isLowValue(Material m) {
        if (JUNK.contains(m)) {
            return true;
        }
        String n = m.name();
        if (n.startsWith("RECORD_") || n.startsWith("DISC_FRAGMENT_")
                || n.endsWith("_BANNER_PATTERN") || n.endsWith("_POTTERY_SHERD")
                || n.endsWith("_SMITHING_TEMPLATE") || n.endsWith("_DYE") || n.endsWith("_SEEDS")) {
            return true;
        }
        // 能放置但没有碰撞箱：站不上去，也就没法当垫脚石。
        return m.isBlock() && !m.isSolid();
    }

    /** 把某个 Material 加工成最终物品（药水随机效果、附魔书随机附魔、其余原样 1 个）。 */
    public static ItemStack byMaterial(Material material) {
        ItemStack item = new ItemStack(material, 1);
        try {
            if (material == Material.POTION) {
                randomizePotion(item);
            } else if (material == Material.ENCHANTED_BOOK) {
                randomizeBook(item);
            }
        } catch (Throwable ignored) {
            // 单件物品加工失败就退回原物品，绝不能因为一件物品把整局打断
        }
        return item;
    }

    private static void randomizePotion(ItemStack item) {
        if (POTION_POOL.isEmpty() || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return;
        }
        meta.setBasePotionType(POTION_POOL.get(RANDOM.nextInt(POTION_POOL.size())));
        item.setItemMeta(meta);
    }

    private static void randomizeBook(ItemStack item) {
        List<Enchantment> all = allEnchants();
        if (all.isEmpty() || !(item.getItemMeta() instanceof ItemMeta meta)) {
            return;
        }
        Enchantment ench = all.get(RANDOM.nextInt(all.size()));
        int max = Math.max(1, ench.getMaxLevel());
        int level = 1 + RANDOM.nextInt(max);
        meta.addEnchant(ench, level, true);
        item.setItemMeta(meta);
    }

    private static List<Enchantment> allEnchants() {
        if (enchants == null) {
            List<Enchantment> list = new ArrayList<>();
            if (!ENCHANT_POOL.isEmpty()) {
                list.addAll(ENCHANT_POOL);
            } else {
                for (Enchantment e : Registry.ENCHANTMENT) {
                    if (e != null) {
                        list.add(e);
                    }
                }
            }
            enchants = list;
        }
        return enchants;
    }

    /** 设计稿 §5.2：Material.values() 里 isItem() 的，排除三类。 */
    private static List<Material> buildPool() {
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m == null || m.isAir() || m.isLegacy() || !m.isItem()) {
                continue;
            }
            if (BLACKLIST_ABUSE.contains(m) || BLACKLIST_OP.contains(m)) {
                continue;
            }
            if (m.name().endsWith("_SPAWN_EGG")) {
                continue; // 刷怪蛋全部排除
            }
            list.add(m);
        }
        return list;
    }

    /** 一个类别的子池 + 每件物品的抽中权重 + 权重总和。 */
    private record Bucket(List<Material> items, int[] weights, int total) {
    }
}
