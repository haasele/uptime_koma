import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

    sourceSets {
        jvmMain {
            kotlin.setSrcDirs(listOf("src"))
            dependencies {
                implementation(project(":composeApp"))
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.slf4j.simple)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.haasele.koma.app.desktop.DesktopLauncher"
        jvmArgs(
            "-Dsun.java2d.uiScale.enabled=false",
            "-Dsun.java2d.uiScale=1.0",
            "-Dskiko.linux.autodpi=false",
            "-Dskiko.renderApi=SOFTWARE",
        )

        nativeDistributions {
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

tasks.withType<JavaExec>().configureEach {
    environment("_JAVA_AWT_WM_NONREPARENTING", "1")
    environment("GDK_SCALE", System.getenv("GDK_SCALE") ?: "1")
    environment("GDK_DPI_SCALE", System.getenv("GDK_DPI_SCALE") ?: "1")
}
