#!/usr/bin/env python3
"""Draws the mod's 16x16 item icons (no image libraries needed).

Usage: python3 tools/make_icons.py
Writes src/main/resources/assets/villagerhuts/textures/item/*.png
Replace any of them with your own art any time (Blockbench, Aseprite, Piskel...).
"""
import struct
import zlib
from pathlib import Path

COLORS = {
    ".": (0, 0, 0, 0),          # transparent
    "K": (43, 29, 14, 255),     # outline
    "Y": (217, 177, 59, 255),   # straw hat
    "y": (176, 138, 36, 255),   # straw shade
    "b": (110, 70, 35, 255),    # hat band
    "S": (189, 140, 110, 255),  # skin
    "N": (160, 108, 84, 255),   # nose
    "E": (40, 96, 52, 255),     # eyes
    "B": (122, 86, 48, 255),    # robe
    "g": (95, 158, 53, 255),    # wheat stem on the robe
}

ICONS = {
    # A villager farmer's head in a straw hat.
    "farmer": [
        "................",
        ".....KKKKKK.....",
        "....KYYYYYYK....",
        "....KYyYYyYK....",
        "..KKKbbbbbbKKK..",
        ".KYYYYYYYYYYYYK.",
        "..KKKKKKKKKKKK..",
        "...KSSSSSSSSK...",
        "...KSEESSEESK...",
        "...KSSSNNSSSK...",
        "...KSSSNNSSSK...",
        "...KKSSNNSSKK...",
        "..KBBKKKKKKBBK..",
        ".KBBBBBBBBBgBBK.",
        ".KBBBBBBBBgBBBK.",
        ".KKKKKKKKKKKKKK.",
    ],
}


def png(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    raw = b"".join(b"\x00" + b"".join(bytes(COLORS[c]) for c in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    header = struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)  # 8-bit RGBA
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


folder = Path(__file__).resolve().parent.parent / "src/main/resources/assets/villagerhuts/textures/item"
folder.mkdir(parents=True, exist_ok=True)
for name, pixels in ICONS.items():
    (folder / f"{name}.png").write_bytes(png(pixels))
    print(f"wrote {folder / name}.png")
