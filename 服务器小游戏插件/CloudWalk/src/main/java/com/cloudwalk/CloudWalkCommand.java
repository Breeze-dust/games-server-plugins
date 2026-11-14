package com.cloudwalk;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /cloudwalk（别名 /parkour、/云中漫步）。无参数直接加入。 */
public class CloudWalkCommand implements CommandExecutor, TabCompleter {

    private final CloudWalkPlugin plugin;

    public CloudWalkCommand(CloudWalkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CloudWalkManager manager = plugin.getManager();
        if (args.length == 0) {
            Player player = asPlayer(sender);
            if (player != null) {
                manager.openDifficultyMenu(player);
            } else {
                help(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join", "play" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 2) {
                    manager.openDifficultyMenu(player);
                    return true;
                }
                CloudWalkSettings.Difficulty difficulty = difficultyArg(manager, args[1]);
                if (difficulty == null) {
                    Chat.send(sender, "&c未知难度：" + args[1] + "（可用 easy/medium/hard 或 简单/中等/困难）");
                    return true;
                }
                manager.join(player, difficulty);
            }
            case "easy", "medium", "hard" -> {
                Player player = asPlayer(sender);
                CloudWalkSettings.Difficulty difficulty = manager.difficultyById(sub);
                if (player != null && difficulty != null) {
                    manager.join(player, difficulty);
                }
            }
            case "leave", "quit", "exit" -> {
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
                    CloudWalkSettings.Difficulty difficulty = args.length >= 2
                            ? difficultyArg(manager, args[1])
                            : manager.defaultDifficulty();
                    if (difficulty == null) {
                        Chat.send(sender, "&c未知难度：" + args[1]);
                        return true;
                    }
                    showTop(sender, manager, difficulty);
                }
            }
            case "stats", "me" -> showStats(sender, manager, args);
            case "help", "?" -> help(sender);
            case "pos1" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.setPos1(player);
                }
            }
            case "pos2" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.setPos2(player);
                }
            }
            case "setstart" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.setStart(player);
                }
            }
            case "setlobby" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.setLobby(player);
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
            case "reload" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                manager.reload();
                Chat.send(sender, "&a云中漫步配置已重载。");
            }
            case "reset" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/cloudwalk reset <在线玩家>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    Chat.send(sender, "&c玩家不在线。");
                    return true;
                }
                manager.resetRecord(target.getUniqueId());
                Chat.send(sender, "&a已清空 &f" + target.getName() + " &a的云中漫步记录。");
            }
            default -> help(sender);
        }
        return true;
    }

    private void showTop(CommandSender sender, CloudWalkManager manager, CloudWalkSettings.Difficulty difficulty) {
        List<CloudWalkManager.TopEntry> top = manager.topEntries(difficulty.id);
        Chat.send(sender, "&8&m----&r " + difficulty.color + "&l云中漫步 · " + difficulty.name
                + "榜 &8&m----");
        if (top.isEmpty()) {
            Chat.send(sender, "&7这个难度还没有记录。");
            return;
        }
        int rank = 1;
        for (CloudWalkManager.TopEntry entry : top) {
            Chat.send(sender, "&e#" + rank + " &f" + entry.name() + " &7- "
                    + difficulty.color + entry.score() + " &7分");
            rank++;
        }
    }

    private void showStats(CommandSender sender, CloudWalkManager manager, String[] args) {
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
                Chat.send(sender, "&c用法：/cloudwalk stats <在线玩家>");
                return;
            }
        }
        Chat.send(sender, "&b" + target.getName() + " &f各难度最高分：");
        for (CloudWalkSettings.Difficulty difficulty : manager.difficulties()) {
            int best = manager.bestScore(target.getUniqueId(), difficulty.id);
            int rank = manager.rankOf(target.getUniqueId(), difficulty.id);
            Chat.send(sender, "  " + difficulty.color + difficulty.name + " &7- &e" + best
                    + (rank > 0 ? " &7（第 " + rank + " 名）" : ""));
        }
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&8&m--------&r &b&l云中漫步 &8&m--------");
        Chat.send(sender, "&f/cloudwalk &7- 选择难度并开始跑酷");
        Chat.send(sender, "&f/cloudwalk easy|medium|hard &7- 直接开始某个难度");
        Chat.send(sender, "&f/cloudwalk leave &7- 退出本局");
        Chat.send(sender, "&f/cloudwalk top [难度] &7- 对应难度前十（聊天）");
        Chat.send(sender, "&f/cloudwalk stats [玩家] &7- 查看各难度最高分");
        if (hasAdmin(sender)) {
            Chat.send(sender, "&7管理：&fpos1 &7/&f pos2 &7/&f setstart &7/&f setlobby &7/&f info &7/&f reload &7/&f reset <玩家>");
            Chat.send(sender, "&7全息榜：&f/cloudwalk top place &7（在当前位置放置）、&f/cloudwalk top remove");
        }
    }

    private CloudWalkSettings.Difficulty difficultyArg(CloudWalkManager manager, String raw) {
        CloudWalkSettings.Difficulty difficulty = manager.difficultyById(raw);
        if (difficulty != null) {
            return difficulty;
        }
        for (CloudWalkSettings.Difficulty candidate : manager.difficulties()) {
            if (candidate.name.equals(raw)) {
                return candidate;
            }
        }
        return null;
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
        return sender.isOp() || sender.hasPermission("cloudwalk.admin");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("join", "easy", "medium", "hard", "leave", "top", "stats", "help")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            if (hasAdmin(sender)) {
                for (String sub : List.of("pos1", "pos2", "setstart", "setlobby", "info", "reload", "reset")) {
                    if (sub.startsWith(args[0].toLowerCase())) {
                        out.add(sub);
                    }
                }
            }
        } else if (args.length == 2
                && ("stats".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]))) {
            String prefix = args[1].toLowerCase();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        } else if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            List<String> options = hasAdmin(sender) ? List.of("place", "remove") : List.of();
            for (String option : options) {
                if (option.startsWith(args[1].toLowerCase())) {
                    out.add(option);
                }
            }
            for (CloudWalkSettings.Difficulty difficulty : plugin.getManager().difficulties()) {
                if (difficulty.id.startsWith(args[1].toLowerCase())) {
                    out.add(difficulty.id);
                }
            }
        } else if (args.length == 2
                && ("join".equalsIgnoreCase(args[0]) || "play".equalsIgnoreCase(args[0]))) {
            for (CloudWalkSettings.Difficulty difficulty : plugin.getManager().difficulties()) {
                if (difficulty.id.startsWith(args[1].toLowerCase())) {
                    out.add(difficulty.id);
                }
            }
        }
        return out;
    }
}
