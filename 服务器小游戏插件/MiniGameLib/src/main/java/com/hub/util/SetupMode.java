package com.hub.util;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用 OP 建场模式（四个小游戏原来各抄一份 enterSetup/exitSetup/handleSetupTool）：
 * <ul>
 *   <li>进入：备份背包/护甲/模式/位置 → 清空背包 → 发放带 PDC 标记的工具；</li>
 *   <li>使用：按工具 id 回调插件的 {@link Handler}，插件只管自己的场地字段；</li>
 *   <li>退出：还原背包与状态。</li>
 * </ul>
 * 公共部分（进场/退场、工具识别、save、exit）在这里；各插件专属工具（例如单挑的 kit 工具、
 * 建筑猜猜乐的选区木棍）通过 {@link Tool} + {@link Handler} 自己扩展。
 * <p>
 * 每个插件 new 一个实例（用不同的 keyName 区隔 PDC 标记），并在 onDisable 调 {@link #exitAll()}。
 */
public final class SetupMode {

    /** 一件配置工具。leftClick / rightClick 声明它接受哪种点击。 */
    public record Tool(Material material, String id, String name, String lore,
                       boolean leftClick, boolean rightClick) {

        /** 右键型工具（大多数）。 */
        public static Tool right(Material material, String id, String name, String lore) {
            return new Tool(material, id, name, lore, false, true);
        }

        /** 左右键都要用的工具（如选区木棍/范围工具）。 */
        public static Tool both(Material material, String id, String name, String lore) {
            return new Tool(material, id, name, lore, true, true);
        }
    }

    /** 插件侧回调。 */
    public interface Handler {

        /**
         * 某个配置工具被使用。
         *
         * @param arenaName 该玩家正在配置的场地名
         * @param toolId    工具的 id（{@link Tool#id()}）
         * @param action    原始交互动作
         * @param clicked   点到的方块（右键空气时为 null）
         */
        void onTool(Player player, String arenaName, String toolId, Action action, Block clicked);

        /** save 工具被右键（一般是 saveAll 落盘 + 提示）。 */
        void onSave(Player player, String arenaName);
    }

    /** 保留 id：由公共库自己处理。 */
    public static final String TOOL_SAVE = "save";
    public static final String TOOL_EXIT = "exit";

    /** 所有实例，关服时统一还原（避免 OP 背包卡在配置模式里）。 */
    private static final List<SetupMode> INSTANCES = new ArrayList<>();

    private final Plugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, String> editing = new LinkedHashMap<>();
    private final Map<UUID, Saved> backups = new LinkedHashMap<>();
    private final Map<UUID, Handler> handlers = new LinkedHashMap<>();
    private final Map<UUID, List<Tool>> tools = new LinkedHashMap<>();

    public SetupMode(Plugin plugin, String keyName) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, keyName);
        synchronized (INSTANCES) {
            INSTANCES.add(this);
        }
    }

    /** 关服兜底：还原所有插件的所有配置模式玩家。 */
    public static void exitAllRegistered() {
        List<SetupMode> copy;
        synchronized (INSTANCES) {
            copy = new ArrayList<>(INSTANCES);
        }
        for (SetupMode m : copy) {
            m.exitAll();
        }
    }

    public boolean isEditing(Player p) {
        return editing.containsKey(p.getUniqueId());
    }

    /** 该玩家正在配置的场地名，没有则 null。 */
    public String editingArena(Player p) {
        return editing.get(p.getUniqueId());
    }

    /**
     * 进入配置模式。
     *
     * @return false = 该玩家已在配置模式中（不会重复备份背包）
     */
    public boolean enter(Player p, String arenaName, List<Tool> toolList, Handler handler) {
        UUID id = p.getUniqueId();
        if (editing.containsKey(id)) {
            return false;
        }
        editing.put(id, arenaName);
        backups.put(id, new Saved(p));
        handlers.put(id, handler);
        tools.put(id, List.copyOf(toolList));

        p.getInventory().clear();
        int slot = 0;
        for (Tool t : toolList) {
            if (slot >= 9) {
                break;
            }
            p.getInventory().setItem(slot++, item(t));
        }
        return true;
    }

    /** 退出配置模式并还原背包；返回是否本来就在配置模式。 */
    public boolean exit(Player p) {
        UUID id = p.getUniqueId();
        editing.remove(id);
        handlers.remove(id);
        tools.remove(id);
        Saved st = backups.remove(id);
        if (st == null) {
            return false;
        }
        st.apply(p);
        return true;
    }

    /** 关服兜底：还原本实例所有仍在配置模式的玩家。 */
    public void exitAll() {
        for (UUID id : new ArrayList<>(editing.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                exit(p);
            } else {
                editing.remove(id);
                handlers.remove(id);
                tools.remove(id);
                backups.remove(id);
            }
        }
    }

    /**
     * 交互入口：手持本插件的配置工具时按 id 分发。
     * <p>
     * 与各插件原来的写法一致：只要是"本插件的配置工具"就吞掉这次交互（返回 true），
     * 但只有在工具声明的点击方式（{@link Tool#leftClick()} / {@link Tool#rightClick()}）
     * 匹配时才真正处理——例如右键型工具被左键点击时什么都不做。
     *
     * @return true = 这次交互已被配置模式消费（调用方应把事件取消）
     */
    public boolean handleInteract(Player p, Action action, ItemStack hand, Block clicked) {
        String arenaName = editing.get(p.getUniqueId());
        if (arenaName == null) {
            return false;
        }
        ItemStack item = hand == null ? p.getInventory().getItemInMainHand() : hand;
        String toolId = toolId(item);
        if (toolId == null) {
            return false;
        }
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        Tool spec = null;
        for (Tool t : tools.getOrDefault(p.getUniqueId(), List.of())) {
            if (t.id().equals(toolId)) {
                spec = t;
                break;
            }
        }
        boolean allowed = spec != null && ((left && spec.leftClick()) || (right && spec.rightClick()));
        if (!allowed) {
            return true; // 认识这件工具，但这次点击方式不对：吞掉不处理（与原来的 switch 行为一致）
        }
        if (TOOL_EXIT.equals(toolId)) {
            exit(p);
            Chat.send(p, "&7已退出配置模式，背包已恢复。");
            return true;
        }
        if (TOOL_SAVE.equals(toolId)) {
            Handler h = handlers.get(p.getUniqueId());
            if (h != null) {
                h.onSave(p, arenaName);
            }
            return true;
        }
        Handler h = handlers.get(p.getUniqueId());
        if (h != null) {
            h.onTool(p, arenaName, toolId, action, clicked);
        }
        return true;
    }

    /** 物品是否为配置模式工具（用于拦截丢弃/移动）。 */
    public boolean isTool(ItemStack item) {
        return toolId(item) != null;
    }

    private String toolId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack item(Tool t) {
        ItemStack it = new ItemStack(t.material());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Chat.noItalic(Chat.color(t.name())));
            meta.lore(List.of(Chat.color(t.lore())));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id());
            it.setItemMeta(meta);
        }
        return it;
    }

    /** 玩家进入配置模式前的背包与状态。 */
    private static final class Saved {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final GameMode mode;
        private final Location loc;

        Saved(Player p) {
            this.contents = p.getInventory().getContents().clone();
            this.armor = p.getInventory().getArmorContents().clone();
            this.mode = p.getGameMode();
            this.loc = p.getLocation().clone();
        }

        void apply(Player p) {
            p.getInventory().clear();
            p.getInventory().setContents(contents);
            p.getInventory().setArmorContents(armor);
            p.setGameMode(mode);
            p.teleport(loc);
        }
    }
}
