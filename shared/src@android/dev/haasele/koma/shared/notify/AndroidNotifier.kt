package dev.haasele.koma.shared.notify

import android.app.NotificationChannel as SystemNotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class AndroidNotifier(private val context: Context) : LocalNotifier {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                SystemNotificationChannel(CHANNEL_ID, "Monitor status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts when a monitor goes up or down"
                },
            )
        }
    }

    override suspend fun notify(title: String, message: String, level: NotificationLevel) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull() ?: message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                when (level) {
                    NotificationLevel.ERROR -> NotificationCompat.PRIORITY_HIGH
                    NotificationLevel.WARNING -> NotificationCompat.PRIORITY_DEFAULT
                    NotificationLevel.INFO -> NotificationCompat.PRIORITY_LOW
                },
            )
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(Random.nextInt(), notification) }
    }

    private companion object {
        const val CHANNEL_ID = "koma_monitor_status"
    }
}
