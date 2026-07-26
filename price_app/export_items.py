#!/usr/bin/env python3
"""Export the counter-manual price list as the Android app's search index.

Reads SECTIONS from shelf_manual/generate_manual.py (the single source of
truth for item names and selling prices) and writes
price_app/app/assets/items.json with search keywords per item, so the app
never needs the network.
"""

import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "shelf_manual"))
from generate_manual import SECTIONS  # noqa: E402
from photo_queries import ITEM_PHOTO  # noqa: E402

PHOTO_SRC = os.path.join(HERE, "..", "shelf_manual", "photos")

# extra spoken-word aliases attached when the pattern appears in the name
ALIASES = [
    (r"\bBulb\b", "bulb light led lamp"),
    (r"Batten|Tube", "tube batten tubelight light"),
    (r"Panel", "panel light ceiling"),
    (r"Downlight", "downlight down light"),
    (r"Wire|Cable|Cord|Flex", "wire cable tar taar"),
    (r"Switch", "switch"),
    (r"Socket", "socket"),
    (r"Plug\b|Plugs", "plug top"),
    (r"MCB", "mcb breaker"),
    (r"Fuse", "fuse kitkat kit kat"),
    (r"Holder", "holder"),
    (r"Extension", "extension board flex box cord"),
    (r"Tape", "tape insulation"),
    (r"Clip", "clip"),
    (r"Screw", "screw"),
    (r"Wall Plugs|Gillis", "gitti gilli wall plug rawl"),
    (r"Cable Ties", "cable tie zip tie ties"),
    (r"Ceiling Rose", "ceiling rose"),
    (r"Tester", "tester line tester"),
    (r"Regulator", "regulator fan speed"),
    (r"Indicator", "indicator neon lamp"),
    (r"Bell", "bell push doorbell calling"),
    (r"Meter\b", "meter"),
    (r"Changeover", "changeover change over"),
    (r"Adaptor|Multi-plug", "adaptor adapter multiplug multi plug"),
    (r"Module Modular Box|Modular Box", "box module surface"),
    (r"Cover Plate", "plate cover front"),
    (r"Connector", "connector joint block"),
    (r"Polythene", "polythene poly packet plastic"),
    (r"Cap\b", "cap"),
    (r"Pins|Nails", "pin nail pins nails"),
    (r"Bed", "bed switch hanging"),
    (r"Main Switch", "main switch kitkat iron"),
    (r"Energy Meter", "energy meter bijli electric"),
]

UNIT_WORD = {"pc": "per piece", "roll": "per roll", "pkt": "per packet",
             "coil": "per coil", "mtr": "per metre", "len": "per length"}


def keywords(name, sub, label):
    text = f"{name} {sub or ''} {label or ''}"
    kws = []
    for pat, words in ALIASES:
        if re.search(pat, name, re.I):
            kws.append(words)
    # spoken number variants: "9W" -> "9 watt", "1.5 SQMM" -> "1.5 mm"
    for m in re.finditer(r"(\d+(?:\.\d+)?)\s*W\b", name, re.I):
        kws.append(f"{m.group(1)} watt {m.group(1)}w")
    for m in re.finditer(r"(\d+(?:\.\d+)?)\s*SQMM", name, re.I):
        kws.append(f"{m.group(1)} mm {m.group(1)} sqmm square mm")
    for m in re.finditer(r"(\d+)\s*MM\b", name, re.I):
        kws.append(f"{m.group(1)} mm")
    for m in re.finditer(r"(\d+)\s*A\b", name, re.I):
        kws.append(f"{m.group(1)} amp ampere {m.group(1)}a")
    for m in re.finditer(r"(\d+)/(\d+)", name):
        kws.append(f"{m.group(1)} by {m.group(2)} {m.group(1)}{m.group(2)}")
    return f"{text} {' '.join(kws)}"


def export_photo(key):
    """Convert shelf_manual/photos/<key>.png to a compact JPEG app asset.
    Returns the asset-relative path, or "" if the source is missing."""
    src = os.path.join(PHOTO_SRC, f"{key}.png")
    if not os.path.exists(src):
        return ""
    dst_dir = os.path.join(HERE, "app", "assets", "photos")
    os.makedirs(dst_dir, exist_ok=True)
    dst = os.path.join(dst_dir, f"{key}.jpg")
    if not os.path.exists(dst) or os.path.getmtime(src) > os.path.getmtime(dst):
        im = Image.open(src).convert("RGB")
        im.thumbnail((640, 640), Image.LANCZOS)
        im.save(dst, "JPEG", quality=80, optimize=True)
    return f"photos/{key}.jpg"


def main():
    items = []
    exported = set()
    for title, _color, sec_items in SECTIONS:
        for it in sec_items:
            key = ITEM_PHOTO.get(it["name"])
            photo = export_photo(key) if key else ""
            if photo:
                exported.add(key)
            items.append({
                "name": it["name"],
                "price": it["price"],
                "unit": UNIT_WORD[it["unit"]],
                "cat": title,
                "kw": keywords(it["name"], it.get("sub"), it.get("label")),
                "photo": photo,
                "est": bool(it.get("est")),
            })
    out = os.path.join(HERE, "app", "assets", "items.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        json.dump({"items": items}, f, ensure_ascii=False, indent=1)
    print(f"wrote {out} with {len(items)} items, {len(exported)} photos")


if __name__ == "__main__":
    main()
