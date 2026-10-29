package com.skypit;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /skypit 命令：区域配置（OP）+ 加入战斗 / 查看击杀（玩家）。 */
public class SkyPitCommand implements CommandExecutor, TabCompleter {

    private final SkyPitPlugin plugin;

    public SkyPitCommand(SkyPitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        SkyPitManager m = plugin.getManager();
        boolean admin = isAdmin(sender);

        switch (sub) {
            case "region" -> {
                if (!requireAdmin(sender, admin)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/skypit region pos1|pos2|info");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "pos1" -> m.setPos1(p);
                    case "pos2" -> m.setPos2(p);
                    case "info" -> Chat.send(sender, m.regionInfo());
                    default -> Chat.send(sender, "&c用法：/skypit region pos1|pos2|info");
                }
                return true;
            }
            case "set" -> {
                if (!requireAdmin(sender, admin)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/skypit set entry|spawn");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "entry" -> m.setEntry(p);
                    case "spawn" -> m.setRespawnPoint(p);
                    default -> Chat.send(sender, "&c用法：/skypit set entry|spawn");
                }
                return true;
            }
            case "kit" -> {
                if (!requireAdmin(sender, admin)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                m.openKitEditor(p);
                return true;
            }
            case "save" -> {
                if (!requireAdmin(sender, admin)) return true;
                m.saveAll();
                Chat.send(sender, "&a已保存天坑配置（区域/入口/复活点/装备/排行榜）。");
                return true;
            }
            case "reload" -> {
                if (!requireAdmin(sender, admin)) return true;
                m.reload();
                Chat.send(sender, "&a已重载天坑配置。");
                return true;
            }
            case "top" -> {
                if (!requireAdmin(sender, admin)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                    m.removeLeaderboards(p);
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("refresh")) {
                    m.refreshLeaderboards();
                    Chat.send(sender, "&a已刷新全部天坑击杀榜。");
                } else {
                    m.placeLeaderboard(p);
                }
                return true;
            }
            case "stats" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                    if (!requireAdmin(sender, admin)) return true;
                    if (args.length >= 3) {
                        Player target = plugin.getServer().getPlayerExact(args[2]);
                        if (target == null) {
                            Chat.send(sender, "&c玩家不在线。");
                            return true;
                        }
                        m.resetKills(target.getUniqueId());
                        Chat.send(sender, "&a已清空 &e" + target.getName() + "&a 的天坑击杀记录。");
                    } else {
                        m.resetKills();
                        Chat.send(sender, "&a已清空全部天坑击杀记录（排行榜已刷新）。");
                    }
                    return true;
                }
                Player target;
                if (args.length >= 2) {
                    target = plugin.getServer().getPlayerExact(args[1]);
                    if (target == null) {
                        Chat.send(sender, "&c玩家不在线。");
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    Chat.send(sender, "&c请指定玩家：/skypit stats <玩家>");
                    return true;
                }
                UUID id = target.getUniqueId();
                Chat.send(sender, "&6" + target.getName() + " 的天坑击杀数：&e" + m.getKills(id));
                return true;
            }
            case "info" -> {
                Chat.send(sender, "&6====== 天坑 (/skypit) ======");
                Chat.send(sender, m.regionInfo());
                Chat.send(sender, "&7入口：&f" + (m.getEntry() == null ? "未设置" : shortLoc(m.getEntry())));
                Chat.send(sender, "&7装备：&f" + (m.hasKitConfigured() ? "已配置" : "未配置"));
                Chat.send(sender, "&7场内战斗人数：&f" + m.pitCount() + " &7| 击杀记录：&f" + m.totalPlayers() + " 人");
                if (admin) {
                    Chat.send(sender, "&7OP 用 /skypit region / set / kit / save 完成配置。");
                }
                return true;
            }
            case "warp" -> {
                Player p = asPlayer(sender);
                if (p == null) return true;
                m.warp(p);
                return true;
            }
            case "leave" -> {
                Player p = asPlayer(sender);
                if (p == null) return true;
                if (!m.inPit(p.getUniqueId())) {
                    Chat.send(p, "&c你不在天坑内。");
                    return true;
                }
                m.exitPit(p, true);
                return true;
            }
            case "help", "?" -> help(sender);
            default -> Chat.send(sender, "&c未知子命令。输入 /skypit help 查看帮助。");
        }
        return true;
    }

    private void help(CommandSender sender) {
        boolean admin = isAdmin(sender);
        Chat.send(sender, "&6====== 天坑 (/skypit) ======");
        Chat.send(sender, "&e/skypit warp&f 传送到天坑入口");
        Chat.send(sender, "&e/skypit leave&f 离开天坑（归还装备回入口）");
        Chat.send(sender, "&e/skypit stats [玩家]&f 查看击杀数");
        if (admin) {
            Chat.send(sender, "&6-- OP 管理 --");
            Chat.send(sender, "&e/skypit region pos1 &f/&e pos2&f 圈战斗区域（站在两个对角）  &e/skypit region info&f 查看");
            Chat.send(sender, "&e/skypit set entry&f 设置入口（warp 传送点）  &e/skypit set spawn&f 设置死亡复活点");
            Chat.send(sender, "&e/skypit kit&f 打开装备配置菜单（与决斗相同，进入天坑自动发放）");
            Chat.send(sender, "&e/skypit save&f 保存配置  &e/skypit reload&f 重载配置");
            Chat.send(sender, "&e/skypit top&f 在脚下放置击杀榜浮空文字（前十名）  &e/skypit top remove&f 移除  &e/skypit top refresh&f 刷新");
            Chat.send(sender, "&e/skypit stats reset [玩家]&f 清空击杀记录");
            Chat.send(sender, "&e/skypit info&f 查看天坑配置状态");
        }
    }

    /** 管理权限：OP 或持有 plugin.yml 里声明的 skypit.admin 节点。 */
    private boolean isAdmin(CommandSender sender) {
        return sender instanceof Player p ? (p.isOp() || p.hasPermission("skypit.admin")) : true;
    }

    private boolean requireAdmin(CommandSender sender, boolean admin) {
        if (!admin) {
            Chat.send(sender, "&c只有 OP 或拥有 skypit.admin 权限的玩家可以使用该命令。");
            return false;
        }
        return true;
    }

    private Player asPlayer(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Chat.send(sender, "&c该命令需要玩家执行。");
            return null;
        }
        return p;
    }

    private String shortLoc(org.bukkit.Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = isAdmin(sender);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("warp", "leave", "stats", "info"));
            if (admin) {
                subs.addAll(List.of("region", "set", "kit", "save", "reload", "top", "stats"));
            }
            String cur = args[0].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(cur)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2) {
            String cur = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("region") && admin) {
                for (String s : List.of("pos1", "pos2", "info")) {
                    if (s.startsWith(cur)) out.add(s);
                }
            } else if (args[0].equalsIgnoreCase("set") && admin) {
                for (String s : List.of("entry", "spawn")) {
                    if (s.startsWith(cur)) out.add(s);
                }
            } else if (args[0].equalsIgnoreCase("top") && admin) {
                for (String s : List.of("remove", "refresh")) {
                    if (s.startsWith(cur)) out.add(s);
                }
            } else if (args[0].equalsIgnoreCase("stats")) {
                if ("reset".startsWith(cur) && admin) {
                    out.add("reset");
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("stats")
                && args[1].equalsIgnoreCase("reset") && admin) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
