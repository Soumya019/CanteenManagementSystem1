#!/bin/bash
# Render an HTML figure to PNG with Chromium, then crop to the exact canvas size.
# Chromium's headless screenshot clips slightly short of the requested viewport,
# so we render into a taller window and crop back down.
set -e
CHROME=/opt/pw-browsers/chromium-1194/chrome-linux/chrome
DIR="$(cd "$(dirname "$0")" && pwd)"
NAME=$1; W=$2; H=$3
PAD=260
mkdir -p "$DIR/img"
"$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size="$W,$((H+PAD))" \
  --screenshot="$DIR/img/$NAME.png" "file://$DIR/$NAME.html" >/dev/null 2>&1
python3 - "$DIR/img/$NAME.png" "$W" "$H" <<'PY'
import sys
from PIL import Image
path, w, h = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
im = Image.open(path)
scale = im.width / w
im.crop((0, 0, im.width, min(im.height, int(h*scale)))).save(path)
print(f"{path.split('/')[-1]} -> {Image.open(path).size}")
PY
