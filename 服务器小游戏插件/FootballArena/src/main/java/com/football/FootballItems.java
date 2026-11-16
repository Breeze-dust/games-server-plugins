package com.football;

import com.hub.util.Chat;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 绿茵足球的所有功能道具。全部带 PDC 标记（fb-item），识别只认标记不认材质，
 * 防止玩家自己合成的糖/床/鞋触发功能。
 */
public class FootballItems {

    /** 道具种类（PDC 里存的字符串）。JERSEY = 物品栏最后一格的展示队服（锁死不能穿/不能丢）。 */
    public enum Kind {
        SPRINT, JUMP, TEAM_RED, TEAM_BLUE, LEAVE_BED, START_PAPER, KICKOFF, JERSEY
    }

    /** 开球靴的力度档位数（快捷栏前 N 格，力度 1~N）。 */
    public static final int KICKOFF_MAX_LEVEL = 4;

    /** 解析结果：道具种类 + 开球力度（仅 KICKOFF 有意义）。 */
    public record Tag(Kind kind, int power) {
    }

    private final NamespacedKey key;

    public FootballItems(FootballPlugin plugin) {
        this.key = new NamespacedKey(plugin, "fb-item");
    }

    /** PDC 标记的值：普通道具是种类名，开球靴是 "kickoff:力度"。 */
    public String tagValue(Kind kind, int power) {
        return kind == Kind.KICKOFF ? "kickoff:" + power : kind.name();
    }

    public ItemStack make(Kind kind, int power) {
        ItemStack item = new ItemStack(materialOf(kind, power));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(nameOf(kind, power))));
            meta.setLore(loreOf(kind, power).stream()
                    .map(s -> com.hub.util.Chat.toLegacy(Chat.color(s))).toList());
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tagValue(kind, power));
            if (meta instanceof LeatherArmorMeta leather && kind == Kind.KICKOFF) {
                leather.setColor(kickoffColor(power));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 识别道具（没有标记返回 null）。 */
    public Tag tag(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String v = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (v == null) {
            return null;
        }
        if (v.startsWith("kickoff:")) {
            try {
                return new Tag(Kind.KICKOFF, Integer.parseInt(v.substring(8)));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return new Tag(Kind.valueOf(v), 0);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Material materialOf(Kind kind, int power) {
        return switch (kind) {
            case SPRINT -> Material.SUGAR;
            case JUMP -> Material.FEATHER;
            case KICKOFF -> Material.LEATHER_BOOTS;
            case TEAM_RED -> Material.RED_BANNER;
            case TEAM_BLUE -> Material.BLUE_BANNER;
            case LEAVE_BED -> Material.RED_BED;
            case START_PAPER -> Material.PAPER;
            case JERSEY -> Material.LEATHER_CHESTPLATE;
        };
    }

    /** 开球靴按力度染色：1 绿 → 2 黄 → 3 橙 → 4 红，一眼看出档位。 */
    private Color kickoffColor(int power) {
        return switch (power) {
            case 1 -> Color.LIME;
            case 2 -> Color.YELLOW;
            case 3 -> Color.ORANGE;
            default -> Color.RED;
        };
    }

    private String nameOf(Kind kind, int power) {
        return switch (kind) {
            case SPRINT -> "&b冲刺 &7(右键)";
            case JUMP -> "&d高跳 &7(右键)";
            case KICKOFF -> (switch (power) {
                case 1 -> "&a";
                case 2 -> "&e";
                case 3 -> "&6";
                default -> "&c";
            }) + "开球·力度 " + power;
            case TEAM_RED -> "&c加入红队 &7(右键)";
            case TEAM_BLUE -> "&9加入蓝队 &7(右键)";
            case LEAVE_BED -> "&c退出游戏 &7(右键)";
            case START_PAPER -> "&a&l开始游戏 &7(右键)";
            case JERSEY -> "&f队服";
        };
    }

    private List<String> loreOf(Kind kind, int power) {
        return switch (kind) {
            case SPRINT -> List.of("&7速度效果 5 秒", "&7冷却 5 秒");
            case JUMP -> List.of("&7立即高高跳起一次", "&7冷却 5 秒");
            case KICKOFF -> List.of("&7左键开球", "&7力度越大，球飞得越高越远");
            case TEAM_RED -> List.of("&7右键加入红队");
            case TEAM_BLUE -> List.of("&7右键加入蓝队");
            case LEAVE_BED -> List.of("&7右键离开场地");
            case START_PAPER -> List.of("&7OP 右键立即开始");
            case JERSEY -> List.of("&7队伍颜色", "&7仅作展示：不能穿、不能丢");
        };
    }

    // ---------- 成套发放 ----------

    /** 等待阶段：选队旗帜 + 退出床（+ OP 开始纸）。 */
    public void giveWaitingItems(Player p, boolean op) {
        p.getInventory().clear();
        p.getInventory().setItem(0, make(Kind.TEAM_RED, 0));
        p.getInventory().setItem(1, make(Kind.TEAM_BLUE, 0));
        if (op) {
            p.getInventory().setItem(4, make(Kind.START_PAPER, 0));
        }
        if (!com.hub.util.WaitCountdown.isGloballySuppressed()) {
            p.getInventory().setItem(8, make(Kind.LEAVE_BED, 0));
        }
    }

    /**
     * 对局装备：队服穿在胸甲位（+护腿），物品栏最后一格再放一个同款「队服」展示道具（锁死，不能穿不能丢）。
     * 没有踢球鞋：对局中任何物品（含空手）左键都能踢球，所以快捷栏 3~8 格留空。
     * 对局中不发退出床，要离场用 /fb leave。
     * <p>
     * 2026-11-15 玩家反馈：最后一格那份队服右键穿不上（胸甲位已经穿着同款）、还丢不掉，纯占格；
     * 按需求保留这个展示道具，但由 FootballListener 明确锁死"不能穿、不能丢、不能挪"，
     * 穿戴只认自动补穿的胸甲位（{@link #wearJersey}）。
     */
    public void giveMatchKit(Player p, FootballArena.Team team) {
        p.getInventory().clear();
        boolean red = team == FootballArena.Team.RED;
        Color color = teamColor(team);

        p.getInventory().setChestplate(makeJersey(team));
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        if (legs.getItemMeta() instanceof LeatherArmorMeta m) {
            m.setColor(color);
            m.displayName(Chat.noItalic(Chat.color((red ? "&c" : "&9") + "球裤")));
            legs.setItemMeta(m);
        }
        p.getInventory().setLeggings(legs);

        p.getInventory().setItem(0, make(Kind.SPRINT, 0));
        p.getInventory().setItem(1, make(Kind.JUMP, 0));
        ensureJerseyItem(p, team);
    }

    /** 玩家胸甲位是否已经穿着这队的队服（材质 + 颜色都对）。 */
    public boolean wearingJersey(Player p, FootballArena.Team team) {
        ItemStack chest = p.getInventory().getChestplate();
        return chest != null && chest.getType() == Material.LEATHER_CHESTPLATE
                && matchesJerseyColor(chest, team);
    }

    /** 给玩家穿上这队的队服（脱了/被别的插件扒了就补回来）。 */
    public void wearJersey(Player p, FootballArena.Team team) {
        p.getInventory().setChestplate(makeJersey(team));
    }

    /** 队服颜色：红队偏暗红、蓝队偏暗蓝（与旧版一致）。 */
    private Color teamColor(FootballArena.Team team) {
        return team == FootballArena.Team.RED ? Color.fromRGB(0xC03030) : Color.fromRGB(0x3050C0);
    }

    /**
     * 对应队伍颜色的皮革胸甲「队服」。穿戴用的那件不写 PDC 标记：它是真盔甲，不是功能道具
     * （见 {@link #makeJerseyItem} 里另做的背包展示道具）。
     */
    private ItemStack makeJersey(FootballArena.Team team) {
        boolean red = team == FootballArena.Team.RED;
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        if (item.getItemMeta() instanceof LeatherArmorMeta m) {
            m.setColor(teamColor(team));
            m.displayName(Chat.noItalic(Chat.color((red ? "&c" : "&9") + "队服")));
            item.setItemMeta(m);
        }
        return item;
    }

    /**
     * 物品栏最后一格（快捷栏第 9 格 / 编号 8）里的「队服」展示道具：和身上那件同款同色，
     * 但带 PDC 标记（JERSEY），由事件层锁死不能穿、不能丢、不能挪，每秒由
     * {@link #ensureJerseyItem} 兜底补回。只作队伍颜色标识，不是可穿戴装备。
     */
    public ItemStack makeJerseyItem(FootballArena.Team team) {
        ItemStack item = makeJersey(team);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    Chat.toLegacy(Chat.color("&7队伍：" + team.display())),
                    Chat.toLegacy(Chat.color("&7仅作展示：不能穿、不能丢"))));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, Kind.JERSEY.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 是不是最后一格那个锁死的队服展示道具（只认 PDC 标记，不认材质/名字）。 */
    public boolean isJerseyItem(ItemStack item) {
        Tag t = tag(item);
        return t != null && t.kind() == Kind.JERSEY;
    }

    /** 把最后一格的队服补成当前队伍颜色；被挪走、被别的插件清掉都会补回来。 */
    public void ensureJerseyItem(Player p, FootballArena.Team team) {
        ItemStack current = p.getInventory().getItem(8);
        if (!isJerseyItem(current) || !matchesJerseyColor(current, team)) {
            p.getInventory().setItem(8, makeJerseyItem(team));
        }
        // 兜底：队服只允许待在最后一格（防止换手/换格后补发时出现第二份）
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != 8 && isJerseyItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    /** 清掉背包里所有锁死的队服展示道具（离场/结算/掉线时调用，别让它跟着回主城或被带出对局）。 */
    public void removeJerseyItem(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isJerseyItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    /** 皮革护甲颜色是否正好是这队的队服色（不检查 PDC 标记）。 */
    private boolean matchesJerseyColor(ItemStack item, FootballArena.Team team) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta m) || m.getColor() == null) {
            return false;
        }
        return m.getColor().asRGB() == teamColor(team).asRGB();
    }

    /** 开球阶段（开球方）：快捷栏前 4 格放力度 1~4 的开球靴（顶替普通装备，踢完恢复）。 */
    public void giveKickoffBoots(Player p) {
        for (int power = 1; power <= KICKOFF_MAX_LEVEL; power++) {
            p.getInventory().setItem(power - 1, make(Kind.KICKOFF, power));
        }
    }

    /** 清掉某玩家快捷栏里的所有开球靴（开球结束后调用，随后 giveMatchKit 恢复全套）。 */
    public void clearKickoffBoots(Player p) {
        for (int i = 0; i < 9; i++) {
            Tag t = tag(p.getInventory().getItem(i));
            if (t != null && t.kind() == Kind.KICKOFF) {
                p.getInventory().setItem(i, null);
            }
        }
    }
}
