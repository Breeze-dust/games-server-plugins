package com.hub;

import com.hub.util.Chat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TAB 那种「固定格子」玩家列表：往 tab 里塞满空白假条目，让客户端排出 4 列 × 20 行，
 * 再把真玩家按分组（大厅 / 每个游戏）依次放进槽位，固定槽位可以写文字。
 *
 * <p>原理：客户端按 {@code listOrder} 从大到小排序、每 20 行一列、最多 4 列；
 * 服务端只给真玩家 {@code Player#setPlayerListOrder}，其余槽位由
 * {@code ClientboundPlayerInfoUpdatePacket} 的假条目占位（1.21.2+ 不会再污染 tab 补全）。
 * 反射调用 NMS，失败时自动降级为关闭（不影响原有 tab）。
 *
 * <p>配置（{@code src/main/resources/config.yml} 的模板 + 线上 config.yml）：
 * <pre>
 * tab:
 *   footer: ''
 *   layout:
 *     enabled: true
 *     columns: 4
 *     rows: 20
 *     empty-slot-text: ''
 *     empty-slot-ping: 0
 *     fixed-slots:
 *       '1': '&amp;6&amp;l小游戏服'
 * </pre>
 */
public final class TabLayout {

    private static final String UPDATE_PACKET = "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket";
    private static final String ENTRY_PACKET = UPDATE_PACKET + "$Entry";
    private static final String ACTION = UPDATE_PACKET + "$Action";
    private static final String REMOVE_PACKET = "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket";
    private static final String GAME_TYPE = "net.minecraft.world.level.GameType";
    private static final String NMS_COMPONENT = "net.minecraft.network.chat.Component";
    private static final String CHAT_DATA = "net.minecraft.network.chat.RemoteChatSession$Data";
    private static final String PROFILE = "com.mojang.authlib.GameProfile";
    private static final String PACKET = "net.minecraft.network.protocol.Packet";

    private final Plugin plugin;

    private boolean enabled;
    private int slots = 80;
    /** 槽位（1 起）→ 固定文字（legacy）。 */
    private final Map<Integer, String> fixedSlots = new LinkedHashMap<>();
    private String emptyText = "";
    private int emptyPing;

    /** 槽位 → 假条目 UUID（固定不变，客户端靠它区分条目）。 */
    private final Map<Integer, UUID> fakeIds = new LinkedHashMap<>();
    /** 槽位 → 上一次发给客户端的文字（没变就不重发）。 */
    private final Map<Integer, String> sentText = new HashMap<>();

    private boolean nmsReady;
    private String nmsError;
    private Constructor<?> entryCtor;
    private Constructor<?> updateCtor;
    private Constructor<?> removeCtor;
    private Constructor<?> profileCtor;
    private Class<?> actionClass;
    private Class<?> gameTypeClass;
    private Class<?> componentClass;
    private Class<?> packetClass;
    private Method asVanillaComponent;
    private Method getHandle;
    private Field connectionField;
    private Method sendMethod;

    public TabLayout(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 重新读配置（启动与 /hub reload 时调用）。 */
    public void reload() {
        boolean wasActive = isEnabled();
        enabled = plugin.getConfig().getBoolean("tab.layout.enabled", false);
        int columns = Math.max(1, Math.min(4, plugin.getConfig().getInt("tab.layout.columns", 4)));
        int rows = Math.max(1, Math.min(20, plugin.getConfig().getInt("tab.layout.rows", 20)));
        slots = columns * rows;
        emptyText = plugin.getConfig().getString("tab.layout.empty-slot-text", "");
        if (emptyText == null) {
            emptyText = "";
        }
        emptyPing = plugin.getConfig().getInt("tab.layout.empty-slot-ping", 0);
        fixedSlots.clear();
        if (plugin.getConfig().isConfigurationSection("tab.layout.fixed-slots")) {
            for (String key : plugin.getConfig().getConfigurationSection("tab.layout.fixed-slots").getKeys(false)) {
                int slot = parseInt(key, -1);
                String text = plugin.getConfig().getString("tab.layout.fixed-slots." + key, "");
                if (slot >= 1 && slot <= slots && text != null && !text.isEmpty()) {
                    fixedSlots.put(slot, text);
                }
            }
        }
        sentText.clear();
        if (wasActive) {
            // 关掉或改了格子数量：先把旧假条目从客户端清掉，再由 apply() 重新铺
            clearAll();
        }
        if (enabled) {
            prepareNms();
        }
    }

    public boolean isEnabled() {
        return enabled && nmsReady;
    }

    /** 固定槽位占用的位置（外部排版时跳过）。 */
    public boolean isFixedSlot(int slot) {
        return fixedSlots.containsKey(slot);
    }

    public int slotCount() {
        return slots;
    }

    /**
     * 重排一次：把玩家按传入顺序放进非固定槽位，其余槽位用假条目填满。
     *
     * @return 被放进格子的玩家；没排上的（格子不够）不在其中
     */
    public Map<UUID, Integer> apply(List<Player> orderedPlayers) {
        Map<UUID, Integer> assigned = new HashMap<>();
        if (!isEnabled()) {
            return assigned;
        }
        List<Object> changed = new ArrayList<>();
        List<UUID> removed = new ArrayList<>();
        int index = 0;
        for (int slot = 1; slot <= slots; slot++) {
            if (isFixedSlot(slot)) {
                collectFake(slot, fixedSlots.get(slot), changed, removed);
                continue;
            }
            Player player = index < orderedPlayers.size() ? orderedPlayers.get(index++) : null;
            if (player != null) {
                player.setPlayerListOrder(order(slot));
                assigned.put(player.getUniqueId(), slot);
                UUID fake = fakeIds.remove(slot);
                if (fake != null) {
                    sentText.remove(slot);
                    removed.add(fake);
                }
            } else {
                collectFake(slot, emptyText, changed, removed);
            }
        }
        // 超出格子的玩家排在最后（order 0）
        for (; index < orderedPlayers.size(); index++) {
            orderedPlayers.get(index).setPlayerListOrder(0);
        }
        if (!changed.isEmpty()) {
            Object packet = buildUpdate(changed);
            for (Player p : Bukkit.getOnlinePlayers()) {
                send(p, packet);
            }
        }
        if (!removed.isEmpty()) {
            Object packet = newRemove(removed);
            for (Player p : Bukkit.getOnlinePlayers()) {
                send(p, packet);
            }
        }
        return assigned;
    }

    /** 玩家进服：把当前所有假条目发给他。 */
    public void sendTo(Player viewer) {
        if (!isEnabled() || !viewer.isOnline()) {
            return;
        }
        List<Object> entries = new ArrayList<>();
        for (int slot = 1; slot <= slots; slot++) {
            if (fakeIds.containsKey(slot)) {
                entries.add(buildEntry(slot, sentText.getOrDefault(slot, "")));
            }
        }
        send(viewer, buildUpdate(entries));
    }

    /** 关闭布局时把假条目从所有客户端移除。 */
    public void clearAll() {
        if (!nmsReady || fakeIds.isEmpty()) {
            return;
        }
        Object packet = newRemove(new ArrayList<>(fakeIds.values()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, packet);
        }
        fakeIds.clear();
        sentText.clear();
    }

    // ---------- 内部：槽位 / 假条目 ----------

    /** 槽位 → listOrder（越大越靠前，1 号槽位排最前）。 */
    private int order(int slot) {
        return (slots + 1 - slot) * 10;
    }

    /** 收集这个槽位要发/要更新的假条目（文字没变就跳过）。 */
    private void collectFake(int slot, String legacy, List<Object> changed, List<UUID> removed) {
        String text = legacy == null ? "" : legacy;
        UUID existing = fakeIds.get(slot);
        if (existing != null) {
            if (text.equals(sentText.get(slot))) {
                return;
            }
            changed.add(buildEntry(slot, existing, text));
        } else {
            UUID id = UUID.nameUUIDFromBytes(("gamehub:tab:" + slot).getBytes(StandardCharsets.UTF_8));
            fakeIds.put(slot, id);
            changed.add(buildEntry(slot, id, text));
        }
        sentText.put(slot, text);
    }

    private Object buildEntry(int slot, String legacy) {
        UUID id = fakeIds.get(slot);
        return buildEntry(slot, id, legacy);
    }

    private Object buildEntry(int slot, UUID id, String legacy) {
        try {
            Object profile = profileCtor.newInstance(id, "");
            Component text = legacy == null || legacy.isEmpty() ? Component.empty() : Chat.color(legacy);
            Object nmsText = asVanillaComponent.invoke(null, text);
            Object survival = Enum.valueOf(gameTypeClass.asSubclass(Enum.class), "SURVIVAL");
            return entryCtor.newInstance(id, profile, true, emptyPing, survival, nmsText, false, order(slot), null);
        } catch (Exception e) {
            throw new IllegalStateException("构建 tab 假条目失败", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object buildUpdate(List<Object> entries) {
        try {
            EnumSet actions = EnumSet.noneOf(actionClass.asSubclass(Enum.class));
            actions.add(action("ADD_PLAYER"));
            actions.add(action("UPDATE_LISTED"));
            actions.add(action("UPDATE_LIST_ORDER"));
            actions.add(action("UPDATE_LATENCY"));
            actions.add(action("UPDATE_GAME_MODE"));
            actions.add(action("UPDATE_DISPLAY_NAME"));
            return updateCtor.newInstance(actions, entries);
        } catch (Exception e) {
            throw new IllegalStateException("构建 tab 数据包失败", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object action(String name) {
        return Enum.valueOf((Class<? extends Enum>) actionClass, name);
    }

    private Object newRemove(List<UUID> ids) {
        try {
            return removeCtor.newInstance(ids);
        } catch (Exception e) {
            throw new IllegalStateException("构建 tab 移除包失败", e);
        }
    }

    private void send(Player viewer, Object packet) {
        try {
            Object handle = getHandle.invoke(viewer);
            Object connection = connectionField.get(handle);
            sendMethod.invoke(connection, packet);
        } catch (Exception e) {
            if (nmsError == null) {
                nmsError = e.getClass().getSimpleName() + ": " + e.getMessage();
                plugin.getLogger().warning("[tab] 发送假条目失败，已降级为普通 tab：" + nmsError);
                nmsReady = false;
            }
        }
    }

    // ---------- 内部：反射准备 ----------

    private void prepareNms() {
        if (nmsReady) {
            return;
        }
        try {
            Class<?> entryClass = Class.forName(ENTRY_PACKET);
            actionClass = Class.forName(ACTION);
            gameTypeClass = Class.forName(GAME_TYPE);
            componentClass = Class.forName(NMS_COMPONENT);
            Class<?> chatDataClass = Class.forName(CHAT_DATA);
            Class<?> profileClass = Class.forName(PROFILE);
            packetClass = Class.forName(PACKET);
            Class<?> updateClass = Class.forName(UPDATE_PACKET);
            Class<?> removeClass = Class.forName(REMOVE_PACKET);

            profileCtor = profileClass.getConstructor(UUID.class, String.class);
            entryCtor = entryClass.getConstructor(UUID.class, profileClass, boolean.class, int.class,
                    gameTypeClass, componentClass, boolean.class, int.class, chatDataClass);
            updateCtor = updateClass.getConstructor(EnumSet.class, List.class);
            removeCtor = removeClass.getConstructor(List.class);
            asVanillaComponent = Class.forName("io.papermc.paper.adventure.PaperAdventure")
                    .getMethod("asVanilla", Component.class);
            getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
            connectionField = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            sendMethod = connectionField.getType().getMethod("send", packetClass);
            nmsReady = true;
            nmsError = null;
        } catch (Throwable e) {
            nmsReady = false;
            nmsError = e.getClass().getSimpleName() + ": " + e.getMessage();
            plugin.getLogger().warning("[tab] 无法初始化假条目（当前服务端版本不兼容），tab 布局已关闭：" + nmsError);
        }
    }

    private int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
