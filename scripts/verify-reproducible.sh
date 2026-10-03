#!/usr/bin/env bash
# Usage: scripts/verify-reproducible.sh
# Builds the plugin twice from identical copies of the sources in two isolated directories (same revision
# stamp) and compares the jar SHA-256. Exit 0 only when both jars are byte-identical. Uses cached dependencies.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/bayzyl-repro.XXXXXX")"
trap 'rm -rf "$work"' EXIT

revision="$(cd "$here" && git rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
if [[ "$revision" != unknown && -n "$(cd "$here" && git status --porcelain -- src build.gradle settings.gradle gradle.properties gradle gradlew scripts 2>/dev/null)" ]]; then
  revision="$revision-dirty"
fi

hash_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi
}

for side in a b; do
  mkdir -p "$work/$side"
  (cd "$here" && tar cf - src gradle gradlew build.gradle settings.gradle gradle.properties) | (cd "$work/$side" && tar xf -)
  (cd "$work/$side" && ./gradlew --offline -q jar -Pbayzyl.revision="$revision" >/dev/null)
done

jar_a="$(ls "$work"/a/build/libs/*.jar)"
jar_b="$(ls "$work"/b/build/libs/*.jar)"
hash_a="$(hash_of "$jar_a")"
hash_b="$(hash_of "$jar_b")"
echo "build A: $hash_a  $(basename "$jar_a")"
echo "build B: $hash_b  $(basename "$jar_b")"
if [[ "$hash_a" == "$hash_b" ]]; then
  echo "reproducible: yes"
else
  echo "reproducible: NO" >&2
  exit 1
fi
