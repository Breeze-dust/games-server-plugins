package com.dontdoit;

import com.dontdoit.criteria.Criterion;
import com.dontdoit.criteria.CriterionLibrary;
import com.hub.util.Chat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /ddi 命令：玩家入口 + 管理建场。 */
public final class DontDoItCommand implements CommandExecutor, TabCompleter {

    private final DontDoItPlugin plugin;
    private final DontDoItManager manager;

    public DontDoItCommand(DontDoItPlugin plugin, DontDoItManager manager) {
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
            case "help" -> help(sender);
            case "join" -> join(sender, args);
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                manager.leave(player, false);
                Chat.send(sender, "&7已退出不要做挑战。");
            }
            case "list" -> list(sender);
            case "stats" -> stats(sender, args);
            case "custom" -> custom(sender, args);
            case "settings" -> settings(sender, args);
            case "create" -> admin(sender, () -> create(sender, args));
            case "remove" -> admin(sender, () -> remove(sender, args));
            case "setcenter" -> admin(sender, () -> setCenter(sender, args));
            case "setarea" -> admin(sender, () -> setArea(sender, args));
            case "setlobby" -> admin(sender, () -> setLobby(sender, args));
            case "setspectator" -> admin(sender, () -> setSpectator(sender, args));
            case "gen" -> admin(sender, () -> gen(sender, args));
            case "selftest" -> admin(sender, () -> selfTest(sender, args));
            case "savemap" -> admin(sender, () -> saveMap(sender, args));
            case "rules" -> admin(sender, () -> rules(sender, args));
            case "save" -> admin(sender, () -> {
                manager.saveArenas();
                Chat.send(sender, "&a场地配置已保存。");
            });
            case "reload" -> admin(sender, () -> {
                for (Game g : manager.games().values()) {
                    if (g.state() == Game.State.RUNNING) {
                        Chat.send(sender, "&c有对局正在进行，先 /ddi stop 再重载。");
                        return;
                    }
                }
                manager.reload();
                Chat.send(sender, "&a已重载配置与词条库。");
            });
            case "start" -> admin(sender, () -> start(sender, args));
            case "stop" -> admin(sender, () -> stop(sender, args));
            case "change" -> admin(sender, () -> change(sender, args));
            case "grant" -> admin(sender, () -> grant(sender, args));
            case "trigger" -> trigger(sender, args);
            case "setlife" -> admin(sender, () -> setLife(sender, args));
            case "guess" -> guess(sender, args);
            case "words" -> words(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void admin(CommandSender sender, Runnable action) {
        if (!sender.isOp() && !sender.hasPermission("ddi.admin")) {
            Chat.send(sender, "&c你没有权限（需要 OP 或 ddi.admin）。");
            return;
        }
        action.run();
    }

    private void help(CommandSender sender) {
        Chat.send(sender, "&d&l不要做挑战 &7命令：");
        Chat.send(sender, "&e/ddi join [场地] &7加入等待室");
        Chat.send(sender, "&e/ddi leave &7退出");
        Chat.send(sender, "&e/ddi list &7场地列表");
        Chat.send(sender, "&e/ddi stats [玩家] &7胜场");
        Chat.send(sender, "&e/ddi custom <文字> &7设置自定义词条（也可用书与笔签名）");
        Chat.send(sender, "&e/ddi guess <队伍> <true|false> &7猜词条");
        Chat.send(sender, "&e/ddi trigger <队伍> &7触发别人的自定义词条");
        if (sender.isOp() || sender.hasPermission("ddi.admin")) {
            Chat.send(sender, "&6管理：&ecreate|remove|setcenter|setarea|setlobby|setspectator|gen|selftest|savemap|save|reload");
            Chat.send(sender, "&6对局：&estart|stop|change|grant|setlife|settings|words");
        }
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能加入。");
            return;
        }
        String arenaName = args.length > 1 ? args[1] : null;
        String error = manager.join(player, arenaName);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        }
    }

    private void list(CommandSender sender) {
        if (manager.arenas().isEmpty()) {
            Chat.send(sender, "&7还没有场地。");
            return;
        }
        Chat.send(sender, "&d场地列表：");
        for (DontDoItArena arena : manager.arenas().values()) {
            Game game = manager.game(arena.getName());
            String state = game == null ? "?" : game.state().name();
            int players = game == null ? 0 : game.playerCount() + game.playingCount();
            Chat.send(sender, "&7- &f" + arena.getName() + " &8[" + state + "] &7" + players + " 人"
                    + " &8中心 " + (arena.center() == null ? "未设" : arena.center().getBlockX() + ","
                    + arena.center().getBlockY() + "," + arena.center().getBlockZ())
                    + " &8边长 " + arena.side());
        }
    }

    private void stats(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? plugin.getServer().getPlayerExact(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            Chat.send(sender, "&c找不到玩家。");
            return;
        }
        Chat.send(sender, "&e" + target.getName() + " &7的不要做挑战胜场：&f" + manager.winsOf(target.getUniqueId()));
    }

    private void custom(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/ddi custom <词条内容>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String error = manager.setCustomCriteria(player, text);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        }
    }

    private void settings(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("diamond")) {
            if (!sender.isOp() && !sender.hasPermission("ddi.admin")) {
                Chat.send(sender, "&c没有权限。");
                return;
            }
            String value = args.length > 2 ? args[2] : "toggle";
            if (value.equalsIgnoreCase("add")) {
                manager.setDiamondBehavior("add_self_life");
            } else if (value.equalsIgnoreCase("reduce")) {
                manager.setDiamondBehavior("reduce_others_life");
            } else {
                manager.setDiamondBehavior("add_self_life".equalsIgnoreCase(manager.diamondBehavior())
                        ? "reduce_others_life" : "add_self_life");
            }
            Chat.send(sender, "&a钻石行为：" + manager.diamondBehavior());
            return;
        }
        if (args.length >= 2) {
            if (!sender.isOp() && !sender.hasPermission("ddi.admin")) {
                Chat.send(sender, "&c没有权限。");
                return;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "custom" -> manager.setCustomCriteriaEnabled(!manager.customCriteriaEnabled());
                case "guess" -> manager.setGuessCriteriaEnabled(!manager.guessCriteriaEnabled());
                case "diamond-behavior" -> manager.setDiamondBehaviorEnabled(!manager.diamondBehaviorEnabled());
                case "nightvision" -> manager.setNightVision(!manager.nightVision());
                case "border" -> manager.setUseWorldBorder(!manager.useWorldBorder());
                case "ylevel" -> manager.setYLevelCriteriaEnabled(!manager.yLevelCriteriaEnabled());
                default -> {
                    Chat.send(sender, "&c未知设置项。");
                    return;
                }
            }
            Chat.send(sender, "&a已切换设置（自定义=" + manager.customCriteriaEnabled()
                    + "，猜词条=" + manager.guessCriteriaEnabled()
                    + "，钻石=" + manager.diamondBehaviorEnabled()
                    + "，夜视=" + manager.nightVision()
                    + "，边界=" + manager.useWorldBorder()
                    + "，Y高度=" + manager.yLevelCriteriaEnabled() + "）");
            return;
        }
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c控制台请用 /ddi settings <项>。");
            return;
        }
        if (!player.isOp() && !player.hasPermission("ddi.admin")) {
            Chat.send(sender, "&c只有管理员能修改设置。");
            return;
        }
        manager.settingsMenu().open(player);
    }

    // ---------- 建场 ----------

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c请玩家执行。");
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/ddi create <场地名>");
            return;
        }
        String name = args[1];
        String error = manager.createArena(name);
        if (error != null) {
            Chat.send(sender, "&c" + error);
            return;
        }
        DontDoItArena arena = manager.arena(name);
        arena.setCenter(player.getLocation());
        arena.setLobby(player.getLocation());
        arena.setSpectator(player.getLocation().clone().add(0, 12, 0));
        manager.saveArenas();
        Chat.send(sender, "&a已创建场地 &f" + name + "&a，中心/等待点设在你脚下。");
        Chat.send(sender, "&7下一步：/ddi setarea " + name + " 64，搭好石层后再调整中心。");
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/ddi remove <场地名>");
            return;
        }
        if (!manager.hasArena(args[1])) {
            Chat.send(sender, "&c场地不存在。");
            return;
        }
        manager.removeArena(args[1]);
        Chat.send(sender, "&a已删除场地 &f" + args[1]);
    }

    private void setCenter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        arena.setCenter(player.getLocation());
        manager.saveArenas();
        Chat.send(sender, "&a场地 &f" + arena.getName() + " &a中心已设为 "
                + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY()
                + "," + player.getLocation().getBlockZ());
    }

    private void setArea(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        if (args.length < 3) {
            Chat.send(sender, "&c用法：/ddi setarea <场地> <边长>");
            return;
        }
        try {
            arena.setAreaSize(Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            Chat.send(sender, "&c边长必须是整数。");
            return;
        }
        manager.saveArenas();
        Chat.send(sender, "&a场地 &f" + arena.getName() + " &a边长设为 " + arena.side() + "。");
    }

    private void setLobby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        arena.setLobby(player.getLocation());
        manager.saveArenas();
        Chat.send(sender, "&a等待点已设在你脚下。");
    }

    private void setSpectator(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        arena.setSpectator(player.getLocation());
        manager.saveArenas();
        Chat.send(sender, "&a观战点已设在你脚下。");
    }

    /** 只跑一遍地形生成，用来事先确认石层位置（不会开局）。 */
    private void gen(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        if (arena.world() == null) {
            Chat.send(sender, "&c世界不存在：" + arena.getWorldName());
            return;
        }
        long start = System.currentTimeMillis();
        int stoneY = ArenaGenerator.generate(arena, manager.oreRules(), manager.bedrockDepth(), manager.random());
        long cost = System.currentTimeMillis() - start;
        if (stoneY == Integer.MIN_VALUE) {
            Chat.send(sender, "&c中心往下 30 格内找不到石层（需要至少 25% 石头类方块），生成失败。");
            return;
        }
        arena.setStoneY(stoneY);
        Chat.send(sender, "&a生成完成：石层 Y=" + stoneY + "，基岩 Y="
                + (stoneY - manager.bedrockDepth() + 1) + "，耗时 " + cost + " ms。");
    }

    /** 查看场地所在世界的 PvP / 刷怪 / 死亡掉落规则。 */
    private void rules(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        org.bukkit.World world = arena.world();
        if (world == null) {
            Chat.send(sender, "&c世界不存在：" + arena.getWorldName());
            return;
        }
        Chat.send(sender, "&e" + arena.getName() + " &7世界 " + world.getName() + "：");
        Chat.send(sender, "&7PvP=" + (world.getPVP() ? "&a开" : "&c关")
                + "&7，刷怪=" + boolText(world.getGameRuleValue(org.bukkit.GameRule.DO_MOB_SPAWNING))
                + "&7，保留背包=" + boolText(world.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)));
    }

    private static String boolText(Boolean value) {
        return Boolean.TRUE.equals(value) ? "&a开" : "&c关";
    }

    /** 把当前场地存成基线（之后每局开始/结束都会还原到这份状态）。 */
    private void saveMap(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        if (arena.world() == null) {
            Chat.send(sender, "&c世界不存在：" + arena.getWorldName());
            return;
        }
        int stoneY = ArenaGenerator.detectStoneY(arena);
        if (stoneY == Integer.MIN_VALUE) {
            Chat.send(sender, "&c找不到石层，先 /ddi gen 检查场地。");
            return;
        }
        int bottom = ArenaGenerator.bedrockY(stoneY, manager.bedrockDepth());
        int top = Math.min(arena.world().getMaxHeight() - 1,
                Math.max(arena.center().getBlockY(), stoneY) + manager.restoreTopOffset());
        if (top <= bottom) {
            Chat.send(sender, "&c基线范围为空（检查 restore-top-offset）。");
            return;
        }
        long start = System.currentTimeMillis();
        int pad = manager.useWorldBorder() ? manager.worldBorderPadding() : 0;
        int width = arena.side() + pad * 2;
        MapBaseline baseline = MapBaseline.capture(arena.world(),
                arena.center().getBlockX() - width / 2, bottom,
                arena.center().getBlockZ() - width / 2, width, top - bottom + 1);
        manager.saveBaseline(arena, baseline);
        Game game = manager.game(arena.getName());
        if (game != null) {
            game.invalidateBaseline();
        }
        Chat.send(sender, "&a已保存地图基线：&f" + baseline.volume() + " &a个方块 + &f"
                + baseline.entityCount() + " &a个实体（Y " + bottom + "~" + top + "），耗时 "
                + (System.currentTimeMillis() - start) + " ms，" + manager.baselineFile(arena).getName());
    }

    /** 运维自检：不需要真人，跑一遍开局→触发→淘汰→结算→重置。 */
    private void selfTest(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        Game game = manager.game(arena.getName());
        if (game == null) {
            Chat.send(sender, "&c场地未加载。");
            return;
        }
        Chat.send(sender, "&7开始自检 &f" + arena.getName() + "&7…");
        String result = game.selfTest();
        Chat.send(sender, "&e" + result);
    }

    private DontDoItArena arenaArg(CommandSender sender, String[] args) {        if (args.length < 2) {
            Chat.send(sender, "&c请指定场地名（/ddi list 查看）。");
            return null;
        }
        DontDoItArena arena = manager.arena(args[1]);
        if (arena == null) {
            Chat.send(sender, "&c场地不存在：" + args[1]);
        }
        return arena;
    }

    // ---------- 对局控制 ----------

    private void start(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        Game game = manager.game(arena.getName());
        if (game == null) {
            return;
        }
        game.autoAssignTeams();
        if (game.start(sender)) {
            Chat.send(sender, "&a对局开始。");
        }
    }

    private void stop(CommandSender sender, String[] args) {
        DontDoItArena arena = arenaArg(sender, args);
        if (arena == null) {
            return;
        }
        Game game = manager.game(arena.getName());
        if (game != null) {
            game.reset();
            Chat.send(sender, "&a已结束并重置场地。");
        }
    }

    private void change(CommandSender sender, String[] args) {
        Team team = teamArg(sender, args);
        if (team == null) {
            return;
        }
        Game game = manager.game(teamGameName(team));
        if (game == null) {
            return;
        }
        game.changeCriterion(team, true);
        Chat.send(sender, "&a已更换词条：" + team.getCriterion().name);
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Chat.send(sender, "&c用法：/ddi grant <场地> <队伍> <词条名>");
            return;
        }
        Team team = teamArg(sender, args);
        if (team == null) {
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        CriterionLibrary library = manager.library();
        Criterion criterion = library.findByName(name);
        if (criterion == null) {
            Chat.send(sender, "&c词条库里没有：" + name);
            return;
        }
        Game game = manager.game(args[1]);
        if (game != null) {
            game.forceCriterion(team, criterion);
        } else {
            team.setCriterion(criterion, 0);
        }
        Chat.send(sender, "&a已把 " + team.getColor().name + " 的词条设为 " + criterion.name);
    }

    private void setLife(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Chat.send(sender, "&c用法：/ddi setlife <场地> <队伍> <数量>");
            return;
        }
        Team team = teamArg(sender, args);
        if (team == null) {
            return;
        }
        try {
            int value = Integer.parseInt(args[3]);
            team.setLives(value);
            if (value <= 0) {
                Game game = manager.game(args[1]);
                if (game != null) {
                    game.eliminate(team);
                }
            }
            Game game = manager.game(args[1]);
            if (game != null) {
                game.updateSidebar(true);
            }
            Chat.send(sender, "&a已设置 " + team.getColor().name + " 的规则数量为 " + value);
        } catch (NumberFormatException e) {
            Chat.send(sender, "&c数量必须是整数。");
        }
    }

    private void trigger(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能触发自定义词条。");
            return;
        }
        if (args.length < 2) {
            Chat.send(sender, "&c用法：/ddi trigger <队伍>");
            return;
        }
        Team team = teamByName(manager.gameOf(player), args[1]);
        if (team == null) {
            Chat.send(sender, "&c你不在对局中，或队伍不存在。");
            return;
        }
        if (!manager.gameOf(player).triggerCustom(team, player)) {
            Chat.send(sender, "&c这支队伍当前不是自定义词条。");
        }
    }

    private void guess(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.send(sender, "&c只有玩家能猜词条。");
            return;
        }
        if (args.length < 3) {
            Chat.send(sender, "&c用法：/ddi guess <队伍> <true|false>");
            return;
        }
        Game game = manager.gameOf(player);
        if (game == null) {
            Chat.send(sender, "&c你不在对局中。");
            return;
        }
        Team team = teamByName(game, args[1]);
        if (team == null) {
            Chat.send(sender, "&c队伍不存在。");
            return;
        }
        boolean correct = Boolean.parseBoolean(args[2]);
        String error = game.guess(player, team, correct);
        if (error != null) {
            Chat.send(sender, "&c" + error);
        }
    }

    private void words(CommandSender sender, String[] args) {
        Chat.send(sender, "&d词条库共 " + manager.library().all().size() + " 条。");
        if (args.length > 1) {
            CriterionLibrary library = manager.library();
            List<Criterion> hits = new ArrayList<>();
            for (Criterion c : library.all()) {
                if (c.name.contains(args[1])) {
                    hits.add(c);
                }
            }
            for (int i = 0; i < Math.min(20, hits.size()); i++) {
                Criterion c = hits.get(i);
                Chat.send(sender, "&7- &f" + c.name + " &8[" + c.type.id + " / " + c.difficulty.id + "]");
            }
            if (hits.size() > 20) {
                Chat.send(sender, "&8...还有 " + (hits.size() - 20) + " 条");
            }
        }
    }

    // ---------- 参数解析 ----------

    private Team teamArg(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Chat.send(sender, "&c用法：/ddi " + args[0] + " <场地> <队伍>");
            return null;
        }
        Game game = manager.game(args[1]);
        if (game == null) {
            Chat.send(sender, "&c场地不存在：" + args[1]);
            return null;
        }
        Team team = teamByName(game, args[2]);
        if (team == null) {
            Chat.send(sender, "&c队伍不存在：" + args[2]);
        }
        return team;
    }

    private String teamGameName(Team team) {
        for (Game game : manager.games().values()) {
            if (game.teams().contains(team)) {
                return game.arena().getName();
            }
        }
        return null;
    }

    private Team teamByName(Game game, String name) {
        if (game == null) {
            return null;
        }
        for (Team t : game.teams()) {
            if (t.getColor().id.equalsIgnoreCase(name) || t.getColor().name.equals(name)) {
                return t;
            }
        }
        return null;
    }

    // ---------- Tab ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("help", "join", "leave", "list", "stats", "custom", "guess",
                    "trigger", "settings", "create", "remove", "setcenter", "setarea", "setlobby",
                    "setspectator", "save", "reload", "start", "stop", "change", "grant", "setlife", "words")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2) {
            for (String name : manager.arenas().keySet()) {
                if (name.startsWith(args[1])) {
                    out.add(name);
                }
            }
            return out;
        }
        if (args.length == 3) {
            Game game = manager.game(args[1]);
            if (game != null) {
                for (Team t : game.teams()) {
                    if (t.getColor().id.startsWith(args[2])) {
                        out.add(t.getColor().id);
                    }
                }
            }
            return out;
        }
        return out;
    }
}
