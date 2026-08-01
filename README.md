# Uptime Koma

A native rewrite of [Uptime Kuma](https://github.com/louislam/uptime-kuma) as a Kotlin Multiplatform app with Compose Multiplatform UI. The monitoring engine, database and notifications live in shared Kotlin; Android, iOS and Desktop share the same screens. There is no Node.js and no web view in the runtime path.

## Roles

| Platform | Role |
| --- | --- |
| Desktop (JVM) | Full 24/7 server: engine, tray, autostart, embedded HTTP (push / metrics / status JSON / remote UI) |
| Android | Local engine while the app (or foreground service) runs; can also drive a desktop over remote UI |
| iOS | Same UI; background polling is best-effort — prefer a headless desktop for always-on checks |

## Build

Full compile/packaging task list and output paths: **[docs/BUILD.md](docs/BUILD.md)**.

```bash
./gradlew :shared:jvmTest
./gradlew :composeApp:run                  # desktop UI
./gradlew :composeApp:run --args='--headless --port 3001'
./gradlew :androidApp:assembleDebug        # Android APK
```

Linux packages (AppImage / Flatpak via `packaging/linux/package.sh`; Wayland is handled in-app, not via extra launch scripts):

```bash
./gradlew :composeApp:packageLinuxAppImage   # needs appimagetool
./gradlew :composeApp:packageFlatpak         # needs flatpak + flatpak-builder
./gradlew :composeApp:createDistributable
./gradlew :composeApp:packageUberJarForCurrentOS
./gradlew :composeApp:packageRpm             # needs rpmbuild
./gradlew :composeApp:packageDeb             # needs dpkg-deb
./gradlew :composeApp:packageDmg             # macOS
./gradlew :composeApp:packageMsi             # Windows + WiX
```

`composeApp` is the multiplatform UI library; the installable APK comes from `:androidApp`.
Headless mode runs the engine without a window — configure monitors in the UI (or import a backup), then start with `--headless`. Enable remote access in Settings for `ws://<host>:<port>/api/remote`.

Wayland / niri: [docs/BUILD.md § Wayland](docs/BUILD.md#wayland-niri--xwayland-satellite).

## Remote UI

1. On the desktop: Settings → allow remote UI clients → copy the access token.
2. On the phone: Settings → Open remote console → enter `http://<desktop-ip>:3001` and the token.
3. Pause / resume monitors and start / stop the engine from the console.

## Backup

Settings → Backup exports monitors, notification channels, status screens, tags and maintenance windows as JSON (no heartbeats). Paste the JSON on another instance to import.

## Stack

- Kotlin Multiplatform + Compose Multiplatform
- SQLDelight (SQLite)
- Ktor Client / Server
- kotlinx-coroutines + kotlinx-serialization
