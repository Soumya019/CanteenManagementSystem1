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


SIG_GRID = 4          # 4x4 colour-layout cells
SIG_HUE_BINS = 12     # coarse hue histogram
WHITE_V, WHITE_S = 232, 26   # background cut-off (bright + unsaturated)


def signature(path):
    """Compact colour fingerprint of a product photo, mirrored byte-for-byte
    by PhotoMatch.java on the phone.

    Backgrounds in both the bundled shots and a snapshot taken against the
    shop's white shelving are near-white, so those pixels are dropped and the
    fingerprint describes the product itself: a hue histogram (survives
    lighting changes) plus a 4x4 mean-colour grid (keeps rough layout).
    Returns 12 + 48 = 60 ints in 0..255, or None for an all-white image.
    """
    im = Image.open(path).convert("RGB").resize((64, 64), Image.LANCZOS)
    px = im.load()
    hue = [0.0] * SIG_HUE_BINS
    cells = [[0.0, 0.0, 0.0, 0] for _ in range(SIG_GRID * SIG_GRID)]
    kept = 0
    for y in range(64):
        cy = y * SIG_GRID // 64
        for x in range(64):
            r, g, b = px[x, y]
            mx, mn = max(r, g, b), min(r, g, b)
            chroma = mx - mn
            if mx >= WHITE_V and chroma <= WHITE_S:
                continue                      # white/grey background
            kept += 1
            cell = cells[cy * SIG_GRID + x * SIG_GRID // 64]
            cell[0] += r
            cell[1] += g
            cell[2] += b
            cell[3] += 1
            if chroma > 20:                   # only colourful pixels vote
                if mx == r:
                    h = (60.0 * (g - b) / chroma) % 360.0
                elif mx == g:
                    h = 60.0 * (b - r) / chroma + 120.0
                else:
                    h = 60.0 * (r - g) / chroma + 240.0
                hue[int(h * SIG_HUE_BINS / 360.0) % SIG_HUE_BINS] += chroma
    if kept < 40:
        return None
    peak = max(hue) or 1.0
    sig = [int(round(255.0 * v / peak)) for v in hue]
    for c in cells:
        if c[3]:
            sig += [int(c[0] / c[3]), int(c[1] / c[3]), int(c[2] / c[3])]
        else:
            sig += [255, 255, 255]
    return sig


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
    sigs = {}
    for title, _color, sec_items in SECTIONS:
        for it in sec_items:
            key = ITEM_PHOTO.get(it["name"])
            photo = export_photo(key) if key else ""
            sig = None
            if photo:
                exported.add(key)
                sig = sigs.get(key)
                if sig is None:
                    sig = signature(os.path.join(PHOTO_SRC, f"{key}.png"))
                    sigs[key] = sig
            items.append({
                "name": it["name"],
                "price": it["price"],
                "unit": UNIT_WORD[it["unit"]],
                "cat": title,
                "kw": keywords(it["name"], it.get("sub"), it.get("label")),
                "photo": photo,
                "est": bool(it.get("est")),
                "sig": sig,
            })
    out = os.path.join(HERE, "app", "assets", "items.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        json.dump({"items": items}, f, ensure_ascii=False, indent=1)
    n_sig = sum(1 for i in items if i["sig"])
    print(f"wrote {out} with {len(items)} items, {len(exported)} photos, "
          f"{n_sig} photo signatures")


if __name__ == "__main__":
    main()
