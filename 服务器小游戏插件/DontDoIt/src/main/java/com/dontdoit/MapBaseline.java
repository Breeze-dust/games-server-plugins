package com.dontdoit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场地基线快照：把一块长方体区域的原方块状态存下来（调色板 + 索引），
 * 开局前恢复干净地图、结束时把玩家挖掉的方块还回去。
 */
public final class MapBaseline {

    private static final String MAGIC = "DDI-MAP-1";

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int width;
    private final int height;
    private final String[] palette;
    private final int[] indices;
    private final List<EntitySnapshot> entities;

    /** 基线里记录的一个实体（类型 + 位置 + 朝向）。 */
    public record EntitySnapshot(String type, double x, double y, double z, float yaw, float pitch) {
    }

    private MapBaseline(int minX, int minY, int minZ, int width, int height,
                        String[] palette, int[] indices, List<EntitySnapshot> entities) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.palette = palette;
        this.indices = indices;
        this.entities = entities;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return minY + height - 1;
    }

    public int volume() {
        return width * height * width;
    }

    public static MapBaseline capture(World world, int minX, int minY, int minZ, int width, int height) {
        Map<String, Integer> lookup = new LinkedHashMap<>();
        List<String> palette = new ArrayList<>();
        int[] data = new int[width * height * width];
        int i = 0;
        for (int y = minY; y < minY + height; y++) {
            for (int z = minZ; z < minZ + width; z++) {
                for (int x = minX; x < minX + width; x++) {
                    String key = world.getBlockAt(x, y, z).getBlockData().getAsString();
                    Integer id = lookup.get(key);
                    if (id == null) {
                        id = palette.size();
                        palette.add(key);
                        lookup.put(key, id);
                    }
                    data[i++] = id;
                }
            }
        }
        List<EntitySnapshot> entities = new ArrayList<>();
        BoundingBox box = new BoundingBox(minX, minY, minZ,
                minX + width, minY + height, minZ + width);
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof Player || entity.getType() == EntityType.ITEM
                    || entity.getType() == EntityType.EXPERIENCE_ORB) {
                continue;
            }
            Location loc = entity.getLocation();
            entities.add(new EntitySnapshot(entity.getType().getKey().toString(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        }
        return new MapBaseline(minX, minY, minZ, width, height,
                palette.toArray(new String[0]), data, entities);
    }

    public int entityCount() {
        return entities.size();
    }

    /** 方块 + 实体一起还原。返回改动过的方块数量。 */
    public int restore(World world) {
        int changed = restoreBlocks(world);
        restoreEntities(world);
        return changed;
    }

    private void restoreEntities(World world) {
        BoundingBox box = new BoundingBox(minX, minY, minZ,
                minX + width, minY + height, minZ + width);
        for (Entity entity : new ArrayList<>(world.getNearbyEntities(box))) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
        for (EntitySnapshot snap : entities) {
            NamespacedKey key = NamespacedKey.fromString(snap.type());
            EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
            if (type == null) {
                continue;
            }
            Location loc = new Location(world, snap.x(), snap.y(), snap.z(), snap.yaw(), snap.pitch());
            try {
                world.spawnEntity(loc, type);
            } catch (RuntimeException ignored) {
                // 单个实体生成失败不影响其它实体
            }
        }
    }

    /** @return 实际改动过的方块数量 */
    private int restoreBlocks(World world) {
        BlockData[] parsed = new BlockData[palette.length];
        int changed = 0;
        int i = 0;
        for (int y = minY; y < minY + height; y++) {
            for (int z = minZ; z < minZ + width; z++) {
                for (int x = minX; x < minX + width; x++) {
                    int id = indices[i++];
                    Block block = world.getBlockAt(x, y, z);
                    BlockData current = block.getBlockData();
                    if (parsed[id] == null) {
                        parsed[id] = Bukkit.createBlockData(palette[id]);
                    }
                    if (!current.equals(parsed[id])) {
                        block.setBlockData(parsed[id], false);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建目录 " + parent);
        }
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeUTF(MAGIC);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(palette.length);
            for (String s : palette) {
                out.writeUTF(s);
            }
            for (int v : indices) {
                out.writeInt(v);
            }
            out.writeInt(entities.size());
            for (EntitySnapshot e : entities) {
                out.writeUTF(e.type());
                out.writeDouble(e.x());
                out.writeDouble(e.y());
                out.writeDouble(e.z());
                out.writeFloat(e.yaw());
                out.writeFloat(e.pitch());
            }
        }
    }

    public static MapBaseline load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            String magic = in.readUTF();
            if (!MAGIC.equals(magic)) {
                throw new IOException("不是 DontDoIt 地图快照：" + file.getName());
            }
            int minX = in.readInt();
            int minY = in.readInt();
            int minZ = in.readInt();
            int width = in.readInt();
            int height = in.readInt();
            int paletteSize = in.readInt();
            if (width <= 0 || height <= 0 || paletteSize <= 0 || paletteSize > 100_000) {
                throw new IOException("快照数据不合法：" + file.getName());
            }
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = in.readUTF();
            }
            int volume = width * height * width;
            int[] indices = new int[volume];
            for (int i = 0; i < volume; i++) {
                indices[i] = in.readInt();
            }
            int entityCount = 0;
            List<EntitySnapshot> entities = new ArrayList<>();
            try {
                entityCount = in.readInt();
                for (int i = 0; i < entityCount; i++) {
                    entities.add(new EntitySnapshot(in.readUTF(),
                            in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readFloat()));
                }
            } catch (EOFException oldFormat) {
                // 旧版快照没有实体段：按 0 个实体处理
                entities.clear();
            }
            return new MapBaseline(minX, minY, minZ, width, height, palette, indices, entities);
        }
    }
}
