package com.hubnpc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /npc 指令：生成、删除、改名、皮肤、右键指令、移动。 */
final class NpcCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> ID_COMMANDS =
            Set.of("remove", "rename", "skin", "move", "tp", "info");
    private static final String[] ROOT = {
            "help", "create", "remove", "list", "rename",
            "skin", "command", "move", "tp", "info"
    };

    private final HubNPCPlugin plugin;
    private final NpcManager manager;

    NpcCommand(HubNPCPlugin plugin, NpcManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "list" -> list(sender);
            case "rename" -> rename(sender, args);
            case "skin" -> skin(sender, args);
            case "command", "cmd" -> commands(sender, args);
            case "move" -> move(sender, args);
            case "tp" -> teleport(sender, args);
            case "info" -> info(sender, args);
            case "help" -> help(sender);
            default -> help(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------- 子指令

    private void create(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/npc create <id> <名字> [here | <x> <y> <z> [yaw] [pitch] | <世界> <x> <y> <z> ...]");
            return;
        }
        String id = args[1];
        if (!validId(id)) {
            msg(sender, "&cid 不能包含空格、点、&、<>、{}，长度 1-32。");
            return;
        }
        if (manager.get(id) != null) {
            msg(sender, "&c已经有一个叫 &f" + id + " &c的 NPC 了。");
            return;
        }

        int[] cursor = new int[1];
        String name = readName(args, 2, cursor);
        if (name.isBlank()) {
            msg(sender, "&c名字不能为空。");
            return;
        }

        Location location;
        try {
            location = parseLocation(sender, args, cursor[0]);
        } catch (IllegalArgumentException ex) {
            msg(sender, "&c" + ex.getMessage());
            return;
        }

        try {
            manager.create(id, name, location);
        } catch (IllegalArgumentException ex) {
            msg(sender, "&c生成失败：当前服务端不支持 Mannequin 实体。");
            plugin.getLogger().warning("生成 NPC 失败：" + ex.getMessage());
            return;
        }
        msg(sender, "&a已生成 NPC &f" + id + " &7：&r" + name);
        msg(sender, "&7位置：&f" + location.getWorld().getName() + " "
                + fmt(location.getX()) + " " + fmt(location.getY()) + " " + fmt(location.getZ())
                + " &7朝向：&f" + fmt(location.getYaw()) + " / " + fmt(location.getPitch()));
        msg(sender, "&7下一步：&e/npc skin " + id + " <玩家名> &7设置皮肤，"
                + "&e/npc command add " + id + " <指令> &7设置右键动作。");
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/npc remove <id>");
            return;
        }
        if (!manager.remove(args[1])) {
            notFound(sender, args[1]);
            return;
        }
        msg(sender, "&a已删除 NPC &f" + args[1] + "&a。");
    }

    private void list(CommandSender sender) {
        if (manager.size() == 0) {
            msg(sender, "&7还没有任何 NPC。用 &e/npc create&7 生成一个。");
            return;
        }
        msg(sender, "&6&lNPC 列表 &7(" + manager.size() + ")");
        for (NpcData data : manager.all()) {
            String loaded = manager.entity(data) != null ? "&a已加载" : "&7未加载";
            msg(sender, "&e" + data.id + " &7- &r" + data.displayName
                    + " &7| &f" + data.world + " " + fmt(data.x) + " " + fmt(data.y) + " " + fmt(data.z)
                    + " &7| " + loaded + " &7| 指令 " + data.commands.size() + " 条");
        }
    }

    private void rename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/npc rename <id> <新名字>");
            return;
        }
        NpcData data = manager.get(args[1]);
        if (data == null) {
            notFound(sender, args[1]);
            return;
        }
        int[] cursor = new int[1];
        String name = readName(args, 2, cursor);
        if (name.isBlank()) {
            msg(sender, "&c名字不能为空。");
            return;
        }
        manager.rename(data, name);
        msg(sender, "&a已把 &f" + data.id + " &a的名字改成：&r" + name);
    }

    private void skin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/npc skin <id> <玩家名|reset>");
            return;
        }
        NpcData data = manager.get(args[1]);
        if (data == null) {
            notFound(sender, args[1]);
            return;
        }
        if (args[2].equalsIgnoreCase("reset")) {
            manager.resetSkin(data);
            msg(sender, "&a已把 NPC &f" + data.id + " &a的皮肤恢复默认。");
            return;
        }
        if (plugin.skins() == null || !plugin.skins().available()) {
            msg(sender, "&c未检测到 SkinsRestorer 皮肤插件，无法读取皮肤。");
            return;
        }
        String playerName = args[2];
        msg(sender, "&7正在读取 &f" + playerName + " &7的皮肤...");
        plugin.skins().fetch(playerName, skin -> {
            if (skin == null) {
                msg(sender, "&c没有找到 &f" + playerName + " &c的皮肤（皮肤插件里没有记录）。");
                return;
            }
            manager.setSkin(data, playerName, skin);
            msg(sender, "&a已把 NPC &f" + data.id + " &a的皮肤设置为 &f" + playerName + "&a。");
        });
    }

    private void commands(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/npc command <add|remove|list|clear> <id> [内容/序号]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        NpcData data = manager.get(args[2]);
        if (data == null) {
            notFound(sender, args[2]);
            return;
        }
        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    usage(sender, "/npc command add <id> <指令>   例：/npc command add shop console: say 欢迎");
                    return;
                }
                String raw = join(args, 3);
                manager.addCommand(data, raw);
                msg(sender, "&a已添加右键指令 &e#" + data.commands.size() + "&a：&f" + raw);
                msg(sender, "&7以 &econsole:&7 开头由后台执行，否则以玩家身份执行；"
                        + "可用占位符 &e{player}&7、&e{npc}&7、&e{world}&7。");
            }
            case "remove" -> {
                if (args.length < 4) {
                    usage(sender, "/npc command remove <id> <序号>");
                    return;
                }
                int index;
                try {
                    index = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    msg(sender, "&c序号必须是数字。");
                    return;
                }
                if (!manager.removeCommand(data, index)) {
                    msg(sender, "&c序号超出范围（当前共 " + data.commands.size() + " 条）。");
                    return;
                }
                msg(sender, "&a已删除右键指令 #" + index + "。");
            }
            case "list" -> {
                if (data.commands.isEmpty()) {
                    msg(sender, "&7NPC &f" + data.id + " &7还没有右键指令。");
                    return;
                }
                msg(sender, "&6NPC &f" + data.id + " &6的右键指令：");
                for (int i = 0; i < data.commands.size(); i++) {
                    msg(sender, " &e#" + (i + 1) + " &7- &f" + data.commands.get(i));
                }
            }
            case "clear" -> {
                manager.clearCommands(data);
                msg(sender, "&a已清空 NPC &f" + data.id + " &a的所有右键指令。");
            }
            default -> usage(sender, "/npc command <add|remove|list|clear> <id> [内容/序号]");
        }
    }

    private void move(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/npc move <id> [here | <x> <y> <z> [yaw] [pitch] | <世界> <x> <y> <z> ...]");
            return;
        }
        NpcData data = manager.get(args[1]);
        if (data == null) {
            notFound(sender, args[1]);
            return;
        }
        Location location;
        try {
            location = parseLocation(sender, args, 2);
        } catch (IllegalArgumentException ex) {
            msg(sender, "&c" + ex.getMessage());
            return;
        }
        manager.move(data, location);
        msg(sender, "&a已把 NPC &f" + data.id + " &a移动到 &f"
                + location.getWorld().getName() + " " + fmt(location.getX()) + " "
                + fmt(location.getY()) + " " + fmt(location.getZ()) + "&a。");
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&c只有玩家可以传送到 NPC。");
            return;
        }
        if (args.length < 2) {
            usage(sender, "/npc tp <id>");
            return;
        }
        NpcData data = manager.get(args[1]);
        if (data == null) {
            notFound(sender, args[1]);
            return;
        }
        Location location = data.location();
        if (location == null) {
            msg(sender, "&cNPC 所在世界 &f" + data.world + " &c不存在。");
            return;
        }
        player.teleport(location);
        msg(sender, "&a已传送到 NPC &f" + data.id + "&a。");
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/npc info <id>");
            return;
        }
        NpcData data = manager.get(args[1]);
        if (data == null) {
            notFound(sender, args[1]);
            return;
        }
        msg(sender, "&6&lNPC &f" + data.id);
        msg(sender, " &7名字：&r" + data.displayName);
        msg(sender, " &7位置：&f" + data.world + " " + fmt(data.x) + " " + fmt(data.y) + " " + fmt(data.z)
                + " &7朝向：&f" + fmt(data.yaw) + " / " + fmt(data.pitch));
        msg(sender, " &7实体：&f" + data.entityId
                + (manager.entity(data) != null ? " &a(已加载)" : " &7(未加载)"));
        msg(sender, " &7皮肤：&f" + (data.skinName == null ? "默认" : data.skinName));
        msg(sender, " &7右键指令：&f" + data.commands.size() + " 条");
        for (int i = 0; i < data.commands.size(); i++) {
            msg(sender, "  &e#" + (i + 1) + " &7- &f" + data.commands.get(i));
        }
    }

    private void help(CommandSender sender) {
        msg(sender, "&6&lHubNPC &7- 主城 NPC 管理");
        msg(sender, "&e/npc create <id> <名字> [位置] &7生成 NPC（不写位置=脚下，朝向玩家）");
        msg(sender, "&7  位置写法：&fhere &7| &f<x> <y> <z> [yaw] [pitch] &7| &f<世界> <x> <y> <z> ...");
        msg(sender, "&e/npc skin <id> <玩家名> &7加载该玩家皮肤（走 SkinsRestorer）");
        msg(sender, "&e/npc skin <id> reset &7恢复默认皮肤");
        msg(sender, "&e/npc command add <id> <指令> &7添加右键执行指令");
        msg(sender, "&7  &fconsole: say 你好 &7由后台执行；不加前缀则以玩家身份执行");
        msg(sender, "&e/npc command list|remove|clear <id> [序号] &7查看/删除/清空右键指令");
        msg(sender, "&e/npc rename <id> <新名字> &7改名　&e/npc move <id> [位置] &7移动");
        msg(sender, "&e/npc list &7列表　&e/npc info <id> &7详情　&e/npc tp <id> &7传送　&e/npc remove <id> &7删除");
        msg(sender, "&7颜色：&f&6 &7颜色代码、&f&#FFAA00 &7单色、"
                + "&f<gradient:#FF0000:#00AAFF>渐变</gradient> &7或 "
                + "&f{#FF0000:#00AAFF}简写渐变{/}");
        msg(sender, "&7带空格的名字可以用英文引号包起来，例如 &f\"商店 老板\"&7。");
    }

    // ---------------------------------------------------------------- 解析

    /** 名字支持英文引号包住多段；返回后 cursor[0] 指向位置参数的起点。 */
    private static String readName(String[] args, int start, int[] cursor) {
        if (start >= args.length) {
            cursor[0] = start;
            return "";
        }
        String first = args[start];
        if (!first.startsWith("\"")) {
            cursor[0] = start + 1;
            return first;
        }
        StringBuilder name = new StringBuilder(first.substring(1));
        if (first.length() > 1 && first.endsWith("\"")) {
            cursor[0] = start + 1;
            return name.substring(0, name.length() - 1);
        }
        for (int i = start + 1; i < args.length; i++) {
            String token = args[i];
            boolean closed = token.endsWith("\"");
            name.append(' ').append(closed ? token.substring(0, token.length() - 1) : token);
            if (closed) {
                cursor[0] = i + 1;
                return name.toString();
            }
        }
        cursor[0] = args.length;
        return name.toString();
    }

    /**
     * 解析位置：here / [世界] x y z [yaw] [pitch]。
     * 没写 yaw 时默认面向发指令的玩家。
     */
    private static Location parseLocation(CommandSender sender, String[] args, int start) {
        Player player = sender instanceof Player p ? p : null;
        Location here = player == null ? null : player.getLocation();

        if (args.length <= start || args[start].equalsIgnoreCase("here")) {
            if (here == null) {
                throw new IllegalArgumentException("控制台执行时必须指定坐标。");
            }
            Location location = here.clone();
            location.setYaw(here.getYaw() + 180f);
            location.setPitch(0f);
            return location;
        }

        int i = start;
        if (args[i].equalsIgnoreCase("at") || args[i].equalsIgnoreCase("位置")) {
            i++;
        }
        World world = here == null ? null : here.getWorld();
        if (i < args.length) {
            World named = Bukkit.getWorld(args[i]);
            if (named != null) {
                world = named;
                i++;
            }
        }
        if (world == null) {
            if (Bukkit.getWorlds().isEmpty()) {
                throw new IllegalArgumentException("服务器还没有任何世界。");
            }
            world = Bukkit.getWorlds().get(0);
        }
        if (args.length - i < 3) {
            throw new IllegalArgumentException("坐标不完整：需要 x y z [yaw] [pitch]。");
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(args[i++]);
            y = Double.parseDouble(args[i++]);
            z = Double.parseDouble(args[i++]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("坐标必须是数字。");
        }

        Float yaw = null;
        float pitch = 0f;
        try {
            if (args.length - i >= 1) {
                yaw = Float.parseFloat(args[i++]);
            }
            if (args.length - i >= 1) {
                pitch = Float.parseFloat(args[i++]);
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("朝向必须是数字。");
        }

        Location location = new Location(world, x, y, z);
        if (yaw != null) {
            location.setYaw(yaw);
            location.setPitch(pitch);
        } else if (player != null && player.getWorld().equals(world)) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            location.setYaw(dx * dx + dz * dz < 1.0E-4
                    ? player.getLocation().getYaw() + 180f
                    : (float) Math.toDegrees(Math.atan2(-dx, dz)));
            location.setPitch(0f);
        } else {
            location.setYaw(0f);
            location.setPitch(0f);
        }
        return location;
    }

    // ---------------------------------------------------------------- 工具

    private static boolean validId(String id) {
        if (id.length() > 32) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '.' || c == '&' || c == '<' || c == '>' || c == '{' || c == '}'
                    || Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private static String join(String[] args, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    private static String fmt(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(Text.parse(text));
    }

    private void usage(CommandSender sender, String text) {
        msg(sender, "&e用法：&f" + text);
    }

    private void notFound(CommandSender sender, String id) {
        msg(sender, "&c没有找到 NPC &f" + id + "&c。");
    }

    // ---------------------------------------------------------------- 补全

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of(ROOT));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && ID_COMMANDS.contains(sub)) {
            return filter(args[1], manager.ids());
        }
        if (args.length == 2 && sub.equals("command")) {
            return filter(args[1], List.of("add", "remove", "list", "clear"));
        }
        if (args.length == 3 && sub.equals("command")) {
            return filter(args[2], manager.ids());
        }
        if (args.length == 3 && sub.equals("skin")) {
            return filter(args[2], List.of("reset"));
        }
        if (args.length == 3 && (sub.equals("create") || sub.equals("move"))) {
            return filter(args[2], List.of("here", "at"));
        }
        if (args.length == 4 && (sub.equals("create") || sub.equals("move"))) {
            if (args[2].equalsIgnoreCase("at")) {
                List<String> worlds = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    worlds.add(world.getName());
                }
                return filter(args[3], worlds);
            }
        }
        return List.of();
    }

    private static List<String> filter(String prefix, Collection<String> options) {
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
