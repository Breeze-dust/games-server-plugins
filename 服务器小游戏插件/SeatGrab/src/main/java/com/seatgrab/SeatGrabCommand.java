package com.seatgrab;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /seatgrab 命令。 */
public final class SeatGrabCommand implements CommandExecutor, TabCompleter {

    private final SeatGrabPlugin plugin;
    private final SeatGrabManager manager;

    public SeatGrabCommand(SeatGrabPlugin plugin, SeatGrabManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                Player player = playerOrNull(sender);
                if (player != null) {
                    manager.join(player);
                }
            }
            case "leave" -> {
                Player player = playerOrNull(sender);
                if (player != null) {
                    manager.leave(player, false);
                }
            }
            case "list" -> {
                Chat.send(sender, "&e" + SeatGrabManager.GAME + " &7| &f" + manager.statusText());
                SeatGrabArena arena = manager.arena();
                Chat.send(sender, "&7场地：" + arena.sizeText() + "，等待点 "
                        + (arena.lobby() == null ? "&c未设置" : "&a已设置")
                        + " &7，出生点 " + arena.spawns().size() + " 个");
            }
            case "help", "?" -> help(sender);
            case "pos1" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.arena().setCorner(1, player.getLocation());
                    Chat.send(sender, "&a已记录 pos1（地面 Y " + player.getLocation().getBlockY() + "）。记得 /seatgrab save。");
                }
            }
            case "pos2" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.arena().setCorner(2, player.getLocation());
                    Chat.send(sender, "&a已记录 pos2。记得 /seatgrab save。");
                }
            }
            case "setlobby" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.arena().setLobby(player.getLocation());
                    Chat.send(sender, "&a已记录等待点/淘汰旁观点。记得 /seatgrab save。");
                }
            }
            case "setspawn" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.arena().setSpawn(player.getLocation());
                    Chat.send(sender, "&a已把当前站位设为唯一出生点。记得 /seatgrab save。");
                }
            }
            case "addspawn" -> {
                Player player = adminPlayer(sender);
                if (player != null) {
                    manager.arena().addSpawn(player.getLocation());
                    Chat.send(sender, "&a已添加出生点，共 " + manager.arena().spawns().size() + " 个。记得 /seatgrab save。");
                }
            }
            case "clearspawns" -> {
                if (admin(sender)) {
                    manager.arena().clearSpawns();
                    Chat.send(sender, "&a已清空出生点（不配置时会在场地内随机找落脚点）。记得 /seatgrab save。");
                }
            }
            case "save" -> {
                if (admin(sender)) {
                    manager.arena().save();
                    Chat.send(sender, "&a已保存。场地" + (manager.arena().ready() ? "&a可开局" : "&c还缺 " + manager.arena().missing()));
                }
            }
            case "start" -> {
                if (admin(sender)) {
                    manager.startGame(true);
                    Chat.send(sender, "&e已请求开始本局。");
                }
            }
            case "stop" -> {
                if (admin(sender)) {
                    manager.abortGame();
                    Chat.send(sender, "&e已停止本局。");
                }
            }
            case "reload" -> {
                if (admin(sender)) {
                    plugin.reloadConfig();
                    manager.reload();
                    Chat.send(sender, "&a配置已重新载入。");
                }
            }
            case "frames" -> {
                if (admin(sender)) {
                    Chat.send(sender, "&e" + manager.framesText());
                }
            }
            case "debug" -> {
                if (admin(sender)) {
                    for (String line : manager.debugLines()) {
                        Chat.send(sender, "&7" + line);
                    }
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&e&l抢位占座 &7命令");
        Chat.send(sender, "&f/seatgrab join &7- 加入等待队列");
        Chat.send(sender, "&f/seatgrab leave &7- 离开");
        Chat.send(sender, "&f/seatgrab list &7- 查看状态");
        if (sender.hasPermission("seatgrab.admin")) {
            Chat.send(sender, "&f/seatgrab pos1|pos2 &7- 站到场地两个对角设置区域（只取 XZ，Y 自动延伸）");
            Chat.send(sender, "&f/seatgrab setlobby &7- 等待点 / 淘汰旁观点");
            Chat.send(sender, "&f/seatgrab setspawn|addspawn|clearspawns &7- 出生点（不配就随机）");
            Chat.send(sender, "&f/seatgrab save &7- 保存场地");
            Chat.send(sender, "&f/seatgrab frames &7- 检测场地内展示框数量");
            Chat.send(sender, "&f/seatgrab start|stop|reload|debug");
        }
    }

    private Player playerOrNull(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.send(sender, "&c这条命令只能由玩家执行。");
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("seatgrab.admin")) {
            return true;
        }
        Chat.send(sender, "&c你没有权限。");
        return false;
    }

    private Player adminPlayer(CommandSender sender) {
        if (!admin(sender)) {
            return null;
        }
        return playerOrNull(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return new ArrayList<>();
        }
        List<String> options = new ArrayList<>(List.of("join", "leave", "list", "help"));
        if (sender.hasPermission("seatgrab.admin")) {
            options.addAll(List.of("pos1", "pos2", "setlobby", "setspawn", "addspawn",
                    "clearspawns", "save", "start", "stop", "reload", "frames", "debug"));
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
