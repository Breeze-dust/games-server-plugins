package com.archery;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /archery（别名 /am、/箭术大师）。无参数打开难度选择。 */
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
                manager.openDifficultyMenu(player);
            } else {
                help(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "join", "start", "play", "射箭", "开始" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 2) {
                    manager.openDifficultyMenu(player);
                    return true;
                }
                ArcherySettings.Difficulty difficulty = manager.difficultyById(args[1]);
                if (difficulty == null) {
                    Chat.send(sender, "&c未知难度：" + args[1] + "（可用 normal/hard/evil 或 正常/困难/恶意）");
                    return true;
                }
                manager.join(player, difficulty);
            }
            case "normal", "hard", "evil", "正常", "困难", "恶意" -> {
                Player player = asPlayer(sender);
                ArcherySettings.Difficulty difficulty = switch (sub) {
                    case "normal", "正常" -> manager.difficultyById("normal");
                    case "hard", "困难" -> manager.difficultyById("hard");
                    default -> manager.difficultyById("evil");
                };
                if (player != null && difficulty != null) {
                    manager.join(player, difficulty);
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
                    if (player == null) {
                        return true;
                    }
                    ArcherySettings.Difficulty difficulty = args.length >= 3
                            ? manager.difficultyById(args[2]) : manager.defaultDifficulty();
                    if (difficulty == null) {
                        Chat.send(sender, "&c未知难度：" + args[2]);
                        return true;
                    }
                    manager.placeLeaderboard(player, difficulty);
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                    if (requireAdmin(sender)) {
                        manager.removeLeaderboards(sender);
                    }
                } else {
                    ArcherySettings.Difficulty difficulty = args.length >= 2
                            ? manager.difficultyById(args[1]) : manager.defaultDifficulty();
                    if (difficulty == null) {
                        Chat.send(sender, "&c未知难度：" + args[1]);
                        return true;
                    }
                    showTop(sender, manager, difficulty);
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
                if (player == null) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/archery edit <normal|hard|evil>");
                    return true;
                }
                ArcherySettings.Difficulty difficulty = manager.difficultyById(args[1]);
                if (difficulty == null) {
                    Chat.send(sender, "&c未知难度：" + args[1]);
                    return true;
                }
                manager.enterEdit(player, difficulty);
            }
            case "regions", "regionlist" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/archery regions <normal|hard|evil>");
                    return true;
                }
                ArcherySettings.Difficulty difficulty = manager.difficultyById(args[1]);
                if (difficulty == null) {
                    Chat.send(sender, "&c未知难度：" + args[1]);
                    return true;
                }
                for (String line : manager.listRegions(difficulty)) {
                    Chat.send(sender, line);
                }
            }
            case "region" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                handleRegion(sender, manager, args);
            }
            case "info" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                if (args.length >= 2) {
                    ArcherySettings.Difficulty difficulty = manager.difficultyById(args[1]);
                    if (difficulty == null) {
                        Chat.send(sender, "&c未知难度：" + args[1]);
                        return true;
                    }
                    for (String line : manager.regionInfo(difficulty)) {
                        Chat.send(sender, line);
                    }
                } else {
                    for (String line : manager.difficultyInfo()) {
                        Chat.send(sender, line);
                    }
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

    private void handleRegion(CommandSender sender, ArcheryManager manager, String[] args) {
        if (args.length >= 4 && args[1].equalsIgnoreCase("remove")) {
            ArcherySettings.Difficulty difficulty = manager.difficultyById(args[2]);
            Integer index = integer(args[3]);
            if (difficulty == null || index == null || !manager.removeRegion(difficulty, index)) {
                Chat.send(sender, "&c难度或序号不对。");
                return;
            }
            Chat.send(sender, "&a已删除 " + difficulty.name + " 难度的区域 #" + index + "。");
            return;
        }

        if (args.length >= 6 && args[1].equalsIgnoreCase("set")) {
            ArcherySettings.Difficulty difficulty = manager.difficultyById(args[2]);
            Integer index = integer(args[3]);
            if (difficulty == null || index == null) {
                Chat.send(sender, "&c难度或序号不对。");
                return;
            }
            String key = args[4].toLowerCase();
            if (args.length == 6) {
                if (isAxisKey(key)) {
                    TargetAxis axis = TargetAxis.fromKey(args[5]);
                    if (axis == null || !manager.setRegionAxis(difficulty, index, axis)) {
                        Chat.send(sender, "&c移动轴只能是 x 或 z。");
                        return;
                    }
                    updated(sender, difficulty, index);
                    return;
                }
                Integer value = integer(args[5]);
                if (value == null) {
                    Chat.send(sender, "&c数值不对。");
                    return;
                }
                boolean ok = switch (key) {
                    case "count", "数量", "总数" -> manager.setRegionCount(difficulty, index, value);
                    case "score", "基础分", "基础" -> manager.setRegionScore(difficulty, index, value);
                    default -> false;
                };
                if (!ok) {
                    Chat.send(sender, "&c设置失败：检查序号/数值（保底总和不能超过总数）。");
                    return;
                }
                updated(sender, difficulty, index);
                return;
            }
            if (args.length == 7) {
                RegionMetric metric = RegionMetric.fromKey(args[5]);
                Integer value = integer(args[6]);
                if (metric == null || value == null) {
                    Chat.send(sender, "&c类型或数值不对（类型：fixed/moving/fast/normal/small）。");
                    return;
                }
                boolean ok = switch (key) {
                    case "weight", "权重" -> manager.setRegionWeight(difficulty, index, metric, value);
                    case "guarantee", "min", "保底" -> manager.setRegionMinimum(difficulty, index, metric, value);
                    default -> false;
                };
                if (!ok) {
                    Chat.send(sender, "&c设置失败：检查序号/数值（保底总和不能超过总数）。");
                    return;
                }
                updated(sender, difficulty, index);
                return;
            }
        }

        Chat.send(sender, "&c用法：");
        Chat.send(sender, "&f/archery region remove <难度> <序号>");
        Chat.send(sender, "&f/archery region set <难度> <序号> count <数量>");
        Chat.send(sender, "&f/archery region set <难度> <序号> score <基础分>");
        Chat.send(sender, "&f/archery region set <难度> <序号> axis <x|z>");
        Chat.send(sender, "&f/archery region set <难度> <序号> weight <fixed|moving|fast|normal|small> <数值>");
        Chat.send(sender, "&f/archery region set <难度> <序号> guarantee <fixed|moving|fast|small> <数量>");
    }

    private static boolean isAxisKey(String key) {
        return key.equals("axis") || key.equals("轴") || key.equals("移动轴");
    }

    private void updated(CommandSender sender, ArcherySettings.Difficulty difficulty, int index) {
        Chat.send(sender, "&a已更新 " + difficulty.name + " 难度的区域 #" + index
                + "，用 /archery regions " + difficulty.id + " 查看。");
    }

    private void showTop(CommandSender sender, ArcheryManager manager,
                         ArcherySettings.Difficulty difficulty) {
        List<ArcheryManager.TopEntry> top = manager.topEntries(difficulty);
        Chat.send(sender, "&8&m----&r " + difficulty.color + "&l箭术大师 · " + difficulty.name
                + "榜 &8&m----");
        if (top.isEmpty()) {
            Chat.send(sender, "&7这个难度还没有记录。");
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
        Chat.send(sender, "&6" + target.getName() + " &f各难度最高分：");
        for (ArcherySettings.Difficulty difficulty : manager.difficulties()) {
            int best = manager.bestScore(target.getUniqueId(), difficulty);
            int rank = manager.rankOf(target.getUniqueId(), difficulty);
            Chat.send(sender, "  " + difficulty.color + difficulty.name + " &7- &e" + best
                    + (rank > 0 ? " &7（第 " + rank + " 名）" : ""));
        }
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&8&m--------&r &6&l箭术大师 &8&m--------");
        Chat.send(sender, "&f/archery &7- 选择难度并开始射箭");
        Chat.send(sender, "&f/archery normal|hard|evil &7- 直接开始某个难度");
        Chat.send(sender, "&f/archery leave &7- 退出本局");
        Chat.send(sender, "&f/archery top [难度] &7- 查看排行榜");
        Chat.send(sender, "&f/archery stats [玩家] &7- 查看各难度最高分");
        if (hasAdmin(sender)) {
            Chat.send(sender, "&7管理：&fedit <难度> &7/&f setstart &7/&f info [难度] &7/&f reload &7/&f reset <玩家|all> &7/&f debug");
            Chat.send(sender, "&7靶场：&f/archery edit <normal|hard|evil> &7编辑对应难度；"
                    + "&f/archery regions <难度> &7查看区域");
            Chat.send(sender, "&7区域：&f/archery region remove <难度> <序号>，"
                    + "&fset <难度> <序号> count|score|axis|weight|guarantee ...");
            Chat.send(sender, "&7排行榜：&f/archery top place [难度] &7（当前位置放置）、&f/archery top remove");
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

    private List<String> difficultyIds() {
        List<String> ids = new ArrayList<>();
        for (ArcherySettings.Difficulty difficulty : plugin.getManager().difficulties()) {
            ids.add(difficulty.id);
        }
        return ids;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("join", "normal", "hard", "evil", "leave", "top", "stats", "help")) {
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
            return out;
        }

        String first = args[0].toLowerCase();
        String second = args[1].toLowerCase();
        if (args.length == 2) {
            if (first.equals("top")) {
                if (hasAdmin(sender)) {
                    for (String option : List.of("place", "remove")) {
                        if (option.startsWith(second)) {
                            out.add(option);
                        }
                    }
                }
                for (String id : difficultyIds()) {
                    if (id.startsWith(second)) {
                        out.add(id);
                    }
                }
            } else if (first.equals("join") || first.equals("play") || first.equals("start")
                    || first.equals("edit") || first.equals("regions") || first.equals("info")) {
                for (String id : difficultyIds()) {
                    if (id.startsWith(second)) {
                        out.add(id);
                    }
                }
            } else if (first.equals("region") && hasAdmin(sender)) {
                for (String option : List.of("remove", "set")) {
                    if (option.startsWith(second)) {
                        out.add(option);
                    }
                }
            } else if (first.equals("stats") || first.equals("reset")) {
                if (first.equals("reset") && "all".startsWith(second)) {
                    out.add("all");
                }
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(second)) {
                        out.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3 && first.equals("top")
                && ("place".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))) {
            for (String id : difficultyIds()) {
                if (id.startsWith(args[2].toLowerCase())) {
                    out.add(id);
                }
            }
        } else if (args.length == 3 && first.equals("region") && hasAdmin(sender)
                && ("remove".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1]))) {
            for (String id : difficultyIds()) {
                if (id.startsWith(args[2].toLowerCase())) {
                    out.add(id);
                }
            }
        } else if (args.length == 5 && first.equals("region") && hasAdmin(sender)
                && "set".equalsIgnoreCase(args[1])) {
            for (String option : List.of("count", "score", "axis", "weight", "guarantee")) {
                if (option.startsWith(args[4].toLowerCase())) {
                    out.add(option);
                }
            }
        } else if (args.length == 6 && first.equals("region") && hasAdmin(sender)
                && "set".equalsIgnoreCase(args[1])) {
            String key = args[4].toLowerCase();
            if (key.equals("axis") || key.equals("轴") || key.equals("移动轴")) {
                for (String option : List.of("x", "z")) {
                    if (option.startsWith(args[5].toLowerCase())) {
                        out.add(option);
                    }
                }
            } else if (key.equals("weight") || key.equals("权重")
                    || key.equals("guarantee") || key.equals("min") || key.equals("保底")) {
                for (String option : List.of("fixed", "moving", "fast", "normal", "small")) {
                    if (option.startsWith(args[5].toLowerCase())) {
                        out.add(option);
                    }
                }
            }
        }
        return out;
    }
}
