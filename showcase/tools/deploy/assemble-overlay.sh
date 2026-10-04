#!/usr/bin/env bash
# assemble-overlay.sh <out-dir>: build the folder push-overlay.py turns into the image layer, from the repo alone.
#   out/*.js             current server modules, including debt and Arcade APIs
#   out/site/            showcase/prismet-site/dist (build it first: node showcase/prismet-site/build.mjs)
#   out/public/          Steam and Debt pages/assets from showcase/server/public
#   out/whiteouts.txt    paths deleted from the image: public/shots (old captures, some with host names)
#   out/fly.toml         the app's service config, so `fly deploy` can run from <out-dir> too
set -euo pipefail
out=${1:?out dir}
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
dist="$root/showcase/prismet-site/dist"
[ -f "$dist/index.html" ] || { echo "assemble-overlay: $dist has no index.html; run node showcase/prismet-site/build.mjs first" >&2; exit 1; }
grep -q "editor.js\|data-edit" "$dist/index.html" && { echo "assemble-overlay: dist contains the preview editor; refusing" >&2; exit 1; }
rm -rf "$out"; mkdir -p "$out/public"
cp "$root/showcase/server/"*.js "$out/"
cp -r "$dist" "$out/site"
for asset in steam.html steam.js debt.html debt.js debt.css debt-metrics.js debt-estimate.js debt-discovery.js; do
  cp "$root/showcase/server/public/$asset" "$out/public/"
done
if grep -qE '7656[0-9]{13}' "$out"/public/*; then echo "assemble-overlay: a SteamID64 is in public/; run scrub-public.sh" >&2; exit 1; fi
printf 'public/shots\n' > "$out/whiteouts.txt"
cp "$here/fly.toml" "$out/fly.toml"
echo "assemble-overlay: $out ready ($(find "$out/site" -type f | wc -l) site files)"
