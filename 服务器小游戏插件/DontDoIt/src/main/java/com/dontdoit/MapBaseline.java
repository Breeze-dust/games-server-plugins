package com.dontdoit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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

    private MapBaseline(int minX, int minY, int minZ, int width, int height,
                        String[] palette, int[] indices) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.palette = palette;
        this.indices = indices;
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
        return new MapBaseline(minX, minY, minZ, width, height,
                palette.toArray(new String[0]), data);
    }

    /** @return 实际改动过的方块数量 */
    public int restore(World world) {
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
            return new MapBaseline(minX, minY, minZ, width, height, palette, indices);
        }
    }
}
