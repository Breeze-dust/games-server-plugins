package com.murdermystery;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「真理之石 · 选择探测目标」菜单的持有者（凶手以外的人右键真理之石打开）。
 * <p>
 * 按格子记下对应的玩家 UUID：点击时按格子取人，比"按 GUI 标题认"稳
 * （标题是可变文案、还可能是别的插件/别的场次开的同名菜单），也不会被顺手拿走物品。
 */
public class TruthMenuHolder implements InventoryHolder {

    /** 格子下标 → 该格头像对应的玩家。 */
    private final Map<Integer, UUID> targets = new LinkedHashMap<>();
    private Inventory inventory;

    /** 菜单建好后由管理器回填，{@link #getInventory()} 才能返回它。 */
    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    /** 记一个格子（slot）对应哪个玩家。 */
    public void target(int slot, UUID id) {
        targets.put(slot, id);
    }

    /** 取某个格子对应的玩家；空格/占位格返回 null。 */
    public UUID targetAt(int slot) {
        return targets.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
