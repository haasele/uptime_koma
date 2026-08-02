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
                implementation(libs.dorkbox.systemtray)
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

        // Netty / dorkbox / Skiko pull in hundreds of optional classes ProGuard treats as errors.
        // Desktop installers do not need shrinking here; disable until dedicated keep/dontwarn rules exist.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            // AppImage is built by :packageLinuxAppImage (appimagetool + AppRun), not Compose's
            // TargetFormat.AppImage — both would fight over main-release/app.
            val formats = mutableListOf(
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

val releaseBinariesDir = layout.buildDirectory.dir("compose/binaries/main-release")
val releaseAppDir = releaseBinariesDir.map { it.dir("app") }

tasks.register<Exec>("packageLinuxAppImage") {
    group = "distribution"
    description = "Builds a portable .AppImage via appimagetool (needs appimagetool on PATH)"
    onlyIf {
        val ok = org.gradle.internal.os.OperatingSystem.current().isLinux && toolOnPath("appimagetool")
        if (!ok && org.gradle.internal.os.OperatingSystem.current().isLinux) {
            logger.warn("Skipping packageLinuxAppImage: install appimagetool (e.g. pacman -S appimagetool)")
        }
        ok
    }
    dependsOn("createReleaseDistributable")
    // Compose packageRelease* tasks also touch main-release/; finish them first when requested together.
    mustRunAfter(tasks.matching { it.name.startsWith("packageRelease") })
    workingDir = rootProject.projectDir
    environment("BINARIES_DIR", releaseBinariesDir.get().asFile.absolutePath)
    commandLine("bash", "packaging/linux/package.sh", "appimage")
    inputs.dir(releaseAppDir)
    outputs.dir(releaseBinariesDir.map { it.dir("appimage") })
}

tasks.register<Exec>("packageFlatpak") {
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
    dependsOn("createReleaseDistributable")
    mustRunAfter(tasks.matching { it.name.startsWith("packageRelease") })
    mustRunAfter("packageLinuxAppImage")
    workingDir = rootProject.projectDir
    environment("BINARIES_DIR", releaseBinariesDir.get().asFile.absolutePath)
    commandLine("bash", "packaging/linux/package.sh", "flatpak")
    inputs.dir(releaseAppDir)
    outputs.dir(releaseBinariesDir.map { it.dir("flatpak") })
}

tasks.withType<JavaExec>().configureEach {
    environment("_JAVA_AWT_WM_NONREPARENTING", "1")
    environment("GDK_SCALE", System.getenv("GDK_SCALE") ?: "1")
    environment("GDK_DPI_SCALE", System.getenv("GDK_DPI_SCALE") ?: "1")
}
