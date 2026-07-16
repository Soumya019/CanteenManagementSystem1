#!/usr/bin/env python3
"""
Fetch real product photos for the counter sales manual.

For every key in photo_queries.PHOTO_QUERIES this searches DuckDuckGo
Images, downloads the first usable candidate, normalises it (EXIF rotate,
white background, <=800px) and stores it as photos/<key>.png. All candidate
URLs are kept in photos/manifest.json so a bad pick can be swapped without
re-searching:

    python3 fetch_photos.py                 # fetch all missing keys
    python3 fetch_photos.py key1 key2       # (re)fetch specific keys
    python3 fetch_photos.py key1 --pick 3   # use candidate #3 from manifest
"""

import io
import json
import os
import random
import re
import subprocess
import sys
import time
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed

from PIL import Image, ImageOps

from photo_queries import PHOTO_QUERIES

HERE = os.path.dirname(os.path.abspath(__file__))
PHOTO_DIR = os.path.join(HERE, "photos")
MANIFEST = os.path.join(PHOTO_DIR, "manifest.json")

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")

BAD_HOSTS = ("fbsbx.com", "gstatic.com", "encrypted-tbn", "youtube.com",
             "ytimg.com", "tiktok", "instagram", "lookaside")


def curl(url, timeout=25, referer="https://duckduckgo.com/"):
    r = subprocess.run(
        ["curl", "-sS", "-L", "--max-time", str(timeout), "-A", UA,
         "-H", f"Referer: {referer}", url],
        capture_output=True)
    return r.returncode, r.stdout


def ddg_image_search(query, retries=3):
    """Return list of image result dicts from DuckDuckGo."""
    for attempt in range(retries):
        rc, body = curl("https://duckduckgo.com/?q=" +
                        urllib.parse.quote(query) + "&iax=images&ia=images")
        m = re.search(rb"vqd=([\d-]+)", body) or re.search(rb'vqd="([^"]+)"', body)
        if rc == 0 and m:
            vqd = m.group(1).decode()
            rc2, body2 = curl(
                "https://duckduckgo.com/i.js?l=us-en&o=json&q=" +
                urllib.parse.quote(query) + "&vqd=" + vqd)
            if rc2 == 0:
                try:
                    return json.loads(body2).get("results", [])
                except json.JSONDecodeError:
                    pass
        time.sleep(5 * (attempt + 1) + random.random() * 3)
    return []


def usable(url, width, height):
    if any(b in url for b in BAD_HOSTS):
        return False
    if width and height:
        if min(width, height) < 260:
            return False
        if max(width, height) / max(min(width, height), 1) > 3:
            return False
    return True


def normalise(raw):
    """Validate bytes as an image and return a clean RGB PNG-ready Image."""
    img = Image.open(io.BytesIO(raw))
    img.load()
    img = ImageOps.exif_transpose(img)
    if min(img.size) < 250:
        raise ValueError(f"too small {img.size}")
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        bg = Image.new("RGB", img.size, (255, 255, 255))
        bg.paste(img, mask=img.split()[-1])
        img = bg
    else:
        img = img.convert("RGB")
    img.thumbnail((800, 800), Image.LANCZOS)
    return img


def fetch_key(key, query, pick=None, manifest_entry=None):
    """Fetch one photo. Returns (key, ok, note, entry)."""
    if pick is not None and manifest_entry and manifest_entry.get("candidates"):
        cands = manifest_entry["candidates"]
        order = cands[pick:pick + 1]
        if not order:
            return key, False, f"pick {pick} out of range", manifest_entry
    else:
        results = ddg_image_search(query)
        cands = [dict(image=r.get("image", ""), page=r.get("url", ""),
                      w=r.get("width"), h=r.get("height"),
                      title=r.get("title", ""))
                 for r in results][:15]
        if not cands:
            return key, False, "no search results", dict(query=query, candidates=[])
        order = [c for c in cands if usable(c["image"], c.get("w"), c.get("h"))]
    entry = dict(query=query, candidates=cands)
    for i, c in enumerate(order):
        rc, raw = curl(c["image"])
        if rc != 0 or len(raw) < 6000:
            continue
        try:
            img = normalise(raw)
        except Exception:
            continue
        img.save(os.path.join(PHOTO_DIR, f"{key}.png"))
        entry.update(picked=c["image"], picked_page=c.get("page", ""),
                     picked_title=c.get("title", ""))
        return key, True, f"{img.size[0]}x{img.size[1]} {c['image'][:80]}", entry
    return key, False, "no candidate downloadable", entry


def main():
    os.makedirs(PHOTO_DIR, exist_ok=True)
    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST) as f:
            manifest = json.load(f)

    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    pick = None
    if "--pick" in sys.argv:
        pick = int(sys.argv[sys.argv.index("--pick") + 1])

    if args:
        keys = args
    else:
        keys = [k for k in PHOTO_QUERIES
                if not os.path.exists(os.path.join(PHOTO_DIR, f"{k}.png"))]
    print(f"fetching {len(keys)} keys", flush=True)

    ok = fail = 0
    with ThreadPoolExecutor(max_workers=3) as ex:
        futs = {}
        for k in keys:
            futs[ex.submit(fetch_key, k, PHOTO_QUERIES[k], pick,
                           manifest.get(k))] = k
            time.sleep(0.7 + random.random() * 0.8)  # stagger searches
        for fut in as_completed(futs):
            key, good, note, entry = fut.result()
            manifest[key] = entry
            ok += good
            fail += (not good)
            print(("OK  " if good else "FAIL") + f" {key}: {note}", flush=True)
            with open(MANIFEST, "w") as f:
                json.dump(manifest, f, indent=1)

    print(f"done: {ok} ok, {fail} failed", flush=True)


if __name__ == "__main__":
    main()
