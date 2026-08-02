package dev.haasele.koma.shared.notify

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class AndroidNotifier(private val context: Context) : LocalNotifier {

    init {
        AndroidNotificationChannels.ensure(context)
    }

    override suspend fun notify(title: String, message: String, level: NotificationLevel) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(AndroidNotificationChannels.ALERTS_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return
        }

        val notification = NotificationCompat.Builder(context, AndroidNotificationChannels.ALERTS_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull() ?: message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(
                when (level) {
                    NotificationLevel.ERROR -> NotificationCompat.PRIORITY_HIGH
                    NotificationLevel.WARNING -> NotificationCompat.PRIORITY_DEFAULT
                    NotificationLevel.INFO -> NotificationCompat.PRIORITY_LOW
                },
            )
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(Random.nextInt(1, Int.MAX_VALUE), notification) }
    }
}
