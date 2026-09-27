#!/usr/bin/env python3
"""Generate deterministic placeholder textures for The Truth until M8 art pass.

Writes 16x16 truecolor PNGs by hand (no third-party deps, mirroring
tools/make_empty_structure.py). Re-running overwrites files byte-identically.
"""

import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "thetruth")

SIZE = 16


def lcg(seed):
    """Tiny deterministic PRNG so regenerating yields identical textures."""
    state = seed

    def rand():
        nonlocal state
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        return state / 0x7FFFFFFF

    return rand


def png_chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def write_png(path, pixels):
    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("BBB", *px) for px in row)
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header)
            + png_chunk(b"IDAT", zlib.compress(raw)) + png_chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(blob)
    print("wrote", path)


def certus_stone():
    """Grey-blue stratum base with pale quartz glints."""
    rand = lcg(20260926)
    base = (63, 71, 79)        # dark grey-blue stone
    glint = (191, 232, 234)    # pale certus quartz
    bright = (224, 250, 252)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            jitter = int(rand() * 26) - 13
            r, g, b = (max(0, min(255, c + jitter)) for c in base)
            # Slightly darker seams to hint block grain.
            if rand() < 0.12:
                r, g, b = r - 12, g - 12, b - 12
            row.append((r, g, b))
        pixels.append(row)
    # Deterministic quartz glint clusters: short vertical streaks.
    for _ in range(6):
        x = int(rand() * SIZE)
        y = int(rand() * (SIZE - 4))
        length = 2 + int(rand() * 3)
        color = glint if rand() < 0.6 else bright
        for dy in range(length):
            pixels[(y + dy) % SIZE][x] = color
    return pixels




def generic_noise(seed, base, accents, streaks=5):
    """Deterministic noisy block texture with accent streaks."""
    rand = lcg(seed)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            jitter = int(rand() * 26) - 13
            r, g, b = (max(0, min(255, c + jitter)) for c in base)
            row.append((r, g, b))
        pixels.append(row)
    for _ in range(streaks):
        x = int(rand() * SIZE)
        y = int(rand() * (SIZE - 3))
        length = 2 + int(rand() * 3)
        color = accents[int(rand() * len(accents))]
        for dy in range(length):
            pixels[(y + dy) % SIZE][x] = color
    return pixels


def generic_item(seed, base, core, ring):
    """Deterministic item texture: gem ring with bright core."""
    rand = lcg(seed)
    pixels = []
    cx = cy = (SIZE - 1) / 2
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            d = max(abs(x - cx), abs(y - cy))
            if d <= 2.5:
                color = core
            elif d <= 5.5:
                color = ring
            else:
                color = None
            if color is None:
                row.append((0, 0, 0))
            else:
                jitter = int(rand() * 18) - 9
                row.append(tuple(max(0, min(255, c + jitter)) for c in color))
        pixels.append(row)
    # The item layer needs transparency outside the gem; rewrite with alpha.
    header_alpha = bytearray()
    return pixels, True


def residual_matter():
    """Purple crystalline cluster on grey host rock."""
    rand = lcg(20260927)
    base = (96, 62, 128)
    bright = (188, 130, 235)
    pale = (222, 180, 250)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            jitter = int(rand() * 24) - 12
            r, g, b = (max(0, min(255, c + jitter)) for c in base)
            row.append((r, g, b))
        pixels.append(row)
    for _ in range(7):
        x = int(rand() * SIZE)
        y = int(rand() * (SIZE - 3))
        length = 2 + int(rand() * 3)
        color = bright if rand() < 0.6 else pale
        for dy in range(length):
            pixels[(y + dy) % SIZE][x] = color
    return pixels


def certus_solidifier():
    """Dark machine body with a cyan write-line across the middle."""
    rand = lcg(20260928)
    base = (52, 58, 66)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            jitter = int(rand() * 16) - 8
            r, g, b = (max(0, min(255, c + jitter)) for c in base)
            if 6 <= y <= 9:
                r, g, b = (120, 230, 235) if x % 4 != 3 else (80, 160, 170)
            row.append((r, g, b))
        pixels.append(row)
    return pixels


def umbilical_anchor():
    """Deep plinth with two crossing cyan/white strands."""
    rand = lcg(20260929)
    base = (44, 50, 60)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            jitter = int(rand() * 14) - 7
            r, g, b = (max(0, min(255, c + jitter)) for c in base)
            if x == y or x + y == SIZE - 1:
                r, g, b = (150, 235, 240)
            row.append((r, g, b))
        pixels.append(row)
    return pixels


def unformed_matter():
    """Pale violet static: matter data that was never written."""
    rand = lcg(20260930)
    base = (150, 120, 190)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            pick = rand()
            color = (232, 220, 250) if pick < 0.2 else (120, 92, 160) if pick < 0.5 else base
            jitter = int(rand() * 20) - 10
            row.append(tuple(max(0, min(255, c + jitter)) for c in color))
        pixels.append(row)
    return pixels


def certus_matrix():
    """Cyan-white crystal diamond."""
    pixels, _ = generic_item(20260931, (0, 0, 0), (214, 248, 250), (128, 216, 224))
    return pixels


def certus_core():
    """Golden core inside a cyan ring."""
    pixels, _ = generic_item(20260932, (0, 0, 0), (250, 214, 120), (110, 200, 215))
    return pixels


def certus_cell():
    """AE2-style cell: teal housing with data stripes."""
    rand = lcg(20260933)
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            border = x in (0, SIZE - 1) or y in (0, SIZE - 1)
            if border:
                row.append((36, 120, 130))
            elif 4 <= x <= 11:
                stripe = (150, 235, 240) if (y // 2) % 2 == 0 else (90, 180, 190)
                row.append(stripe)
            else:
                jitter = int(rand() * 12) - 6
                row.append((44, 52, 60 + jitter))
        pixels.append(row)
    return pixels


if __name__ == "__main__":
    write_png(os.path.join(ROOT, "textures", "block", "certus_stone.png"), certus_stone())
    write_png(os.path.join(ROOT, "textures", "block", "residual_matter.png"), residual_matter())
    write_png(os.path.join(ROOT, "textures", "block", "certus_solidifier.png"), certus_solidifier())
    write_png(os.path.join(ROOT, "textures", "block", "umbilical_anchor.png"), umbilical_anchor())
    write_png(os.path.join(ROOT, "textures", "item", "unformed_matter.png"), unformed_matter())
    write_png(os.path.join(ROOT, "textures", "item", "certus_matrix.png"), certus_matrix())
    write_png(os.path.join(ROOT, "textures", "item", "certus_core.png"), certus_core())
    write_png(os.path.join(ROOT, "textures", "item", "certus_cell.png"), certus_cell())
