package com.archery;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /archery（别名 /am、/箭术大师）。无参数直接开始。 */
public class ArcheryCommand implements CommandExecutor, TabCompleter {

    private final ArcheryPlugin plugin;

    public ArcheryCommand(ArcheryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ArcheryManager manager = plugin.getManager();
        if (args.length == 0) {
            Player player = asPlayer(sender);
            if (player != null) {
                manager.join(player);
            } else {
                help(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join", "start", "play", "射箭", "开始" -> {
                Player player = asPlayer(sender);
                if (player != null) {
                    manager.join(player);
                }
            }
            case "leave", "quit", "exit", "退出" -> {
                Player player = asPlayer(sender);
                if (player != null) {
                    manager.leave(player);
                }
            }
            case "top", "rank" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("place")) {
                    Player player = adminPlayer(sender);
                    if (player != null) {
                        manager.placeLeaderboard(player);
                    }
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                    if (requireAdmin(sender)) {
                        manager.removeLeaderboards(sender);
                    }
                } else {
                    showTop(sender, manager);
                }
            }
            case "stats", "me" -> showStats(sender, manager, args);
            case "help", "?" -> help(sender);
            case "setstart" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.setStart(player);
                }
            }
            case "edit" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.enterEdit(player);
                }
            }
            case "regions", "regionlist" -> {
                if (requireAdmin(sender)) {
                    for (String line : manager.listRegions()) {
                        Chat.send(sender, line);
                    }
                }
            }
            case "region" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
                    Integer index = integer(args[2]);
                    if (index == null || !manager.removeRegion(index)) {
                        Chat.send(sender, "&c序号不对。");
                        return true;
                    }
                    Chat.send(sender, "&a已删除区域 #" + index + "。");
                    return true;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
                    Integer index = integer(args[2]);
                    if (index == null) {
                        Chat.send(sender, "&c序号不对。");
                        return true;
                    }
                    if (args.length == 5) {
                        Integer value = integer(args[4]);
                        if (value == null) {
                            Chat.send(sender, "&c数值不对。");
                            return true;
                        }
                        boolean ok = switch (args[3].toLowerCase()) {
                            case "count", "数量", "总数" -> manager.setRegionCount(index, value);
                            case "score", "基础分", "基础" -> manager.setRegionScore(index, value);
                            default -> false;
                        };
                        if (!ok) {
                            Chat.send(sender, "&c设置失败：检查序号/数值（保底总和不能超过总数）。");
                            return true;
                        }
                        Chat.send(sender, "&a已更新区域 #" + index + "，用 /archery regions 查看。");
                        return true;
                    }
                    if (args.length == 6) {
                        TargetType type = TargetType.fromKey(args[4]);
                        Integer value = integer(args[5]);
                        if (type == null || value == null) {
                            Chat.send(sender, "&c类型或数值不对（类型：fixed/moving/fast）。");
                            return true;
                        }
                        boolean ok = switch (args[3].toLowerCase()) {
                            case "weight", "权重" -> manager.setRegionWeight(index, type, value);
                            case "guarantee", "min", "保底" -> manager.setRegionMinimum(index, type, value);
                            default -> false;
                        };
                        if (!ok) {
                            Chat.send(sender, "&c设置失败：检查序号/数值（保底总和不能超过总数）。");
                            return true;
                        }
                        Chat.send(sender, "&a已更新区域 #" + index + "，用 /archery regions 查看。");
                        return true;
                    }
                }
                Chat.send(sender, "&c用法：");
                Chat.send(sender, "&f/archery region remove <序号>");
                Chat.send(sender, "&f/archery region set <序号> count <数量>");
                Chat.send(sender, "&f/archery region set <序号> weight <fixed|moving|fast> <数值>");
                Chat.send(sender, "&f/archery region set <序号> guarantee <fixed|moving|fast> <数量>");
                Chat.send(sender, "&f/archery region set <序号> score <基础分>");
            }
            case "info" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                for (String line : manager.regionInfo()) {
                    Chat.send(sender, line);
                }
            }
            case "debug" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                Player player = asPlayer(sender);
                if (player != null) {
                    for (String line : manager.debugInfo(player.getUniqueId())) {
                        Chat.send(sender, line);
                    }
                }
            }
            case "reload" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                manager.reload();
                Chat.send(sender, "&a箭术大师配置已重载。");
            }
            case "reset" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/archery reset <在线玩家> &7或 &f/archery reset all");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    manager.resetAllRecords();
                    Chat.send(sender, "&a已清空全部玩家的箭术大师记录。");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    Chat.send(sender, "&c玩家不在线。");
                    return true;
                }
                manager.resetRecord(target.getUniqueId());
                Chat.send(sender, "&a已清空 &f" + target.getName() + " &a的箭术大师记录。");
            }
            default -> help(sender);
        }
        return true;
    }

    private void showTop(CommandSender sender, ArcheryManager manager) {
        List<ArcheryManager.TopEntry> top = manager.topEntries();
        Chat.send(sender, "&8&m----&r &6&l箭术大师 · 排行榜 &8&m----");
        if (top.isEmpty()) {
            Chat.send(sender, "&7还没有记录。");
            return;
        }
        int rank = 1;
        for (ArcheryManager.TopEntry entry : top) {
            Chat.send(sender, "&e#" + rank + " &f" + entry.name() + " &7- &6"
                    + entry.score() + " &7分");
            rank++;
        }
    }

    private void showStats(CommandSender sender, ArcheryManager manager, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                Chat.send(sender, "&c玩家不在线。");
                return;
            }
        } else {
            target = asPlayer(sender);
            if (target == null) {
                Chat.send(sender, "&c用法：/archery stats <在线玩家>");
                return;
            }
        }
        int best = manager.bestScore(target.getUniqueId());
        int rank = manager.rankOf(target.getUniqueId());
        Chat.send(sender, "&6" + target.getName() + " &f最高分：&6" + best
                + (rank > 0 ? " &7（第 " + rank + " 名）" : " &7（暂无排名）"));
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&8&m--------&r &6&l箭术大师 &8&m--------");
        Chat.send(sender, "&f/archery &7- 开始射箭");
        Chat.send(sender, "&f/archery leave &7- 退出本局");
        Chat.send(sender, "&f/archery top &7- 查看排行榜");
        Chat.send(sender, "&f/archery stats [玩家] &7- 查看最高分和名次");
        if (hasAdmin(sender)) {
            Chat.send(sender, "&7管理：&fedit &7/&f setstart &7/&f info &7/&f reload &7/&f reset <玩家|all> &7/&f debug");
            Chat.send(sender, "&7靶场：&f/archery edit &7进入编辑模式（木棍选对角、下界之星确认并输入基础分和参数、绿宝石保存、屏障退出）");
            Chat.send(sender, "&7　　　&f/archery regions &7查看全部区域，&f/archery region remove <序号> &7删除");
            Chat.send(sender, "&7　　　&f/archery region set <序号> count|weight|guarantee|score ... &7在线改参数");
            Chat.send(sender, "&7排行榜：&f/archery top place &7（当前位置放置）、&f/archery top remove");
        }
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.send(sender, "&c这个命令只能由玩家执行。");
        return null;
    }

    private Player adminPlayer(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return null;
        }
        return asPlayer(sender);
    }

    private boolean requireAdmin(CommandSender sender) {
        if (hasAdmin(sender)) {
            return true;
        }
        Chat.send(sender, "&c你没有权限执行这个操作。");
        return false;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("archery.admin");
    }

    private static Integer integer(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("join", "leave", "top", "stats", "help")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            if (hasAdmin(sender)) {
                for (String sub : List.of("edit", "setstart", "regions", "region", "info", "reload", "reset", "debug")) {
                    if (sub.startsWith(args[0].toLowerCase())) {
                        out.add(sub);
                    }
                }
            }
        } else if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            List<String> options = hasAdmin(sender) ? List.of("place", "remove") : List.of();
            for (String option : options) {
                if (option.startsWith(args[1].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && "region".equalsIgnoreCase(args[0]) && hasAdmin(sender)) {
            for (String option : List.of("remove", "set")) {
                if (option.startsWith(args[1].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length == 4 && "region".equalsIgnoreCase(args[0])
                && "set".equalsIgnoreCase(args[1]) && hasAdmin(sender)) {
            for (String option : List.of("count", "weight", "guarantee", "score")) {
                if (option.startsWith(args[3].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length == 5 && "region".equalsIgnoreCase(args[0])
                && "set".equalsIgnoreCase(args[1]) && hasAdmin(sender)
                && ("weight".equalsIgnoreCase(args[3]) || "guarantee".equalsIgnoreCase(args[3]))) {
            for (String option : List.of("fixed", "moving", "fast")) {
                if (option.startsWith(args[4].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length == 2
                && ("stats".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase();
            if ("reset".equalsIgnoreCase(args[0]) && "all".startsWith(prefix)) {
                out.add("all");
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }
}
