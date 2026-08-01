package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.core.ioDispatcher
import kotlinx.coroutines.withContext
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

/**
 * Uses the system tray balloon because it is the only notification channel available to a plain
 * JVM process on all three desktop platforms.
 */
class DesktopNotifier : LocalNotifier {

    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        runCatching {
            val image = Toolkit.getDefaultToolkit().createImage(ByteArray(0))
            TrayIcon(image, "Uptime Koma").apply {
                isImageAutoSize = true
                SystemTray.getSystemTray().add(this)
            }
        }.getOrNull()
    }

    /** Set by the desktop entry point so notifications reuse the visible tray icon. */
    var sharedTrayIcon: TrayIcon? = null

    override suspend fun notify(title: String, message: String, level: NotificationLevel) {
        withContext(ioDispatcher) {
            val icon = sharedTrayIcon ?: trayIcon ?: return@withContext
            val messageType = when (level) {
                NotificationLevel.ERROR -> TrayIcon.MessageType.ERROR
                NotificationLevel.WARNING -> TrayIcon.MessageType.WARNING
                NotificationLevel.INFO -> TrayIcon.MessageType.INFO
            }
            runCatching { icon.displayMessage(title, message, messageType) }
        }
    }
}
