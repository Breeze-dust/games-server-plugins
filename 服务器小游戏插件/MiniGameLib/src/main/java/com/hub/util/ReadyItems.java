package com.hub.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 等待室「准备」道具：所有等待中的玩家都右键准备后自动开局。
 *
 * <p>道具本身与右键处理都在这里（事件监听全局只注册一份），各小游戏的
 * {@link WaitCountdown} 只负责“谁准备了 / 什么时候开局”。发放方式：
 * <pre>
 *   countdown.giveReadyItem(player);   // 给等待物品时调用
 * </pre>
 * 管理员（队列里有人 {@link WaitCountdown.Host#hasOpQueued()}）在队列中时不会自动开局；
 * 街机派对接管期间（{@link WaitCountdown#isGloballySuppressed()}）不发放。
 */
public final class ReadyItems {

    /** 准备道具的槽位（各游戏的开始/退出/选队道具一般占 0 / 1 / 4 / 8，所以放 2）。 */
    public static final int SLOT = 2;

    private static final NamespacedKey KEY = new NamespacedKey("minigamelib", "ready_item");

    /** 玩家 -> 他所在等待室的倒计时（右键准备时据此找到所属游戏）。 */
    private static final Map<UUID, WaitCountdown> OWNERS = new ConcurrentHashMap<>();

    /**
     * 玩家 -> 上一次处理准备点击的 tick。
     * <p>
     * 对着方块右键时 Paper 会把 {@code PlayerInteractEvent} 连发两次（先方块、再物品），
     * 不拦一下就会“准备 → 取消准备”切换两遍：道具回到灰色，但 3 秒倒计时已经被触发。
     * 同一 tick 内的第二次点击直接忽略。
     */
    private static final Map<UUID, Integer> LAST_TOGGLE_TICK = new ConcurrentHashMap<>();

    private ReadyItems() {
    }

    /** MiniGameLib 启用时注册全局监听（只注册一次）。 */
    public static void init(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.HIGHEST)
            public void onInteract(PlayerInteractEvent event) {
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    return;
                }
                Action action = event.getAction();
                if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                if (!isReadyItem(event.getItem())) {
                    return;
                }
                event.setCancelled(true);
                UUID id = event.getPlayer().getUniqueId();
                int tick = Bukkit.getCurrentTick();
                Integer last = LAST_TOGGLE_TICK.put(id, tick);
                if (last != null && last == tick) {
                    return;
                }
                WaitCountdown owner = OWNERS.get(id);
                if (owner != null) {
                    owner.toggleReady(event.getPlayer());
                }
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                UUID id = event.getPlayer().getUniqueId();
                LAST_TOGGLE_TICK.remove(id);
                WaitCountdown owner = OWNERS.remove(id);
                if (owner != null) {
                    owner.removeReady(id);
                }
            }
        }, plugin);
    }

    static void bind(UUID player, WaitCountdown countdown) {
        OWNERS.put(player, countdown);
    }

    static void unbind(UUID player) {
        OWNERS.remove(player);
    }

    /** 清掉“已经不在这个等待室里”的绑定（玩家退队/开局后不再需要）。 */
    static void retain(WaitCountdown countdown, Collection<UUID> stillWaiting) {
        Set<UUID> keep = new HashSet<>(stillWaiting);
        Iterator<Map.Entry<UUID, WaitCountdown>> it = OWNERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WaitCountdown> entry = it.next();
            if (entry.getValue() == countdown && !keep.contains(entry.getKey())) {
                it.remove();
            }
        }
    }

    /** 生成准备道具：未准备 = 灰色染料，已准备 = 青柠染料。 */
    static ItemStack icon(boolean ready) {
        ItemStack item = new ItemStack(ready ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(ready
                    ? "&a&l已准备 &7(右键取消)"
                    : "&f&l准备 &7(右键)")));
            meta.lore(List.of(Chat.color(ready
                    ? "&7已准备，等待其他玩家"
                    : "&7所有玩家都准备后自动开始")));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isReadyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .getOrDefault(KEY, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    /** 玩家身上还挂着准备道具时把它清掉（离开等待室 / 开局时调用）。 */
    static void clearFrom(Player player) {
        if (player == null) {
            return;
        }
        if (isReadyItem(player.getInventory().getItem(SLOT))) {
            player.getInventory().setItem(SLOT, null);
        }
    }
}
