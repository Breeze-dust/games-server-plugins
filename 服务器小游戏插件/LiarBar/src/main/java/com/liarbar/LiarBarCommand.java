package com.liarbar;

import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /liarbar（别名 /lb、/liars）。 */
public class LiarBarCommand implements CommandExecutor, TabCompleter {

    private final LiarBarPlugin plugin;
    private final LiarBarManager manager;

    public LiarBarCommand(LiarBarPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getManager();
    }

    private boolean isAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.isOp() || player.hasPermission("liarbar.admin");
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
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c该命令需要玩家执行。");
                    return true;
                }
                manager.enter(player, args.length >= 2 ? args[1] : null);
            }
            case "leave", "quit" -> {
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c该命令需要玩家执行。");
                    return true;
                }
                manager.leave(player, false);
            }
            case "list", "tables" -> list(sender);
            case "help" -> help(sender);
            case "pos1", "pos2" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c该命令需要玩家执行。");
                    return true;
                }
                manager.setCorner(player, sub.equals("pos1") ? 1 : 2);
                Chat.send(sender, "&a已记录房间对角点 " + sub + "。");
            }
            case "setspawn" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c该命令需要玩家执行。");
                    return true;
                }
                manager.setSpawn(player);
                Chat.send(sender, "&a已把当前位置设为酒馆等待点。");
            }
            case "addchair" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    Chat.send(sender, "&c该命令需要玩家执行。");
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/liarbar addchair <桌名>（站在台阶上执行）");
                    return true;
                }
                manager.addChair(player, args[1]);
            }
            case "deltable" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/liarbar deltable <桌名>");
                    return true;
                }
                if (manager.deleteTable(args[1])) {
                    Chat.send(sender, "&a已删除桌子「" + args[1] + "」。");
                } else {
                    Chat.send(sender, "&c没有叫「" + args[1] + "」的桌子。");
                }
            }
            case "start" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/liarbar start <桌名>");
                    return true;
                }
                LiarBarTable table = manager.tables().get(args[1]);
                if (table == null) {
                    Chat.send(sender, "&c没有叫「" + args[1] + "」的桌子。");
                    return true;
                }
                if (table.getState() != LiarBarTable.State.WAITING) {
                    Chat.send(sender, "&c这张桌子正在对局/倒计时中。");
                    return true;
                }
                if (table.occupiedCount() < manager.settings().playersPerTable) {
                    Chat.send(sender, "&c「" + args[1] + "」需要 "
                            + manager.settings().playersPerTable + " 人全部入座才能开局（现在 "
                            + table.occupiedCount() + " 人）。");
                    return true;
                }
                table.forceStart();
                Chat.send(sender, "&a已强制开始「" + args[1] + "」。");
            }
            case "stop" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/liarbar stop <桌名>");
                    return true;
                }
                LiarBarTable table = manager.tables().get(args[1]);
                if (table == null) {
                    Chat.send(sender, "&c没有叫「" + args[1] + "」的桌子。");
                    return true;
                }
                table.forceStop();
                Chat.send(sender, "&a已中止「" + args[1] + "」的牌局，所有人已从座椅下来。");
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (manager.reload()) {
                    Chat.send(sender, "&a骗子酒馆配置已重载：" + manager.tables().size() + " 张桌子。");
                } else {
                    Chat.send(sender, "&c有牌局正在进行，无法重载；先 /liarbar stop <桌名>。");
                }
            }
            default -> {
                Chat.send(sender, "&c未知子命令，/liarbar help 查看用法。");
            }
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (!isAdmin(sender)) {
            Chat.send(sender, "&c你没有权限（需要 liarbar.admin）。");
            return false;
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (manager.tables().isEmpty()) {
            Chat.send(sender, "&7还没有桌子。OP 站在台阶上执行 &e/liarbar addchair <桌名>&7 登记椅子。");
            return;
        }
        Chat.send(sender, "&e骗子酒馆的桌子：");
        for (LiarBarTable table : manager.tables().values()) {
            String state = switch (table.getState()) {
                case WAITING -> "&a等待中";
                case STARTING -> "&e倒计时";
                case PLAYING -> "&c对局中";
                case ENDING -> "&6结算中";
            };
            Chat.send(sender, "&f- &e" + table.getName() + " &f[" + state + "&f] &7椅子 "
                    + table.seatCount() + "/" + manager.settings().playersPerTable
                    + "，已坐 " + table.occupiedCount() + " 人");
        }
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&6骗子酒馆 &7—— 大厅多桌牌局");
        Chat.send(sender, "&e/liarbar join [桌名] &7进入酒馆（可指定桌子站到空位上）");
        Chat.send(sender, "&e/liarbar leave &7离开酒馆");
        Chat.send(sender, "&e/liarbar list &7查看所有桌子状态");
        if (isAdmin(sender)) {
            Chat.send(sender, "&8管理：");
            Chat.send(sender, "&e/liarbar pos1 &e/liarbar pos2 &7房间两个对角（保护区域）");
            Chat.send(sender, "&e/liarbar setspawn &7酒馆等待点（进酒馆的落点）");
            Chat.send(sender, "&e/liarbar addchair <桌名> &7站在台阶上登记一把椅子（每桌 4 把）");
            Chat.send(sender, "&e/liarbar deltable <桌名> &7删除桌子");
            Chat.send(sender, "&e/liarbar start <桌名> &e/liarbar stop <桌名> &7强制开场/中止");
            Chat.send(sender, "&e/liarbar reload &7重载配置");
        }
        Chat.send(sender, "&7玩法：右键台阶椅子坐下即准备，四人坐满自动开局；左键选牌、右键出牌、右键钟质疑。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "list", "help"));
            if (isAdmin(sender)) {
                subs.addAll(List.of("pos1", "pos2", "setspawn", "addchair", "deltable",
                        "start", "stop", "reload"));
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("addchair") || sub.equals("deltable")
                    || sub.equals("start") || sub.equals("stop")) {
                return filter(new ArrayList<>(manager.tables().keySet()), args[1]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
