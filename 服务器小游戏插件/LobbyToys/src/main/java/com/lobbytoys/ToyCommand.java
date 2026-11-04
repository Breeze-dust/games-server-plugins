package com.lobbytoys;

import com.hub.util.Chat;
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

    public ToyCommand(LobbyToysPlugin plugin, CloneManager clones, ToyItems items) {
        this.plugin = plugin;
        this.clones = clones;
        this.items = items;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "off" -> off(sender);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
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

        target.getInventory().addItem(items.create(target));
        Chat.send(sender, "&a已给 " + target.getName() + " 发放分身玩具。");
        if (!target.equals(sender)) {
            Chat.send(target, "&a你收到了一个分身玩具，右键试试。");
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

    private void reload(CommandSender sender) {
        if (!isAdmin(sender)) {
            Chat.send(sender, "&c只有管理员能重载配置。");
            return;
        }
        plugin.reloadSettings();
        Chat.send(sender, "&aLobbyToys 配置已重载。");
    }

    private void help(CommandSender sender, String label) {
        Chat.send(sender, "&b主城小玩具");
        Chat.send(sender, "&7/" + label + " give [玩家] &8- 发放分身玩具");
        Chat.send(sender, "&7/" + label + " off &8- 收回自己的分身");
        Chat.send(sender, "&7/" + label + " reload &8- 重载配置");
        Chat.send(sender, "&7右键玩具：召唤 / 收回分身（仅主城可用）");
    }

    private static boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOp()
                || player.hasPermission("lobbytoys.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "off", "reload", "help"), args[0]);
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
