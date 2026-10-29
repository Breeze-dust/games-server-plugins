package com.tntbattle;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TNTBattleCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "leave", "list", "stats", "top", "help",
            "pos1", "pos2", "setspawn", "addspawn", "clearspawns",
            "setlobby", "setvoid", "setbuildheight", "settop", "removetop",
            "save", "start", "stop", "reload", "music");

    private final TNTBattlePlugin plugin;
    private final TNTBattleManager manager;

    public TNTBattleCommand(TNTBattlePlugin plugin, TNTBattleManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> join(sender);
            case "leave" -> leave(sender);
            case "list" -> list(sender);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender, args);
            case "help" -> help(sender);
            case "pos1" -> setCorner(sender, 1);
            case "pos2" -> setCorner(sender, 2);
            case "setspawn" -> setSpawn(sender, args);
            case "addspawn" -> addSpawn(sender, args);
            case "clearspawns" -> clearSpawns(sender, args);
            case "setlobby" -> setLobby(sender);
            case "setvoid" -> setVoid(sender, args);
            case "setbuildheight" -> setBuildHeight(sender, args);
            case "settop" -> setTop(sender);
            case "removetop" -> removeTop(sender);
            case "save" -> save(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "music" -> music(sender, args);
            default -> Chat.send(sender, "&c未知子指令，输入 &f/tntbattle help &c查看帮助。");
        }
        return true;
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以加入游戏。");
            return;
        }
        manager.join(player);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以离开游戏。");
            return;
        }
        if (!manager.isPlaying(player)) {
            Chat.send(player, "&7你不在游戏中。");
            return;
        }
        manager.leave(player, false);
        Chat.send(player, "&7已离开 TNT 对战。");
    }

    private void list(CommandSender sender) {
        Chat.send(sender, "&c===== TNT 对战 =====");
        String state = manager.isRunning() ? "&c游戏中" : (manager.isEnding() ? "&c结算中" : "&e等待中");
        Chat.send(sender, "&7状态: " + state + " &7| 玩家: &f" + manager.statesCount());
        if (manager.isRunning()) {
            Chat.send(sender, "&c红队存活 &f" + manager.aliveCountTotal(TNTBattleArena.Team.RED)
                    + "&7/&f" + manager.teamSize(TNTBattleArena.Team.RED)
                    + " &8| &9蓝队存活 &f" + manager.aliveCountTotal(TNTBattleArena.Team.BLUE)
                    + "&7/&f" + manager.teamSize(TNTBattleArena.Team.BLUE));
        }
        Chat.send(sender, "&7区域: &f" + manager.arena().sizeText()
                + " &7| 红队出生点 &f" + manager.arena().spawns(TNTBattleArena.Team.RED).size()
                + " &7个 | 蓝队出生点 &f" + manager.arena().spawns(TNTBattleArena.Team.BLUE).size()
                + " &7个 | 虚空线: &fY " + manager.arena().voidY()
                + " &7| 建筑上限: " + (manager.buildHeightLimit() < 0 ? "&f无限制" : "&fY " + manager.buildHeightLimit()));
        if (!manager.arena().ready()) {
            Chat.send(sender, "&c场地尚未配置完整：/tntbattle pos1、pos2、setspawn/addspawn、setlobby、save");
        }
    }

    private void stats(CommandSender sender, String[] args) {
        String name;
        UUID id;
        if (args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            id = target.getUniqueId();
            name = target.getName() == null ? args[1] : target.getName();
        } else if (sender instanceof Player player) {
            id = player.getUniqueId();
            name = player.getName();
        } else {
            Chat.send(sender, "&c用法: /tntbattle stats <玩家>");
            return;
        }
        Chat.send(sender, "&c" + name + " &7的 TNT 对战胜场：&e" + manager.firsts(id));
    }

    private void top(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                Chat.send(sender, "&c数量必须是数字，例如 /tntbattle top 10");
                return;
            }
        }
        limit = Math.max(1, Math.min(50, limit));
        List<TNTBattleManager.WinEntry> entries = manager.leaderboard(limit);
        Chat.send(sender, "&c&lTNT 对战 胜场排行榜 &7(前 " + limit + " 名)");
        if (entries.isEmpty()) {
            Chat.send(sender, "&7暂无胜场记录。");
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            TNTBattleManager.WinEntry entry = entries.get(i);
            Chat.send(sender, "&e#" + (i + 1) + " &f" + entry.name()
                    + " &7- &c" + entry.wins() + " &7胜");
        }
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&cTNT 对战指令:");
        Chat.send(sender, "&f/tntbattle join &7- 加入等待队列");
        Chat.send(sender, "&f/tntbattle leave &7- 离开游戏");
        Chat.send(sender, "&f/tntbattle list &7- 查看状态与队伍");
        Chat.send(sender, "&f/tntbattle stats [玩家] &7- 查看胜场");
        Chat.send(sender, "&f/tntbattle top [数量] &7- 胜场排行榜（默认 10）");
        if (sender.hasPermission("tntbattle.admin")) {
            Chat.send(sender, "&6-- 管理 --");
            Chat.send(sender, "&f/tntbattle pos1 | pos2 &7- 站在两个对角设置 X/Z 范围（TNT 只破坏范围内方块，Y 覆盖世界全高）");
            Chat.send(sender, "&f/tntbattle setspawn red|blue &7- 设置该队出生点（替换原有）");
            Chat.send(sender, "&f/tntbattle addspawn red|blue &7- 给该队添加一个出生点");
            Chat.send(sender, "&f/tntbattle clearspawns red|blue &7- 清空该队出生点");
            Chat.send(sender, "&f/tntbattle setlobby &7- 设置等待点");
            Chat.send(sender, "&f/tntbattle setvoid <Y> &7- 设置虚空淘汰高度");
            Chat.send(sender, "&f/tntbattle setbuildheight [Y|off] &7- 建筑高度上限（默认取当前 Y；off 关闭）");
            Chat.send(sender, "&f/tntbattle settop &7- 在当前位置放置大厅悬浮排行榜");
            Chat.send(sender, "&f/tntbattle removetop &7- 移除大厅悬浮排行榜");
            Chat.send(sender, "&f/tntbattle save &7- 保存场地配置");
            Chat.send(sender, "&f/tntbattle start &7- 立即开始");
            Chat.send(sender, "&f/tntbattle stop &7- 强制结束本局");
            Chat.send(sender, "&f/tntbattle reload &7- 重新读取配置");
            Chat.send(sender, "&f/tntbattle music <pos1|pos2|record|stop|play|info|clear|reload> &7- 背景音乐录制/试听");
            Chat.send(sender, "&7  录制：站在红石音乐装置两个对角 &fmusic pos1/pos2 &7→ &fmusic record &7→ 启动红石机 → &fmusic stop");
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("tntbattle.admin")) {
            Chat.send(sender, "&c你没有权限执行该指令。");
            return false;
        }
        return true;
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以设置出生点。");
            return;
        }
        TNTBattleArena.Team team = teamArg(args, 1);
        if (team == null) {
            Chat.send(sender, "&c用法: /tntbattle setspawn red|blue（会清空该队原有出生点；要多个用 addspawn）");
            return;
        }
        manager.arena().setSpawn(team, player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已设置" + team.display() + "出生点（1 个，已保存）。要多个出生点请用 /tntbattle addspawn。");
    }

    private void addSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以添加出生点。");
            return;
        }
        TNTBattleArena.Team team = teamArg(args, 1);
        if (team == null) {
            Chat.send(sender, "&c用法: /tntbattle addspawn red|blue");
            return;
        }
        manager.arena().addSpawn(team, player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已给" + team.display() + "添加出生点，共 &f"
                + manager.arena().spawns(team).size() + " &a个（已保存）。");
    }

    private void clearSpawns(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        TNTBattleArena.Team team = teamArg(args, 1);
        if (team == null) {
            Chat.send(sender, "&c用法: /tntbattle clearspawns red|blue");
            return;
        }
        manager.arena().clearSpawns(team);
        manager.arena().save();
        Chat.send(sender, "&a已清空" + team.display() + "的出生点（已保存）。");
    }

    private TNTBattleArena.Team teamArg(String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        if (args[index].equalsIgnoreCase("red")) {
            return TNTBattleArena.Team.RED;
        }
        if (args[index].equalsIgnoreCase("blue")) {
            return TNTBattleArena.Team.BLUE;
        }
        return null;
    }

    private void setCorner(CommandSender sender, int index) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以设置区域角。");
            return;
        }
        manager.arena().setCorner(index, player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已设置区域角" + index + "：&f" + player.getWorld().getName() + " "
                + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockZ()
                + " &7（只取 X/Z，Y 自动覆盖整个世界高度）");
        if (manager.arena().regionConfigured()) {
            Chat.send(player, "&7区域大小: &f" + manager.arena().sizeText());
        }
    }

    private void setLobby(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以设置等待点。");
            return;
        }
        manager.arena().setLobby(player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已设置等待点（已保存）。");
    }

    private void setTop(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以放置排行榜。");
            return;
        }
        org.bukkit.Location at = player.getLocation().add(0, 1.5, 0);
        manager.setLeaderboardLocation(at);
        Chat.send(player, "&a已在这里放置大厅胜场排行榜并保存（&f" + at.getWorld().getName()
                + " " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ()
                + "&a）；胜场变化会自动刷新。");
    }

    private void removeTop(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        manager.removeLeaderboard();
        Chat.send(sender, "&a已移除大厅胜场排行榜（已保存）。");
    }

    private void setVoid(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        int y;
        if (args.length >= 2) {
            try {
                y = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                Chat.send(sender, "&c虚空高度必须是整数。");
                return;
            }
        } else if (sender instanceof Player player) {
            y = player.getLocation().getBlockY() - 10;
        } else {
            Chat.send(sender, "&c用法: /tntbattle setvoid <Y>");
            return;
        }
        manager.arena().setVoidY(y);
        Chat.send(sender, "&a已设置虚空淘汰高度：&fY " + y);
    }

    private void setBuildHeight(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        int y;
        if (args.length >= 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("none"))) {
            y = -1;
        } else if (args.length >= 2) {
            try {
                y = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                Chat.send(sender, "&c高度必须是整数，或写 off 关闭。");
                return;
            }
        } else if (sender instanceof Player player) {
            y = player.getLocation().getBlockY();
        } else {
            Chat.send(sender, "&c用法: /tntbattle setbuildheight [Y|off]");
            return;
        }
        plugin.getConfig().set("build-height-limit", y);
        plugin.saveConfig();
        Chat.send(sender, y < 0
                ? "&a已关闭建筑高度限制。"
                : "&a建筑高度上限已设为 &fY " + y + "&a（只能在这层及以下放羊毛）。");
    }

    private void save(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!manager.arena().ready()) {
            Chat.send(sender, "&c至少需要设置 pos1/pos2 区域，以及红蓝两队各至少一个出生点。");
            return;
        }
        manager.arena().save();
        Chat.send(sender, "&a已保存场地配置（红/蓝出生点、等待点、虚空线）。");
    }

    private void start(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!manager.startGame(true)) {
            Chat.send(sender, "&c无法开始：检查场地配置和等待玩家。");
        }
    }

    private void stop(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!manager.isRunning()) {
            Chat.send(sender, "&7当前没有进行中的对局。");
            return;
        }
        manager.endGame(null);
        Chat.send(sender, "&a已结束本局。");
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (manager.isRunning() || manager.isEnding()) {
            Chat.send(sender, "&c对局进行中，不能重载配置。");
            return;
        }
        plugin.reloadConfig();
        manager.reload();
        Chat.send(sender, "&a配置已重新载入。");
    }

    private void music(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        TNTBattleMusic music = manager.music();
        if (args.length < 2) {
            Chat.send(sender, "&6/tntbattle music <pos1|pos2|record|stop|play|info|clear|reload>");
            Chat.send(sender, "&7录制：站在红石音乐装置两个对角执行 &fmusic pos1&7 / &fmusic pos2&7，"
                    + "再 &fmusic record&7，启动红石机，最后 &fmusic stop");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c只有玩家可以设置录制范围。");
                    return;
                }
                music.setCorner(args[1].equalsIgnoreCase("pos1") ? 1 : 2, player.getLocation(), sender);
            }
            case "record" -> music.startRecording(sender);
            case "stop" -> music.finishRecording(sender, false);
            case "play" -> {
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c只有玩家可以试听。");
                    return;
                }
                music.playTest(player);
            }
            case "info" -> music.info(sender);
            case "clear" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    Chat.send(sender, "&c这会删除已录制的全部音符。确认执行：&f/tntbattle music clear confirm");
                    return;
                }
                music.clear(sender);
            }
            case "reload" -> {
                music.load();
                Chat.send(sender, "&a背景音乐已从 music.yml 重新载入。");
            }
            default -> Chat.send(sender, "&c用法：/tntbattle music <pos1|pos2|record|stop|play|info|clear|reload>");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (isAdminSub(sub) && !sender.hasPermission("tntbattle.admin")) {
                    continue;
                }
                options.add(sub);
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("stats")) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filter(names, args[1]);
            }
            if (sub.equals("top")) {
                return filter(List.of("5", "10", "20", "50"), args[1]);
            }
            if (sub.equals("setspawn") || sub.equals("addspawn") || sub.equals("clearspawns")) {
                return filter(List.of("red", "blue"), args[1]);
            }
            if (sub.equals("setvoid")) {
                return filter(List.of("-64", "-32", "0"), args[1]);
            }
            if (sub.equals("music")) {
                return filter(List.of("pos1", "pos2", "record", "stop", "play", "info", "clear", "reload"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("music")
                && args[1].equalsIgnoreCase("clear")) {
            return filter(List.of("confirm"), args[2]);
        }
        return List.of();
    }

    private boolean isAdminSub(String sub) {
        return switch (sub) {
            case "pos1", "pos2", "setspawn", "addspawn", "clearspawns",
                    "setlobby", "setvoid", "setbuildheight", "settop", "removetop",
                    "save", "start", "stop", "reload", "music" -> true;
            default -> false;
        };
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
