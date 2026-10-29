package com.quark;

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

/** /quark 命令。 */
public final class QuarkCommand implements CommandExecutor, TabCompleter {

    private final QuarkPlugin plugin;
    private final QuarkManager manager;

    public QuarkCommand(QuarkPlugin plugin, QuarkManager manager) {
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
            case "start" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (manager.isRunning()) {
                    Chat.send(sender, "&e对局已经在进行中。");
                    return true;
                }
                if (manager.arena().isReady()) {
                    manager.startGame(true);
                } else {
                    Chat.send(sender, "&c场地未配置完整（缺 " + manager.arena().missing() + "）。");
                }
            }
            case "stop" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (!manager.isRunning()) {
                    Chat.send(sender, "&e当前没有进行中的对局。");
                    return true;
                }
                manager.endGame("管理员结束");
            }
            case "setpos1" -> setCorner(sender, true);
            case "setpos2" -> setCorner(sender, false);
            case "setlobby" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player player = player(sender);
                if (player == null) {
                    return true;
                }
                manager.arena().setLobby(player.getLocation());
                Chat.send(sender, "&a等待点已设置为 " + describe(player.getLocation()) + "，记得 /quark save。");
            }
            case "addspawn" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player player = player(sender);
                if (player == null) {
                    return true;
                }
                manager.arena().addSpawn(player.getLocation());
                Chat.send(sender, "&a已添加出生点 " + describe(player.getLocation())
                        + "（共 " + manager.arena().getSpawns().size() + " 个），记得 /quark save。");
            }
            case "clearspawns" -> {
                if (!admin(sender)) {
                    return true;
                }
                manager.arena().clearSpawns();
                Chat.send(sender, "&a已清空出生点，记得 /quark save。");
            }
            case "save" -> {
                if (!admin(sender)) {
                    return true;
                }
                manager.arena().save();
                Chat.send(sender, "&a场地已写入 config.yml。");
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (manager.isRunning()) {
                    Chat.send(sender, "&c对局进行中不能重载配置。");
                    return true;
                }
                plugin.reloadConfig();
                manager.reload();
                Chat.send(sender, "&a配置已重载。场地状态："
                        + (manager.arena().isReady() ? "&a已配置" : "&c缺 " + manager.arena().missing()));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&b&l粒子枪战 &7(Quark)");
        Chat.send(sender, "&b/quark join &7- 加入等待队列");
        Chat.send(sender, "&b/quark leave &7- 离开对局");
        Chat.send(sender, "&b/quark list &7- 查看对局状态");
        if (adminOnly(sender)) {
            Chat.send(sender, "&b/quark start &7- 立即开始（至少 1 人）");
            Chat.send(sender, "&b/quark stop &7- 结束当前对局");
            Chat.send(sender, "&b/quark setpos1 &7/ &b/quark setpos2 &7- 设置场地 X/Z 对角");
            Chat.send(sender, "&b/quark setlobby &7- 设置等待点");
            Chat.send(sender, "&b/quark addspawn &7- 添加出生点（可多次）");
            Chat.send(sender, "&b/quark clearspawns &7- 清空出生点");
            Chat.send(sender, "&b/quark save &7- 保存场地到 config.yml");
            Chat.send(sender, "&b/quark reload &7- 重载 config.yml");
        }
    }

    private void list(CommandSender sender) {
        Chat.send(sender, "&b[" + QuarkManager.GAME_NAME + "] &f状态："
                + (manager.isRunning() ? "&a对局中 &7(剩余 " + manager.secondsLeft() + "s)" : "&e等待中"));
        Chat.send(sender, "&7场地：" + (manager.arena().isReady()
                ? "&a已配置 &7(出生点 " + manager.arena().getSpawns().size() + " 个)"
                : "&c缺 " + manager.arena().missing()));
    }

    private void setCorner(CommandSender sender, boolean first) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        Location loc = player.getLocation();
        if (first) {
            manager.arena().setPos1(loc);
        } else {
            manager.arena().setPos2(loc);
        }
        Chat.send(sender, "&a已设置 " + (first ? "pos1" : "pos2") + " 为 " + describe(loc)
                + "，记得 /quark save。");
    }

    private static String describe(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.send(sender, "&c这个命令只能由玩家执行。");
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (sender.isOp() || sender.hasPermission("quark.admin")) {
            return true;
        }
        Chat.send(sender, "&c你没有权限执行这个命令。");
        return false;
    }

    private boolean adminOnly(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("quark.admin");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("join", "leave", "list", "help"));
        if (adminOnly(sender)) {
            options.addAll(List.of("start", "stop", "setpos1", "setpos2", "setlobby",
                    "addspawn", "clearspawns", "save", "reload"));
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
