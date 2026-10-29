package com.arcade;

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

/** /arcade 命令（别名 /party、/arcadeparty）。 */
public final class ArcadePartyCommand implements CommandExecutor, TabCompleter {

    private final ArcadePartyPlugin plugin;
    private final ArcadePartyManager manager;
    private final ArcadeMenu menu;

    public ArcadePartyCommand(ArcadePartyPlugin plugin, ArcadePartyManager manager, ArcadeMenu menu) {
        this.plugin = plugin;
        this.manager = manager;
        this.menu = menu;
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
                Player p = player(sender);
                if (p != null) {
                    manager.join(p);
                }
            }
            case "leave" -> {
                Player p = player(sender);
                if (p != null) {
                    manager.leave(p, false);
                }
            }
            case "list" -> list(sender);
            case "stars" -> stars(sender, args);
            case "menu" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player p = player(sender);
                if (p != null) {
                    menu.open(p);
                }
            }
            case "start" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (manager.state() != ArcadePartyManager.State.WAITING) {
                    Chat.send(sender, "&e派对已经在进行中。");
                } else {
                    manager.startParty(true);
                }
            }
            case "stop" -> {
                if (!admin(sender)) {
                    return true;
                }
                manager.abortParty("&e管理员结束了本场派对。");
            }
            case "setlobby" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player p = player(sender);
                if (p == null) {
                    return true;
                }
                manager.setLobby(p.getLocation());
                Chat.send(sender, "&a派对等待区已设置：" + describe(p.getLocation()));
            }
            case "setaudience" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player p = player(sender);
                if (p == null) {
                    return true;
                }
                manager.setAudience(p.getLocation());
                Chat.send(sender, "&a颁奖观众席已设置：" + describe(p.getLocation()));
            }
            case "setpodium" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player p = player(sender);
                if (p == null) {
                    return true;
                }
                if (args.length < 2) {
                    Chat.send(sender, "&c用法：/arcade setpodium <first|second|third>");
                    return true;
                }
                String place = args[1].toLowerCase(Locale.ROOT);
                if (!place.equals("first") && !place.equals("second") && !place.equals("third")) {
                    Chat.send(sender, "&c名次只能是 first / second / third。");
                    return true;
                }
                manager.setPodium(place, p.getLocation());
                Chat.send(sender, "&a颁奖台 " + place + " 已设置：" + describe(p.getLocation()));
            }
            case "save" -> {
                if (!admin(sender)) {
                    return true;
                }
                manager.saveSettings();
                Chat.send(sender, "&a配置已保存。");
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (manager.state() != ArcadePartyManager.State.WAITING) {
                    Chat.send(sender, "&c派对进行中，不能重载配置。");
                    return true;
                }
                plugin.reloadConfig();
                manager.reload();
                Chat.send(sender, "&a配置已重新载入。");
            }
            default -> Chat.send(sender, "&c未知子指令，输入 &f/arcade help &c查看帮助。");
        }
        return true;
    }

    private void list(CommandSender sender) {
        Chat.send(sender, "&6===== 街机派对 =====");
        Chat.send(sender, "&7状态：&e" + manager.state() + " &7等待 &e" + manager.waitingCount()
                + " &7场内 &e" + manager.playingCount());
        Chat.send(sender, "&7每局小游戏：&e" + manager.gamesPerParty()
                + " &7| 最多玩家 &e" + manager.maxPlayers()
                + " &7| 过渡 &e" + manager.transitionSeconds() + "s");
        Chat.send(sender, "&7等待区：&f" + (manager.getLobby() != null ? "已配置" : "未配置（/arcade setlobby）"));
        List<String> pool = manager.enabledGameKeys();
        List<String> names = new ArrayList<>();
        for (String key : pool) {
            names.add(manager.displayOf(key));
        }
        Chat.send(sender, "&7启用的小游戏（&e" + pool.size() + "&7）：&f"
                + (names.isEmpty() ? "无" : String.join("、", names)));
    }

    private void stars(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c该命令需要玩家执行。");
            return;
        }
        Player target = player;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                Chat.send(sender, "&c玩家不在线。");
                return;
            }
        }
        Chat.send(sender, "&6" + target.getName() + " &7本场派对星星：&6★ " + manager.starsOf(target.getUniqueId()));
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&6====== 街机派对 /arcade ======");
        Chat.send(sender, "&e/arcade join&f 加入  &e/arcade leave&f 离开  &e/arcade list&f 状态  &e/arcade stars [玩家]&f 星星");
        if (admin(sender)) {
            Chat.send(sender, "&6-- OP --");
            Chat.send(sender, "&e/arcade menu&f 打开设置菜单（每局个数 / 人数 / 过渡时间 / 小游戏开关）");
            Chat.send(sender, "&e/arcade start&f 开始  &e/arcade stop&f 结束");
            Chat.send(sender, "&e/arcade setlobby&f 等待区  &e/arcade setaudience&f 颁奖观众席");
            Chat.send(sender, "&e/arcade setpodium <first|second|third>&f 前三名站位");
            Chat.send(sender, "&e/arcade save&f 保存  &e/arcade reload&f 重载");
        }
    }

    private boolean admin(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!manager.isAdmin(player)) {
                Chat.send(sender, "&c你没有权限执行该指令。");
                return false;
            }
            return true;
        }
        return true;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.send(sender, "&c该命令需要玩家执行。");
        return null;
    }

    private String describe(Location loc) {
        return loc.getWorld().getName() + " " + String.format(Locale.ROOT, "%.1f %.1f %.1f",
                loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "list", "stars", "help"));
            boolean admin = !(sender instanceof Player player) || manager.isAdmin(player);
            if (admin) {
                subs.addAll(List.of("menu", "start", "stop", "setlobby", "setaudience",
                        "setpodium", "save", "reload"));
            }
            subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).forEach(out::add);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setpodium")) {
            for (String option : List.of("first", "second", "third")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        }
        return out;
    }
}
