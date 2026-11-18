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
    /** 自助领酒指令的冷却到期时间（毫秒）。 */
    private final Map<UUID, Long> liquorCooldowns = new HashMap<>();
    /** 刚喝完酒的玩家暂时不能扔空瓶（防止喝着喝着顺手把瓶子扔出去）。 */
    private final Map<UUID, Long> throwLocks = new HashMap<>();

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
        HubAPI.setArenaSpectator(GAME, table.getName(), table.spectatorPoint());
    }

    /** 离某个位置最近的桌子（主城观战者只看离自己最近那张桌子的对局栏）；没有桌子返回 null。 */
    public LiarBarTable nearestTable(Location loc) {
        if (loc == null) {
            return null;
        }
        LiarBarTable best = null;
        double bestDist = Double.MAX_VALUE;
        for (LiarBarTable table : tables.values()) {
            Location center = table.getCenter();
            if (center == null || center.getWorld() != loc.getWorld()) {
                continue;
            }
            double dist = center.distanceSquared(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = table;
            }
        }
        return best;
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

    /** 这块按钮是不是某张桌子座椅对应的开火按钮。 */
    public LiarBarTable tableAtButton(Block block) {
        if (block == null) {
            return null;
        }
        for (LiarBarTable table : tables.values()) {
            if (table.hasButton(block)) {
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
        String currentGame = HubAPI.gameOf(id);
        // 已经通过别的方式加入了其它游戏（例如在区域里用指南针进别的游戏）：
        // 不再算在酒馆里，也绝不要 clearGame，否则会把那个游戏的 tab 状态清掉。
        if (currentGame != null && !currentGame.equals(GAME)) {
            if (inRoom.remove(id)) {
                clearLiquorItems(player);
            }
            return;
        }
        boolean seated = seating.containsKey(id);
        boolean inside = seated || room.inRoom(player.getLocation());
        if (inside && !inRoom.contains(id)) {
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
        clearLiquorItems(player);
        inRoom.remove(id);
        // 只清理自己上报的 tab 状态，别把刚加入的其它游戏状态清掉
        if (GAME.equals(HubAPI.gameOf(id))) {
            HubAPI.clearGame(id);
        }
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

    /**
     * 退出对局。{@code fromHub} = true 表示玩家点了回主城（由 HubCore 负责传送），
     * 否则只退出牌局、留在酒馆里，不传送回大厅。
     */
    public void leave(Player player, boolean fromHub) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        LiarBarTable table = seating.get(id);
        boolean inside = inRoom.contains(id) || table != null;
        if (!inside) {
            if (!fromHub) {
                Chat.send(player, "&7你不在骗子酒馆里。");
            }
            return;
        }

        if (table != null) {
            table.removePlayer(player);
            restoreSnapshot(player);
        } else if (!fromHub) {
            Chat.send(player, "&7你没有在对局中；走出区域即可离开酒馆。");
            return;
        }

        if (fromHub || !room.inRoom(player.getLocation())) {
            clearLiquorItems(player);
            inRoom.remove(id);
            if (GAME.equals(HubAPI.gameOf(id))) {
                HubAPI.clearGame(id);
            }
            if (!fromHub) {
                Chat.send(player, "&e你已退出骗子酒馆。");
            }
        } else {
            // 人还在酒馆区域里：只退出牌局，继续算在酒馆等待
            HubAPI.setGame(id, GAME);
            Chat.send(player, "&e你已退出当前牌局，仍留在酒馆里；走出区域即可离开。");
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
        liquorCooldowns.remove(id);
        throwLocks.remove(id);
        LiarBarTable table = seating.get(id);
        if (table != null) {
            table.removePlayer(player);
        }
        restoreSnapshot(player);
        inRoom.remove(id);
    }

    public void sitOrStand(Player player, LiarBarTable table) {
        sitOrStand(player, table, null);
    }

    /** chairBlock 为右键点击的那把椅子；右键哪把就坐哪把。 */
    public void sitOrStand(Player player, LiarBarTable table, Block chairBlock) {
        LiarBarTable current = seating.get(player.getUniqueId());
        if (current == table) {
            table.standUp(player);
            return;
        }
        if (current != null) {
            Chat.actionBar(player, "&c你已经坐在「" + current.getName() + "」了，先右键起身。");
            return;
        }
        if (table.sit(player, chairBlock)) {
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

    /**
     * 每 tick 兜底一次：切换手持物品时原版会先把真实牌面发给附近玩家，
     * 这里在下一 tick 立刻重发伪装，避免闪出真实道具。
     */
    public void tickDisguises() {
        if (seating.isEmpty()) {
            return;
        }
        ItemStack paper = items.paperDisguise();
        for (UUID id : seating.keySet()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            ItemStack held = target.getInventory()
                    .getItem(target.getInventory().getHeldItemSlot());
            if (held == null || held.getType().isAir() || !items.isConcealable(held)) {
                continue;
            }
            for (Player viewer : target.getWorld().getPlayers()) {
                if (viewer.equals(target)) {
                    continue;
                }
                if (viewer.getLocation().distanceSquared(target.getLocation()) > 64 * 64) {
                    continue;
                }
                viewer.sendEquipmentChange(target, EquipmentSlot.HAND, paper);
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

    /** 玩家自助领酒：带冷却，成功返回 0，失败返回剩余秒数。 */
    public long giveLiquorToSelf(Player player) {
        if (player == null) {
            return 0;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = liquorCooldowns.get(id);
        if (until != null && until > now) {
            return (until - now + 999) / 1000;
        }
        giveLiquor(player, 1);
        liquorCooldowns.put(id, now + liquor.giveCooldownSeconds() * 1000L);
        return 0;
    }

    /** 离开酒馆区域时清掉身上的所有酒和空酒瓶。 */
    public void clearLiquorItems(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (items.isLiquor(item) || items.isBottle(item)) {
                inv.setItem(slot, null);
                changed = true;
            }
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (items.isLiquor(offhand) || items.isBottle(offhand)) {
            inv.setItemInOffHand(null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * 玩家用原版「喝」动作喝完酒（PlayerItemConsumeEvent）后触发：
     * 取消原版消耗与药水效果，手动消耗一瓶、给空酒瓶、施加随机反胃。
     */
    public void finishDrinking(Player player, EquipmentSlot hand) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        boolean offhand = hand == EquipmentSlot.OFF_HAND;
        ItemStack inHand = offhand ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (!items.isLiquor(inHand)) {
            return;
        }
        if (inHand.getAmount() > 1) {
            inHand.setAmount(inHand.getAmount() - 1);
        } else if (offhand) {
            inv.setItemInOffHand(null);
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
        // 刚喝完的 0.7 秒内不响应空瓶右键，避免端着瓶子顺手把它扔出去
        throwLocks.put(player.getUniqueId(), System.currentTimeMillis() + 700L);
        player.updateInventory();
        refreshDisguise(player);
    }

    /** 右键扔空酒瓶。 */
    public void throwBottle(Player player) {
        UUID id = player.getUniqueId();
        Long lock = throwLocks.get(id);
        if (lock != null) {
            if (lock > System.currentTimeMillis()) {
                return; // 刚喝完，需要重新点一次右键
            }
            throwLocks.remove(id);
        }
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

    /** 空酒瓶砸中玩家：没落座才吃击退；坐着的只吃受击变红（不扣血）。 */
    public void bottleHit(UUID throwerId, Player target) {
        if (target == null || !target.isOnline() || !inRoom(target)) {
            return;
        }
        boolean seated = tableOf(target) != null;
        if (seated) {
            Chat.actionBar(target, "&c你坐在椅子上，被空酒瓶砸中了！");
        } else {
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
            Chat.actionBar(target, "&c被空酒瓶砸中了！");
        }

        double damage = liquor.bottleDamage();
        if (damage > 0) {
            double health = target.getHealth();
            target.setNoDamageTicks(0);
            allowNextDamage(target.getUniqueId());
            try {
                target.damage(damage);
            } finally {
                clearDamageAllowed(target.getUniqueId());
            }
            if (seated) {
                // 坐着只保留受击动画，不真的扣血
                target.setHealth(Math.max(0.5, Math.min(health, target.getMaxHealth())));
            }
        }
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
            HubAPI.setArenaSpectator(GAME, tableName, null);
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
