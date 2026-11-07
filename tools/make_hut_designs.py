#!/usr/bin/env python3
"""Builds the starter hut structure files.

Usage: python3 tools/make_hut_designs.py
Writes src/main/resources/data/villagerhuts/structure/farmer_hut.nbt (hut + farm)
   and src/main/resources/data/villagerhuts/structure/worker_hut.nbt (hut only)

This is only a starting design. To use your own: build a hut in-game, save it with a
Structure Block named "villagerhuts:farmer_hut", and copy the saved .nbt (from the world's
generated/villagerhuts/structure/ folder) over the file above. See HutBuilder.java for the rules
a design has to follow (front edge, ground layer, "worker" marker).

Farmer Hut layout (seen from above, the player stands at the top, looking down/south).
The Worker Hut is the same without the farm rows:
    z 0-8   fenced 7x7 farmland (unplanted) with water in the middle, gap in the fence facing the hut
    z 9     path
    z 10-16 7x7 hut: door facing the farm, chest, composter, crafting table, lantern
"""
import gzip
import struct
from pathlib import Path

DATA_VERSION = 5023  # Minecraft 26.3 (version.json "world_version")
SIZE_X, SIZE_Y, SIZE_Z = 9, 6, 18

# ---------------------------------------------------------------- tiny NBT writer
TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 3, 8, 9, 10


class Int(int):
    pass


class NbtList(list):
    def __init__(self, tag_type, items):
        super().__init__(items)
        self.tag_type = tag_type


def tag_of(value):
    if isinstance(value, Int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, NbtList):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError(f"unsupported NBT value {value!r}")


def encode(value):
    tag = tag_of(value)
    if tag == TAG_INT:
        return struct.pack(">i", value)
    if tag == TAG_STRING:
        data = value.encode("utf-8")
        return struct.pack(">H", len(data)) + data
    if tag == TAG_LIST:
        return struct.pack(">bi", value.tag_type if value else 0, len(value)) + b"".join(encode(v) for v in value)
    out = b""
    for key, v in value.items():
        k = key.encode("utf-8")
        out += struct.pack(">bH", tag_of(v), len(k)) + k + encode(v)
    return out + b"\x00"


def ints(*values):
    return NbtList(TAG_INT, [Int(v) for v in values])


# ---------------------------------------------------------------- the design
blocks = {}  # (x, y, z) -> (block name, properties, block entity nbt)


def put(x, y, z, name, nbt=None, **props):
    blocks[(x, y, z)] = (name, props, nbt)


def flag(b):
    return "true" if b else "false"


# Farm: fence ring with a gap facing the hut, farmland inside, water in the center.
fence = {(x, z) for x in range(9) for z in range(9) if (x in (0, 8) or z in (0, 8)) and (x, z) != (4, 8)}
for x, z in fence:
    put(x, 1, z, "oak_fence",
        north=flag((x, z - 1) in fence), south=flag((x, z + 1) in fence),
        east=flag((x + 1, z) in fence), west=flag((x - 1, z) in fence), waterlogged="false")
for x in range(1, 8):
    for z in range(1, 8):
        if (x, z) == (4, 4):
            put(x, 0, z, "water", level="0")
        else:
            # Bare, watered farmland: the farmer plants it with seeds you put in the hut chest.
            put(x, 0, z, "farmland", moisture="7")

# Path from the fence gap to the door.
put(4, 0, 8, "dirt_path")
put(4, 0, 9, "dirt_path")

# Hut shell: cobblestone footing, log corners, plank walls, plank ceiling.
X0, X1, Z0, Z1 = 1, 7, 10, 16
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        edge = x in (X0, X1) or z in (Z0, Z1)
        corner = x in (X0, X1) and z in (Z0, Z1)
        put(x, 0, z, "cobblestone" if edge else "spruce_planks")
        if edge:
            for y in (1, 2, 3):
                if corner:
                    put(x, y, z, "oak_log", axis="y")
                else:
                    put(x, y, z, "oak_planks")
        put(x, 4, z, "spruce_planks")

# Door facing the farm.
for y, half in ((1, "lower"), (2, "upper")):
    put(4, y, 10, "oak_door", facing="south", half=half, hinge="left", open="false", powered="false")

# Windows (panes connect along the wall they sit in).
for x, z, along_x in ((2, 10, True), (6, 10, True), (1, 13, False), (7, 13, False), (4, 16, True)):
    put(x, 2, z, "glass_pane", east=flag(along_x), west=flag(along_x),
        north=flag(not along_x), south=flag(not along_x), waterlogged="false")

# Roof: slab lip around the edge, raised slab center.
for x in range(0, 9):
    for z in range(9, 18):
        if x in (0, 8) or z in (9, 17):
            put(x, 4, z, "spruce_slab", type="bottom", waterlogged="false")
for x in range(2, 7):
    for z in range(11, 16):
        put(x, 5, z, "spruce_slab", type="bottom", waterlogged="false")

# Inside.
put(6, 1, 15, "chest", facing="north", type="single", waterlogged="false")
put(2, 1, 15, "composter", level="0")
put(6, 1, 11, "crafting_table")
put(4, 3, 13, "lantern", hanging="true", waterlogged="false")
# Where the worker spawns (removed when the hut is built).
put(4, 1, 12, "structure_block", mode="data",
    nbt={"id": "minecraft:structure_block", "mode": "DATA", "metadata": "worker", "name": ""})

# Everything else above ground is air, so grass and flowers get cleared.
# Ground-level spots not listed are left as they are (natural ground).
for x in range(SIZE_X):
    for y in range(1, SIZE_Y):
        for z in range(SIZE_Z):
            blocks.setdefault((x, y, z), ("air", {}, None))

# ---------------------------------------------------------------- write the files
def write_structure(name, design, size):
    palette, palette_index, block_list = [], {}, []
    for (x, y, z) in sorted(design, key=lambda p: (p[1], p[2], p[0])):  # bottom layer first
        block_name, props, nbt = design[(x, y, z)]
        key = (block_name, tuple(sorted(props.items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            # 26.3 uses "id"/"properties" (older versions used "Name"/"Properties").
            entry = {"id": f"minecraft:{block_name}"}
            if props:
                entry["properties"] = dict(sorted(props.items()))
            palette.append(entry)
        block = {"pos": ints(x, y, z), "state": Int(palette_index[key])}
        if nbt:
            block["nbt"] = nbt
        block_list.append(block)

    root = {
        "DataVersion": Int(DATA_VERSION),
        "size": ints(*size),
        "palette": NbtList(TAG_COMPOUND, palette),
        "blocks": NbtList(TAG_COMPOUND, block_list),
        "entities": NbtList(TAG_COMPOUND, []),
    }
    data = b"\x0a" + struct.pack(">H", 0) + encode(root)  # unnamed root compound
    out = Path(__file__).resolve().parent.parent / f"src/main/resources/data/villagerhuts/structure/{name}.nbt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(data))
    print(f"wrote {out}: {len(block_list)} blocks, {len(palette)} block types")


# Farmer Hut: farm + path + hut.
write_structure("farmer_hut", blocks, (SIZE_X, SIZE_Y, SIZE_Z))

# Worker Hut: the same hut and path without the farm (drop rows z 0-8, shift the rest forward).
FARM_DEPTH = 9
hut_only = {(x, y, z - FARM_DEPTH): b for (x, y, z), b in blocks.items() if z >= FARM_DEPTH}
write_structure("worker_hut", hut_only, (SIZE_X, SIZE_Y, SIZE_Z - FARM_DEPTH))

# Test platform for the automated in-game tests (src/main/java/.../gametest): a grass pad with
# room for a Farmer Hut built facing south, plus margins.
PLATFORM_X, PLATFORM_Y, PLATFORM_Z = 17, 10, 24
platform = {}
for x in range(PLATFORM_X):
    for z in range(PLATFORM_Z):
        platform[(x, 0, z)] = ("grass_block", {"snowy": "false"}, None)
        for y in range(1, PLATFORM_Y):
            platform[(x, y, z)] = ("air", {}, None)
write_structure("test/platform", platform, (PLATFORM_X, PLATFORM_Y, PLATFORM_Z))
