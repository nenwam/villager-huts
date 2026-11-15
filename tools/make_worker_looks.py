#!/usr/bin/env python3
"""Draws the workers' looks: 64x64 textures for Minecraft's villager model (no image libraries needed).

Usage: python3 tools/make_worker_looks.py
Writes src/main/resources/assets/villagerhuts/textures/entity/worker/*.png, one per look in
entity/WorkerLook.java (the file names must match the look ids there).

Each texture is the whole worker: skin, face, hair, clothes, and headwear (drawn on the model's hat
layer, which sits just outside the head). The pixel noise is seeded, so the output only changes when
this file does. Replace any of them with your own art any time (Blockbench, Aseprite, Piskel...).
"""
import random
import struct
import zlib
from pathlib import Path

SIZE = 64
CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------- colors

def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def scale(color, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in color[:3]) + (255,)


class Palette:
    """A material in four shades, picked at random per pixel like vanilla's textures."""

    def __init__(self, base, spread=0.06):
        self.base = rgb(base) if isinstance(base, str) else base
        self.light = scale(self.base, 1 + spread)
        self.shade = scale(self.base, 1 - spread)
        self.dark = scale(self.base, 1 - 2.5 * spread)

    def pick(self, rng):
        roll = rng.random()
        return self.base if roll < 0.55 else self.light if roll < 0.75 else self.shade


# Skin tones, wheatish to deep brown.
SKIN = {
    1: Palette("c8946a"),
    2: Palette("ad7a52"),
    3: Palette("94643f"),
    4: Palette("7a4f32"),
    5: Palette("5f3c26"),
}
HAIR_BLACK = Palette("1c1714", 0.12)
HAIR_GRAY = Palette("8a8580", 0.10)
EYE_WHITE = rgb("f2ede4")
EYE_BROWN = rgb("3a2416")
BROW = rgb("17110d")
GOLD = rgb("e0b13a")
STEEL = rgb("b9bec4")


# ---------------------------------------------------------------- the texture

class Texture:
    def __init__(self, seed):
        self.px = [[CLEAR] * SIZE for _ in range(SIZE)]
        self.rng = random.Random(seed)

    def set(self, x, y, color):
        self.px[y][x] = color

    def fill(self, rect, paint):
        """paint: a Palette, a color, or f(col, row, width, height) -> color/Palette/None (None = leave it)."""
        x0, y0, w, h = rect
        for row in range(h):
            for col in range(w):
                color = paint(col, row, w, h) if callable(paint) else paint
                if isinstance(color, Palette):
                    color = color.pick(self.rng)
                if color is not None:
                    self.px[y0 + row][x0 + col] = color

    def png(self):
        raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in self.px)

        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

        header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)  # 8-bit RGBA
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def box(u, v, w, h, d):
    """Where each face of a model box sits on the texture: (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),       # its last column touches the front
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),  # its first column touches the front
        "back": (u + 2 * d + w, v + d, w, h),
    }


# The villager model's boxes (net.minecraft.client.model.npc.VillagerModel).
HEAD = box(0, 0, 8, 10, 8)
HAT = box(32, 0, 8, 10, 8)      # the head again, a little bigger: headwear goes here
NOSE = box(24, 0, 2, 4, 2)
BODY = box(16, 20, 8, 12, 6)
ROBE = box(0, 38, 8, 20, 6)     # over the body, down to the ankles (the "jacket")
ARM = box(44, 22, 4, 8, 4)      # both side arms use it (one mirrored)
HANDS = box(40, 38, 8, 4, 4)    # the folded hands
LEG = box(0, 22, 4, 12, 4)      # both legs use it; only the bottom 3 rows show under the robe

SIDES = ("right", "front", "left", "back")


# ---------------------------------------------------------------- body parts

def skin(t, tone):
    for part in (HEAD, NOSE, ARM, HANDS, BODY, LEG):
        for rect in part.values():
            t.fill(rect, tone)
    # Shading like vanilla's: a darker jaw at the sides of the face, and a darker nose front.
    t.fill(HEAD["front"], lambda c, r, w, h: tone.shade if r >= 7 and c in (0, 7) else None)
    t.fill(NOSE["front"], lambda c, r, w, h: tone.shade)
    t.fill(NOSE["bottom"], lambda c, r, w, h: tone.dark)


def face(t, tone, mustache=None, beard=None, bindi=None, lips="6e3a2a"):
    front = HEAD["front"]
    x, y = front[0], front[1]
    for col in (1, 2, 5, 6):  # two brows (not one long villager brow)
        t.set(x + col, y + 5, BROW)
    for col, color in ((1, EYE_WHITE), (2, EYE_BROWN), (5, EYE_BROWN), (6, EYE_WHITE)):
        t.set(x + col, y + 6, color)
    t.set(x + 3, y + 6, tone.shade)
    t.set(x + 4, y + 6, tone.shade)
    for col in range(2, 6):
        t.set(x + col, y + 8, rgb(lips))
    if bindi:
        t.set(x + 3, y + 4, rgb(bindi))
        t.set(x + 4, y + 4, rgb(bindi))
    if mustache:
        for col in range(1, 7):
            t.set(x + col, y + 7, mustache.pick(t.rng))
    if beard:
        # Full beard: under the mouth, along the jaw and up the sides to the ears, and under the chin.
        t.fill(front, lambda c, r, w, h: beard if (r >= 7 and (c in (0, 1, 6, 7) or r == 9)) or (r == 7 and 1 <= c <= 6) else None)
        t.fill(HEAD["right"], lambda c, r, w, h: beard if r >= 5 and c >= 5 else None)
        t.fill(HEAD["left"], lambda c, r, w, h: beard if r >= 5 and c <= 2 else None)
        t.fill(HEAD["bottom"], lambda c, r, w, h: beard if r <= 4 else None)


def short_hair(t, hair):
    """Short hair: the top, a hairline at the front, the upper sides with sideburns, and most of the back."""
    t.fill(HEAD["top"], hair)
    t.fill(HEAD["front"], lambda c, r, w, h: hair if r == 0 else None)
    t.fill(HEAD["right"], lambda c, r, w, h: hair if r <= 2 or (r <= 4 and c == 6) or (r <= 4 and c <= 2) else None)
    t.fill(HEAD["left"], lambda c, r, w, h: hair if r <= 2 or (r <= 4 and c == 1) or (r <= 4 and c >= 5) else None)
    t.fill(HEAD["back"], lambda c, r, w, h: hair if r <= 5 else None)


def long_hair(t, tone, hair, part=True, earrings=GOLD):
    """Long hair parted in the middle and tied back in a braid; earrings at the ears."""
    t.fill(HEAD["top"], lambda c, r, w, h: tone.shade if part and c == 4 and r <= 5 else hair)
    t.fill(HEAD["front"], lambda c, r, w, h: hair if r == 0 or (r == 1 and c not in (3, 4)) or (r <= 3 and c in (0, 7)) else None)
    t.fill(HEAD["right"], lambda c, r, w, h: hair if r <= 2 or c <= 3 or (c >= 6 and r <= 4) else None)
    t.fill(HEAD["left"], lambda c, r, w, h: hair if r <= 2 or c >= 4 or (c <= 1 and r <= 4) else None)
    t.fill(HEAD["back"], hair)
    t.fill(HEAD["back"], lambda c, r, w, h: hair.dark if c in (3, 4) and r >= 4 and (r + c) % 2 == 0 else None)  # braid
    if earrings:
        t.set(HEAD["right"][0] + 5, HEAD["right"][1] + 7, earrings)
        t.set(HEAD["left"][0] + 2, HEAD["left"][1] + 7, earrings)


def braid_down_back(t, hair, rows=6):
    """The braid continues down the back of the robe."""
    t.fill(ROBE["back"], lambda c, r, w, h: (hair.dark if (r + c) % 2 == 0 else hair) if c in (3, 4) and r < rows else None)


def sleeves(t, cloth, rows, bangles=None, kara=False):
    """Sleeve cloth down the arms (rows of 8, from the shoulder); hands stay bare."""
    for face_name in SIDES:
        t.fill(ARM[face_name], lambda c, r, w, h: cloth if r < rows else None)
        if bangles and rows < 7:
            t.fill(ARM[face_name], lambda c, r, w, h: bangles[c % len(bangles)] if r == 7 else None)
        if kara:
            t.fill(ARM[face_name], lambda c, r, w, h: STEEL if r == 7 else None)
    t.fill(ARM["top"], cloth)


def feet(t, lower, sandal=rgb("4a2e1c")):
    """Under the robe only the ankles show: the hem of the lower garment, bare feet, and sandals."""
    for face_name in SIDES:
        t.fill(LEG[face_name], lambda c, r, w, h: lower if r <= 9 else sandal if r == 11 else None)
    t.fill(LEG["bottom"], sandal)


def robe(t, paint_front, paint_sides, paint_back, paint_top=None):
    t.fill(ROBE["front"], paint_front)
    t.fill(ROBE["right"], paint_sides)
    t.fill(ROBE["left"], paint_sides)
    t.fill(ROBE["back"], paint_back)
    t.fill(ROBE["top"], paint_top or paint_sides)
    t.fill(ROBE["bottom"], lambda c, r, w, h: CLEAR)  # open at the bottom: the legs show through
    for rect in BODY.values():  # hidden under the robe, but match it in case a resource pack shrinks it
        t.fill(rect, paint_sides)


def checks(a, b, size=2):
    return lambda c, r, w, h: a if ((c // size) + (r // size)) % 2 == 0 else b


# ---------------------------------------------------------------- headwear (hat layer)

def pagri(t, cloth, rows=3):
    """A wrapped turban: the whole top, and the upper rows all the way round, with diagonal folds."""
    t.fill(HAT["top"], lambda c, r, w, h: cloth.shade if (c + r) % 3 == 0 else cloth)
    for face_name in SIDES:
        t.fill(HAT[face_name], lambda c, r, w, h: (cloth.dark if (c + r) % 4 == 0 else cloth) if r <= rows else None)


def dastar(t, cloth):
    """A Sikh turban: neat layers meeting in a point over the forehead, covering the ears."""
    t.fill(HAT["top"], cloth)
    t.fill(HAT["front"], lambda c, r, w, h:
           (cloth.dark if abs(c - 3.5) + r in (3.5, 5.5) else cloth) if r <= 2 or (r == 3 and 1 <= c <= 6) or (r == 4 and c in (3, 4)) else None)
    t.fill(HAT["right"], lambda c, r, w, h: (cloth.shade if r % 2 else cloth) if r <= 4 else None)
    t.fill(HAT["left"], lambda c, r, w, h: (cloth.shade if r % 2 else cloth) if r <= 4 else None)
    t.fill(HAT["back"], lambda c, r, w, h: (cloth.shade if r % 2 else cloth) if r <= 5 else None)


def dumalla(t, cloth, emblem=STEEL):
    """A Nihang's dumalla: a big layered turban over the head, ears and back, with a steel chakkar (a ring) at the front."""
    t.fill(HAT["top"], lambda c, r, w, h: cloth.shade if (c + r) % 3 == 0 else cloth)
    t.fill(HAT["front"], lambda c, r, w, h:
           (cloth.dark if r in (1, 3) and (c + r) % 2 == 0 else cloth) if r <= 3 or (r == 4 and 2 <= c <= 5) else None)
    t.fill(HAT["right"], lambda c, r, w, h: (cloth.shade if r % 2 else cloth) if r <= 5 else None)
    t.fill(HAT["left"], lambda c, r, w, h: (cloth.shade if r % 2 else cloth) if r <= 5 else None)
    t.fill(HAT["back"], lambda c, r, w, h: (cloth.shade if r % 2 else cloth) if r <= 6 else None)
    x, y = HAT["front"][0], HAT["front"][1]
    for col, row in ((3, 0), (4, 0), (2, 1), (5, 1), (2, 2), (5, 2), (3, 3), (4, 3)):
        t.set(x + col, y + row, emblem)


def gamcha(t, a, b):
    """A checked cotton towel tied round the head like a band, knotted at the back."""
    pattern = checks(a, b, 1)
    for face_name in SIDES:
        t.fill(HAT[face_name], lambda c, r, w, h: pattern(c, r, w, h) if 1 <= r <= 3 else None)
    t.fill(HAT["back"], lambda c, r, w, h: pattern(c, r, w, h) if r in (4, 5) and c in (3, 4) else None)  # the knot's ends


def pallu_over_head(t, cloth, border):
    """The end of the saree drawn over the head: covers the top, back and sides, and frames the face."""
    t.fill(HAT["top"], lambda c, r, w, h: border if r == 0 else cloth)
    t.fill(HAT["front"], lambda c, r, w, h: border if r == 0 else cloth if c in (0, 7) and r <= 7 else None)
    t.fill(HAT["right"], lambda c, r, w, h: (border if c == 7 else cloth) if c >= 1 and (c <= 4 or r <= 1 or c == 7) and not (c == 7 and r >= 7) else None)
    t.fill(HAT["left"], lambda c, r, w, h: (border if c == 0 else cloth) if c <= 6 and (c >= 3 or r <= 1 or c == 0) and not (c == 0 and r >= 7) else None)
    t.fill(HAT["back"], lambda c, r, w, h: border if r == 9 else cloth)


# ---------------------------------------------------------------- outfits

def kurta_dhoti(t, kurta, dhoti, hem=15):
    """A long tunic (kurta) over a white wrapped dhoti."""
    def front(c, r, w, h):
        if r <= 1 and c in (3, 4):
            return SKIN_NOW.base if r == 0 else kurta.shade  # neckline
        if r < hem:
            return kurta.shade if (c == 4 and r <= 5) or r == hem - 1 else kurta
        return dhoti.shade if c in (2, 5) else dhoti

    def sides(c, r, w, h):
        return (kurta.shade if r == hem - 1 else kurta) if r < hem else dhoti

    robe(t, front, sides, sides)
    feet(t, dhoti)


def vest_lungi(t, vest, lungi_a, lungi_b, waist=11):
    """A sleeveless cotton vest, and a checked lungi wrapped round the waist."""
    lungi = checks(lungi_a, lungi_b, 2)

    def any_side(c, r, w, h):
        if r < waist:
            return vest
        if r == waist:
            return lungi_a.dark  # the tucked fold at the waist
        return lungi(c, r, w, h)

    def front(c, r, w, h):
        if r <= 2 and 2 <= c <= 5 and not (r == 2 and c in (2, 5)):
            return SKIN_NOW.pick(t.rng)  # the vest's scoop neck
        return any_side(c, r, w, h)

    robe(t, front, any_side, any_side)
    feet(t, lungi_a)


def saree(t, cloth, border, blouse, waist=9):
    """A saree: pleats below the waist with a border at the hem, and the pallu across the chest from the left shoulder."""
    def front(c, r, w, h):
        if r < waist:
            band = c - r  # the pallu runs diagonally across the chest
            if -1 <= band <= 2:
                return border if band == 2 else cloth
            if r <= 4 or band > 2:
                return blouse
            return SKIN_NOW.pick(t.rng) if r <= 6 else cloth  # a little waist shows between the blouse and the pleats
        if r >= 18:
            return border
        return cloth.shade if c in (3, 5) else cloth  # pleats

    def sides(c, r, w, h):
        return border if r >= 18 else cloth

    def back(c, r, w, h):
        return border if r >= 18 or r == 12 else cloth  # the pallu hangs to mid-back

    robe(t, front, sides, back)
    feet(t, cloth)


def salwar_kameez(t, kameez, print_color, salwar, dupatta, hem=14):
    """A long tunic (kameez) with a small print, loose trousers (salwar), and a dupatta round the shoulders."""
    def printed(c, r):
        return print_color if (c * 3 + r * 5) % 7 == 0 else kameez

    def front(c, r, w, h):
        if r <= 2 and (r == 2 or c in (0, 1, 6, 7)) or (r == 3 and c in (0, 7)):
            return dupatta  # draped round the neck, ends over the shoulders
        if r < hem:
            return printed(c, r)
        return salwar.shade if c in (1, 6) else salwar

    def sides(c, r, w, h):
        if r <= 1:
            return dupatta
        return printed(c, r) if r < hem else salwar

    def back(c, r, w, h):
        if r <= 1 or (c in (1, 6) and r <= 12):
            return dupatta  # its two ends hang down the back
        return printed(c, r) if r < hem else salwar

    robe(t, front, sides, back)
    feet(t, salwar)


def chola(t, cloth, sash, lower, gatra=None, hem=16, waist=8):
    """A warrior's long robe (chola) to below the knees over white pajama, tied at the waist with a sash (kamarkasa)
    whose end hangs down the front. gatra: the strap of the kirpan, across the chest from the right shoulder."""
    def any_side(c, r, w, h, strap=None):
        if r in (waist, waist + 1):
            return sash.shade if r == waist + 1 else sash
        if r >= hem:
            return lower
        if strap and r < waist and strap(c, r):
            return gatra
        return cloth.shade if r == hem - 1 else cloth

    def front(c, r, w, h):
        if r <= 1 and c in (3, 4):
            return SKIN_NOW.base if r == 0 else cloth.shade  # neckline
        if waist + 2 <= r <= waist + 5 and c in (5, 6):
            return sash  # the end of the sash, hanging down
        return any_side(c, r, w, h, (lambda col, row: col - row in (0, 1)) if gatra else None)

    def back(c, r, w, h):
        return any_side(c, r, w, h, (lambda col, row: col + row in (7, 8)) if gatra else None)

    robe(t, front, any_side, back)
    feet(t, lower)


# ---------------------------------------------------------------- the looks

LOOKS = {}
SKIN_NOW = SKIN[1]  # the skin of the look being drawn (outfits show some skin at the neck)


def look(name):
    def register(draw):
        LOOKS[name] = draw
        return draw
    return register


@look("pagri_kurta")
def _(t):
    """White kurta and dhoti, saffron pagri, black moustache."""
    skin(t, SKIN[2])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[2], mustache=HAIR_BLACK)
    kurta_dhoti(t, Palette("ece6d6", 0.04), Palette("f5f2ea", 0.03))
    sleeves(t, Palette("ece6d6", 0.04), 6)
    pagri(t, Palette("e8892a"))


@look("gamcha_lungi")
def _(t):
    """Cotton vest and green checked lungi, red-and-white gamcha tied round the head."""
    skin(t, SKIN[4])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[4], mustache=HAIR_BLACK)
    vest_lungi(t, Palette("eeeae0", 0.04), Palette("2f7a4a"), Palette("1f4f7a"))
    gamcha(t, Palette("c23b2e"), Palette("f1ece2", 0.03))


@look("dastar")
def _(t):
    """Sky-blue kurta and white pajama, navy dastar, full beard, and a steel kara on the wrist."""
    skin(t, SKIN[1])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[1], mustache=HAIR_BLACK, beard=HAIR_BLACK)
    kurta_dhoti(t, Palette("8cb8dc"), Palette("f2efe8", 0.03))
    sleeves(t, Palette("8cb8dc"), 7, kara=True)
    dastar(t, Palette("1f2f63"))


@look("elder")
def _(t):
    """An older farmer: rust-brown kurta, white dhoti, gray hair and moustache, no headwear."""
    skin(t, SKIN[3])
    short_hair(t, HAIR_GRAY)
    face(t, SKIN[3], mustache=HAIR_GRAY)
    kurta_dhoti(t, Palette("9a4f2c"), Palette("f0ece2", 0.03), hem=14)
    sleeves(t, Palette("9a4f2c"), 6)


@look("saree_green")
def _(t):
    """Green saree with a gold border, its pallu over her head, red blouse, red-and-gold bangles, bindi."""
    skin(t, SKIN[2])
    long_hair(t, SKIN[2], HAIR_BLACK)
    face(t, SKIN[2], bindi="b3202a", lips="7a3a30")
    saree(t, Palette("2f8f4e"), Palette("e0b13a", 0.05), Palette("b3302a"))
    sleeves(t, Palette("b3302a"), 3, bangles=(rgb("c0262b"), GOLD))
    pallu_over_head(t, Palette("2f8f4e"), Palette("e0b13a", 0.05))


@look("saree_pink")
def _(t):
    """Pink saree with an orange border, hair in a braid, green glass bangles, bindi."""
    skin(t, SKIN[4])
    long_hair(t, SKIN[4], HAIR_BLACK)
    face(t, SKIN[4], bindi="7a1430", lips="5e2a24")
    saree(t, Palette("d0457f"), Palette("f08a24", 0.05), Palette("f08a24"))
    sleeves(t, Palette("f08a24"), 3, bangles=(rgb("2e9e4a"), rgb("1f7a37")))
    braid_down_back(t, HAIR_BLACK)


@look("salwar_kameez")
def _(t):
    """Yellow printed kameez, white salwar, red dupatta, hair in a braid."""
    skin(t, SKIN[1])
    long_hair(t, SKIN[1], HAIR_BLACK)
    face(t, SKIN[1], lips="8a4a3a")
    salwar_kameez(t, Palette("e6c02e"), rgb("d0602a"), Palette("f4f0e6", 0.03), Palette("c22a3a"))
    sleeves(t, Palette("e6c02e"), 6)
    braid_down_back(t, HAIR_BLACK, rows=4)


@look("saree_blue")
def _(t):
    """Indigo cotton saree with a red border, its pallu over her head, gold bangles, gray-streaked hair."""
    skin(t, SKIN[5])
    long_hair(t, SKIN[5], Palette("2a2724", 0.25))
    face(t, SKIN[5], lips="4a221c")
    saree(t, Palette("33427e"), Palette("c0392b", 0.05), Palette("c0392b"))
    sleeves(t, Palette("c0392b"), 3, bangles=(GOLD, rgb("b8902a")))
    pallu_over_head(t, Palette("33427e"), Palette("c0392b", 0.05))


# Fighters: all Sikhs, dressed as Sikh warriors.

@look("nihang_blue")
def _(t):
    """A Nihang: royal-blue chola, saffron sash, a tall blue dumalla with a steel chakkar, black beard, steel kara."""
    skin(t, SKIN[3])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[3], mustache=HAIR_BLACK, beard=HAIR_BLACK)
    chola(t, Palette("2a3f9e"), Palette("e8892a"), Palette("f2efe8", 0.03), gatra=rgb("2a2420"))
    sleeves(t, Palette("2a3f9e"), 7, kara=True)
    dumalla(t, Palette("24357f"))


@look("nihang_elder")
def _(t):
    """An older Nihang: saffron chola, blue sash, a navy dumalla with a steel chakkar, white beard, steel kara."""
    white = Palette("dcd8d0", 0.05)
    skin(t, SKIN[2])
    short_hair(t, white)
    face(t, SKIN[2], mustache=white, beard=white)
    chola(t, Palette("d9822b"), Palette("2a3f9e"), Palette("f2efe8", 0.03), gatra=rgb("2a2420"))
    sleeves(t, Palette("d9822b"), 7, kara=True)
    dumalla(t, Palette("1f2f63"))


@look("singh_kesri")
def _(t):
    """White kurta and pajama, navy sash, a saffron (kesri) dastar, black beard, the black gatra strap, steel kara."""
    skin(t, SKIN[1])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[1], mustache=HAIR_BLACK, beard=HAIR_BLACK)
    chola(t, Palette("ece6d6", 0.04), Palette("1f2f63"), Palette("f5f2ea", 0.03), gatra=rgb("221c18"), hem=14)
    sleeves(t, Palette("ece6d6", 0.04), 7, kara=True)
    dastar(t, Palette("e8892a"))


@look("kaur_blue")
def _(t):
    """A Kaur warrior: blue chola, saffron sash, a saffron dastar, the black gatra strap, steel kara."""
    skin(t, SKIN[2])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[2], lips="7a3a30")
    chola(t, Palette("2f4fae"), Palette("e8892a"), Palette("f2efe8", 0.03), gatra=rgb("2a2420"))
    sleeves(t, Palette("2f4fae"), 7, kara=True)
    dastar(t, Palette("e8892a"))


@look("kaur_kesri")
def _(t):
    """A Kaur warrior: saffron chola, blue sash, a navy dastar, steel kara."""
    skin(t, SKIN[4])
    short_hair(t, HAIR_BLACK)
    face(t, SKIN[4], lips="5e2a24")
    chola(t, Palette("e07b26"), Palette("2a3f9e"), Palette("f2efe8", 0.03))
    sleeves(t, Palette("e07b26"), 7, kara=True)
    dastar(t, Palette("1f2f63"))


def main():
    global SKIN_NOW
    folder = Path(__file__).resolve().parent.parent / "src/main/resources/assets/villagerhuts/textures/entity/worker"
    folder.mkdir(parents=True, exist_ok=True)
    for seed, (name, draw) in enumerate(LOOKS.items()):
        t = Texture(seed)
        SKIN_NOW = next(s for s in SKIN.values() if s is SKIN[TONES[name]])
        draw(t)
        (folder / f"{name}.png").write_bytes(t.png())
        print(f"wrote {folder / name}.png")


TONES = {"pagri_kurta": 2, "gamcha_lungi": 4, "dastar": 1, "elder": 3,
         "saree_green": 2, "saree_pink": 4, "salwar_kameez": 1, "saree_blue": 5,
         "nihang_blue": 3, "nihang_elder": 2, "singh_kesri": 1, "kaur_blue": 2, "kaur_kesri": 4}

if __name__ == "__main__":
    main()
