package dev.haasele.koma.app.desktop

import java.io.File
import kotlin.system.exitProcess

/**
 * JVM entry point for desktop packages (`java -jar`, jpackage, AppImage).
 *
 * Must not touch AWT/Compose before the Wayland env is in place. Mutating Java's
 * env map is not enough for AWT (native getenv); when the flag is missing this
 * process re-execs with a real environment, then enters [runDesktop].
 *
 * @see <a href="https://github.com/niri-wm/niri/wiki/Application-Issues">niri Application Issues</a>
 */
object DesktopLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        if (!ensureNativeWaylandEnv(args)) return
        WaylandAwtBootstrap.apply()
        runDesktop(args)
    }
}

/**
 * @return `false` if a child was started and this JVM should exit.
 */
internal fun ensureNativeWaylandEnv(args: Array<String>): Boolean {
    val isLinux = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)
    if (!isLinux) return true
    if (System.getenv("_JAVA_AWT_WM_NONREPARENTING") == "1") return true

    val command = buildReexecCommand(args) ?: run {
        System.err.println(
            "koma-desktop: cannot re-exec with _JAVA_AWT_WM_NONREPARENTING=1; " +
                "export it in your shell or niri environment { } block.",
        )
        WaylandAwtBootstrap.apply()
        return true
    }

    val builder = ProcessBuilder(command).inheritIO()
    val env = builder.environment()
    env["_JAVA_AWT_WM_NONREPARENTING"] = "1"
    if (env["GDK_SCALE"].isNullOrBlank()) env["GDK_SCALE"] = "1"
    if (env["GDK_DPI_SCALE"].isNullOrBlank()) env["GDK_DPI_SCALE"] = "1"

    System.err.println(
        "koma-desktop: re-exec with _JAVA_AWT_WM_NONREPARENTING=1 (Wayland / xwayland-satellite)",
    )
    exitProcess(builder.start().waitFor())
}

private fun buildReexecCommand(args: Array<String>): List<String>? {
    val info = ProcessHandle.current().info()
    val executable = info.command().orElse(null)?.takeIf { it.isNotBlank() }
    val processArgs = info.arguments()

    // Prefer the exact argv that started us (covers `java -jar …` and jpackage).
    if (executable != null && processArgs.isPresent) {
        return listOf(executable) + processArgs.get().toList()
    }

    val jar = jarPathOf(DesktopLauncher::class.java)
    if (jar != null) {
        val java = File(System.getProperty("java.home"), "bin/java")
        if (!java.canExecute()) return null
        return listOf(java.absolutePath) + defaultJvmArgs() + listOf("-jar", jar) + args
    }

    if (executable != null) {
        return listOf(executable) + args
    }
    return null
}

private fun defaultJvmArgs(): List<String> = listOf(
    "-Dsun.java2d.uiScale.enabled=false",
    "-Dsun.java2d.uiScale=1.0",
    "-Dskiko.linux.autodpi=false",
    "-Dskiko.renderApi=${System.getProperty("skiko.renderApi") ?: "SOFTWARE"}",
)

private fun jarPathOf(type: Class<*>): String? {
    val url = type.protectionDomain?.codeSource?.location ?: return null
    return runCatching { File(url.toURI()).absolutePath }.getOrNull()
        ?.takeIf { it.endsWith(".jar", ignoreCase = true) }
}
