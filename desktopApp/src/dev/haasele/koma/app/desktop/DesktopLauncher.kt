package dev.haasele.koma.app.desktop

import java.io.File
import kotlin.system.exitProcess

/**
 * JVM entry point for desktop packages (`java -jar`, jpackage, AppImage, Flatpak).
 *
 * Must not touch AWT/Compose before the Wayland env is in place — except in `--nogui` /
 * `--port` mode, which skips the UI stack entirely.
 *
 * @see <a href="https://github.com/niri-wm/niri/wiki/Application-Issues">niri Application Issues</a>
 */
object DesktopLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val cli = DesktopCli.parse(args)
        applyDebugLogging(cli.debug)

        if (cli.help) {
            println(DesktopCli.helpText(commandName()))
            return
        }
        if (!cli.ok) {
            cli.errors.forEach { System.err.println("error: $it") }
            System.err.println()
            System.err.println(DesktopCli.helpText(commandName()))
            exitProcess(2)
        }

        // Headless webservice: no AWT, no Wayland re-exec.
        if (cli.isHeadless) {
            runHeadless(cli)
            return
        }

        if (!ensureNativeWaylandEnv(args)) return
        WaylandAwtBootstrap.apply()
        runDesktop(cli)
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
    System.err.println("koma-desktop: re-exec argv: ${command.joinToString(" ")}")
    exitProcess(builder.start().waitFor())
}

/**
 * Rebuild a safe argv for re-exec.
 *
 * Never reuse [ProcessHandle] arguments blindly: for jpackage/AppImage the reported command is
 * often `…/runtime/bin/java` while arguments are only the *application* flags (`--debug`).
 * Re-running that as `java --debug` makes HotSpot treat `--debug` as a JVM option and abort.
 */
private fun buildReexecCommand(args: Array<String>): List<String>? {
    val appArgs = args.toList()

    // jpackage sets this to the native launcher (bin/koma-native). Prefer it.
    System.getProperty("jpackage.app-path")
        ?.takeIf { it.isNotBlank() && File(it).canExecute() }
        ?.let { return listOf(it) + appArgs }

    val info = ProcessHandle.current().info()
    val command = info.command().orElse(null)?.takeIf { it.isNotBlank() }
    val processArgs = info.arguments().orElse(null)?.toList()

    // Native ELF launcher reported as the process image.
    if (command != null && !isJavaBinary(command) && File(command).canExecute()) {
        return listOf(command) + appArgs
    }

    // Plain `java -jar …` / IDE runs: reconstruct from java.home + our jar.
    val jar = jarPathOf(DesktopLauncher::class.java)
    if (jar != null) {
        val java = javaExecutable(command)
        if (java != null) {
            return listOf(java) + defaultJvmArgs() + listOf("-jar", jar) + appArgs
        }
    }

    // Full JVM argv from the OS (must already look like a java invocation).
    if (command != null && isJavaBinary(command) && processArgs != null && looksLikeJvmArgv(processArgs)) {
        return listOf(command) + replaceAppArgs(processArgs, appArgs)
    }

    return null
}

private fun isJavaBinary(path: String): Boolean {
    val name = File(path).name.lowercase()
    return name == "java" || name == "java.exe"
}

private fun javaExecutable(processCommand: String?): String? {
    val fromHome = File(System.getProperty("java.home"), "bin/java")
    if (fromHome.canExecute()) return fromHome.absolutePath
    if (processCommand != null && isJavaBinary(processCommand) && File(processCommand).canExecute()) {
        return processCommand
    }
    return null
}

/** True when [argv] already contains JVM wiring (-jar / -cp / -m / -D…), not only app flags. */
private fun looksLikeJvmArgv(argv: List<String>): Boolean {
    if (argv.isEmpty()) return false
    if (argv.any { it == "-jar" || it == "-cp" || it == "-classpath" || it == "-m" || it == "--module" }) {
        return true
    }
    return argv.any { token ->
        token.startsWith("-D") ||
            token.startsWith("-X") ||
            token.startsWith("-javaagent") ||
            token.startsWith("--add-") ||
            token.startsWith("--enable-") ||
            token.startsWith("-Djpackage.")
    }
}

/**
 * Drop previous trailing application arguments and append [appArgs].
 * Keeps everything up to and including `-jar <file>`, `-m <module>`, or the main class.
 */
private fun replaceAppArgs(processArgs: List<String>, appArgs: List<String>): List<String> {
    var i = 0
    while (i < processArgs.size) {
        val token = processArgs[i]
        when {
            token == "-jar" || token == "-m" || token == "--module" ||
                token == "-cp" || token == "-classpath" -> {
                // flag + value; app args start after that
                return processArgs.take(i + 2) + appArgs
            }
            token.startsWith("-") -> i += 1
            token.contains('.') && token.any { it.isUpperCase() } -> {
                // likely main class
                return processArgs.take(i + 1) + appArgs
            }
            else -> i += 1
        }
    }
    // Could not find a split point — do not risk passing app flags to the JVM.
    return processArgs
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

private fun commandName(): String {
    System.getProperty("jpackage.app-path")?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?.let { return it }
    val jar = jarPathOf(DesktopLauncher::class.java)
    if (jar != null) return "java -jar ${File(jar).name}"
    return ProcessHandle.current().info().command().orElse("koma-native")
        .substringAfterLast('/')
}
