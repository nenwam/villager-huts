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
    "R": (90, 59, 30, 255),     # roof
    "r": (122, 82, 48, 255),    # roof edge
    "L": (107, 79, 47, 255),    # log corners / stake
    "W": (184, 148, 95, 255),   # planks
    "w": (156, 122, 72, 255),   # planks shadow
    "D": (74, 50, 24, 255),     # door
    "G": (168, 216, 240, 255),  # window
    "Y": (217, 177, 59, 255),   # wheat / flag
    "y": (176, 138, 36, 255),   # flag shade
    "g": (95, 158, 53, 255),    # stems / grass
}

HUT = [
    "................",
    ".......KK.......",
    "......KrrK......",
    ".....KrRRrK.....",
    "....KrRRRRrK....",
    "...KrRRRRRRrK...",
    "..KrRRRRRRRRrK..",
    ".KKKKKKKKKKKKKK.",
    "..KLWWWWWWWWLK..",
    "..KLWGGWWDDWLK..",
    "..KLWGGWWDDWLK..",
    "..KLWWWWWDDWLK..",
    "..KLwwwwwDDwLK..",
    ".KKKKKKKKKKKKKK.",
]

ICONS = {
    # Hut with wheat in front: hut + farm.
    "farmer_hut": HUT + [
        ".Y.Y.Y....Y.Y.Y.",
        ".g.g.g....g.g.g.",
    ],
    # Just the hut, on a strip of grass.
    "worker_hut": HUT + [
        "..gggggggggggg..",
        "................",
    ],
    # A wooden stake with a yellow pennant.
    "work_area_marker": [
        "................",
        "....KKKKK.......",
        "....KLYYYKK.....",
        "....KLYYYYYKK...",
        "....KLYYYYyyyK..",
        "....KLYYyyKK....",
        "....KLyyKK......",
        "....KLKK........",
        "....KLK.........",
        "....KLK.........",
        "....KLK.........",
        "....KLK.........",
        "....KLK.........",
        ".....KLK........",
        "......K.........",
        "................",
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
