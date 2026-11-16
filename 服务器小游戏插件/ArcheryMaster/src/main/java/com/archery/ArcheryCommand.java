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
            case "setarea" -> {
                Player player = adminPlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length == 6) {
                    Double centerX = number(args[1]);
                    Double centerZ = number(args[2]);
                    Double radius = number(args[3]);
                    Double yMin = number(args[4]);
                    Double yMax = number(args[5]);
                    if (centerX == null || centerZ == null || radius == null
                            || yMin == null || yMax == null) {
                        Chat.send(sender, "&c坐标、半径和高度都必须是数字。");
                        return true;
                    }
                    manager.setArea(player, centerX, centerZ, radius, yMin, yMax);
                } else if (args.length == 4) {
                    Double radius = number(args[1]);
                    Double yMin = number(args[2]);
                    Double yMax = number(args[3]);
                    if (radius == null || yMin == null || yMax == null) {
                        Chat.send(sender, "&c半径和高度都必须是数字。");
                        return true;
                    }
                    manager.setArea(player, player.getLocation().getX(),
                            player.getLocation().getZ(), radius, yMin, yMax);
                } else {
                    Chat.send(sender, "&c用法：/archery setarea <中心X> <中心Z> <半径> <最低Y> <最高Y>");
                    Chat.send(sender, "&c或站到中心：/archery setarea <半径> <最低Y> <最高Y>");
                }
            }
            case "setstart" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.setStart(player);
                }
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
            Chat.send(sender, "&7管理：&fsetarea &7/&f setstart &7/&f info &7/&f reload &7/&f reset <玩家|all> &7/&f debug");
            Chat.send(sender, "&7区域：&f/archery setarea <中心X> <中心Z> <半径> <最低Y> <最高Y>");
            Chat.send(sender, "&7　　　或站到中心：&f/archery setarea <半径> <最低Y> <最高Y>");
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

    private static Double number(String raw) {
        try {
            return Double.parseDouble(raw);
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
                for (String sub : List.of("setarea", "setstart", "info", "reload", "reset", "debug")) {
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
