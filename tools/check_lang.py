#!/usr/bin/env python3
"""Language-key drift check (M8, docs/02 M8 acceptance: no missing keys).

Scans the Java sources for translation keys referenced by the mod (string
literals starting with "thetruth." or "block.thetruth." / "item.thetruth." /
"entity.thetruth." / "container.thetruth." / "biome.thetruth.") and compares
them against both language files. Also asserts the two language files define
exactly the same key set.

Exit code 0 = clean; 1 = drift found.
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/java")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/thetruth/lang")

KEY_PATTERN = re.compile(
    r'"(?:thetruth\.[A-Za-z0-9_.]+|block\.thetruth\.[A-Za-z0-9_.]+|'
    r'item\.thetruth\.[A-Za-z0-9_.]+|entity\.thetruth\.[A-Za-z0-9_.]+|'
    r'container\.thetruth\.[A-Za-z0-9_.]+|biome\.thetruth\.[A-Za-z0-9_.]+)"')


def referenced_keys():
    keys = set()
    for base, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith(".java"):
                continue
            with open(os.path.join(base, name), encoding="utf-8") as f:
                keys |= {m.group(0)[1:-1] for m in KEY_PATTERN.finditer(f.read())}
    return keys


def load_lang(name):
    with open(os.path.join(ASSETS, name), encoding="utf-8") as f:
        return json.load(f)


def main():
    refs = referenced_keys()
    problems = []
    for name in ("en_us.json", "zh_cn.json"):
        lang = load_lang(name)
        missing = refs - set(lang)
        if missing:
            problems.append(f"{name}: missing {len(missing)} key(s): {sorted(missing)}")
    en = set(load_lang("en_us.json"))
    zh = set(load_lang("zh_cn.json"))
    only_en = en - zh
    only_zh = zh - en
    if only_en:
        problems.append(f"keys only in en_us: {sorted(only_en)}")
    if only_zh:
        problems.append(f"keys only in zh_cn: {sorted(only_zh)}")
    # Keys defined but never referenced are fine (dynamic keys); report only.
    unused = en - refs
    if unused:
        print(f"note: {len(unused)} lang key(s) not directly referenced in code "
              f"(dynamic or meta keys): {sorted(unused)}")

    if problems:
        for p in problems:
            print("FAIL:", p)
        return 1
    print(f"OK: {len(refs)} referenced keys present in both languages; "
          f"{len(en)} keys per file, key sets identical.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
