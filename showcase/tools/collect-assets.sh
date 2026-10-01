#!/usr/bin/env bash
# collect-assets.sh — copy + compress the real project visuals used by the Fiverr kit and the
# prismet.xyz site into showcase/assets/. Re-run after a project ships new screenshots.
#
# Point these at your local clones (defaults match the cloud session that built this kit).
set -euo pipefail
KSCOPE="${KSCOPE:-$HOME/sagedeutschle/kaleidoscope}"   # Prismet monorepo
QR="${QR:-$HOME/sagedeutschle/qr-scanner}"
PRISMCODE="${PRISMCODE:-$HOME/prismcode}"
QUARK="${QUARK:-$HOME/proton-outlook-mod}"
HELM_SHOTS="${HELM_SHOTS:?set HELM_SHOTS to the folder render-helm-faces.py wrote}"
WEB_SHOTS="${WEB_SHOTS:?set WEB_SHOTS to the folder shoot-web.mjs wrote}"
OUT="$(cd "$(dirname "$0")/../assets" && pwd)"

webp() { convert "$1" -resize "$2" -quality "${4:-82}" "$3"; }  # src size out [quality]

# Prismet — App Store screenshot set (v14 final) + app icon + game tiles
for f in "$KSCOPE"/ios/docs/appstore-screenshots-v14/final/*.png; do
  webp "$f" 720x "$OUT/prismet/store-$(basename "${f%.png}").webp"
done
webp "$KSCOPE/ios/docs/appstore-screenshots-v14/shot_home.png" 720x "$OUT/prismet/raw-home.webp"
webp "$KSCOPE/ios/docs/appstore-screenshots-1.0.1/shot_snake.png" 720x "$OUT/prismet/raw-snake.webp"
webp "$KSCOPE/ios/docs/appstore-screenshots-1.0.1/shot_seabattle.png" 720x "$OUT/prismet/raw-seabattle.webp"
webp "$KSCOPE/ios/docs/appstore-screenshots-1.0.1/shot_checkers.png" 720x "$OUT/prismet/raw-checkers.webp"
webp "$KSCOPE/macos/Assets/icon-src/kaleidoscope_appicon_1024.png" 512x "$OUT/icons/prismet-app.webp" 90
mkdir -p "$OUT/prismet/tiles"
for f in "$KSCOPE"/ios/IconSources/preview/*_512.png; do
  n="$(basename "${f%_512.png}")"; webp "$f" 256x "$OUT/prismet/tiles/$n.webp" 90
done

# Desktop apps — icons
webp "$PRISMCODE/build/icon-src/prismcode-1024.png" 512x "$OUT/icons/prismcode.webp" 90
webp "$QUARK/build/quark-icon.png" 512x "$OUT/icons/quark.webp" 90

# Web tools — qr-scanner suite (repo screenshots + fresh Playwright captures)
webp "$QR/docs/screenshots/desktop.png" 1440x "$OUT/web/qr-scanner-desktop.webp"
webp "$QR/docs/screenshots/mobile.png" 600x "$OUT/web/qr-scanner-mobile.webp"
for f in "$WEB_SHOTS"/*.png; do webp "$f" 1440x "$OUT/web/$(basename "${f%.png}").webp"; done

# THE HELM — QML faces rendered offscreen from source (render-helm-faces.py)
# tailnet + comms are skipped on purpose: their sample data shows mesh IPs / clipboard text.
for n in chronos cpu gpu reactor fleet net transit diskmap diskpie worldclock orbital breakout \
         minesweeper procs citycontrol qlora forge snake vault toolbox theme llmbay; do
  convert "$HELM_SHOTS/org.helm.$n.png" -quality 88 "$OUT/helm/$n.webp"
done
echo "assets -> $OUT"; du -sh "$OUT"
