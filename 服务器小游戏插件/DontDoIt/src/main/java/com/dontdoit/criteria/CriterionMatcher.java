package com.dontdoit.criteria;

import com.dontdoit.Team;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 词条判定：把 criteria.yml 里每种类型的触发条件翻译成 Bukkit 事件/状态判断。
 * 语义与官方 DontDoIt 的实现保持一致。
 */
public final class CriterionMatcher {

    public CriterionMatcher() {
    }

    // ================== 事件型 ==================

    public boolean matchesEvent(Player player, Team team, Criterion c, Event event, MatchContext ctx) {
        switch (c.type) {
            case EAT_ITEM:
                return event instanceof PlayerItemConsumeEvent e && c.items("item").matches(e.getItem().getType());
            case BREAK_BLOCK:
                return event instanceof BlockBreakEvent e && c.blocks("block").matches(e.getBlock().getType());
            case PICK_UP_ITEM:
                return event instanceof EntityPickupItemEvent e && e.getEntity() instanceof Player
                        && c.items("item").matches(e.getItem().getItemStack().getType());
            case CRAFT_ITEM:
                if (event instanceof CraftItemEvent e) {
                    ItemStack result = e.getRecipe() == null ? null : e.getRecipe().getResult();
                    return result != null && c.items("item").matches(result.getType());
                }
                return false;
            case HURT_ENTITY:
                return matchHurtEntity(player, c, event);
            case HURT_BY_ENTITY:
                return matchHurtByEntity(player, c, event);
            case KILL_ENTITY:
                return matchKillEntity(player, c, event);
            case KILLED_BY_ENTITY:
                return matchKilledByEntity(player, c, event);
            case OPEN_INVENTORY:
                return matchOpenInventory(player, c, event);
            case TRANSFER_ITEM:
                return matchTransfer(c, event);
            case INTERACT_BLOCK_WITH_ITEM:
                return matchInteract(c, event);
            case DROP_ITEM:
                return event instanceof PlayerDropItemEvent e
                        && c.items("item").matches(e.getItemDrop().getItemStack().getType());
            case PLACE_BLOCK:
                return event instanceof BlockPlaceEvent e && c.blocks("block").matches(e.getBlockPlaced().getType());
            case PHYSICAL_ACTION:
                return matchPhysical(c, event);
            case LEVEL_UP:
                return event instanceof PlayerLevelChangeEvent e && e.getNewLevel() > e.getOldLevel();
            case BUCKET_OPERATION:
                return matchBucket(c, event);
            case SELECTED_HOTBAR_SLOT:
                return event instanceof PlayerItemHeldEvent e && (e.getNewSlot() + 1) == c.intParam("slot", -1);
            case USER_DEFINED:
            case IMMEDIATELY_TRIGGER:
                return false;
            default:
                return false;
        }
    }

    private boolean matchHurtEntity(Player player, Criterion c, Event event) {
        if (!(event instanceof EntityDamageByEntityEvent e)) {
            return false;
        }
        Entity damager = e.getDamager();
        Player attacker = damager instanceof Player p ? p : (damager instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null);
        if (attacker == null || !attacker.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        if (c.has("mainhand") && !c.items("mainhand").matches(player.getInventory().getItemInMainHand().getType())) {
            return false;
        }
        if (c.has("entity") && !c.entities("entity").matches(e.getEntity().getType())) {
            return false;
        }
        if (c.has("damage_type") && !c.plain("damage_type").matches(damageKey(e))) {
            return false;
        }
        if (c.has("amount")) {
            NumRange range = c.range("amount");
            boolean inside = range.contains(e.getDamage());
            return c.bool("reversed", false) ? !inside : inside;
        }
        return true;
    }

    private boolean matchHurtByEntity(Player player, Criterion c, Event event) {
        if (!(event instanceof EntityDamageEvent e) || e.getEntity().getUniqueId() != player.getUniqueId()) {
            return false;
        }
        Entity source = e.getDamageSource().getCausingEntity();
        if (source == null) {
            source = e.getDamageSource().getDirectEntity();
        }
        String entitySpec = c.param("entity");
        if (entitySpec != null) {
            String spec = entitySpec.trim();
            if (spec.equals("/")) {
                if (source != null) {
                    return false;
                }
            } else if (!spec.equals("*") && (source == null || !c.entities("entity").matches(source.getType()))) {
                return false;
            }
        }
        if (c.has("damage_type") && !c.plain("damage_type").matches(damageKey(e))) {
            return false;
        }
        if (c.has("amount")) {
            NumRange range = c.range("amount");
            boolean inside = range.contains(e.getDamage());
            return c.bool("reversed", false) ? !inside : inside;
        }
        return true;
    }

    private boolean matchKillEntity(Player player, Criterion c, Event event) {
        if (!(event instanceof EntityDeathEvent e)) {
            return false;
        }
        Player killer = e.getEntity().getKiller();
        if (killer == null || !killer.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        if (c.has("entity") && !c.entities("entity").matches(e.getEntity().getType())) {
            return false;
        }
        return !c.has("damage_type") || c.plain("damage_type").matches(damageKey(e));
    }

    private boolean matchKilledByEntity(Player player, Criterion c, Event event) {
        if (!(event instanceof PlayerDeathEvent e) || !e.getEntity().getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        Entity source = e.getDamageSource() == null ? null : e.getDamageSource().getCausingEntity();
        String entitySpec = c.param("entity");
        if (entitySpec != null) {
            String spec = entitySpec.trim();
            if (spec.equals("/")) {
                if (source != null) {
                    return false;
                }
            } else if (source == null || !c.entities("entity").matches(source.getType())) {
                return false;
            }
        }
        return !c.has("damage_type") || c.plain("damage_type").matches(damageKey(e));
    }

    private boolean matchOpenInventory(Player player, Criterion c, Event event) {
        if (!(event instanceof InventoryOpenEvent e)) {
            return false;
        }
        if (!matchesInventoryType(c.param("target_inventory"), e.getInventory().getType())) {
            return false;
        }
        return !c.has("item") || c.items("item").matches(player.getInventory().getItemInMainHand().getType());
    }

    private boolean matchTransfer(Criterion c, Event event) {
        if (!(event instanceof InventoryClickEvent e)) {
            return false;
        }
        Inventory top = e.getView().getTopInventory();
        if (!matchesInventoryType(c.param("target_inventory"), top.getType())) {
            return false;
        }
        boolean out = c.bool("reversed", false);
        ValueSet items = c.items("item");
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        if (out) {
            return clickedTop && current != null && items.matches(current.getType());
        }
        if (clickedTop) {
            return cursor != null && items.matches(cursor.getType());
        }
        return e.isShiftClick() && current != null && items.matches(current.getType());
    }

    private boolean matchInteract(Criterion c, Event event) {
        if (!(event instanceof PlayerInteractEvent e)
                || e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || e.getClickedBlock() == null) {
            return false;
        }
        if (!c.blocks("block").matches(e.getClickedBlock().getType())) {
            return false;
        }
        ItemStack item = e.getItem();
        Material type = item == null ? Material.AIR : item.getType();
        return !c.has("item") || c.items("item").matches(type);
    }

    private boolean matchPhysical(Criterion c, Event event) {
        if (!(event instanceof PlayerInteractEvent e) || e.getAction() != org.bukkit.event.block.Action.PHYSICAL
                || e.getClickedBlock() == null) {
            return false;
        }
        return c.blocks("block").matches(e.getClickedBlock().getType());
    }

    private boolean matchBucket(Criterion c, Event event) {
        boolean fill;
        Material bucket;
        if (event instanceof PlayerBucketFillEvent e) {
            fill = true;
            bucket = e.getBucket();
        } else if (event instanceof PlayerBucketEmptyEvent e) {
            fill = false;
            bucket = e.getBucket();
        } else {
            return false;
        }
        boolean wantFill = "fill".equalsIgnoreCase(c.param("type"));
        if (fill != wantFill) {
            return false;
        }
        String fluid = c.param("fluid");
        if (fluid == null) {
            return true;
        }
        Material expected = switch (fluid.trim().toLowerCase(Locale.ROOT)) {
            case "water" -> Material.WATER_BUCKET;
            case "lava" -> Material.LAVA_BUCKET;
            case "powder_snow" -> Material.POWDER_SNOW_BUCKET;
            default -> null;
        };
        return expected == null || bucket == expected;
    }

    // ================== 状态型（周期性） ==================

    public boolean matchesPeriodic(Player player, Team team, Criterion c, MatchContext ctx) {
        switch (c.type) {
            case STATUS_EFFECT:
                return matchStatusEffect(player, c);
            case STANDING_ON_BLOCK:
                return matchStandingOnBlock(player, c);
            case IN_BLOCK:
                return matchInBlock(player, c);
            case DISTANCE_TO_ENTITY:
                return matchDistance(player, c, ctx);
            case ROTATION:
                return matchRotation(player, c);
            case HOLDING_ITEM:
                return matchHolding(player, c);
            case HEALTH:
                return matchRangeValue(c, player.getHealth());
            case FOOD_LEVEL:
                return matchRangeValue(c, player.getFoodLevel());
            case SURROUNDED_BY_BLOCK:
                return matchSurrounded(player, c);
            case INVENTORY_CONTAINING_ITEM:
                return matchInventoryContains(player, c);
            case HEIGHTMAP_MATCHING_POS:
                return matchHeightmap(player, c);
            case RESPAWN_TIME:
                return matchRespawnStay(player, c, ctx);
            case MOVE_TIME:
                return matchMoveTime(player, c, ctx);
            case CRITERIA_HOLD_TIME:
                return matchCriteriaHold(team, c, ctx);
            case AFTER_DEATH_TIME:
                return matchAfterDeath(player, c, ctx);
            case LIFE_COUNT:
                return matchRangeValue(c, team.getLives());
            case Y_LEVEL:
                return matchYLevel(player, c, ctx);
            default:
                return false;
        }
    }

    private boolean matchStatusEffect(Player player, Criterion c) {
        String spec = c.param("effect");
        if (spec == null || spec.trim().equals("*")) {
            return !player.getActivePotionEffects().isEmpty();
        }
        for (String name : spec.split(",")) {
            PotionEffectType type = potionType(name.trim());
            if (type != null && player.hasPotionEffect(type)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType potionType(String name) {
        try {
            return PotionEffectType.getByName(name.toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean matchStandingOnBlock(Player player, Criterion c) {
        ValueSet set = c.blocks("block");
        boolean reversed = c.bool("reversed", false);
        if (set.isWildcard()) {
            return reversed != player.isOnGround();
        }
        if (!player.isOnGround()) {
            return false;
        }
        Location below = player.getLocation().clone().add(0, -0.1, 0);
        boolean matches = set.matches(below.getBlock().getType());
        return reversed != matches;
    }

    private boolean matchInBlock(Player player, Criterion c) {
        ValueSet set = c.blocks("block");
        boolean reversed = c.bool("reversed", false);
        Location loc = player.getLocation();
        List<Material> types = List.of(
                loc.getBlock().getType(),
                loc.clone().add(0, player.getHeight() / 2.0, 0).getBlock().getType(),
                player.getEyeLocation().getBlock().getType());
        boolean all = types.stream().allMatch(set::matches);
        boolean none = types.stream().noneMatch(set::matches);
        return reversed ? none : all;
    }

    private boolean matchDistance(Player player, Criterion c, MatchContext ctx) {
        ValueSet set = c.entities("entity");
        NumRange range = c.range("distance");
        boolean reversed = c.bool("reversed", false);
        String mode = c.param("mode");
        boolean allOf = "all_of".equalsIgnoreCase(mode) || "ALL_OF".equalsIgnoreCase(mode);
        List<Entity> candidates = new ArrayList<>();
        for (Entity e : player.getWorld().getEntities()) {
            if (e.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (e instanceof Player other && !ctx.isParticipant(other)) {
                continue;
            }
            if (!set.isWildcard() && !set.matches(e.getType())) {
                continue;
            }
            candidates.add(e);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        boolean result = allOf;
        for (Entity e : candidates) {
            double distance = Math.sqrt(player.getLocation().distanceSquared(e.getLocation()));
            boolean inside = range.contains(distance);
            boolean now = reversed ? !inside : inside;
            if (allOf) {
                if (!now) {
                    return false;
                }
            } else if (now) {
                return true;
            }
        }
        return result && allOf;
    }

    private boolean matchRotation(Player player, Criterion c) {
        Location loc = player.getLocation();
        boolean yawOk = c.range("yaw").contains(loc.getYaw()) ^ c.bool("yaw_reversed", false);
        boolean pitchOk = c.range("pitch").contains(loc.getPitch()) ^ c.bool("pitch_reversed", false);
        return yawOk && pitchOk;
    }

    private boolean matchHolding(Player player, Criterion c) {
        if (c.has("mainhand") && !c.items("mainhand").matches(player.getInventory().getItemInMainHand().getType())) {
            return false;
        }
        return !c.has("offhand") || c.items("offhand").matches(player.getInventory().getItemInOffHand().getType());
    }

    private boolean matchRangeValue(Criterion c, double value) {
        NumRange range = c.param("count") != null ? c.range("count") : c.range("amount");
        boolean inside = range.contains(value);
        return c.bool("reversed", false) ? !inside : inside;
    }

    private boolean matchSurrounded(Player player, Criterion c) {
        ValueSet set = c.blocks("block");
        boolean reversed = c.bool("reversed", false);
        List<Material> types = surroundingTypes(player);
        if (set.isWildcard()) {
            return !reversed;
        }
        boolean all = types.stream().allMatch(set::matches);
        boolean none = types.stream().noneMatch(set::matches);
        return reversed ? none : all;
    }

    private List<Material> surroundingTypes(Player player) {
        Location feet = player.getLocation();
        Location eye = player.getEyeLocation();
        List<Material> out = new ArrayList<>(10);
        out.add(feet.clone().add(1, 0, 0).getBlock().getType());
        out.add(feet.clone().add(-1, 0, 0).getBlock().getType());
        out.add(feet.clone().add(0, -1, 0).getBlock().getType());
        out.add(feet.clone().add(0, 0, 1).getBlock().getType());
        out.add(feet.clone().add(0, 0, -1).getBlock().getType());
        out.add(eye.clone().add(1, 0, 0).getBlock().getType());
        out.add(eye.clone().add(-1, 0, 0).getBlock().getType());
        out.add(eye.clone().add(0, 1, 0).getBlock().getType());
        out.add(eye.clone().add(0, 0, 1).getBlock().getType());
        out.add(eye.clone().add(0, 0, -1).getBlock().getType());
        return out;
    }

    private boolean matchInventoryContains(Player player, Criterion c) {
        ValueSet set = c.items("item");
        boolean reversed = c.bool("reversed", false);
        boolean has = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && set.matches(item.getType())) {
                has = true;
                break;
            }
        }
        return reversed != has;
    }

    private boolean matchHeightmap(Player player, Criterion c) {
        HeightMap map = HeightMap.WORLD_SURFACE;
        String spec = c.param("heightmap");
        if (spec != null) {
            try {
                map = HeightMap.valueOf(spec.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                map = HeightMap.WORLD_SURFACE;
            }
        }
        int highest = player.getWorld().getHighestBlockYAt(player.getLocation(), map);
        boolean same = player.getEyeLocation().getY() >= highest + 1.0;
        return same ^ c.bool("reversed", false);
    }

    private boolean matchRespawnStay(Player player, Criterion c, MatchContext ctx) {
        if (!c.param("mode").trim().equalsIgnoreCase("stay")) {
            return false;
        }
        PlayerState st = ctx.state(player);
        if (st == null || !player.isDead() || st.deathTick < 0) {
            return false;
        }
        long stayed = ctx.tick - st.deathTick;
        boolean inside = c.range("time").contains(stayed);
        return c.bool("reversed", false) ? !inside : inside;
    }

    /** 玩家复活时调用：mode=delay 检查死亡到复活的时间。 */
    public boolean matchesRespawnDelay(Player player, Criterion c, MatchContext ctx) {
        if (!"delay".equalsIgnoreCase(String.valueOf(c.param("mode")))) {
            return false;
        }
        PlayerState st = ctx.state(player);
        if (st == null || st.deathTick < 0) {
            return false;
        }
        long delay = ctx.tick - st.deathTick;
        boolean inside = c.range("time").contains(delay);
        return c.bool("reversed", false) ? !inside : inside;
    }

    private boolean matchMoveTime(Player player, Criterion c, MatchContext ctx) {
        PlayerState st = ctx.state(player);
        if (st == null) {
            return false;
        }
        String type = String.valueOf(c.param("type")).trim().toLowerCase(Locale.ROOT);
        long time = (long) c.doubleParam("time", 100);
        boolean reversed = c.bool("reversed", false);
        long value;
        switch (type) {
            case "move" -> value = reversed ? PlayerState.idle(st.lastMove, ctx.tick)
                    : PlayerState.continuous(st.moveStart, ctx.tick);
            case "sprint" -> value = reversed ? PlayerState.idle(st.lastSprint, ctx.tick)
                    : PlayerState.continuous(st.sprintStart, ctx.tick);
            case "sneak" -> value = reversed ? PlayerState.idle(st.lastSneak, ctx.tick)
                    : PlayerState.continuous(st.sneakStart, ctx.tick);
            case "swim" -> value = reversed ? PlayerState.idle(st.lastSwim, ctx.tick)
                    : PlayerState.continuous(st.swimStart, ctx.tick);
            case "glide" -> value = reversed ? PlayerState.idle(st.lastGlide, ctx.tick)
                    : PlayerState.continuous(st.glideStart, ctx.tick);
            case "jump" -> value = PlayerState.idle(st.lastJump, ctx.tick);
            case "fall" -> value = reversed ? PlayerState.idle(st.lastFall, ctx.tick)
                    : PlayerState.continuous(st.fallStart, ctx.tick);
            case "on_air" -> value = reversed ? PlayerState.idle(st.lastOnAir, ctx.tick)
                    : PlayerState.continuous(st.onAirStart, ctx.tick);
            case "rotate" -> value = reversed ? PlayerState.idle(st.lastRotate, ctx.tick)
                    : PlayerState.continuous(st.rotateStart, ctx.tick);
            default -> {
                return false;
            }
        }
        if (value <= 0) {
            return false;
        }
        return reversed ? value >= time : value >= time;
    }

    private boolean matchCriteriaHold(Team team, Criterion c, MatchContext ctx) {
        long held = ctx.tick - team.getCriterionSince();
        boolean inside = c.range("time").contains(held);
        return c.bool("reversed", false) ? !inside : inside;
    }

    private boolean matchAfterDeath(Player player, Criterion c, MatchContext ctx) {
        PlayerState st = ctx.state(player);
        if (st == null || st.lastDeathTick < 0) {
            return false;
        }
        return ctx.tick - st.lastDeathTick >= c.doubleParam("time", 1200);
    }

    private boolean matchYLevel(Player player, Criterion c, MatchContext ctx) {
        int border = c.intParam("border", ctx.groundY);
        double y = player.getLocation().getY();
        return "below".equalsIgnoreCase(c.param("mode")) ? y < border : y > border;
    }

    // ================== 工具 ==================

    private static String damageKey(EntityDamageEvent event) {
        try {
            return event.getDamageSource().getDamageType().getKey().getKey();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String damageKey(EntityDeathEvent event) {
        try {
            return event.getDamageSource().getDamageType().getKey().getKey();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean matchesInventoryType(String spec, InventoryType type) {
        if (spec == null || spec.trim().isEmpty()) {
            return true;
        }
        String want = spec.trim().toLowerCase(Locale.ROOT);
        String actual = type.name().toLowerCase(Locale.ROOT);
        return switch (want) {
            case "workbench" -> type == InventoryType.WORKBENCH;
            case "chest" -> type == InventoryType.CHEST;
            default -> actual.equals(want) || actual.equals(want + "_box");
        };
    }

    /** 供 /execute 类命令或调试使用：某类型是否属于“状态型”。 */
    public static boolean isPeriodic(CritType type) {
        return type.isPeriodic();
    }
}
