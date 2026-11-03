package com.redlight;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /redlight 命令（别名 /rl、/woodenman）。 */
public final class RedLightCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "leave", "list", "help",
            "pos1", "pos2", "setspawn", "addspawn", "clearspawns",
            "setfinish", "setlobby", "setground", "setcountdown",
            "save", "start", "stop", "reload", "debug");

    private final RedLightPlugin plugin;
    private final RedLightManager manager;

    public RedLightCommand(RedLightPlugin plugin, RedLightManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                Player player = player(sender);
                if (player != null) {
                    manager.join(player);
                }
            }
            case "leave" -> {
                Player player = player(sender);
                if (player != null) {
                    manager.leave(player, false);
                }
            }
            case "list" -> list(sender);
            case "pos1" -> setCorner(sender, 1);
            case "pos2" -> setCorner(sender, 2);
            case "setspawn" -> setSpawn(sender);
            case "addspawn" -> addSpawn(sender);
            case "clearspawns" -> clearSpawns(sender);
            case "setfinish" -> setFinish(sender);
            case "setlobby" -> setLobby(sender);
            case "setground" -> setGround(sender, args);
            case "setcountdown" -> setCountdown(sender, args);
            case "save" -> save(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            default -> Chat.send(sender, "&c未知子指令，输入 &f/redlight help &c查看帮助。");
        }
        return true;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.send(sender, "&c只有玩家可以执行这个指令。");
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (!sender.hasPermission("redlight.admin")) {
            Chat.send(sender, "&c你没有权限执行该指令。");
            return false;
        }
        return true;
    }

    private void list(CommandSender sender) {
        RedLightArena arena = manager.arena();
        Chat.send(sender, "&6===== " + RedLightManager.GAME + " =====");
        Chat.send(sender, "&7状态: " + switch (manager.state()) {
            case WAITING -> "&e等待中";
            case RUNNING -> "&c游戏中";
            case ENDING -> "&c结算中";
        } + " &7| &f" + manager.statusText());
        Chat.send(sender, "&7场地: &f" + arena.sizeText()
                + " &7| 等待点 " + (arena.lobby() == null ? "&c未设置" : "&a已设置")
                + " &7| 终点线 " + (arena.finish() == null ? "&c未设置"
                : "&a" + arena.finish().getBlockX() + "," + arena.finish().getBlockY()
                + "," + arena.finish().getBlockZ())
                + " &7| 出生点 &f" + arena.spawns().size() + " &7个");
        Chat.send(sender, "&7木头人: 倒数 &f" + manager.countdownSeconds() + "s"
                + " &7| 前几名结束 &f" + manager.finishSlots());
        if (arena.ready()) {
            Chat.send(sender, "&a可开局。");
        } else {
            Chat.send(sender, "&c未配置完整：缺 " + arena.missing()
                    + "（/redlight pos1、pos2、addspawn、setfinish、setlobby、save）");
        }
    }

    private void setCorner(CommandSender sender, int index) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        manager.arena().setCorner(index, player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已设置区域角" + index + "：&f" + player.getWorld().getName() + " "
                + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockZ()
                + " &7（地面 Y " + manager.arena().groundY() + "）");
        if (manager.arena().regionConfigured()) {
            Chat.send(player, "&7区域大小: &f" + manager.arena().sizeText());
        }
    }

    private void setSpawn(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        manager.arena().setSpawn(player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已把当前位置设为唯一起跑点（已保存）。要多个起跑点请用 /redlight addspawn。");
    }

    private void addSpawn(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        manager.arena().addSpawn(player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已添加起跑点，共 &f" + manager.arena().spawns().size() + " &a个（已保存）。");
    }

    private void clearSpawns(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        manager.arena().clearSpawns();
        manager.arena().save();
        Chat.send(sender, "&a已清空全部起跑点（已保存）。");
    }

    private void setFinish(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        manager.arena().setFinish(player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已把当前位置设为终点线（已保存）：&f"
                + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY()
                + "," + player.getLocation().getBlockZ());
        Chat.send(player, "&7终点线方向由「出生点中心 → 终点线」自动判断；请确保出生点和终点线在场地两边。");
    }

    private void setLobby(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        manager.arena().setLobby(player.getLocation());
        manager.arena().save();
        Chat.send(player, "&a已设置等待点 / 淘汰旁观点（已保存）。");
    }

    private void setGround(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        int y;
        if (args.length >= 2) {
            try {
                y = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                Chat.send(sender, "&c地面高度必须是整数。");
                return;
            }
        } else if (sender instanceof Player player) {
            y = player.getLocation().getBlockY();
        } else {
            Chat.send(sender, "&c用法: /redlight setground <Y>");
            return;
        }
        manager.arena().setGround(y);
        manager.arena().save();
        Chat.send(sender, "&a地面高度已设为 &fY " + y + "&a（玩家站立时的脚部高度）。");
    }

    private void setCountdown(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法: /redlight setcountdown <秒>（当前 " + manager.countdownSeconds() + "）");
            return;
        }
        int seconds;
        try {
            seconds = Math.max(1, Math.min(10, Integer.parseInt(args[1])));
        } catch (NumberFormatException exception) {
            Chat.send(sender, "&c秒数必须是整数。");
            return;
        }
        plugin.getConfig().set("check.countdown-seconds", seconds);
        plugin.saveConfig();
        Chat.send(sender, "&a木头人回头前的 title 倒数已设为 &f" + seconds + " &a秒（对下一局生效）。");
    }

    private void save(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        if (!manager.arena().ready()) {
            Chat.send(sender, "&c还缺 " + manager.arena().missing() + "，无法保存为可开局场地。");
            return;
        }
        manager.arena().save();
        Chat.send(sender, "&a场地已保存。");
    }

    private void start(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        if (manager.state() != RedLightManager.State.WAITING) {
            Chat.send(sender, "&e对局已经在进行中。");
        } else if (!manager.arena().ready()) {
            Chat.send(sender, "&c场地未配置完整（缺 " + manager.arena().missing() + "）。");
        } else {
            manager.startGame(true);
        }
    }

    private void stop(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        if (manager.state() == RedLightManager.State.WAITING) {
            Chat.send(sender, "&7当前没有进行中的对局。");
            return;
        }
        manager.abortGame();
        Chat.send(sender, "&a已结束本局。");
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        if (manager.state() != RedLightManager.State.WAITING) {
            Chat.send(sender, "&c对局进行中，不能重载配置。");
            return;
        }
        plugin.reloadConfig();
        manager.reload();
        Chat.send(sender, "&a配置已重新载入。");
    }

    private void debug(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        for (String line : manager.debugLines()) {
            Chat.send(sender, "&7" + line);
        }
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&6" + RedLightManager.GAME + " 指令:");
        Chat.send(sender, "&f/redlight join &7- 加入等待队列");
        Chat.send(sender, "&f/redlight leave &7- 离开游戏");
        Chat.send(sender, "&f/redlight list &7- 查看场地与状态");
        if (sender.hasPermission("redlight.admin")) {
            Chat.send(sender, "&6-- 管理 --");
            Chat.send(sender, "&f/redlight pos1 | pos2 &7- 站在地面两个对角设置区域（只取 XZ）");
            Chat.send(sender, "&f/redlight setspawn / addspawn / clearspawns &7- 起跑出生点");
            Chat.send(sender, "&f/redlight setfinish &7- 站在终点线上设置终点线");
            Chat.send(sender, "&f/redlight setlobby &7- 设置等待点 / 淘汰旁观点");
            Chat.send(sender, "&f/redlight setground [Y] &7- 设置地面高度（默认当前位置）");
            Chat.send(sender, "&f/redlight setcountdown <秒> &7- 木头人回头前 title 倒数（默认 3）");
            Chat.send(sender, "&f/redlight save &7- 保存场地");
            Chat.send(sender, "&f/redlight start | stop &7- 开始 / 强制结束（管理员可 1 人测试）");
            Chat.send(sender, "&f/redlight reload &7- 重新读取配置");
            Chat.send(sender, "&f/redlight debug &7- 打印阶段 / 终点线方向 / 检查参数");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (isAdmin(sub) && !sender.hasPermission("redlight.admin")) {
                    continue;
                }
                options.add(sub);
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("setcountdown")) {
                return filter(List.of("2", "3", "5"), args[1]);
            }
            if (sub.equals("setground")) {
                return filter(List.of("60", "64", "70", "100"), args[1]);
            }
        }
        return List.of();
    }

    private boolean isAdmin(String sub) {
        return switch (sub) {
            case "pos1", "pos2", "setspawn", "addspawn", "clearspawns",
                    "setfinish", "setlobby", "setground", "setcountdown",
                    "save", "start", "stop", "reload", "debug" -> true;
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
