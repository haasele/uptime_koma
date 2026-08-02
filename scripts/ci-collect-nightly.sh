#!/usr/bin/env bash
# Collect nightly build artifacts into a flat directory with stable names.
#
# Usage: scripts/ci-collect-nightly.sh <out-dir> <name-prefix>
#   name-prefix e.g. UptimeKoma-nightly-42-abc1234
set -euo pipefail

OUT="${1:?out dir}"
PREFIX="${2:?name prefix}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

mkdir -p "$OUT"
copied=0

copy_one() {
  local src="$1"
  local dest_name="$2"
  if [ -f "$src" ]; then
    cp -f "$src" "${OUT}/${dest_name}"
    echo "collected ${dest_name} <- ${src}"
    copied=$((copied + 1))
  fi
}

copy_glob() {
  local pattern="$1"
  local dest_name="$2"
  local f
  # shellcheck disable=SC2086
  for f in $pattern; do
    [ -f "$f" ] || continue
    copy_one "$f" "$dest_name"
    return 0
  done
  return 0
}

# Desktop executable JAR (Kotlin Toolchain), then Compose uber-jar fallback
copy_one \
  "${ROOT}/build/tasks/_desktopApp_executableJarJvm/desktopApp-jvm-executable.jar" \
  "${PREFIX}-linux-x64-executable.jar"
if [ ! -f "${OUT}/${PREFIX}-linux-x64-executable.jar" ]; then
  copy_glob \
    "${ROOT}/desktopApp/build/compose/jars/"*.jar \
    "${PREFIX}-linux-x64-executable.jar"
fi

# Compose Desktop / jpackage outputs
copy_glob \
  "${ROOT}/desktopApp/build/compose/binaries/main-release/deb/"*.deb \
  "${PREFIX}-linux-amd64.deb"
copy_glob \
  "${ROOT}/desktopApp/build/compose/binaries/main-release/rpm/"*.rpm \
  "${PREFIX}-linux-x86_64.rpm"
copy_glob \
  "${ROOT}/desktopApp/build/compose/binaries/main-release/appimage/"*.AppImage \
  "${PREFIX}-linux-x86_64.AppImage"
copy_glob \
  "${ROOT}/desktopApp/build/compose/binaries/main-release/flatpak/"*.flatpak \
  "${PREFIX}-linux.flatpak"
copy_glob \
  "${ROOT}/desktopApp/build/compose/binaries/main-release/dmg/"*.dmg \
  "${PREFIX}-macos.dmg"
copy_glob \
  "${ROOT}/desktopApp/build/compose/binaries/main-release/msi/"*.msi \
  "${PREFIX}-windows.msi"

# Android (Kotlin Toolchain paths first, then Gradle)
copy_one \
  "${ROOT}/build/tasks/_androidApp_bundleAndroid/gradle-project-release.aab" \
  "${PREFIX}-android-release.aab"
copy_one \
  "${ROOT}/build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk" \
  "${PREFIX}-android-release-unsigned.apk"
copy_one \
  "${ROOT}/build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk" \
  "${PREFIX}-android-debug.apk"

copy_glob \
  "${ROOT}/androidApp/build/outputs/bundle/release/"*.aab \
  "${PREFIX}-android-release.aab"
copy_glob \
  "${ROOT}/androidApp/build/outputs/apk/release/"*.apk \
  "${PREFIX}-android-release-unsigned.apk"
copy_glob \
  "${ROOT}/androidApp/build/outputs/apk/debug/"*.apk \
  "${PREFIX}-android-debug.apk"

# Optional iOS framework zip (produced by the macOS job)
copy_glob \
  "${ROOT}/dist-ios/"*.zip \
  "${PREFIX}-ios-framework-simulator-arm64.zip"

if [ "$copied" -eq 0 ]; then
  echo "ERROR: no artifacts collected into ${OUT}" >&2
  exit 1
fi

echo "Collected ${copied} artifact(s) into ${OUT}"
ls -lah "$OUT"
