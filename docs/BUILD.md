# Build & packaging

Build system: **Kotlin Toolchain** (`./kotlin` / `./kotlin.bat`).  
Package version is **1.0.0**. There is **no web target**.

| Module | Role |
| --- | --- |
| `shared` | Engine, DB, notifications, embedded server (`kmp/lib`) |
| `composeApp` | Shared Compose UI library (`kmp/lib`) |
| `desktopApp` | Desktop JVM app (`jvm/app`) |
| `androidApp` | Installable Android APK / AAB shell (`android/app`) |

iOS Kotlin (`src@ios`) exists; there is **no** `ios/app` / Xcode project in this repo yet.

Sources use the **Amper layout** (`src/`, `src@jvm/`, `src@android/`, `src@ios/`, `src@jvmAndAndroid/`).

---

## Quick map

| Goal | Command |
| --- | --- |
| Build JVM apps/libs | `./kotlin build -p jvm` |
| Build Android | `./kotlin build -p android` |
| Desktop UI | `_JAVA_AWT_WM_NONREPARENTING=1 ./kotlin run -m desktopApp` |
| Headless / web service | `./kotlin run -m desktopApp -- --nogui --port 3001` |
| Shared tests | `./kotlin test -p jvm -m shared` |
| Clean | `./kotlin clean` |

```bash
./kotlin build -p jvm -m shared -m composeApp -m desktopApp
./kotlin test -p jvm -m shared
```

Install the CLI globally if needed: `curl -fsSL https://kotl.in/install.sh | sh`  
Repo wrappers: `./kotlin` / `./kotlin.bat` (created via `kotlin update --create`).

---

## Build outputs

All Toolchain outputs live under the project-root **`build/`** directory  
(override with `--build-dir=<path>`).

| Kind | Path |
| --- | --- |
| Compiled classes (JVM) | `build/artifacts/CompiledJvmArtifact/<module><platform>/kotlin-output/` |
| Module JARs (task products) | `build/tasks/_<module>_jarJvm/<module>-jvm.jar` |
| Logs | `build/logs/` |
| Reports / temp | `build/reports/`, `build/temp/` |

Examples after `./kotlin build -p jvm`:

- `build/artifacts/CompiledJvmArtifact/sharedjvm/`
- `build/artifacts/CompiledJvmArtifact/composeAppjvm/`
- `build/artifacts/CompiledJvmArtifact/desktopAppjvm/`
- `build/tasks/_desktopApp_jarJvm/desktopApp-jvm.jar`

To run the app you normally do **not** need those paths: use `./kotlin run -m desktopApp`.

---

## Packaging

```bash
# Desktop JVM JAR / executable JAR
./kotlin package -p jvm -m desktopApp -f jar
./kotlin package -p jvm -m desktopApp -f executable-jar

# Android App Bundle (release by default; use -v debug for debug)
./kotlin package -p android -m androidApp -f aab
```

Supported `-f` formats: `jar`, `executable-jar`, `aab`, `maven-central-bundle`.  
Packaged artifacts are written under `build/` (same build root as compile outputs).

Legacy Linux AppImage / Flatpak helpers under [`packaging/linux/`](../packaging/linux/) expected a Compose Desktop jpackage tree that is no longer produced by the Toolchain workflow. Prefer `executable-jar` (or rework those scripts against Toolchain outputs) until native desktop installers are wired again.

---

## SQLDelight

`.sq` sources live in [`shared/sqldelight/`](../shared/sqldelight/).  
Generated Kotlin is **committed** under `shared/src/…/shared/db/` so `./kotlin build` needs no separate codegen step.

After editing `.sq` files, regenerate the committed sources under `shared/src/dev/haasele/koma/shared/db/` and commit them.

---

## Desktop CLI (JAR / binary / AppImage / Flatpak)

All desktop entry points share the same flags (`DesktopLauncher`):

```bash
koma-native --help
koma-native --nogui --port 3001
koma-native --port 8443 --https /etc/koma/keystore.p12
koma-native --nogui --http --port 3001 --debug
```

| Flag | Meaning |
| --- | --- |
| `--nogui`, `--headless` | No GUI; engine + embedded API |
| `--port <n>` | Listen port; implies `--nogui` |
| `--http` | Cleartext HTTP (default in nogui mode) |
| `--https <cert>` | HTTPS via PKCS12 or PEM (+ `key.pem`); password: `KOMA_HTTPS_PASSWORD` |
| `--hostname <list>` | Comma-separated DNS names (Host allowlist + advertised URLs) |
| `--debug` | slf4j DEBUG + engine beat logs |
| `-h`, `--help` | Help text |

AppImage/`AppRun` and Flatpak `command: koma-native` forward `"$@"` unchanged. For Flatpak HTTPS, grant the cert path (`flatpak run --filesystem=host:ro …`).

---

## Wayland (niri / xwayland-satellite)

Java AWT needs `_JAVA_AWT_WM_NONREPARENTING=1` in the **native** process environment before AWT init.

- `./kotlin run -m desktopApp` — export the env yourself (or rely on `WaylandAwtBootstrap` + `DesktopLauncher` re-exec)
- Packaged apps — in-process bootstrap

Skiko/DPI JVM args for Linux are set in `desktopApp` and in `Main.kt`.

Stale windows: `pkill -f 'dev.haasele.koma'`.

---

## Android

```bash
./kotlin build -p android -m androidApp
./kotlin package -p android -m androidApp -f aab -v debug
```

Requires an Android SDK (`ANDROID_HOME` / `ANDROID_SDK_ROOT` / `local.properties`).


---

TLDR

Run Compile ALL with:

```bash
./kotlin build -p jvm -p android -v debug -v release && \
./kotlin package -p jvm -m desktopApp -f executable-jar && \
./kotlin package -p android -m androidApp -f aab && \
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS \
  :desktopApp:packageLinuxAppImage \
  :desktopApp:packageFlatpak
```

Find everything under:

| File                           | Path                                                                                        |
|--------------------------------|---------------------------------------------------------------------------------------------|
| Desktop executable JAR         | `build/tasks/_desktopApp_executableJarJvm/desktopApp-jvm-executable.jar`                    |
| Android Debug-APK              | `build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk`                        |
| Android Release-APK (unsigned) | `build/tasks/_androidApp_buildAndroidRelease/gradle-project-release-unsigned.apk`           |
| Android Release-AAB            | `build/tasks/_androidApp_bundleAndroid/gradle-project-release.aab`                          |
| jpackage-App (ausgepackt)      | `desktopApp/build/compose/binaries/main-release/app/koma-native/`                           |
| RPM                            | `desktopApp/build/compose/binaries/main-release/rpm/koma-native-1.0.0-1.x86_64.rpm`         |
| AppImage                       | `desktopApp/build/compose/binaries/main-release/appimage/koma-native-1.0.0-x86_64.AppImage` |
| Flatpak                        | `desktopApp/build/compose/binaries/main-release/flatpak/dev.haasele.KomaNative.flatpak`     |

No Deb — `dpkg-deb` is missing on my Arch system. DMG/MSI only getting created on MacOS/Windows.