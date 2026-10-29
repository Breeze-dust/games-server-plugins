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
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 「任意物品」随机池（纯静态，无状态）。
 * <p>
 * 设计稿 §5：没有硬性权重表，每次随机给 1 件物品；排除「崩服/作弊」「过强」「刷怪蛋」三类，
 * 另外对「纯材料 / 装饰」和「能放置但踩不住的方块（旗帜、火把…）」降权（正常 4 : 低价值 1）。
 * 水桶、岩浆桶明确保留（可以用来杀人或自杀，属于玩法）。
 * <p>
 * 要调池子只改本文件顶部三张常量表 + {@link #buildPool()} 的过滤条件，不用碰 Manager。
 */
public final class LuckyPillarLoot {

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
    private static final String[] GEAR_SUFFIX = {
            "_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE",
            "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"};

    /** 后缀认不出来的装备（弓/弩/三叉戟/盾/鱼竿/剪刀/打火石/重锤）。 */
    private static final Set<Material> GEAR_EXTRA = EnumSet.of(
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD,
            Material.FISHING_ROD, Material.SHEARS, Material.FLINT_AND_STEEL, Material.MACE);

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

    /** 与 {@link #POOL} 一一对应的抽中权重。 */
    private static final int[] WEIGHTS = buildWeights();

    /** 权重总和（{@link #random()} 用）。 */
    private static final int TOTAL_WEIGHT = sumWeights();

    /** 武器 / 工具 / 装备子池（与普通权重池并列，开局可单独抬概率）。 */
    private static final List<Material> GEAR = Collections.unmodifiableList(buildGear());

    /** 装备命中率：每次抽物品有多大概率直接从 {@link #GEAR} 里出（0~1，Manager 从 config 读入）。 */
    private static double gearChance = 0.3;

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
        for (int w : WEIGHTS) {
            if (w == WEIGHT_LOW) {
                n++;
            }
        }
        return n;
    }

    /**
     * 随机 1 件幸运物品（数量恒为 1，设计稿 §0-12）。
     * <p>先掷 {@link #gearChance}：命中就从武器/工具/装备子池里均匀抽 1 件（并且按
     * {@link #enchantChance} 追加一个低等级附魔）；没命中才走原来的权重池
     * （正常物品 {@link #WEIGHT_NORMAL}，没用 / 不能踮脚的物品 {@link #WEIGHT_LOW}）。
     */
    public static ItemStack random() {
        if (POOL.isEmpty()) {
            return new ItemStack(Material.COBBLESTONE);
        }
        if (!GEAR.isEmpty() && RANDOM.nextDouble() < gearChance) {
            ItemStack item = byMaterial(GEAR.get(RANDOM.nextInt(GEAR.size())));
            if (RANDOM.nextDouble() < enchantChance) {
                try {
                    enchantLow(item);
                } catch (Throwable ignored) {
                    // 附魔失败就发白板装备，绝不能因为一件物品把整局打断
                }
            }
            return item;
        }
        int roll = RANDOM.nextInt(TOTAL_WEIGHT);
        for (int i = 0; i < WEIGHTS.length; i++) {
            roll -= WEIGHTS[i];
            if (roll < 0) {
                return byMaterial(POOL.get(i));
            }
        }
        return byMaterial(POOL.get(POOL.size() - 1));
    }

    /** 武器 / 工具 / 装备子池大小（日志 / 调参用）。 */
    public static int gearCount() {
        return GEAR.size();
    }

    /** 当前装备命中率（0~1）。 */
    public static double gearChance() {
        return gearChance;
    }

    /** 当前附魔率（0~1）。 */
    public static double enchantChance() {
        return enchantChance;
    }

    /** 当前附魔等级上限。 */
    public static int enchantMaxLevel() {
        return enchantMaxLevel;
    }

    /** 由 {@code LuckyPillarManager#load} 从 config.yml 读入（非法值自动收敛到 0~1 / 1~3 级）。 */
    public static void configure(double gearChance, double enchantChance, int enchantMaxLevel) {
        LuckyPillarLoot.gearChance = clamp01(gearChance, 0.3);
        LuckyPillarLoot.enchantChance = clamp01(enchantChance, 0.35);
        LuckyPillarLoot.enchantMaxLevel = Math.max(1, Math.min(3, enchantMaxLevel));
    }

    /** 是否是武器 / 工具 / 装备（名字后缀 + {@link #GEAR_EXTRA} 显式表）。 */
    public static boolean isGear(Material m) {
        if (m == null) {
            return false;
        }
        if (GEAR_EXTRA.contains(m)) {
            return true;
        }
        String n = m.name();
        for (String suffix : GEAR_SUFFIX) {
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

    private static List<Material> buildGear() {
        List<Material> list = new ArrayList<>();
        for (Material m : POOL) {
            if (isGear(m)) {
                list.add(m);
            }
        }
        return list;
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

    private static int[] buildWeights() {
        int[] weights = new int[POOL.size()];
        for (int i = 0; i < weights.length; i++) {
            boolean low;
            try {
                low = isLowValue(POOL.get(i));
            } catch (Throwable ignored) {
                // isSolid() 之类的注册表查询万一不可用，就当普通物品，绝不能因此让插件起不来
                low = false;
            }
            weights[i] = low ? WEIGHT_LOW : WEIGHT_NORMAL;
        }
        return weights;
    }

    private static int sumWeights() {
        int total = 0;
        for (int w : WEIGHTS) {
            total += w;
        }
        return total;
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
}
