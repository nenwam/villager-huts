#!/usr/bin/env python3
"""Builds the platform the automated in-game tests run on.

Usage: python3 tools/make_test_platform.py
Writes src/main/resources/data/villagerhuts/structure/test/platform.nbt: a flat grass pad. The tests
build their own farm and chest on it (see gametest/VillagerHutsTests.java).
"""
import gzip
import struct
from pathlib import Path

DATA_VERSION = 5023  # Minecraft 26.3 (version.json "world_version")

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



# A grass pad with room for a farm, its chest, and margins.
PLATFORM_X, PLATFORM_Y, PLATFORM_Z = 17, 10, 24
platform = {}
for x in range(PLATFORM_X):
    for z in range(PLATFORM_Z):
        platform[(x, 0, z)] = ("grass_block", {"snowy": "false"}, None)
        for y in range(1, PLATFORM_Y):
            platform[(x, y, z)] = ("air", {}, None)
write_structure("test/platform", platform, (PLATFORM_X, PLATFORM_Y, PLATFORM_Z))
