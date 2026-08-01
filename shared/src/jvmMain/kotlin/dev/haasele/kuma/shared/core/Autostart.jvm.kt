package dev.haasele.koma.shared.core

import java.io.File

private enum class DesktopOs { LINUX, MAC, WINDOWS }

private val currentOs: DesktopOs = System.getProperty("os.name").lowercase().let { name ->
    when {
        name.contains("win") -> DesktopOs.WINDOWS
        name.contains("mac") -> DesktopOs.MAC
        else -> DesktopOs.LINUX
    }
}

/** Absolute path of the running launcher, which differs between a packaged app and `gradlew run`. */
private val launchCommand: String? by lazy {
    ProcessHandle.current().info().command().orElse(null)
}

private val home: File get() = File(System.getProperty("user.home"))

private val autostartEntry: File
    get() = when (currentOs) {
        DesktopOs.LINUX -> {
            val base = System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: home.resolve(".config")
            base.resolve("autostart/koma-native.desktop")
        }
        DesktopOs.MAC -> home.resolve("Library/LaunchAgents/dev.haasele.koma.plist")
        DesktopOs.WINDOWS -> home.resolve("AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup/koma-native.bat")
    }

actual object Autostart {

    actual val supported: Boolean get() = launchCommand != null

    actual fun isEnabled(): Boolean = autostartEntry.exists()

    actual fun setEnabled(enabled: Boolean): Boolean = runCatching {
        val entry = autostartEntry
        if (!enabled) {
            if (entry.exists()) entry.delete()
            return@runCatching true
        }
        val command = launchCommand ?: return@runCatching false
        entry.parentFile?.mkdirs()
        entry.writeText(entryContent(command))
        if (currentOs != DesktopOs.WINDOWS) entry.setExecutable(true)
        true
    }.getOrDefault(false)

    private fun entryContent(command: String): String = when (currentOs) {
        DesktopOs.LINUX -> """
            [Desktop Entry]
            Type=Application
            Name=Uptime Koma
            Comment=Uptime monitoring that keeps running in the background
            Exec=$command
            Terminal=false
            X-GNOME-Autostart-enabled=true
        """.trimIndent()

        DesktopOs.MAC -> """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key><string>dev.haasele.koma</string>
                <key>ProgramArguments</key>
                <array><string>$command</string></array>
                <key>RunAtLoad</key><true/>
            </dict>
            </plist>
        """.trimIndent()

        DesktopOs.WINDOWS -> "@echo off\r\nstart \"\" \"$command\"\r\n"
    }
}
