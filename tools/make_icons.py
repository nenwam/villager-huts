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
    "T": (14, 63, 66, 255),     # diamond outline
    "c": (26, 170, 167, 255),   # diamond shade
    "D": (74, 237, 217, 255),   # diamond
    "L": (161, 251, 232, 255),  # diamond light
    "W": (255, 255, 255, 255),  # sparkle
    "k": (24, 60, 18, 255),     # carrot leaf outline
    "v": (52, 120, 34, 255),    # carrot leaf shade
    "V": (96, 176, 56, 255),    # carrot leaf
    "U": (40, 62, 160, 255),    # blue dastar
    "u": (26, 40, 112, 255),    # dastar folds, gatra strap
    "C": (200, 205, 212, 255),  # steel chakkar
    "e": (58, 36, 22, 255),     # brown eyes
    "w": (240, 236, 228, 255),  # eye whites
    "H": (28, 23, 20, 255),     # beard
    "O": (232, 137, 42, 255),   # saffron chola
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
    # A Sikh warrior: blue dastar with a steel chakkar, full beard, saffron chola with the gatra strap across it.
    "fighter": [
        ".....KKKKKK.....",
        "....KUUUUUUK....",
        "...KUUUCCUUUK...",
        "...KUUCuuCUUK...",
        "...KuUUCCUUuK...",
        "...KUuUUUUuUK...",
        "...KSSSSSSSSK...",
        "...KSweSSewSK...",
        "...KHSSNNSSHK...",
        "...KHHHNNHHHK...",
        "...KHHHHHHHHK...",
        "..KKKHHHHHHKKK..",
        ".KOOOKHHHHKuOOK.",
        ".KOOOOKKKKuOOOK.",
        ".KOOOOOOOuOOOOK.",
        ".KKKKKKKKKKKKKK.",
    ],
    # A carrot made of diamond.
    "diamond_carrot": [
        "..........k..k..",
        ".........kVk.kVk",
        ".........kVvkVvk",
        "..........kVvVvk",
        "........TTTvVvk.",
        ".......TLLDTkk..",
        "......TLWDDcT...",
        ".....TLLDDccT...",
        "....TLLDcDcT....",
        "....TLWDccT.....",
        "...TLDDccT......",
        "..TLLDcDT.......",
        "..TLDcTT........",
        ".TLDcT..........",
        ".TDcT...........",
        "..TT............",
    ],
    # The flawless one: paler, brighter, sparkling (it has the enchanted glint in the game too).
    "flawless_diamond_carrot": [
        "...L......k..k..",
        "..LWL....kVk.kVk",
        "...L.....kVvkVvk",
        "..........kVvVvk",
        "........TTTvVvk.",
        ".......TLWLTkk..",
        "......TWLLDLT...",
        ".....TLLWDDcT...",
        "....TLLDLDcT..L.",
        "....TWLLDcT..LWL",
        "...TLLDLcT....L.",
        "..TLWLcDT.......",
        "..TLLcTT........",
        ".TWLcT..........",
        ".TLcT...........",
        "..TT............",
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
