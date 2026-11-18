package com.dontdoit;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 场地生成：与官方 DontDoIt 的 GameAreaGenerator 同一套逻辑——
 * 从场地中心往下找石层，铺基岩，然后把石层里的石头按深度比例换成安山岩 / 煤矿 / 铁矿 / 钻石矿。
 */
public final class ArenaGenerator {

    /** 视为「石层」的方块。 */
    private static final Set<Material> STONE_LIKE = EnumSet.of(
            Material.STONE, Material.ANDESITE, Material.GRANITE, Material.DIORITE,
            Material.INFESTED_STONE, Material.INFESTED_COBBLESTONE,
            Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE,
            Material.REDSTONE_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE,
            Material.DEEPSLATE, Material.TUFF, Material.INFESTED_DEEPSLATE,
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_DIAMOND_ORE);

    private static final double STONE_PERCENTAGE = 0.25;
    private static final int STONE_SEARCH_MAX_DISTANCE = 30;

    private ArenaGenerator() {
    }

    /** 一条矿物生成规则。 */
    public record OreRule(Material material, int depthMin, int depthMax, double ratio) {
    }

    /**
     * @return 检测到的石层 Y；失败返回 Integer.MIN_VALUE
     */
    public static int generate(DontDoItArena arena, List<OreRule> rules, int bedrockDepth, Random random) {
        World world = arena.world();
        if (world == null) {
            return Integer.MIN_VALUE;
        }
        int cx = arena.center().getBlockX();
        int cy = arena.center().getBlockY();
        int cz = arena.center().getBlockZ();
        int radius = arena.radius();
        int side = arena.side();

        int stoneY = Integer.MIN_VALUE;
        for (int offset = 0; offset < STONE_SEARCH_MAX_DISTANCE; offset++) {
            int y = cy - offset;
            int count = 0;
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (STONE_LIKE.contains(world.getBlockAt(x, y, z).getType())) {
                        count++;
                    }
                }
            }
            if ((double) count / (side * side) >= STONE_PERCENTAGE) {
                stoneY = y;
                break;
            }
        }
        if (stoneY == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }

        int bedrockY = stoneY - bedrockDepth + 1;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                world.getBlockAt(x, bedrockY, z).setType(Material.BEDROCK, false);
            }
        }

        for (int depth = 1; depth < bedrockDepth; depth++) {
            int y = stoneY - depth + 1;
            List<long[]> stones = new ArrayList<>();
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (STONE_LIKE.contains(world.getBlockAt(x, y, z).getType())) {
                        stones.add(new long[]{x, z});
                    }
                }
            }
            if (stones.isEmpty()) {
                continue;
            }
            Set<Long> replaced = new HashSet<>();
            for (OreRule rule : rules) {
                if (depth < rule.depthMin() || depth > rule.depthMax()) {
                    continue;
                }
                int want = (int) Math.round(rule.ratio() * stones.size());
                List<long[]> available = new ArrayList<>(stones);
                available.removeIf(p -> replaced.contains(encode(p[0], p[1])));
                for (int i = 0; i < want && !available.isEmpty(); i++) {
                    long[] pos = available.remove(random.nextInt(available.size()));
                    replaced.add(encode(pos[0], pos[1]));
                    Block block = world.getBlockAt((int) pos[0], y, (int) pos[1]);
                    block.setType(rule.material(), false);
                }
            }
            for (long[] pos : stones) {
                if (replaced.contains(encode(pos[0], pos[1]))) {
                    continue;
                }
                world.getBlockAt((int) pos[0], y, (int) pos[1]).setType(Material.STONE, false);
            }
        }
        return stoneY;
    }

    private static long encode(long x, long z) {
        return (x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
