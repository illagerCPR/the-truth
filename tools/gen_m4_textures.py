"""Generate M4 placeholder textures.

- certus_frame.png       16x16 block: riveted metal frame with a cyan channel
- quantum_entrance.png   16x16 block: dark housing around a swirling core
- entanglement_key.png   16x16 item: a pearl bound by amethyst arc filaments

Why hand-rolled PNG: keep the toolchain dependency-free (no PIL) the same way
tools/make_empty_structure.py builds binary .nbt by hand (the PNG writer is
copied from tools/gen_m3_textures.py).

Usage: python3 tools/gen_m4_textures.py
"""

import random
import struct
import zlib

BLOCK_OUT = "src/main/resources/assets/thetruth/textures/block/{}.png"
ITEM_OUT = "src/main/resources/assets/thetruth/textures/item/entanglement_key.png"


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


def clamp8(v: int) -> int:
    return max(0, min(255, v))


def frame_texture() -> None:
    random.seed(20260927)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            cross = x == y or x == 15 - y
            if edge or cross:
                n = random.randint(-5, 5)
                base = [34 + n, 44 + n, 54 + n, 255]
                # rivets in the corners
                if (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
                    base = [140, 150, 160, 255]
            elif x in (7, 8) or y in (7, 8):
                base = [72, 210, 210, 255]
            else:
                n = random.randint(-4, 4)
                base = [58 + n, 66 + n, 76 + n, 255]
            row.extend(base)
        rows.append(row)
    write_png(BLOCK_OUT.format("certus_frame"), 16, 16, rows)


def entrance_texture() -> None:
    random.seed(20260928)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = 3 <= x <= 12 and 3 <= y <= 12
            if edge:
                base = [24, 32, 42, 255]
            elif inner:
                # swirling cyan/violet core
                t = (x + y) / 30.0
                swirl = random.randint(-10, 10)
                base = [clamp8(int(90 + 60 * t + swirl)),
                        clamp8(int(200 - 40 * t + swirl)),
                        clamp8(220 + swirl), 255]
                if (x in (6, 7, 8, 9)) and (y in (6, 7, 8, 9)):
                    base = [235, 255, 255, 255]
            else:
                n = random.randint(-4, 4)
                base = [46 + n, 56 + n, 68 + n, 255]
            row.extend(base)
        rows.append(row)
    write_png(BLOCK_OUT.format("quantum_entrance"), 16, 16, rows)


def key_texture() -> None:
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            dist = (dx * dx + dy * dy) ** 0.5
            if dist <= 4.5:
                # pearl body with an off-center highlight
                if dist <= 1.6 and (dx + dy) < 0:
                    base = [240, 248, 255, 255]
                else:
                    base = [168, 130, 220, 255]
            elif 6.5 <= dist <= 8.0 and abs(dx) < 4.5 and abs(dy) < 4.5:
                # entanglement filament arc
                base = [110, 235, 225, 255]
            else:
                base = [0, 0, 0, 0]
            row.extend(base)
        rows.append(row)
    write_png(ITEM_OUT, 16, 16, rows)


if __name__ == "__main__":
    frame_texture()
    entrance_texture()
    key_texture()
