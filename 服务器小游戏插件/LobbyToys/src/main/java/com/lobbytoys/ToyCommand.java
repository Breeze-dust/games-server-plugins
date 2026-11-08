package com.lobbytoys;

import com.hub.util.Chat;
import com.lobbytoys.scratch.ScratchManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ToyCommand implements CommandExecutor, TabCompleter {

    private final LobbyToysPlugin plugin;
    private final CloneManager clones;
    private final ToyItems items;
    private final ToyMenu menu;
    private final ScratchManager scratch;

    public ToyCommand(LobbyToysPlugin plugin, CloneManager clones, ToyItems items, ToyMenu menu,
                      ScratchManager scratch) {
        this.plugin = plugin;
        this.clones = clones;
        this.items = items;
        this.menu = menu;
        this.scratch = scratch;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> openMenu(sender);
            case "give" -> give(sender, args);
            case "off" -> off(sender);
            case "scratch" -> scratch(sender, args);
            case "debug" -> debug(sender);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能打开小玩具菜单。");
            return;
        }
        menu.open(player);
    }

    private void give(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            Chat.send(sender, "&c只有管理员能发放玩具道具。");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Chat.send(sender, "&c找不到在线玩家 " + args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Chat.send(sender, "&c控制台请写 /lobbytoys give <玩家>");
            return;
        }

        items.equip(target, items.selected(target), plugin.settings().itemSlot);
        Chat.send(sender, "&a已给 " + target.getName() + " 发放小玩具。");
        if (!target.equals(sender)) {
            Chat.send(target, "&a你收到了一个小玩具，右键试试。");
        }
    }

    private void off(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能收回自己的分身。");
            return;
        }
        if (!clones.isActive(player.getUniqueId())) {
            Chat.send(sender, "&7你当前没有开启分身。");
            return;
        }
        clones.stop(player.getUniqueId(), true);
    }

    /** 刮刮乐：只能命令打开，不进入小玩具菜单，没有权限的玩家看不到入口。 */
    private void scratch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能生成刮刮乐。");
            return;
        }
        if (!canScratch(sender)) {
            Chat.send(sender, "&c你没有使用刮刮乐的权限。");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("close")) {
            if (!scratch.hasSession(player)) {
                Chat.send(player, "&7你当前没有刮刮乐。");
                return;
            }
            scratch.close(player.getUniqueId(), "&7刮刮乐已收回。");
            return;
        }
        int rows = scratch.settings().rows;
        int columns = scratch.settings().columns;
        if (args.length >= 3) {
            rows = parseDimension(args[1], rows);
            columns = parseDimension(args[2], columns);
        }
        scratch.open(player, rows, columns);
    }

    private static int parseDimension(String raw, int fallback) {
        try {
            return Math.max(1, Math.min(6, Integer.parseInt(raw)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void debug(CommandSender sender) {
        if (!isAdmin(sender)) {
            Chat.send(sender, "&c只有管理员能看调试信息。");
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能看手里的道具。");
            return;
        }
        Chat.send(player, "&7插件版本：&f" + plugin.getPluginMeta().getVersion());
        Chat.send(player, "&7手里：&f" + items.describe(player.getInventory().getItemInMainHand()));
        ToyType selected = items.selected(player);
        String base = "toys." + selected.id();
        Chat.send(player, "&7配置原文：material=&f" + plugin.getConfig().getString(base + ".material")
                + " &7potion=&f" + plugin.getConfig().getString(base + ".potion"));
        Chat.send(player, "&7hide-from-owner=&f" + plugin.settings().hideFromOwner
                + " &7worlds=&f" + plugin.settings().worlds);
        Chat.send(player, "&7已选玩具：&f" + selected.id()
                + " &7期望外观：&f" + items.expectedLook(selected));
    }

    private void reload(CommandSender sender) {
        if (!isAdmin(sender)) {
            Chat.send(sender, "&c只有管理员能重载配置。");
            return;
        }
        plugin.reloadSettings();
        int refreshed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (items.refresh(player)) {
                refreshed++;
            }
        }
        Chat.send(sender, "&aLobbyToys 配置已重载"
                + (refreshed > 0 ? "，已刷新 " + refreshed + " 名玩家的小玩具。" : "。"));
    }

    private void help(CommandSender sender, String label) {
        Chat.send(sender, "&b主城小玩具");
        Chat.send(sender, "&7/" + label + " menu &8- 打开小玩具菜单");
        Chat.send(sender, "&7/" + label + " give [玩家] &8- 发放小玩具");
        Chat.send(sender, "&7/" + label + " scratch [行 列] &8- 生成 / 收回刮刮乐（管理员）");
        Chat.send(sender, "&7/" + label + " off &8- 收回自己的分身");
        Chat.send(sender, "&7/" + label + " debug &8- 查看插件版本与手持道具的玩具标记");
        Chat.send(sender, "&7/" + label + " reload &8- 重载配置");
        Chat.send(sender, "&7右键玩具：召唤 / 收回分身（仅主城可用）");
    }

    private static boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOp()
                || player.hasPermission("lobbytoys.admin");
    }

    private static boolean canScratch(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOp()
                || player.hasPermission("lobbytoys.scratch")
                || player.hasPermission("lobbytoys.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("menu", "give", "off", "scratch", "debug", "reload", "help"), args[0]);
        }
        if (args[0].equalsIgnoreCase("scratch")) {
            if (args.length == 2) {
                return filter(List.of("close"), args[1]);
            }
            if (args.length == 3 || args.length == 4) {
                return filter(List.of("1", "2", "3", "4", "5", "6"), args[args.length - 1]);
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
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
