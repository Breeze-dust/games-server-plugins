package com.example.playertitles.command;

import com.example.playertitles.PlayerTitlesPlugin;
import com.example.playertitles.Title;
import com.example.playertitles.gui.AdminMenu;
import com.example.playertitles.gui.TitleMenu;
import com.example.playertitles.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class TitlesCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "menu", "help", "give", "giveall", "take", "revoke", "create", "delete",
            "setname", "setcolor", "setdesc", "setvisible", "list", "admin", "reload");

    private final PlayerTitlesPlugin plugin;

    public TitlesCommand(PlayerTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            openMenu(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> openMenu(sender);
            case "help" -> help(sender);
            case "give" -> give(sender, args);
            case "giveall" -> giveAll(sender, args);
            case "take" -> take(sender, args);
            case "revoke" -> revoke(sender, args);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "setname" -> setName(sender, args);
            case "setcolor" -> setColor(sender, args);
            case "setdesc" -> setDescription(sender, args);
            case "setvisible" -> setVisible(sender, args);
            case "list" -> list(sender);
            case "admin" -> admin(sender);
            case "reload" -> reload(sender);
            default -> {
                msg(sender, "&c未知的子指令，输入 &f/titles help &c查看帮助");
            }
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&c只有玩家可以打开头衔菜单");
            return;
        }
        if (!player.hasPermission("playertitles.use")) {
            msg(sender, "&c你没有权限使用头衔菜单");
            return;
        }
        TitleMenu.open(plugin, player, 0);
    }

    private void admin(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "&c只有玩家可以打开管理面板");
            return;
        }
        AdminMenu.open(plugin, player, 0);
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles give <玩家> <头衔ID> [keep]");
            msg(sender, "&7加 &fkeep &7= 只授予拥有权，保留玩家当前装备的头衔");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&c找不到在线玩家: " + args[1]);
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[2]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[2] + "，可用头衔: " + String.join(", ", titleIds()));
            return;
        }
        boolean keepCurrent = args.length >= 4
                && (args[3].equalsIgnoreCase("keep") || args[3].equalsIgnoreCase("noequip"));
        plugin.getTitleManager().grant(target.getUniqueId(), title.id());
        if (!keepCurrent) {
            plugin.getTitleManager().setPlayerTitle(target.getUniqueId(), title.id());
        }
        msg(sender, keepCurrent
                ? "&a已授予 &f" + target.getName() + " &a头衔（保留当前装备）"
                : "&a已给予并装备 &f" + target.getName() + " &a头衔 ");
        sender.sendMessage(title.bracketedComponent());
        target.sendMessage(Component.text(keepCurrent ? "你获得了新头衔（可在菜单中装备）: " : "你获得了新头衔: ",
                        NamedTextColor.GREEN)
                .append(title.bracketedComponent()));
    }

    private void take(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&c用法: /titles take <玩家> [头衔ID]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&c找不到在线玩家: " + args[1]);
            return;
        }
        if (args.length >= 3) {
            Title title = plugin.getTitleManager().getTitle(args[2]);
            if (title == null) {
                msg(sender, "&c找不到头衔: " + args[2] + "，可用头衔: " + String.join(", ", titleIds()));
                return;
            }
            plugin.getTitleManager().revoke(target.getUniqueId(), title.id());
            msg(sender, "&a已收回 &f" + target.getName() + " &a的头衔 ");
            sender.sendMessage(title.bracketedComponent());
            target.sendMessage(Component.text("你的头衔已被收回: ", NamedTextColor.YELLOW)
                    .append(title.bracketedComponent()));
            return;
        }
        String current = plugin.getTitleManager().getTitleId(target.getUniqueId());
        if (current == null) {
            plugin.getTitleManager().revokeAll(target.getUniqueId());
            msg(sender, "&a已收回 &f" + target.getName() + " &a的全部头衔");
        } else {
            plugin.getTitleManager().revoke(target.getUniqueId(), current);
            msg(sender, "&a已收回 &f" + target.getName() + " &a的头衔 &f" + current);
        }
        target.sendMessage(Component.text("你的头衔已被收回", NamedTextColor.YELLOW));
    }

    /** 给全服玩家授予同一个头衔（只授予拥有权，不覆盖当前装备；加 all 时连离线玩家一起）。 */
    private void giveAll(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&c用法: /titles giveall <头衔ID> [all]");
            msg(sender, "&7默认只给在线玩家；加 &fall &7会把服务器记录过的离线玩家也授予");
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[1]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[1] + "，可用头衔: " + String.join(", ", titleIds()));
            return;
        }
        java.util.Set<UUID> targets = new java.util.LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            targets.add(online.getUniqueId());
        }
        boolean includeOffline = args.length >= 3 && args[2].equalsIgnoreCase("all");
        if (includeOffline) {
            for (org.bukkit.OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                targets.add(offline.getUniqueId());
            }
        }
        int granted = plugin.getTitleManager().grantAll(targets, title.id());
        msg(sender, "&a已给 &f" + granted + " &a名玩家授予头衔 ");
        sender.sendMessage(title.bracketedComponent());
        if (!includeOffline) {
            msg(sender, "&7提示: 加 &fall &7可把离线玩家也一起授予");
        }
    }

    private void revoke(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles revoke <玩家> <头衔ID>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&c找不到在线玩家: " + args[1]);
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[2]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[2] + "，可用头衔: " + String.join(", ", titleIds()));
            return;
        }
        plugin.getTitleManager().revoke(target.getUniqueId(), title.id());
        msg(sender, "&a已收回 &f" + target.getName() + " &a的头衔 ");
        sender.sendMessage(title.bracketedComponent());
        target.sendMessage(Component.text("你的头衔已被收回: ", NamedTextColor.YELLOW)
                .append(title.bracketedComponent()));
    }

    private void create(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles create <ID> <名称>");
            return;
        }
        String id = args[1];
        if (!id.matches("[A-Za-z0-9_]{1,24}")) {
            msg(sender, "&cID 只能包含字母、数字、下划线，且最长 24 位");
            return;
        }
        if (plugin.getTitleManager().exists(id)) {
            msg(sender, "&c头衔 ID 已存在: " + id);
            return;
        }
        String name = join(args, 2);
        if (name.isBlank()) {
            msg(sender, "&c头衔名称不能为空");
            return;
        }
        plugin.getTitleManager().create(id, name, "&f", "&7暂无描述");
        msg(sender, "&a已创建头衔 &f" + id + "&a，可用 &f/titles setcolor " + id
                + " <颜色> &a与 &f/titles setdesc " + id + " <描述> &a继续设置");
    }

    private void delete(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&c用法: /titles delete <ID>");
            return;
        }
        if (!plugin.getTitleManager().delete(args[1])) {
            msg(sender, "&c找不到头衔: " + args[1]);
            return;
        }
        msg(sender, "&a已删除头衔 &f" + args[1]);
    }

    private void setName(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles setname <ID> <新名称>");
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[1]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[1]);
            return;
        }
        plugin.getTitleManager().update(
                new Title(title.id(), join(args, 2), title.color(), title.description(), title.visible()));
        msg(sender, "&a头衔名称已更新");
    }

    private void setColor(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles setcolor <ID> <颜色>");
            msg(sender, "&7颜色示例: &6gold&7、&cred&7、&e#FFAA00&7 或 &eFFAA00");
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[1]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[1]);
            return;
        }
        String color = Text.normalizeColor(args[2]);
        if (color == null) {
            msg(sender, "&c无法识别的颜色: " + args[2] + "，可用 &6gold&c、&4&c&4&c&c 或 &f#FFAA00");
            return;
        }
        plugin.getTitleManager().update(
                new Title(title.id(), title.name(), color, title.description(), title.visible()));
        msg(sender, "&a头衔颜色已更新");
    }

    private void setDescription(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles setdesc <ID> <描述>");
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[1]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[1]);
            return;
        }
        plugin.getTitleManager().update(
                new Title(title.id(), title.name(), title.color(), join(args, 2), title.visible()));
        msg(sender, "&a头衔描述已更新");
    }

    private void setVisible(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            msg(sender, "&c用法: /titles setvisible <ID> <true|false>");
            msg(sender, "&7true = 未拥有的玩家也能在菜单里看到；false = 只有拥有者能看到");
            return;
        }
        Title title = plugin.getTitleManager().getTitle(args[1]);
        if (title == null) {
            msg(sender, "&c找不到头衔: " + args[1]);
            return;
        }
        boolean visible;
        if (args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("yes")
                || args[2].equalsIgnoreCase("on") || args[2].equals("1")) {
            visible = true;
        } else if (args[2].equalsIgnoreCase("false") || args[2].equalsIgnoreCase("no")
                || args[2].equalsIgnoreCase("off") || args[2].equals("0")) {
            visible = false;
        } else {
            msg(sender, "&c请填写 true 或 false");
            return;
        }
        plugin.getTitleManager().update(
                new Title(title.id(), title.name(), title.color(), title.description(), visible));
        msg(sender, visible
                ? "&a未拥有该头衔的玩家现在可以在菜单中看到它"
                : "&c未拥有该头衔的玩家现在看不到它");
    }

    private void list(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        List<Title> titles = plugin.getTitleManager().getTitles();
        if (titles.isEmpty()) {
            msg(sender, "&7当前没有任何头衔");
            return;
        }
        msg(sender, "&7当前共有 &f" + titles.size() + " &7个头衔:");
        for (Title title : titles) {
            sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(title.id() + " ", NamedTextColor.GRAY))
                    .append(title.bracketedComponent())
                    .append(Component.text(" " + (title.visible() ? "未拥有者可见" : "仅拥有者可见")
                            + " · 拥有 " + plugin.getTitleManager().ownerCount(title.id()) + " 人",
                            NamedTextColor.DARK_GRAY)));
        }
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadConfig();
        plugin.getTitleManager().reload();
        msg(sender, "&a头衔数据已重新载入");
    }

    private void help(CommandSender sender) {
        msg(sender, "&6头衔插件指令:");
        msg(sender, "&f/titles &7- 打开头衔菜单");
        if (sender.hasPermission("playertitles.admin")) {
            msg(sender, "&f/titles admin &7- 打开管理面板");
            msg(sender, "&f/titles give <玩家> <ID> [keep] &7- 授予并装备；加 keep 只授予不装备");
            msg(sender, "&f/titles giveall <ID> [all] &7- 给全服玩家授予头衔（不自动装备）");
            msg(sender, "&f/titles take <玩家> [ID] &7- 收回指定头衔；不写 ID 则收回当前/全部");
            msg(sender, "&f/titles revoke <玩家> <ID> &7- 收回指定头衔");
            msg(sender, "&f/titles create <ID> <名称> &7- 新建头衔");
            msg(sender, "&f/titles delete <ID> &7- 删除头衔");
            msg(sender, "&f/titles setname <ID> <名称> &7- 修改名称");
            msg(sender, "&f/titles setcolor <ID> <颜色> &7- 修改颜色");
            msg(sender, "&f/titles setdesc <ID> <描述> &7- 修改描述");
            msg(sender, "&f/titles setvisible <ID> <true|false> &7- 未拥有者是否可见");
            msg(sender, "&f/titles list &7- 列出所有头衔");
            msg(sender, "&f/titles reload &7- 重新载入 titles.yml 与 config.yml");
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("playertitles.admin")) {
            msg(sender, "&c你没有权限执行该指令");
            return false;
        }
        return true;
    }

    private List<String> titleIds() {
        List<String> ids = new ArrayList<>();
        for (Title title : plugin.getTitleManager().getTitles()) {
            ids.add(title.id());
        }
        return ids;
    }

    private String join(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(Text.parse("&7[&6头衔&7] " + text));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            boolean isAdmin = sender.hasPermission("playertitles.admin");
            for (String option : SUBCOMMANDS) {
                if (isAdmin || option.equals("menu") || option.equals("help")) {
                    options.add(option);
                }
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give", "take", "revoke" -> filter(onlinePlayerNames(), args[1]);
                case "giveall", "delete", "setname", "setcolor", "setdesc", "setvisible" -> filter(titleIds(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "give", "take", "revoke" -> filter(titleIds(), args[2]);
                case "giveall" -> filter(List.of("all"), args[2]);
                case "setvisible" -> filter(List.of("true", "false"), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("give")) {
            return filter(List.of("keep", "equip"), args[3]);
        }
        return List.of();
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

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }
}
