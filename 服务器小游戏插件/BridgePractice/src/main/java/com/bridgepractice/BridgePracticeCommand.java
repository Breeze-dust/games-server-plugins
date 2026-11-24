package com.bridgepractice;

import com.hub.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public final class BridgePracticeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS = List.of(
            "join", "leave", "reset", "lock", "breakblocks", "list", "top", "stats", "help");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
            "create", "setstart", "setspawn", "setend", "setvoid", "delete", "icon", "clear", "reload");

    private final BridgePracticeManager manager;
    private final CourseMenu menu;

    public BridgePracticeCommand(BridgePracticePlugin plugin, BridgePracticeManager manager, CourseMenu menu) {
        this.manager = manager;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (manager.isPracticing(player)) {
                    Chat.send(player, "&e你已经在练习中，先 &f/bridge leave &e离开再换房间。");
                } else {
                    menu.open(player);
                }
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "reset" -> reset(sender);
            case "lock" -> lock(sender);
            case "breakblocks" -> breakblocks(sender);
            case "list" -> list(sender);
            case "top" -> top(sender, args);
            case "stats" -> stats(sender, args);
            case "help" -> help(sender);
            case "create" -> create(sender, args);
            case "setstart" -> setstart(sender, args);
            case "setspawn" -> setspawn(sender, args);
            case "setend" -> setend(sender, args);
            case "setvoid" -> setvoid(sender, args);
            case "delete" -> delete(sender, args);
            case "icon" -> icon(sender, args);
            case "clear" -> clear(sender, args);
            case "reload" -> reload(sender);
            default -> Chat.send(sender, "&c未知子指令，输入 &f/bridge help &c查看帮助。");
        }
        return true;
    }

    // ---------- 玩家指令 ----------

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以加入练习。");
            return;
        }
        if (args.length < 2) {
            if (manager.isPracticing(player)) {
                Chat.send(player, "&e你已经在练习中，先 &f/bridge leave &e离开再换房间。");
                return;
            }
            menu.open(player);
            return;
        }
        manager.tryJoin(player, args[1]);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以离开练习。");
            return;
        }
        if (!manager.leave(player)) {
            Chat.send(player, "&7你不在练习中。");
        }
    }

    private void reset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以回到起点。");
            return;
        }
        if (!manager.isPracticing(player)) {
            Chat.send(player, "&7你不在练习中。");
            return;
        }
        manager.resetToStart(player);
        Chat.send(player, "&a已回到出生点，本次搭的方块已清掉。");
    }

    private void lock(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以切换房间加入状态。");
            return;
        }
        if (!manager.isPracticing(player)) {
            Chat.send(player, "&7你不在练习中。");
            return;
        }
        manager.toggleJoin(player);
    }

    private void breakblocks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以切换挖方块设置。");
            return;
        }
        if (!manager.isPracticing(player)) {
            Chat.send(player, "&7你不在练习中。");
            return;
        }
        manager.toggleBreakOthers(player);
    }

    private void list(CommandSender sender) {
        Chat.send(sender, "&6===== 搭路练习房间 =====");
        if (manager.courses().isEmpty()) {
            Chat.send(sender, "&7还没有房间。管理员用 &f/bridge create <名字> &7创建。");
            return;
        }
        for (PracticeCourse course : manager.courses()) {
            String state;
            if (!course.ready()) {
                state = "&c未设置起点/终点";
            } else {
                int players = manager.playerCount(course.name());
                if (players > 0) {
                    state = "&a" + players + " 人 &8| &7房主 " + manager.ownerName(course.name())
                            + " &8| " + (manager.allowsJoin(course.name()) ? "&a开放加入" : "&c关闭加入")
                            + " &8| 挖别人方块 " + (manager.allowsBreakOthers(course.name())
                            ? "&a允许" : "&c禁止");
                } else {
                    state = "&7空闲";
                }
            }
            Chat.send(sender, " &f" + course.name() + " &8- " + state);
            List<BridgePracticeManager.TopEntry> top = manager.topEntries(course.name());
            if (top.isEmpty()) {
                Chat.send(sender, "   &7暂无纪录");
            } else {
                BridgePracticeManager.TopEntry best = top.get(0);
                Chat.send(sender, "   &7最快：&f" + best.name() + " &a"
                        + BridgePracticeManager.formatTime(best.millis()));
            }
        }
        Chat.send(sender, "&7输入 &f/bridge join <房间> &7开始练习。");
    }

    private void top(CommandSender sender, String[] args) {
        String courseName = args.length >= 2 ? args[1] : null;
        if (courseName == null) {
            List<PracticeCourse> courses = manager.courses();
            if (courses.size() == 1) {
                courseName = courses.get(0).name();
            } else {
                Chat.send(sender, "&c用法：/bridge top <房间>，房间有：&f"
                        + String.join("、", courses.stream().map(PracticeCourse::name).toList()));
                return;
            }
        }
        PracticeCourse course = manager.course(courseName);
        if (course == null) {
            Chat.send(sender, "&c没有这个房间。");
            return;
        }
        List<BridgePracticeManager.TopEntry> top = manager.topEntries(course.name());
        Chat.send(sender, "&6===== " + course.name() + " 纪录榜 =====");
        if (top.isEmpty()) {
            Chat.send(sender, "&7还没有人完成这个房间。");
            return;
        }
        int index = 1;
        for (BridgePracticeManager.TopEntry entry : top) {
            Chat.send(sender, " &7#" + index + " &f" + entry.name() + " &a"
                    + BridgePracticeManager.formatTime(entry.millis()));
            index++;
        }
    }

    private void stats(CommandSender sender, String[] args) {
        UUID id;
        String name;
        if (args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            id = target.getUniqueId();
            name = target.getName() == null ? args[1] : target.getName();
        } else if (sender instanceof Player player) {
            id = player.getUniqueId();
            name = player.getName();
        } else {
            Chat.send(sender, "&c用法：/bridge stats <玩家>");
            return;
        }
        Chat.send(sender, "&6" + name + " &7的搭路练习纪录：");
        boolean any = false;
        for (PracticeCourse course : manager.courses()) {
            long best = manager.bestOf(course.name(), id);
            int finishes = manager.finishesOf(course.name(), id);
            int falls = manager.fallsOf(course.name(), id);
            if (best <= 0 && finishes <= 0 && falls <= 0) {
                continue;
            }
            any = true;
            Chat.send(sender, " &f" + course.name() + " &8- &7最佳 &a"
                    + (best > 0 ? BridgePracticeManager.formatTime(best) : "--")
                    + " &7| 完成 &f" + finishes + " &7| 掉落 &f" + falls);
        }
        if (!any) {
            Chat.send(sender, "&7还没有任何纪录。");
        }
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&6搭路练习指令：");
        Chat.send(sender, "&f/bridge &7- 打开房间选择菜单");
        Chat.send(sender, "&f/bridge join <房间> &7- 直接进入某个房间");
        Chat.send(sender, "&f/bridge leave &7- 离开练习");
        Chat.send(sender, "&f/bridge reset &7- 清掉本次方块回到起点");
        Chat.send(sender, "&f/bridge lock &7- 房主切换房间是否允许加入");
        Chat.send(sender, "&f/bridge breakblocks &7- 房主切换能不能挖别人的方块");
        Chat.send(sender, "&f/bridge list &7- 查看所有房间状态");
        Chat.send(sender, "&f/bridge top [房间] &7- 查看房间最快纪录榜");
        Chat.send(sender, "&f/bridge stats [玩家] &7- 查看个人纪录");
        if (sender.hasPermission("bridgepractice.admin")) {
            Chat.send(sender, "&6-- 管理 --");
            Chat.send(sender, "&f/bridge create <名字> &7- 创建房间");
            Chat.send(sender, "&f/bridge setstart <名字> [1|2] &7- 设置起点；填 1/2 时设成一条起点线");
            Chat.send(sender, "&f/bridge setspawn <名字> &7- 设置房间出生点（不设则用起点）");
            Chat.send(sender, "&f/bridge setend <名字> [1|2] &7- 设置终点；填 1/2 时设成一条终点线");
            Chat.send(sender, "&f/bridge setvoid <名字> [Y] &7- 设置虚空高度（不填用你当前高度）");
            Chat.send(sender, "&f/bridge icon <名字> <方块> &7- 设置菜单图标");
            Chat.send(sender, "&f/bridge clear <名字> &7- 清掉房间内的残留练习方块");
            Chat.send(sender, "&f/bridge delete <名字> &7- 删除房间");
            Chat.send(sender, "&f/bridge reload &7- 重新读取配置和房间");
        }
    }

    // ---------- 管理指令 ----------

    private void create(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge create <名字>");
            return;
        }
        String error = manager.createCourse(args[1]);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else {
            Chat.send(sender, "&a已创建房间 &f" + args[1] + "&a。站在起点执行 &f/bridge setstart "
                    + args[1] + "&a（可加 1/2 设两条起点线），再站到终点执行 &f/bridge setend "
                    + args[1] + "&a。");
        }
    }

    private void setstart(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以设置起点。");
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge setstart <名字> [1|2]");
            return;
        }
        int index = 0;
        if (args.length >= 3) {
            if (args[2].equals("1")) {
                index = 1;
            } else if (args[2].equals("2")) {
                index = 2;
            } else {
                Chat.send(sender, "&c起点序号只能是 1 或 2（不填则设成一个点）。");
                return;
            }
        }
        String error = manager.setStart(args[1], player.getLocation(), index);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else if (index == 0) {
            Chat.send(sender, "&a已把 &f" + args[1] + " &a的起点设在你脚下（一个点）。");
        } else {
            Chat.send(sender, "&a已设置 &f" + args[1] + " &a起点线的第 &f" + index + " &a个端点。");
        }
    }

    private void setspawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以设置出生点。");
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge setspawn <名字>");
            return;
        }
        String error = manager.setSpawn(args[1], player.getLocation());
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else {
            Chat.send(sender, "&a已把 &f" + args[1] + " &a的出生点设在你脚下。不设时默认使用起点。");
        }
    }

    private void setend(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家可以设置终点。");
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge setend <名字> [1|2]");
            return;
        }
        int index = 0;
        if (args.length >= 3) {
            if (args[2].equals("1")) {
                index = 1;
            } else if (args[2].equals("2")) {
                index = 2;
            } else {
                Chat.send(sender, "&c终点序号只能是 1 或 2（不填则设成一个点）。");
                return;
            }
        }
        String error = manager.setEnd(args[1], player.getLocation(), index);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else if (index == 0) {
            Chat.send(sender, "&a已把 &f" + args[1] + " &a的终点设在你脚下（一个点）。");
        } else {
            Chat.send(sender, "&a已设置 &f" + args[1] + " &a终点线的第 &f" + index + " &a个端点。");
        }
    }

    private void setvoid(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge setvoid <名字> [Y]（不填 Y 就用你当前的方块高度）");
            return;
        }
        int y;
        if (args.length >= 3) {
            try {
                y = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                Chat.send(sender, "&cY 必须是整数高度。");
                return;
            }
        } else if (sender instanceof Player player) {
            y = player.getLocation().getBlockY();
        } else {
            Chat.send(sender, "&c控制台必须写高度：/bridge setvoid <名字> <Y>");
            return;
        }
        String error = manager.setVoid(args[1], y);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else {
            Chat.send(sender, "&a已把 &f" + args[1] + " &a的虚空高度设为 &fY=" + y
                    + "&a（低于这个高度算掉落）。");
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge delete <名字>");
            return;
        }
        String error = manager.deleteCourse(args[1]);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else {
            Chat.send(sender, "&a已删除房间 &f" + args[1] + "&a。");
        }
    }

    private void icon(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            Chat.send(sender, "&c用法：/bridge icon <名字> <方块>");
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null || !material.isItem()) {
            Chat.send(sender, "&c不是可用的方块/物品：&f" + args[2]);
            return;
        }
        String error = manager.setIcon(args[1], material);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        } else {
            Chat.send(sender, "&a已把 &f" + args[1] + " &a的菜单图标设置成 &f" + material.name() + "&a。");
        }
    }

    private void clear(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/bridge clear <名字>");
            return;
        }
        PracticeCourse course = manager.course(args[1]);
        if (course == null) {
            Chat.send(sender, "&c没有这个房间。");
            return;
        }
        if (manager.playerCount(course.name()) > 0) {
            Chat.send(sender, "&c这个房间正有人在练习，等他们离开再清理。");
            return;
        }
        int removed = manager.clearCourseArea(course);
        Chat.send(sender, "&a已清理房间 &f" + course.name() + " &a内的练习方块：&f" + removed + " &a个。");
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        manager.reload();
        Chat.send(sender, "&a已重新读取搭路练习配置和房间。");
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("bridgepractice.admin")) {
            Chat.send(sender, "&c你没有权限执行该指令。");
            return false;
        }
        return true;
    }

    // ---------- tab 补全 ----------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission("bridgepractice.admin")) {
                out.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "join", "top", "delete", "clear" -> {
                    return filter(courseNames(), args[1]);
                }
                case "setstart", "setspawn", "setend", "setvoid", "icon" -> {
                    return filter(courseNames(), args[1]);
                }
                case "stats" -> {
                    return filter(onlineNames(), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && sub.equals("icon")) {
            List<String> materials = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isLegacy()) {
                    materials.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
            return filter(materials, args[2]);
        }
        if (args.length == 3 && (sub.equals("setend") || sub.equals("setstart"))) {
            return filter(List.of("1", "2"), args[2]);
        }
        return List.of();
    }

    private List<String> courseNames() {
        List<String> names = new ArrayList<>();
        for (PracticeCourse course : manager.courses()) {
            names.add(course.name());
        }
        return names;
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
