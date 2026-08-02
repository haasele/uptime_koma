#!/usr/bin/env bash
# Linux packaging for Uptime Koma (AppImage / Flatpak).
#
#   packaging/linux/package.sh appimage
#   packaging/linux/package.sh flatpak
#
# Requires :desktopApp:createDistributable first.
# Wayland/Java AWT is handled in-process (WaylandAwtBootstrap) and via Flatpak
# finish-args — no extra launcher shell scripts are emitted into the build tree.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VERSION="${APP_VERSION:-1.0.0}"
BINARIES_DIR="${BINARIES_DIR:-$ROOT/desktopApp/build/compose/binaries/main}"
ICON_SRC="${ICON_SRC:-$ROOT/packaging/linux/dev.haasele.KomaNative.png}"
SLUG="koma-native"
APP_ID="dev.haasele.KomaNative"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command>

  appimage   Build a portable .AppImage (needs appimagetool)
  flatpak    Build a Flatpak bundle (needs flatpak + flatpak-builder)

EOF
  exit "${1:-0}"
}

find_app_dir() {
  local d count=0
  app_dir=""
  shopt -s nullglob
  for d in "${BINARIES_DIR}/app"/*/; do
    [ -d "$d" ] || continue
    app_dir="${d%/}"
    count=$((count + 1))
  done
  shopt -u nullglob
  if [ "$count" -ne 1 ]; then
    echo "Expected exactly one directory under ${BINARIES_DIR}/app (run :desktopApp:createDistributable first). Found ${count}." >&2
    exit 1
  fi
  app_name="$(basename "$app_dir")"
}

# Undo older builds that renamed the jpackage ELF to *.bin and left a shell wrapper.
restore_native_launcher() {
  local root="$1"
  local name="${2:-$app_name}"
  local launcher="${root}/bin/${name}"
  local real="${launcher}.bin"
  if [ -f "$real" ]; then
    rm -f "$launcher"
    mv "$real" "$launcher"
    chmod +x "$launcher"
  fi
  [ -x "$launcher" ] || {
    echo "jpackage launcher missing or not executable: ${launcher}" >&2
    exit 1
  }
}

cmd_appimage() {
  command -v appimagetool >/dev/null 2>&1 || {
    echo "appimagetool not found. On Arch/CachyOS: sudo pacman -S appimagetool" >&2
    exit 1
  }
  find_app_dir
  restore_native_launcher "$app_dir"

  local icon="${app_dir}/lib/${app_name}.png"
  [ -f "$icon" ] || icon="$ICON_SRC"
  [ -f "$icon" ] || { echo "No icon found for AppImage" >&2; exit 1; }

  local out_dir="${BINARIES_DIR}/appimage"
  local appdir="${out_dir}/${SLUG}.AppDir"
  rm -rf "$appdir"
  mkdir -p "$appdir"
  cp -a "${app_dir}/." "$appdir/"
  restore_native_launcher "$appdir"

  # AppRun is required by the AppImage format (not a Wayland helper).
  cat > "${appdir}/AppRun" <<EOF
#!/bin/sh
HERE="\$(dirname "\$(readlink -f "\$0")")"
exec "\$HERE/bin/${app_name}" "\$@"
EOF
  chmod +x "${appdir}/AppRun"

  cp -f "$icon" "${appdir}/${APP_ID}.png"
  ln -sf "${APP_ID}.png" "${appdir}/.DirIcon"

  cat > "${appdir}/${APP_ID}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Uptime Koma
Comment=Native uptime monitoring
Exec=AppRun
Icon=${APP_ID}
Categories=Network;Monitor;System;
Terminal=false
StartupWMClass=${app_name}
EOF

  mkdir -p "$out_dir"
  local arch outfile
  arch="$(uname -m)"
  outfile="${out_dir}/${SLUG}-${VERSION}-${arch}.AppImage"
  rm -f "${out_dir}/${SLUG}-run.sh" "${out_dir}"/*.sh 2>/dev/null || true
  export ARCH="$arch"
  appimagetool --no-appstream "$appdir" "$outfile"
  chmod +x "$outfile"
  echo "Wrote $outfile"
  echo "If FUSE is unavailable: APPIMAGE_EXTRACT_AND_RUN=1 $outfile"
}

cmd_flatpak() {
  local cmd
  for cmd in flatpak flatpak-builder; do
    command -v "$cmd" >/dev/null 2>&1 || {
      echo "$cmd not found. On Arch/CachyOS: sudo pacman -S flatpak flatpak-builder" >&2
      exit 1
    }
  done
  find_app_dir
  restore_native_launcher "$app_dir"

  local staging="$ROOT/desktopApp/build/flatpak-staging"
  local build_dir="$ROOT/desktopApp/build/flatpak-build"
  local repo_dir="$ROOT/desktopApp/build/flatpak-repo"
  local export_dir="${BINARIES_DIR}/flatpak"
  local manifest="$ROOT/packaging/linux/flatpak/dev.haasele.KomaNative.yml"
  local gen_manifest="$ROOT/desktopApp/build/flatpak-manifest.yml"

  rm -rf "$staging"
  mkdir -p "$staging/app" "$staging/share/applications" "$staging/share/icons/hicolor/256x256/apps"
  cp -a "${app_dir}/." "$staging/app/"
  restore_native_launcher "$staging/app"
  cp -f "$ICON_SRC" "$staging/share/icons/hicolor/256x256/apps/${APP_ID}.png"

  cat > "$staging/share/applications/${APP_ID}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Uptime Koma
Comment=Native uptime monitoring
Exec=koma-native
Icon=${APP_ID}
Categories=Network;Monitor;System;
Terminal=false
StartupWMClass=${app_name}
EOF

  # No shell wrapper: Flatpak finish-args set the Wayland env; command is the jpackage ELF.
  sed \
    -e "s|__STAGING_DIR__|${staging}|g" \
    -e "s|__APP_LAUNCHER__|${app_name}|g" \
    "$manifest" > "$gen_manifest"

  flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo || true
  flatpak install -y --user flathub org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08 || true

  rm -rf "$build_dir" "$repo_dir"
  mkdir -p "$export_dir"
  # --disable-rofiles-fuse: required in many CI/container hosts where FUSE is unavailable.
  # --user: keep the build under the runner account (no system install).
  flatpak-builder \
    --user \
    --force-clean \
    --disable-rofiles-fuse \
    --repo="$repo_dir" \
    "$build_dir" \
    "$gen_manifest"
  flatpak build-bundle "$repo_dir" "${export_dir}/${APP_ID}.flatpak" "$APP_ID"
  echo "Wrote ${export_dir}/${APP_ID}.flatpak"
  echo "Install with: flatpak install --user ${export_dir}/${APP_ID}.flatpak"
}

case "${1:-}" in
  -h|--help|help|"") usage 0 ;;
  appimage) cmd_appimage ;;
  flatpak) cmd_flatpak ;;
  *)
    echo "Unknown command: $1" >&2
    usage 1
    ;;
esac
