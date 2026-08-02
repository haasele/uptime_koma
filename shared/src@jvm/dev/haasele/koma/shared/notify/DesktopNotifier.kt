package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.core.ioDispatcher
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

/**
 * Desktop OS notifications.
 *
 * On Linux (especially Wayland) AWT [TrayIcon.displayMessage] is usually unavailable —
 * [SystemTray.isSupported] is false without an XEmbed tray host. Prefer `notify-send`
 * / the freedesktop notification daemon, then fall back to an AWT balloon when present.
 */
class DesktopNotifier : LocalNotifier {

    /** Optional AWT tray icon owned by the UI (Compose / dorkbox bridge). */
    @Volatile
    var sharedTrayIcon: TrayIcon? = null

    private val ownTrayIcon: TrayIcon? by lazy { createOwnTrayIcon() }

    override suspend fun notify(title: String, message: String, level: NotificationLevel) {
        withContext(ioDispatcher) {
            val delivered = when {
                isLinux() -> notifySend(title, message, level)
                isMac() -> notifyOsascript(title, message)
                else -> false
            }
            if (!delivered) {
                notifyAwtBalloon(title, message, level)
            }
        }
    }

    private fun notifySend(title: String, message: String, level: NotificationLevel): Boolean {
        val urgency = when (level) {
            NotificationLevel.ERROR -> "critical"
            NotificationLevel.WARNING -> "normal"
            NotificationLevel.INFO -> "low"
        }
        val icon = when (level) {
            NotificationLevel.ERROR -> "dialog-error"
            NotificationLevel.WARNING -> "dialog-warning"
            NotificationLevel.INFO -> "dialog-information"
        }
        return runCatching {
            val process = ProcessBuilder(
                "notify-send",
                "-a", "Uptime Koma",
                "-u", urgency,
                "-i", icon,
                "--",
                title,
                message,
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        }.onFailure { error ->
            System.err.println("koma-desktop: notify-send failed: ${error.message}")
        }.getOrDefault(false)
    }

    private fun notifyOsascript(title: String, message: String): Boolean {
        val script =
            "display notification ${osascriptString(message)} with title ${osascriptString(title)}"
        return runCatching {
            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun notifyAwtBalloon(title: String, message: String, level: NotificationLevel): Boolean {
        val icon = sharedTrayIcon ?: ownTrayIcon ?: return false
        val messageType = when (level) {
            NotificationLevel.ERROR -> TrayIcon.MessageType.ERROR
            NotificationLevel.WARNING -> TrayIcon.MessageType.WARNING
            NotificationLevel.INFO -> TrayIcon.MessageType.INFO
        }
        return runCatching {
            icon.displayMessage(title, message, messageType)
            true
        }.getOrDefault(false)
    }

    private fun createOwnTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        return runCatching {
            TrayIcon(komaTrayImage(32), "Uptime Koma").apply {
                isImageAutoSize = true
                SystemTray.getSystemTray().add(this)
            }
        }.onFailure { error ->
            System.err.println("koma-desktop: AWT tray icon unavailable: ${error.message}")
        }.getOrNull()
    }

    private companion object {
        fun isLinux(): Boolean =
            System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)

        fun isMac(): Boolean =
            System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

        fun osascriptString(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

private fun komaTrayImage(size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics() as Graphics2D
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(0x2D, 0xD4, 0xA7)
    g.fillOval(0, 0, size, size)
    g.color = Color.WHITE
    val stroke = (size / 9f).coerceAtLeast(1f)
    g.stroke = java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
    val midY = size / 2f
    val path = java.awt.geom.Path2D.Float().apply {
        moveTo(size * 0.18f, midY)
        lineTo(size * 0.38f, midY)
        lineTo(size * 0.50f, size * 0.24f)
        lineTo(size * 0.64f, size * 0.78f)
        lineTo(size * 0.74f, midY)
        lineTo(size * 0.86f, midY)
    }
    g.draw(path)
    g.dispose()
    return image
}
