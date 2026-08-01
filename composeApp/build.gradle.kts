import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val iosEnabled: Boolean = run {
    val prop = providers.gradleProperty("koma.enableIos").orNull
    if (prop != null && prop != "auto") prop.toBoolean()
    else System.getProperty("os.name").startsWith("Mac")
}

fun toolOnPath(name: String): Boolean =
    System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.any { dir -> File(dir, name).canExecute() }
        ?: false

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "dev.haasele.koma.app"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources.enable = true
    }

    if (iosEnabled) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "KomaApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.slf4j.simple)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.appcompat)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.haasele.koma.app.desktop.DesktopLauncher"
        // Linux/XWayland: disable Java2D UI scaling so Skiko contentScale stays 1×.
        jvmArgs(
            "-Dsun.java2d.uiScale.enabled=false",
            "-Dsun.java2d.uiScale=1.0",
            "-Dskiko.linux.autodpi=false",
            "-Dskiko.renderApi=SOFTWARE",
        )

        nativeDistributions {
            // TargetFormat.AppImage = jpackage unpacked app dir (needed by createDistributable).
            // Real .AppImage / Flatpak are built by packageLinuxAppImage / packageFlatpak.
            val formats = mutableListOf(
                TargetFormat.AppImage,
                TargetFormat.Dmg,
                TargetFormat.Msi,
            )
            if (toolOnPath("dpkg-deb")) formats += TargetFormat.Deb
            if (toolOnPath("rpmbuild")) formats += TargetFormat.Rpm
            targetFormats(*formats.toTypedArray())

            packageName = "koma-native"
            packageVersion = "1.0.0"
            description = "Native uptime monitoring for desktop and mobile"
            vendor = "haasele"

            // jlink strips unused JDK modules; SQLDelight JDBC needs java.sql (and friends).
            modules(
                "java.sql",
                "java.naming",
                "java.net.http",
                "jdk.crypto.ec",
                "jdk.unsupported",
            )

            linux {
                packageName = "koma-native"
                menuGroup = "Monitoring"
            }
        }
    }
}

val packageLinuxAppImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds a portable .AppImage (needs appimagetool on PATH)"
    onlyIf {
        val ok = org.gradle.internal.os.OperatingSystem.current().isLinux && toolOnPath("appimagetool")
        if (!ok && org.gradle.internal.os.OperatingSystem.current().isLinux) {
            logger.warn("Skipping packageLinuxAppImage: install appimagetool (e.g. pacman -S appimagetool)")
        }
        ok
    }
    dependsOn("createDistributable")
    workingDir = rootProject.projectDir
    commandLine("bash", "packaging/linux/package.sh", "appimage")
    inputs.dir(layout.buildDirectory.dir("compose/binaries/main/app"))
    outputs.dir(layout.buildDirectory.dir("compose/binaries/main/appimage"))
}

val packageFlatpak by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds a Flatpak bundle (needs flatpak + flatpak-builder on PATH)"
    onlyIf {
        val ok = org.gradle.internal.os.OperatingSystem.current().isLinux &&
            toolOnPath("flatpak") &&
            toolOnPath("flatpak-builder")
        if (!ok && org.gradle.internal.os.OperatingSystem.current().isLinux) {
            logger.warn(
                "Skipping packageFlatpak: install flatpak + flatpak-builder " +
                    "(e.g. pacman -S flatpak flatpak-builder)",
            )
        }
        ok
    }
    dependsOn("createDistributable")
    workingDir = rootProject.projectDir
    commandLine("bash", "packaging/linux/package.sh", "flatpak")
    inputs.dir(layout.buildDirectory.dir("compose/binaries/main/app"))
    outputs.dir(layout.buildDirectory.dir("compose/binaries/main/flatpak"))
}

// Wayland / niri: env for :run; packaged apps rely on WaylandAwtBootstrap (+ Flatpak finish-args).
tasks.withType<JavaExec>().configureEach {
    environment("_JAVA_AWT_WM_NONREPARENTING", "1")
    environment("GDK_SCALE", System.getenv("GDK_SCALE") ?: "1")
    environment("GDK_DPI_SCALE", System.getenv("GDK_DPI_SCALE") ?: "1")
}
