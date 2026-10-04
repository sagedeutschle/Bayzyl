#!/usr/bin/env bash
# render.sh: renders the site's leaves (foliage.html) to showcase/assets/foliage/*.webp.
#   CHROME=/path/to/chromium showcase/tools/foliage/render.sh
# Needs a Chromium (headless) and cwebp. Each scene is captured on a transparent ground, then scaled and encoded.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out="$here/../../assets/foliage"
chrome=${CHROME:-$(ls -d "$HOME"/Library/Caches/ms-playwright/chromium-*/chrome-mac*/Chromium.app/Contents/MacOS/Chromium 2>/dev/null | tail -1)}
[ -x "$chrome" ] || { echo "render.sh: set CHROME to a Chromium binary" >&2; exit 1; }
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir -p "$out"
shot() {   # scene, capture width, capture height, output width
  "$chrome" --headless=new --hide-scrollbars --default-background-color=00000000 --window-size="$2,$3" \
    --screenshot="$tmp/$1.png" "file://$here/foliage.html#$1" >/dev/null 2>&1
  cwebp -quiet -q 80 -alpha_q 85 -m 6 -resize "$4" 0 "$tmp/$1.png" -o "$out/$1.webp"
  echo "$1.webp $(wc -c < "$out/$1.webp" | tr -d ' ') bytes"
}
shot wreath 1800 1800 1280
shot spray-tl 900 1300 620
shot spray-br 900 1200 620
