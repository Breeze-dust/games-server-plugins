package com.landclaim;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

/** 加入游戏时保存玩家完整状态，退出/结算时原样恢复。 */
public final class PlayerState {

    private final Location location;
    private final GameMode gameMode;
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final ItemStack offHand;
    private final double health;
    private final int foodLevel;
    private final int level;
    private final float saturation;
    private final float exp;
    private final boolean allowFlight;
    private final boolean flying;
    private final Collection<PotionEffect> effects;

    private PlayerState(Location location, GameMode gameMode,
                        ItemStack[] inventory, ItemStack[] armor, ItemStack[] extra, ItemStack offHand,
                        double health, int foodLevel, int level, float saturation, float exp,
                        boolean allowFlight, boolean flying, Collection<PotionEffect> effects) {
        this.location = location;
        this.gameMode = gameMode;
        this.inventory = inventory;
        this.armor = armor;
        this.extra = extra;
        this.offHand = offHand;
        this.health = health;
        this.foodLevel = foodLevel;
        this.level = level;
        this.saturation = saturation;
        this.exp = exp;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.effects = effects;
    }

    public static PlayerState capture(Player player) {
        PlayerInventory inv = player.getInventory();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;
        return new PlayerState(
                player.getLocation().clone(),
                player.getGameMode(),
                inv.getContents().clone(),
                inv.getArmorContents().clone(),
                inv.getExtraContents().clone(),
                inv.getItemInOffHand().clone(),
                Math.min(player.getHealth(), maxHealth),
                player.getFoodLevel(),
                player.getLevel(),
                player.getSaturation(),
                player.getExp(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getActivePotionEffects());
    }

    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setContents(inventory);
        inv.setArmorContents(armor);
        inv.setExtraContents(extra);
        inv.setItemInOffHand(offHand);

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        player.setHealth(health);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        player.setFireTicks(0);
        player.setInvulnerable(false);
        // 游戏可能改过计分板/队伍；恢复主计分板，让头顶头衔重新生效。
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.addPotionEffects(effects);
        player.teleport(location);
    }
}
