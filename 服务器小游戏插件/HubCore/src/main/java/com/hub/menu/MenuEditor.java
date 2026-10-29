package com.hub.menu;

import com.hub.HubCorePlugin;
import com.hub.HubManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 菜单可视化编辑器（OP 专用，/hub menu edit）。
 *
 * <p>操作方式：左键拿起/放下 = 移动或替换该格物品，右键拿起 = 复制该格物品，
 * 然后到别处左键放下；SHIFT+左键 = 聊天栏输入材质名；SHIFT+右键 = 输入显示名；
 * 双击 = 输入 lore 提示（多行用 | 分隔，留空 = 恢复默认）；Q = 删除该格。
 * 所有修改立即写进 config.yml 并重开菜单，OP 看到的就是玩家看到的效果。
 *
 * <p>玩家侧：普通左键走动作（进游戏/进场地子菜单），SHIFT+左键只看提示，方便 OP 核对 lore。
 */
public class MenuEditor implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /** 聊天栏输入的有效期：超过这个毫秒数没输入就当取消（避免很久以后的一条聊天改到菜单）。 */
    private static final long PROMPT_TIMEOUT_MS = 120_000L;

    private enum Field {
        MATERIAL, NAME, LORE
    }

    private record Prompt(MenuLayout.Place place, String game, int slot, Field field, long expireAt) {
    }

    private final HubCorePlugin plugin;
    private final MenuLayout layout;
    /** 正在编辑菜单的 OP。 */
    private final Map<UUID, MenuLayout.Place> editing = new LinkedHashMap<>();
    /** 等待聊天栏输入的 OP。 */
    private final Map<UUID, Prompt> prompts = new LinkedHashMap<>();
    /** 开放中的菜单被重开时置位，用于区分"关闭"和"重开"。 */
    private final Map<UUID, Boolean> reopening = new LinkedHashMap<>();
    /** 编辑模式里"右键复制"暂存的物品（含动作），左键粘贴时连动作一起还原。 */
    private final Map<UUID, Clipboard> clipboard = new LinkedHashMap<>();

    private record Clipboard(ItemStack item, String action, String game) {
    }

    public MenuEditor(HubCorePlugin plugin, MenuLayout layout) {
        this.plugin = plugin;
        this.layout = layout;
    }

    // ---------- 开关编辑模式 ----------

    public boolean isEditing(Player p) {
        return editing.containsKey(p.getUniqueId());
    }

    /** 打开菜单；isEditing 为 true 时进入编辑模式。 */
    public void openMenu(Player p, MenuLayout.Place place, String game, boolean isEditing) {
        if (place == MenuLayout.Place.MAIN) {
            if (!layout.hasMain()) {
                layout.generateDefaultMain();
            }
            layout.ensureMiniGameList();
        } else if (!layout.hasSub(game)) {
            layout.generateDefaultSub(game);
        }
        p.closeInventory();
        if (isEditing) {
            editing.put(p.getUniqueId(), place);
        } else {
            editing.remove(p.getUniqueId());
            prompts.remove(p.getUniqueId());
            clipboard.remove(p.getUniqueId());
        }
        Inventory inv = (place == MenuLayout.Place.SUB) ? layout.buildSub(p, game) : layout.buildMain(p);
        p.openInventory(inv);
        if (isEditing) {
            sendHelp(p, place, game);
        }
    }

    /** /hub menu edit [游戏|main] */
    public void startEdit(Player p, MenuLayout.Place place, String game) {
        openMenu(p, place, game, true);
        p.sendMessage(LEGACY.deserialize("&a已进入菜单编辑模式，修改立即保存到 config.yml。"));
    }

    public void stopEdit(Player p, boolean verbose) {
        editing.remove(p.getUniqueId());
        prompts.remove(p.getUniqueId());
        clipboard.remove(p.getUniqueId());
        if (verbose) {
            p.sendMessage(LEGACY.deserialize("&7已退出菜单编辑模式。"));
        }
    }

    // ---------- 供 /hub menu 命令使用 ----------

    /** 命令里定位槽位：给了游戏名就用该游戏在主菜单的槽位，否则用当前打开的菜单。 */
    public int resolveSlot(Player p, String game, int argSlot) {
        if (argSlot >= 0) {
            return argSlot;
        }
        if (game != null) {
            return layout.slotOfGame(game);
        }
        InventoryHolder h = currentHolder(p);
        return (h instanceof MenuHolder mh) ? mh.slot() : -1;
    }

    /** 命令里定位"主菜单还是子菜单"：给了游戏名 = 子菜单。 */
    public MenuLayout.Place placeOf(Player p, String game) {
        return game == null ? MenuLayout.Place.MAIN : MenuLayout.Place.SUB;
    }

    /** 命令里定位游戏名：没给参数时取当前打开的子菜单所属游戏。 */
    public String gameOf(Player p, String given) {
        if (given != null) {
            return given;
        }
        InventoryHolder h = currentHolder(p);
        return (h instanceof MenuHolder mh) ? mh.game() : null;
    }

    public void reopen(Player p) {
        if (!isEditing(p)) {
            return;
        }
        MenuLayout.Place place = editing.get(p.getUniqueId());
        String game = gameOfCurrent(p, place);
        reopening.put(p.getUniqueId(), true);
        openMenu(p, place, game, true);
        reopening.remove(p.getUniqueId());
    }

    public void reopenFor(Player p, MenuLayout.Place place, String game) {
        if (!isEditing(p)) {
            return;
        }
        reopening.put(p.getUniqueId(), true);
        openMenu(p, place, game, true);
        reopening.remove(p.getUniqueId());
    }

    private String gameOfCurrent(Player p, MenuLayout.Place place) {
        if (place != MenuLayout.Place.SUB) {
            return null;
        }
        InventoryHolder h = currentHolder(p);
        return (h instanceof MenuHolder mh) ? mh.game() : null;
    }

    private InventoryHolder currentHolder(Player p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }

    // ---------- 玩家侧点击 ----------

    private boolean isMenu(InventoryHolder holder) {
        return holder instanceof MenuHolder;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isMenu(holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (isEditing(p)) {
            if (holder instanceof MenuHolder mh) {
                editClick(p, mh.place(), mh.game(), slot, event);
            }
            return;
        }
        // 玩家侧：SHIFT+左键 只看提示（OP 核对 lore 用）
        if (event.isShiftClick() && event.isLeftClick()) {
            p.sendMessage(LEGACY.deserialize("&7该格提示（lore）内容："));
            ItemStack it = event.getCurrentItem();
            if (it == null || !it.hasItemMeta() || it.getItemMeta().lore() == null) {
                p.sendMessage(LEGACY.deserialize("  &8(无)"));
            } else {
                for (net.kyori.adventure.text.Component line : it.getItemMeta().lore()) {
                    p.sendMessage(line);
                }
            }
            return;
        }
        if (holder instanceof MenuHolder mh) {
            if (isEditing(p) && carriedAction(mh.place(), mh.game(), slot) == null) {
                p.sendMessage(LEGACY.deserialize("&e这一格没有动作（点了没反应）。用 &f/hub menu action " + slot + " game:<游戏名> &e给他加动作。"));
                return;
            }
            plugin.getManager().handleMenuClick(p, mh.place(), mh.game(), slot);
        }
    }

    // ---------- 编辑模式点击 ----------

    private void editClick(Player p, MenuLayout.Place place, String game, int slot, InventoryClickEvent event) {
        UUID id = p.getUniqueId();
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;

        // Q：删除该格
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            boolean removed = layout.remove(place, game, slot);
            layout.save();
            p.sendMessage(LEGACY.deserialize(removed ? "&a已删除槽位 &e" + slot + "&a。" : "&7槽位 &e" + slot + "&7 本来就是空的。"));
            reopenFor(p, place, game);
            return;
        }
        // SHIFT+左键：材质
        if (event.getClick() == ClickType.SHIFT_LEFT) {
            prompt(p, place, game, slot, Field.MATERIAL, "请输入材质名（英文，例如 DIAMOND_SWORD），输入 &ccancel&7 取消：");
            return;
        }
        // SHIFT+右键：显示名
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            prompt(p, place, game, slot, Field.NAME, "请输入显示名（支持 &7& 颜色代码），输入 &cclear&7 恢复默认，&ccancel&7 取消：");
            return;
        }
        // 双击：lore 提示
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            prompt(p, place, game, slot, Field.LORE, "请输入提示文字（多行用 &e|&7 分隔），输入 &cclear&7 恢复默认，&ccancel&7 取消：");
            return;
        }
        // 左键放下：把光标上的物品放到这一格；若该物品是刚在这里"右键复制"来的，连动作一起还原
        if (event.isLeftClick() && !cursorEmpty) {
            ItemStack given = cursor.clone();
            given.setAmount(1);
            p.setItemOnCursor(null); // 编辑用的物品从背包里拿走，避免复制
            Clipboard cp = clipboard.get(id);
            String carriedAction = null;
            if (cp != null && cp.item().getType() == given.getType() && matcherEquals(cp.item(), given)) {
                carriedAction = cp.action();
            }
            applyItem(p, place, game, slot, given, carriedAction);
            return;
        }
        // 右键拿起：把这一格复制到光标（动作一起记住），再左键放到别处即可
        if (event.isRightClick()) {
            ItemStack cur = event.getCurrentItem();
            if (cur == null || cur.getType() == Material.AIR) {
                p.sendMessage(LEGACY.deserialize("&7这一格是空的。"));
                return;
            }
            String action = carriedAction(place, game, slot);
            clipboard.put(id, new Clipboard(cur.clone(), action, place == MenuLayout.Place.SUB ? game : null));
            p.setItemOnCursor(cur.clone()); // 光标上原来的东西会退回背包
            p.sendMessage(action == null
                    ? LEGACY.deserialize("&a已复制该物品到光标（&e这一格没有动作&a，贴上后请用 /hub menu action 补）。")
                    : LEGACY.deserialize("&a已复制该物品到光标（含动作 &f" + action + "&a）：左键点到目标格即可放下（右键取消）。"));
            return;
        }
        p.sendMessage(LEGACY.deserialize("&7编辑操作：左键放下 / 右键复制 / SHIFT+左键改材质 / SHIFT+右键改名字 / 双击改提示 / Q 删除"));
    }

    /** 该格当前的点击动作（主菜单读 menu.items，子菜单读场地条目）。 */
    private String carriedAction(MenuLayout.Place place, String game, int slot) {
        if (place == MenuLayout.Place.SUB) {
            MenuLayout.SubItem item = layout.subItem(game, String.valueOf(slot));
            return item == null ? null : item.action();
        }
        MenuLayout.Entry entry = layout.entry(MenuLayout.Place.MAIN, null, slot);
        return entry == null ? null : entry.action();
    }

    private static boolean matcherEquals(ItemStack a, ItemStack b) {
        ItemMeta ma = a.getItemMeta();
        ItemMeta mb = b.getItemMeta();
        if (ma == null || mb == null) {
            return ma == mb;
        }
        String na = ma.displayName() == null ? "" : LEGACY.serialize(ma.displayName());
        String nb = mb.displayName() == null ? "" : LEGACY.serialize(mb.displayName());
        if (!na.equals(nb)) {
            return false;
        }
        List<String> la = loreOf(ma);
        List<String> lb = loreOf(mb);
        return la.equals(lb);
    }

    private static List<String> loreOf(ItemMeta meta) {
        List<String> out = new ArrayList<>();
        if (meta != null && meta.lore() != null) {
            for (net.kyori.adventure.text.Component c : meta.lore()) {
                out.add(LEGACY.serialize(c));
            }
        }
        return out;
    }

    private void applyItem(Player p, MenuLayout.Place place, String game, int slot, ItemStack item, String carriedAction) {
        String mat = item.getType().name();
        String name = null;
        List<String> lore = null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.displayName() != null) {
                name = LEGACY.serialize(meta.displayName());
            }
            if (meta.lore() != null && !meta.lore().isEmpty()) {
                lore = new ArrayList<>();
                for (net.kyori.adventure.text.Component c : meta.lore()) {
                    lore.add(LEGACY.serialize(c));
                }
            }
        }
        MenuLayout.Entry old = layout.entry(place, game, slot);
        String action = carriedAction != null ? carriedAction : (old == null ? null : old.action());
        layout.setItem(place, game, String.valueOf(slot), mat, name, lore, action);
        layout.save();
        p.sendMessage(action == null
                ? LEGACY.deserialize("&a槽位 &e" + slot + "&a 已设为 &f" + mat + "&a，但它&e还没有动作&a（点了没反应），请用 &f/hub menu action " + slot + " game:<游戏名> &a补上。")
                : LEGACY.deserialize("&a槽位 &e" + slot + "&a 已设为 &f" + mat + "&a，动作：&f" + action + "&a。"));
        reopenFor(p, place, game);
    }

    /** 命令 /hub menu set 用：直接设定一格的材质/名字/lore。 */
    public void setSlot(Player p, MenuLayout.Place place, String game, int slot, String material, String name, List<String> lore, String action) {
        layout.setItem(place, game, String.valueOf(slot), material, name, lore, action);
        layout.save();
        p.sendMessage(LEGACY.deserialize("&a已更新" + whereText(place, game) + "槽位 &e" + slot + "&a。"));
        reopen(p);
    }

    public void moveSlot(Player p, MenuLayout.Place place, String game, int from, int to) {
        if (from == to) {
            p.sendMessage(LEGACY.deserialize("&7起止槽位相同，没动。"));
            return;
        }
        layout.move(place, game, from, to);
        layout.save();
        p.sendMessage(LEGACY.deserialize("&a已把" + whereText(place, game) + "槽位 &e" + from + " &a搬到 &e" + to + "&a。"));
        reopen(p);
    }

    public void deleteSlot(Player p, MenuLayout.Place place, String game, int slot) {
        boolean removed = layout.remove(place, game, slot);
        layout.save();
        p.sendMessage(LEGACY.deserialize(removed ? "&a已删除" + whereText(place, game) + "槽位 &e" + slot + "&a。"
                : "&7槽位 &e" + slot + "&7 本来就是空的。"));
        reopen(p);
    }

    public void bindAction(Player p, MenuLayout.Place place, String game, int slot, String[] actionArgs) {
        String action = parseActionArgs(actionArgs);
        if (action == null) {
            p.sendMessage(LEGACY.deserialize("&c写法：/hub menu action <槽位> game <游戏> | open <游戏> | arena <游戏> <场地> | cmd <命令> | none"
                    + "&7（游戏名/命令要带冒号也行：&fcmd:gtb join&7）"));
            return;
        }
        // 游戏名归一化成注册表里的显示名，免得 OP 手写少了空格/写了 alias 之后点不动
        action = layout.canonicalAction(action);
        layout.setAction(place, game, slot, action);
        layout.save();
        p.sendMessage(LEGACY.deserialize("&a已把" + whereText(place, game) + "槽位 &e" + slot + "&a 的动作设为 &f" + action + "&a。"));
        reopen(p);
    }

    /**
     * 解析动作参数。兼容两种写法：
     * <pre>
     *   /hub menu action 20 cmd gtb join      空格分隔（多词命令用这个）
     *   /hub menu action 20 cmd:gtb join      冒号分隔（帮助里写的写法，也必须能用）
     * </pre>
     * 实现要点：类型后面允许带一个冒号，去不去掉都要能认出 cmd/game/open/arena/none。
     */
    public static String parseActionArgs(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String type = args[0].toLowerCase();
        if (type.endsWith(":")) {
            type = type.substring(0, type.length() - 1);
        }
        switch (type) {
            case "none" -> {
                return "none";
            }
            case "spectate" -> {
                return "spectate";
            }
            case "game", "open" -> {
                if (args.length < 2) {
                    return null;
                }
                return type + ":" + args[1];
            }
            case "arena" -> {
                if (args.length < 3) {
                    return null;
                }
                return "arena:" + args[1] + "|" + args[2];
            }
            case "cmd" -> {
                if (args.length < 2) {
                    return null;
                }
                return "cmd:" + String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            default -> {
                return null;
            }
        }
    }

    private String whereText(MenuLayout.Place place, String game) {
        return place == MenuLayout.Place.SUB ? ("「" + game + "」子菜单") : "主菜单";
    }

    // ---------- 聊天栏输入 ----------

    private void prompt(Player p, MenuLayout.Place place, String game, int slot, Field field, String hint) {
        prompts.put(p.getUniqueId(), new Prompt(place, game, slot, field, System.currentTimeMillis() + PROMPT_TIMEOUT_MS));
        p.closeInventory();
        p.sendMessage(LEGACY.deserialize("&e" + hint));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        Prompt prompt = prompts.remove(p.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (System.currentTimeMillis() > prompt.expireAt()) {
            p.sendMessage(LEGACY.deserialize("&7输入已超时，菜单未改动。"));
            return;
        }
        event.setCancelled(true);
        String text = PLAIN.serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(p, prompt, text));
    }

    private void applyInput(Player p, Prompt prompt, String text) {
        String input = text == null ? "" : text.trim();
        boolean reopen = true;
        if (input.equalsIgnoreCase("cancel")) {
            p.sendMessage(LEGACY.deserialize("&7已取消。"));
        } else if (prompt.field() == Field.MATERIAL) {
            Material m = MenuLayout.matchMaterial(input);
            if (m == null) {
                p.sendMessage(LEGACY.deserialize("&c没有叫 &e" + input + "&c 的材质，未改动。"));
            } else {
                MenuLayout.Entry old = layout.entry(prompt.place(), prompt.game(), prompt.slot());
                layout.setItem(prompt.place(), prompt.game(), String.valueOf(prompt.slot()), m.name(),
                        old == null ? null : old.name(), old == null ? null : old.lore(), old == null ? null : old.action());
                layout.save();
                p.sendMessage(LEGACY.deserialize("&a材质已改为 &f" + m.name() + "&a。"));
            }
        } else if (prompt.field() == Field.NAME) {
            if (input.equalsIgnoreCase("clear")) {
                layout.setName(prompt.place(), prompt.game(), prompt.slot(), null);
                layout.save();
                p.sendMessage(LEGACY.deserialize("&a已恢复自动显示名。"));
            } else {
                layout.setName(prompt.place(), prompt.game(), prompt.slot(), input);
                layout.save();
                p.sendMessage(LEGACY.deserialize("&a显示名已改为 &f" + input + "&a。"));
            }
        } else {
            if (input.equalsIgnoreCase("clear") || input.isEmpty()) {
                layout.setLore(prompt.place(), prompt.game(), prompt.slot(), null);
                layout.save();
                p.sendMessage(LEGACY.deserialize("&a提示已恢复默认。"));
            } else {
                List<String> lines = new ArrayList<>();
                for (String line : input.split("\\|")) {
                    lines.add(line.trim());
                }
                layout.setLore(prompt.place(), prompt.game(), prompt.slot(), lines);
                layout.save();
                p.sendMessage(LEGACY.deserialize("&a已写入 &e" + lines.size() + "&a 行提示。"));
            }
        }
        if (reopen) {
            reopening.put(p.getUniqueId(), true);
            openMenu(p, prompt.place(), prompt.game(), true);
            reopening.remove(p.getUniqueId());
        }
    }

    private void sendHelp(Player p, MenuLayout.Place place, String game) {
        p.sendMessage(LEGACY.deserialize("&6===== 菜单编辑模式" + (place == MenuLayout.Place.SUB ? ("（" + game + "）") : "") + " ====="));
        p.sendMessage(LEGACY.deserialize("&e左键放下 &7光标物品到该格（原动作保留）&8｜&e右键 &7复制该格物品到光标"));
        p.sendMessage(LEGACY.deserialize("&eSHIFT+左键 &7改材质 &8｜&eSHIFT+右键 &7改显示名 &8｜&e双击 &7改提示(lore，用 | 分行)"));
        p.sendMessage(LEGACY.deserialize("&eQ &7删除该格 &8｜&e/hub &7退出编辑（不关菜单则留在编辑模式）"));
        p.sendMessage(LEGACY.deserialize("&7提示用 &f{arena} {players} {extra} {state} &7占位符可自动填场地信息。"));
    }

    // ---------- 库存保护 / 拖拽 ----------

    /** 菜单 GUI 放行，其它 GUI（箱子等）照旧锁住；锁定时禁止拖拽。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (isMenu(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
            if (isEditing(p)) {
                p.sendMessage(LEGACY.deserialize("&7编辑请用：左键 / 右键 / SHIFT+左键 / SHIFT+右键 / 双击 / Q。"));
            }
            return;
        }
        if (plugin.getManager().isInLobby(p.getUniqueId()) && plugin.getConfig().getBoolean("lock-inventory", true)) {
            event.setCancelled(true);
        }
    }

    /** 编辑模式里关闭菜单：给一次提示；聊天输入期间关闭不算退出。 */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) {
            return;
        }
        UUID id = p.getUniqueId();
        if (prompts.containsKey(id)) {
            return; // 正在等聊天输入，编辑状态保留
        }
        if (isEditing(p) && !Boolean.TRUE.equals(reopening.get(id))) {
            editing.remove(id);
            p.sendMessage(LEGACY.deserialize("&7已退出菜单编辑模式（改动已保存）。"));
        }
    }
}
