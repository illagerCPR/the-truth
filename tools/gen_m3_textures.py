"""Generate M3 placeholder textures.

- certus_anchor.png      16x16 block texture (dark metal + cyan core dot)
- uncertainty_noise.png  256x256 GUI overlay: a 4x4 atlas of 64x64 noise tiles
                         with faint white specks on transparency

Why hand-rolled PNG: keep the toolchain dependency-free (no PIL) the same way
tools/make_empty_structure.py builds binary .nbt by hand.

Usage: python3 tools/gen_m3_textures.py
"""

import random
import struct
import zlib

BLOCK_OUT = "src/main/resources/assets/thetruth/textures/block/certus_anchor.png"
NOISE_OUT = "src/main/resources/assets/thetruth/textures/gui/uncertainty_noise.png"


def chunk(tag: bytes, payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + tag + payload + struct.pack(
        ">I", zlib.crc32(tag + payload) & 0xFFFFFFFF
    )


def write_png(path: str, width: int, height: int, rows: list) -> None:
    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    data = zlib.compress(raw, 9)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", data)
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {path}: {width}x{height}, {len(png)} bytes")


def anchor_texture() -> None:
    random.seed(20260926)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            border = x in (0, 15) or y in (0, 15)
            if border:
                base = (28, 36, 44)
            else:
                n = random.randint(-6, 6)
                base = (48 + n, 58 + n, 70 + n)
            if 6 <= x <= 9 and 6 <= y <= 9:
                base = (120, 235, 235)
            if (x in (7, 8)) and (y in (7, 8)):
                base = (225, 255, 255)
            row.extend([base[0], base[1], base[2], 255])
        rows.append(row)
    write_png(BLOCK_OUT, 16, 16, rows)


def noise_texture() -> None:
    random.seed(194300)
    rows = []
    for y in range(256):
        row = []
        for x in range(256):
            # Sparse bright specks, denser towards tile edges for a grainy look.
            a = 255 if random.random() < 0.10 else 0
            shade = random.randint(200, 255)
            row.extend([shade, shade, 255, a])
        rows.append(row)
    write_png(NOISE_OUT, 256, 256, rows)


if __name__ == "__main__":
    anchor_texture()
    noise_texture()
