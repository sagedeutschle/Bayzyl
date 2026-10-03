#!/usr/bin/env bash
# Scrub account identifiers from the server's public/ tools before they go into an overlay layer.
# usage: scrub-public.sh <public-dir>   (a COPY of the image's /app/public; edits in place)
# - steam.html: drop the prefilled SteamID64 from the search box (value="7656…")
# - steam.js:   the Reset button clears the box instead of restoring that id
# Fails if any SteamID64 (17 digits starting 7656) is left in either file.
set -euo pipefail
d=${1:?public dir}
sed -i -E 's/ value="7656[0-9]{13}"//' "$d/steam.html"
sed -i -E "s/qEl\.value = '7656[0-9]{13}';/qEl.value = '';/" "$d/steam.js"
if grep -nE '7656[0-9]{13}' "$d/steam.html" "$d/steam.js"; then echo "scrub-public: a SteamID64 is still present" >&2; exit 1; fi
echo "scrub-public: steam.html and steam.js clean"
