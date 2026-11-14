package com.hubnpc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一个 NPC 的持久化数据。 */
final class NpcData {

    final String id;
    final List<String> commands = new ArrayList<>();
    UUID entityId;
    String world;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    /** 名字原文（保留 & / 渐变标签，改名时重新解析）。 */
    String displayName;
    /** 皮肤来源玩家名；没有设置皮肤时为 null。 */
    String skinName;
    String skinValue;
    String skinSignature;

    NpcData(String id) {
        this.id = id;
    }

    boolean hasSkinData() {
        return skinValue != null && !skinValue.isBlank();
    }

    Location location() {
        World target = Bukkit.getWorld(world);
        return target == null ? null : new Location(target, x, y, z, yaw, pitch);
    }

    void updateLocation(Location location) {
        world = location.getWorld().getName();
        x = location.getX();
        y = location.getY();
        z = location.getZ();
        yaw = location.getYaw();
        pitch = location.getPitch();
    }
}
