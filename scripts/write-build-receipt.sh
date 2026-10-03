#!/usr/bin/env bash
# Usage: scripts/write-build-receipt.sh <jar-path>
# Writes a non-release receipt naming the exact jar: SHA-256, version, revision, and creation time (UTC).
# Output goes to $RECEIPT_DIR, default build/receipts/.
set -euo pipefail

jar="${1:-}"
if [[ -z "$jar" || ! -f "$jar" ]]; then
  echo "write-build-receipt: no jar at '${jar}'" >&2
  exit 2
fi

here="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="${RECEIPT_DIR:-$here/build/receipts}"

manifest="$(unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r')"
version="$(printf '%s\n' "$manifest" | sed -n 's/^Implementation-Version: //p')"
revision="$(printf '%s\n' "$manifest" | sed -n 's/^Bayzyl-Revision: //p')"
if command -v sha256sum >/dev/null 2>&1; then
  sha="$(sha256sum "$jar" | awk '{print $1}')"
else
  sha="$(shasum -a 256 "$jar" | awk '{print $1}')"
fi

mkdir -p "$out_dir"
receipt="$out_dir/$(basename "$jar").receipt"
{
  echo "jar: $(basename "$jar")"
  echo "sha256: $sha"
  echo "version: ${version:-unknown}"
  echo "revision: ${revision:-unknown}"
  echo "created: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$receipt"
echo "$receipt"
