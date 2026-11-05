package com.bombardmentzone;

import com.hub.util.Chat;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /bombard 命令（别名 /bz、/bombardzone）。 */
public final class BombardmentZoneCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "leave", "list", "help",
            "pos1", "pos2", "setspawn", "addspawn", "clearspawns",
            "setlobby", "setground",
            "addcannon", "clearcannons", "cannons", "scan",
            "setlate", "save", "start", "stop", "reload", "debug");

    private final BombardmentZonePlugin plugin;
    private final BombardmentZoneManager manager;

    public BombardmentZoneCommand(BombardmentZonePlugin plugin, BombardmentZoneManager manager) {
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
            case "setlobby" -> setLobby(sender);
            case "setground" -> setGround(sender, args);
            case "addcannon" -> addCannon(sender);
            case "clearcannons" -> clearCannons(sender);
            case "cannons" -> cannons(sender);
            case "scan" -> scan(sender);
            case "setlate" -> setLate(sender, args);
            case "save" -> save(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            default -> Chat.send(sender, "&c未知子指令，输入 &f/bombard help &c查看帮助。");
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
        if (!sender.hasPermission("bombard.admin")) {
            Chat.send(sender, "&c你没有权限执行该指令。");
            return false;
        }
        return true;
    }

    private void list(CommandSender sender) {
        BombardmentZoneArena arena = manager.arena();
        Chat.send(sender, "&6===== " + BombardmentZoneManager.GAME + " =====");
        Chat.send(sender, "&7状态: " + switch (manager.state()) {
            case WAITING -> "&e等待中";
            case RUNNING -> "&c游戏中";
            case ENDING -> "&c结算中";
        } + " &7| 存活 &f" + manager.aliveCount()
                + " &7| 剩余 &f" + manager.roundText()
                + " &7| 阶段 " + (manager.isLatePhase() ? "&c饱和轰炸" : "&e零星炮火")
                + " &7| 炮弹 &f" + manager.shellsInFlight());
        Chat.send(sender, "&7场地: &f" + arena.sizeText()
                + " &7| 等待点 " + (arena.lobby() == null ? "&c未设置" : "&a已设置")
                + " &7| 出生点 &f" + arena.spawns().size() + " &7个"
                + " &7| 炮口 &f" + arena.cannons().size() + " &7个");
        if (arena.ready()) {
            Chat.send(sender, "&a可开局。");
        } else {
            Chat.send(sender, "&c未配置完整：缺 " + arena.missing()
                    + "（/bombard pos1、pos2、addspawn、setlobby、addcannon、save）");
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
        Chat.send(player, "&a已把当前位置设为唯一起点（已保存）。要多个起点请用 /bombard addspawn。");
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
        Chat.send(player, "&a已添加出生点，共 &f" + manager.arena().spawns().size() + " &a个（已保存）。");
    }

    private void clearSpawns(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        manager.arena().clearSpawns();
        manager.arena().save();
        Chat.send(sender, "&a已清空全部出生点（已保存）。");
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
        Chat.send(player, "&a已设置等待点（已保存）。");
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
            Chat.send(sender, "&c用法: /bombard setground <Y>");
            return;
        }
        manager.arena().setGround(y);
        manager.arena().save();
        Chat.send(sender, "&a地面高度已设为 &fY " + y + "&a（仅用于场地中心/观战点；落点会自动扫描整列高度）。");
    }

    private void addCannon(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        Location muzzle = player.getEyeLocation();
        manager.arena().addCannon(muzzle);
        manager.arena().save();
        Chat.send(player, "&a已记录炮口 #" + manager.arena().cannons().size() + "：&f"
                + muzzle.getWorld().getName() + " "
                + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", muzzle.getX(), muzzle.getY(), muzzle.getZ())
                + " &7（取你眼睛所在的位置，已保存）");
    }

    private void clearCannons(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        manager.arena().clearCannons();
        manager.arena().save();
        Chat.send(sender, "&a已清空全部炮口（已保存）。");
    }

    private void cannons(CommandSender sender) {
        List<Location> cannons = manager.arena().cannons();
        Chat.send(sender, "&6炮口列表（&f" + cannons.size() + "&6 个）：");
        int index = 1;
        for (Location cannon : cannons) {
            Chat.send(sender, "&7#" + index++ + " &f" + cannon.getWorld().getName() + " "
                    + String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
                    cannon.getX(), cannon.getY(), cannon.getZ()));
        }
    }

    private void scan(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        if (!manager.arena().regionConfigured()) {
            Chat.send(sender, "&c请先设置 pos1 / pos2。");
            return;
        }
        manager.scanLanding();
        Chat.send(sender, "&a已扫描落点：&f" + manager.landingCount() + " &a个"
                + "（落点方块见 config.yml 的 landing.materials）");
        if (manager.landingCount() == 0) {
            Chat.send(sender, "&c没找到落点：检查 landing.materials 是否包含岛面方块"
                    + "（水下或被水/其它方块盖住的地面不算落点）。");
        }
    }

    private void setLate(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法: /bombard setlate <秒>（当前 " + manager.lateStartSeconds() + " 秒）");
            return;
        }
        int seconds;
        try {
            seconds = Math.max(1, Integer.parseInt(args[1]));
        } catch (NumberFormatException exception) {
            Chat.send(sender, "&c秒数必须是整数。");
            return;
        }
        plugin.getConfig().set("phases.late-start-seconds", seconds);
        plugin.saveConfig();
        Chat.send(sender, "&a已设置开局 &f" + seconds + " &a秒后进入饱和轰炸阶段（对下一局生效）。");
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
        if (manager.state() != BombardmentZoneManager.State.WAITING) {
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
        if (manager.state() == BombardmentZoneManager.State.WAITING) {
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
        if (manager.state() != BombardmentZoneManager.State.WAITING) {
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
        Chat.send(sender, "&6" + BombardmentZoneManager.GAME + " 指令:");
        Chat.send(sender, "&f/bombard join &7- 加入等待队列");
        Chat.send(sender, "&f/bombard leave &7- 离开游戏");
        Chat.send(sender, "&f/bombard list &7- 查看场地与状态");
        if (sender.hasPermission("bombard.admin")) {
            Chat.send(sender, "&6-- 管理 --");
            Chat.send(sender, "&f/bombard pos1 | pos2 &7- 站在小岛两个对角设置区域（记 XZ 与地面 Y）");
            Chat.send(sender, "&f/bombard setspawn / addspawn / clearspawns &7- 固定出生点");
            Chat.send(sender, "&f/bombard setlobby &7- 设置等待点");
            Chat.send(sender, "&f/bombard setground [Y] &7- 设置地面高度（仅用于中心/观战点，落点自动全高扫描）");
            Chat.send(sender, "&f/bombard addcannon &7- 把眼睛位置记录为一个炮口");
            Chat.send(sender, "&f/bombard cannons / clearcannons &7- 查看 / 清空炮口");
            Chat.send(sender, "&f/bombard scan &7- 扫描并统计可落点方块数");
            Chat.send(sender, "&f/bombard setlate <秒> &7- 开局多少秒后进入饱和轰炸（默认 90）");
            Chat.send(sender, "&f/bombard save &7- 保存场地");
            Chat.send(sender, "&f/bombard start | stop &7- 开始 / 强制结束（管理员可 1 人测试）");
            Chat.send(sender, "&f/bombard reload &7- 重新读取配置");
            Chat.send(sender, "&f/bombard debug &7- 打印炮弹/阶段/落点状态");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (isAdmin(sub) && !sender.hasPermission("bombard.admin")) {
                    continue;
                }
                options.add(sub);
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("setlate")) {
                return filter(List.of("60", "90", "120", "150"), args[1]);
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
                    "setlobby", "setground", "addcannon", "clearcannons", "cannons", "scan",
                    "setlate", "save", "start", "stop", "reload", "debug" -> true;
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
