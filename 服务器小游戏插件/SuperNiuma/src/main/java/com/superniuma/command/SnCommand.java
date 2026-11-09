package com.superniuma.command;

import com.superniuma.Msg;
import com.superniuma.SuperNiumaPlugin;
import com.superniuma.arena.Arena;
import com.superniuma.game.GameMode;
import com.superniuma.game.GameRoom;
import com.superniuma.menu.RoomMenu;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /sn 命令。 */
public final class SnCommand implements CommandExecutor, TabCompleter {

    private final SuperNiumaPlugin plugin;
    private final Map<String, Location> corners = new HashMap<>();

    public SnCommand(SuperNiumaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "rooms" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "&c只有玩家能打开菜单。");
                    return true;
                }
                RoomMenu.open(plugin, player);
            }
            case "create" -> create(sender, args);
            case "join" -> join(sender, args);
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "&c只有玩家能离开房间。");
                    return true;
                }
                if (plugin.rooms().roomOf(player) == null) {
                    Msg.send(player, "&c你不在任何房间里。");
                    return true;
                }
                plugin.rooms().leave(player, true);
                Msg.send(player, "&7已离开房间。");
            }
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "&c只有玩家能开始游戏。");
                    return true;
                }
                GameRoom room = plugin.rooms().roomOf(player);
                if (room == null) {
                    Msg.send(player, "&c你不在任何房间里。");
                    return true;
                }
                room.startGame(player);
            }
            case "stop" -> stop(sender);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            case "arena" -> arena(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Msg.raw(sender, "&e&l超级牛马");
        Msg.raw(sender, "&f/sn menu &7- 打开房间菜单");
        Msg.raw(sender, "&f/sn create <地图> [solo|team] [队伍数] [目标分] [最高回合]");
        Msg.raw(sender, "&f/sn join <房间号> &7- 加入房间");
        Msg.raw(sender, "&f/sn leave &7- 离开房间");
        Msg.raw(sender, "&f/sn start &7- 房主开始游戏");
        Msg.raw(sender, "&f/sn list &7- 房间列表");
        Msg.raw(sender, "&f/sn stop &7- 房主/管理停止当前对局");
        Msg.raw(sender, "&f/sn arena ... &7- 地图配置");
        Msg.raw(sender, "&f/sn reload &7- 重载配置");
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&c只有玩家能创建房间。");
            return;
        }
        if (args.length < 2) {
            Msg.send(player, "&c用法：/sn create <地图> [solo|team] [队伍数] [目标分] [最高回合]");
            return;
        }
        if (plugin.rooms().roomOf(player) != null) {
            Msg.send(player, "&c你已经在一个房间里了。");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(player, "&c没有这张地图：" + args[1]);
            return;
        }
        if (!arena.isReady()) {
            Msg.send(player, "&c这张地图还没有配置完整。");
            return;
        }
        GameMode mode = args.length > 2 ? GameMode.parse(args[2]) : GameMode.SOLO;
        int teams = args.length > 3 ? parseInt(args[3], plugin.getConfig().getInt("room.default-team-count", 2))
                : plugin.getConfig().getInt("room.default-team-count", 2);
        int win = args.length > 4 ? parseInt(args[4], plugin.getConfig().getInt("room.default-win-score", 10))
                : plugin.getConfig().getInt("room.default-win-score", 10);
        int rounds = args.length > 5 ? parseInt(args[5], plugin.getConfig().getInt("room.default-max-rounds", 8))
                : plugin.getConfig().getInt("room.default-max-rounds", 8);
        GameRoom room = plugin.rooms().create(arena, mode, teams, win, rounds, player);
        Msg.send(player, "&a房间已创建：&e" + room.id() + " &7（" + room.modeText()
                + "，目标 " + room.winScore() + " 分，最高 " + room.maxRounds() + " 回合）");
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&c只有玩家能加入房间。");
            return;
        }
        if (args.length < 2) {
            Msg.send(player, "&c用法：/sn join <房间号>");
            return;
        }
        if (plugin.rooms().roomOf(player) != null) {
            Msg.send(player, "&c你已经在一个房间里了。");
            return;
        }
        GameRoom room = plugin.rooms().byId(args[1]);
        if (room == null) {
            Msg.send(player, "&c房间不存在或已结束：&e" + args[1]
                    + "&c。这里要填 /sn create 返回的 4 位房间号，不是地图名；用 /sn list 查看当前房间。");
            return;
        }
        if (!plugin.rooms().join(room.id(), player)) {
            Msg.send(player, "&c加入失败（房间已开始或已满）。");
            return;
        }
        Msg.send(player, "&a已加入房间 &e" + room.id() + "&a。");
    }

    private void stop(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        GameRoom room = player == null ? null : plugin.rooms().roomOf(player);
        if (room == null) {
            Msg.send(sender, "&c没有可停止的房间。");
            return;
        }
        if (player != null && !player.getUniqueId().equals(room.host())
                && !player.hasPermission("sn.admin")) {
            Msg.send(player, "&c只有房主或管理员能停止对局。");
            return;
        }
        room.shutdown();
        plugin.rooms().removeRoom(room);
        Msg.send(sender, "&7房间已停止并还原场地。");
    }

    private void list(CommandSender sender) {
        if (plugin.rooms().all().isEmpty()) {
            Msg.send(sender, "&7当前没有房间。");
            return;
        }
        Msg.send(sender, "&e房间列表：");
        for (GameRoom room : plugin.rooms().all()) {
            Msg.raw(sender, "&f" + room.id() + " &7- " + room.arena().getName()
                    + " &7| " + room.modeText() + " | " + room.statusText()
                    + " | " + room.size() + "/" + room.maxPlayers());
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("sn.admin")) {
            Msg.send(sender, "&c没有权限。");
            return;
        }
        plugin.reloadConfig();
        plugin.arenas().load();
        plugin.mechanisms().load();
        Msg.send(sender, "&a配置已重载。");
    }

    // ---------- 地图配置 ----------

    private void arena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sn.admin")) {
            Msg.send(sender, "&c没有权限。");
            return;
        }
        if (args.length < 2) {
            Msg.raw(sender, "&e/sn arena create <名字>");
            Msg.raw(sender, "&e/sn arena delete <名字>");
            Msg.raw(sender, "&e/sn arena list");
            Msg.raw(sender, "&e/sn arena info <名字>");
            Msg.raw(sender, "&e/sn arena setspawn <名字>");
            Msg.raw(sender, "&e/sn arena setstart <名字>");
            Msg.raw(sender, "&e/sn arena setpos1 <名字> / setpos2 <名字>");
            Msg.raw(sender, "&e/sn arena setfinish1 <名字> / setfinish2 <名字>");
            Msg.raw(sender, "&e/sn arena setvoid <名字>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                if (plugin.arenas().all().isEmpty()) {
                    Msg.send(sender, "&7还没有地图。");
                    return;
                }
                for (Arena arena : plugin.arenas().all()) {
                    Msg.raw(sender, "&f" + arena.getName() + " &7- " + arena.getWorldName()
                            + (arena.isReady() ? " &a✓" : " &c未完成"));
                }
            }
            case "create" -> {
                if (!(sender instanceof Player player) || args.length < 3) {
                    Msg.send(sender, "&c用法：/sn arena create <名字>（需要玩家在世界里执行）");
                    return;
                }
                String name = args[2];
                if (plugin.arenas().get(name) != null) {
                    Msg.send(player, "&c地图已存在：" + name);
                    return;
                }
                Arena arena = plugin.arenas().create(name, player.getWorld());
                arena.setSpawn(player.getLocation());
                arena.setStart(player.getLocation());
                plugin.arenas().save();
                Msg.send(player, "&a地图已创建：&e" + name + "&a，接着配置出生点/起点/范围/终点。");
            }
            case "delete" -> {
                if (args.length < 3) {
                    Msg.send(sender, "&c用法：/sn arena delete <名字>");
                    return;
                }
                Msg.send(sender, plugin.arenas().delete(args[2])
                        ? "&a已删除地图 " + args[2]
                        : "&c没有这张地图。");
            }
            case "info" -> {
                Arena arena = requireArena(sender, args);
                if (arena == null) {
                    return;
                }
                Msg.raw(sender, "&e地图 " + arena.getName() + "（" + arena.getWorldName() + "）");
                Msg.raw(sender, "&7出生点：" + fmt(arena.getSpawn()));
                Msg.raw(sender, "&7起点：" + fmt(arena.getStart()));
                Msg.raw(sender, "&7建造范围：" + fmt(arena.getBuildMin()) + " ~ " + fmt(arena.getBuildMax()));
                Msg.raw(sender, "&7终点范围：" + fmt(arena.getFinishMin()) + " ~ " + fmt(arena.getFinishMax()));
                Msg.raw(sender, "&7虚空高度：" + arena.getVoidY());
                Msg.raw(sender, arena.isReady() ? "&a状态：可用" : "&c状态：配置不完整");
            }
            case "setspawn", "setstart" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "&c需要玩家执行。");
                    return;
                }
                Arena arena = requireArena(sender, args);
                if (arena == null) {
                    return;
                }
                if (action.equals("setspawn")) {
                    arena.setSpawn(player.getLocation());
                } else {
                    arena.setStart(player.getLocation());
                }
                plugin.arenas().save();
                Msg.send(player, "&a已设置 " + arena.getName() + " 的" + (action.equals("setspawn") ? "出生点" : "起点") + "。");
            }
            case "setvoid" -> {
                if (!(sender instanceof Player player)) {
                    Msg.send(sender, "&c需要玩家执行。");
                    return;
                }
                Arena arena = requireArena(sender, args);
                if (arena == null) {
                    return;
                }
                arena.setVoidY(player.getLocation().getY());
                plugin.arenas().save();
                Msg.send(player, "&a已设置 " + arena.getName() + " 的虚空高度为 " + player.getLocation().getY());
            }
            case "setpos1", "setpos2", "setfinish1", "setfinish2" -> setCorner(sender, args, action);
            default -> Msg.send(sender, "&c未知的地图子命令。");
        }
    }

    private void setCorner(CommandSender sender, String[] args, String action) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&c需要玩家执行。");
            return;
        }
        Arena arena = requireArena(sender, args);
        if (arena == null) {
            return;
        }
        String kind = action.startsWith("setfinish") ? "finish" : "build";
        String firstKey = cornerKey(player, kind + "1", arena.getName());
        String secondKey = cornerKey(player, kind + "2", arena.getName());
        if (action.endsWith("1")) {
            corners.put(firstKey, player.getLocation());
            Msg.send(player, "&a已记录" + (kind.equals("build") ? "建造" : "终点") + "范围第一个角，接着执行 set"
                    + (kind.equals("build") ? "pos2" : "finish2") + "。");
            return;
        }
        Location first = corners.remove(firstKey);
        if (first == null) {
            Msg.send(player, "&c请先记录第一个角（set" + (kind.equals("build") ? "pos1" : "finish1") + "）。");
            return;
        }
        Location second = player.getLocation();
        if (kind.equals("build")) {
            arena.setBuildCorner(first, second);
        } else {
            arena.setFinishCorner(first, second);
        }
        corners.remove(secondKey);
        plugin.arenas().save();
        Msg.send(player, "&a已设置 " + arena.getName() + " 的"
                + (kind.equals("build") ? "建造范围" : "终点范围") + "。");
    }

    private Arena requireArena(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.send(sender, "&c缺少地图名字。");
            return null;
        }
        Arena arena = plugin.arenas().get(args[2]);
        if (arena == null) {
            Msg.send(sender, "&c没有这张地图：" + args[2]);
        }
        return arena;
    }

    private String cornerKey(Player player, String kind, String arena) {
        return player.getUniqueId() + ":" + kind + ":" + arena;
    }

    private String fmt(Location location) {
        if (location == null) {
            return "未设置";
        }
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    // ---------- Tab ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("menu", "create", "join", "leave", "start", "stop",
                    "list", "reload", "arena", "help")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args[0].equalsIgnoreCase("create") && args.length == 2) {
            for (Arena arena : plugin.arenas().all()) {
                out.add(arena.getName());
            }
            return out;
        }
        if (args[0].equalsIgnoreCase("join") && args.length == 2) {
            return plugin.rooms().ids();
        }
        if (args[0].equalsIgnoreCase("arena")) {
            if (args.length == 2) {
                for (String sub : List.of("create", "delete", "list", "info", "setspawn", "setstart",
                        "setpos1", "setpos2", "setfinish1", "setfinish2", "setvoid")) {
                    if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(sub);
                    }
                }
                return out;
            }
            if (args.length == 3) {
                for (Arena arena : plugin.arenas().all()) {
                    out.add(arena.getName());
                }
                return out;
            }
        }
        return out;
    }
}
