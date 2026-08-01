# Build & packaging

Commands run from the repository root via `./gradlew`.
Package version is **1.0.0**. There is **no web target**.

| Module | Role |
| --- | --- |
| `:shared` | Engine, DB, notifications, embedded server |
| `:composeApp` | Shared UI (Desktop JVM + Android library) + desktop packaging |
| `:androidApp` | Installable Android APK / AAB shell |

iOS Kotlin (`iosMain`) exists; there is **no** `iosApp` / Xcode project in this repo yet.

---

## Quick map

| Goal | Command | Output |
| --- | --- | --- |
| Desktop UI | `:composeApp:run` | live process |
| Headless engine | `:composeApp:run --args='--headless --port 3001'` | live process |
| Linux AppImage | `:composeApp:packageLinuxAppImage` | `composeApp/build/compose/binaries/main/appimage/*.AppImage` |
| Flatpak | `:composeApp:packageFlatpak` | `composeApp/build/compose/binaries/main/flatpak/*.flatpak` |
| Unpacked desktop app | `:composeApp:createDistributable` | `composeApp/build/compose/binaries/main/app/koma-native/` |
| Uber JAR | `:composeApp:packageUberJarForCurrentOS` | `composeApp/build/compose/jars/*.jar` |
| Android debug APK | `:androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/` |
| Shared tests | `:shared:jvmTest` | `shared/build/reports/tests/jvmTest/` |

```bash
./gradlew :shared:jvmTest :composeApp:compileKotlinJvm :androidApp:assembleDebug
```

---

## Compile / check

| Task | Purpose |
| --- | --- |
| `:shared:compileKotlinJvm` | Shared JVM |
| `:shared:compileAndroidMain` | Shared Android |
| `:composeApp:compileKotlinJvm` | Desktop UI |
| `:composeApp:compileAndroidMain` | Android UI library |
| `:androidApp:compileDebugKotlin` | Android app module |
| `:shared:jvmTest` / `:shared:allTests` | Tests |
| `:composeApp:assemble` / `:androidApp:assemble` | Assemble outputs |

---

## Desktop — run

| Task | Notes |
| --- | --- |
| `:composeApp:run` | Dev UI. Wayland env via Gradle + `WaylandAwtBootstrap`. |
| `:composeApp:run --args='--headless --port 3001'` | Engine only |
| `:composeApp:runDistributable` | Runs the `createDistributable` tree |
| `:composeApp:runRelease` / `runReleaseDistributable` | Release variants |

Stale windows: `pkill -f 'dev.haasele.koma'`.

---

## Desktop — packaging

**Repo tooling** (one script only):

```text
packaging/linux/
├── package.sh                 # appimage | flatpak
├── dev.haasele.KomaNative.png
└── flatpak/dev.haasele.KomaNative.yml
```

Build outputs are native binaries / installers — **no companion `.sh` launchers** are written next to JARs or into `app/bin/`.
AppImage still contains the required `AppRun` entrypoint (AppImage format); Flatpak uses the jpackage ELF plus `finish-args` env.

**Artifact root:** `composeApp/build/compose/binaries/main/`  
Release: `…/binaries/main-release/`

| Task | Host / tools | Output |
| --- | --- | --- |
| `:composeApp:createDistributable` | any desktop OS | `…/app/koma-native/` · `bin/koma-native` (jpackage ELF) |
| `:composeApp:packageAppImage` | Compose `TargetFormat.AppImage` = **jpackage app dir**, not a real `.AppImage` | same under `…/app/` |
| `:composeApp:packageLinuxAppImage` | Linux + `appimagetool` | `…/appimage/koma-native-1.0.0-<arch>.AppImage` |
| `:composeApp:packageFlatpak` | Linux + `flatpak` + `flatpak-builder` | `…/flatpak/dev.haasele.KomaNative.flatpak` |
| `:composeApp:packageUberJarForCurrentOS` | any desktop OS | `composeApp/build/compose/jars/koma-native-<os>-<arch>-1.0.0.jar` |
| `:composeApp:packageRpm` | Linux + `rpmbuild` (if on `PATH`) | `…/rpm/*.rpm` |
| `:composeApp:packageDeb` | Linux + `dpkg-deb` (if on `PATH`) | `…/deb/*.deb` |
| `:composeApp:packageDmg` | macOS | `…/dmg/*.dmg` |
| `:composeApp:packageMsi` | Windows + WiX | `…/msi/*.msi` |
| `:composeApp:packageDistributionForCurrentOS` | current OS | formats for this host |
| `:composeApp:package` | — | Compose umbrella task |

```bash
./gradlew :composeApp:createDistributable
packaging/linux/package.sh appimage   # same as :packageLinuxAppImage
packaging/linux/package.sh flatpak    # same as :packageFlatpak
```

### AppImage

Prefer **`:composeApp:packageLinuxAppImage`**. Without FUSE:

```bash
APPIMAGE_EXTRACT_AND_RUN=1 composeApp/build/compose/binaries/main/appimage/koma-native-1.0.0-$(uname -m).AppImage
```

### Flatpak

```bash
./gradlew :composeApp:packageFlatpak
flatpak install --user composeApp/build/compose/binaries/main/flatpak/dev.haasele.KomaNative.flatpak
```

### Uber JAR

```bash
java -jar composeApp/build/compose/jars/koma-native-linux-x64-1.0.0.jar
```

On Linux/Wayland the entry point (`DesktopLauncher`) re-execs once with
`_JAVA_AWT_WM_NONREPARENTING=1` in the real process environment (AWT ignores
in-process env-map hacks). You should see a short `koma-desktop: re-exec …` line
on stderr, then the UI.

### Flatpak / AppImage tooling

| Task | Skipped unless |
| --- | --- |
| `:packageLinuxAppImage` | `appimagetool` on `PATH` |
| `:packageFlatpak` | `flatpak` **and** `flatpak-builder` on `PATH` |

```bash
# Arch / CachyOS
sudo pacman -S appimagetool flatpak flatpak-builder
```

---

## Wayland (niri / xwayland-satellite)

Java AWT needs `_JAVA_AWT_WM_NONREPARENTING=1` before toolkit init
([niri Application Issues](https://github.com/niri-wm/niri/wiki/Application-Issues)).

Applied by:

1. `DesktopLauncher` — re-exec with a real process env when the flag is missing (`java -jar`)
2. `WaylandAwtBootstrap` — in-process map update (best-effort)
3. Gradle `JavaExec` / `:composeApp:run`
4. Flatpak `finish-args --env=…`

Optional niri config:

```kdl
environment {
    _JAVA_AWT_WM_NONREPARENTING "1"
}
```

---

## Android

| Task | Output |
| --- | --- |
| `:androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| `:androidApp:assembleRelease` | `androidApp/build/outputs/apk/release/` |
| `:androidApp:bundleDebug` / `bundleRelease` | `androidApp/build/outputs/bundle/…` |
| `:androidApp:installDebug` | device / emulator |

Application id: `dev.haasele.koma`.

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

---

## Shared library

| Task | Output |
| --- | --- |
| `:shared:jvmJar` | `shared/build/libs/` |
| `:shared:jvmTest` | `shared/build/reports/tests/jvmTest/` |

---

## Output tree

```text
composeApp/build/compose/binaries/main/
├── app/koma-native/bin/koma-native    # jpackage ELF
├── appimage/koma-native-1.0.0-<arch>.AppImage
├── flatpak/dev.haasele.KomaNative.flatpak
└── rpm/ | deb/ | dmg/ | msi/          # when built

composeApp/build/compose/jars/*.jar

androidApp/build/outputs/apk/debug/androidApp-debug.apk
shared/build/libs/
```

---

## Tooling

| Artifact | Needs |
| --- | --- |
| Desktop run / JAR / unpacked app | JDK **17+** (bytecode target 17) |
| Real `.AppImage` | `appimagetool` |
| Flatpak | `flatpak`, `flatpak-builder`, Freedesktop 24.08 |
| RPM / DEB | `rpmbuild` / `dpkg-deb` |
| DMG / MSI | macOS / Windows+WiX |
| Android APK | Android SDK |

```bash
./gradlew :composeApp:tasks --group=distribution
```
