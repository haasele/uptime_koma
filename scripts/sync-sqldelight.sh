#!/usr/bin/env bash
# Regenerate SQLDelight Kotlin into shared/src (committed for Kotlin Toolchain).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./gradlew :shared:generateCommonMainKomaDatabaseInterface --quiet
SRC="$ROOT/shared/src"
GEN="$ROOT/shared/build/generated/sqldelight/code/KomaDatabase/commonMain"
if [[ ! -d "$GEN" ]]; then
  echo "SQLDelight output missing: $GEN" >&2
  exit 1
fi
# Remove previous generated package tree (keep hand-written sources outside db/)
# Only wipe known generated package
rm -rf "$SRC/dev/haasele/koma/shared/db"
mkdir -p "$SRC/dev/haasele/koma/shared/db"
cp -a "$GEN/dev/haasele/koma/shared/db/." "$SRC/dev/haasele/koma/shared/db/"
echo "Synced SQLDelight → shared/src/dev/haasele/koma/shared/db/"
