#!/bin/bash
# check-routes.sh <base-url>: status, content type, size and cache-control for every route the
# apps and pages rely on. Run it against a local server before a deploy and against
# https://prismet.xyz after one; compare the two. Locally /api/wordle is 502 (no upstream)
# and /api/steam 500 (no key); live they are 200 and 400.
B=$1
for p in / /index.html /healthz /api/wordle /api/projects /api/steam /api/nope /steam /steam.html /debt /debt.html /style.css /steam.js /debt.js /site.js /icon.svg /manifest.webmanifest /og-image.png /shots/bayzyl-1-card.webp /shots/helm-1-full.webp /work/bayzyl.html /site.css /assets/fonts/fonts.css /assets/minecraft/bayzyl-scene-plaza-dome.webp /assets/og.jpg /nope /steam/ /../etc/passwd; do
  printf '%-34s ' "$p"; curl -s -o /tmp/probe.body -D /tmp/probe.h --path-as-is "$B$p" -w '%{http_code} ' ; printf '%-40s %7s  %s\n' "$(grep -i '^content-type' /tmp/probe.h | cut -d' ' -f2- | tr -d '\r')" "$(stat -c %s /tmp/probe.body)" "$(grep -i '^cache-control' /tmp/probe.h | cut -d' ' -f2- | tr -d '\r')"
done
