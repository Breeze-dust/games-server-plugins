package com.football;

import com.hub.util.Chat;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 绿茵足球的所有功能道具。全部带 PDC 标记（fb-item），识别只认标记不认材质，
 * 防止玩家自己合成的糖/床/鞋触发功能。
 */
public class FootballItems {

    /** 道具种类（PDC 里存的字符串）。 */
    public enum Kind {
        SPRINT, JUMP, KICK_FORWARD, KICK_VERTICAL, TEAM_RED, TEAM_BLUE, LEAVE_BED, START_PAPER, KICKOFF
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
            case KICK_FORWARD, KICK_VERTICAL, KICKOFF -> Material.LEATHER_BOOTS;
            case TEAM_RED -> Material.RED_BANNER;
            case TEAM_BLUE -> Material.BLUE_BANNER;
            case LEAVE_BED -> Material.RED_BED;
            case START_PAPER -> Material.PAPER;
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
            case KICK_FORWARD -> "&b击球 &7(拿在手里左键)";
            case KICK_VERTICAL -> "&e挑高靴 &7(拿在手里左键)";
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
        };
    }

    private List<String> loreOf(Kind kind, int power) {
        return switch (kind) {
            case SPRINT -> List.of("&7速度效果 5 秒", "&7冷却 5 秒");
            case JUMP -> List.of("&7立即高高跳起一次", "&7冷却 5 秒");
            case KICK_FORWARD -> List.of("&7左键把球往面前踢", "&7半人高的平抽，无冷却");
            case KICK_VERTICAL -> List.of("&7左键把球垂直击飞", "&7无冷却");
            case KICKOFF -> List.of("&7左键开球", "&7力度越大，球飞得越高越远");
            case TEAM_RED -> List.of("&7右键加入红队");
            case TEAM_BLUE -> List.of("&7右键加入蓝队");
            case LEAVE_BED -> List.of("&7右键离开场地");
            case START_PAPER -> List.of("&7OP 右键立即开始");
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
        p.getInventory().setItem(8, make(Kind.LEAVE_BED, 0));
    }

    /** 对局装备：队服（胸甲+护腿）+ 冲刺/高跳 + 两双踢球鞋 + 退出床。快捷栏：1冲刺 2高跳 3击球 4空 5挑高靴 9退出床。 */
    public void giveMatchKit(Player p, FootballArena.Team team) {
        p.getInventory().clear();
        boolean red = team == FootballArena.Team.RED;
        Color color = red ? Color.fromRGB(0xC03030) : Color.fromRGB(0x3050C0);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        if (chest.getItemMeta() instanceof LeatherArmorMeta m) {
            m.setColor(color);
            m.displayName(Chat.noItalic(Chat.color((red ? "&c" : "&9") + "队服")));
            chest.setItemMeta(m);
        }
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        if (legs.getItemMeta() instanceof LeatherArmorMeta m) {
            m.setColor(color);
            m.displayName(Chat.noItalic(Chat.color((red ? "&c" : "&9") + "球裤")));
            legs.setItemMeta(m);
        }
        p.getInventory().setChestplate(chest);
        p.getInventory().setLeggings(legs);

        p.getInventory().setItem(0, make(Kind.SPRINT, 0));
        p.getInventory().setItem(1, make(Kind.JUMP, 0));
        p.getInventory().setItem(2, make(Kind.KICK_FORWARD, 0));
        // 第 4 格（index 3）必须留空：开球靴占快捷栏前 4 格，开球踢出换回普通装备的瞬间手里那格
        // 会被原位顶替——若挑高靴也在这格，紧接着补的一脚会把刚开出的球垂直踢飞（2026-10-17 反馈）
        p.getInventory().setItem(4, make(Kind.KICK_VERTICAL, 0));
        p.getInventory().setItem(8, make(Kind.LEAVE_BED, 0));
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
