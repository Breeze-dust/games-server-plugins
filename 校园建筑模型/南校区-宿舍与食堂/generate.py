"""Generate a Sponge v2 .schem from the campus footprint masks."""
import numpy as np
import gzip, struct, sys, json
from collections import defaultdict

FLOOR_H = 4
DORM_LEVELS = 9
CANTEEN_LEVELS = 3
SERVICE_LEVELS = 3
PAVILION_LEVELS = 2

PALETTE = {
    "wall": "minecraft:white_concrete",
    "fin": "minecraft:orange_terracotta",
    "glass": "minecraft:gray_stained_glass",
    "floor": "minecraft:smooth_stone",
    "roof": "minecraft:light_gray_concrete",
    "base": "minecraft:stone_bricks",
    "trim": "minecraft:orange_concrete",
    "metal": "minecraft:iron_block",
    "yellow": "minecraft:yellow_concrete",
    "dark": "minecraft:gray_concrete",
}


def boundary(mask):
    """True where a mask cell touches the outside."""
    m = np.zeros(mask.shape, dtype=bool)
    n = np.zeros_like(mask)
    n[1:, :] |= ~mask[:-1, :]
    n[:-1, :] |= ~mask[1:, :]
    n[:, 1:] |= ~mask[:, :-1]
    n[:, :-1] |= ~mask[:, 1:]
    n[0, :] = True
    n[-1, :] = True
    n[:, 0] = True
    n[:, -1] = True
    m = mask & n
    return m


def build(masks, out_path, data_version):
    voxels = {}
    H = max(
        DORM_LEVELS * FLOOR_H,
        CANTEEN_LEVELS * FLOOR_H,
        SERVICE_LEVELS * FLOOR_H,
    ) + 3
    W = masks["dorm"].shape[1]
    L = masks["dorm"].shape[0]

    def put(x, y, z, kind):
        voxels[(x, y, z)] = kind

    specs = [
        ("dorm", DORM_LEVELS, "wall", "fin"),
        ("service", SERVICE_LEVELS, "fin", "trim"),
        ("pavilion", PAVILION_LEVELS, "fin", "trim"),
        ("canteen", CANTEEN_LEVELS, "wall", "yellow"),
    ]
    for name, levels, wall_key, fin_key in specs:
        if name not in masks:
            continue
        m = masks[name]
        bnd = boundary(m)
        ys, xs = np.nonzero(m)
        for level in range(levels):
            y0 = level * FLOOR_H
            for x, z in zip(xs, ys):
                if level == 0 and y0 == 0:
                    put(x, y0, z, "base")
                else:
                    put(x, y0, z, "floor")
            if level == levels - 1:
                for x, z in zip(xs, ys):
                    put(x, y0 + FLOOR_H, z, "roof")
            bys, bxs = np.nonzero(bnd)
            for x, z in zip(bxs, bys):
                fin = (x * 3 + z * 5) % 11 < 2
                glazed = (x + 2 * z) % 3 != 0
                for dy in range(FLOOR_H):
                    y = y0 + dy
                    if fin:
                        put(x, y, z, fin_key)
                    elif glazed and 1 <= dy <= 2:
                        put(x, y, z, "glass")
                    else:
                        put(x, y, z, wall_key)
        # parapet
        top = (levels - 1) * FLOOR_H + FLOOR_H
        bys, bxs = np.nonzero(bnd)
        for x, z in zip(bxs, bys):
            put(x, top + 1, z, "trim" if name == "dorm" else "metal")

    write_schem(voxels, out_path, W, H, L, data_version)
    return voxels, (W, H, L)


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return out


def tag(t, name, payload):
    nb = name.encode("utf-8")
    return bytes([t]) + struct.pack(">H", len(nb)) + nb + payload


def tag_int(name, v):
    return tag(3, name, struct.pack(">i", v))


def tag_short(name, v):
    return tag(2, name, struct.pack(">h", v))


def tag_byte_array(name, data):
    return tag(7, name, struct.pack(">i", len(data)) + bytes(data))


def tag_string(name, s):
    b = s.encode("utf-8")
    return tag(8, name, struct.pack(">H", len(b)) + b)


def write_schem(voxels, path, W, H, L, data_version):
    palette = {}
    for kind in voxels.values():
        block = PALETTE[kind]
        if block not in palette:
            palette[block] = 0
    remap = {"minecraft:air": 0}
    for b in palette:
        remap[b] = len(remap)
    data = bytearray()
    for y in range(H):
        for z in range(L):
            for x in range(W):
                kind = voxels.get((x, y, z))
                idx = 0 if kind is None else remap[PALETTE[kind]]
                data += varint(idx)

    body = b""
    body += tag_int("Version", 2)
    body += tag_int("DataVersion", data_version)
    body += tag_short("Width", W)
    body += tag_short("Height", H)
    body += tag_short("Length", L)
    body += tag_int("PaletteMax", len(remap))
    pal = b""
    for b, i in remap.items():
        pal += tag_int(b, i)
    body += tag(10, "Palette", pal + b"\x00")
    body += tag_byte_array("BlockData", data)
    body += tag(9, "BlockEntities", b"\x09\x00\x00\x00\x00")
    raw = b"\x0a" + struct.pack(">H", len("Schematic")) + b"Schematic" + body + b"\x00"
    with gzip.open(path, "wb") as f:
        f.write(raw)
    print("wrote", path, "voxels", len(voxels), "palette", len(remap), "size", (W, H, L))


def main():
    npz = np.load(sys.argv[1])
    masks = {k: npz[k] for k in npz.files if k != "scale"}
    out = sys.argv[2]
    build(masks, out, 4884)


if __name__ == "__main__":
    main()
