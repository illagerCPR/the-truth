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


if __name__ == "__main__":
    write_png(os.path.join(ROOT, "textures", "block", "certus_stone.png"), certus_stone())
