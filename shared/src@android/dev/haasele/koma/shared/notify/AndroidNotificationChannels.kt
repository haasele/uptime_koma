package dev.haasele.koma.shared.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Android notification categories so the ongoing foreground-service icon and
 * monitor up/down alerts can be muted independently in system settings.
 */
object AndroidNotificationChannels {

    /** Ongoing foreground-service notification ("tray" / status bar icon). */
    const val PERSISTENT_ID = "koma_persistent_status"

    /** Up/down and other monitor alert notifications. */
    const val ALERTS_ID = "koma_monitor_alerts"

    private val LEGACY_IDS = listOf("koma_engine", "koma_monitor_status")

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                PERSISTENT_ID,
                "Background monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "Persistent status notification that keeps checks running in the background. " +
                        "Turn this off to hide the ongoing icon (Android may still show a minimized entry)."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALERTS_ID,
                "Monitor alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "Alerts when a monitor goes up, down, or otherwise changes status. " +
                        "Turn this off to silence monitoring notifications only."
                setShowBadge(true)
            },
        )

        // Drop previous channel ids so Settings only shows the two clear categories.
        LEGACY_IDS.forEach { id ->
            runCatching { manager.deleteNotificationChannel(id) }
        }
    }

    fun openSystemSettings(context: Context) {
        val intent = Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        }
        context.startActivity(intent)
    }
}
