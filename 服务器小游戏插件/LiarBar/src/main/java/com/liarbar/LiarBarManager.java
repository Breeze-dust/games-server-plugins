package com.liarbar;

import com.hub.api.HubAPI;
import com.hub.data.PlayerStats;
import com.hub.util.Chat;
import com.hub.util.Top3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 房间、桌子、玩家进出的总调度。 */
public final class LiarBarManager {

    public static final String GAME = "骗子酒馆";

    private final LiarBarPlugin plugin;
    private final LiarBarSettings settings = new LiarBarSettings();
    private final LiarBarRoom room;
    private final LiarBarItems items;
    private final LiarBarLiquor liquor;
    private final Map<String, LiarBarTable> tables = new LinkedHashMap<>();
    private final Map<UUID, LiarBarTable> seating = new HashMap<>();
    private final Set<UUID> inRoom = new HashSet<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Set<UUID> forceDismounts = new HashSet<>();
    /** 打开「建场模式」的玩家：在房间区域内可以破坏/放置方块。 */
    private final Set<UUID> builders = new HashSet<>();
    /** 在建场模式前保存的游戏模式，关闭时还原。 */
    private final Map<UUID, GameMode> buildPreviousMode = new HashMap<>();
    /** 下一次伤害事件要放行的玩家（岩浆灼烧 / 酒瓶砸人的受击动画）。 */
    private final Set<UUID> damageAllowed = new HashSet<>();
    /** 扔出去的空酒瓶 -> 投掷者。 */
    private final Map<UUID, UUID> thrownBottles = new HashMap<>();
    /** 正在喝酒的玩家。 */
    private final Map<UUID, Drinking> drinking = new HashMap<>();

    private YamlConfiguration stats;
    private int loadRetries;

    public LiarBarManager(LiarBarPlugin plugin) {
        this.plugin = plugin;
        this.room = new LiarBarRoom(plugin);
        this.items = new LiarBarItems(plugin);
        this.liquor = new LiarBarLiquor(plugin, items);
    }

    // ---------- 配置加载 ----------

    public void load() {
        plugin.reloadConfig();
        settings.load(plugin.getConfig());
        room.load(plugin.getConfig());
        liquor.load();
        rebuildTables();
        if (room.unresolved() && loadRetries++ < 30) {
            Bukkit.getScheduler().runTaskLater(plugin, this::load, 200L);
        } else if (room.unresolved()) {
            plugin.getLogger().warning("骗子酒馆：配置里的世界还没有加载，房间/椅子暂时不可用。");
        }
    }

    private void rebuildTables() {
        for (LiarBarTable table : tables.values()) {
            table.shutdown();
        }
        tables.clear();
        for (Map.Entry<String, List<Location>> entry : room.tables().entrySet()) {
            tables.put(entry.getKey(),
                    new LiarBarTable(plugin, this, settings, entry.getKey(), entry.getValue()));
        }
        reportAll();
    }

    public boolean reload() {
        for (LiarBarTable table : tables.values()) {
            if (table.getState() != LiarBarTable.State.WAITING) {
                return false;
            }
            HubAPI.removeArena(GAME, table.getName());
        }
        loadRetries = 0;
        load();
        return true;
    }

    private void reportAll() {
        for (LiarBarTable table : tables.values()) {
            reportArena(table);
        }
    }

    public void reportArena(LiarBarTable table) {
        HubAPI.setArena(GAME, table.getName(), table.hubState(),
                table.occupiedCount(), table.hubExtra());
    }

    /** 每 0.5 秒检查一次所有桌子的开局条件（准备状态变化不会直接回调到插件）。 */
    public void tickTables() {
        for (LiarBarTable table : tables.values()) {
            table.evaluateStart();
        }
    }

    public LiarBarSettings settings() {
        return settings;
    }

    public LiarBarItems items() {
        return items;
    }

    public LiarBarLiquor liquor() {
        return liquor;
    }

    public LiarBarRoom room() {
        return room;
    }

    public Map<String, LiarBarTable> tables() {
        return tables;
    }

    // ---------- 进出房间（按区域自动识别） ----------

    public boolean inRoom(Player player) {
        return player != null && inRoom.contains(player.getUniqueId());
    }

    public LiarBarTable tableOf(Player player) {
        return player == null ? null : seating.get(player.getUniqueId());
    }

    public LiarBarTable tableAt(Block block) {
        if (block == null) {
            return null;
        }
        for (LiarBarTable table : tables.values()) {
            if (table.hasChair(block)) {
                return table;
            }
        }
        return null;
    }

    /** 玩家移动时调用：走进 pos1~pos2 区域自动加入，走出去自动退出。 */
    public void checkRegion(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        boolean seated = seating.containsKey(id);
        boolean inside = seated || room.inRoom(player.getLocation());
        if (inside && !inRoom.contains(id)) {
            String currentGame = HubAPI.gameOf(id);
            if (currentGame != null && !currentGame.equals(GAME)) {
                return;
            }
            enterRegion(player);
        } else if (!inside && inRoom.contains(id) && !seated) {
            exitRegion(player);
        }
    }

    private void enterRegion(Player player) {
        UUID id = player.getUniqueId();
        inRoom.add(id);
        HubAPI.setGame(id, GAME);
        Chat.send(player, "&a你已进入&6骗子酒馆&a：右键楼梯椅子坐下，再右键准备道具就能开局。");
        Chat.actionBar(player, "&e右键楼梯椅子入座");
        refreshDisguises();
    }

    private void exitRegion(Player player) {
        UUID id = player.getUniqueId();
        cancelDrinking(id, true);
        inRoom.remove(id);
        HubAPI.clearGame(id);
        Chat.send(player, "&7你离开了骗子酒馆。");
    }

    /** /liarbar join：没在区域里就先传送到等待点。 */
    public void enter(Player player, String tableName) {
        if (!room.configured()) {
            Chat.send(player, "&c骗子酒馆还没配置好：OP 需要先设置房间等待点并登记至少一张桌子。");
            return;
        }
        UUID id = player.getUniqueId();
        String currentGame = HubAPI.gameOf(id);
        if (currentGame != null && !currentGame.equals(GAME)) {
            Chat.send(player, "&c你正在「" + currentGame + "」里，先回主城再来骗子酒馆。");
            return;
        }
        if (!room.inRoom(player.getLocation()) && room.spawn() != null) {
            player.teleport(room.spawn());
        }
        checkRegion(player);
        if (tableName != null && !tableName.isEmpty()) {
            sitNamed(player, tableName);
        }
    }

    private void sitNamed(Player player, String tableName) {
        LiarBarTable table = tables.get(tableName);
        if (table == null) {
            Chat.send(player, "&c没有叫「" + tableName + "」的桌子。");
            return;
        }
        sitOrStand(player, table);
    }

    public void leave(Player player, boolean silent) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        LiarBarTable table = seating.get(id);
        boolean inside = inRoom.contains(id) || table != null;
        if (!inside) {
            if (!silent) {
                Chat.send(player, "&7你不在骗子酒馆里。");
            }
            return;
        }
        cancelDrinking(id, true);
        if (table != null) {
            table.removePlayer(player);
        }
        restoreSnapshot(player);
        inRoom.remove(id);
        HubAPI.clearGame(id);
        if (!silent) {
            HubAPI.teleportToLobby(player);
            Chat.send(player, "&e已离开骗子酒馆。");
        }
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        builders.remove(id);
        buildPreviousMode.remove(id);
        damageAllowed.remove(id);
        cancelDrinking(id, true);
        LiarBarTable table = seating.get(id);
        if (table != null) {
            table.removePlayer(player);
        }
        restoreSnapshot(player);
        inRoom.remove(id);
    }

    public void sitOrStand(Player player, LiarBarTable table) {
        LiarBarTable current = seating.get(player.getUniqueId());
        if (current == table) {
            table.standUp(player);
            return;
        }
        if (current != null) {
            Chat.actionBar(player, "&c你已经坐在「" + current.getName() + "」了，先右键起身。");
            return;
        }
        if (table.sit(player)) {
            Chat.send(player, "&a你坐到了「" + table.getName() + "」的椅子上。");
        }
    }

    public void bind(Player player, LiarBarTable table) {
        seating.put(player.getUniqueId(), table);
    }

    public void unbind(UUID id) {
        seating.remove(id);
    }

    public void markForceDismount(UUID id) {
        forceDismounts.add(id);
    }

    public void clearForceDismount(UUID id) {
        forceDismounts.remove(id);
    }

    public boolean isForceDismount(UUID id) {
        return forceDismounts.contains(id);
    }

    /** 切换建场模式：开启后在房间区域内允许破坏/放置方块。 */
    public boolean toggleBuild(Player player) {
        UUID id = player.getUniqueId();
        if (builders.remove(id)) {
            GameMode previous = buildPreviousMode.remove(id);
            if (previous != null) {
                player.setGameMode(previous);
            }
            return false;
        }
        builders.add(id);
        buildPreviousMode.put(id, player.getGameMode());
        player.setGameMode(GameMode.CREATIVE);
        return true;
    }

    public boolean isBuilder(Player player) {
        return player != null && builders.contains(player.getUniqueId());
    }

    public void clearBuild(Player player) {
        if (player != null) {
            builders.remove(player.getUniqueId());
            buildPreviousMode.remove(player.getUniqueId());
        }
    }

    /** 放行一次伤害事件（岩浆灼烧时让其他玩家看到受击变红）。 */
    public void allowNextDamage(UUID id) {
        damageAllowed.add(id);
    }

    public boolean consumeDamageAllowed(UUID id) {
        return damageAllowed.remove(id);
    }

    public void clearDamageAllowed(UUID id) {
        damageAllowed.remove(id);
    }

    // ---------- 背包快照（只在开局时保存，结束后还原） ----------

    public void captureSnapshot(Player player) {
        snapshots.computeIfAbsent(player.getUniqueId(), k -> Snapshot.capture(player));
    }

    public void restoreSnapshot(Player player) {
        if (player == null) {
            return;
        }
        Snapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player);
        }
        refreshDisguise(player);
    }

    // ---------- 手牌伪装（别人看你手里只是一张纸） ----------

    /** 把某个玩家当前手持物品按「是否手牌」伪装后发给所有其他玩家。 */
    public void refreshDisguise(Player target) {
        if (target == null || !target.isOnline()) {
            return;
        }
        ItemStack held = target.getInventory()
                .getItem(target.getInventory().getHeldItemSlot());
        ItemStack shown = items.isConcealable(held) ? items.paperDisguise() : held;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                viewer.sendEquipmentChange(target, EquipmentSlot.HAND, shown);
            }
        }
    }

    /** 刷新所有入座玩家的伪装（进服、进区域、发牌、出牌后调用）。 */
    public void refreshDisguises() {
        for (UUID id : seating.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                refreshDisguise(player);
            }
        }
    }

    // ---------- 酒与空瓶 ----------

    /** 给玩家发若干瓶随机酒（指令用）。 */
    public void giveLiquor(Player target, int amount) {
        if (target == null) {
            return;
        }
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> leftover =
                    target.getInventory().addItem(liquor.randomLiquor());
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
        }
    }

    /** 右键酒：开始一个持续数秒的喝酒过程（期间移动/换手会打断）。 */
    public void beginDrinking(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (drinking.containsKey(id)) {
            return;
        }
        if (!items.isLiquor(player.getInventory().getItemInMainHand())) {
            return;
        }
        Drinking state = new Drinking(player.getLocation().clone(), liquor.drinkTicks(),
                player.getLevel(), player.getExp());
        drinking.put(id, state);
        Chat.actionBar(player, "&6开始喝酒… &7（移动或换手会打断）");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.0f);
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickDrinking(id), 1L, 1L);
    }

    private void tickDrinking(UUID id) {
        Drinking state = drinking.get(id);
        if (state == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline() || !inRoom(player)
                || player.getWorld() != state.start.getWorld()
                || player.getLocation().distanceSquared(state.start) > 0.09
                || !items.isLiquor(player.getInventory().getItemInMainHand())) {
            cancelDrinking(id, true);
            if (player != null && player.isOnline()) {
                Chat.actionBar(player, "&7喝酒被打断了。");
            }
            return;
        }
        state.elapsed++;
        float progress = Math.min(1.0f, (float) state.elapsed / state.totalTicks);
        player.setExp(progress);
        player.setLevel((int) (progress * 100));
        Chat.actionBar(player, "&6正在喝酒… &f" + (int) (progress * 100) + "%");
        if (state.elapsed % 8 == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.4f, 1.0f);
            player.getWorld().spawnParticle(Particle.CRIT,
                    player.getLocation().add(0, 1.1, 0), 4, 0.15, 0.15, 0.15, 0.02);
        }
        if (state.elapsed >= state.totalTicks) {
            int level = state.savedLevel;
            float exp = state.savedExp;
            cancelDrinking(id, false);
            finishDrinking(player, level, exp);
        }
    }

    private void cancelDrinking(UUID id, boolean restoreExp) {
        Drinking state = drinking.remove(id);
        if (state == null) {
            return;
        }
        if (state.task != null) {
            state.task.cancel();
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) {
            if (restoreExp) {
                player.setLevel(state.savedLevel);
                player.setExp(state.savedExp);
            }
            player.updateInventory();
        }
    }

    /** 喝酒完成：消耗一瓶、留下空瓶、随机反胃。 */
    private void finishDrinking(Player player, int savedLevel, float savedExp) {
        player.setLevel(savedLevel);
        player.setExp(savedExp);
        PlayerInventory inv = player.getInventory();
        ItemStack inHand = inv.getItemInMainHand();
        if (!items.isLiquor(inHand)) {
            return;
        }
        if (inHand.getAmount() > 1) {
            inHand.setAmount(inHand.getAmount() - 1);
        } else {
            inv.setItemInMainHand(null);
        }
        Map<Integer, ItemStack> leftover = inv.addItem(items.emptyBottle());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        int ticks = liquor.randomNauseaTicks();
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks, 0, true, true));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 0.8f);
        Chat.actionBar(player, "&6咕嘟咕嘟… &7反胃 " + (ticks / 20) + " 秒");
        refreshDisguise(player);
    }

    public void cancelDrink(Player player) {
        if (player != null) {
            cancelDrinking(player.getUniqueId(), true);
        }
    }

    /** 正在进行的喝酒过程。 */
    private static final class Drinking {
        final Location start;
        final int totalTicks;
        final int savedLevel;
        final float savedExp;
        BukkitTask task;
        int elapsed;

        Drinking(Location start, int totalTicks, int savedLevel, float savedExp) {
            this.start = start;
            this.totalTicks = totalTicks;
            this.savedLevel = savedLevel;
            this.savedExp = savedExp;
        }
    }

    /** 右键扔空酒瓶。 */
    public void throwBottle(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack inHand = inv.getItemInMainHand();
        if (!items.isBottle(inHand)) {
            return;
        }
        if (inHand.getAmount() > 1) {
            inHand.setAmount(inHand.getAmount() - 1);
        } else {
            inv.setItemInMainHand(null);
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Snowball snowball = player.getWorld().spawn(
                eye.clone().add(direction.clone().multiply(0.6)), Snowball.class, ball -> {
                    ball.setShooter(player);
                    ball.setVelocity(direction.multiply(liquor.throwSpeed()));
                    ball.setItem(new ItemStack(Material.GLASS_BOTTLE));
                });
        thrownBottles.put(snowball.getUniqueId(), player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> thrownBottles.remove(snowball.getUniqueId()), 200L);
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);
        refreshDisguise(player);
    }

    public UUID takeThrownBottle(UUID projectile) {
        return thrownBottles.remove(projectile);
    }

    /** 空酒瓶砸中玩家：没落座才吃击退，并且触发受击变红。 */
    public void bottleHit(UUID throwerId, Player target) {
        if (target == null || !target.isOnline() || !inRoom(target)) {
            return;
        }
        if (tableOf(target) != null) {
            Chat.actionBar(target, "&7你坐在椅子上，酒瓶没能推动你。");
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
            return;
        }
        Player thrower = Bukkit.getPlayer(throwerId);
        Vector direction = thrower != null
                ? target.getLocation().toVector().subtract(thrower.getLocation().toVector())
                : target.getLocation().getDirection().multiply(-1);
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vector(0, 0, 1);
        }
        direction = direction.normalize().multiply(liquor.knockbackHorizontal());
        direction.setY(liquor.knockbackVertical());
        target.setVelocity(target.getVelocity().add(direction));

        double damage = liquor.bottleDamage();
        if (damage > 0) {
            target.setNoDamageTicks(0);
            allowNextDamage(target.getUniqueId());
            try {
                target.damage(damage);
            } finally {
                clearDamageAllowed(target.getUniqueId());
            }
        }
        Chat.actionBar(target, "&c被空酒瓶砸中了！");
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0), 16, 0.3, 0.4, 0.3, 0.1);
    }

    // ---------- 管理员建场 ----------

    /** 站在/看着台阶时把它登记为某张桌子的一把椅子。 */
    public boolean addChair(Player player, String tableName) {
        Block block = blockUnder(player);
        if (block == null) {
            Chat.send(player, "&c请站在楼梯方块上（或看着 5 格内的楼梯）再执行。"
                    + "注意：半砖（台阶）不算，需要的是橡木楼梯这类楼梯方块。");
            return false;
        }
        if (room.isChair(block)) {
            Chat.send(player, "&c这个方块已经是某张桌子的椅子了。");
            return false;
        }
        LiarBarTable existing = tables.get(tableName);
        if (existing != null) {
            if (existing.getState() != LiarBarTable.State.WAITING) {
                Chat.send(player, "&c「" + tableName + "」正在对局中，不能改椅子。");
                return false;
            }
            if (existing.occupiedCount() > 0) {
                Chat.send(player, "&c「" + tableName + "」已经有人入座了，等人起身再登记椅子。");
                return false;
            }
        }
        if (room.seatsOf(tableName).size() >= settings.playersPerTable) {
            Chat.send(player, "&c「" + tableName + "」已经有 " + settings.playersPerTable + " 把椅子了。");
            return false;
        }
        room.addChair(tableName, block.getLocation());
        room.save();
        List<Location> seats = new ArrayList<>(room.seatsOf(tableName));
        if (existing != null) {
            existing.setSeats(seats);
            reportArena(existing);
        } else {
            LiarBarTable table = new LiarBarTable(plugin, this, settings, tableName, seats);
            tables.put(tableName, table);
            reportArena(table);
        }
        Chat.send(player, "&a已把楼梯 &7(" + block.getX() + ", " + block.getY() + ", " + block.getZ()
                + ")&a 登记为「&e" + tableName + "&a」的第 " + seats.size() + " 把椅子。");
        if (seats.size() < settings.playersPerTable) {
            Chat.send(player, "&7还差 &e" + (settings.playersPerTable - seats.size()) + " &7把椅子。");
        } else {
            Chat.send(player, "&6这张桌子配好了，" + settings.playersPerTable + " 人坐下就会自动开局。");
        }
        return true;
    }

    public boolean deleteTable(String tableName) {
        LiarBarTable table = tables.remove(tableName);
        if (table == null && !room.tables().containsKey(tableName)) {
            return false;
        }
        if (table != null) {
            table.forceStop();
            HubAPI.removeArena(GAME, tableName);
        }
        room.removeTable(tableName);
        room.save();
        return true;
    }

    private Block blockUnder(Player player) {
        Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (Tag.STAIRS.isTagged(below.getType())) {
            return below;
        }
        Block target = player.getTargetBlockExact(5);
        if (target != null && Tag.STAIRS.isTagged(target.getType())) {
            return target;
        }
        return null;
    }

    public void setSpawn(Player player) {
        room.setSpawn(player.getLocation());
        room.save();
    }

    public void setCorner(Player player, int index) {
        if (index == 1) {
            room.setPos1(player.getLocation());
        } else {
            room.setPos2(player.getLocation());
        }
        room.save();
    }

    // ---------- 战绩 ----------

    public void recordWin(UUID winner) {
        try {
            if (stats == null) {
                stats = PlayerStats.load("liarbar", new File(plugin.getDataFolder(), "data.yml"));
            }
            Top3.award(stats, List.of(winner), List.of(), List.of());
            PlayerStats.save("liarbar", stats);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存骗子酒馆战绩失败：" + ex.getMessage());
        }
    }

    // ---------- 关闭 ----------

    public void shutdown() {
        for (LiarBarTable table : tables.values()) {
            table.shutdown();
        }
        for (Drinking state : drinking.values()) {
            if (state.task != null) {
                state.task.cancel();
            }
        }
        drinking.clear();
        for (Map.Entry<UUID, Snapshot> entry : snapshots.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                entry.getValue().restore(p);
            }
        }
        snapshots.clear();
        inRoom.clear();
        seating.clear();
    }

    /** 开局时保存的原背包/状态，结束或离开时还原。 */
    private static final class Snapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final int level;
        private final float exp;
        private final double health;
        private final int food;
        private final float saturation;
        private final GameMode mode;

        private Snapshot(Player p) {
            PlayerInventory inv = p.getInventory();
            contents = inv.getStorageContents().clone();
            armor = inv.getArmorContents().clone();
            offhand = inv.getItemInOffHand();
            level = p.getLevel();
            exp = p.getExp();
            health = p.getHealth();
            food = p.getFoodLevel();
            saturation = p.getSaturation();
            mode = p.getGameMode();
        }

        static Snapshot capture(Player p) {
            return new Snapshot(p);
        }

        void restore(Player p) {
            PlayerInventory inv = p.getInventory();
            inv.setStorageContents(contents);
            inv.setArmorContents(armor);
            inv.setItemInOffHand(offhand);
            p.setLevel(level);
            p.setExp(exp);
            p.setFoodLevel(food);
            p.setSaturation(saturation);
            p.setHealth(Math.max(0.5, Math.min(health, p.getMaxHealth())));
            p.setGameMode(mode);
            p.updateInventory();
        }
    }
}
