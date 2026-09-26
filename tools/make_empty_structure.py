"""Generate an empty structure template (.nbt) for GameTests.

Why this exists: the datapack route of StructureTemplateManager only reads
BINARY .nbt files (FileToIdConverter("structure", ".nbt")); .snbt files are
only honored by the IDE-only `gameteststructures/` filesystem path, which
drops the namespace. So GameTest templates shipped in the mod must be .nbt.

Usage:
    python3 tools/make_empty_structure.py [width height height] [output]

Defaults: 3x3x3 -> src/main/resources/data/thetruth/structure/smoke.nbt
"""

import gzip
import struct
import sys

DATA_VERSION = 3955  # MC 1.21.1


def tag_string(s: str) -> bytes:
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def field(tag_type: int, name: str, payload: bytes) -> bytes:
    return bytes([tag_type]) + tag_string(name) + payload


def lst(elem_type: int, items: list) -> bytes:
    return bytes([elem_type]) + struct.pack(">i", len(items)) + b"".join(items)


def build_template(size: tuple) -> bytes:
    body = b""
    body += field(9, "size", lst(3, [struct.pack(">i", v) for v in size]))
    body += field(9, "entities", lst(0, []))
    body += field(9, "blocks", lst(0, []))
    body += field(9, "palette", lst(0, []))
    body += field(3, "DataVersion", struct.pack(">i", DATA_VERSION))
    body += b"\x00"  # TAG_End
    return b"\x0a" + tag_string("") + body


def main() -> None:
    args = sys.argv[1:]
    if len(args) == 4:
        size = (int(args[0]), int(args[1]), int(args[2]))
        out = args[3]
    else:
        size, out = (3, 3, 3), "src/main/resources/data/thetruth/structure/smoke.nbt"

    raw = build_template(size)
    with open(out, "wb") as f:
        f.write(gzip.compress(raw, mtime=0))
    print(f"wrote {out}: size={size}, {len(gzip.compress(raw, mtime=0))} bytes")


if __name__ == "__main__":
    main()
